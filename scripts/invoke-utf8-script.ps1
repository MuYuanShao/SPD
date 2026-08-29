param(
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [string[]]$ScriptArguments = @()
)

$ErrorActionPreference = 'Stop'
$resolvedScript = (Resolve-Path -LiteralPath $ScriptPath).Path
$env:SPD_INVOKED_SCRIPT_PATH = $resolvedScript
$content = Get-Content -LiteralPath $resolvedScript -Raw -Encoding UTF8
$scriptBlock = [scriptblock]::Create($content)
& $scriptBlock @ScriptArguments
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
