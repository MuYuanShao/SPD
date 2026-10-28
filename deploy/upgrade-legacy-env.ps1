param([Parameter(Mandatory=$true)][string]$LegacyEnvFile, [Parameter(Mandatory=$true)][string]$DatabaseVolume, [string]$OutputEnvFile = '')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'docker/common.ps1')
if (-not $OutputEnvFile) { $OutputEnvFile=Join-Path $PSScriptRoot '.env.upgraded' }
$OutputEnvFile=[IO.Path]::GetFullPath($OutputEnvFile)
if (Test-Path -LiteralPath $OutputEnvFile) { throw 'Choose a new output environment file; existing files are never overwritten.' }
$values=@{}
foreach ($line in [IO.File]::ReadAllLines([IO.Path]::GetFullPath($LegacyEnvFile))) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $values[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'") }
}
foreach ($required in @('SPD_DB_USERNAME','SPD_DB_PASSWORD','SPD_DB_ROOT_PASSWORD','SPD_JWT_SECRET')) {
    if (-not $values[$required]) { throw "Legacy environment is missing $required; use the original deployment credentials." }
}
if ($values['SPD_DB_USERNAME'] -eq 'root') { throw 'Root cannot be reused as a migration account. Prepare a dedicated schema owner first.' }
& docker volume inspect $DatabaseVolume > $null
if ($LASTEXITCODE -ne 0) { throw 'The original database volume must exist; no empty volume will be created.' }
$values['SPD_FLYWAY_USERNAME']=$values['SPD_DB_USERNAME']
$values['SPD_FLYWAY_PASSWORD']=$values['SPD_DB_PASSWORD']
$values['SPD_DB_USERNAME']=if ($values['SPD_FLYWAY_USERNAME'] -eq 'spd_runtime') { 'spd_app' } else { 'spd_runtime' }
$values['SPD_DB_PASSWORD']=New-SpdSecret
$values['SPD_MYSQL_VOLUME']=$DatabaseVolume
if (-not $values['COMPOSE_PROJECT_NAME']) { $values['COMPOSE_PROJECT_NAME']='deploy' }
Initialize-SpdEnvironment $OutputEnvFile
$generated=@{}
foreach ($line in [IO.File]::ReadAllLines($OutputEnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $generated[$Matches[1]]=$Matches[2] }
}
foreach ($key in $values.Keys) { $generated[$key]=$values[$key] }
$lines=@($generated.Keys | Sort-Object | ForEach-Object {
    $escaped=([string]$generated[$_]).Replace("'", "\'")
    "$_='$escaped'"
})
[IO.File]::WriteAllText($OutputEnvFile,($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Host "Prepared $OutputEnvFile. Original environment and volume unchanged. Back up the database before maintenance startup."
Write-Host 'Use a fresh shell without overriding SPD_* variables, then run start.ps1 -EnvFile <prepared-file>.'
