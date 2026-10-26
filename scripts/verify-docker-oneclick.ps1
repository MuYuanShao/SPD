param([string]$OutputDirectory = '')
$ErrorActionPreference = 'Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $repoRoot 'deploy/docker/common.ps1')
if (-not $OutputDirectory) { $OutputDirectory=Join-Path $repoRoot ('output/docker-deployment/fixtures-'+[guid]::NewGuid().ToString('N')) }
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$results=@()
function Check([string]$Name,[bool]$Condition) {
    if (-not $Condition) { throw "Verification failed: $Name" }
    $script:results += [pscustomobject]@{name=$Name;passed=$true}
    Write-Host "PASS $Name"
}
$envPath=Join-Path $OutputDirectory 'private.env'
Initialize-SpdEnvironment $envPath
$hash=(Get-FileHash -LiteralPath $envPath).Hash
Initialize-SpdEnvironment $envPath
Check 'initialization-preserves-existing-settings' ((Get-FileHash -LiteralPath $envPath).Hash -eq $hash)
Check 'database-secret-generated' ((Get-SpdSetting $envPath 'SPD_DB_PASSWORD' '').Length -ge 16)
Check 'jwt-secret-generated' ((Get-SpdSetting $envPath 'SPD_JWT_SECRET' '').Length -ge 48)
Check 'administrator-secret-generated' ((Get-SpdSetting $envPath 'SPD_INITIAL_ADMIN_PASSWORD' '').Length -ge 16)
Check 'separate-database-credentials' ((Get-SpdSetting $envPath 'SPD_DB_PASSWORD' '') -ne (Get-SpdSetting $envPath 'SPD_FLYWAY_PASSWORD' ''))
$composeArgs=@('compose','--env-file',$envPath,'-f',(Join-Path $repoRoot 'deploy/docker-compose.yml'))
Assert-SpdCompose $composeArgs
$raw=& docker @composeArgs config --format json
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Compose model.' }
$cfg=$raw | ConvertFrom-Json
Check 'database-not-published' (-not $cfg.services.mysql.ports)
Check 'backend-not-published' (-not $cfg.services.backend.ports)
Check 'forwarded-host-and-port-restored-for-same-origin' ($cfg.services.backend.environment.SERVER_FORWARD_HEADERS_STRATEGY -eq 'framework')
Check 'migration-gated-by-database-health' ($cfg.services.migrate.depends_on.mysql.condition -eq 'service_healthy')
Check 'bootstrap-gated-by-migration' ($cfg.services.bootstrap.depends_on.migrate.condition -eq 'service_completed_successfully')
Check 'runtime-gated-by-bootstrap' ($cfg.services.backend.depends_on.bootstrap.condition -eq 'service_completed_successfully')
Check 'web-gated-by-backend-health' ($cfg.services.frontend.depends_on.backend.condition -eq 'service_healthy')
Check 'runtime-disables-automatic-ddl' ($cfg.services.backend.environment.SPRING_FLYWAY_ENABLED -eq 'false' -and $cfg.services.backend.environment.SPD_REQUIRE_NO_DDL -eq 'true')
Check 'runtime-does-not-receive-migration-account' ($cfg.services.backend.environment.SPD_FLYWAY_USERNAME -ne $cfg.services.migrate.environment.SPD_FLYWAY_USERNAME)
Check 'attachments-have-persistent-volume' (@($cfg.services.backend.volumes | Where-Object { $_.target -eq '/app/output' }).Count -eq 1)
$coreImages=@(& docker @composeArgs config --images | Sort-Object -Unique)
Check 'core-image-bundle-has-exactly-three-images' ($coreImages.Count -eq 3)
$aiArgs=$composeArgs+@('--profile','ai')
Assert-SpdCompose $aiArgs
$aiImages=@(& docker @aiArgs config --images | Sort-Object -Unique)
Check 'ai-profile-includes-model-server-image' ($aiImages.Count -eq 4 -and @($aiImages | Where-Object { $_ -match 'ollama/' }).Count -eq 1)
# Checksum protection is tested without inventing or loading a Docker image archive.
$bundle=Join-Path $OutputDirectory 'checksum-fixture'
[IO.Directory]::CreateDirectory($bundle) | Out-Null
$payload=Join-Path $bundle 'payload.txt'
[IO.File]::WriteAllText($payload,'trusted fixture',[Text.UTF8Encoding]::new($false))
$manifest=Join-Path $bundle 'SHA256SUMS'
[IO.File]::WriteAllText($manifest,((Get-FileHash -LiteralPath $payload).Hash.ToLowerInvariant())+'  payload.txt'+"`n",[Text.UTF8Encoding]::new($false))
Assert-SpdBundle $bundle
Check 'valid-bundle-checksum-accepted' $true
[IO.File]::WriteAllText($payload,'tampered fixture',[Text.UTF8Encoding]::new($false))
$rejected=$false
try { Assert-SpdBundle $bundle } catch { $rejected=$_.Exception.Message -match 'checksum failed' }
Check 'tampered-bundle-rejected' $rejected
[IO.File]::WriteAllText($manifest,('0'*64)+'  ../outside.txt'+"`n",[Text.UTF8Encoding]::new($false))
$rejected=$false
try { Assert-SpdBundle $bundle } catch { $rejected=$_.Exception.Message -match 'Invalid path' }
Check 'bundle-path-traversal-rejected' $rejected
foreach ($file in @('deploy/start.ps1','deploy/package.ps1','deploy/docker/common.ps1')) {
    $errors=$null
    [Management.Automation.Language.Parser]::ParseFile((Join-Path $repoRoot $file),[ref]$null,[ref]$errors) | Out-Null
    Check "syntax-$file" (-not $errors)
}
$report=[ordered]@{generatedAt=[DateTime]::UtcNow.ToString('o');scope='configuration-and-script-fixtures';containerBuildExecuted=$false;passed=$true;checks=$results}
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'report.json'),($report|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
Write-Host "Deployment file checks passed: $($results.Count). No containers started and no business data changed."
