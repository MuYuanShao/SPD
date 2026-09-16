param([int]$Port = 13316)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$mysqlBase = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlBase 'bin\mysqld.exe'
$mysql = Join-Path $mysqlBase 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlBase 'bin\mysqladmin.exe'
$runtime = Join-Path $repo ('output\mobile-mysql-' + [guid]::NewGuid().ToString('N'))
$data = Join-Path $runtime 'data'
New-Item -ItemType Directory -Path $data -Force | Out-Null
$testPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$server = $null
$testExitCode = 1
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Test port $Port is occupied. Choose another isolated port."
}
try {
    & $mysqld --no-defaults "--basedir=$mysqlBase" "--datadir=$data" --initialize-insecure "--log-error=$runtime\initialize.log"
    if ($LASTEXITCODE -ne 0) { throw "Isolated MySQL initialization failed: $runtime" }
    $args = @('--no-defaults', "--basedir=`"$mysqlBase`"", "--datadir=`"$data`"", '--bind-address=127.0.0.1',
        "--port=$Port", '--mysqlx=OFF', '--skip-log-bin', '--secure-file-priv=NULL', '--persisted-globals-load=OFF',
        "--log-error=`"$runtime\server.log`"")
    $server = Start-Process -FilePath $mysqld -ArgumentList $args -WindowStyle Hidden -PassThru
    $env:MYSQL_PWD = ''
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $server.Refresh()
        if ($server.HasExited) { throw "Isolated MySQL exited: $runtime\server.log" }
        & $mysqladmin --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Isolated MySQL startup timed out' }
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop
    foreach ($binding in $listener) {
        $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($binding.OwningProcess)"
        if (($owner.ProcessId -ne $server.Id -and $owner.ParentProcessId -ne $server.Id) -or
            $owner.ExecutablePath -ne $mysqld -or -not $owner.CommandLine.Contains($data)) {
            throw 'Test listener belongs to a different process. Refusing connection.'
        }
    }
    "ALTER USER 'root'@'localhost' IDENTIFIED BY '$testPassword';" |
        & $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL password initialization failed' }
    $env:MYSQL_PWD = $testPassword
    $env:SPD_MYSQL_INTEGRATION_TESTS = 'true'
    $env:SPD_TEST_MYSQL_URL = "jdbc:mysql://127.0.0.1:$Port/"
    $env:SPD_TEST_MYSQL_USERNAME = 'root'
    $env:SPD_TEST_MYSQL_PASSWORD = $testPassword
    $env:SPD_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    Push-Location (Join-Path $repo 'backend')
    try {
        # Windows PowerShell treats native stderr warnings as errors under Stop.
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\paangjuk\.m2\repository' '-Dtest=MobileMysqlAcceptanceTest' test *> (Join-Path $runtime 'acceptance.log')
        $testExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop'; Pop-Location }
    Get-Content (Join-Path $runtime 'acceptance.log') -Tail 26
    Write-Host "Acceptance log: $runtime\acceptance.log"
} finally {
    if ($null -ne $server) {
        $owned = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
            $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($_.OwningProcess)"
            if ($owner.ExecutablePath -eq $mysqld -and $owner.CommandLine -and $owner.CommandLine.Contains($data)) { $owner }
        })
        if ($owned.Count -gt 0) { & $mysqladmin --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root shutdown 2>$null }
        $server.Refresh()
        if (-not $server.HasExited) {
            if (-not $server.WaitForExit(10000)) { Stop-Process -Id $server.Id -Force }
        }
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item Env:SPD_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
}
exit $testExitCode
