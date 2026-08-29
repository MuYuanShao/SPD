param([Parameter(Mandatory)][ValidateSet('start', 'stop', 'restart', 'open', 'diagnose', 'uninstall')][string]$Action)

$ErrorActionPreference = 'Stop'
$sourceScriptPath = if ($env:SPD_INVOKED_SCRIPT_PATH) { $env:SPD_INVOKED_SCRIPT_PATH } else { $MyInvocation.MyCommand.Path }
$commonScript = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $sourceScriptPath) 'common.ps1') -Raw -Encoding UTF8
. ([scriptblock]::Create($commonScript))
$installation = Get-SpdInstallation
$installDir = [string]$installation.InstallDir; $dataDir = [string]$installation.DataDir; $port = [int]$installation.Port

switch ($Action) {
    'start' { Assert-SpdAdministrator; Invoke-SpdService -Action start -InstallDir $installDir }
    'stop' { Assert-SpdAdministrator; Invoke-SpdService -Action stop -InstallDir $installDir }
    'restart' { Assert-SpdAdministrator; Invoke-SpdService -Action restart -InstallDir $installDir; if (-not (Wait-SpdHealth $port)) { throw '服务重启后健康检查失败。' } }
    'open' { Start-Process "http://localhost:$port/api/" }
    'diagnose' {
        $service = Get-Service -Name $script:SpdServiceName -ErrorAction SilentlyContinue
        $health = 'DOWN'; try { if (Wait-SpdHealth -Port $port -TimeoutSeconds 3) { $health = 'UP' } } catch { }
        $version = if (Test-Path (Join-Path $installDir 'VERSION.txt')) { (Get-Content (Join-Path $installDir 'VERSION.txt') -Raw).Trim() } else { 'unknown' }
        $serviceStatus = if ($service) { [string]$service.Status } else { 'NOT_INSTALLED' }
        [pscustomobject]@{ Service = $serviceStatus; Health = $health; Port = $port; Version = $version; DataDir = $dataDir; Database = $(if ($health -eq 'UP') { 'application-connected' } else { 'unavailable' }); Flyway = 'packaged' } | Format-List
    }
    'uninstall' {
        Assert-SpdAdministrator
        $confirmation = Read-Host '输入 HospitalSPD 确认卸载服务和程序文件（数据、附件和日志将保留）'
        if ($confirmation -ne 'HospitalSPD') { throw '确认文本不匹配，已取消卸载。' }
        $service = Get-Service -Name $script:SpdServiceName -ErrorAction SilentlyContinue
        if ($service -and $service.Status -ne 'Stopped') { Invoke-SpdService -Action stop -InstallDir $installDir }
        if ($service) { Invoke-SpdService -Action uninstall -InstallDir $installDir }
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
        Write-Host "服务和程序文件已卸载。配置、日志及附件仍保留在：$dataDir" -ForegroundColor Green
    }
}
