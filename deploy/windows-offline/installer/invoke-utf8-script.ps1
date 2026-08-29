param(
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [ValidateSet('start', 'stop', 'restart', 'open', 'diagnose', 'uninstall')][string]$Action,
    [switch]$Upgrade,
    [string]$BackupEvidencePath
)

$ErrorActionPreference = 'Stop'
$resolvedScript = (Resolve-Path -LiteralPath $ScriptPath).Path
$env:SPD_INVOKED_SCRIPT_PATH = $resolvedScript
$content = Get-Content -LiteralPath $resolvedScript -Raw -Encoding UTF8
$scriptBlock = [scriptblock]::Create($content)
$arguments = @{}
if (-not [string]::IsNullOrWhiteSpace($Action)) { $arguments.Action = $Action }
if ($Upgrade) { $arguments.Upgrade = $true }
if (-not [string]::IsNullOrWhiteSpace($BackupEvidencePath)) { $arguments.BackupEvidencePath = $BackupEvidencePath }
& $scriptBlock @arguments
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
