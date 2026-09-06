# tools/sync-version-branches.ps1
# Local companion script to synchronize master into version branches and verify tests

param (
    [string[]]$TargetBranches = @("ver/1.21.1", "ver/1.20.1"),
    [switch]$Push,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$WorkspaceRoot = Resolve-Path "$PSScriptRoot/.."
Set-Location $WorkspaceRoot

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  HeapHammer Multi-Version Local Branch Synchronization Tool    " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Check for uncommitted changes
$status = git status --porcelain
if ($status) {
    Write-Error "Working directory is not clean. Commit or stash changes before syncing."
    exit 1
}

$originalBranch = (git branch --show-current).Trim()
Write-Host "Current branch: $originalBranch" -ForegroundColor Gray

try {
    foreach ($branch in $TargetBranches) {
        Write-Host "`n-----------------------------------------------------------------" -ForegroundColor Magenta
        Write-Host " Syncing master into target branch: $branch" -ForegroundColor Magenta
        Write-Host "-----------------------------------------------------------------"

        # Check if branch exists locally
        $branchExists = git branch --list $branch
        if (-not $branchExists) {
            # Check if exists on remote
            $remoteExists = git branch -r --list "origin/$branch"
            if ($remoteExists) {
                Write-Host "  Checking out remote branch origin/$branch..." -ForegroundColor Gray
                git checkout -b $branch "origin/$branch"
            } else {
                Write-Host "  Branch $branch not found. Creating from master..." -ForegroundColor Yellow
                git checkout -b $branch master
            }
        } else {
            Write-Host "  Switching to branch: $branch" -ForegroundColor Gray
            git checkout $branch
        }

        if ($DryRun) {
            Write-Host "  [DryRun] Would merge master into $branch and execute test suite." -ForegroundColor Yellow
            continue
        }

        # Attempt merge from master
        Write-Host "  Merging master into $branch..." -ForegroundColor Cyan
        $mergeOutput = git merge master -m "chore(sync): automated merge from master into $branch" 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "  Merge conflict detected while merging master into $branch!"
            git merge --abort
            Write-Warning "  Merge aborted. Manual version adaptation required on $branch."
            continue
        }

        # Run verification tests
        Write-Host "  Running test suite verification on $branch..." -ForegroundColor Cyan
        ./gradlew test --no-daemon
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "  Test suite failed on $branch after merge!"
            git reset --hard HEAD~1
            Write-Warning "  Rolled back merge on $branch due to test failures."
            continue
        }

        Write-Host "  [SUCCESS] $branch cleanly merged and verified with ./gradlew test!" -ForegroundColor Green

        if ($Push) {
            Write-Host "  Pushing $branch to remote origin..." -ForegroundColor Yellow
            git push origin $branch
            Write-Host "  [SUCCESS] Pushed $branch to origin." -ForegroundColor Green
        }
    }
} finally {
    Write-Host "`nRestoring original branch: $originalBranch" -ForegroundColor Gray
    git checkout $originalBranch
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  Synchronization process completed.                             " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan
