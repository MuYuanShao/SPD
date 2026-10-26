param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GradleHome = '',
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'
$mobileRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Set JavaHome to JDK 21.' }
if (-not $AndroidSdk -or -not (Test-Path (Join-Path $AndroidSdk 'platforms\android-35'))) { throw 'Install Android SDK API 35 and set AndroidSdk.' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $mobileRoot 'output\apk' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:CI = 'true'
Push-Location $mobileRoot
try {
    & npm.cmd ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Server configuration tests failed' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Web build failed' }
    & node_modules\.bin\cap.cmd sync android
    if ($LASTEXITCODE -ne 0) { throw 'Capacitor sync failed' }
    Push-Location android
    try {
        $gradle = if ($GradleHome) { Join-Path $GradleHome 'bin\gradle.bat' } else { '.\gradlew.bat' }
        & $gradle --no-daemon assembleDebug
        if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
    } finally { Pop-Location }
    $apk = Join-Path $mobileRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
    $signer = Join-Path $AndroidSdk 'build-tools\35.0.0\apksigner.bat'
    & $signer verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $target = Join-Path $OutputDirectory 'spd-mobile-1.3.6-test.1.apk'
    Copy-Item -LiteralPath $apk -Destination $target
    Get-FileHash -LiteralPath $target -Algorithm SHA256
} finally { Pop-Location }
