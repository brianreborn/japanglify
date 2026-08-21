# Download-first Swarm Bench restore. Only needs Windows PowerShell.
# Hunt tools, then clone/pull, then optional stop/arm/grok.
#
# powershell -NoProfile -ExecutionPolicy Bypass -Command ^
#   "Invoke-WebRequest -UseBasicParsing https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.ps1 -OutFile $env:TEMP\swarm-bootstrap.ps1; & $env:TEMP\swarm-bootstrap.ps1 -Restore"
param(
    [switch]$Restore,
    [switch]$Start,
    [switch]$Runner,
    [switch]$Stop,
    [switch]$DryRun,
    [string]$Role = "swarm-bench",
    [string]$Project = "japanglify"
)

$ErrorActionPreference = "Stop"
$HunterUrl = "https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-path.ps1"

function Import-SwarmPath {
    $local = $null
    if ($PSScriptRoot) {
        $c = Join-Path $PSScriptRoot "swarm-path.ps1"
        if (Test-Path $c) { $local = $c }
    }
    if (-not $local) {
        $local = Join-Path $env:TEMP "swarm-path.ps1"
        Write-Host "fetch $HunterUrl"
        Invoke-WebRequest -UseBasicParsing -Uri "$HunterUrl?$(Get-Random)" -OutFile $local
    }
    . $local
    if (-not $env:SWARM_GIT -or -not (Test-Path -LiteralPath $env:SWARM_GIT)) {
        throw "git.exe not found after hunt. Install Git for Windows (system), then re-run."
    }
    Write-Host "using git=$env:SWARM_GIT"
}

Import-SwarmPath

function Ensure-GhOAuth {
    $gh = $env:SWARM_GH
    foreach ($c in @(
        $gh,
        "C:\Program Files\GitHub CLI\gh.exe",
        "C:\Program Files (x86)\GitHub CLI\gh.exe"
    )) {
        if ($c -and (Test-Path -LiteralPath $c)) { $gh = $c; break }
    }
    if (-not $gh -or -not (Test-Path -LiteralPath $gh)) {
        throw "gh.exe not found (GitHub CLI system install)"
    }
    $env:SWARM_GH = $gh
    $env:PATH = ("{0};{1}" -f (Split-Path $gh), $env:PATH)
    Write-Host "using gh=$gh"
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $gh auth status --hostname github.com 2>&1 | Out-Null
    $st = $LASTEXITCODE
    $ErrorActionPreference = $prevEa
    if ($st -eq 0) {
        Write-Host "gh already logged in"
        return
    }
    Write-Host "starting GitHub OAuth (browser) - finish as brianreborn, then this continues"
    & $gh auth login --hostname github.com --git-protocol https --web
    if ($LASTEXITCODE -ne 0) { throw "gh auth login did not finish" }
}

if (-not $DryRun) { Ensure-GhOAuth }

$OfficialRepo = "brianreborn/japanglify"
$DevRepo = "electrobrian/japanglify"
$OfficialBranch = "main"
$DevBranch = "BETA-2"
$Git = $env:SWARM_GIT

$homeDir = $env:USERPROFILE
if (-not $homeDir) { $homeDir = $env:HOME }
if (-not $homeDir) { throw "USERPROFILE/HOME is not set" }
$hostName = $env:COMPUTERNAME
if (-not $hostName) { $hostName = $env:HOSTNAME }
if (-not $hostName) { $hostName = "unknown" }
$root = if ($env:SWARM_AGENTS) { $env:SWARM_AGENTS } elseif ($env:SWARM_SRC) { $env:SWARM_SRC } else { Join-Path $homeDir "swarm-agents" }
$sep = [IO.Path]::DirectorySeparatorChar
$agent = Join-Path $root "$Project$sep$hostName$sep$Role"
$official = Join-Path $agent "official"
$dev = Join-Path $agent "dev"
$runnerDir = "C:\actions-runner"

Write-Host "agentHome=$agent"
Write-Host "official=$official"
Write-Host "dev=$dev"
Write-Host "runner=$runnerDir"

function Git-Do([string[]]$gitArgs) {
    & $Git @gitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($gitArgs -join ' ') failed ($LASTEXITCODE)" }
}

function Ensure-Repo([string]$repo, [string]$dir, [string]$branch) {
    $url = "https://github.com/$repo.git"
    if ($DryRun) {
        if (Test-Path (Join-Path $dir ".git")) { Write-Host "would pull $dir ($branch)" }
        else { Write-Host "would clone $url -> $dir ($branch)" }
        return
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $dir) | Out-Null
    if (Test-Path (Join-Path $dir ".git")) {
        Git-Do @("-C", $dir, "fetch", "origin")
        Git-Do @("-C", $dir, "checkout", $branch)
        & $Git -C $dir pull --ff-only origin $branch
        if ($LASTEXITCODE -ne 0) { Git-Do @("-C", $dir, "pull", "--ff-only") }
    } else {
        Git-Do @("clone", "--branch", $branch, $url, $dir)
    }
}

Ensure-Repo $OfficialRepo $official $OfficialBranch
Ensure-Repo $DevRepo $dev $DevBranch

if ($DryRun) {
    Write-Host "next: $official\scripts\swarm-grok.cmd"
    return
}

$doStop = [bool]$Stop -or [bool]$Restore
$doArm = [bool]$Runner -or [bool]$Restore
$doGrok = [bool]$Start -or [bool]$Restore

if ($doStop) {
    $stopScript = Join-Path $official "scripts\swarm-bench-stop.ps1"
    if (Test-Path $stopScript) {
        Write-Host "idle stop"
        & $stopScript
    } else {
        Write-Warning "missing $stopScript"
    }
}

if ($doArm) {
    $bench = Join-Path $official "scripts\swarm-bench-runner.ps1"
    if (-not (Test-Path $bench)) { throw "missing $bench" }
    Write-Host "arm listener"
    & $bench
}

$startCmd = Join-Path $official "scripts\swarm-grok.cmd"
Write-Host "next: $startCmd"
Write-Host "     (product work in $dev)"
if ($doGrok) {
    if (-not (Test-Path $startCmd)) { throw "missing $startCmd" }
    & $startCmd
    exit $LASTEXITCODE
}
