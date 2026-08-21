# Swarm host paths

Given a lease id, the tree is always the same. Matcher: `scripts/swarm_paths.py`. Template: [hosts.json](hosts.json) `workdir`. Repos: [instance.json](instance.json).

```
{home}/src/{owner}/{repo}
```

| OS | `{home}` | Listener (not a git clone) |
|---|---|---|
| Windows | `%USERPROFILE%` | `C:\actions-runner` |
| Unix | `$HOME` | `{home}/actions-runner` |
| GitHub-hosted | n/a | `$GITHUB_WORKSPACE` |
| grok-cloud | none | none |

`$SWARM_SRC` replaces `{home}/src` on any OS.

## Fresh machine (Grok CLI + this script)

Need **git** and **Grok CLI**. Python 3 for `swarm-grok`. Android JDK/SDK/`adb`/`gh` for Bench UAT. This script only checks out the two trees.

Windows (save, then run — do not `irm | iex`, `param()` needs `-File`):

```bat
curl -L -o %TEMP%\swarm-bootstrap.ps1 https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.ps1
powershell -ExecutionPolicy Bypass -File %TEMP%\swarm-bootstrap.ps1
```

```powershell
irm https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.ps1 -OutFile $env:TEMP\swarm-bootstrap.ps1
powershell -ExecutionPolicy Bypass -File $env:TEMP\swarm-bootstrap.ps1
# optional: -Runner  (listener)   -Start  (swarm-grok)   -DryRun
```

Unix:

```sh
curl -fsSL https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.sh | sh
# or save and: sh swarm-bootstrap.sh --dry-run
# optional: --runner --start
```

Then, from the official clone:

```bat
%USERPROFILE%\src\brianreborn\japanglify\scripts\swarm-grok.cmd
```

```sh
$HOME/src/brianreborn/japanglify/scripts/swarm-grok
```

## Which tree (not “conductor vs everything else”)

**No.** Swarm Conductor on `brianreborn` and “everything else” on `electrobrian` is the wrong cut. The official tree is the **swarm + releases**. The dev tree is the **app**. Several agents need both.

| Tree | Repo | Persistent path (bench) |
|---|---|---|
| **official** (rel) | `brianreborn/japanglify` | `{home}/src/brianreborn/japanglify` |
| **dev** | `electrobrian/japanglify` | `{home}/src/electrobrian/japanglify` |
| **runner workspace** | ephemeral checkout | `C:\actions-runner\_work\…` (job only) |

| Work | Tree |
|---|---|
| Swarm Conductor Grok automations, `/accept` `/block` | official **issues** (cloud; no clone) |
| `/uat` `/kick` `/clip-*` workflows, ping, watchdog | official `.github/` |
| `swarm-grok`, `prompt-bench.md`, `swarm-bench-runner.ps1`, budget, hosts | official clone |
| Register / keep `SHALOM-swarm-bench` | official repo + `C:\actions-runner` |
| Official `/latest`, real keystore | official (signer role — **not** Swarm Bench) |
| Product source, `agent/*`, pull requests into `BETA-2` | **dev** |
| Gradle assemble, inner-loop `adb`, tester APKs | **dev** |
| Classify / fix generation | Grok **starts** on official (`swarm-grok`); **edits** the dev `agent/*` branch |
| `/uat` job assemble+install | workflow on official; checkout of **dev** `agent/*` into runner workspace |

Cloud conductor never needs a laptop clone. Swarm Bench on SHALOM keeps **both** clones. Do not implement Japanglify app code on official `main`. Do not keep swarm scripts only on the fork — `/uat` and `swarm-grok` would drift.

```text
python3 scripts/swarm_paths.py --id win11-pixel
python3 scripts/swarm_paths.py --id unix-pixel --json
```

SHALOM (`win11-pixel`), if `USERPROFILE=C:\Users\brian`:

| Slot | Path |
|---|---|
| official (`swarm-grok`) | `C:\Users\brian\src\brianreborn\japanglify` |
| dev (`agent/*`) | `C:\Users\brian\src\electrobrian\japanglify` |
| runner | `C:\actions-runner` |

Do not `cd` the runner dir to start Grok. Per-lease `workdir` overrides exist only for a broken disk; do not add them because the clone “already lives somewhere else” — move the clone to the layout.
