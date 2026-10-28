param([switch]$WithAi, [switch]$NoBuild, [switch]$InitOnly, [string]$EnvFile = '', [int]$WaitTimeout = 1200)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'docker/common.ps1')
if (-not $EnvFile) { $EnvFile = Join-Path $PSScriptRoot '.env' }
$EnvFile = [IO.Path]::GetFullPath($EnvFile)
if (-not $InitOnly) { Assert-SpdExistingDatabase $EnvFile }
Initialize-SpdEnvironment $EnvFile
$composeArgs = @('compose', '--env-file', $EnvFile, '-f', (Join-Path $PSScriptRoot 'docker-compose.yml'))
if ($WithAi) { $composeArgs += @('--profile','ai') }
Assert-SpdCompose $composeArgs
if ($InitOnly) { Write-Host 'Deployment settings validated. No containers started.'; exit 0 }
Assert-SpdDocker
$bundle = Join-Path $PSScriptRoot 'images.tar'
$offline = Test-Path -LiteralPath $bundle
if ($offline) {
    Assert-SpdBundle $PSScriptRoot
    Invoke-SpdDocker load --input $bundle
    $env:SPD_OFFLINE='true'
    if ($WithAi -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'models.tar'))) {
        $project = Get-SpdSetting $EnvFile 'COMPOSE_PROJECT_NAME' 'hospital-spd'
        $tag = Get-SpdSetting $EnvFile 'SPD_IMAGE_TAG' '20261020'
        if ($project -notmatch '^[a-z0-9][a-z0-9_-]*$') { throw 'Invalid Compose project name.' }
        $volume="$project-ollama-models"
        Invoke-SpdDocker volume create $volume | Out-Null
        Invoke-SpdDocker run --rm --user 0 --entrypoint /bin/sh --mount "type=volume,src=$volume,dst=/models" --mount "type=bind,src=$PSScriptRoot,dst=/bundle,readonly" "hospital-spd/backend:$tag" -ec 'if [ -z "$(ls -A /models)" ]; then tar -xf /bundle/models.tar -C /models; fi'
    }
}
$up=@('up','-d','--wait','--wait-timeout',"$WaitTimeout")
if ($offline) { $up+=@('--no-build','--pull','never') } elseif ($NoBuild) { $up+='--no-build' } else { $up+='--build' }
Invoke-SpdDocker @composeArgs @up
Invoke-SpdDocker @composeArgs exec -T frontend wget -q -O - http://127.0.0.1/api/health
$port=Get-SpdSetting $EnvFile 'SPD_HTTP_PORT' '1820'
Write-Host "`nSPD: http://localhost:$port ; Mobile H5: http://localhost:$port/mobile/"
Write-Host "Initial account: admin. Initial password is SPD_INITIAL_ADMIN_PASSWORD in $EnvFile (never printed)."
Write-Host 'Repeat startup reuses the same volumes and administrator-managed password. Do not regenerate .env.'
