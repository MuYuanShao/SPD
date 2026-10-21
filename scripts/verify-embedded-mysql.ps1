param([string]$BundleRoot = 'output/offline-bundle/spd-server', [switch]$ReviewRegression)
$ErrorActionPreference = 'Stop'
$ReviewRegression = $ReviewRegression -or $env:SPD_REVIEW_REGRESSION -eq '1'
$sourceScriptPath = if ($env:SPD_INVOKED_SCRIPT_PATH) { $env:SPD_INVOKED_SCRIPT_PATH } else { $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent (Split-Path -Parent $sourceScriptPath)
$BundleRoot = if ([IO.Path]::IsPathRooted($BundleRoot)) { [IO.Path]::GetFullPath($BundleRoot) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $BundleRoot)) }
. ([scriptblock]::Create((Get-Content (Join-Path $BundleRoot 'installer/common.ps1') -Raw -Encoding UTF8)))
. ([scriptblock]::Create((Get-Content (Join-Path $BundleRoot 'installer/mysql.ps1') -Raw -Encoding UTF8)))

# Only ACL setup is adapted for this unprivileged, foreground fixture; services and live databases are untouched.
function Protect-SpdDataDirectory([string]$DataDir) {
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $DataDir /inheritance:r /grant:r "*$($sid):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Fixture directory ACL failed' }
}
function Write-SpdPrivateFile([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls.exe $Path /inheritance:r /grant:r "*$($sid):F" '*S-1-5-18:F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Fixture file ACL failed' }
}
function New-FixturePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop(); return $port
}
function Assert-Fixture([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Assert-FixtureReject([scriptblock]$Action, [string]$Expected) {
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message.Contains($Expected)) { return }
        throw
    }
    throw "Expected rejection: $Expected"
}

$testRoot = Join-Path $repoRoot ('output/embedded-mysql-acceptance/' + [Guid]::NewGuid().ToString('N'))
$dataDir = Join-Path $testRoot 'hospital data'; New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$port = New-FixturePort; $apiPort = New-FixturePort
$mysqlProcess = $null; $app = $null; $appOutput = $null; $appErrors = $null
$mysql = Join-Path $dataDir 'mysql/server/bin/mysql.exe'
$java = Join-Path $BundleRoot 'runtime/jdk-17/bin/java.exe'; $jar = Join-Path $BundleRoot 'app/app.jar'
$checks = [Collections.Generic.List[string]]::new()
try {
    $db = Initialize-SpdEmbeddedMysql -BundleRoot $BundleRoot -DataDir $dataDir -Port $port -DbName 'SPD_ACCEPTANCE' -SkipServiceInstall -SkipPrerequisiteInstall
    $mysqlProcess = $db.Process
    $checks.Add('fresh-initialization-with-space-in-path')
    $credentials = Get-Content $db.CredentialsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $dbUrl = "jdbc:mysql://127.0.0.1:$port/SPD_ACCEPTANCE?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    $environment = @{ SPD_DB_URL = $dbUrl; SPD_FLYWAY_USERNAME = $db.FlywayUser; SPD_FLYWAY_PASSWORD = $db.FlywayPassword }
    $migration = Invoke-SpdDatabaseCommand -JavaExe $java -JarPath $jar -Command migrate -Environment $environment
    Assert-Fixture ($migration.ExitCode -eq 0) $migration.Output
    $checks.Add('fresh-flyway-migration')
    $lastVersion = Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword 'SELECT MAX(CAST(version AS UNSIGNED)) FROM SPD_ACCEPTANCE.flyway_schema_history WHERE success=1;'
    Assert-Fixture ($lastVersion -eq (Get-Content (Join-Path $BundleRoot 'MIGRATION_VERSION.txt') -Raw).Trim().Substring(1)) 'Migration version mismatch'
    $checks.Add('latest-migration-version')
    $runtimeEnvironment = @{ SPD_DB_URL = $dbUrl; SPD_FLYWAY_USERNAME = $db.RuntimeUser; SPD_FLYWAY_PASSWORD = $db.RuntimePassword; SPD_REQUIRE_NO_DDL = 'true' }
    $validation = Invoke-SpdDatabaseCommand -JavaExe $java -JarPath $jar -Command validate -Environment $runtimeEnvironment
    Assert-Fixture ($validation.ExitCode -eq 0) $validation.Output
    $checks.Add('runtime-account-no-ddl')
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "CREATE ROLE 'review_ddl', 'review_parent'; GRANT CREATE, ALTER, DROP ON SPD_ACCEPTANCE.* TO 'review_ddl'; GRANT 'review_ddl' TO 'review_parent'; GRANT 'review_parent' TO 'spd_runtime'@'localhost'; SET DEFAULT ROLE 'review_parent' TO 'spd_runtime'@'localhost';" | Out-Null
    $roleValidation = Invoke-SpdDatabaseCommand -JavaExe $java -JarPath $jar -Command validate -Environment $runtimeEnvironment
    Assert-Fixture ($roleValidation.ExitCode -eq 6) 'Inherited role DDL was not rejected'
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "REVOKE 'review_parent' FROM 'spd_runtime'@'localhost'; DROP ROLE 'review_parent', 'review_ddl'; CREATE ROLE 'review_dml'; GRANT SELECT ON SPD_ACCEPTANCE.* TO 'review_dml'; GRANT 'review_dml' TO 'spd_runtime'@'localhost'; SET DEFAULT ROLE 'review_dml' TO 'spd_runtime'@'localhost';" | Out-Null
    $roleValidation = Invoke-SpdDatabaseCommand -JavaExe $java -JarPath $jar -Command validate -Environment $runtimeEnvironment
    Assert-Fixture ($roleValidation.ExitCode -eq 0) 'DML-only role was incorrectly rejected'
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "REVOKE 'review_dml' FROM 'spd_runtime'@'localhost'; DROP ROLE 'review_dml';" | Out-Null
    $checks.Add('nested-role-ddl-rejected-and-dml-role-accepted')
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword 'CREATE TABLE SPD_ACCEPTANCE.deployment_marker(id INT PRIMARY KEY);' | Out-Null
    Invoke-SpdMysqlSql $mysql $port $db.RuntimeUser $db.RuntimePassword 'INSERT INTO SPD_ACCEPTANCE.deployment_marker VALUES (42);' | Out-Null
    Assert-FixtureReject { Invoke-SpdMysqlSql $mysql $port $db.RuntimeUser $db.RuntimePassword 'CREATE TABLE SPD_ACCEPTANCE.forbidden(id INT);' } '配置失败'
    $checks.Add('runtime-dml-allowed-ddl-denied')
    $triggers = Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='SPD_ACCEPTANCE';"
    Assert-Fixture ([int]$triggers -gt 0) 'Expected migration triggers'
    $checks.Add('triggers-created')
    $environment.SPD_INITIAL_ADMIN_PASSWORD = $db.InitialAdminPassword
    $admin = Invoke-SpdDatabaseCommand -JavaExe $java -JarPath $jar -Command bootstrap-admin -Environment $environment
    Assert-Fixture ($admin.ExitCode -eq 0) $admin.Output
    $checks.Add('admin-bootstrap')
    $demoAccounts = Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "SELECT COUNT(*) FROM SPD_ACCEPTANCE.sys_user WHERE username IN ('operator01','nurse01') AND status=1;"
    Assert-Fixture ($demoAccounts -eq '0') 'Default demo accounts remain enabled'
    $checks.Add('default-demo-accounts-disabled')

    if ($ReviewRegression) {
        $savedReviewEnv = @{}
        foreach ($name in @('SPD_REVIEW_MYSQL_TESTS', 'SPD_REVIEW_JDBC_URL', 'SPD_REVIEW_ROOT_PASSWORD')) {
            $savedReviewEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        }
        try {
            $env:SPD_REVIEW_MYSQL_TESTS = 'true'; $env:SPD_REVIEW_JDBC_URL = $dbUrl; $env:SPD_REVIEW_ROOT_PASSWORD = $credentials.RootPassword
            Push-Location (Join-Path $repoRoot 'backend')
            try { & .\mvnw.cmd '-Dtest=ReviewRegressionMysqlTest' test; if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL review regressions failed' } }
            finally { Pop-Location }
        } finally {
            foreach ($name in $savedReviewEnv.Keys) { [Environment]::SetEnvironmentVariable($name, $savedReviewEnv[$name], 'Process') }
        }
        $checks.Add('real-mysql-settlement-race-and-package-code-identity')
    }
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "INSERT INTO SPD_ACCEPTANCE.audit_log(operation_type,biz_type,remark) VALUES ('REVIEW_FIX','review_contract','review regression marker'),('REVIEW_OTHER','review_contract','other marker');" | Out-Null

    $info = [Diagnostics.ProcessStartInfo]::new(); $info.FileName = $java
    $webRoot = Join-Path $BundleRoot 'app/web'
    $info.Arguments = "-jar `"$jar`" --server.address=127.0.0.1 --server.port=$apiPort --spring.profiles.active=production --spring.flyway.enabled=false --spd.web.static-dir=`"$webRoot`""
    $info.WorkingDirectory = $testRoot; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($entry in @{ SPD_DB_URL = $dbUrl; SPD_DB_USERNAME = $db.RuntimeUser; SPD_DB_PASSWORD = $db.RuntimePassword; SPD_JWT_SECRET = (New-SpdJwtSecret) }.GetEnumerator()) { $info.EnvironmentVariables[$entry.Key] = [string]$entry.Value }
    $app = [Diagnostics.Process]::new(); $app.StartInfo = $info; $app.Start() | Out-Null
    $appOutput = $app.StandardOutput.ReadToEndAsync(); $appErrors = $app.StandardError.ReadToEndAsync()
    Assert-Fixture (Wait-SpdHealth -Port $apiPort) 'Production app health failed'
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:$apiPort/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{ username = 'admin'; password = $db.InitialAdminPassword } | ConvertTo-Json)
    Assert-Fixture ($login.code -eq 0 -and $login.data.token) 'Generated administrator login failed'
    $checks.Add('production-app-health-and-real-login')
    $headers = @{ Authorization = "Bearer $($login.data.token)" }
    $operations = Invoke-RestMethod -Uri "http://127.0.0.1:$apiPort/api/audit-logs/operation-types" -Headers $headers
    $businessTypes = Invoke-RestMethod -Uri "http://127.0.0.1:$apiPort/api/audit-logs/biz-types" -Headers $headers
    Assert-Fixture (@($operations.data.operationTypes | Where-Object { $_.operationType -eq 'REVIEW_FIX' }).Count -eq 1) 'Audit operation facet contract failed'
    Assert-Fixture (@($businessTypes.data.bizTypes | Where-Object { $_.bizType -eq 'review_contract' }).Count -eq 1) 'Audit business facet contract failed'
    $checks.Add('real-audit-facet-api-contract')
    if ($ReviewRegression) {
        $previousUrl = $env:SPD_REVIEW_APP_URL; $previousPassword = $env:SPD_REVIEW_ADMIN_PASSWORD
        try {
            $env:SPD_REVIEW_APP_URL = "http://127.0.0.1:$apiPort"; $env:SPD_REVIEW_ADMIN_PASSWORD = $db.InitialAdminPassword
            & node.exe (Join-Path $repoRoot 'scripts/verify-review-ui.mjs')
            if ($LASTEXITCODE -ne 0) { throw 'Browser audit filter regression failed' }
        } finally { $env:SPD_REVIEW_APP_URL = $previousUrl; $env:SPD_REVIEW_ADMIN_PASSWORD = $previousPassword }
        $checks.Add('chrome-audit-filters-with-real-database')
    }
    $app.Kill(); $app.WaitForExit()
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword 'SHUTDOWN;' | Out-Null
    Assert-Fixture ($mysqlProcess.WaitForExit(30000)) 'Fixture MySQL shutdown failed'
    $dbAgain = Initialize-SpdEmbeddedMysql -BundleRoot $BundleRoot -DataDir $dataDir -Port $port -DbName 'SPD_ACCEPTANCE' -SkipServiceInstall -SkipPrerequisiteInstall
    $mysqlProcess = $dbAgain.Process
    Assert-Fixture (-not $dbAgain.NewDatabase -and $dbAgain.RuntimePassword -eq $db.RuntimePassword -and $dbAgain.FlywayPassword -ne $db.FlywayPassword) 'Reinstall credential policy failed'
    $marker = Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword 'SELECT id FROM SPD_ACCEPTANCE.deployment_marker;'
    Assert-Fixture ($marker -eq '42') 'Reinstall lost data'
    $checks.Add('reinstall-preserves-data-and-runtime-password-rotates-migration-password')
    Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "UPDATE SPD_ACCEPTANCE.sys_user SET password='operator-managed-fixture' WHERE username='admin';" | Out-Null
    $environment.SPD_FLYWAY_PASSWORD = $dbAgain.FlywayPassword
    $adminAgain = Invoke-SpdDatabaseCommand -JavaExe $java -JarPath $jar -Command bootstrap-admin -Environment $environment
    Assert-Fixture ($adminAgain.ExitCode -eq 0) $adminAgain.Output
    $preserved = Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword "SELECT password FROM SPD_ACCEPTANCE.sys_user WHERE username='admin';"
    Assert-Fixture ($preserved -eq 'operator-managed-fixture') 'Reinstall reset a user-managed password'
    $checks.Add('user-managed-admin-password-preserved')
    Assert-FixtureReject { Initialize-SpdEmbeddedMysql -BundleRoot $BundleRoot -DataDir $dataDir -Port ($port + 1) -DbName 'SPD_ACCEPTANCE' -SkipServiceInstall -SkipPrerequisiteInstall } '须保持原配置'
    $unknown = Join-Path $testRoot 'unknown'; New-Item -ItemType Directory -Force -Path (Join-Path $unknown 'mysql') | Out-Null
    $keep = Join-Path $unknown 'mysql/keep.txt'; Set-Content $keep 'do not delete'
    Assert-FixtureReject { Initialize-SpdEmbeddedMysql -BundleRoot $BundleRoot -DataDir $unknown -Port (New-FixturePort) -DbName 'SPD_ACCEPTANCE' -SkipServiceInstall -SkipPrerequisiteInstall } '不会覆盖或清空'
    Assert-Fixture (Test-Path $keep) 'Unrecognized data directory was modified'
    $occupied = Join-Path $testRoot 'occupied'
    Assert-FixtureReject { Initialize-SpdEmbeddedMysql -BundleRoot $BundleRoot -DataDir $occupied -Port $port -DbName 'SPD_ACCEPTANCE' -SkipServiceInstall -SkipPrerequisiteInstall } '已被占用'
    Assert-Fixture (-not (Test-Path (Join-Path $occupied 'mysql'))) 'Port conflict created a database directory'
    $checks.Add('port-conflict-and-unrecognized-directory-and-config-drift-rejected')
    $credentials.Initialized = $false; Write-SpdPrivateFile $db.CredentialsPath ($credentials | ConvertTo-Json)
    Assert-FixtureReject { Initialize-SpdEmbeddedMysql -BundleRoot $BundleRoot -DataDir $dataDir -Port $port -DbName 'SPD_ACCEPTANCE' -SkipServiceInstall -SkipPrerequisiteInstall } '上次数据库初始化未完成'
    $checks.Add('partial-initialization-preserved-and-rejected')
    $report = @{ ok = $true; checks = $checks.ToArray(); migrationVersion = "V$lastVersion"; servicesInstalled = $false; aclMode = 'fixture-user-only'; liveDatabaseTouched = $false }
    $report | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $testRoot 'acceptance.json') -Encoding UTF8
    $report | ConvertTo-Json -Depth 4
} finally {
    if ($app) {
        if (-not $app.HasExited) { $app.Kill(); $app.WaitForExit() }
        if ($appOutput) { [IO.File]::WriteAllText((Join-Path $testRoot 'app.log'), $appOutput.GetAwaiter().GetResult()) }
        if ($appErrors) { [IO.File]::WriteAllText((Join-Path $testRoot 'app-error.log'), $appErrors.GetAwaiter().GetResult()) }
        $app.Dispose()
    }
    if ($mysqlProcess) {
        $mysqlProcess.Refresh()
        if (-not $mysqlProcess.HasExited) {
            try { Invoke-SpdMysqlSql $mysql $port root $credentials.RootPassword 'SHUTDOWN;' | Out-Null } catch { }
            if (-not $mysqlProcess.WaitForExit(10000)) { $mysqlProcess.Kill() }
        }
        $mysqlProcess.Dispose()
    }
    Write-Host "Acceptance evidence: $testRoot"
}
