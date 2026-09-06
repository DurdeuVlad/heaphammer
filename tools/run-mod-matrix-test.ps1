# tools/run-mod-matrix-test.ps1
# Automated Multi-Mod Empirical Matrix Test Harness for HeapHammer

param (
    [string]$SpecificScenario = ""
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = Resolve-Path "$PSScriptRoot/.."
Set-Location $WorkspaceRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  HeapHammer Multi-Mod Matrix Automated Verification Harness     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Step 1: Build all testmods
Write-Host "`n[Step 1] Building synthetic test mod jars..." -ForegroundColor Yellow
./gradlew buildTestmods --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to build testmods"
    exit 1
}

$TestModsDir = "$WorkspaceRoot/build/testmods"
$RunModsDir = "$WorkspaceRoot/run/mods"
$ReportsDir = "$WorkspaceRoot/run/heaphammer/reports"
$OutputDir = "$WorkspaceRoot/build/matrix-reports"

if (-not (Test-Path $RunModsDir)) {
    New-Item -ItemType Directory -Path $RunModsDir -Force | Out-Null
}
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$MatrixScenarios = @(
    @{
        Name = "01_Baseline_Clean";
        Mods = @();
        ExpectedVerdict = "PASS";
        Description = "Vanilla server baseline with HeapHammer only";
    },
    @{
        Name = "02_SingleArea_ChunkCache";
        Mods = @("testmod-leak-chunkcache-1.0.0.jar");
        ExpectedVerdict = "SUSPICIOUS";
        Description = "Single-subsystem chunk cache leak mod";
    },
    @{
        Name = "03_MultiSubsystem_OmniTrack";
        Mods = @("testmod-leak-omnitrack-1.0.0.jar");
        ExpectedVerdict = "SUSPICIOUS";
        Description = "Multi-subsystem leak mod (chunks + entities + tick queue)";
    },
    @{
        Name = "04_CrossMod_ModA_Alone";
        Mods = @("testmod-crossmod-core-1.0.0.jar");
        ExpectedVerdict = "PASS";
        Description = "CrossMod Core Provider alone (no subscribers)";
    },
    @{
        Name = "05_CrossMod_ModB_Alone";
        Mods = @("testmod-crossmod-consumer-1.0.0.jar");
        ExpectedVerdict = "PASS";
        Description = "CrossMod Consumer alone (clean fallback mode)";
    },
    @{
        Name = "06_CrossMod_Collision";
        Mods = @("testmod-crossmod-core-1.0.0.jar", "testmod-crossmod-consumer-1.0.0.jar");
        ExpectedVerdict = "SUSPICIOUS";
        Description = "CrossMod Collision: Mod A + Mod B circular subscriber loop";
    }
)

Function Run-ServerScenario {
    param (
        [hashtable]$Scenario
    )

    $name = $Scenario.Name
    $mods = $Scenario.Mods
    $expected = $Scenario.ExpectedVerdict
    $desc = $Scenario.Description

    Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
    Write-Host " Running Scenario: $name ($desc)" -ForegroundColor Magenta
    Write-Host " Expected Verdict: $expected" -ForegroundColor Magenta
    Write-Host "-----------------------------------------------------------------"

    # Clean run/mods/
    Get-ChildItem -Path $RunModsDir -Filter "*.jar" | Remove-Item -Force

    # Copy required mods
    foreach ($m in $mods) {
        $sourceJar = "$TestModsDir/$m"
        if (-not (Test-Path $sourceJar)) {
            Write-Error "Testmod jar not found: $sourceJar"
            exit 1
        }
        Copy-Item -Path $sourceJar -Destination "$RunModsDir/$m" -Force
        Write-Host "  Staged mod: $m" -ForegroundColor Gray
    }

    # Record existing report files to detect new one
    $existingReports = @()
    if (Test-Path $ReportsDir) {
        $existingReports = Get-ChildItem -Path $ReportsDir -Filter "*.json" | ForEach-Object { $_.FullName }
    }

    # Launch server process
    Write-Host "  Starting dedicated server..." -ForegroundColor Gray
    $pinfo = New-Object System.Diagnostics.ProcessStartInfo
    $pinfo.FileName = "cmd.exe"
    $pinfo.Arguments = "/c gradlew.bat runServer --no-daemon 2>&1"
    $pinfo.WorkingDirectory = $WorkspaceRoot
    $pinfo.RedirectStandardInput = $true
    $pinfo.RedirectStandardOutput = $true
    $pinfo.RedirectStandardError = $false
    $pinfo.UseShellExecute = $false
    $pinfo.CreateNoWindow = $true

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $pinfo
    $proc.Start() | Out-Null

    $serverReady = $false
    $experimentDone = $false
    $reportPath = $null

    # Watchdog loop
    $timeoutSeconds = 120
    $startTime = [System.DateTime]::Now

    while (-not $proc.HasExited) {
        $line = $proc.StandardOutput.ReadLine()
        if ($line -ne $null) {
            # Log key events
            if ($line -match "Done \([0-9\.]+s\)! For help, type `"help`"") {
                $serverReady = $true
                Write-Host "  [Server Ready] Triggering HeapHammer run..." -ForegroundColor Green
                Start-Sleep -Seconds 2
                $proc.StandardInput.WriteLine("hh run chunks --iterations=5 --batch=10 --hold=5 --settle=10 --explicit-gc=true")
            }
            if ($line -match "Report saved successfully:\s*(.*\.json)") {
                $reportPath = $Matches[1].Trim()
                Write-Host "  [Experiment Completed] Report: $reportPath" -ForegroundColor Green
                $experimentDone = $true
                Start-Sleep -Seconds 2
                Write-Host "  Stopping server..." -ForegroundColor Gray
                $proc.StandardInput.WriteLine("stop")
            }
        }

        if (([System.DateTime]::Now - $startTime).TotalSeconds -gt $timeoutSeconds) {
            Write-Warning "  Scenario timed out after $timeoutSeconds seconds!"
            $proc.StandardInput.WriteLine("stop")
            Start-Sleep -Seconds 5
            if (-not $proc.HasExited) {
                $proc.Kill()
            }
            break
        }
    }

    $proc.WaitForExit(10000)

    # Locate newly created report
    $newReport = $null
    if ($reportPath -ne $null -and (Test-Path $reportPath)) {
        $newReport = $reportPath
    } else {
        $currentReports = Get-ChildItem -Path $ReportsDir -Filter "*.json" | ForEach-Object { $_.FullName }
        $diffReports = $currentReports | Where-Object { $existingReports -notcontains $_ }
        if ($diffReports.Count -gt 0) {
            $newReport = $diffReports[-1]
        }
    }

    if ($newReport -eq $null -or (-not (Test-Path $newReport))) {
        Write-Error "  FAILED: No report was generated for scenario $name"
        return $false
    }

    # Save to matrix reports
    $destReport = "$OutputDir/${name}.json"
    Copy-Item -Path $newReport -Destination $destReport -Force

    # Read and inspect JSON
    $json = Get-Content -Path $destReport -Raw | ConvertFrom-Json
    $verdict = $json.detection.classification
    $slope = $json.detection.slopeBytesPerCycle
    $slopeMb = [math]::Round($slope / (1024.0 * 1024.0), 2)
    $retainedChunks = $json.cleanupValidation.retainedChunks

    Write-Host "  Verdict: $verdict (Expected: $expected)" -ForegroundColor $(if ($verdict -eq $expected) { "Green" } else { "Red" })
    Write-Host "  Slope: $slopeMb MB/cycle | Retained Chunks: $retainedChunks" -ForegroundColor Gray

    if ($verdict -ne $expected) {
        Write-Error "Verdict mismatch for scenario $($name): expected $expected, got $verdict"
        return $false
    }

    return $true
}

# Main execution loop
$results = @{}
foreach ($scenario in $MatrixScenarios) {
    if ($SpecificScenario -ne "" -and $scenario.Name -ne $SpecificScenario) {
        continue
    }
    $success = Run-ServerScenario -Scenario $scenario
    $results[$scenario.Name] = $success
    if (-not $success) {
        Write-Error "Matrix test aborted due to scenario failure: $($scenario.Name)"
        exit 1
    }
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  All Matrix Scenarios Completed Successfully!                   " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
foreach ($k in $results.Keys) {
    Write-Host "  $k : PASS" -ForegroundColor Green
}
