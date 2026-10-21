Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:SpdServiceName = 'HospitalSPD'
$script:SpdRegistryPath = 'HKLM:\SOFTWARE\HospitalSPD'

function Test-SpdAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Assert-SpdAdministrator {
    if (-not (Test-SpdAdministrator)) {
        throw '该操作需要管理员权限，请右键选择“以管理员身份运行”。'
    }
}

function Get-SpdInstallation {
    if (-not (Test-Path -LiteralPath $script:SpdRegistryPath)) {
        throw '未找到 HospitalSPD 安装信息，请先运行 安装SPD.bat。'
    }
    return Get-ItemProperty -LiteralPath $script:SpdRegistryPath
}

function ConvertTo-SpdPropertyValue([string]$Value) {
    if ($null -eq $Value) { return '' }
    return $Value.Replace('\', '\\').Replace("`r", '').Replace("`n", '\n')
}

function ConvertTo-SpdXmlValue([string]$Value) {
    return [Security.SecurityElement]::Escape($Value)
}

function New-SpdJwtSecret {
    $bytes = New-Object byte[] 64
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Get-SpdExistingJwtSecret([string]$PropertiesPath) {
    if (-not (Test-Path -LiteralPath $PropertiesPath)) { return $null }
    $line = Get-Content -LiteralPath $PropertiesPath -Encoding UTF8 |
        Where-Object { $_ -match '^spd\.jwt\.secret=' } |
        Select-Object -First 1
    if ($null -eq $line) { return $null }
    $value = $line.Substring($line.IndexOf('=') + 1).Trim()
    try {
        $bytes = [Convert]::FromBase64String($value)
        if ($bytes.Length -ge 64) { return $value }
    } catch { }
    return $null
}

function Protect-SpdDataDirectory([string]$DataDir) {
    Assert-SpdAdministrator
    & icacls.exe $DataDir /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '无法设置 SPD 数据目录访问权限。' }
}

function Invoke-SpdDatabaseCommand {
    param(
        [Parameter(Mandatory)][string]$JavaExe,
        [Parameter(Mandatory)][string]$JarPath,
        [Parameter(Mandatory)][ValidateSet('validate', 'migrate', 'legacy-repair', 'bootstrap-admin')][string]$Command,
        [Parameter(Mandatory)][hashtable]$Environment
    )
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaExe
    $startInfo.Arguments = '-jar "' + $JarPath + '" --spd.command=' + $Command
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($entry in $Environment.GetEnumerator()) {
        $startInfo.EnvironmentVariables[$entry.Key] = [string]$entry.Value
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw '无法启动数据库迁移进程。' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = $process.ExitCode
    $process.Dispose()
    $combined = (($stdout, $stderr) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join [Environment]::NewLine
    foreach ($secretName in @('SPD_FLYWAY_PASSWORD', 'SPD_DB_PASSWORD', 'SPD_JWT_SECRET', 'SPD_INITIAL_ADMIN_PASSWORD')) {
        if ($Environment.ContainsKey($secretName) -and -not [string]::IsNullOrEmpty($Environment[$secretName])) {
            $combined = $combined.Replace([string]$Environment[$secretName], '<redacted>')
        }
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = $combined.Trim() }
}

function Assert-SpdVendorArtifact([string]$BundleRoot, [string]$RelativePath) {
    $manifest = Get-Content -LiteralPath (Join-Path $BundleRoot 'installer/vendor.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $entries = @($manifest | Where-Object { $_.path -ceq $RelativePath })
    if ($entries.Count -ne 1 -or $entries[0].sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw '第三方组件清单无效。' }
    $root = [IO.Path]::GetFullPath($BundleRoot).TrimEnd('\') + '\'
    $path = [IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    if (-not $path.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw '第三方组件路径越界。' }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "第三方组件缺失：$RelativePath" }
    $stream = [IO.File]::OpenRead($path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { $actual = ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '') }
    finally { $algorithm.Dispose(); $stream.Dispose() }
    if ($actual -ine $entries[0].sha256) {
        throw "第三方组件缺失或 SHA-256 不匹配：$RelativePath"
    }
}

function Assert-SpdInstallSource([string]$BundleRoot, [string]$InstallDir) {
    $source = [IO.Path]::GetFullPath($BundleRoot).TrimEnd('\', '/') + '\'
    $target = [IO.Path]::GetFullPath($InstallDir).TrimEnd('\', '/') + '\'
    if ($source.StartsWith($target, [StringComparison]::OrdinalIgnoreCase) -or
            $target.StartsWith($source, [StringComparison]::OrdinalIgnoreCase)) {
        throw '离线包目录与安装目录不能相同或互相包含。请从新解压的完整离线包运行安装或升级。'
    }
    foreach ($relative in @('app/app.jar', 'app/web/index.html', 'runtime/jdk-17/bin/java.exe',
            'service/HospitalSPD.exe', 'service/HospitalSPD.xml', 'installer/common.ps1',
            'installer/install.ps1', 'installer/mysql.ps1', 'licenses/WinSW-LICENSE.txt', 'VERSION.txt')) {
        if (-not (Test-Path -LiteralPath (Join-Path $BundleRoot $relative) -PathType Leaf)) {
            throw "离线包不完整，已中止安装：$relative"
        }
    }
}

function Wait-SpdHealth([int]$Port, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $url = "http://127.0.0.1:$Port/api/health"
    do {
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($response.code -eq 0 -and $response.data.status -eq 'UP') { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Get-SpdLanAddresses([int]$Port) {
    return @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
        ForEach-Object { "http://$($_.IPAddress):$Port/api/" } |
        Sort-Object -Unique)
}

function Invoke-SpdService([ValidateSet('install', 'uninstall', 'start', 'stop', 'restart', 'status')][string]$Action,
        [string]$InstallDir) {
    $wrapper = Join-Path $InstallDir 'service\HospitalSPD.exe'
    if (-not (Test-Path -LiteralPath $wrapper)) { throw "服务包装器不存在：$wrapper" }
    & $wrapper $Action
    if ($LASTEXITCODE -ne 0) { throw "HospitalSPD 服务操作失败：$Action" }
    if ($Action -eq 'stop') {
        (Get-Service -Name $script:SpdServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
    }
}
