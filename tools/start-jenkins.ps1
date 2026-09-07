# tools/start-jenkins.ps1
# Automated local Jenkins controller launcher for HeapHammer multi-version builds

param (
    [int]$Port = 8080,
    [switch]$Rebuild,
    [switch]$Stop
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = Resolve-Path "$PSScriptRoot/.."
$JenkinsDir = "$WorkspaceRoot/jenkins"

if ($Stop) {
    Write-Host "Stopping Jenkins container..." -ForegroundColor Yellow
    docker stop jenkins-heaphammer 2>$null | Out-Null
    docker rm jenkins-heaphammer 2>$null | Out-Null
    Write-Host "Jenkins container stopped." -ForegroundColor Green
    exit 0
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  HeapHammer Automated Jenkins Controller Launcher               " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Verify Docker is running
& docker version >$null 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not running. Please launch Docker Desktop or Rancher Desktop."
    exit 1
}

# 2. Build image if needed
$imageExists = docker images -q heaphammer-jenkins:latest
if (-not $imageExists -or $Rebuild) {
    Write-Host "`nBuilding heaphammer-jenkins:latest Docker image (with multi-JDK & plugins)..." -ForegroundColor Yellow
    docker build -t heaphammer-jenkins:latest $JenkinsDir
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to build Jenkins Docker image."
        exit 1
    }
} else {
    Write-Host "`nUsing existing image: heaphammer-jenkins:latest" -ForegroundColor Green
}

# 3. Stop old container if running
$existingContainer = docker ps -aq -f name=jenkins-heaphammer
if ($existingContainer) {
    Write-Host "Removing previous container instance..." -ForegroundColor Gray
    docker stop jenkins-heaphammer 2>$null | Out-Null
    docker rm jenkins-heaphammer 2>$null | Out-Null
}

# 4. Run Jenkins container
Write-Host "Launching container on port ${Port}..." -ForegroundColor Cyan
docker run -d `
    --name jenkins-heaphammer `
    -p "${Port}:8080" `
    -p "50000:50000" `
    -v "heaphammer_jenkins_home:/var/jenkins_home" `
    heaphammer-jenkins:latest | Out-Null

# 5. Wait for Jenkins to become healthy
Write-Host "Waiting for Jenkins to initialize and load JCasC configuration..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$ready = $false
$maxWaitSeconds = 120

while ($sw.Elapsed.TotalSeconds -lt $maxWaitSeconds) {
    Start-Sleep -Seconds 3
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:${Port}/login" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($response -and ($response.StatusCode -eq 200 -or $response.StatusCode -eq 403)) {
            $ready = $true
            break
        }
    } catch {
        # Still starting up
    }
    Write-Host "  [Waiting] Jenkins starting up ($([math]::Round($sw.Elapsed.TotalSeconds, 1))s)..." -ForegroundColor Gray
}

if ($ready) {
    Write-Host "`n=================================================================" -ForegroundColor Green
    Write-Host "  [SUCCESS] Jenkins is UP and READY!                             " -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host "  URL:           http://localhost:${Port}" -ForegroundColor Cyan
    Write-Host "  Credentials:   admin / admin" -ForegroundColor Yellow
    Write-Host "  Multi-JDK:     JDK21, JDK17, JDK8 (Pre-installed & configured)" -ForegroundColor Green
    Write-Host "  Pipeline Job:  'HeapHammer' (Pre-configured for GitHub repo)" -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
} else {
    Write-Warning "Jenkins did not respond within $maxWaitSeconds seconds. Check logs with: docker logs jenkins-heaphammer"
}
