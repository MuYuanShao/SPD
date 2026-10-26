param([switch]$WithAi, [string]$EnvFile = '', [string]$OutputDirectory = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'docker/common.ps1')
if (-not $EnvFile) { $EnvFile=Join-Path $PSScriptRoot '.env' }
$EnvFile=[IO.Path]::GetFullPath($EnvFile)
Initialize-SpdEnvironment $EnvFile
Assert-SpdDocker
$composeArgs=@('compose','--env-file',$EnvFile,'-f',(Join-Path $PSScriptRoot 'docker-compose.yml'))
if ($WithAi) { $composeArgs+=@('--profile','ai') }
Assert-SpdCompose $composeArgs
Invoke-SpdDocker @composeArgs build mysql backend frontend
$tag=Get-SpdSetting $EnvFile 'SPD_IMAGE_TAG' '20261020'
$project=Get-SpdSetting $EnvFile 'COMPOSE_PROJECT_NAME' 'hospital-spd'
if ($WithAi) {
    Invoke-SpdDocker @composeArgs up -d --wait --wait-timeout 600 ollama
    Invoke-SpdDocker @composeArgs run --rm ollama-init
}
if (-not $OutputDirectory) { $OutputDirectory=Join-Path $SpdDeployRoot "../output/docker-bundle/spd-$tag-$([DateTime]::Now.ToString('yyyyMMdd-HHmmss'))" }
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Output directory already exists; choose a new directory to preserve old bundles.' }
[IO.Directory]::CreateDirectory((Join-Path $OutputDirectory 'docker')) | Out-Null
foreach ($name in @('start.ps1','start.sh','start.bat','docker-compose.yml','.env.production.example','README.docker.md')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $OutputDirectory }
foreach ($common in @('common.ps1','common.sh')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot "docker/$common") -Destination (Join-Path $OutputDirectory 'docker') }
$images=@(& docker @composeArgs config --images | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0 -or $images.Count -eq 0) { throw 'Cannot resolve image list.' }
Invoke-SpdDocker image save --output (Join-Path $OutputDirectory 'images.tar') @images
if ($WithAi) {
    Invoke-SpdDocker run --rm --user 0 --entrypoint tar --mount "type=volume,src=$project-ollama-models,dst=/models,readonly" --mount "type=bind,src=$OutputDirectory,dst=/bundle" "hospital-spd/backend:$tag" -cf /bundle/models.tar -C /models .
}
if ($WithAi) {
    $license=& docker @composeArgs exec -T ollama ollama show --license (Get-SpdSetting $EnvFile 'SPD_AI_MODEL' 'qwen3-vl:8b')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot capture the model license.' }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'model-license.txt'),($license -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
}
$metadata=& docker image inspect @images --format '{{json .}}'
if ($LASTEXITCODE -ne 0) { throw 'Cannot capture image identity.' }
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'images-metadata.jsonl'),($metadata -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
$lines=@(Get-ChildItem -LiteralPath $OutputDirectory -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative=$_.FullName.Substring($OutputDirectory.Length+1).Replace('\','/')
    ((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())+'  '+$relative
})
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'SHA256SUMS'),($lines -join "`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Host "Complete Docker image bundle: $OutputDirectory"
Write-Host 'Copy the whole directory to the target host. Run start.bat / start.sh; no Java, Node, Maven or source download is needed.'
