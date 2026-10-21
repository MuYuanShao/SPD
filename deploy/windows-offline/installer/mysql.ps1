# Owns only the private MySQL instance under the selected SPD data directory.
$script:SpdMysqlServiceName = 'HospitalSPDMySQL'

function New-SpdDatabasePassword {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '')
}

function Write-SpdPrivateFile([string]$Path, [string]$Content) {
    # Protect a sibling file before writing secrets, then replace atomically to preserve existing credentials on failure.
    $temporary = $Path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [IO.File]::WriteAllText($temporary, '', [Text.UTF8Encoding]::new($false))
        & icacls.exe $temporary /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '无法保护数据库凭据文件。' }
        [IO.File]::WriteAllText($temporary, $Content, [Text.UTF8Encoding]::new($false))
        if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temporary, $Path, $null) }
        else { [IO.File]::Move($temporary, $Path) }
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Invoke-SpdMysqlSql {
    param([string]$MysqlExe, [int]$Port, [string]$User, [string]$Password, [string]$Sql)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $MysqlExe
    $info.Arguments = "--no-defaults --protocol=TCP --host=127.0.0.1 --port=$Port --user=$User --batch --raw --skip-column-names --default-character-set=utf8mb4"
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.EnvironmentVariables['MYSQL_PWD'] = $Password
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw '无法启动 MySQL 客户端。' }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.WriteLine($Sql); $process.StandardInput.Close()
        if (-not $process.WaitForExit(30000)) { $process.Kill(); throw 'MySQL 客户端执行超时。' }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderrTask.GetAwaiter().GetResult() | Out-Null
        # MySQL errors may echo SQL containing generated credentials; do not include raw stderr.
        if ($process.ExitCode -ne 0) { throw "MySQL 账号验证或配置失败（exit=$($process.ExitCode)）。" }
        return $stdout.Trim()
    } finally { $process.Dispose() }
}

function Assert-SpdMysqlServiceOwnership([string]$OptionsPath, [string]$MysqldExe) {
    $OptionsPath = [IO.Path]::GetFullPath($OptionsPath)
    $MysqldExe = [IO.Path]::GetFullPath($MysqldExe)
    $service = Get-CimInstance Win32_Service -Filter "Name='$script:SpdMysqlServiceName'" -ErrorAction Stop
    if ($service -and ($service.PathName.IndexOf($OptionsPath, [StringComparison]::OrdinalIgnoreCase) -lt 0 -or $service.PathName.IndexOf($MysqldExe, [StringComparison]::OrdinalIgnoreCase) -lt 0)) {
        throw 'HospitalSPDMySQL 服务属于其他安装目录，已中止。'
    }
    return $service
}

function Wait-SpdMysqlReady([string]$MysqlExe, [int]$Port, [string]$RootPassword, [string]$DataPath, $Process = $null) {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($Process) { $Process.Refresh(); if ($Process.HasExited) { throw '内置 MySQL 进程退出，请检查数据库日志。' } }
        try {
            $identity = Invoke-SpdMysqlSql $MysqlExe $Port root $RootPassword 'SELECT @@datadir;'
            if ($identity.Replace('\', '/').TrimEnd('/') -ine $DataPath.Replace('\', '/').TrimEnd('/')) { throw '数据库数据目录不匹配。' }
            return
        } catch {
            if ($_.Exception.Message -eq '数据库数据目录不匹配。') { throw }
            Start-Sleep -Milliseconds 500
        }
    }
    throw '内置 MySQL 启动或账号验证超时。'
}

function Initialize-SpdEmbeddedMysql {
    param([string]$BundleRoot, [string]$DataDir, [int]$Port, [string]$DbName,
          [switch]$SkipServiceInstall, [switch]$SkipPrerequisiteInstall)
    if ($DbName -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') { throw '内置数据库名只允许字母、数字和下划线，且须以字母开头。' }
    if ($Port -lt 1 -or $Port -gt 65535) { throw '内置数据库端口无效。' }
    $mysqlRoot = Join-Path ([IO.Path]::GetFullPath($DataDir)) 'mysql'
    $serverHome = Join-Path $mysqlRoot 'server'; $dbData = Join-Path $mysqlRoot 'data'
    $optionsPath = Join-Path $mysqlRoot 'my.ini'; $credentialsPath = Join-Path $mysqlRoot 'credentials.json'
    $mysqld = Join-Path $serverHome 'bin\mysqld.exe'; $mysql = Join-Path $serverHome 'bin\mysql.exe'
    $credentials = $null; $newDatabase = -not (Test-Path -LiteralPath $credentialsPath)
    if (-not $newDatabase) {
        $credentials = Get-Content -LiteralPath $credentialsPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($credentials.DbName -ne $DbName -or $credentials.Port -ne $Port) { throw '内置数据库已存在，数据库名和端口须保持原配置。' }
        if (-not $credentials.Initialized) { throw '上次数据库初始化未完成，已保留现场。请通过诊断日志处理，禁止自动清空数据重试。' }
        if (-not (Test-Path -LiteralPath (Join-Path $dbData 'mysql.ibd')) -or -not (Test-Path -LiteralPath $mysqld)) {
            throw '已有数据库文件或程序缺失，已中止；不会重新初始化已有数据。'
        }
    } elseif (Test-Path -LiteralPath $mysqlRoot) {
        throw '数据库目录已存在但没有安装凭据，已中止；不会覆盖或清空已有目录。'
    }
    $service = $null
    if (-not $SkipServiceInstall) { $service = Assert-SpdMysqlServiceOwnership -OptionsPath $optionsPath -MysqldExe $mysqld }
    if ($newDatabase -and $service) { throw '数据库服务已存在但没有对应凭据，已中止。' }
    if (($newDatabase -or -not $service -or $service.State -ne 'Running') -and
            (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) {
        throw "内置数据库端口 $Port 已被占用，请选择其他端口或连接已有 MySQL。"
    }
    if (-not $SkipPrerequisiteInstall) {
        Assert-SpdVendorArtifact $BundleRoot 'prerequisites/vc_redist.x64.exe'
        Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security') -Force
        $redist = Join-Path $BundleRoot 'prerequisites\vc_redist.x64.exe'
        $signature = Get-AuthenticodeSignature -LiteralPath $redist
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notlike '*O=Microsoft Corporation*') { throw 'Visual C++ 运行库签名校验失败。' }
        $runtime = Start-Process -FilePath $redist -ArgumentList '/install /quiet /norestart' -WindowStyle Hidden -Wait -PassThru
        if ($runtime.ExitCode -eq 3010) { throw 'Visual C++ 运行库已安装，但需要重启 Windows；重启后再次安装 SPD。' }
        if ($runtime.ExitCode -notin @(0, 1638)) { throw "Visual C++ 运行库安装失败（exit=$($runtime.ExitCode)）。" }
    }
    $bootstrap = Join-Path $mysqlRoot 'bootstrap.tmp'; $process = $null
    if ($newDatabase) {
        Assert-SpdVendorArtifact $BundleRoot 'vendor/mysql-8.4.11-winx64.zip'
        New-Item -ItemType Directory -Path $mysqlRoot | Out-Null
        Protect-SpdDataDirectory $mysqlRoot
        $credentials = [pscustomobject]@{ DbName = $DbName; Port = $Port; RootPassword = (New-SpdDatabasePassword)
            RuntimeUser = 'spd_runtime'; RuntimePassword = (New-SpdDatabasePassword)
            AdminPassword = (New-SpdDatabasePassword); Initialized = $false }
        Write-SpdPrivateFile $credentialsPath ($credentials | ConvertTo-Json)
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = Join-Path $BundleRoot 'vendor\mysql-8.4.11-winx64.zip'
        [IO.Compression.ZipFile]::ExtractToDirectory($archive, $mysqlRoot)
        Move-Item -LiteralPath (Join-Path $mysqlRoot 'mysql-8.4.11-winx64') -Destination $serverHome
        New-Item -ItemType Directory -Path $dbData | Out-Null
        $options = @('[mysqld]', "basedir=`"$($serverHome.Replace('\', '/'))`"", "datadir=`"$($dbData.Replace('\', '/'))`"",
            'bind-address=127.0.0.1', "port=$Port", 'mysqlx=OFF', 'character-set-server=utf8mb4',
            'collation-server=utf8mb4_0900_ai_ci', 'log-bin-trust-function-creators=ON', 'secure-file-priv=NULL',
            "log-error=`"$($mysqlRoot.Replace('\', '/'))/mysql-error.log`"") -join "`n"
        [IO.File]::WriteAllText($optionsPath, $options, [Text.UTF8Encoding]::new($false))
        & $mysqld "--defaults-file=$optionsPath" --initialize 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "内置 MySQL 初始化失败，数据已保留。请检查 $mysqlRoot\mysql-error.log。" }
        Write-SpdPrivateFile $bootstrap "ALTER USER 'root'@'localhost' IDENTIFIED BY '$($credentials.RootPassword)';"
    }
    try {
        if ($newDatabase -or $SkipServiceInstall -or -not $service) {
            $arguments = @("--defaults-file=`"$optionsPath`"")
            if ($newDatabase) { $arguments += "--init-file=`"$bootstrap`"" }
            $process = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru
        } elseif (-not $service -or $service.State -ne 'Running') {
            Start-Service -Name $script:SpdMysqlServiceName
        }
        Wait-SpdMysqlReady $mysql $Port $credentials.RootPassword $dbData $process
        $migrationPassword = New-SpdDatabasePassword
        $sql = @"
CREATE DATABASE IF NOT EXISTS ``$DbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'spd_migrate'@'localhost' IDENTIFIED BY '$migrationPassword';
ALTER USER 'spd_migrate'@'localhost' IDENTIFIED BY '$migrationPassword';
GRANT ALL PRIVILEGES ON ``$DbName``.* TO 'spd_migrate'@'localhost';
CREATE USER IF NOT EXISTS 'spd_runtime'@'localhost' IDENTIFIED BY '$($credentials.RuntimePassword)';
GRANT SELECT, INSERT, UPDATE, DELETE ON ``$DbName``.* TO 'spd_runtime'@'localhost';
"@
        Invoke-SpdMysqlSql $mysql $Port root $credentials.RootPassword $sql | Out-Null
        if ($newDatabase) {
            $credentials.Initialized = $true
            Write-SpdPrivateFile $credentialsPath ($credentials | ConvertTo-Json)
        }
        if (-not $SkipServiceInstall -and $process) {
            Invoke-SpdMysqlSql $mysql $Port root $credentials.RootPassword 'SHUTDOWN;' | Out-Null
            if (-not $process.WaitForExit(30000)) { throw 'MySQL 未正常停止，已中止服务注册。' }
            & $mysqld --install $script:SpdMysqlServiceName "--defaults-file=$optionsPath" --local-service | Out-Null
            if ($LASTEXITCODE -ne 0) { throw '内置 MySQL 服务注册失败。' }
            Start-Service -Name $script:SpdMysqlServiceName
            $service = Get-Service -Name $script:SpdMysqlServiceName
            $service.WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
            Wait-SpdMysqlReady $mysql $Port $credentials.RootPassword $dbData
            $process.Dispose(); $process = $null
        }
        return [pscustomobject]@{ FlywayUser = 'spd_migrate'; FlywayPassword = $migrationPassword
            RuntimeUser = $credentials.RuntimeUser; RuntimePassword = $credentials.RuntimePassword
            InitialAdminPassword = $credentials.AdminPassword
            NewDatabase = $newDatabase; Process = $process; CredentialsPath = $credentialsPath }
    } catch {
        if ($process) { $process.Refresh(); if (-not $process.HasExited) { $process.Kill() }; $process.Dispose() }
        throw
    } finally {
        if (Test-Path -LiteralPath $bootstrap) { Remove-Item -LiteralPath $bootstrap -Force }
    }
}
