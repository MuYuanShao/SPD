<#
  院内 SPD 平台一键启动脚本
  依次检查 MySQL -> 启动后端（复用 scripts/backend-control.ps1）-> 启动前端 -> 输出访问信息。
  用法：
    powershell -NoProfile -ExecutionPolicy Bypass -File start-dev.ps1
    powershell ... -File start-dev.ps1 -SkipBackend     # 只启动前端
    powershell ... -File start-dev.ps1 -SkipFrontend    # 只启动后端
#>
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Continue'
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

Write-Host ''
Write-Host '===== 院内 SPD 平台启动 =====' -ForegroundColor Cyan

# 1. MySQL
if (-not (Test-Port 3306)) {
    Write-Warning 'MySQL (3306) 未启动，请先启动 MySQL84 服务；否则后端启动会失败。'
} else {
    Write-Host '[1/3] MySQL 3306 已就绪。' -ForegroundColor Green
}

# 2. 后端
if ($SkipBackend) {
    Write-Host '[2/3] 已跳过后端启动。' -ForegroundColor Yellow
} elseif (Test-Port 1818) {
    Write-Host '[2/3] 后端 1818 已在运行，跳过。' -ForegroundColor Yellow
} else {
    Write-Host '[2/3] 正在启动后端（首次编译可能需要 1-2 分钟）...' -ForegroundColor Cyan
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts\backend-control.ps1') start
    if ($LASTEXITCODE -ne 0 -and -not (Test-Port 1818)) {
        Write-Warning '后端启动失败，请查看 backend-1818.log / backend-1818-error.log。'
    }
}

# 3. 前端
if ($SkipFrontend) {
    Write-Host '[3/3] 已跳过前端启动。' -ForegroundColor Yellow
} elseif (Test-Port 1820) {
    Write-Host '[3/3] 前端 1820 已在运行，跳过。' -ForegroundColor Yellow
} else {
    Write-Host '[3/3] 正在启动前端...' -ForegroundColor Cyan
    $frontendOut = Join-Path $repoRoot 'frontend-1820.log'
    $frontendErr = Join-Path $repoRoot 'frontend-1820-error.log'
    Start-Process -FilePath 'npm.cmd' -ArgumentList '--prefix', 'frontend', 'run', 'dev' `
        -WorkingDirectory $repoRoot -WindowStyle Hidden `
        -RedirectStandardOutput $frontendOut -RedirectStandardError $frontendErr
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline -and -not (Test-Port 1820)) {
        Start-Sleep -Seconds 1
    }
    if (Test-Port 1820) {
        Write-Host '前端已启动。' -ForegroundColor Green
    } else {
        Write-Warning "前端 60 秒内未就绪，请查看 $frontendOut / $frontendErr。"
    }
}

# 4. 汇总
Write-Host ''
Write-Host '===== 启动完成 =====' -ForegroundColor Cyan
if (Test-Port 1818) {
    Write-Host '后端 API : http://localhost:1818/api   （健康检查 http://localhost:1818/api/health）' -ForegroundColor Green
} else {
    Write-Host '后端 API : 未运行' -ForegroundColor Red
}
if (Test-Port 1820) {
    Write-Host '前端页面 : http://localhost:1820' -ForegroundColor Green
} else {
    Write-Host '前端页面 : 未运行' -ForegroundColor Red
}
Write-Host '登录账号 : admin / admin123' -ForegroundColor Green
Write-Host ''
