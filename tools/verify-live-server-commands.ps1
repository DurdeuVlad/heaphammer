# tools/verify-live-server-commands.ps1
# Automated Live Minecraft Dedicated Server Verification Harness for HeapHammer Commands

param (
    [int]$BootTimeoutSeconds = 180,
    [int]$CommandTimeoutSeconds = 25,
    [int]$RunTimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = Resolve-Path "$PSScriptRoot/.."
Set-Location $WorkspaceRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  HeapHammer Live Dedicated Server Command & Crash Verification  " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Step 1: Ensure run/mods/ is clean
$RunModsDir = "$WorkspaceRoot/run/mods"
if (Test-Path $RunModsDir) {
    Get-ChildItem -Path $RunModsDir -Filter "*.jar" | Remove-Item -Force -ErrorAction SilentlyContinue
}

# Step 2: Define Command Verification Matrix
$TestCommands = @(
    @{
        Cmd = "hh version";
        Pattern = "HeapHammer v[0-9]+\.[0-9]+\.[0-9]+";
        Description = "Version information display";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh help";
        Pattern = "HeapHammer Commands";
        Description = "Top-level help overview";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh capabilities";
        Pattern = "HeapHammer Capabilities:";
        Description = "Platform adapter capabilities";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh doctor";
        Pattern = "HeapHammer Doctor:";
        Description = "Diagnostic health check";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh status";
        Pattern = "No experiment currently active";
        Description = "Idle experiment status check";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh metrics";
        Pattern = "Metrics: Heap:";
        Description = "JVM and chunk metrics inspection";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh inspect mods";
        Pattern = "Installed Mods \(";
        Description = "Active mod list scanner";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh config show";
        Pattern = "HeapHammer Configuration";
        Description = "Safety ceiling configuration dump";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh config reload";
        Pattern = "reloaded successfully";
        Description = "Hot-reload config from disk";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh adapters list";
        Pattern = "workload adapters";
        Description = "Workload adapter registry check";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh diagnostics histogram";
        Pattern = "Capturing JVM class histogram|Top \d+ Classes|histogram";
        Description = "JVM class histogram collection";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh scenario list";
        Pattern = "Available Scenarios:";
        Description = "Scenario engine catalog";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh scenario describe chunks";
        Pattern = "Scenario: chunks";
        Description = "Chunk scenario documentation";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh scenario describe entities";
        Pattern = "Scenario: entities";
        Description = "Entity scenario documentation";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh scenario describe blockentities";
        Pattern = "Scenario: blockentities";
        Description = "Block entity scenario documentation";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh plan chunks --radius=4 --iterations=2";
        Pattern = "Plan Created:";
        Description = "Dry-run chunk scenario planner";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh plan entities --iterations=2 --batch=5";
        Pattern = "Entity Plan Created:";
        Description = "Dry-run entity churn planner";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh plan blockentities --iterations=2 --batch=5";
        Pattern = "Block Entity Plan Created:";
        Description = "Dry-run block entity planner";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh run chunks --iterations=2 --batch=5 --hold=2 --settle=2 --radius=4 --explicit-gc=true";
        Pattern = "Report saved successfully";
        Description = "Live chunk workload run and ticket lifecycle";
        Timeout = $RunTimeoutSeconds
    },
    @{
        Cmd = "hh report list";
        Pattern = "Saved Reports|No reports found";
        Description = "Saved report enumeration";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh report show last";
        Pattern = "=== HeapHammer Report:|Verdict:";
        Description = "Report card inspection and OLS verdict";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh report export last";
        Pattern = "=== HeapHammer Report:|Verdict:";
        Description = "Report summary export";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh run entities --iterations=2 --batch=5 --hold=2 --settle=2 --explicit-gc=true";
        Pattern = "Report saved successfully";
        Description = "Live entity churn workload and cleanup";
        Timeout = $RunTimeoutSeconds
    },
    @{
        Cmd = "hh run blockentities --iterations=2 --batch=5 --hold=2 --settle=2 --explicit-gc=true";
        Pattern = "Report saved successfully";
        Description = "Live block entity stress workload and cleanup";
        Timeout = $RunTimeoutSeconds
    },
    @{
        Cmd = "hh checkpoint";
        Pattern = "Recorded manual checkpoint";
        Description = "Manual memory checkpoint recording";
        Timeout = $CommandTimeoutSeconds
    },
    @{
        Cmd = "hh cleanup";
        Pattern = "Cleanup complete";
        Description = "Emergency cleanup and ticket release sweep";
        Timeout = $CommandTimeoutSeconds
    }
)

# Step 3: Launch Dedicated Server Process
Write-Host "`n[Step 1] Launching Minecraft dedicated server..." -ForegroundColor Yellow

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
$bootStart = [System.DateTime]::Now
$cmdIndex = 0
$currentCmdStart = [System.DateTime]::Now
$results = [System.Collections.Generic.List[PSCustomObject]]::new()
$crashDetected = $false
$crashDetails = @()

Write-Host "Waiting for server to complete boot sequence..." -ForegroundColor Gray

# Use asynchronous non-blocking stream reader task
$lineTask = $proc.StandardOutput.ReadLineAsync()

while (-not $proc.HasExited) {
    if ($lineTask.Wait(100)) {
        $line = $lineTask.Result
        $lineTask = $proc.StandardOutput.ReadLineAsync()

        if ($line -ne $null) {
            if ($line -match "NullPointerException|FatalException|CrashReport") {
                $crashDetected = $true
                $crashDetails += $line
                Write-Host "    [CRASH DETECTED] $line" -ForegroundColor Red
            }

            # Detect server boot completion
            if (-not $serverReady) {
                if ($line -match "HeapHammer|Registered|Starting Minecraft|Loading properties|Starting Minecraft server") {
                    Write-Host "  [INIT] $line" -ForegroundColor DarkGray
                }
                if ($line -match "Done \([0-9\.]+s\)! For help, type `"help`"") {
                    $serverReady = $true
                    $elapsedBoot = [math]::Round(([System.DateTime]::Now - $bootStart).TotalSeconds, 1)
                    Write-Host "`n[SUCCESS] Server booted and ready for commands in ${elapsedBoot}s!" -ForegroundColor Green
                    Write-Host "`n[Step 2] Executing Command Verification Matrix ($($TestCommands.Count) commands)..." -ForegroundColor Yellow

                    Start-Sleep -Seconds 1
                    $activeTest = $TestCommands[$cmdIndex]
                    Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
                    Write-Host " [1/$($TestCommands.Count)] Command:     /$($activeTest.Cmd)" -ForegroundColor White
                    Write-Host " Description: $($activeTest.Description)" -ForegroundColor Gray
                    Write-Host " Expecting:   $($activeTest.Pattern)" -ForegroundColor DarkGray
                    $currentCmdStart = [System.DateTime]::Now
                    $proc.StandardInput.WriteLine($activeTest.Cmd)
                    continue
                }
            }

            # Evaluate active command response
            if ($serverReady -and $cmdIndex -lt $TestCommands.Count) {
                $activeTest = $TestCommands[$cmdIndex]
                if ($line -match $activeTest.Pattern) {
                    $elapsed = [math]::Round(([System.DateTime]::Now - $currentCmdStart).TotalSeconds, 2)
                    Write-Host "  -> PASS in ${elapsed}s: $line" -ForegroundColor Green
                    $results.Add([PSCustomObject]@{
                        Command = "/$($activeTest.Cmd)"
                        Status = "PASS"
                        Duration = "${elapsed}s"
                        Output = $line.Trim()
                    })
                    $cmdIndex++
                    if ($cmdIndex -lt $TestCommands.Count) {
                        $nextTest = $TestCommands[$cmdIndex]
                        Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
                        Write-Host " [$($cmdIndex+1)/$($TestCommands.Count)] Command:     /$($nextTest.Cmd)" -ForegroundColor White
                        Write-Host " Description: $($nextTest.Description)" -ForegroundColor Gray
                        Write-Host " Expecting:   $($nextTest.Pattern)" -ForegroundColor DarkGray
                        $currentCmdStart = [System.DateTime]::Now
                        Start-Sleep -Milliseconds 200
                        $proc.StandardInput.WriteLine($nextTest.Cmd)
                    } else {
                        Write-Host "`n[ALL COMMANDS EXECUTED] Stopping dedicated server gracefully..." -ForegroundColor Green
                        Start-Sleep -Seconds 2
                        $proc.StandardInput.WriteLine("stop")
                    }
                }
            }
        }
    } else {
        # Check command timeout if no lines received
        if ($serverReady -and $cmdIndex -lt $TestCommands.Count) {
            $activeTest = $TestCommands[$cmdIndex]
            if (([System.DateTime]::Now - $currentCmdStart).TotalSeconds -gt $activeTest.Timeout) {
                $elapsed = [math]::Round(([System.DateTime]::Now - $currentCmdStart).TotalSeconds, 2)
                Write-Host "  -> FAIL: Timed out waiting for '$($activeTest.Pattern)' in ${elapsed}s" -ForegroundColor Red
                $results.Add([PSCustomObject]@{
                    Command = "/$($activeTest.Cmd)"
                    Status = "FAIL"
                    Duration = "${elapsed}s"
                    Output = "TIMEOUT (Pattern: $($activeTest.Pattern))"
                })
                $cmdIndex++
                if ($cmdIndex -lt $TestCommands.Count) {
                    $nextTest = $TestCommands[$cmdIndex]
                    Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
                    Write-Host " [$($cmdIndex+1)/$($TestCommands.Count)] Command:     /$($nextTest.Cmd)" -ForegroundColor White
                    Write-Host " Description: $($nextTest.Description)" -ForegroundColor Gray
                    Write-Host " Expecting:   $($nextTest.Pattern)" -ForegroundColor DarkGray
                    $currentCmdStart = [System.DateTime]::Now
                    Start-Sleep -Milliseconds 200
                    $proc.StandardInput.WriteLine($nextTest.Cmd)
                } else {
                    Write-Host "`n[ALL COMMANDS EXECUTED] Stopping dedicated server gracefully..." -ForegroundColor Green
                    Start-Sleep -Seconds 2
                    $proc.StandardInput.WriteLine("stop")
                }
            }
        }
    }
}

$proc.WaitForExit(5000)

# Step 4: Log File Crash Analysis
Write-Host "`n[Step 3] Checking latest.log for hidden exceptions..." -ForegroundColor Yellow
$latestLog = "$WorkspaceRoot/run/logs/latest.log"
$logExceptions = @()
if (Test-Path $latestLog) {
    $logLines = Get-Content $latestLog
    $logExceptions = $logLines | Where-Object {
        $_ -match "NullPointerException|FatalException|CrashReport" -and
        $_ -notmatch "expected in test|synthetic"
    }
}

# Step 5: Results Summary Table
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  Command Verification Matrix Results                            " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$results | Format-Table -Property Command, Status, Duration, Output -AutoSize

$passCount = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$failCount = $TestCommands.Count - $passCount

Write-Host "`nTest Execution Summary:" -ForegroundColor White
Write-Host "  Total Commands Tested: $($TestCommands.Count)" -ForegroundColor White
Write-Host "  Passed:                $passCount" -ForegroundColor Green
Write-Host "  Failed:                $failCount" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "  Crash/Exceptions:      $($logExceptions.Count)" -ForegroundColor $(if ($logExceptions.Count -eq 0) { "Green" } else { "Red" })

if ($logExceptions.Count -gt 0) {
    Write-Warning "Detected unhandled exceptions in log:"
    $logExceptions | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
}

if ($passCount -eq $TestCommands.Count -and $logExceptions.Count -eq 0 -and -not $crashDetected) {
    Write-Host "`n[FINAL VERDICT] ALL 26 COMMANDS WORKING - ZERO CRASHES - SERVER STABLE!" -ForegroundColor Green
    exit 0
} else {
    Write-Error "Verification failed: Expected $($TestCommands.Count) passing commands, got $passCount."
    exit 1
}
