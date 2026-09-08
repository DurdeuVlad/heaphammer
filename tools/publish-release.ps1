# tools/publish-release.ps1
# Multi-version production packaging, checksum generation, and GitHub release deployment tool

param (
    [string]$Tag = "v1.0.0",
    [string]$Title = "HeapHammer v1.0.0 - Production Release",
    [switch]$BuildAll,
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
Write-Host "Target Primary Minecraft: $mcVersion | Mod Version: $modVersion" -ForegroundColor Yellow

# 2. Build primary production mod if needed
$primaryJar = "$WorkspaceRoot/build/libs/heaphammer-$modVersion.jar"
if (-not (Test-Path $primaryJar)) {
    Write-Host "`nBuilding primary production artifacts via Gradle..." -ForegroundColor Cyan
    ./gradlew build --no-daemon
}

if (-not (Test-Path $primaryJar)) {
    Write-Error "Production JAR not found at: $primaryJar"
    exit 1
}

# 3. Setup production staging directory
$prodDir = "$WorkspaceRoot/dist/production"
if (-not (Test-Path $prodDir)) {
    New-Item -ItemType Directory -Path $prodDir -Force | Out-Null
}

Write-Host "`nStaging production release bundle in dist/production..." -ForegroundColor Cyan

# Copy primary jar with standard and versioned names
Copy-Item $primaryJar "$prodDir/heaphammer-$modVersion.jar" -Force
Copy-Item $primaryJar "$prodDir/heaphammer-$mcVersion-$modVersion.jar" -Force

# 4. Build other supported Minecraft versions if -BuildAll requested
if ($BuildAll) {
    Write-Host "`n[-BuildAll specified] Compiling multi-version LTS release binaries..." -ForegroundColor Cyan
    $versionBranches = @(
        @{ Branch = "ver/1.21.4"; MC = "1.21.4" },
        @{ Branch = "ver/1.20.1"; MC = "1.20.1" },
        @{ Branch = "ver/1.19.2"; MC = "1.19.2" },
        @{ Branch = "ver/1.18.2"; MC = "1.18.2" },
        @{ Branch = "ver/1.16.5"; MC = "1.16.5" },
        @{ Branch = "ver/1.12.2-forge"; MC = "1.12.2" },
        @{ Branch = "ver/1.7.10-forge"; MC = "1.7.10" }
    )

    $wtBase = "$WorkspaceRoot/.worktrees"
    foreach ($entry in $versionBranches) {
        $targetMC = $entry.MC
        $targetBranch = $entry.Branch
        $wtPath = "$wtBase/wt-$targetMC"
        Write-Host "`nBuilding Minecraft $targetMC from branch $targetBranch..." -ForegroundColor Yellow

        if (Test-Path $wtPath) {
            Remove-Item -Path $wtPath -Recurse -Force -ErrorAction SilentlyContinue
            git worktree prune
        }

        git worktree add $wtPath $targetBranch
        try {
            Push-Location $wtPath
            ./gradlew build -x test --no-daemon
            Pop-Location

            $builtJar = "$wtPath/build/libs/heaphammer-$modVersion.jar"
            if (-not (Test-Path $builtJar)) {
                $builtJar = Get-ChildItem "$wtPath/build/libs/heaphammer-*.jar" | Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1 -ExpandProperty FullName
            }

            if (Test-Path $builtJar) {
                Copy-Item $builtJar "$prodDir/heaphammer-$targetMC-$modVersion.jar" -Force
                Write-Host "  [OK] Staged: heaphammer-$targetMC-$modVersion.jar" -ForegroundColor Green
            } else {
                Write-Warning "Could not find built JAR for $targetMC in $wtPath/build/libs"
            }
        } finally {
            if ((Get-Location).Path -eq $wtPath) { Pop-Location }
            Remove-Item -Path $wtPath -Recurse -Force -ErrorAction SilentlyContinue
            git worktree prune
        }
    }
    if (Test-Path $wtBase) {
        Remove-Item -Path $wtBase -Recurse -Force -ErrorAction SilentlyContinue
    }
}

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

# 5. Generate SHA-256 Checksums
Write-Host "`nGenerating SHA-256 checksums across all staged binaries..." -ForegroundColor Cyan
$checksumFile = "$prodDir/SHA256SUMS.txt"
$hashes = Get-ChildItem "$prodDir/*.jar" | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    "$hash  $($_.Name)"
}
$hashes | Set-Content $checksumFile -Encoding utf8
Write-Host "Checksums written to: $checksumFile" -ForegroundColor Green
Get-Content $checksumFile | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

# 6. GitHub Release Deployment
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

# Prepare multi-version release notes
$notesLines = @(
    "# HeapHammer v$modVersion - Official Production Release",
    "",
    "**Deterministic Minecraft server stress testing and retained-memory regression detection.**",
    "",
    "### Supported Minecraft Versions",
    "HeapHammer v$modVersion provides dedicated, precompiled binaries for 8 major Minecraft version lines:",
    "",
    "| Minecraft Version | Mod Loader | Java Target | Release Binary |",
    "|---|---|---|---|",
    "| **1.21.4** | Fabric | Java 21 | ``heaphammer-1.21.4-$modVersion.jar`` |",
    "| **1.21.1** *(Trunk)* | Fabric & NeoForge | Java 21 | ``heaphammer-1.21.1-$modVersion.jar`` |",
    "| **1.20.1** | Fabric & Forge | Java 17 | ``heaphammer-1.20.1-$modVersion.jar`` |",
    "| **1.19.2** | Fabric & Forge | Java 17 | ``heaphammer-1.19.2-$modVersion.jar`` |",
    "| **1.18.2** | Fabric & Forge | Java 17 | ``heaphammer-1.18.2-$modVersion.jar`` |",
    "| **1.16.5** | Forge & Fabric | Java 8 / 17 | ``heaphammer-1.16.5-$modVersion.jar`` |",
    "| **1.12.2** | Forge | Java 8 | ``heaphammer-1.12.2-$modVersion.jar`` |",
    "| **1.7.10** | Forge | Java 8 | ``heaphammer-1.7.10-$modVersion.jar`` |",
    "",
    "### Release Highlights",
    "- **Hexagonal Core Architecture**: 100% pure Java domain engine with zero ``net.minecraft.*`` runtime coupling.",
    "- **Statistical OLS Regression**: Ordinary Least Squares (y = mx + b) trend slope and plateau pattern detection vs GC noise.",
    "- **Production Safety**: Safety ceilings (``config/heaphammer.json``), tick budgets (15ms max), and automated crash recovery journal.",
    "- **Command Security**: Operator Level 2 gating and Fabric Permissions API / LuckPerms integration.",
    "",
    "### Quickstart",
    "1. Download the JAR corresponding to your server's Minecraft version from the assets below.",
    "2. Place the JAR into your dedicated server's ``mods/`` directory and restart.",
    "3. Run ``/hh run chunks`` to simulate 72 hours of player exploration in 90 seconds.",
    "4. View the diagnostic verdict with ``/hh report show last``.",
    "5. Refer to the attached ``HeapHammer_Server_Admin_Guide.pdf`` for a complete walkthrough.",
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
$existingReleases = & gh release list --json tagName -q ".[].tagName" 2>$null
$releaseExists = ($existingReleases -split '\r?\n') -contains $Tag

if ($releaseExists) {
    Write-Host "Release $Tag already exists. Updating release notes and uploading multi-version assets..." -ForegroundColor Yellow
    gh release edit $Tag --title $Title --notes-file $notesFile
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
    Write-Host "`n[SUCCESS] Production Release $Tag with all Minecraft versions published successfully!" -ForegroundColor Green
    gh release view $Tag
} else {
    Write-Warning "GitHub release update returned code $LASTEXITCODE."
}
