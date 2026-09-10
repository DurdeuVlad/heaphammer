# tools/verify-live-server-commands.ps1
# Automated Live Minecraft Dedicated Server Verification Harness for HeapHammer Commands (1.7.10)

param (
    [int]$BootTimeoutSeconds = 180,
    [int]$CommandTimeoutSeconds = 25
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = Resolve-Path "$PSScriptRoot/.."
Set-Location $WorkspaceRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  HeapHammer Live Dedicated Server Command Verification (1.7.10) " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Step 1: Ensure run/mods/ is clean
$RunModsDir = "$WorkspaceRoot/run/mods"
if (Test-Path $RunModsDir) {
    Get-ChildItem -Path $RunModsDir -Filter "*.jar" | Remove-Item -Force -ErrorAction SilentlyContinue
}

# Step 2: Define Command Verification Matrix (1.7.10 supports 7 subcommands)
$TestCommands = @(
    @{ Cmd = "hh version"; Pattern = "HeapHammer v[0-9]+\.[0-9]+\.[0-9]+"; Description = "Version information display"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh help"; Pattern = "HeapHammer 1.7.10 Command Center"; Description = "Top-level help overview"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh status"; Pattern = "HeapHammer Status"; Description = "Idle experiment status check"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh diagnostics histogram"; Pattern = "JVM class histogram"; Description = "Diagnostic histogram trigger"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh run chunks --iterations=2 --batch=5"; Pattern = "Starting chunk leak benchmark"; Description = "Chunk workload run"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh run entities --iterations=2 --batch=5"; Pattern = "Starting entity benchmark"; Description = "Entity workload run"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh run blockentities --iterations=2 --batch=5"; Pattern = "Starting block entity benchmark"; Description = "Block entity workload run"; Timeout = $CommandTimeoutSeconds },
    @{ Cmd = "hh abort"; Pattern = "Abort requested"; Description = "Emergency abort and ticket release"; Timeout = $CommandTimeoutSeconds }
)

# Step 3: Launch the dedicated server
Write-Host "`n[Step 1] Launching Minecraft dedicated server..." -ForegroundColor Yellow

$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = "cmd.exe"
$pinfo.Arguments = "/c gradlew.bat runServer --no-daemon 2>&1"
$pinfo.WorkingDirectory = $WorkspaceRoot
$pinfo.RedirectStandardInput = $true
$pinfo.RedirectStandardOutput = $true
$pinfo.RedirectStandardError = $true
$pinfo.UseShellExecute = $false
$pinfo.CreateNoWindow = $true

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $pinfo
[void]$proc.Start()

$bootOutput = New-Object System.Collections.ArrayList
$bootStart = Get-Date
$booted = $false

while (-not $proc.HasExited) {
    $line = $proc.StandardOutput.ReadLine()
    if ($line -ne $null) {
        [void]$bootOutput.Add($line)
        Write-Host "  [INIT] $line" -ForegroundColor DarkGray
        if ($line -match "Done \(.*\)! for help") {
            $booted = $true
            break
        }
        if ($line -match "Starting Minecraft server on") {
            # 1.7.10 may not print "Done!" in nogui mode via stdin
            Start-Sleep -Seconds 5
            $booted = $true
            break
        }
    }
    $elapsed = ((Get-Date) - $bootStart).TotalSeconds
    if ($elapsed -gt $BootTimeoutSeconds) {
        break
    }
}

if (-not $booted) {
    Write-Host "`n[FAIL] Server did not boot within $BootTimeoutSeconds seconds" -ForegroundColor Red
    if (-not $proc.HasExited) { $proc.Kill() }
    exit 1
}

Write-Host "`n[SUCCESS] Server booted and ready for commands!" -ForegroundColor Green

# Step 4: Execute commands
Write-Host "`n[Step 2] Executing Command Verification Matrix ($($TestCommands.Count) commands)..." -ForegroundColor Yellow

$results = @()
$passCount = 0
$failCount = 0

foreach ($test in $TestCommands) {
    Write-Host "`n-----------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host " Command:     /$($test.Cmd)" -ForegroundColor White
    Write-Host " Description: $($test.Description)" -ForegroundColor White
    Write-Host " Expecting:   $($test.Pattern)" -ForegroundColor White

    $outputFile = "$WorkspaceRoot/run/logs/latest.log"
    $beforeLineCount = if (Test-Path $outputFile) { (Get-Content $outputFile | Measure-Object -Line).Lines } else { 0 }

    $proc.StandardInput.WriteLine($test.Cmd)

    $found = $false
    $foundLine = ""
    $cmdStart = Get-Date

    while (-not $proc.HasExited) {
        Start-Sleep -Milliseconds 200
        if (Test-Path $outputFile) {
            $currentContent = Get-Content $outputFile -ErrorAction SilentlyContinue
            $currentLineCount = ($currentContent | Measure-Object -Line).Lines
            if ($currentLineCount -gt $beforeLineCount) {
                $newLines = $currentContent | Select-Object -Skip $beforeLineCount
                foreach ($nl in $newLines) {
                    if ($nl -match $test.Pattern) {
                        $found = $true
                        $foundLine = $nl
                        break
                    }
                }
            }
        }
        if ($found) { break }
        $elapsed = (Get-Date) - $cmdStart | ForEach-Object TotalSeconds
        if ($elapsed -gt $test.Timeout) { break }
    }

    if ($found) {
        Write-Host "  -> PASS: $foundLine" -ForegroundColor Green
        $passCount++
        $results += [PSCustomObject]@{ Command = "/$($test.Cmd)"; Status = "PASS"; Output = $foundLine }
    } else {
        Write-Host "  -> FAIL: Timed out waiting for '$($test.Pattern)' in $($test.Timeout)s" -ForegroundColor Red
        $failCount++
        $results += [PSCustomObject]@{ Command = "/$($test.Cmd)"; Status = "FAIL"; Output = "Timeout" }
    }
}

# Step 5: Stop server
Write-Host "`n[ALL COMMANDS EXECUTED] Stopping dedicated server gracefully..." -ForegroundColor Yellow
$proc.StandardInput.WriteLine("stop")
Start-Sleep -Seconds 5
if (-not $proc.HasExited) { $proc.Kill() }

# Step 6: Check for crashes
Write-Host "`n[Step 3] Checking latest.log for hidden exceptions..." -ForegroundColor Yellow
$crashCount = 0
if (Test-Path "$WorkspaceRoot/run/logs/latest.log") {
    $logContent = Get-Content "$WorkspaceRoot/run/logs/latest.log" -ErrorAction SilentlyContinue
    $crashPatterns = @("Exception", "CRASH", "FATAL", "java\.lang\.\w+Exception")
    foreach ($line in $logContent) {
        foreach ($pattern in $crashPatterns) {
            if ($line -match $pattern) { $crashCount++ }
        }
    }
}

# Step 7: Report
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  Command Verification Matrix Results                            " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$results | Format-Table -AutoSize

Write-Host "`nTest Execution Summary:"
Write-Host "  Total Commands Tested: $($TestCommands.Count)"
Write-Host "  Passed:                $passCount"
Write-Host "  Failed:                $failCount"
Write-Host "  Crash/Exceptions:      $crashCount"

if ($passCount -eq $TestCommands.Count -and $crashCount -eq 0) {
    Write-Host "`n[FINAL VERDICT] ALL $($TestCommands.Count) COMMANDS WORKING - ZERO CRASHES - SERVER STABLE!" -ForegroundColor Green
    exit 0
} else {
    Write-Error "Verification failed: Expected $($TestCommands.Count) passing commands, got $passCount."
    exit 1
}
