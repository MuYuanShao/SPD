param([switch]$Upgrade, [switch]$NonInteractive, [switch]$SkipServiceInstall)

$ErrorActionPreference = 'Stop'
$sourceScriptPath = if ($env:SPD_INVOKED_SCRIPT_PATH) { $env:SPD_INVOKED_SCRIPT_PATH } else { $MyInvocation.MyCommand.Path }
$installerDir = Split-Path -Parent $sourceScriptPath
$commonScript = Get-Content -LiteralPath (Join-Path $installerDir 'common.ps1') -Raw -Encoding UTF8
. ([scriptblock]::Create($commonScript))
$bundleRoot = Split-Path -Parent $installerDir

if (-not $SkipServiceInstall) { Assert-SpdAdministrator }

function Show-SpdConfigurationDialog {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = [Windows.Forms.Form]::new()
    $form.Text = '院内 SPD 安装配置'
    $form.Size = [Drawing.Size]::new(650, 570)
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false

    $defaults = [ordered]@{
        DbHost = '127.0.0.1'; DbPort = '3306'; DbName = 'ISPD'
        FlywayUser = ''; FlywayPassword = ''; RuntimeUser = ''; RuntimePassword = ''
        ServicePort = '1818'; InstallDir = 'C:\Program Files\HospitalSPD'; DataDir = 'C:\ProgramData\HospitalSPD'
    }
    $labels = [ordered]@{
        DbHost = 'MySQL 地址'; DbPort = 'MySQL 端口'; DbName = '数据库名'
        FlywayUser = '迁移账号'; FlywayPassword = '迁移密码'; RuntimeUser = '运行账号'; RuntimePassword = '运行密码'
        ServicePort = '服务端口'; InstallDir = '安装目录'; DataDir = '数据目录'
    }
    $controls = @{}
    $y = 20
    foreach ($name in $labels.Keys) {
        $label = [Windows.Forms.Label]::new(); $label.Text = $labels[$name]; $label.Location = [Drawing.Point]::new(20, $y + 4); $label.Width = 120
        $box = [Windows.Forms.TextBox]::new(); $box.Name = $name; $box.Text = $defaults[$name]; $box.Location = [Drawing.Point]::new(150, $y); $box.Width = 450
        if ($name -like '*Password') { $box.UseSystemPasswordChar = $true }
        $form.Controls.Add($label); $form.Controls.Add($box); $controls[$name] = $box; $y += 42
    }
    $note = [Windows.Forms.Label]::new(); $note.Text = '迁移账号仅用于本次安装，不会保存。运行密码和 JWT 将写入受 ACL 保护的配置目录。'; $note.Location = [Drawing.Point]::new(20, 445); $note.Size = [Drawing.Size]::new(580, 40)
    $ok = [Windows.Forms.Button]::new(); $ok.Text = '验证并安装'; $ok.Location = [Drawing.Point]::new(390, 490); $ok.DialogResult = [Windows.Forms.DialogResult]::OK
    $cancel = [Windows.Forms.Button]::new(); $cancel.Text = '取消'; $cancel.Location = [Drawing.Point]::new(500, 490); $cancel.DialogResult = [Windows.Forms.DialogResult]::Cancel
    $form.Controls.Add($note); $form.Controls.Add($ok); $form.Controls.Add($cancel); $form.AcceptButton = $ok; $form.CancelButton = $cancel
    if ($form.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { throw '用户取消安装。' }
    $result = @{}; foreach ($name in $controls.Keys) { $result[$name] = $controls[$name].Text.Trim() }; return $result
}

function Get-SpdNonInteractiveConfiguration {
    return @{
        DbHost = $env:SPD_DB_HOST; DbPort = $env:SPD_DB_PORT; DbName = $env:SPD_DB_NAME
        FlywayUser = $env:SPD_FLYWAY_USERNAME; FlywayPassword = $env:SPD_FLYWAY_PASSWORD
        RuntimeUser = $env:SPD_DB_USERNAME; RuntimePassword = $env:SPD_DB_PASSWORD
        ServicePort = $(if ($env:SPD_SERVICE_PORT) { $env:SPD_SERVICE_PORT } else { '1818' })
        InstallDir = $(if ($env:SPD_INSTALL_DIR) { $env:SPD_INSTALL_DIR } else { 'C:\Program Files\HospitalSPD' })
        DataDir = $(if ($env:SPD_DATA_DIR) { $env:SPD_DATA_DIR } else { 'C:\ProgramData\HospitalSPD' })
    }
}

$config = if ($NonInteractive) { Get-SpdNonInteractiveConfiguration } else { Show-SpdConfigurationDialog }
$required = @('DbHost', 'DbPort', 'DbName', 'FlywayUser', 'FlywayPassword', 'RuntimeUser', 'RuntimePassword', 'ServicePort', 'InstallDir', 'DataDir')
foreach ($name in $required) { if ([string]::IsNullOrWhiteSpace($config[$name])) { throw "$name 未填写。" } }
$dbPort = 0; $servicePort = 0
if (-not [int]::TryParse($config.DbPort, [ref]$dbPort) -or $dbPort -lt 1 -or $dbPort -gt 65535) { throw 'MySQL 端口无效。' }
if (-not [int]::TryParse($config.ServicePort, [ref]$servicePort) -or $servicePort -lt 1 -or $servicePort -gt 65535) { throw '服务端口无效。' }
if ($config.RuntimePassword.Length -lt 16) { throw '运行账号密码至少需要 16 个字符。' }

$installDir = [IO.Path]::GetFullPath($config.InstallDir)
$dataDir = [IO.Path]::GetFullPath($config.DataDir)
$configDir = Join-Path $dataDir 'config'; $logsDir = Join-Path $dataDir 'logs'; $uploadsDir = Join-Path $dataDir 'uploads'
$auditDir = Join-Path $logsDir 'migration-audit'; $rollbackDir = Join-Path $dataDir 'rollback-app'
$dbUrl = "jdbc:mysql://$($config.DbHost):$dbPort/$($config.DbName)?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai"
$existingService = Get-Service -Name $script:SpdServiceName -ErrorAction SilentlyContinue
if ($existingService -and $existingService.Status -ne 'Stopped') { Stop-Service -Name $script:SpdServiceName -Force; $existingService.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30)) }

New-Item -ItemType Directory -Force -Path $installDir, $dataDir, $configDir, $logsDir, $uploadsDir, $auditDir, $rollbackDir | Out-Null
$propertiesPath = Join-Path $configDir 'application-production.properties'
$existingJwtSecret = Get-SpdExistingJwtSecret -PropertiesPath $propertiesPath
$backupApp = $null
if (Test-Path -LiteralPath (Join-Path $installDir 'app\app.jar')) {
    $backupApp = Join-Path $rollbackDir (Get-Date -Format 'yyyyMMdd-HHmmss')
    New-Item -ItemType Directory -Force -Path $backupApp | Out-Null
    Copy-Item -LiteralPath (Join-Path $installDir 'app') -Destination $backupApp -Recurse -Force
}

foreach ($name in @('app', 'runtime', 'service', 'installer', 'licenses')) {
    $destination = Join-Path $installDir $name
    if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Recurse -Force }
    Copy-Item -LiteralPath (Join-Path $bundleRoot $name) -Destination $destination -Recurse -Force
}
Copy-Item -LiteralPath (Join-Path $bundleRoot 'VERSION.txt') -Destination (Join-Path $installDir 'VERSION.txt') -Force
Get-ChildItem -LiteralPath $bundleRoot -Filter '*.bat' | Copy-Item -Destination $installDir -Force

$javaExe = Join-Path $installDir 'runtime\jdk-17\bin\java.exe'; $jarPath = Join-Path $installDir 'app\app.jar'
$migrationEnvironment = @{
    SPD_DB_URL = $dbUrl; SPD_FLYWAY_USERNAME = $config.FlywayUser; SPD_FLYWAY_PASSWORD = $config.FlywayPassword
    SPD_AUDIT_DIR = $auditDir
}
$migration = Invoke-SpdDatabaseCommand -JavaExe $javaExe -JarPath $jarPath -Command migrate -Environment $migrationEnvironment
if ($migration.ExitCode -ne 0) { throw "数据库迁移失败。$([Environment]::NewLine)$($migration.Output)$([Environment]::NewLine)如为 V48-V53 历史校验问题，请先运行数据库兼容升级工具。" }

$runtimeValidationEnvironment = @{
    SPD_DB_URL = $dbUrl; SPD_FLYWAY_USERNAME = $config.RuntimeUser; SPD_FLYWAY_PASSWORD = $config.RuntimePassword
    SPD_REQUIRE_NO_DDL = 'true'
}
$runtimeValidation = Invoke-SpdDatabaseCommand -JavaExe $javaExe -JarPath $jarPath -Command validate -Environment $runtimeValidationEnvironment
if ($runtimeValidation.ExitCode -ne 0) {
    throw "运行账号校验失败。请使用可读写业务表、但不含 CREATE/ALTER/DROP 的独立账号。$([Environment]::NewLine)$($runtimeValidation.Output)"
}

$jwtSecret = if ($existingJwtSecret) { $existingJwtSecret } else { New-SpdJwtSecret }
$properties = @(
    "server.port=$servicePort", 'server.address=0.0.0.0', "spring.datasource.url=$(ConvertTo-SpdPropertyValue $dbUrl)",
    "spring.datasource.username=$(ConvertTo-SpdPropertyValue $config.RuntimeUser)", "spring.datasource.password=$(ConvertTo-SpdPropertyValue $config.RuntimePassword)",
    'spring.flyway.enabled=false', "spd.jwt.secret=$jwtSecret", "spd.upload.dir=$(ConvertTo-SpdPropertyValue ($uploadsDir.Replace('\', '/')))",
    "spd.web.static-dir=$(ConvertTo-SpdPropertyValue ((Join-Path $installDir 'app\web').Replace('\', '/')))",
    "logging.file.name=$(ConvertTo-SpdPropertyValue ((Join-Path $logsDir 'hospital-spd.log').Replace('\', '/')))",
    'logging.logback.rollingpolicy.max-file-size=20MB', 'logging.logback.rollingpolicy.max-history=30', 'logging.logback.rollingpolicy.total-size-cap=1GB'
)
$properties | Set-Content -LiteralPath $propertiesPath -Encoding UTF8
Protect-SpdDataDirectory $dataDir

$serviceXmlPath = Join-Path $installDir 'service\HospitalSPD.xml'
$serviceXml = Get-Content -LiteralPath $serviceXmlPath -Raw -Encoding UTF8
$replacements = @{
    '{{JAVA_EXE}}' = (ConvertTo-SpdXmlValue $javaExe); '{{APP_JAR}}' = (ConvertTo-SpdXmlValue $jarPath)
    '{{WORK_DIR}}' = (ConvertTo-SpdXmlValue $dataDir); '{{LOG_DIR}}' = (ConvertTo-SpdXmlValue $logsDir)
    '{{CONFIG_DIR}}' = (ConvertTo-SpdXmlValue (($configDir.Replace('\', '/') + '/')))
}
foreach ($entry in $replacements.GetEnumerator()) { $serviceXml = $serviceXml.Replace($entry.Key, $entry.Value) }
$serviceXml | Set-Content -LiteralPath $serviceXmlPath -Encoding UTF8

if (-not $SkipServiceInstall) {
    if ($existingService) { Invoke-SpdService -Action uninstall -InstallDir $installDir }
    Invoke-SpdService -Action install -InstallDir $installDir
    New-Item -Path $script:SpdRegistryPath -Force | Out-Null
    Set-ItemProperty -LiteralPath $script:SpdRegistryPath -Name InstallDir -Value $installDir
    Set-ItemProperty -LiteralPath $script:SpdRegistryPath -Name DataDir -Value $dataDir
    Set-ItemProperty -LiteralPath $script:SpdRegistryPath -Name Port -Value $servicePort -Type DWord
    Get-NetFirewallRule -DisplayName 'Hospital SPD' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName 'Hospital SPD' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $servicePort -Profile Domain,Private | Out-Null
    Invoke-SpdService -Action start -InstallDir $installDir
    if (-not (Wait-SpdHealth -Port $servicePort)) {
        if ($backupApp) {
            Invoke-SpdService -Action stop -InstallDir $installDir
            $currentApp = Join-Path $installDir 'app'
            if (Test-Path -LiteralPath $currentApp) { Remove-Item -LiteralPath $currentApp -Recurse -Force }
            Copy-Item -LiteralPath (Join-Path $backupApp 'app') -Destination $currentApp -Recurse -Force
            Invoke-SpdService -Action start -InstallDir $installDir
            [void](Wait-SpdHealth -Port $servicePort -TimeoutSeconds 30)
        }
        throw "服务未在 90 秒内健康启动，请检查 $logsDir。数据库迁移不会自动回滚。"
    }
    $localUrl = "http://localhost:$servicePort/api/"
    Start-Process $localUrl
    $lanUrls = Get-SpdLanAddresses -Port $servicePort
    [Windows.Forms.MessageBox]::Show("安装完成。`n本机：$localUrl`n院内地址：$($lanUrls -join ', ')", '院内 SPD') | Out-Null
}

[pscustomobject]@{ version = (Get-Content (Join-Path $installDir 'VERSION.txt') -Raw).Trim(); installedAt = (Get-Date).ToString('o'); servicePort = $servicePort; migration = 'completed'; secretsRecorded = $false } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logsDir 'installation-report.json') -Encoding UTF8
$config.FlywayPassword = $null; $migrationEnvironment.SPD_FLYWAY_PASSWORD = $null
$runtimeValidationEnvironment.SPD_FLYWAY_PASSWORD = $null
