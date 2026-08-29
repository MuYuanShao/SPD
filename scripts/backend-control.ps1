param(
    [ValidateSet('start', 'restart', 'stop', 'status')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendPom = Join-Path $repoRoot 'backend\pom.xml'
$stdoutLog = Join-Path $repoRoot 'backend-1818.log'
$stderrLog = Join-Path $repoRoot 'backend-1818-error.log'
$healthUrl = 'http://127.0.0.1:1818/api/health'
$backendPort = 1818

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
    # The pom enforcer requires Java [17,18), so a JDK 17 must drive Maven.
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

    # Local copies of the repo keep toolchains under <drive-root>\<repo>\.tools.
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
    return $null
}

function Resolve-MavenCommand {
    $wrapper = Join-Path $repoRoot 'backend\mvnw.cmd'
    if (Test-Path $wrapper) {
        return $wrapper
    }

    if ($env:SPD_MAVEN_HOME) {
        $candidate = Join-Path $env:SPD_MAVEN_HOME 'bin\mvn.cmd'
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $onPath = Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1
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

    return $null
}

function Get-BackendProcess {
    $listener = Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $listener) {
        return $null
    }

    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($process.CommandLine -notlike '*com.hospital.spd.SpdApplication*') {
        throw "Port $backendPort is occupied by a non-SPD process (PID $($listener.OwningProcess)). No action was taken."
    }
    return $process
}

function Test-BackendHealth {
    try {
        $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 1
        return $null -ne $response
    } catch {
        return $false
    }
}

function Show-BackendStatus {
    $process = Get-BackendProcess
    if (-not $process) {
        Write-Host 'SPD backend: stopped' -ForegroundColor Yellow
        return
    }
    $health = if (Test-BackendHealth) { 'healthy' } else { 'starting or unhealthy' }
    Write-Host "SPD backend: running, PID $($process.ProcessId), status $health" -ForegroundColor Green
}

function Stop-Backend {
    $process = Get-BackendProcess
    if (-not $process) {
        Write-Host 'SPD backend is already stopped.' -ForegroundColor Yellow
        return
    }

    $parentId = $process.ParentProcessId
    Write-Host "Stopping SPD backend (PID $($process.ProcessId))..."
    Stop-Process -Id $process.ProcessId -Force
    Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue

    if ($parentId) {
        $parent = Get-CimInstance Win32_Process -Filter "ProcessId=$parentId" -ErrorAction SilentlyContinue
        if ($parent -and $parent.CommandLine -like '*spring-boot:run*') {
            Stop-Process -Id $parentId -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Host 'SPD backend stopped.' -ForegroundColor Green
}

function Start-Backend {
    if (Get-BackendProcess) {
        Write-Host 'SPD backend is already running.' -ForegroundColor Yellow
        Show-BackendStatus
        return
    }

    $javaHome = Resolve-Jdk17Home
    if (-not $javaHome) {
        throw 'JDK 17 was not found. Set SPD_JAVA_HOME (or JAVA_HOME) to a JDK 17 installation; the pom enforcer requires Java [17,18).'
    }
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
    if (-not $env:SPRING_PROFILES_ACTIVE) {
        $env:SPRING_PROFILES_ACTIVE = 'local'
    }

    $maven = Resolve-MavenCommand
    if (-not $maven) {
        throw 'Maven was not found. Install Maven 3.9.9+ (the pom enforcer requires [3.9.9,3.10.0)), or set SPD_MAVEN_HOME.'
    }

    Write-Host "Starting SPD backend (JDK: $javaHome, Maven: $maven)..."
    $usingWrapper = [IO.Path]::GetFileName($maven) -ieq 'mvnw.cmd'
    $mavenArguments = if ($usingWrapper) {
        @('clean', 'spring-boot:run')
    } else {
        @('-f', $backendPom, 'clean', 'spring-boot:run')
    }
    $mavenWorkingDirectory = if ($usingWrapper) { Join-Path $repoRoot 'backend' } else { $repoRoot }
    $mavenProcess = Start-Process `
        -FilePath $maven `
        -ArgumentList $mavenArguments `
        -WorkingDirectory $mavenWorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-BackendHealth) {
            $process = Get-BackendProcess
            Write-Host "SPD backend started successfully (PID $($process.ProcessId))." -ForegroundColor Green
            return
        }
        $mavenProcess.Refresh()
        if ($mavenProcess.HasExited) {
            $tail = if (Test-Path $stdoutLog) { (Get-Content -Tail 12 $stdoutLog) -join [Environment]::NewLine } else { '' }
            throw "SPD backend Maven process exited with code $($mavenProcess.ExitCode).$([Environment]::NewLine)$tail"
        }
        Start-Sleep -Seconds 1
    }

    throw "SPD backend did not pass its health check within 90 seconds. See $stdoutLog and $stderrLog."
}

function Invoke-BackendAction([string]$SelectedAction) {
    switch ($SelectedAction) {
        'start' { Start-Backend }
        'restart' {
            Stop-Backend
            Start-Backend
        }
        'stop' { Stop-Backend }
        'status' { Show-BackendStatus }
    }
}

if ($Action) {
    Invoke-BackendAction $Action
    exit
}

while ($true) {
    Write-Host ''
    Write-Host 'SPD Backend Control' -ForegroundColor Cyan
    Write-Host '1. Start'
    Write-Host '2. Restart'
    Write-Host '3. Stop'
    Write-Host '4. Status'
    Write-Host '0. Exit'
    $choice = Read-Host 'Select an action'
    switch ($choice) {
        '1' { Invoke-BackendAction 'start' }
        '2' { Invoke-BackendAction 'restart' }
        '3' { Invoke-BackendAction 'stop' }
        '4' { Invoke-BackendAction 'status' }
        '0' { exit }
        default { Write-Host 'Invalid option. Try again.' -ForegroundColor Red }
    }
}
