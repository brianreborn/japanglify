# Swarm host paths

Given a lease id, the tree is always the same. Matcher: `scripts/swarm_paths.py`. Template: [hosts.json](hosts.json) `workdir`. Repos: [instance.json](instance.json).

```
{home}/swarm-agents/{project}/{hostname}/{role}/
  official/     brianreborn/japanglify (swarm scripts, Restore)
  dev/          electrobrian/japanglify (app, agent/*)
```

Listener stays **outside** this tree (`C:\actions-runner` / `{home}/actions-runner`). GitHub-hosted = `$GITHUB_WORKSPACE`. grok-cloud = none.

| OS | `{home}` |
|---|---|
| Windows | `%USERPROFILE%` |
| Unix | `$HOME` |

`$SWARM_AGENTS` replaces `{home}/swarm-agents`. Hostname is the lease pin (`SHALOM`) or `COMPUTERNAME` / `hostname`. Role is the lease role (`swarm-bench`).

```text
python3 scripts/swarm_paths.py --id win11-pixel
```

SHALOM:

| Slot | Path |
|---|---|
| agentHome | `%USERPROFILE%\swarm-agents\japanglify\SHALOM\swarm-bench` |
| official | `…\official` |
| dev | `…\dev` |
| runner | `C:\actions-runner` |

## Fresh machine (Grok CLI + this script)

Windows — **one** PowerShell (System32). Hunts Git/gh/pwsh, then clones. No git-on-PATH, no curl:

```powershell
$boot = "$env:TEMP\swarm-bootstrap.ps1"
Invoke-WebRequest -UseBasicParsing -Uri "https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.ps1" -OutFile $boot
& "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $boot -Restore
```

`-Restore` = hunt → clone/pull → idle stop leftover listener → arm → `swarm-grok`. Flags: `-Stop`, `-Runner`, `-Start`, `-DryRun` separately. Do not `irm | iex`.

Unix:

```sh
curl -fsSL https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.sh | sh
```

Flags: `-Runner`/`--runner`, `-Start`/`--start`, `-DryRun`/`--dry-run`. Then:

```bat
%USERPROFILE%\swarm-agents\japanglify\%COMPUTERNAME%\swarm-bench\official\scripts\swarm-grok.cmd
```

## Which tree (not “conductor vs everything else”)

Official is **swarm + releases**. Dev is the **app**. Several agents need both. Cloud conductor needs no clone.

| Work | Tree |
|---|---|
| Conductor, `/accept`, issues, workflows, budget, hosts | official repo on GitHub |
| `swarm-grok`, prompt-bench, bench-runner source | **official** clone |
| Listener process | runner dir (not under swarm-agents) |
| `/latest`, keystore | official (signer — not Bench) |
| Product source, `agent/*`, PRs, Gradle, `adb` | **dev** clone |
| Classify / fix | **Start** Grok on official; **edit** dev `agent/*` |
| `/uat` job | workflow on official; checkout of **dev** into runner workspace |

Do not implement app code on official `main`. Do not keep swarm scripts only on the fork.

Do not `cd` the runner dir to start Grok. Per-lease `workdir` overrides exist only for a broken disk — move the clone to this layout.
