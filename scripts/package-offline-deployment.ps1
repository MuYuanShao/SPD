<#
  Builds the zero-data Windows service deployment package.
  Production database backups and real configuration are intentionally excluded.
#>
param(
    [switch]$SkipBuild,
    [switch]$SkipZip,
    [string]$WinSwCachePath
)

$ErrorActionPreference = 'Stop'
$SkipBuild = $SkipBuild -or $env:SPD_PACKAGE_SKIP_BUILD -eq '1'
$SkipZip = $SkipZip -or $env:SPD_PACKAGE_SKIP_ZIP -eq '1'
$sourceScriptPath = if ($env:SPD_INVOKED_SCRIPT_PATH) { $env:SPD_INVOKED_SCRIPT_PATH } else { $MyInvocation.MyCommand.Path }
$repoRoot = [IO.Path]::GetFullPath((Split-Path -Parent (Split-Path -Parent $sourceScriptPath)))
Set-Location $repoRoot
$winSwVersion = '2.12.0'
$winSwSha256 = '05B82D46AD331CC16BDC00DE5C6332C1EF818DF8CEEFCD49C726553209B3A0DA'
$winSwLicenseSha256 = '1CDF703C10A70E5973BF3ACF2A5EEABE7746237155B92DB2034AEAE26FDF7802'
$winSwUrl = "https://github.com/winsw/winsw/releases/download/v$winSwVersion/WinSW-x64.exe"
$winSwLicenseUrl = "https://raw.githubusercontent.com/winsw/winsw/v$winSwVersion/LICENSE.txt"

function Resolve-Jdk17Home {
    foreach ($candidate in @($env:SPD_JAVA_HOME, $env:JAVA_HOME, (Join-Path $repoRoot '.tools\jdk-17'))) {
        $releaseFile = if ($candidate) { Join-Path $candidate 'release' } else { $null }
        if ($releaseFile -and (Test-Path -LiteralPath $releaseFile) -and
                (Select-String -LiteralPath $releaseFile -Pattern 'JAVA_VERSION="17\.' -Quiet)) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw '未找到 JDK 17，请设置 SPD_JAVA_HOME。'
}

function Invoke-Checked([string]$Name, [scriptblock]$Command) {
    Write-Host $Name -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Name 失败（exit=$LASTEXITCODE）。" }
}

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '') }
        finally { $algorithm.Dispose() }
    } finally { $stream.Dispose() }
}

function Get-VerifiedVendorFile([string]$Path, [string]$Url, [string]$ExpectedHash) {
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
        Write-Host "下载受控依赖：$Url" -ForegroundColor Cyan
        Invoke-WebRequest -Uri $Url -OutFile $Path
    }
    $actual = Get-Sha256 $Path
    if ($actual -ne $ExpectedHash) { throw "第三方依赖哈希不匹配：$Path" }
    return $Path
}

$releaseVersion = (Get-Content -LiteralPath (Join-Path $repoRoot 'package.json') -Raw -Encoding UTF8 | ConvertFrom-Json).version
$jdkHome = Resolve-Jdk17Home
$mavenWrapper = Join-Path $repoRoot 'backend\mvnw.cmd'
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

if (-not $SkipBuild) {
    Invoke-Checked '[1/6] Flyway 历史哈希校验' { & npm.cmd run verify:flyway-migrations }
    Invoke-Checked '[2/6] 后端测试与可执行 JAR 打包' {
        Push-Location (Join-Path $repoRoot 'backend')
        try { & $mavenWrapper clean package } finally { Pop-Location }
    }
    Invoke-Checked '[3/6] 前端生产构建' {
        $previousBasePath = $env:VITE_BASE_PATH
        try {
            $env:VITE_BASE_PATH = '/api/app/'
            & npm.cmd --prefix frontend run build
        } finally {
            $env:VITE_BASE_PATH = $previousBasePath
        }
    }
    Invoke-Checked '[4/6] 前端生产依赖审计' { & npm.cmd audit --omit=dev --audit-level=high }
}

$jarPath = Join-Path $repoRoot 'backend\target\app.jar'
$distPath = Join-Path $repoRoot 'frontend\dist'
if (-not (Test-Path -LiteralPath $jarPath)) { throw "未找到后端产物：$jarPath" }
if (-not (Test-Path -LiteralPath (Join-Path $distPath 'index.html'))) { throw "未找到前端产物：$distPath" }
$jarEntries = & (Join-Path $jdkHome 'bin\jar.exe') tf $jarPath
if (($LASTEXITCODE -ne 0) -or
        (-not ($jarEntries -match 'db/migration/V70__core_inventory_integrity_constraints.sql')) -or
        (-not ($jarEntries -match 'com/hospital/spd/deployment/DeploymentDatabaseCommand.class'))) {
    throw 'JAR 内容检查失败：缺少最新 Flyway 迁移或部署数据库命令。'
}

$vendorCache = if ($WinSwCachePath) { [IO.Path]::GetFullPath($WinSwCachePath) } else { Join-Path $repoRoot "output\vendor-cache\winsw-$winSwVersion" }
$winSwExe = Get-VerifiedVendorFile -Path (Join-Path $vendorCache 'WinSW-x64.exe') -Url $winSwUrl -ExpectedHash $winSwSha256
$winSwLicense = Get-VerifiedVendorFile -Path (Join-Path $vendorCache 'LICENSE.txt') -Url $winSwLicenseUrl -ExpectedHash $winSwLicenseSha256

Write-Host '[5/6] 组装 Windows 零数据服务包' -ForegroundColor Cyan
$bundleRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'output\offline-bundle'))
$bundleDir = [IO.Path]::GetFullPath((Join-Path $bundleRoot 'spd-server'))
if ((Split-Path -Parent $bundleDir) -ne $bundleRoot) { throw '离线包目标目录校验失败。' }
if (Test-Path -LiteralPath $bundleDir) { Remove-Item -LiteralPath $bundleDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $bundleDir 'app\web'), (Join-Path $bundleDir 'runtime\jdk-17'), (Join-Path $bundleDir 'licenses') | Out-Null

Copy-Item -LiteralPath $jarPath -Destination (Join-Path $bundleDir 'app\app.jar')
Copy-Item (Join-Path $distPath '*') (Join-Path $bundleDir 'app\web') -Recurse -Force
Get-ChildItem -LiteralPath $jdkHome -Force | Where-Object Name -ne 'jmods' | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path (Join-Path $bundleDir 'runtime\jdk-17') $_.Name) -Recurse -Force
}
Copy-Item (Join-Path $repoRoot 'deploy\windows-offline\*') $bundleDir -Recurse -Force
Copy-Item -LiteralPath $winSwExe -Destination (Join-Path $bundleDir 'service\HospitalSPD.exe') -Force
Copy-Item -LiteralPath $winSwLicense -Destination (Join-Path $bundleDir 'licenses\WinSW-LICENSE.txt') -Force
$releaseVersion | Set-Content -LiteralPath (Join-Path $bundleDir 'VERSION.txt') -Encoding ASCII
'V70' | Set-Content -LiteralPath (Join-Path $bundleDir 'MIGRATION_VERSION.txt') -Encoding ASCII

$forbidden = @(Get-ChildItem -LiteralPath $bundleDir -Recurse -File | Where-Object Extension -in @('.sql', '.java', '.class'))
if ($forbidden.Count -gt 0) { throw "安全检查失败：发布包发现禁止文件：$($forbidden[0].FullName)" }
$textFiles = @(Get-ChildItem -LiteralPath $bundleDir -Recurse -File | Where-Object Extension -in @('.ps1', '.bat', '.cmd', '.xml', '.txt', '.properties', '.yml', '.yaml', '.json'))
if ($textFiles | Select-String -Pattern @('admin' + '123', 'root' + '123', 'change-me-to-a-strong', 'hnzlth' + '@20260620') -SimpleMatch -ErrorAction SilentlyContinue) {
    throw '安全检查失败：发布包发现固定密码或占位密钥。'
}

$manifestPath = Join-Path $bundleDir 'SHA256SUMS.txt'
$manifestLines = Get-ChildItem -LiteralPath $bundleDir -Recurse -File | Where-Object FullName -ne $manifestPath | Sort-Object FullName | ForEach-Object {
    $relativePath = $_.FullName.Substring($bundleDir.Length + 1).Replace('\', '/')
    "$((Get-Sha256 $_.FullName).ToLowerInvariant())  $relativePath"
}
[IO.File]::WriteAllLines($manifestPath, [string[]]$manifestLines, [Text.UTF8Encoding]::new($false))
$env:SPD_OFFLINE_BUNDLE_DIR = $bundleDir
Invoke-Checked '离线包结构与敏感信息契约校验' { & node.exe scripts/verify-offline-package.mjs }

Write-Host '[6/6] 生成 ZIP 与交付哈希' -ForegroundColor Cyan
if (-not $SkipZip) {
    $zipPath = Join-Path $bundleRoot "spd-server-offline-$releaseVersion.zip"
    $zipHashPath = "$zipPath.sha256"
    if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
    if (Test-Path -LiteralPath $zipHashPath) { Remove-Item -LiteralPath $zipHashPath -Force }
    Compress-Archive -Path (Join-Path $bundleDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
    "$((Get-Sha256 $zipPath).ToLowerInvariant())  $(Split-Path -Leaf $zipPath)" |
        Set-Content -LiteralPath $zipHashPath -Encoding ASCII
    Write-Host "已生成：$zipPath" -ForegroundColor Green
    Write-Host "ZIP 哈希：$zipHashPath" -ForegroundColor Green
}
Write-Host "部署目录：$bundleDir" -ForegroundColor Green
