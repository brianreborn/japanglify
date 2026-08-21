# Fresh Swarm Bench checkout. Download this first; Grok CLI is the other client.
# powershell -ExecutionPolicy Bypass -File swarm-bootstrap.ps1
param(
    [switch]$Start,
    [switch]$Runner,
    [switch]$DryRun,
    [string]$Role = "swarm-bench",
    [string]$Project = "japanglify"
)

$ErrorActionPreference = "Stop"
$OfficialRepo = "brianreborn/japanglify"
$DevRepo = "electrobrian/japanglify"
$OfficialBranch = "main"
$DevBranch = "BETA-2"

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

function Need-Cmd($name, [switch]$Optional) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        if ($Optional) { Write-Warning "missing $name"; return $false }
        throw "missing $name"
    }
    return $true
}

Need-Cmd git | Out-Null
Need-Cmd grok -Optional | Out-Null
if (-not (Get-Command py -ErrorAction SilentlyContinue) -and -not (Get-Command python3 -ErrorAction SilentlyContinue) -and -not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Warning "missing Python (needed for swarm-grok / swarm_paths)"
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
        git -C $dir fetch origin
        git -C $dir checkout $branch
        git -C $dir pull --ff-only origin $branch
        if ($LASTEXITCODE -ne 0) { git -C $dir pull --ff-only }
    } else {
        git clone --branch $branch $url $dir
        if ($LASTEXITCODE -ne 0) { throw "git clone failed: $url" }
    }
}

Ensure-Repo $OfficialRepo $official $OfficialBranch
Ensure-Repo $DevRepo $dev $DevBranch

if ($DryRun) {
    Write-Host "next: $official\scripts\swarm-grok.cmd"
    return
}

if ($Runner) {
    $bench = Join-Path $official "scripts\swarm-bench-runner.ps1"
    if (-not (Test-Path $bench)) { throw "missing $bench" }
    & $bench
}

$startCmd = Join-Path $official "scripts\swarm-grok.cmd"
Write-Host "next: $startCmd"
Write-Host "     (product work in $dev)"
if ($Start) {
    & $startCmd
    exit $LASTEXITCODE
}
