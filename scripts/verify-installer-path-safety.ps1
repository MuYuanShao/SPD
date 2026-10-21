$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. ([scriptblock]::Create((Get-Content -LiteralPath (Join-Path $repoRoot 'deploy/windows-offline/installer/common.ps1') -Raw -Encoding UTF8)))
$fixture = Join-Path $repoRoot ('output/installer-path-safety/' + [Guid]::NewGuid().ToString('N'))
$source = Join-Path $fixture 'bundle'
$target = Join-Path $fixture 'installed'
$required = @('app/app.jar', 'app/web/index.html', 'runtime/jdk-17/bin/java.exe',
    'service/HospitalSPD.exe', 'service/HospitalSPD.xml', 'installer/common.ps1',
    'installer/install.ps1', 'installer/mysql.ps1', 'licenses/WinSW-LICENSE.txt', 'VERSION.txt')
foreach ($relative in $required) {
    $path = Join-Path $source $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, 'fixture')
}
$rejections = 0
foreach ($candidate in @($source, ($source.ToUpperInvariant() + '\'), (Join-Path $source 'nested'), $fixture)) {
    try { Assert-SpdInstallSource $source $candidate }
    catch { $rejections++; continue }
    throw 'Overlapping installation source was accepted'
}
Assert-SpdInstallSource $source $target
try { Assert-SpdInstallSource (Join-Path $fixture 'missing') $target }
catch { $rejections++ }
if ($rejections -ne 5 -or [IO.File]::ReadAllText((Join-Path $source 'app/app.jar')) -ne 'fixture') {
    throw 'Installer path guard regression failed'
}
Write-Output 'PASS: same path, case variant, nested paths and incomplete package rejected; source preserved.'
