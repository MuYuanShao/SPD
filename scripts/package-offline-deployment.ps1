<#
  Builds a zero-data offline SPD release bundle.
  Production database backups are intentionally excluded and belong to a separate DBA workflow.
#>
param([switch]$SkipZip)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Resolve-Jdk17Home {
    foreach ($candidate in @($env:SPD_JAVA_HOME, $env:JAVA_HOME, (Join-Path $repoRoot '.tools\jdk-17'))) {
        $releaseFile = if ($candidate) { Join-Path $candidate 'release' } else { $null }
        if ($releaseFile -and (Test-Path $releaseFile) -and
                (Select-String -Path $releaseFile -Pattern 'JAVA_VERSION="17\.' -Quiet)) {
            return $candidate
        }
    }
    throw '未找到 JDK 17，请设置 SPD_JAVA_HOME。'
}

function Invoke-Checked([string]$Name, [scriptblock]$Command) {
    Write-Host $Name -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Name 失败（exit=$LASTEXITCODE）。" }
}

$releaseVersion = (Get-Content (Join-Path $repoRoot 'package.json') -Raw -Encoding UTF8 |
    ConvertFrom-Json).version
$jdkHome = Resolve-Jdk17Home
$mavenWrapper = Join-Path $repoRoot 'backend\mvnw.cmd'
if (-not (Test-Path $mavenWrapper)) { throw '缺少 backend\mvnw.cmd。' }

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

Invoke-Checked '[1/5] 后端测试与打包（Maven Wrapper 3.9.9）' {
    Push-Location (Join-Path $repoRoot 'backend')
    try { & $mavenWrapper clean package } finally { Pop-Location }
}
Invoke-Checked '[2/5] 前端生产构建' {
    & npm.cmd --prefix frontend run build
}
Invoke-Checked '[3/5] 前端生产依赖审计' {
    & npm.cmd --prefix frontend audit --omit=dev --audit-level=high
}

$jarPath = Join-Path $repoRoot 'backend\target\app.jar'
$distPath = Join-Path $repoRoot 'frontend\dist'
if (-not (Test-Path $jarPath)) { throw "未找到后端产物：$jarPath" }
if (-not (Test-Path (Join-Path $distPath 'index.html'))) { throw "未找到前端产物：$distPath" }
$jarEntries = & (Join-Path $jdkHome 'bin\jar.exe') tf $jarPath
if (($LASTEXITCODE -ne 0) -or
    (-not ($jarEntries -match 'db/migration/V69__approval_requisition_and_catalog_integrity.sql')) -or
    (-not ($jarEntries -match 'db/migration/V70__core_inventory_integrity_constraints.sql'))) {
    throw 'JAR 内容检查失败：缺少最新 Flyway 迁移。'
}

Write-Host '[4/5] 组装零数据离线目录' -ForegroundColor Cyan
$bundleRoot = Join-Path $repoRoot 'output\offline-bundle'
$bundleDir = Join-Path $bundleRoot 'spd-server'
if (Test-Path $bundleDir) { Remove-Item -LiteralPath $bundleDir -Recurse -Force }
$webDir = Join-Path $bundleDir 'web'
$jdkTarget = Join-Path $bundleDir 'tools\jdk-17'
New-Item -ItemType Directory -Force -Path $webDir, $jdkTarget | Out-Null
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $bundleDir 'app.jar')
Copy-Item (Join-Path $distPath '*') $webDir -Recurse -Force
Get-ChildItem -LiteralPath $jdkHome -Force | Where-Object Name -ne 'jmods' | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $jdkTarget $_.Name) -Recurse -Force
}
$releaseVersion | Set-Content (Join-Path $bundleDir 'VERSION.txt') -Encoding ASCII

@'
# SPD production configuration. Copy to config.env and replace every value.
SPD_DB_URL=jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
SPD_DB_USERNAME=
SPD_DB_PASSWORD=
SPD_JWT_SECRET=
'@ | Set-Content (Join-Path $bundleDir 'config.env.example') -Encoding UTF8

@'
$ErrorActionPreference = 'Stop'
$bundleRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $bundleRoot 'config.env'
if (-not (Test-Path $configPath)) {
    Write-Host '首次启动：创建生产配置。数据库内容不会随发布包分发。' -ForegroundColor Yellow
    $dbUrl = Read-Host 'SPD_DB_URL'
    $dbUser = Read-Host 'SPD_DB_USERNAME'
    $secureDbPassword = Read-Host 'SPD_DB_PASSWORD' -AsSecureString
    $dbPassword = [System.Net.NetworkCredential]::new('', $secureDbPassword).Password
    $secretBytes = New-Object byte[] 64
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
    $jwtSecret = [Convert]::ToBase64String($secretBytes)
    @("SPD_DB_URL=$dbUrl", "SPD_DB_USERNAME=$dbUser", "SPD_DB_PASSWORD=$dbPassword", "SPD_JWT_SECRET=$jwtSecret") |
        Set-Content -LiteralPath $configPath -Encoding UTF8
    & icacls.exe $configPath /inheritance:r /grant:r "$env:USERNAME`:(R,W)" | Out-Null
}
foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
    if ($line -match '^\s*([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2], 'Process')
    }
}
foreach ($required in @('SPD_DB_URL', 'SPD_DB_USERNAME', 'SPD_DB_PASSWORD', 'SPD_JWT_SECRET')) {
    $value = [Environment]::GetEnvironmentVariable($required, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { throw "$required 未配置。" }
}
if ([Text.Encoding]::UTF8.GetByteCount($env:SPD_JWT_SECRET) -lt 48 -or
        $env:SPD_JWT_SECRET -match 'change-me|请替换|placeholder') {
    throw 'SPD_JWT_SECRET 必须为至少 48 字节的随机密钥。'
}
$java = Join-Path $bundleRoot 'tools\jdk-17\bin\java.exe'
& $java -jar (Join-Path $bundleRoot 'app.jar') --spd.web.static-dir=(Join-Path $bundleRoot 'web')
'@ | Set-Content (Join-Path $bundleDir 'start-server.ps1') -Encoding UTF8

@'
@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-server.ps1"
if errorlevel 1 pause
'@ | Set-Content (Join-Path $bundleDir 'start-server.bat') -Encoding ASCII

@'
院内 SPD 零数据离线部署说明
============================
1. 发布包仅包含应用、前端、JDK 和 Flyway 迁移，不包含 SQL 导出或在用数据库数据。
2. DBA 先按医院流程创建数据库、迁移账号和运行时账号；数据库备份须单独加密保管。
3. 首次运行 start-server.ps1，录入运行时数据库凭据；脚本自动生成随机 JWT 密钥并写入受限 config.env。
4. 运行时数据库账号不得拥有 DDL 权限，也不得更新或删除不可变库存流水。
5. JWT 密钥切换后现有会话全部失效，需要重新登录。
'@ | Set-Content (Join-Path $bundleDir '部署说明.txt') -Encoding UTF8

if (Get-ChildItem -LiteralPath $bundleDir -Recurse -File | Where-Object Extension -eq '.sql') {
    throw '安全检查失败：离线包中发现 SQL 文件。'
}
$sensitivePatterns = @('admin' + '123', '-p' + 'dbPass')
$bundleTextFiles = Get-ChildItem -LiteralPath $bundleDir -Recurse -File |
    Where-Object Extension -in @('.ps1', '.bat', '.env', '.example', '.txt')
if ($bundleTextFiles | Select-String -Pattern $sensitivePatterns -SimpleMatch -ErrorAction SilentlyContinue) {
    throw '安全检查失败：离线包中发现固定或命令行数据库密码。'
}

Write-Host '[5/5] 生成发布包' -ForegroundColor Cyan
if (-not $SkipZip) {
    $zipPath = Join-Path $bundleRoot "spd-server-offline-$releaseVersion.zip"
    if (Test-Path $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    Compress-Archive -Path (Join-Path $bundleDir '*') -DestinationPath $zipPath
    Write-Host "已生成：$zipPath" -ForegroundColor Green
}
Write-Host "部署目录：$bundleDir" -ForegroundColor Green
