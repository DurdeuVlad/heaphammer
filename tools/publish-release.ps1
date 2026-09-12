# tools/publish-release.ps1
# Multi-version production packaging, checksum generation, and GitHub release deployment tool

param (
    [string]$Tag = "v1.0.0",
    [string]$Title = "HeapHammer v1.0.0 - Production Release",
    [string]$PrimaryLoader = "fabric",
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

# Copy primary jar with loader-qualified name: heaphammer-<mc>-<loader>-<ver>.jar
Copy-Item $primaryJar "$prodDir/heaphammer-$mcVersion-$PrimaryLoader-$modVersion.jar" -Force

# Build and stage nested loader builds present in this checkout (loaders/*/)
function Invoke-NestedLoaderBuilds {
    param([string]$Root, [string]$MC, [string]$ModVer)
    $loadersDir = Join-Path $Root "loaders"
    if (-not (Test-Path $loadersDir)) { return }
    Get-ChildItem $loadersDir -Directory | ForEach-Object {
        $loader = $_.Name
        if (Test-Path "$($_.FullName)/settings.gradle") {
            Write-Host "  Building nested loader: $loader" -ForegroundColor Cyan
            Push-Location $_.FullName
            try {
                # Prefer a nested wrapper when present — ForgeGradle (1.16.5)
                # requires Gradle 8.x while the repo-root wrapper is Gradle 9.
                $gradlew = if (Test-Path "$($_.FullName)/gradlew.bat") { "$($_.FullName)/gradlew.bat" } else { "$Root/gradlew.bat" }
                & $gradlew build -x test --no-daemon
                if ($LASTEXITCODE -ne 0) { Write-Warning "  Nested loader build failed: $loader"; return }
            } finally {
                Pop-Location
            }
            $loaderJar = Get-ChildItem "$($_.FullName)/build/libs/*.jar" -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -notmatch "sources|dev" } | Select-Object -First 1
            if ($loaderJar) {
                Copy-Item $loaderJar.FullName "$prodDir/heaphammer-$MC-$loader-$ModVer.jar" -Force
                Write-Host "  [OK] Staged: heaphammer-$MC-$loader-$ModVer.jar" -ForegroundColor Green
            } else {
                Write-Warning "  No JAR produced by nested loader build: $loader"
            }
        }
    }
}

Invoke-NestedLoaderBuilds -Root $WorkspaceRoot -MC $mcVersion -ModVer $modVersion

# 4. Build other supported Minecraft versions if -BuildAll requested
if ($BuildAll) {
    Write-Host "`n[-BuildAll specified] Compiling multi-version LTS release binaries..." -ForegroundColor Cyan
    $versionBranches = @(
        @{ Branch = "ver/1.21.4"; MC = "1.21.4"; Loader = "fabric" },
        @{ Branch = "ver/1.20.6"; MC = "1.20.6"; Loader = "fabric" },
        @{ Branch = "ver/1.20.4"; MC = "1.20.4"; Loader = "fabric" },
        @{ Branch = "ver/1.20.1"; MC = "1.20.1"; Loader = "fabric" },
        @{ Branch = "ver/1.19.4"; MC = "1.19.4"; Loader = "fabric" },
        @{ Branch = "ver/1.19.2"; MC = "1.19.2"; Loader = "fabric" },
        @{ Branch = "ver/1.18.2"; MC = "1.18.2"; Loader = "fabric" },
        @{ Branch = "ver/1.17.1"; MC = "1.17.1"; Loader = "fabric" },
        @{ Branch = "ver/1.16.5"; MC = "1.16.5"; Loader = "fabric" },
        @{ Branch = "ver/1.15.2"; MC = "1.15.2"; Loader = "fabric" },
        @{ Branch = "ver/1.14.4"; MC = "1.14.4"; Loader = "fabric" },
        @{ Branch = "ver/1.12.2-forge"; MC = "1.12.2"; Loader = "forge" },
        @{ Branch = "ver/1.7.10-forge"; MC = "1.7.10"; Loader = "forge" }
    )

    $wtBase = "$WorkspaceRoot/.worktrees"
    foreach ($entry in $versionBranches) {
        $targetMC = $entry.MC
        $targetBranch = $entry.Branch
        $targetLoader = $entry.Loader
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
                Copy-Item $builtJar "$prodDir/heaphammer-$targetMC-$targetLoader-$modVersion.jar" -Force
                Write-Host "  [OK] Staged: heaphammer-$targetMC-$targetLoader-$modVersion.jar" -ForegroundColor Green
            } else {
                Write-Warning "Could not find built JAR for $targetMC in $wtPath/build/libs"
            }

            # Build any nested loader builds present on that branch
            Invoke-NestedLoaderBuilds -Root $wtPath -MC $targetMC -ModVer $modVersion
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

# Prepare multi-version release notes — the supported-targets table is generated
# from the actual staged heaphammer-<mc>-<loader>-<ver>.jar files so the notes can
# never overstate loader coverage.
$jarRows = Get-ChildItem "$prodDir/heaphammer-*.jar" | ForEach-Object {
    if ($_.Name -match '^heaphammer-(.+)-(fabric|neoforge|forge)-[^-]+\.jar$') {
        [PSCustomObject]@{ MC = $Matches[1]; Loader = $Matches[2]; File = $_.Name }
    }
} | Sort-Object { [version]$_.MC } -Descending, Loader

$tableLines = $jarRows | ForEach-Object {
    "| **$($_.MC)** | $($_.Loader) | ``$($_.File)`` |"
}

$notesLines = @(
    "# HeapHammer v$modVersion - Official Production Release",
    "",
    "**Deterministic Minecraft server stress testing and retained-memory regression detection.**",
    "",
    "### Supported Minecraft Versions",
    "HeapHammer v$modVersion provides dedicated, precompiled binaries for $($jarRows.Count) version/loader targets:",
    "",
    "| Minecraft Version | Mod Loader | Release Binary |",
    "|---|---|---|"
) + $tableLines + @(
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
