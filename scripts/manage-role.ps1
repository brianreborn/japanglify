[CmdletBinding()]
param(
  [Parameter(Mandatory = $true, Position = 0)]
  [ValidateSet('enable', 'disable', 'status', 'validate')]
  [string] $Action
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
$rolesRoot = Join-Path $repoRoot 'roles'
$hostsRoot = Join-Path $rolesRoot 'hosts'
$selfPath = Join-Path $rolesRoot 'self.json'
$hostname = $env:COMPUTERNAME
$workspace = (Resolve-Path $repoRoot).Path
$remote = (git remote get-url origin).Trim()

function Get-HostProfiles {
  Get-ChildItem -LiteralPath $hostsRoot -Filter '*.json' -File |
    ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json }
}

function Get-MatchingProfile {
  $matches = @(Get-HostProfiles | Where-Object {
    $_.hostname -ieq $hostname -and
    ([IO.Path]::GetFullPath($_.workspace).TrimEnd('\') -ieq $workspace.TrimEnd('\')) -and
    $_.remote -eq $remote
  })
  if ($matches.Count -ne 1) {
    throw "Expected exactly one host profile for hostname '$hostname', workspace '$workspace', remote '$remote'; found $($matches.Count)."
  }
  $matches[0]
}

function Write-SelfProfile([object] $profile, [bool] $enabled) {
  $profile.enabled = $enabled
  $json = $profile | ConvertTo-Json -Depth 10
  $tmp = "$selfPath.$PID.tmp"
  Set-Content -LiteralPath $tmp -Value $json -Encoding utf8NoBOM
  Move-Item -LiteralPath $tmp -Destination $selfPath -Force
}

switch ($Action) {
  'enable' {
    $profile = Get-MatchingProfile
    Write-SelfProfile $profile $true
    Write-Output "Enabled role $($profile.role) with capabilities: $($profile.capabilities -join ', ')"
  }
  'disable' {
    if (Test-Path -LiteralPath $selfPath) { Remove-Item -LiteralPath $selfPath -Force }
    Write-Output 'Disabled local orchestration role.'
  }
  'validate' {
    $profile = if (Test-Path -LiteralPath $selfPath) {
      Get-Content -LiteralPath $selfPath -Raw | ConvertFrom-Json
    } else { throw "No active roles/self.json. Run enable first." }
    if ($profile.hostname -ine $hostname) { throw 'Active profile hostname does not match this host.' }
    if ([IO.Path]::GetFullPath($profile.workspace).TrimEnd('\') -ine $workspace.TrimEnd('\')) { throw 'Active profile workspace does not match this checkout.' }
    if ($profile.remote -ne $remote) { throw 'Active profile remote does not match origin.' }
    if (-not $profile.enabled) { throw 'Active profile is disabled.' }
    Write-Output "Valid: $($profile.id)"
  }
  'status' {
    if (-not (Test-Path -LiteralPath $selfPath)) { Write-Output 'Disabled (no roles/self.json).'; break }
    $profile = Get-Content -LiteralPath $selfPath -Raw | ConvertFrom-Json
    Write-Output "Enabled: $($profile.enabled)"
    Write-Output "Role: $($profile.role)"
    Write-Output "Capabilities: $($profile.capabilities -join ', ')"
    Write-Output "Host: $($profile.hostname)"
    Write-Output "Workspace: $($profile.workspace)"
  }
}
