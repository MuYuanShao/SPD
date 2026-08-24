<#
  院内 SPD 离线一体化部署打包脚本
  将后端 jar、前端构建产物、内置 JDK17 及一键启动脚本打包到 output/offline-bundle，
  实施人员拷贝整个目录即可一键部署，无需联网安装任何依赖。

  用法：
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-offline-deployment.ps1
    powershell ... -File scripts/package-offline-deployment.ps1 -SkipZip   # 不打 zip 包
#>
param(
    [switch]$SkipZip
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
Write-Host '[1/4] 构建后端 jar ...' -ForegroundColor Cyan
$jarPath = Join-Path $repoRoot 'backend\target\app.jar'
Invoke-BuildCommand $maven "$maven -f $repoRoot\backend\pom.xml clean package -DskipTests" $jarPath '后端 jar' 900

# 2. 构建前端
Write-Host '[2/4] 构建前端 ...' -ForegroundColor Cyan
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

# 3. 组装部署目录
Write-Host '[3/4] 组装部署目录 ...' -ForegroundColor Cyan
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

# 复制 JDK（排除 jmods 与源码包以减小体积）
Get-ChildItem $jdkHome -Force | Where-Object { $_.Name -ne 'jmods' } | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $jdkTarget $_.Name) -Recurse -Force
}
$srcZip = Join-Path $jdkTarget 'lib\src.zip'
if (Test-Path $srcZip) {
    Remove-Item $srcZip -Force
}

# 4. 写入一键启动脚本与说明
Write-Host '[4/4] 写入一键启动脚本与说明 ...' -ForegroundColor Cyan
@'
@echo off
rem 院内 SPD 一体化服务一键启动（双击运行）
rem 数据库连接通过环境变量覆盖：SPD_DB_URL / SPD_DB_USERNAME / SPD_DB_PASSWORD
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%tools\jdk-17"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 未找到内置 JDK：%JAVA_HOME%
    pause
    exit /b 1
)
if not defined SPD_DB_URL set "SPD_DB_URL=jdbc:mysql://localhost:3306/ISPD?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai"
if not defined SPD_DB_USERNAME set "SPD_DB_USERNAME=admin"
if not defined SPD_DB_PASSWORD set "SPD_DB_PASSWORD=admin123"
echo 正在启动院内 SPD 一体化服务，访问地址 http://localhost:1818
echo 日志文件：%ROOT%server.log
"%JAVA_HOME%\bin\java.exe" -jar "%ROOT%app.jar" --spd.web.static-dir="%ROOT%web" > "%ROOT%server.log" 2>&1
echo 服务已退出，请查看 %ROOT%server.log
pause
'@ | Set-Content -Path (Join-Path $bundleDir 'start-server.bat') -Encoding UTF8

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
Write-Host '正在启动院内 SPD 一体化服务，访问地址 http://localhost:1818'
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar (Join-Path $root 'app.jar') --spd.web.static-dir=(Join-Path $root 'web')
'@ | Set-Content -Path (Join-Path $bundleDir 'start-server.ps1') -Encoding UTF8

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
  app.jar            后端服务（Spring Boot，含全部数据库迁移）
  web/               前端页面（构建产物）
  tools/jdk-17/      内置 JDK 17（无需在服务器安装 Java）
  start-server.bat   一键启动（双击运行）
  start-server.ps1   一键启动（PowerShell 运行）
  config.env.example 数据库与密钥配置示例

部署步骤：
  1. 将整个 spd-server 目录拷贝到实施服务器（Windows 64 位）。
  2. 确保服务器可访问 MySQL 8.4，并已创建 ISPD 数据库（首次启动会自动执行 Flyway 迁移）。
     默认连接 localhost:3306/ISPD，账号 admin/admin123；
     如需修改，设置环境变量 SPD_DB_URL / SPD_DB_USERNAME / SPD_DB_PASSWORD 后启动。
  3. 双击 start-server.bat 或运行 start-server.ps1 启动服务。
  4. 浏览器访问 http://localhost:1818 登录系统（默认账号 admin / admin123）。
  5. 停止服务：关闭启动窗口或结束 java 进程。

说明：
  - 前端与后端同端口（1818）一体化运行，无需额外 Web 服务器。
  - 服务日志写入 server.log。
'@ | Set-Content -Path (Join-Path $bundleDir '部署说明.txt') -Encoding UTF8

# 5. 打包 zip
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
