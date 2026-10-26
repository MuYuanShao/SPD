param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$OutputDirectory = 'D:\SpdApp\output\SPD-Mobile-OrderPicking-1.3.6'
)
$ErrorActionPreference = 'Stop'
$mobileRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $mobileRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
$buildTools = Join-Path $AndroidSdk 'build-tools\35.0.0'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build the APK first.' }
$signature = & (Join-Path $buildTools 'apksigner.bat') verify --min-sdk-version 28 --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
& (Join-Path $buildTools 'zipalign.exe') -c -P 16 4 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed.' }
$previousApk = Join-Path (Split-Path -Parent $OutputDirectory) 'SPD-Mobile-Test-1.0.0\spd-mobile-1.0.0-test.1.apk'
if (Test-Path -LiteralPath $previousApk) {
    $previousSignature = & (Join-Path $buildTools 'apksigner.bat') verify --print-certs $previousApk
    if ($LASTEXITCODE -ne 0) { throw 'Previous APK signature invalid.' }
    $oldDigest = @($previousSignature | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' })
    $newDigest = @($signature | Where-Object { $_ -match '^Signer #1 certificate SHA-256 digest:' })
    if ($oldDigest.Count -ne 1 -or $newDigest.Count -ne 1 -or $oldDigest[0] -ne $newDigest[0]) {
        throw 'Signing certificate changed. Do not distribute as an upgrade.'
    }
}
$badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'APK manifest inspection failed.' }
$description = $badging -join "`n"
if ($description -notmatch "name='com.hospital.spd.mobile.test'" -or
    $description -notmatch "sdkVersion:'28'" -or $description -notmatch "targetSdkVersion:'35'" -or
    $description -notmatch "versionName='1.3.6-test.1'" -or $description -notmatch "versionCode='11'") {
    throw 'Unexpected app id or Android SDK requirement.'
}
$network = & (Join-Path $buildTools 'aapt.exe') dump xmltree $apk 'res/xml/network_security_config.xml'
if ($LASTEXITCODE -ne 0 -or ($network -join "`n") -notmatch '172\.16\.2\.153') { throw 'Test host network policy missing.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $configEntry = $archive.GetEntry('assets/capacitor.config.json')
    if (-not $configEntry) { throw 'Capacitor configuration missing.' }
    $reader = [IO.StreamReader]::new($configEntry.Open())
    try { $config = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if ($config.appId -ne 'com.hospital.spd.mobile.test' -or -not $config.plugins.CapacitorHttp.enabled) { throw 'Native HTTP configuration mismatch.' }
    if ($config.android.minWebViewVersion -ne 66 -or $config.server.errorPath -ne 'webview-error.html') { throw 'Legacy WebView configuration missing.' }
    $nativeJournal = $false
    foreach ($entry in $archive.Entries | Where-Object { $_.Name -match '^classes[0-9]*\.dex$' }) {
        $memory = [IO.MemoryStream]::new()
        $stream = $entry.Open()
        try {
            $stream.CopyTo($memory)
            if ([Text.Encoding]::ASCII.GetString($memory.ToArray()).Contains('SpdSecureStorePlugin')) { $nativeJournal = $true }
        } finally { $stream.Dispose(); $memory.Dispose() }
    }
    if (-not $nativeJournal) { throw 'Native encrypted signing journal is missing.' }
    foreach ($file in Get-ChildItem (Join-Path $mobileRoot 'dist') -Recurse -File) {
        $relative = $file.FullName.Substring((Join-Path $mobileRoot 'dist').Length + 1).Replace('\', '/')
        $entry = $archive.GetEntry('assets/public/' + $relative)
        if (-not $entry) { throw "Bundled asset missing: $relative" }
        if ($relative.EndsWith('.js')) {
            $reader = [IO.StreamReader]::new($entry.Open())
            try { if ($reader.ReadToEnd().Contains('spd-dev-signing:')) { throw 'Development-only journal found in production assets.' } }
            finally { $reader.Dispose() }
        }
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $packedHash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose(); $sha.Dispose() }
        if ($packedHash -ne (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) { throw "Stale APK asset: $relative" }
    }
} finally { $archive.Dispose() }

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$name = 'spd-mobile-1.3.6-test.1.apk'
Copy-Item -LiteralPath $apk -Destination (Join-Path $OutputDirectory $name)
$verification = @('APK static verification PASS', ('Verified at: ' + (Get-Date -Format o)),
    'Signature valid on API 28, package name, Android 9 minimum, target SDK 35, B-group test host and bundled web assets verified.',
    'Native encrypted signing-journal plugin is present; browser development journal is absent.',
    'Physical-device installation and native API requests have not been verified.', '', '--- Signing certificate ---')
$verification += $signature
$verification += @('', '--- Manifest ---')
$verification += ($badging | Where-Object { $_ -match '^(package:|sdkVersion:|targetSdkVersion:|application-label:|launchable-activity:|uses-permission:)' })
$verification += @('', '--- Network policy ---')
$verification += $network
[IO.File]::WriteAllLines((Join-Path $OutputDirectory 'verification.txt'), $verification, [Text.UTF8Encoding]::new($false))
$hashes = Get-ChildItem -LiteralPath $OutputDirectory -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } |
    Sort-Object Name | ForEach-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name }
[IO.File]::WriteAllLines((Join-Path $OutputDirectory 'SHA256SUMS.txt'), $hashes, [Text.UTF8Encoding]::new($false))
$zip = $OutputDirectory + '.zip'
Compress-Archive -LiteralPath $OutputDirectory -DestinationPath $zip -Force
$checkZip = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    $entry = $checkZip.Entries | Where-Object { $_.Name -eq $name } | Select-Object -First 1
    if (-not $entry) { throw 'Delivery ZIP does not contain APK.' }
    $stream = $entry.Open(); $sha = [Security.Cryptography.SHA256]::Create()
    try { $zipApkHash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $sha.Dispose() }
    if ($zipApkHash -ne (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash) { throw 'Delivery ZIP APK mismatch.' }
} finally { $checkZip.Dispose() }
Get-Item -LiteralPath (Join-Path $OutputDirectory $name), $zip | Select-Object FullName, Length
Get-FileHash -LiteralPath $zip -Algorithm SHA256
