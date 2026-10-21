param([Parameter(Mandatory)][ValidateSet('start', 'stop', 'restart', 'open', 'diagnose', 'uninstall')][string]$Action)

$ErrorActionPreference = 'Stop'
$sourceScriptPath = if ($env:SPD_INVOKED_SCRIPT_PATH) { $env:SPD_INVOKED_SCRIPT_PATH } else { $MyInvocation.MyCommand.Path }
$commonScript = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $sourceScriptPath) 'common.ps1') -Raw -Encoding UTF8
. ([scriptblock]::Create($commonScript))
$installation = Get-SpdInstallation
$installDir = [string]$installation.InstallDir; $dataDir = [string]$installation.DataDir; $port = [int]$installation.Port
$embedded = $installation.PSObject.Properties['DbMode'] -and $installation.DbMode -eq 'embedded'
if ($embedded) {
    . ([scriptblock]::Create((Get-Content -LiteralPath (Join-Path $installDir 'installer/mysql.ps1') -Raw -Encoding UTF8)))
}

function Invoke-SpdPrivateDatabase([ValidateSet('start', 'stop', 'uninstall')][string]$Operation) {
    if (-not $embedded) { return }
    $mysqld = Join-Path $dataDir 'mysql/server/bin/mysqld.exe'
    $service = Assert-SpdMysqlServiceOwnership (Join-Path $dataDir 'mysql/my.ini') $mysqld
    if (-not $service) {
        if ($Operation -eq 'start') { throw '内置数据库服务不存在，请重新运行安装程序。' }
        return
    }
    if ($Operation -eq 'start') {
        Start-Service -Name $script:SpdMysqlServiceName
        (Get-Service -Name $script:SpdMysqlServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
    } else {
        if ($service.State -ne 'Stopped') {
            Stop-Service -Name $script:SpdMysqlServiceName
            (Get-Service -Name $script:SpdMysqlServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
        }
        if ($Operation -eq 'uninstall') {
            & $mysqld --remove $script:SpdMysqlServiceName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw '内置数据库服务卸载失败，数据目录已保留。' }
        }
    }
}

switch ($Action) {
    'start' { Assert-SpdAdministrator; Invoke-SpdPrivateDatabase start; Invoke-SpdService -Action start -InstallDir $installDir }
    'stop' { Assert-SpdAdministrator; Invoke-SpdService -Action stop -InstallDir $installDir; Invoke-SpdPrivateDatabase stop }
    'restart' { Assert-SpdAdministrator; Invoke-SpdPrivateDatabase start; Invoke-SpdService -Action restart -InstallDir $installDir; if (-not (Wait-SpdHealth $port)) { throw '服务重启后健康检查失败。' } }
    'open' { Start-Process "http://localhost:$port/api/" }
    'diagnose' {
        $service = Get-Service -Name $script:SpdServiceName -ErrorAction SilentlyContinue
        $health = 'DOWN'; try { if (Wait-SpdHealth -Port $port -TimeoutSeconds 3) { $health = 'UP' } } catch { }
        $version = if (Test-Path (Join-Path $installDir 'VERSION.txt')) { (Get-Content (Join-Path $installDir 'VERSION.txt') -Raw).Trim() } else { 'unknown' }
        $serviceStatus = if ($service) { [string]$service.Status } else { 'NOT_INSTALLED' }
        $databaseService = if ($embedded) { Get-Service -Name 'HospitalSPDMySQL' -ErrorAction SilentlyContinue } else { $null }
        [pscustomobject]@{ Service = $serviceStatus; Health = $health; Port = $port; Version = $version; DataDir = $dataDir; DatabaseMode = $(if ($embedded) { 'embedded' } else { 'external' }); DatabaseService = $(if ($databaseService) { [string]$databaseService.Status } else { 'not-managed' }); Database = $(if ($health -eq 'UP') { 'application-connected' } else { 'unavailable' }); Flyway = 'packaged' } | Format-List
    }
    'uninstall' {
        Assert-SpdAdministrator
        $confirmation = Read-Host '输入 HospitalSPD 确认卸载服务和程序文件（数据、附件和日志将保留）'
        if ($confirmation -ne 'HospitalSPD') { throw '确认文本不匹配，已取消卸载。' }
        $installDir = [IO.Path]::GetFullPath($installDir); $dataDir = [IO.Path]::GetFullPath($dataDir)
        $installPrefix = $installDir.TrimEnd('\') + '\'; $dataPrefix = $dataDir.TrimEnd('\') + '\'
        if ($installDir.TrimEnd('\') -eq [IO.Path]::GetPathRoot($installDir).TrimEnd('\') -or
                $dataPrefix.StartsWith($installPrefix, [StringComparison]::OrdinalIgnoreCase) -or
                $installPrefix.StartsWith($dataPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw '安装目录安全校验失败，已保留程序文件。' }
        if ($embedded) { $null = Assert-SpdMysqlServiceOwnership (Join-Path $dataDir 'mysql/my.ini') (Join-Path $dataDir 'mysql/server/bin/mysqld.exe') }
        $service = Get-Service -Name $script:SpdServiceName -ErrorAction SilentlyContinue
        if ($service -and $service.Status -ne 'Stopped') { Invoke-SpdService -Action stop -InstallDir $installDir }
        if ($service) { Invoke-SpdService -Action uninstall -InstallDir $installDir }
        Invoke-SpdPrivateDatabase uninstall
        Get-NetFirewallRule -DisplayName 'Hospital SPD' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
        Remove-Item -LiteralPath $script:SpdRegistryPath -Recurse -Force
        $installRoot = [IO.Path]::GetPathRoot($installDir)
        if ([string]::IsNullOrWhiteSpace($installDir) -or $installDir.TrimEnd('\') -eq $installRoot.TrimEnd('\') -or $installDir -eq $dataDir) {
            throw '安装目录安全校验失败，已保留程序文件。'
        }
        foreach ($name in @('app', 'runtime', 'service', 'installer', 'licenses')) {
            $target = Join-Path $installDir $name
            if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
        }
        foreach ($name in @('VERSION.txt', '安装SPD.bat', '启动SPD.bat', '停止SPD.bat', '重启SPD.bat', '打开SPD系统.bat', 'SPD诊断.bat', '升级SPD.bat', '卸载SPD.bat', '数据库兼容升级.bat', '部署说明.txt')) {
            $target = Join-Path $installDir $name
            if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }
        }
        Write-Host "服务和程序文件已卸载。数据库、配置、日志及附件仍保留在：$dataDir" -ForegroundColor Green
    }
}
