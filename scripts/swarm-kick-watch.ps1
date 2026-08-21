# Outbound kick watcher for win11-pixel.
# Poll GitHub for UAT/kick runs that still need the listener.
#
# CRITICAL: after ubuntu `dispatch` succeeds, the WORKFLOW RUN is
# `in_progress` while the swarm-bench JOB is still queued. Looking
# only at `--status queued` misses the stuck listener case.
# No inbound HTTPS. Grok CLI may start this; the owner never types pwsh.

$ErrorActionPreference = "Stop"
$Repo = "brianreborn/japanglify"
$Root = "C:\actions-runner"
$Run = Join-Path $Root "run.cmd"

function Listener-Up {
    return [bool](Get-CimInstance Win32_Process -Filter "Name='Runner.Listener.exe'" -ErrorAction SilentlyContinue)
}

function Start-Listener {
    if (Listener-Up) {
        Write-Host "Runner.Listener already running"
        return
    }
    if (-not (Test-Path $Run)) { throw "missing $Run — register the runner first" }
    Write-Host "kick: starting $Run"
    Start-Process -FilePath $Run -WorkingDirectory $Root -WindowStyle Minimized
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

if ($args -contains "-Once") {
    if (Pending-Kick) { Start-Listener } else { Write-Host "no queued/in_progress kick/UAT" }
    if (Listener-Up) { Write-Host "listener up" } else { Write-Warning "listener still down" }
    exit 0
}

Write-Host "swarm-kick-watch polling $Repo (Ctrl+C to stop)"
while ($true) {
    try {
        if (Pending-Kick) { Start-Listener }
    } catch {
        Write-Warning $_
    }
    Start-Sleep -Seconds 20
}
