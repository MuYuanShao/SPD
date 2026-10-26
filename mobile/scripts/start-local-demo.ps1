$ErrorActionPreference='Stop'
$mobileRoot=Split-Path -Parent $PSScriptRoot
$url='http://127.0.0.1:1831/?demo=1&scenario=multi'
function Test-Demo {
    try {
        $reply=Invoke-WebRequest -Uri 'http://127.0.0.1:1831/src/demoAdapter.ts' -UseBasicParsing -TimeoutSec 2
        return $reply.StatusCode -eq 200 -and $reply.Content.Contains('spd-local-demo-')
    } catch { return $false }
}
if(-not (Test-Demo)) {
    $node=Get-Command node.exe -ErrorAction SilentlyContinue
    if(-not $node){throw 'Node.js is required. Install Node.js 22 or later.'}
    if(-not (Test-Path -LiteralPath (Join-Path $mobileRoot 'node_modules/vite/bin/vite.js'))){throw "Dependencies missing. Run npm ci in $mobileRoot first."}
    $logs=Join-Path $mobileRoot 'output/local-demo'
    New-Item -ItemType Directory -Path $logs -Force|Out-Null
    $process=Start-Process -FilePath $node.Source -ArgumentList @('node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','1831','--strictPort') `
        -WorkingDirectory $mobileRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'server.log') `
        -RedirectStandardError (Join-Path $logs 'server-error.log') -PassThru
    $ready=$false
    for($attempt=0;$attempt -lt 15;$attempt++) {
        if(Test-Demo){$ready=$true;break}
        $process.Refresh()
        if($process.HasExited){throw "Demo server did not start. Port 1831 may be occupied. See $logs"}
        Start-Sleep -Milliseconds 300
    }
    if(-not $ready){throw "Demo startup timed out. See $logs"}
}
Write-Host 'Local demo only: no real SPD API or stock writes.'
Write-Host "Open: $url"
Write-Host 'Login: demo / demo123'
Start-Process $url
