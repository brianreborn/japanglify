# Register and start the GitHub Actions self-hosted runner that `/uat` waits on.
# Interactive user session — NOT a Windows service (services usually cannot see USB adb).
# Labels: swarm-bench. GitHub also stamps self-hosted + Windows.
#
# Smart App Control (Windows 11) often blocks Register-ScheduledTask for
# C:\actions-runner\run.cmd. That is optional: USB UAT already requires this
# logon session to stay open. Do not treat a blocked logon task as runner-down.
# Proof is Runner.Listener.exe + "Listening for Jobs".

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

Write-RunnerEnv

# Do not svc.cmd — Windows services typically cannot talk to a user-session adb/USB device.
$run = Join-Path $Root "run.cmd"
$task = "swarm-bench-runner"
try {
    Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
    $action = New-ScheduledTaskAction -Execute $run -WorkingDirectory $Root
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    Register-ScheduledTask -TaskName $task -Action $action -Trigger $trigger -User $env:USERNAME -RunLevel Limited | Out-Null
    Write-Host "logon-task=$task registered"
} catch {
    Write-Warning "logon task skipped (Smart App Control often blocks this): $($_.Exception.Message)"
    Write-Warning "Keep this Windows logon session. USB adb already requires that."
}

$already = Get-CimInstance Win32_Process -Filter "Name='Runner.Listener.exe'" -ErrorAction SilentlyContinue
if ($already) {
    Write-Host "Runner.Listener already running"
} else {
    Write-Host "starting $run in this user session (keep this login; USB needs it)"
    Start-Process -FilePath $run -WorkingDirectory $Root -WindowStyle Minimized
}
Write-Host "registered $Name labels=$Labels"
