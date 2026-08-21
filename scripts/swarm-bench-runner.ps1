# Register and start the GitHub Actions self-hosted runner that `/uat` waits on.
# Interactive user session  -  NOT a Windows service (services usually cannot see USB adb).
# Labels: swarm-bench. GitHub also stamps self-hosted + Windows.
#
# Robustness: Grok CLI is NOT the supervisor. This script:
#   1. copies swarm-run-loop.cmd + swarm-kick-watch.ps1 to C:\actions-runner
#   2. starts a hidden restart loop around Runner.Listener
#   3. persists via HKCU Run (Smart App Control often blocks Scheduled Task)
#   4. sets Grok CLI default_reasoning_effort = medium in %USERPROFILE%\.grok\config.toml
# Proof the host is up: GitHub runner status online, or Runner.Listener.exe.

$ErrorActionPreference = "Stop"
$Repo = "brianreborn/japanglify"
$Root = "C:\actions-runner"
$Name = if ($env:SWARM_RUNNER_NAME) { $env:SWARM_RUNNER_NAME } else { "$env:COMPUTERNAME-swarm-bench" }
$Labels = "swarm-bench"

function Ensure-ToolPath {
    $hunter = Join-Path $PSScriptRoot "swarm-path.ps1"
    if (Test-Path $hunter) {
        . $hunter -Quiet
        return
    }
    $add = @(
        "$env:SystemRoot\System32",
        "$env:SystemRoot\System32\WindowsPowerShell\v1.0",
        "${env:ProgramFiles}\Git\cmd",
        "${env:ProgramFiles}\Git\bin",
        "${env:ProgramFiles(x86)}\Git\cmd",
        "${env:ProgramFiles}\PowerShell\7",
        "${env:ProgramFiles}\GitHub CLI",
        "$env:LOCALAPPDATA\Programs\Python\Launcher",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links",
        "$env:ProgramData\chocolatey\bin",
        "$env:USERPROFILE\scoop\shims",
        "$env:USERPROFILE\.local\bin"
    ) | Where-Object { $_ -and (Test-Path $_) }
    $env:PATH = ($add + @($env:PATH)) -join ";"
}
Ensure-ToolPath

function Ensure-GhAuth {
    $ghExe = $env:SWARM_GH
    if (-not $ghExe -or -not (Test-Path $ghExe)) {
        foreach ($c in @(
            "C:\Program Files\GitHub CLI\gh.exe",
            "C:\Program Files (x86)\GitHub CLI\gh.exe"
        )) { if (Test-Path $c) { $ghExe = $c; break } }
    }
    if (-not $ghExe) {
        $cmd = Get-Command gh -ErrorAction SilentlyContinue
        if ($cmd) { $ghExe = $cmd.Source }
    }
    if (-not $ghExe) {
        throw "gh.exe not found (GitHub CLI). Expected C:\\Program Files\\GitHub CLI\\gh.exe"
    }
    $env:PATH = ("{0};{1}" -f (Split-Path $ghExe), $env:PATH)
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $ghExe auth status --hostname github.com 2>&1 | Out-Null
    $st = $LASTEXITCODE
    $ErrorActionPreference = $prevEa
    if ($st -eq 0) { return }
    if ($env:GH_TOKEN -or $env:GITHUB_TOKEN) { return }
    Write-Host "starting gh auth login --web (finish in the browser, then this script continues)"
    & $ghExe auth login --hostname github.com --git-protocol https --web
    $ErrorActionPreference = "Continue"
    & $ghExe auth status --hostname github.com 2>&1 | Out-Null
    $st = $LASTEXITCODE
    $ErrorActionPreference = $prevEa
    if ($st -ne 0) { throw "gh auth login did not finish" }
}

function Gh-LoginName {
    $raw = gh api user --jq .login 2>$null
    if (-not $raw) { return $null }
    return ([string]$raw).Trim()
}

function Ensure-GrokEffortMedium {
    # Vendor CLI default is high. This host's default is medium. --effort on the
    # command line still wins for a session (issue label high/xhigh).
    # [models] belongs in user config, not project .grok/config.toml.
    $grokHome = if ($env:GROK_HOME) { $env:GROK_HOME } else { Join-Path $env:USERPROFILE ".grok" }
    $cfg = Join-Path $grokHome "config.toml"
    if (-not (Test-Path $grokHome)) {
        New-Item -ItemType Directory -Path $grokHome | Out-Null
    }
    $text = ""
    if (Test-Path $cfg) {
        $text = [System.IO.File]::ReadAllText($cfg)
    }
    $want = 'default_reasoning_effort = "medium"'
    $nl = [Environment]::NewLine
    if ($text -match '(?m)^\s*default_reasoning_effort\s*=\s*"medium"\s*$') {
        Write-Host "grok CLI default effort already medium ($cfg)"
        return
    }
    if ($text -match '(?m)^\s*default_reasoning_effort\s*=') {
        $text = [regex]::Replace($text, '(?m)^\s*default_reasoning_effort\s*=.*$', $want)
    } elseif ($text -match '(?m)^\[models\]\s*$') {
        $text = [regex]::Replace($text, '(?m)^\[models\]\s*$', ('[models]' + $nl + $want))
    } else {
        $pad = ""
        if ($text -and -not $text.EndsWith("`n")) { $pad = $nl }
        $text = $text + $pad + $nl + '[models]' + $nl + $want + $nl
    }
    [System.IO.File]::WriteAllText($cfg, $text)
    Write-Host "set grok CLI default effort medium ($cfg)"
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
    foreach ($d in @(
        "C:\Program Files\PowerShell\7",
        "C:\Program Files\Git\cmd",
        "C:\Program Files\GitHub CLI",
        "$env:SystemRoot\System32\WindowsPowerShell\v1.0"
    )) {
        if (Test-Path $d) { $extra += $d }
    }
    if ($extra.Count -gt 0) {
        $lines += ("PATH=" + ($extra -join ";") + ";" + $env:PATH)
    }
    if ($lines.Count -gt 0) {
        Set-Content -Path (Join-Path $Root ".env") -Value $lines -Encoding ascii
        Write-Host ("wrote {0}\.env (adb/java for the runner process)" -f $Root)
    } else {
        Write-Warning "adb/JAVA_HOME/ANDROID_HOME not in this shell  -  /uat jobs may fail until they are"
    }
}

function Ensure-SwarmLabel {
    # Old installs skipped config.cmd when .runner existed, so GitHub never got swarm-bench.
    try {
        $jq = ".runners[] | select(.name==`"$Name`") | .labels[].name"
        $have = @(gh api "repos/$Repo/actions/runners" --jq $jq)
        if ($have.Count -eq 0) {
            Write-Warning "runner $Name not listed on $Repo  -  wrong repo or offline"
            return
        }
        Write-Host "github labels: $($have -join ',')"
        if ($have -notcontains "swarm-bench") {
            Write-Warning "missing swarm-bench  -  config --replace (jobs require self-hosted+Windows+swarm-bench)"
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
    foreach ($name in @("swarm-run-loop.cmd", "swarm-kick-watch.ps1", "swarm-bench-stop.ps1", "swarm-bench-stop.cmd")) {
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

Ensure-GrokEffortMedium

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Warning "adb not on PATH in this session. USB UAT will fail until it is."
} else {
    adb devices
}

if (-not (Test-Path $Root)) {
    New-Item -ItemType Directory -Path $Root | Out-Null
}
Set-Location $Root

$already = Test-Path .\.runner
if ($already) {
    Write-Host "runner already registered (.runner present)"
}
Ensure-GhAuth
$who = Gh-LoginName
if ($who -and $who -ne "brianreborn") {
    Write-Warning "gh is $who - registration token needs repo admin on $Repo"
}
if (-not $already) {
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
    $tok = (gh api --method POST "repos/$Repo/actions/runners/registration-token" --jq .token).Trim()
    if (-not $tok) { throw "could not mint registration token - need repo admin" }
    & .\config.cmd --unattended --url "https://github.com/$Repo" --token $tok --name $Name --labels $Labels --work "_work" --replace
}
Ensure-SwarmLabel

Write-RunnerEnv
Remove-Item (Join-Path $Root ".swarm-disarmed") -Force -ErrorAction SilentlyContinue
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
Write-Host "registered $Name labels=$Labels (Grok CLI can stop, listener loop stays)"
