param([Parameter(Mandatory = $true)][string]$MysqlExe)

$ErrorActionPreference = 'Stop'
foreach ($name in @('SPD_DB_HOST', 'SPD_DB_PORT', 'SPD_DB_NAME', 'SPD_FLYWAY_USERNAME', 'SPD_FLYWAY_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name 未配置。"
    }
}
if (-not (Test-Path -LiteralPath $MysqlExe)) { throw 'mysql 客户端不存在。' }

$repoRoot = Split-Path -Parent $PSScriptRoot
$sqlPath = Join-Path $repoRoot 'scripts\inventory-integrity-check.sql'
$defaultsFile = Join-Path ([IO.Path]::GetTempPath()) ("spd-mysql-{0}.cnf" -f [guid]::NewGuid())
try {
    @("[client]", "user=$env:SPD_FLYWAY_USERNAME", "password=$env:SPD_FLYWAY_PASSWORD") |
        Set-Content -LiteralPath $defaultsFile -Encoding ASCII
    & icacls.exe $defaultsFile /inheritance:r /grant:r "$env:USERNAME`:(R,W)" | Out-Null
    $rows = & $MysqlExe "--defaults-extra-file=$defaultsFile" -h $env:SPD_DB_HOST -P $env:SPD_DB_PORT `
        -D $env:SPD_DB_NAME --batch --skip-column-names -e "source $($sqlPath.Replace('\', '/'))"
    if ($LASTEXITCODE -ne 0) { throw '数据库体检执行失败。' }
    if ($rows) {
        $rows | Write-Error
        throw '数据库体检发现异常，禁止执行约束迁移。'
    }
    Write-Host '数据库体检通过：未发现负库存、孤儿关系、无效货位或重复结算来源。' -ForegroundColor Green
} finally {
    if (Test-Path -LiteralPath $defaultsFile) { Remove-Item -LiteralPath $defaultsFile -Force }
}
