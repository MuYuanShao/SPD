<#
  院内 SPD 离线一体化部署打包脚本
  将后端 jar、前端构建产物、内置 JDK17、数据库数据备份及一键启动脚本打包到 output/offline-bundle，
  实施人员拷贝整个目录即可一键部署，无需联网安装任何依赖。

  用法：
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-offline-deployment.ps1
    powershell ... -File scripts/package-offline-deployment.ps1 -SkipZip      # 不打 zip 包
    powershell ... -File scripts/package-offline-deployment.ps1 -SkipDbDump   # 不导出数据库数据
#>
param(
    [switch]$SkipZip,
    [switch]$SkipDbDump
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Test-Jdk17Home([string]$HomePath) {
    if (-not $HomePath -or -not (Test-Path (Join-Path $HomePath 'release'))) {
        return $false
    }
    $line = Select-String -Path (Join-Path $HomePath 'release') -Pattern 'JAVA_VERSION="(\d+)' |
        Select-Object -First 1
    if (-not $line) {
        return $false
    }
    return $line.Matches[0].Groups[1].Value -eq '17'
}

function Resolve-Jdk17Home {
    foreach ($candidate in @(
        $env:SPD_JAVA_HOME,
        $env:JAVA_HOME,
        (Join-Path $repoRoot '.tools\jdk-17'),
        (Join-Path $env:USERPROFILE '.jdks')
    )) {
        if (Test-Jdk17Home $candidate) {
            return $candidate
        }
    }

    $searchRoots = @(
        (Join-Path $repoRoot '.tools'),
        (Join-Path $env:USERPROFILE '.jdks'),
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Zulu',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Semeru'
    )
    foreach ($root in $searchRoots) {
        $hits = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match 'jdk-?17' } |
            Sort-Object Name -Descending
        foreach ($hit in $hits) {
            if (Test-Jdk17Home $hit.FullName) {
                return $hit.FullName
            }
        }
    }

    foreach ($drive in @('C:\', 'D:\')) {
        foreach ($repo in (Get-ChildItem $drive -Directory -ErrorAction SilentlyContinue)) {
            $tools = Join-Path $repo.FullName '.tools'
            $hits = Get-ChildItem $tools -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk-?17' } |
                Sort-Object Name -Descending
            foreach ($hit in $hits) {
                if (Test-Jdk17Home $hit.FullName) {
                    return $hit.FullName
                }
            }
        }
    }
    throw '未找到 JDK 17，请设置 SPD_JAVA_HOME 或安装 JDK 17。'
}

function Resolve-MavenCommand {
    if ($env:SPD_MAVEN_HOME) {
        $candidate = Join-Path $env:SPD_MAVEN_HOME 'bin\mvn.cmd'
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $onPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($onPath) {
        return $onPath.Source
    }
    $searchRoots = @(
        (Join-Path $repoRoot '.tools'),
        'C:\Program Files',
        'C:\apache-maven'
    )
    foreach ($root in $searchRoots) {
        $hits = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^apache-maven-3\.9' } |
            Sort-Object Name -Descending
        foreach ($hit in $hits) {
            $candidate = Join-Path $hit.FullName 'bin\mvn.cmd'
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }
    foreach ($drive in @('C:\', 'D:\')) {
        foreach ($repo in (Get-ChildItem $drive -Directory -ErrorAction SilentlyContinue)) {
            $tools = Join-Path $repo.FullName '.tools'
            $hits = Get-ChildItem $tools -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^apache-maven-3\.9' } |
                Sort-Object Name -Descending
            foreach ($hit in $hits) {
                $candidate = Join-Path $hit.FullName 'bin\mvn.cmd'
                if (Test-Path $candidate) {
                    return $candidate
                }
            }
        }
    }
    throw '未找到 Maven 3.9，请设置 SPD_MAVEN_HOME。'
}

function Resolve-NpmCommand {
    $candidate = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($candidate) {
        return $candidate.Source
    }
    $nodeHome = 'C:\Program Files\nodejs'
    if (Test-Path (Join-Path $nodeHome 'npm.cmd')) {
        return (Join-Path $nodeHome 'npm.cmd')
    }
    throw '未找到 npm，请先安装 Node.js 22+。'
}

function Resolve-MysqldumpCommand {
    if ($env:SPD_MYSQLDUMP -and (Test-Path $env:SPD_MYSQLDUMP)) {
        return $env:SPD_MYSQLDUMP
    }
    $onPath = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($onPath) {
        return $onPath.Source
    }
    foreach ($root in @('C:\Program Files\MySQL', 'D:\Program Files\MySQL')) {
        $hits = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^MySQL Server' } |
            Sort-Object Name -Descending
        foreach ($hit in $hits) {
            $candidate = Join-Path $hit.FullName 'bin\mysqldump.exe'
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }
    throw '未找到 mysqldump，请设置 SPD_MYSQLDUMP 或安装 MySQL Server 8.4；如需跳过数据库备份使用 -SkipDbDump。'
}

Write-Host '===== 院内 SPD 离线部署打包 =====' -ForegroundColor Cyan

$jdkHome = Resolve-Jdk17Home
$maven = Resolve-MavenCommand
$npm = Resolve-NpmCommand
Write-Host "工具链：JDK $jdkHome" -ForegroundColor Green
Write-Host "工具链：Maven $maven" -ForegroundColor Green
Write-Host "工具链：npm $npm" -ForegroundColor Green

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

# 以继承句柄方式启动构建命令（不捕获管道输出，兼容受限执行环境），
# 并轮询产物文件的更新时间判断构建完成。
function Invoke-BuildCommand([string]$FilePath, [string]$Arguments, [string]$MarkerPath, [string]$Name, [int]$TimeoutSeconds) {
    $before = if (Test-Path $MarkerPath) { (Get-Item $MarkerPath).LastWriteTimeUtc } else { [datetime]::MinValue }
    Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', $Arguments) -WorkingDirectory $repoRoot -NoNewWindow
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        if (Test-Path $MarkerPath) {
            $after = (Get-Item $MarkerPath).LastWriteTimeUtc
            if ($after -gt $before) {
                Write-Host "$Name 构建完成。"
                return
            }
        }
    }
    throw "$Name 构建超时（$TimeoutSeconds 秒），请检查构建输出。"
}

# 1. 构建后端 jar
Write-Host '[1/5] 构建后端 jar ...' -ForegroundColor Cyan
$jarPath = Join-Path $repoRoot 'backend\target\app.jar'
Invoke-BuildCommand $maven "$maven -f $repoRoot\backend\pom.xml clean package -DskipTests" $jarPath '后端 jar' 900

# 2. 构建前端
Write-Host '[2/5] 构建前端 ...' -ForegroundColor Cyan
$distIndex = Join-Path $repoRoot 'frontend\dist\index.html'
# npm.cmd 位于带空格路径，经 node 直接调用 npm-cli.js，避免 cmd /c 首尾引号问题
$npmCli = Join-Path (Split-Path -Parent $npm) 'node_modules\npm\bin\npm-cli.js'
$npmNode = Join-Path (Split-Path -Parent $npm) 'node.exe'
$npmCommand = '""' + $npmNode + '" "' + $npmCli + '" --prefix ' + $repoRoot + '\frontend run build"'
Invoke-BuildCommand $npm $npmCommand $distIndex '前端构建产物' 900
$distPath = Join-Path $repoRoot 'frontend\dist'
if (-not (Test-Path $distPath)) {
    throw "未找到前端构建产物：$distPath"
}

# 3. 导出数据库数据（随包分发，供首次部署自动导入）
$dumpDate = Get-Date -Format 'yyyyMMdd'
$ispDumpName = "ispd-$dumpDate.sql"
$dumpStage = Join-Path $repoRoot 'output\db-dump'
if ($SkipDbDump) {
    Write-Host '[3/5] 已跳过数据库导出（-SkipDbDump）。' -ForegroundColor Yellow
} else {
    Write-Host '[3/5] 导出数据库数据 ...' -ForegroundColor Cyan
    $mysqldump = Resolve-MysqldumpCommand
    $dbHost = 'localhost'
    $dbPort = '3306'
    $dbName = 'ispd'
    if ($env:SPD_DB_URL -and $env:SPD_DB_URL -match '//([^:/]+)(?::(\d+))?/([^/?]+)') {
        $dbHost = $Matches[1]
        if ($Matches[2]) { $dbPort = $Matches[2] }
        $dbName = $Matches[3]
    }
    $dbUser = if ($env:SPD_DB_USERNAME) { $env:SPD_DB_USERNAME } else { 'admin' }
    $dbPass = if ($env:SPD_DB_PASSWORD) { $env:SPD_DB_PASSWORD } else { 'admin123' }
    New-Item -ItemType Directory -Force -Path $dumpStage | Out-Null
    $dumpOut = Join-Path $dumpStage $ispDumpName
    $dumpErr = Join-Path $dumpStage 'mysqldump.err.txt'
    if (Test-Path $dumpOut) { Remove-Item $dumpOut -Force }
    if (Test-Path $dumpErr) { Remove-Item $dumpErr -Force }
    $dumpArgs = @(
        '--default-character-set=utf8mb4', '--single-transaction', '--routines',
        '--triggers', '--events', '--hex-blob', '--set-gtid-purged=OFF',
        '--column-statistics=0', '--no-tablespaces',
        '-h', $dbHost, '-P', $dbPort, '-u', $dbUser, "-p$dbPass", $dbName
    )
    $dumpProc = Start-Process -FilePath $mysqldump -ArgumentList $dumpArgs `
        -RedirectStandardOutput $dumpOut -RedirectStandardError $dumpErr `
        -NoNewWindow -Wait -PassThru
    if ($dumpProc.ExitCode -ne 0 -or -not (Test-Path $dumpOut) -or (Get-Item $dumpOut).Length -lt 1024) {
        $errTail = if (Test-Path $dumpErr) { (Get-Content $dumpErr -Tail 3) -join ' ' } else { '' }
        throw "数据库导出失败（exit=$($dumpProc.ExitCode)）：$errTail 如需跳过请使用 -SkipDbDump。"
    }
    Write-Host "数据库导出完成：$ispDumpName（$([math]::Round((Get-Item $dumpOut).Length / 1KB, 1)) KB）" -ForegroundColor Green
}

# 4. 组装部署目录
Write-Host '[4/5] 组装部署目录 ...' -ForegroundColor Cyan
$bundleRoot = Join-Path $repoRoot 'output\offline-bundle'
$bundleDir = Join-Path $bundleRoot 'spd-server'
if (Test-Path $bundleDir) {
    Remove-Item $bundleDir -Recurse -Force
}
$webDir = Join-Path $bundleDir 'web'
$jdkTarget = Join-Path $bundleDir 'tools\jdk-17'
New-Item -ItemType Directory -Force -Path $webDir, $jdkTarget | Out-Null

Copy-Item $jarPath (Join-Path $bundleDir 'app.jar') -Force
Copy-Item (Join-Path $distPath '*') $webDir -Recurse -Force

# 复制数据库备份（若已导出）
if (-not $SkipDbDump) {
    $dbDir = Join-Path $bundleDir 'db'
    New-Item -ItemType Directory -Force -Path $dbDir | Out-Null
    Copy-Item (Join-Path $dumpStage $ispDumpName) (Join-Path $dbDir $ispDumpName) -Force
}

# 复制 JDK（排除 jmods 与源码包以减小体积）
Get-ChildItem $jdkHome -Force | Where-Object { $_.Name -ne 'jmods' } | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $jdkTarget $_.Name) -Recurse -Force
}
$srcZip = Join-Path $jdkTarget 'lib\src.zip'
if (Test-Path $srcZip) {
    Remove-Item $srcZip -Force
}

# 5. 写入一键启动脚本与说明
Write-Host '[5/5] 写入一键启动脚本与说明 ...' -ForegroundColor Cyan
@'
 
@echo off
rem SPD one-click start (double-click to run)
rem DB connection override: SPD_DB_URL / SPD_DB_USERNAME / SPD_DB_PASSWORD
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%tools\jdk-17"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Bundled JDK not found: %JAVA_HOME%
    pause
    exit /b 1
)
if not defined SPD_DB_URL set "SPD_DB_URL=jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai"
if not defined SPD_DB_USERNAME set "SPD_DB_USERNAME=admin"
if not defined SPD_DB_PASSWORD set "SPD_DB_PASSWORD=admin123"
rem Auto-import bundled DB data when ISPD is empty (see init-database.bat)
call "%ROOT%init-database.bat" /q
echo Starting SPD server, visit http://localhost:1818
echo Log file: %ROOT%server.log
"%JAVA_HOME%\bin\java.exe" -jar "%ROOT%app.jar" --spd.web.static-dir="%ROOT%web" > "%ROOT%server.log" 2>&1
echo Server exited, see %ROOT%server.log
pause
'@ | Set-Content -Path (Join-Path $bundleDir 'start-server.bat') -Encoding UTF8

@'
 
@echo off
rem ============================================================
rem SPD database initializer
rem Imports db\__ISP_DUMP__ ONLY when the ISPD database is empty
rem (0 tables). Existing data is never overwritten; safe to rerun.
rem Connection (defaults localhost:3306 admin/admin123):
rem   SPD_DB_HOST / SPD_DB_PORT / SPD_DB_USERNAME / SPD_DB_PASSWORD
rem Note: start-server.bat uses SPD_DB_URL; keep both pointing at
rem       the same database.
rem Usage: double-click to run manually; start-server.bat calls it
rem         silently with /q.
rem ============================================================
setlocal
set "ROOT=%~dp0"
if not defined SPD_DB_HOST set "SPD_DB_HOST=localhost"
if not defined SPD_DB_PORT set "SPD_DB_PORT=3306"
if not defined SPD_DB_USERNAME set "SPD_DB_USERNAME=admin"
if not defined SPD_DB_PASSWORD set "SPD_DB_PASSWORD=admin123"

if not exist "%ROOT%db\__ISP_DUMP__" (
    echo [SKIP] Bundled data backup not found: %ROOT%db\__ISP_DUMP__
    goto :end
)

set "MYSQL_CLI="
where mysql >nul 2>nul
if not errorlevel 1 set "MYSQL_CLI=mysql"
if not defined MYSQL_CLI if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" set "MYSQL_CLI=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
if not defined MYSQL_CLI if exist "D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" set "MYSQL_CLI=D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
if not defined MYSQL_CLI if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL_CLI=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not defined MYSQL_CLI (
    echo [SKIP] mysql client not found; install the MySQL 8.4 client and retry.
    goto :end
)

rem Try to create the database; a missing CREATE privilege is tolerated,
rem later steps report a clear error if the database truly does not exist.
"%MYSQL_CLI%" -h %SPD_DB_HOST% -P %SPD_DB_PORT% -u %SPD_DB_USERNAME% -p%SPD_DB_PASSWORD% -e "CREATE DATABASE IF NOT EXISTS `ISPD` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" >nul 2>nul

rem Check whether ISPD is empty
"%MYSQL_CLI%" -h %SPD_DB_HOST% -P %SPD_DB_PORT% -u %SPD_DB_USERNAME% -p%SPD_DB_PASSWORD% -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('ispd','ISPD')" > "%TEMP%\spd-db-count.tmp" 2>nul
if errorlevel 1 (
    echo [WARN] Cannot connect to database %SPD_DB_HOST%:%SPD_DB_PORT%; import skipped.
    del "%TEMP%\spd-db-count.tmp" >nul 2>nul
    goto :end
)
set /p TABLE_COUNT=<"%TEMP%\spd-db-count.tmp"
del "%TEMP%\spd-db-count.tmp" >nul 2>nul
if not "%TABLE_COUNT%"=="0" (
    echo [SKIP] ISPD already has %TABLE_COUNT% tables; import skipped.
    goto :end
)

echo ISPD is empty, importing bundled data backup ...
"%MYSQL_CLI%" --default-character-set=utf8mb4 -h %SPD_DB_HOST% -P %SPD_DB_PORT% -u %SPD_DB_USERNAME% -p%SPD_DB_PASSWORD% ISPD < "%ROOT%db\__ISP_DUMP__"
if errorlevel 1 (
    echo [ERROR] Import failed: make sure the ISPD database exists and the account has full privileges on it, then retry.
    goto :end
)
echo Import finished: %ROOT%db\__ISP_DUMP__
goto :end

:end
endlocal
if /i "%~1"=="/q" exit /b 0
echo.
pause
exit /b 0
'@ | Set-Content -Path (Join-Path $bundleDir 'init-database.bat') -Encoding UTF8
(Get-Content (Join-Path $bundleDir 'init-database.bat') -Raw -Encoding UTF8).Replace('__ISP_DUMP__', $ispDumpName) |
    Set-Content (Join-Path $bundleDir 'init-database.bat') -Encoding UTF8 -NoNewline

@'
# 院内 SPD 一体化服务一键启动（PowerShell）
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = Join-Path $root 'tools\jdk-17'
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Write-Error '未找到内置 JDK'
    exit 1
}
if (-not $env:SPD_DB_URL) { $env:SPD_DB_URL = 'jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai' }
if (-not $env:SPD_DB_USERNAME) { $env:SPD_DB_USERNAME = 'admin' }
if (-not $env:SPD_DB_PASSWORD) { $env:SPD_DB_PASSWORD = 'admin123' }
# 数据库为空时自动导入 db\ 下的随包数据备份（可单独运行 init-database.bat）
$dumpFile = Join-Path $root 'db\__ISP_DUMP__'
if (Test-Path $dumpFile) {
    $mysqlCli = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
    if (-not $mysqlCli) {
        foreach ($p in @('C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe', 'D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe', 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe')) {
            if (Test-Path $p) { $mysqlCli = $p; break }
        }
    }
    if ($mysqlCli) {
        $dbHost = if ($env:SPD_DB_HOST) { $env:SPD_DB_HOST } else { 'localhost' }
        $dbPort = if ($env:SPD_DB_PORT) { $env:SPD_DB_PORT } else { '3306' }
        $dbUser = if ($env:SPD_DB_USERNAME) { $env:SPD_DB_USERNAME } else { 'admin' }
        $dbPass = if ($env:SPD_DB_PASSWORD) { $env:SPD_DB_PASSWORD } else { 'admin123' }
        & $mysqlCli -h $dbHost -P $dbPort -u $dbUser "-p$dbPass" -e "CREATE DATABASE IF NOT EXISTS \`ISPD\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" *> $null
        $count = & $mysqlCli -h $dbHost -P $dbPort -u $dbUser "-p$dbPass" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('ispd','ISPD')" 2>$null
        if ($LASTEXITCODE -eq 0) {
            if ("$count".Trim() -eq '0') {
                Write-Host 'ISPD 数据库为空，开始导入随包数据备份 ...'
                $import = Start-Process -FilePath $mysqlCli -ArgumentList @('--default-character-set=utf8mb4', '-h', $dbHost, '-P', $dbPort, '-u', $dbUser, "-p$dbPass", 'ISPD') -RedirectStandardInput $dumpFile -NoNewWindow -Wait -PassThru
                if ($import.ExitCode -ne 0) { Write-Warning '数据导入失败：请确认 ISPD 数据库已创建、账号具备该库全部权限。' }
                else { Write-Host '数据导入完成。' }
            } else {
                Write-Host "ISPD 数据库已有数据（$($count.ToString().Trim()) 张表），跳过导入。"
            }
        }
    }
}
Write-Host '正在启动院内 SPD 一体化服务，访问地址 http://localhost:1818'
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar (Join-Path $root 'app.jar') --spd.web.static-dir=(Join-Path $root 'web')
'@ | Set-Content -Path (Join-Path $bundleDir 'start-server.ps1') -Encoding UTF8
(Get-Content (Join-Path $bundleDir 'start-server.ps1') -Raw -Encoding UTF8).Replace('__ISP_DUMP__', $ispDumpName) |
    Set-Content (Join-Path $bundleDir 'start-server.ps1') -Encoding UTF8 -NoNewline

@'
# SPD 一体化服务环境变量示例（可选）
# 将本文件改名为 config.env 或在启动前设置对应环境变量
SPD_DB_URL=jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai
SPD_DB_USERNAME=admin
SPD_DB_PASSWORD=admin123
SPD_JWT_SECRET=请替换为至少 48 位随机字符串
'@ | Set-Content -Path (Join-Path $bundleDir 'config.env.example') -Encoding UTF8

@'
院内 SPD 一体化离线部署说明
================================

目录结构：
  app.jar             后端服务（Spring Boot，含全部数据库迁移）
  web/                前端页面（构建产物）
  tools/jdk-17/       内置 JDK 17（无需在服务器安装 Java）
  db/                 数据库数据备份（ispd-YYYYMMDD.sql，随包分发）
  start-server.bat    一键启动（双击运行）
  start-server.ps1    一键启动（PowerShell 运行）
  init-database.bat   数据库初始化（仅当 ISPD 库为空时导入 db\ 数据，可单独运行）
  config.env.example  数据库与密钥配置示例

部署步骤：
  1. 将整个 spd-server 目录拷贝到实施服务器（Windows 64 位）。
  2. 确保服务器可访问 MySQL 8.4，并已创建 ISPD 数据库。
     创建命令示例（以 root 执行，也可参考项目 db/init/01-create-ispd-database.sql）：
       CREATE DATABASE IF NOT EXISTS ISPD DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
     默认连接 localhost:3306/ISPD，账号 admin/admin123；
     如需修改，设置环境变量 SPD_DB_URL / SPD_DB_USERNAME / SPD_DB_PASSWORD 后启动。
  3. 双击 start-server.bat 或运行 start-server.ps1 启动服务。
     启动前会自动执行数据库初始化：仅当 ISPD 数据库为空（0 张表）时，
     自动导入 db\ 目录下的数据备份；已有数据的数据库不会被覆盖。
     自动导入需要本机可执行 mysql 客户端（MySQL Server 8.4 自带）。
  4. 浏览器访问 http://localhost:1818 登录系统（默认账号 admin / admin123）。
  5. 停止服务：关闭启动窗口或结束 java 进程。

说明：
  - 前端与后端同端口（1818）一体化运行，无需额外 Web 服务器。
  - 服务日志写入 server.log。
  - 数据导入连接参数使用 SPD_DB_HOST / SPD_DB_PORT / SPD_DB_USERNAME / SPD_DB_PASSWORD
    （默认 localhost:3306 admin/admin123）；与 SPD_DB_URL 请保持指向同一个数据库。
  - 如需跳过数据导出（例如打包机未运行 MySQL），打包时使用 -SkipDbDump 参数。
'@ | Set-Content -Path (Join-Path $bundleDir '部署说明.txt') -Encoding UTF8

# 6. 打包 zip
if (-not $SkipZip) {
    Write-Host '正在生成 zip 压缩包 ...' -ForegroundColor Cyan
    $zipPath = Join-Path $bundleRoot 'spd-server-offline.zip'
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $bundleDir '*') -DestinationPath $zipPath
    Write-Host "已生成部署压缩包：$zipPath" -ForegroundColor Green
}

Write-Host ''
Write-Host '===== 打包完成 =====' -ForegroundColor Cyan
Write-Host "部署目录：$bundleDir"
Write-Host '一键启动：spd-server\start-server.bat（双击运行）'
