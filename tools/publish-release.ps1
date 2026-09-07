# tools/publish-release.ps1
# Production packaging, checksum generation, and GitHub release deployment tool

param (
    [string]$Tag = "v1.0.0",
    [string]$Title = "HeapHammer v1.0.0 - Production Release",
    [switch]$SkipGitHub,
    [switch]$Draft
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = Resolve-Path "$PSScriptRoot/.."
Set-Location $WorkspaceRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  HeapHammer Production Release and Deployment Pipeline          " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Read metadata from gradle.properties
$props = @{}
Get-Content "$WorkspaceRoot/gradle.properties" | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        $props[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$modVersion = $props['mod_version']
$mcVersion = $props['minecraft_version']
Write-Host "Target Minecraft: $mcVersion | Mod Version: $modVersion" -ForegroundColor Yellow

# 2. Build production mod if needed
$primaryJar = "$WorkspaceRoot/build/libs/heaphammer-$modVersion.jar"
if (-not (Test-Path $primaryJar)) {
    Write-Host "`nBuilding production artifacts via Gradle..." -ForegroundColor Cyan
    ./gradlew build --no-daemon
}

if (-not (Test-Path $primaryJar)) {
    Write-Error "Production JAR not found at: $primaryJar"
    exit 1
}

# 3. Setup production staging directory
$prodDir = "$WorkspaceRoot/dist/production"
if (Test-Path $prodDir) {
    Remove-Item "$prodDir/*" -Force -Recurse -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory -Path $prodDir -Force | Out-Null
}

Write-Host "`nStaging production release bundle in dist/production..." -ForegroundColor Cyan

# Copy primary jar with standard and versioned names
Copy-Item $primaryJar "$prodDir/heaphammer-$modVersion.jar" -Force
Copy-Item $primaryJar "$prodDir/heaphammer-$mcVersion-$modVersion.jar" -Force

# Copy admin guide PDF if available
$pdfCandidates = @(
    "$WorkspaceRoot/dist/HeapHammer_Server_Admin_Guide.pdf",
    "E:\Downloads\HeapHammer_Server_Admin_Guide.pdf"
)
foreach ($candidate in $pdfCandidates) {
    if (Test-Path $candidate) {
        Copy-Item $candidate "$prodDir/HeapHammer_Server_Admin_Guide.pdf" -Force
        Write-Host "  + Included Admin Guide PDF ($candidate)" -ForegroundColor Green
        break
    }
}

# Copy branding assets
if (Test-Path "$WorkspaceRoot/assets/heaphammer_logo.png") {
    Copy-Item "$WorkspaceRoot/assets/heaphammer_logo.png" "$prodDir/heaphammer_logo.png" -Force
}

# 4. Generate SHA-256 Checksums
Write-Host "`nGenerating SHA-256 checksums..." -ForegroundColor Cyan
$checksumFile = "$prodDir/SHA256SUMS.txt"
$hashes = Get-ChildItem "$prodDir/*.jar" | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "$hash  $($_.Name)"
}
$hashes | Set-Content $checksumFile -Encoding utf8
Write-Host "Checksums written to: $checksumFile" -ForegroundColor Green
Get-Content $checksumFile | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

# 5. GitHub Release Deployment
if ($SkipGitHub) {
    Write-Host "`n[-SkipGitHub specified] Production bundle ready in dist/production." -ForegroundColor Yellow
    exit 0
}

Write-Host "`nDeploying to GitHub Releases for tag $Tag..." -ForegroundColor Cyan
$ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
if (-not $ghInstalled) {
    Write-Warning "GitHub CLI (gh) not found on PATH. Artifacts staged in dist/production for manual upload."
    exit 0
}

# Prepare release notes
$notesLines = @(
    "# HeapHammer v$modVersion - Official Production Release",
    "",
    "**Deterministic Minecraft server stress testing and retained-memory regression detection.**",
    "",
    "### Release Highlights",
    "- **Target Platform**: Minecraft $mcVersion (Fabric, Java 21)",
    "- **Zero-Minecraft Core Domain**: Hexagonal architecture with zero ``net.minecraft.*`` coupling in detection and scenario engines.",
    "- **Statistical Regression**: Ordinary Least Squares (y = mx + b) trend slope and plateau pattern detection vs GC noise.",
    "- **Production Safety**: Configurable safety ceilings (``config/heaphammer.json``), runtime memory circuit breaker, and automated crash recovery journal.",
    "- **Command Security**: Operator Level 2 gating and Fabric Permissions API / LuckPerms support.",
    "",
    "### Quickstart",
    "1. Drop ``heaphammer-$modVersion.jar`` into your dedicated server's ``mods/`` folder.",
    "2. Run ``/hh run chunks`` to simulate 72 hours of player exploration in 90 seconds.",
    "3. Check verdict with ``/hh report show last``.",
    "",
    "### Checksums (SHA-256)",
    '```',
    (Get-Content $checksumFile -Raw).TrimEnd(),
    '```'
)

$scratchDir = "$WorkspaceRoot/.scratch"
if (-not (Test-Path $scratchDir)) {
    New-Item -ItemType Directory -Path $scratchDir -Force | Out-Null
}
$notesFile = "$scratchDir/release_notes_$Tag.md"
$notesLines | Set-Content $notesFile -Encoding utf8

$releaseFiles = Get-ChildItem "$prodDir/*" | Select-Object -ExpandProperty FullName

# Check if release already exists
$existing = gh release view $Tag 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Release $Tag already exists. Uploading assets..." -ForegroundColor Yellow
    gh release upload $Tag @releaseFiles --clobber
} else {
    Write-Host "Creating new GitHub Release: $Tag..." -ForegroundColor Cyan
    $cmdArgs = @("release", "create", $Tag) + $releaseFiles + @("--title", $Title, "--notes-file", $notesFile)
    if ($Draft) {
        $cmdArgs += "--draft"
    }
    & gh @cmdArgs
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[SUCCESS] Production Release $Tag published successfully!" -ForegroundColor Green
    gh release view $Tag
} else {
    Write-Warning "GitHub release creation returned code $LASTEXITCODE."
}
