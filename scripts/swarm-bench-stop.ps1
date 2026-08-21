# Idle SHALOM: Grok down => listener down. Clears HKCU keep-alive so a
# disconnected Runner.Listener cannot come back and grab a queued /uat.
$ErrorActionPreference = "Continue"
$Root = "C:\actions-runner"
$RunKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"

function Ensure-ToolPath {
    $add = @(
        "$env:SystemRoot\System32",
        "$env:SystemRoot\System32\WindowsPowerShell\v1.0",
        "${env:ProgramFiles}\Git\cmd",
        "${env:ProgramFiles}\Git\bin",
        "${env:ProgramFiles}\PowerShell\7"
    ) | Where-Object { $_ -and (Test-Path $_) }
    $env:PATH = ($add + @($env:PATH)) -join ";"
}
Ensure-ToolPath

New-Item -ItemType Directory -Force -Path $Root | Out-Null
$stamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"
Set-Content -Path (Join-Path $Root ".swarm-disarmed") -Value "disarmed $stamp`nGrok down => listener down.`n" -Encoding ascii
Write-Host "wrote $Root\.swarm-disarmed"

foreach ($name in @("swarm-bench-loop", "swarm-kick-watch")) {
    if (Get-ItemProperty -Path $RunKey -Name $name -ErrorAction SilentlyContinue) {
        Remove-ItemProperty -Path $RunKey -Name $name -Force
        Write-Host "removed HKCU Run $name"
    }
}

try {
    Unregister-ScheduledTask -TaskName "swarm-bench-runner" -Confirm:$false -ErrorAction SilentlyContinue
} catch {}

function Stop-Matching($pattern) {
    $hits = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -and ($_.CommandLine -match $pattern)
    })
    foreach ($h in $hits) {
        Write-Host "kill pid=$($h.ProcessId) $($h.Name) $pattern"
        Stop-Process -Id $h.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

Stop-Matching 'swarm-kick-watch'
Stop-Matching 'swarm-run-loop'
Get-Process -Name "Runner.Listener","Runner.Worker" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "kill $($_.Name) pid=$($_.Id)"
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 1
$left = @(Get-Process -Name "Runner.Listener" -ErrorAction SilentlyContinue)
if ($left.Count -gt 0) {
    Write-Warning "Runner.Listener still running: $($left.Id -join ',')"
    exit 1
}
Write-Host "SHALOM bench idle. Restore: scripts\swarm-grok.cmd then swarm-bench-runner.ps1 to arm."
exit 0
