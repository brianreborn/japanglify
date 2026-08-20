[CmdletBinding()]
param(
    [string]$JdkHome = 'C:\Program Files\Java\jdk-22.0.1',
    [switch]$Offline,
    [switch]$RequireDevice,
    [switch]$RequireCopilot,
    [ValidateSet('Text', 'Json')]
    [string]$OutputFormat = 'Text'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $PSCommandPath
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDirectory '..'))
$workspaceRoot = Split-Path -Parent $repositoryRoot

$sessionPaths = [ordered]@{
    RepositoryRoot = $repositoryRoot
    WorkspaceRoot = $workspaceRoot
    JavaHome = $JdkHome
    AndroidSdkRoot = Join-Path $repositoryRoot 'sdk'
    AndroidUserHome = Join-Path $repositoryRoot '.android-user-home'
    GradleUserHome = Join-Path $repositoryRoot '.gradle-user-home'
    GitHubConfigDirectory = Join-Path $workspaceRoot '.gh-cli-temp'
    NpmCache = Join-Path $workspaceRoot '.cache\npm'
    CopilotInstallRoot = Join-Path $workspaceRoot '.tools\github-copilot'
    CopilotHome = Join-Path $workspaceRoot '.copilot-cli'
    CopilotCacheHome = Join-Path $workspaceRoot '.copilot-cli\cache'
    CopilotPackageCacheHome = Join-Path $workspaceRoot '.copilot-cli\package-cache'
    CopilotExecutable = Join-Path $workspaceRoot '.tools\github-copilot\node_modules\@github\copilot-win32-x64\copilot.exe'
}

foreach ($directory in @(
    $sessionPaths.AndroidUserHome,
    $sessionPaths.GradleUserHome,
    $sessionPaths.GitHubConfigDirectory,
    $sessionPaths.NpmCache,
    $sessionPaths.CopilotHome,
    $sessionPaths.CopilotCacheHome,
    $sessionPaths.CopilotPackageCacheHome
)) {
    if (-not (Test-Path -LiteralPath $directory)) {
        $null = New-Item -ItemType Directory -Path $directory -Force
    }
}

$env:JAVA_HOME = $sessionPaths.JavaHome
$env:ANDROID_HOME = $sessionPaths.AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $sessionPaths.AndroidSdkRoot
$env:ANDROID_USER_HOME = $sessionPaths.AndroidUserHome
$env:GRADLE_USER_HOME = $sessionPaths.GradleUserHome
$env:GH_CONFIG_DIR = $sessionPaths.GitHubConfigDirectory
$env:npm_config_cache = $sessionPaths.NpmCache
$env:COPILOT_HOME = $sessionPaths.CopilotHome
$env:COPILOT_CACHE_HOME = $sessionPaths.CopilotCacheHome
$env:COPILOT_PKG_CACHE_HOME = $sessionPaths.CopilotPackageCacheHome

$requiredPathEntries = @(
    (Join-Path $sessionPaths.JavaHome 'bin'),
    (Join-Path $sessionPaths.AndroidSdkRoot 'platform-tools'),
    (Split-Path -Parent $sessionPaths.CopilotExecutable)
)
$currentPathEntries = @($env:PATH -split ';' | Where-Object { $_ })
foreach ($entry in $requiredPathEntries) {
    if ($currentPathEntries -notcontains $entry) {
        $env:PATH = "$entry;$env:PATH"
    }
}

function Invoke-Probe {
    param(
        [Parameter(Mandatory)]
        [scriptblock]$Operation
    )

    try {
        $output = @(& $Operation 2>&1 | ForEach-Object { $_.ToString() })
        [pscustomobject]@{
            Passed = ($LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE)
            Output = ($output -join [Environment]::NewLine).Trim()
        }
    }
    catch {
        [pscustomobject]@{
            Passed = $false
            Output = $_.Exception.Message
        }
    }
}

function Get-AdbServerDevices {
    param(
        [string]$HostName = '127.0.0.1',
        [int]$Port = 5037
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync($HostName, $Port)
        if (-not $connect.Wait(1500)) {
            throw "Timed out connecting to the host ADB server at ${HostName}:$Port."
        }

        $stream = $client.GetStream()
        $payload = [Text.Encoding]::ASCII.GetBytes('host:devices-l')
        $prefix = [Text.Encoding]::ASCII.GetBytes(('{0:x4}' -f $payload.Length))
        $stream.Write($prefix, 0, $prefix.Length)
        $stream.Write($payload, 0, $payload.Length)

        function Read-AdbBytes([System.IO.Stream]$InputStream, [int]$Length) {
            $buffer = [byte[]]::new($Length)
            $offset = 0
            while ($offset -lt $Length) {
                $read = $InputStream.Read($buffer, $offset, $Length - $offset)
                if ($read -le 0) {
                    throw 'The host ADB server closed the connection unexpectedly.'
                }
                $offset += $read
            }
            $buffer
        }

        $status = [Text.Encoding]::ASCII.GetString((Read-AdbBytes $stream 4))
        $bodyLength = [Convert]::ToInt32(
            [Text.Encoding]::ASCII.GetString((Read-AdbBytes $stream 4)),
            16
        )
        $body = [Text.Encoding]::UTF8.GetString((Read-AdbBytes $stream $bodyLength))
        if ($status -ne 'OKAY') {
            throw "The host ADB server returned ${status}: $body"
        }

        [pscustomobject]@{ Passed = $true; Output = $body.Trim() }
    }
    catch {
        [pscustomobject]@{
            Passed = $false
            Output = "No usable host ADB server at ${HostName}:$Port. Start ADB once from a normal Windows terminal. $($_.Exception.Message)"
        }
    }
    finally {
        $client.Dispose()
    }
}

$javaProbe = Invoke-Probe { & (Join-Path $sessionPaths.JavaHome 'bin\java.exe') -version }
$adbExecutable = Join-Path $sessionPaths.AndroidSdkRoot 'platform-tools\adb.exe'
$adbServerProbe = Get-AdbServerDevices
$githubAuthProbe = Invoke-Probe { & gh auth status --hostname github.com }
$copilotProbe = if (Test-Path -LiteralPath $sessionPaths.CopilotExecutable) {
    Invoke-Probe { & $sessionPaths.CopilotExecutable --version }
}
else {
    [pscustomobject]@{
        Passed = $false
        Output = 'Not installed. GitHub Copilot is an optional worker provider and requires separate approval before installation and authentication.'
    }
}
$githubIdentityProbe = if ($Offline) {
    [pscustomobject]@{ Passed = $null; Output = 'Skipped (-Offline).' }
}
else {
    Invoke-Probe { & gh api user --jq .login }
}

$toolchainChecks = @(
    [pscustomobject]@{ Name = 'JDK 22'; Passed = ($javaProbe.Passed -and $javaProbe.Output -match 'version "22\.'); Detail = $javaProbe.Output },
    [pscustomobject]@{ Name = 'Android SDK platform 35'; Passed = (Test-Path -LiteralPath (Join-Path $sessionPaths.AndroidSdkRoot 'platforms\android-35\android.jar')); Detail = (Join-Path $sessionPaths.AndroidSdkRoot 'platforms\android-35\android.jar') },
    [pscustomobject]@{ Name = 'ADB executable'; Passed = (Test-Path -LiteralPath $adbExecutable); Detail = $adbExecutable },
    [pscustomobject]@{ Name = 'Host ADB server'; Passed = $adbServerProbe.Passed; Detail = $adbServerProbe.Output },
    [pscustomobject]@{ Name = 'Android USB device'; Passed = ($adbServerProbe.Passed -and $adbServerProbe.Output -match '(?m)^\S+\s+device\b'); Detail = $adbServerProbe.Output },
    [pscustomobject]@{ Name = 'GitHub CLI authentication'; Passed = $githubAuthProbe.Passed; Detail = $githubAuthProbe.Output },
    [pscustomobject]@{ Name = 'GitHub network/API'; Passed = $githubIdentityProbe.Passed; Detail = $githubIdentityProbe.Output },
    [pscustomobject]@{ Name = 'GitHub Copilot CLI provider'; Passed = $copilotProbe.Passed; Detail = $copilotProbe.Output }
)

$permissionRequirements = @(
    [pscustomobject]@{
        Layer = 'Codex container'
        Requirement = 'Read/write access to the Japanglify workspace root'
        Target = $workspaceRoot
        Persistence = 'Grant again when a new local container does not inherit the task permission profile.'
    },
    [pscustomobject]@{
        Layer = 'Codex container'
        Requirement = 'Outbound network access'
        Target = 'GitHub, Gradle/Maven/Android repositories, release assets, approved LAN worker endpoints'
        Persistence = 'Grant at session scope after a container restart.'
    },
    [pscustomobject]@{
        Layer = 'Windows host'
        Requirement = 'USB access to the authorized Android device'
        Target = 'ADB/RSA-authorized device and host ADB server on loopback port 5037'
        Persistence = 'Windows driver and phone RSA authorization persist independently; the container queries the host ADB server.'
    },
    [pscustomobject]@{
        Layer = 'GitHub'
        Requirement = 'Authenticated electrobrian CLI session'
        Target = $sessionPaths.GitHubConfigDirectory
        Persistence = 'Stored outside the repository; never print or commit hosts.yml.'
    },
    [pscustomobject]@{
        Layer = 'LAN workers'
        Requirement = 'Network route and host-specific authenticated agent endpoint'
        Target = 'Only worker hosts registered in the dashboard inventory'
        Persistence = 'Grant per host when it is enrolled; keep credentials on that host.'
    },
    [pscustomobject]@{
        Layer = 'AI worker provider'
        Requirement = 'GitHub Copilot subscription, Copilot CLI installation, and provider authentication'
        Target = 'Dedicated worker checkout with an explicitly approved Copilot model, reasoning effort, context, and permission mode'
        Persistence = 'Keep credentials in the Windows credential store or a host-local COPILOT_HOME; never store tokens in Git.'
    }
)

$optionalChecks = @()
if (-not $RequireCopilot) {
    $optionalChecks += 'GitHub Copilot CLI provider'
}
if (-not $RequireDevice) {
    $optionalChecks += @('Host ADB server', 'Android USB device')
}
$blockingFailures = @($toolchainChecks | Where-Object { $_.Passed -eq $false -and $_.Name -notin $optionalChecks })

$result = [pscustomobject]@{
    SchemaVersion = 1
    GeneratedAt = [DateTimeOffset]::Now
    Ready = ($blockingFailures.Count -eq 0)
    Offline = [bool]$Offline
    RequireDevice = [bool]$RequireDevice
    RequireCopilot = [bool]$RequireCopilot
    Paths = [pscustomobject]$sessionPaths
    Checks = $toolchainChecks
    Permissions = $permissionRequirements
    Governance = [pscustomobject]@{
        TechnicalAccessIsNotActionApproval = $true
        StillRequiresBrianApproval = @(
            'Every exact worker provider/model/reasoning-effort configuration',
            'Every GitHub Copilot model/context/permission-mode configuration, including Auto selection',
            'Non-routine or scope-expanding changes',
            'Destructive operations',
            'Direct BETA-2 changes, BETA-3 changes, tags, releases, and upstream writes'
        )
    }
}

if ($OutputFormat -eq 'Json') {
    $result | ConvertTo-Json -Depth 8
    return
}

Write-Host 'Japanglify Codex session preflight' -ForegroundColor Cyan
Write-Host "Repository: $repositoryRoot"
Write-Host "Workspace:  $workspaceRoot"
Write-Host ''

foreach ($check in $toolchainChecks) {
    $state = if ($check.Passed -eq $true) { 'OK' } elseif ($null -eq $check.Passed) { 'SKIP' } else { 'NEEDS ATTENTION' }
    $color = if ($check.Passed -eq $true) { 'Green' } elseif ($null -eq $check.Passed) { 'DarkYellow' } else { 'Yellow' }
    Write-Host ("[{0}] {1}" -f $state, $check.Name) -ForegroundColor $color
    if ($check.Detail) {
        Write-Host ($check.Detail -replace '(?m)^', '    ')
    }
}

Write-Host ''
Write-Host 'Restart-time Codex grants' -ForegroundColor Cyan
Write-Host "  1. Workspace read/write: $workspaceRoot"
Write-Host '  2. Outbound network: GitHub, dependency repositories, release assets, and registered LAN workers'
Write-Host '  3. Verify host-owned GitHub auth; for device work, start the authorized host ADB server before opening the container'
Write-Host ''
Write-Host 'Technical access does not pre-approve releases, destructive actions, upstream writes, or worker model/effort choices.' -ForegroundColor DarkYellow

$result
