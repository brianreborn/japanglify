# Codex session permissions

This document is the durable restart contract for the Windows Codex Desktop
development environment. It separates technical access from authorization to
change external state. A broad technical grant must not be interpreted as
approval to release, merge, delete, change an upstream repository, or choose a
worker model configuration.

## Restart procedure

1. Open the Codex task with
   `C:\Users\brian\Documents\Codex\2026-08-19\build` as its workspace.
2. Grant the new local container read/write access to that workspace root. This
   covers both repository clones, the repo-local Android SDK and caches, the
   dashboard, build outputs, and the isolated GitHub CLI configuration.
3. Grant outbound network access at session scope. Shell tools need it for
   GitHub, Actions and release assets; Gradle, Maven and Android dependency
   repositories; and explicitly enrolled LAN worker endpoints.
4. From the Japanglify repository, dot-source the preflight so its environment
   variables remain active in the current PowerShell process:

   ```powershell
   . .\scripts\initialize-codex-session.ps1
   ```

5. Resolve only the checks reported as `NEEDS ATTENTION`. The Android device is
   allowed to be absent when physical-device testing is not part of the task.

When physical-device testing is required, start the repo SDK's ADB server once
from a normal Windows terminal before opening or restarting the Codex
container, then require it during preflight:

```powershell
. .\scripts\initialize-codex-session.ps1 -RequireDevice
```

The container queries that host server over loopback port 5037. This avoids
copying ADB RSA keys into the workspace.

For collectors, the same preflight can emit machine-readable state:

```powershell
. .\scripts\initialize-codex-session.ps1 -OutputFormat Json
```

Use `-Offline` when intentionally validating only local prerequisites.
Use `-RequireCopilot` for a session that will launch an approved Copilot
worker.

## Required access inventory

| Layer | Required access | Why | Persistence |
| --- | --- | --- | --- |
| Codex container | Read/write `C:\Users\brian\Documents\Codex\2026-08-19\build` | Source edits, branches, builds, Android SDK/caches, dashboard state, isolated GitHub config | Re-grant if a restarted container does not inherit the task profile |
| Codex container | Outbound network | GitHub API/git/Actions/releases, Gradle/Maven/Android downloads, enrolled LAN workers | Prefer one session-scoped approval after each container restart |
| Windows host | Read/execute JDK 22 at `C:\Program Files\Java\jdk-22.0.1` | Android and Kotlin builds | Installed at the host level; no routine write permission required |
| Windows host | Android USB/ADB device access and loopback port 5037 | Install APKs, collect logs, and exercise accessibility/share flows | USB driver and phone RSA authorization persist outside Codex; start the host ADB server for device-test sessions |
| GitHub | `electrobrian` CLI authentication | Fork branches, PRs, checks, issue workflow, and tester artifacts | Stored in `build\.gh-cli-temp`; never commit or print its token-bearing files |
| LAN worker host | Route plus a host-specific authenticated endpoint | Dispatch work and receive telemetry/results | Enroll separately; credentials remain on the worker host |
| GitHub Copilot worker | Active Copilot entitlement, Copilot CLI, provider authentication, and a dedicated checkout | Optional local or LAN coding-agent provider | Credentials stay in the Windows credential store or a host-local `COPILOT_HOME`; configuration requires Brian's approval |
| Connector | Existing GitHub/Figma connector authorization when used | Semantic GitHub and Figma actions | Managed by the Codex Desktop connector, not the repository |

The container currently needs a boolean network grant rather than a durable
domain allowlist. Operationally, limit shell network use to:

- `github.com`, `api.github.com`, GitHub release/artifact hosts, and git remotes;
- Gradle, Google Android/Maven, Maven Central, and declared dependency hosts;
- approved model registries for an explicitly approved model experiment; and
- registered private-LAN worker addresses.

Do not grant repository code access to arbitrary inbound LAN clients. A worker
must authenticate to the coordinator and receive an explicit assignment.

## GitHub Copilot worker provider

GitHub Copilot is part of the planned provider pool, alongside Codex and other
approved agents. Official Copilot CLI `1.0.80` is installed workspace-locally
under `build\.tools\github-copilot` using the Codex-bundled Node 24 runtime.
The preflight reports it as optional unless `-RequireCopilot` is supplied.

Before enrolling a Copilot worker:

1. Brian approves the host, exact Copilot model, reasoning effort, context
   choice, permission mode, concurrency slot, task scope, and write authority.
   Copilot's `Auto` model selection is itself a configuration and is not
   implicitly approved.
2. Confirm the GitHub account has an active Copilot entitlement and that any
   organization policy permits Copilot CLI.
3. Install Copilot CLI on the worker host. On this host it is already installed
   from the official `@github/copilot` npm package. Current GitHub documentation
   lists PowerShell 6+ on Windows; the npm installation path additionally
   requires Node.js 22+.
4. Authenticate interactively into the Windows credential store when possible.
   A headless worker may instead use a fine-grained user token with the
   `Copilot Requests` account permission, but the token must remain in that
   host's secret store. Classic personal-access tokens are not supported.
5. Assign a dedicated checkout/worktree and start with Copilot permission mode
   `default` or `assisted`. Do not select `allow-all` unless Brian explicitly
   approves that mode for that isolated worker.
6. Report provider, model, effort, context, permission mode, host, runtime, PR
   or issue parent, and last heartbeat to the CI pipeline dashboard.

Ordinary `gh` repository authentication does not by itself prove Copilot
entitlement or approve a Copilot worker configuration.

## Environment isolation

The preflight intentionally redirects tools that otherwise try to write into a
sandbox-inaccessible Windows profile:

- `GH_CONFIG_DIR` -> `build\.gh-cli-temp`
- npm cache -> `build\.cache\npm`
- Copilot executable -> `build\.tools\github-copilot`
- Copilot configuration/cache -> `build\.copilot-cli`
- `ANDROID_USER_HOME` -> `japanglify\.android-user-home`
- `GRADLE_USER_HOME` -> `japanglify\.gradle-user-home`
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` -> `japanglify\sdk`
- `JAVA_HOME` -> the system JDK 22 installation

Current Windows platform-tools still ask Windows directly for the profile path
and can resolve it as `C:\` inside the Codex sandbox, producing ADB's `Cannot
mkdir '\.android'` error despite these environment variables. Do not copy the
RSA key as a workaround and do not grant the whole profile. Start the already
authorized ADB server on the Windows host; the preflight uses the ADB server
protocol over `127.0.0.1:5037` to verify attached devices. The `.gh-cli-temp`
directory contains credentials and must stay outside Git; local SDK, cache,
Android-home, and credential data must remain ignored.

## Access that remains deliberately gated

Long-lived technical permission does not supersede project governance. Brian's
explicit approval remains required for:

- every exact worker provider, model, and reasoning-effort configuration;
- every Copilot model, context-window choice, and CLI permission mode;
- new secrets, OAuth applications, public webhooks, or account integrations;
- dependency, toolchain, SDK, or CI-security upgrades;
- destructive operations or data migration;
- ambiguous, expanded, or non-routine issue scope;
- direct changes to BETA-2, any BETA-3 change, tags, releases, or upstream
  writes; and
- failed tests, failed CI, or merge conflicts before proceeding.

Routine issue implementation may use an already approved branch/PR workflow,
but it must not silently broaden these permissions.

## When the inventory changes

Whenever work first encounters a missing filesystem root, network destination,
USB/device capability, connector scope, GitHub scope, or LAN endpoint:

1. record the exact resource and operation that failed;
2. determine whether it is a recurring project requirement or a one-off;
3. add recurring requirements here and to the preflight output;
4. request the narrowest reusable session-level grant available; and
5. never store the granted credential or secret in Git.

This keeps restart setup predictable while preserving an auditable distinction
between what the tools *can* access and what they are *authorized* to do.
