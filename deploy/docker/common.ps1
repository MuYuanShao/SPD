$ErrorActionPreference = 'Stop'
$SpdDeployRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
function New-SpdSecret([int]$Bytes = 24) {
    $data = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($data) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($data)).Replace('-', '').ToLowerInvariant()
}
function Initialize-SpdEnvironment([string]$Path) {
    if (Test-Path -LiteralPath $Path) { return }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path)) | Out-Null
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $stream.Dispose()
    if ($env:OS -eq 'Windows_NT') {
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        & icacls.exe $Path /inheritance:r /grant:r "*$($sid):F" '*S-1-5-18:F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot protect the private deployment environment file.' }
    }
    $values = @(
        '# Generated once. Keep private. Reuse this file with the same data volumes.',
        'COMPOSE_PROJECT_NAME=hospital-spd', 'SPD_IMAGE_TAG=20261020', 'SPD_HTTP_PORT=1820',
        'SPD_DB_NAME=ISPD', 'SPD_DB_USERNAME=spd_runtime', "SPD_DB_PASSWORD=$(New-SpdSecret)",
        "SPD_DB_ROOT_PASSWORD=$(New-SpdSecret)", 'SPD_FLYWAY_USERNAME=spd_migrate', "SPD_FLYWAY_PASSWORD=$(New-SpdSecret)",
        "SPD_JWT_SECRET=$(New-SpdSecret 48)", "SPD_INITIAL_ADMIN_PASSWORD=$(New-SpdSecret 16)",
        'SPD_MOBILE_ENABLED=true', "SPD_MOBILE_SERVER_ID=$([guid]::NewGuid().ToString('N'))", "SPD_MOBILE_REVIEW_SECRET=$(New-SpdSecret 32)",
        'SPD_AI_MODEL=qwen3-vl:8b', 'SPD_AI_BASE_URL=http://ollama:11434', 'SPD_MCP_ENABLED=false', 'SPD_MCP_UPLOAD_ENABLED=false'
    )
    [IO.File]::WriteAllText($Path, ($values -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
    Write-Host "Private deployment settings created: $Path"
}
function Get-SpdSetting([string]$Path, [string]$Name, [string]$Default) {
    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if ($processValue) { return $processValue }
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        if ($line -match ('^' + [regex]::Escape($Name) + '=(.*)$')) { return $Matches[1].Trim().Trim('"').Trim("'") }
    }
    return $Default
}
function Invoke-SpdDocker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed (exit $LASTEXITCODE). Existing volumes were retained." }
}
function Assert-SpdDocker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install Docker Engine/Desktop and Compose >= 2.24.4 first.' }
    & docker info --format '{{.OSType}}' > $null 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Docker Linux engine is not ready. Start Docker Desktop (Linux containers) or Docker Engine, then retry.' }
    $kind = & docker info --format '{{.OSType}}'
    if ($kind -ne 'linux') { throw 'Switch Docker Desktop to Linux containers.' }
}
function Assert-SpdCompose([string[]]$ComposeArgs) {
    Invoke-SpdDocker @ComposeArgs config --quiet
    # Parse in memory only; never emit a resolved Compose document containing secrets.
    $raw = & docker @ComposeArgs config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve deployment settings.' }
    $cfg = $raw | ConvertFrom-Json
    $envs = $cfg.services.backend.environment
    $secrets = @($envs.SPD_DB_PASSWORD, $envs.SPD_JWT_SECRET, $cfg.services.mysql.environment.MYSQL_ROOT_PASSWORD,
        $cfg.services.migrate.environment.SPD_FLYWAY_PASSWORD, $cfg.services.bootstrap.environment.SPD_INITIAL_ADMIN_PASSWORD)
    $minimums = @(16, 48, 16, 16, 16)
    for ($i=0; $i -lt $secrets.Count; $i++) {
        if ([string]::IsNullOrWhiteSpace($secrets[$i]) -or $secrets[$i].Length -lt $minimums[$i] -or
            $secrets[$i] -match '(?i)replace|change-me|^admin123$|^root123$|^password$') { throw 'Deployment credentials are missing, placeholders, or too short. Update the private .env file.' }
    }
    if ($cfg.services.mysql.environment.MYSQL_USER -eq $envs.SPD_DB_USERNAME -or $envs.SPD_DB_USERNAME -eq 'root' -or $cfg.services.mysql.environment.MYSQL_USER -eq 'root') { throw 'Use separate migration/runtime/root accounts.' }
    if ($secrets[0] -eq $secrets[3]) { throw 'Database runtime and migration passwords must be different.' }
    if ([Text.Encoding]::UTF8.GetByteCount($secrets[4]) -gt 72) { throw 'Initial administrator password must not exceed 72 UTF-8 bytes.' }
}
function Assert-SpdBundle([string]$Directory) {
    $manifest = Join-Path $Directory 'SHA256SUMS'
    if (-not (Test-Path -LiteralPath $manifest)) { throw 'Offline bundle is missing SHA256SUMS.' }
    foreach ($line in [IO.File]::ReadAllLines($manifest)) {
        if ($line -notmatch '^([a-fA-F0-9]{64})  (.+)$') { throw 'Invalid bundle checksum manifest.' }
        $hash=$Matches[1]; $relative=$Matches[2]
        $target=[IO.Path]::GetFullPath((Join-Path $Directory $relative))
        $prefix=[IO.Path]::GetFullPath($Directory).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
        if (-not $target.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid path in bundle manifest.' }
        if (-not (Test-Path -LiteralPath $target) -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ine $hash) { throw "Bundle checksum failed: $relative" }
    }
}
