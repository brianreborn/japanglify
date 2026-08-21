# Register and start the GitHub Actions self-hosted runner that `/uat` waits on.
# Interactive user session — NOT a Windows service (services usually cannot see USB adb).
# Labels: swarm-bench. GitHub also stamps self-hosted + Windows.
#
# Robustness: Grok CLI is NOT the supervisor. This script:
#   1. copies swarm-run-loop.cmd + swarm-kick-watch.ps1 to C:\actions-runner
#   2. starts a hidden restart loop around Runner.Listener
#   3. persists via HKCU Run (Smart App Control often blocks Scheduled Task)
# Proof the host is up: GitHub runner status online, or Runner.Listener.exe.

$ErrorActionPreference = "Stop"
$Repo = "brianreborn/japanglify"
$Root = "C:\actions-runner"
$Name = if ($env:SWARM_RUNNER_NAME) { $env:SWARM_RUNNER_NAME } else { "$env:COMPUTERNAME-swarm-bench" }
$Labels = "swarm-bench"

function Need-Gh {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "gh not on PATH. Install GitHub CLI and `gh auth login` as brianreborn."
    }
    $login = (gh api user --jq .login).Trim()
    if ($login -ne "brianreborn") {
        Write-Warning "gh is $login — registration token needs repo admin on $Repo"
    }
}

function Write-RunnerEnv {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
    $java = $env:JAVA_HOME
    $android = $env:ANDROID_HOME
    if (-not $android -and $env:ANDROID_SDK_ROOT) { $android = $env:ANDROID_SDK_ROOT }
    $lines = @()
    if ($java) { $lines += "JAVA_HOME=$java" }
    if ($android) {
        $lines += "ANDROID_HOME=$android"
        $lines += "ANDROID_SDK_ROOT=$android"
    }
    $extra = @()
    if ($adb) { $extra += (Split-Path $adb -Parent) }
    if ($java) { $extra += (Join-Path $java "bin") }
    if ($android) {
        $extra += (Join-Path $android "platform-tools")
        $extra += (Join-Path $android "emulator")
    }
    if ($extra.Count -gt 0) {
        $lines += ("PATH=" + ($extra -join ";") + ";" + $env:PATH)
    }
    if ($lines.Count -gt 0) {
        Set-Content -Path (Join-Path $Root ".env") -Value $lines -Encoding ascii
        Write-Host "wrote $Root\.env (adb/java for the runner process)"
    } else {
        Write-Warning "adb/JAVA_HOME/ANDROID_HOME not in this shell — /uat jobs may fail until they are"
    }
}

function Ensure-SwarmLabel {
    # Old installs skipped config.cmd when .runner existed, so GitHub never got swarm-bench.
    try {
        $jq = ".runners[] | select(.name==`"$Name`") | .labels[].name"
        $have = @(gh api "repos/$Repo/actions/runners" --jq $jq)
        if ($have.Count -eq 0) {
            Write-Warning "runner $Name not listed on $Repo — wrong repo or offline"
            return
        }
        Write-Host "github labels: $($have -join ',')"
        if ($have -notcontains "swarm-bench") {
            Write-Warning "missing swarm-bench — config --replace (jobs require self-hosted+Windows+swarm-bench)"
            Get-Process Runner.Listener -ErrorAction SilentlyContinue | Stop-Process -Force
            Start-Sleep -Seconds 2
            $tok = (gh api --method POST "repos/$Repo/actions/runners/registration-token" --jq .token).Trim()
            if (-not $tok) { throw "could not mint registration token" }
            & .\config.cmd --unattended --url "https://github.com/$Repo" --token $tok --name $Name --labels $Labels --work "_work" --replace
        }
    } catch {
        Write-Warning "could not verify runner labels: $($_.Exception.Message)"
    }
}

function Install-KeepAlive {
    # Fixed path so logon persistence does not depend on which git clone ran this.
    foreach ($name in @("swarm-run-loop.cmd", "swarm-kick-watch.ps1")) {
        $src = Join-Path $PSScriptRoot $name
        if (Test-Path $src) {
            Copy-Item $src (Join-Path $Root $name) -Force
            Write-Host "installed $Root\$name"
        } else {
            Write-Warning "missing $src"
        }
    }
    $loop = Join-Path $Root "swarm-run-loop.cmd"
    $watch = Join-Path $Root "swarm-kick-watch.ps1"
    $runKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
    if (-not (Test-Path $runKey)) {
        New-Item -Path $runKey -Force | Out-Null
    }
    if (Test-Path $loop) {
        $loopCmd = "cmd.exe /c start `"swarm-bench`" /min `"$loop`""
        Set-ItemProperty -Path $runKey -Name "swarm-bench-loop" -Value $loopCmd
        Write-Host "HKCU Run swarm-bench-loop (logon keep-alive; SAC-safe vs Scheduled Task)"
    }
    $pwsh = Get-Command pwsh -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if (-not $pwsh) { $pwsh = "powershell.exe" }
    if (Test-Path $watch) {
        Set-ItemProperty -Path $runKey -Name "swarm-kick-watch" -Value "`"$pwsh`" -NoProfile -WindowStyle Hidden -File `"$watch`""
        Write-Host "HKCU Run swarm-kick-watch"
    }
}

function Start-ListenerLoop {
    $loop = Join-Path $Root "swarm-run-loop.cmd"
    $run = Join-Path $Root "run.cmd"
    $listener = Get-CimInstance Win32_Process -Filter "Name='Runner.Listener.exe'" -ErrorAction SilentlyContinue
    if ($listener) {
        Write-Host "Runner.Listener already running"
        return
    }
    $loopHit = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -and ($_.CommandLine -match 'swarm-run-loop')
    })
    if ($loopHit.Count -gt 0) {
        Write-Host "swarm-run-loop already running pid=$($loopHit[0].ProcessId)"
        return
    }
    if (Test-Path $loop) {
        Write-Host "starting hidden $loop (keep this login; USB needs it)"
        Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $loop) -WorkingDirectory $Root -WindowStyle Hidden
        return
    }
    Write-Host "starting $run in this user session (keep this login; USB needs it)"
    Start-Process -FilePath $run -WorkingDirectory $Root -WindowStyle Hidden
}

function Start-KickWatch {
    $watch = Join-Path $Root "swarm-kick-watch.ps1"
    if (-not (Test-Path $watch)) {
        $watch = Join-Path $PSScriptRoot "swarm-kick-watch.ps1"
    }
    if (-not (Test-Path $watch)) { return }
    $hit = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match '^(pwsh|powershell)\.exe$' -and $_.CommandLine -and ($_.CommandLine -match 'swarm-kick-watch')
    })
    if ($hit.Count -gt 0) {
        Write-Host "swarm-kick-watch already running pid=$($hit[0].ProcessId)"
        return
    }
    $pwsh = Get-Command pwsh -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if (-not $pwsh) { $pwsh = "powershell.exe" }
    Write-Host "starting kick-watch $watch"
    Start-Process -FilePath $pwsh -ArgumentList @("-NoProfile", "-WindowStyle", "Hidden", "-File", $watch) -WindowStyle Hidden
}

Need-Gh

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Warning "adb not on PATH in this session. USB UAT will fail until it is."
} else {
    adb devices
}

if (-not (Test-Path $Root)) {
    New-Item -ItemType Directory -Path $Root | Out-Null
}
Set-Location $Root

if (-not (Test-Path .\config.cmd)) {
    $tag = (gh api repos/actions/runner/releases/latest --jq .tag_name).Trim()
    $ver = $tag.TrimStart("v")
    $zip = "actions-runner-win-x64-$ver.zip"
    $url = "https://github.com/actions/runner/releases/download/$tag/$zip"
    Write-Host "download $url"
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath . -Force
    Remove-Item $zip
}

if (-not (Test-Path .\.runner)) {
    $tok = (gh api --method POST "repos/$Repo/actions/runners/registration-token" --jq .token).Trim()
    if (-not $tok) { throw "could not mint registration token — need repo admin" }
    & .\config.cmd --unattended --url "https://github.com/$Repo" --token $tok --name $Name --labels $Labels --work "_work" --replace
}

Ensure-SwarmLabel
Write-RunnerEnv
Install-KeepAlive

$task = "swarm-bench-runner"
$loop = Join-Path $Root "swarm-run-loop.cmd"
$taskExe = if (Test-Path $loop) { $loop } else { Join-Path $Root "run.cmd" }
try {
    Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
    $action = New-ScheduledTaskAction -Execute $taskExe -WorkingDirectory $Root
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    Register-ScheduledTask -TaskName $task -Action $action -Trigger $trigger -User $env:USERNAME -RunLevel Limited | Out-Null
    Write-Host "logon-task=$task registered"
} catch {
    Write-Warning "logon task skipped (Smart App Control often blocks this): $($_.Exception.Message)"
    Write-Warning "HKCU Run is the keep-alive. Keep this Windows logon session (USB adb needs it)."
}

Start-ListenerLoop
Start-KickWatch
Write-Host "registered $Name labels=$Labels (Grok CLI can stop; listener loop stays)"
