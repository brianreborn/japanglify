# Register and start the GitHub Actions self-hosted runner that `/uat` waits on.
# Labels: swarm-bench. GitHub also stamps self-hosted + Windows.
# Run on the Windows 11 box with the Pixel. Repo admin `gh` auth required once.

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

Need-Gh

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

$svc = Get-Service -Name "actions.runner.*" -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*$Repo*" -or $_.Name -like "*swarm-bench*" }
if ($svc -and $svc.Status -eq "Running") {
    Write-Host "already running: $($svc.Name)"
    gh api "repos/$Repo/actions/runners" --jq ".runners[] | {name,status,labels:[.labels[].name]}" 2>$null
    exit 0
}

if (-not (Test-Path .\.runner)) {
    $tok = (gh api --method POST "repos/$Repo/actions/runners/registration-token" --jq .token).Trim()
    if (-not $tok) { throw "could not mint registration token — need repo admin" }
    & .\config.cmd --unattended --url "https://github.com/$Repo" --token $tok --name $Name --labels $Labels --work "_work" --replace
}

if (Test-Path .\svc.cmd) {
    & .\svc.cmd install
    & .\svc.cmd start
    Write-Host "service started as $Name labels=$Labels"
} else {
    Write-Host "no svc.cmd; starting foreground run.cmd (keep this window open)"
    & .\run.cmd
}
