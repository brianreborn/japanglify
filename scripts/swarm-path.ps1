# Find Git / gh / pwsh / py / grok / adb / java on this Windows box.
# Registry + known install dirs. Not PATH, not where.exe.
# cmd trampoline: swarm-path.cmd (always starts System32 powershell.exe).
[CmdletBinding()]
param(
    [string]$Export = "",
    [switch]$Quiet
)

$ErrorActionPreference = "Continue"

function Test-Exe([string]$p) {
    return ($p -and (Test-Path -LiteralPath $p -PathType Leaf))
}

function Add-Dir([System.Collections.Generic.List[string]]$list, [string]$dir) {
    if ($dir -and (Test-Path -LiteralPath $dir -PathType Container)) {
        $full = [IO.Path]::GetFullPath($dir)
        if (-not $list.Contains($full)) { $list.Add($full) }
    }
}

function Reg-Get([string]$key, [string]$name) {
    try {
        $item = Get-ItemProperty -LiteralPath $key -ErrorAction Stop
        return [string]$item.$name
    } catch { return $null }
}

function Uninstall-Rows {
    $roots = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        Get-ChildItem $root -ErrorAction SilentlyContinue | ForEach-Object {
            $p = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue
            if ($p -and $p.DisplayName) { $p }
        }
    }
}

function Find-SwarmTools {
    $dirs = New-Object "System.Collections.Generic.List[string]"
    $git = $null
    $gh = $null
    $pwsh = $null
    $powershell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $py = $null
    $python = $null
    $grok = $null
    $adb = $null
    $javaHome = $env:JAVA_HOME
    $androidHome = $env:ANDROID_HOME
    if (-not $androidHome) { $androidHome = $env:ANDROID_SDK_ROOT }

    # Instance that already ran Actions jobs.
    $envFile = "C:\actions-runner\.env"
    if (Test-Path $envFile) {
        Get-Content $envFile -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_ -match '^(PATH|JAVA_HOME|ANDROID_HOME|ANDROID_SDK_ROOT)=(.*)$') {
                $k, $v = $Matches[1], $Matches[2]
                switch ($k) {
                    "PATH" { $v.Split(';') | ForEach-Object { Add-Dir $dirs $_ } }
                    "JAVA_HOME" { if ($v) { $javaHome = $v } }
                    "ANDROID_HOME" { if ($v) { $androidHome = $v } }
                    "ANDROID_SDK_ROOT" { if ($v -and -not $androidHome) { $androidHome = $v } }
                }
            }
        }
    }

    foreach ($k in @(
        "HKLM:\SOFTWARE\GitForWindows",
        "HKLM:\SOFTWARE\WOW6432Node\GitForWindows"
    )) {
        $root = Reg-Get $k "InstallPath"
        if ($root) {
            Add-Dir $dirs (Join-Path $root "cmd")
            Add-Dir $dirs (Join-Path $root "bin")
            Add-Dir $dirs (Join-Path $root "mingw64\bin")
            foreach ($rel in @("cmd\git.exe", "bin\git.exe")) {
                $c = Join-Path $root $rel
                if (Test-Exe $c) { $git = $c; break }
            }
        }
    }

    foreach ($row in Uninstall-Rows) {
        $n = [string]$row.DisplayName
        $loc = [string]$row.InstallLocation
        if ($n -match '^GitHub CLI' -and $loc) {
            Add-Dir $dirs $loc
            $c = Join-Path $loc "gh.exe"
            if (Test-Exe $c) { $gh = $c }
        }
        if ($n -match '^Git version|^Git$' -and $loc) {
            Add-Dir $dirs (Join-Path $loc "cmd")
            $c = Join-Path $loc "cmd\git.exe"
            if ((Test-Exe $c) -and -not $git) { $git = $c }
        }
        if ($n -match '^PowerShell [7-9]' -and $loc) {
            Add-Dir $dirs $loc
            $c = Join-Path $loc "pwsh.exe"
            if (Test-Exe $c) { $pwsh = $c }
        }
        if ($n -match '^Python 3' -and $loc) {
            Add-Dir $dirs $loc
            Add-Dir $dirs (Join-Path $loc "Scripts")
            $c = Join-Path $loc "python.exe"
            if (Test-Exe $c) { $python = $c }
        }
    }

    $hard = [ordered]@{
        git = @(
            "C:\Program Files\Git\cmd\git.exe",
            "C:\Program Files (x86)\Git\cmd\git.exe",
            "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
        )
        gh = @(
            "C:\Program Files\GitHub CLI\gh.exe",
            "C:\Program Files (x86)\GitHub CLI\gh.exe"
        )
        pwsh = @(
            "C:\Program Files\PowerShell\7\pwsh.exe",
            "C:\Program Files\PowerShell\7-preview\pwsh.exe"
        )
        py = @(
            "$env:SystemRoot\py.exe",
            "$env:LOCALAPPDATA\Programs\Python\Launcher\py.exe"
        )
        grok = @(
            "$env:USERPROFILE\.local\bin\grok.exe",
            "$env:LOCALAPPDATA\Programs\Grok\grok.exe"
        )
        adb = @(
            "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
            "$env:ProgramFiles(x86)\Android\android-sdk\platform-tools\adb.exe"
        )
    }
    Get-ChildItem "C:\Program Files\Microsoft Visual Studio\2022" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $c = Join-Path $_.FullName "Common7\IDE\CommonExtensions\Microsoft\TeamFoundation\Team Explorer\Git\cmd\git.exe"
        $hard.git += $c
    }
    Get-ChildItem "$env:LOCALAPPDATA\GitHubDesktop" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $c = Join-Path $_.FullName "resources\app\git\cmd\git.exe"
        $hard.git += $c
    }
    Get-ChildItem "$env:LOCALAPPDATA\Programs\Python" -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $c = Join-Path $_.FullName "python.exe"
        if (Test-Exe $c) { $python = $c }
        Add-Dir $dirs $_.FullName
    }

    if (-not $git) { foreach ($c in $hard.git) { if (Test-Exe $c) { $git = $c; break } } }
    if (-not $gh) { foreach ($c in $hard.gh) { if (Test-Exe $c) { $gh = $c; break } } }
    if (-not $pwsh) { foreach ($c in $hard.pwsh) { if (Test-Exe $c) { $pwsh = $c; break } } }
    foreach ($c in $hard.py) { if (Test-Exe $c) { $py = $c; break } }
    foreach ($c in $hard.grok) { if (Test-Exe $c) { $grok = $c; break } }
    if (-not $adb) { foreach ($c in $hard.adb) { if (Test-Exe $c) { $adb = $c; break } } }

    foreach ($jdkKey in @(
        "HKLM:\SOFTWARE\Eclipse Adoptium\JDK",
        "HKLM:\SOFTWARE\JavaSoft\JDK",
        "HKLM:\SOFTWARE\Microsoft\JDK"
    )) {
        if (-not (Test-Path $jdkKey)) { continue }
        Get-ChildItem $jdkKey -ErrorAction SilentlyContinue | ForEach-Object {
            $home = Reg-Get $_.PSPath "JavaHome"
            if (-not $home) { $home = Reg-Get $_.PSPath "Path" }
            if ($home -and (Test-Exe (Join-Path $home "bin\java.exe"))) {
                if (-not $javaHome) { $javaHome = $home }
                Add-Dir $dirs (Join-Path $home "bin")
            }
        }
    }

    if ($androidHome) {
        Add-Dir $dirs (Join-Path $androidHome "platform-tools")
        $c = Join-Path $androidHome "platform-tools\adb.exe"
        if (Test-Exe $c) { $adb = $c }
    }
    foreach ($p in @($git, $gh, $pwsh, $powershell, $py, $python, $grok, $adb)) {
        if ($p) { Add-Dir $dirs ([IO.Path]::GetDirectoryName($p)) }
    }
    Add-Dir $dirs "$env:SystemRoot\System32"
    Add-Dir $dirs "$env:SystemRoot\System32\WindowsPowerShell\v1.0"

    return [ordered]@{
        git = $git
        gh = $gh
        pwsh = $pwsh
        powershell = $(if (Test-Exe $powershell) { $powershell } else { $null })
        py = $py
        python = $python
        grok = $grok
        adb = $adb
        JAVA_HOME = $javaHome
        ANDROID_HOME = $androidHome
        dirs = $dirs
    }
}

function Set-SwarmPath {
    $t = Find-SwarmTools
    $prepend = [string[]]@($t.dirs)
    $env:PATH = ($prepend + @($env:PATH)) -join ";"
    if ($t.git) { $env:SWARM_GIT = $t.git }
    if ($t.gh) { $env:SWARM_GH = $t.gh }
    if ($t.pwsh) { $env:SWARM_PWSH = $t.pwsh } elseif ($t.powershell) { $env:SWARM_PWSH = $t.powershell }
    if ($t.powershell) { $env:SWARM_POWERSHELL = $t.powershell }
    if ($t.py) { $env:SWARM_PY = $t.py }
    if ($t.python) { $env:SWARM_PYTHON = $t.python }
    if ($t.grok) { $env:SWARM_GROK = $t.grok }
    if ($t.adb) { $env:SWARM_ADB = $t.adb }
    if ($t.JAVA_HOME) { $env:JAVA_HOME = $t.JAVA_HOME }
    if ($t.ANDROID_HOME) { $env:ANDROID_HOME = $t.ANDROID_HOME; $env:ANDROID_SDK_ROOT = $t.ANDROID_HOME }
    return $t
}

$tools = Set-SwarmPath
if (-not $Quiet) {
    foreach ($k in @("git","gh","pwsh","powershell","py","python","grok","adb","JAVA_HOME","ANDROID_HOME")) {
        $v = $tools[$k]
        if (-not $v) { $v = "(not found)" }
        Write-Host ("{0,-14} {1}" -f $k, $v)
    }
}

$targets = New-Object "System.Collections.Generic.List[string]"
if ($Export) { $targets.Add($Export) }
$tempEnv = Join-Path $env:TEMP "swarm-path.env"
if (-not $targets.Contains($tempEnv)) { $targets.Add($tempEnv) }
if (Test-Path "C:\actions-runner") {
    $runnerEnv = "C:\actions-runner\swarm-tools.env"
    if (-not $targets.Contains($runnerEnv)) { $targets.Add($runnerEnv) }
}
$lines = @(
    "PATH=$env:PATH",
    "SWARM_GIT=$env:SWARM_GIT",
    "SWARM_GH=$env:SWARM_GH",
    "SWARM_PWSH=$env:SWARM_PWSH",
    "SWARM_POWERSHELL=$env:SWARM_POWERSHELL",
    "SWARM_PY=$env:SWARM_PY",
    "SWARM_PYTHON=$env:SWARM_PYTHON",
    "SWARM_GROK=$env:SWARM_GROK",
    "SWARM_ADB=$env:SWARM_ADB",
    "JAVA_HOME=$env:JAVA_HOME",
    "ANDROID_HOME=$env:ANDROID_HOME"
)
foreach ($out in $targets) {
    $dir = Split-Path $out
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    Set-Content -Path $out -Value $lines -Encoding ascii
    if (-not $Quiet) { Write-Host "export $out" }
}
