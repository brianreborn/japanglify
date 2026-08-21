# Supervisor for win11-pixel. Grok CLI is not in this path after first start.
#
# Always keep Runner.Listener up for this logon session (empty mailbox too).
# Also poll GitHub: after ubuntu dispatch the WORKFLOW RUN is in_progress
# while the swarm-bench JOB is still queued.
# No inbound HTTPS. Owner never types pwsh.

$ErrorActionPreference = "Stop"
$Repo = "brianreborn/japanglify"
$Root = "C:\actions-runner"
$Loop = Join-Path $Root "swarm-run-loop.cmd"
$Run = Join-Path $Root "run.cmd"

function Disarmed {
    return Test-Path (Join-Path $Root ".swarm-disarmed")
}

function Listener-Up {
    return [bool](Get-CimInstance Win32_Process -Filter "Name='Runner.Listener.exe'" -ErrorAction SilentlyContinue)
}

function Loop-Up {
    $hit = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^(cmd|pwsh|powershell)\.exe$' -and $_.CommandLine -and ($_.CommandLine -match 'swarm-run-loop')
    })
    return ($hit.Count -gt 0)
}

function Start-Listener {
    if (Disarmed) {
        Write-Host "disarmed — not starting listener"
        return
    }
    if (Listener-Up) {
        Write-Host "Runner.Listener already running"
        return
    }
    if (Loop-Up) {
        Write-Host "swarm-run-loop already running (listener will come back)"
        return
    }
    if (Test-Path $Loop) {
        Write-Host "kick: starting $Loop (hidden)"
        Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $Loop) -WorkingDirectory $Root -WindowStyle Hidden
        return
    }
    if (-not (Test-Path $Run)) { throw "missing $Run — register the runner first" }
    Write-Host "kick: starting $Run"
    Start-Process -FilePath $Run -WorkingDirectory $Root -WindowStyle Hidden
}

function Pending-Kick {
    $workflows = @("swarm-conductor-uat.yml", "swarm-kick.yml")
    $states = @("queued", "waiting", "in_progress")
    foreach ($wf in $workflows) {
        foreach ($st in $states) {
            $runs = gh run list --repo $Repo --workflow $wf --status $st --limit 5 --json databaseId,status 2>$null | ConvertFrom-Json
            if ($runs -and @($runs).Count -gt 0) { return $true }
        }
    }
    return $false
}

if (Disarmed) {
    Write-Host "disarmed ($Root\.swarm-disarmed) — watch exits"
    exit 0
}

if ($args -contains "-Once") {
    $pending = Pending-Kick
    if ($pending) { Write-Host "pending UAT/kick on GitHub" } else { Write-Host "no queued/in_progress kick/UAT" }
    Start-Listener
    Start-Sleep -Seconds 2
    if (Listener-Up) { Write-Host "listener up" } else { Write-Warning "listener still down" }
    exit 0
}

Write-Host "swarm-kick-watch polling $Repo (Ctrl+C to stop); stop with swarm-bench-stop"
while ($true) {
    try {
        if (Disarmed) {
            Write-Host "disarmed — watch exits"
            exit 0
        }
        if (-not (Listener-Up)) { Start-Listener }
        if (Pending-Kick -and -not (Listener-Up)) { Start-Listener }
    } catch {
        Write-Warning $_
    }
    Start-Sleep -Seconds 20
}
