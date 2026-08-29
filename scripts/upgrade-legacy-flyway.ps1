<#
  Controlled upgrade path for databases whose published V48-V53 checksums differ.
  The application never invokes repair automatically.
#>
param(
    [switch]$ConfirmLegacyChecksumRepair,
    [Parameter(Mandatory = $true)][string]$BackupEvidencePath,
    [string]$AuditDirectory = 'output/flyway-upgrade-audit'
)

$ErrorActionPreference = 'Stop'
if (-not $ConfirmLegacyChecksumRepair) {
    throw '必须显式传入 -ConfirmLegacyChecksumRepair。'
}
if (-not (Test-Path -LiteralPath $BackupEvidencePath)) {
    throw '未找到上线前完整数据库备份凭据，禁止升级。'
}
foreach ($name in @('SPD_DB_URL', 'SPD_FLYWAY_USERNAME', 'SPD_FLYWAY_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name 未配置。"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $repoRoot 'backend\mvnw.cmd'
$auditRoot = Join-Path $repoRoot $AuditDirectory
New-Item -ItemType Directory -Force -Path $auditRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$auditFile = Join-Path $auditRoot "legacy-flyway-$stamp.log"

function Invoke-Flyway([string[]]$Arguments, [switch]$AllowFailure) {
    Push-Location (Join-Path $repoRoot 'backend')
    try {
        $output = & $wrapper @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $safeOutput = $output -replace [regex]::Escape($env:SPD_FLYWAY_PASSWORD), '<redacted>'
    $safeOutput | Add-Content -LiteralPath $auditFile -Encoding UTF8
    if ($exitCode -ne 0 -and -not $AllowFailure) { throw "Flyway 命令失败，详见 $auditFile" }
    return @{ ExitCode = $exitCode; Output = ($safeOutput -join "`n") }
}

"backupEvidence=$((Resolve-Path -LiteralPath $BackupEvidencePath).Path)" |
    Set-Content -LiteralPath $auditFile -Encoding UTF8
Invoke-Flyway @('flyway:info') | Out-Null
$validation = Invoke-Flyway @('flyway:validate') -AllowFailure
if ($validation.ExitCode -ne 0) {
    $versions = [regex]::Matches($validation.Output, '(?i)version\s+([0-9]+)') |
        ForEach-Object { [int]$_.Groups[1].Value } | Sort-Object -Unique
    if ($versions.Count -eq 0 -or ($versions | Where-Object { $_ -lt 48 -or $_ -gt 53 })) {
        throw '校验异常不限于 V48-V53，已中止且未执行 repair。'
    }
}

Invoke-Flyway @('-Dflyway.validateOnMigrate=false', '-Dflyway.outOfOrder=true', 'flyway:migrate') | Out-Null
Invoke-Flyway @('flyway:repair') | Out-Null
Invoke-Flyway @('flyway:validate') | Out-Null
Invoke-Flyway @('flyway:info') | Out-Null
Write-Host "旧库升级完成，审计记录：$auditFile" -ForegroundColor Green
