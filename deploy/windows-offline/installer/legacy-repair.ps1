param([Parameter(Mandatory)][string]$BackupEvidencePath)

$ErrorActionPreference = 'Stop'
$sourceScriptPath = if ($env:SPD_INVOKED_SCRIPT_PATH) { $env:SPD_INVOKED_SCRIPT_PATH } else { $MyInvocation.MyCommand.Path }
$commonScript = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $sourceScriptPath) 'common.ps1') -Raw -Encoding UTF8
. ([scriptblock]::Create($commonScript))
Assert-SpdAdministrator
$installation = Get-SpdInstallation
$dbHost = Read-Host 'MySQL 地址'; $dbPort = Read-Host 'MySQL 端口'; $dbName = Read-Host '数据库名（将作为二次确认）'
$flywayUser = Read-Host '迁移账号'; $secure = Read-Host '迁移密码' -AsSecureString
$flywayPassword = [Net.NetworkCredential]::new('', $secure).Password
$dbUrl = "jdbc:mysql://$dbHost`:$dbPort/$dbName?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$environment = @{ SPD_DB_URL = $dbUrl; SPD_FLYWAY_USERNAME = $flywayUser; SPD_FLYWAY_PASSWORD = $flywayPassword; SPD_BACKUP_EVIDENCE = $BackupEvidencePath; SPD_LEGACY_REPAIR_CONFIRM = $dbName; SPD_AUDIT_DIR = (Join-Path $installation.DataDir 'logs\migration-audit') }
$result = Invoke-SpdDatabaseCommand -JavaExe (Join-Path $installation.InstallDir 'runtime\jdk-17\bin\java.exe') -JarPath (Join-Path $installation.InstallDir 'app\app.jar') -Command legacy-repair -Environment $environment
$environment.SPD_FLYWAY_PASSWORD = $null; $flywayPassword = $null
if ($result.ExitCode -ne 0) { throw $result.Output }
Write-Host $result.Output -ForegroundColor Green
