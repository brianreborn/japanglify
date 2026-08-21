# Outbound kick watcher for win11-pixel.
# Poll GitHub for queued swarm-bench jobs or a recent kick comment.
# If Runner.Listener is down, start it. No inbound HTTPS.
# Grok CLI may start this; the owner never types pwsh.

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
    $runs = gh run list --repo $Repo --workflow swarm-conductor-uat.yml --status queued --limit 5 --json databaseId,status,name 2>$null | ConvertFrom-Json
    if ($runs) { return $true }
    $runs = gh run list --repo $Repo --workflow swarm-kick.yml --status queued --limit 5 --json databaseId 2>$null | ConvertFrom-Json
    if ($runs) { return $true }
    return $false
}

if ($args -contains "-Once") {
    if (Pending-Kick) { Start-Listener } else { Write-Host "no queued kick/UAT" }
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
