# Fresh Swarm Bench checkout. Download this first; Grok CLI is the other client.
# irm https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.ps1 | iex
# Or: powershell -ExecutionPolicy Bypass -File swarm-bootstrap.ps1
param(
    [switch]$Start,
    [switch]$Runner,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$OfficialRepo = "brianreborn/japanglify"
$DevRepo = "electrobrian/japanglify"
$OfficialBranch = "main"
$DevBranch = "BETA-2"

$homeDir = $env:USERPROFILE
if (-not $homeDir) { $homeDir = $env:HOME }
if (-not $homeDir) { throw "USERPROFILE/HOME is not set" }
$src = if ($env:SWARM_SRC) { $env:SWARM_SRC } else { Join-Path $homeDir "src" }
$official = Join-Path $src ($OfficialRepo -replace "/", [IO.Path]::DirectorySeparatorChar)
$dev = Join-Path $src ($DevRepo -replace "/", [IO.Path]::DirectorySeparatorChar)
$runnerDir = "C:\actions-runner"

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
