# Exclusive UAT lock for one ADB host on Windows.
# Same protocol as adb-uat-lock.sh: $HOME/$ADBUUID via lockf (fcntl.lockf /
# msvcrt.locking). Serials with ':' use '_' in the NTFS filename.
#
#   $env:ADBUUID = "27841130ae1c7ece"
#   .\scripts\adb-uat-lock.ps1 --timeout 300 -- adb install -r app.apk
param(
  [double]$Timeout = 300,
  [switch]$Status,
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$Cmd
)
$ErrorActionPreference = "Stop"
$py = Join-Path $PSScriptRoot "lib\adb-uat-lock.py"
if (-not $env:HOME) { $env:HOME = $HOME }
if (-not $env:ADBUUID) { Write-Error "ADBUUID is required"; exit 2 }
$python = Get-Command python3 -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python -ErrorAction SilentlyContinue }
if (-not $python) { Write-Error "python3/python required for lockf on Windows"; exit 127 }
$argv = @($py)
if ($Status) { $argv += "--status" }
else {
  $argv += @("--timeout", "$Timeout", "--")
  $rest = @($Cmd)
  if ($rest.Count -gt 0 -and $rest[0] -eq "--") { $rest = $rest[1..($rest.Count - 1)] }
  if ($rest.Count -eq 0) { Write-Error "missing command after --"; exit 2 }
  $argv += $rest
}
& $python.Source @argv
exit $LASTEXITCODE
