# Owner only

Not for reporters. Public repo, but this page is the admin cheat sheet.

**USB rubric:** plugged + one `adb` device = Pixel UAT is possible. Unplugged = no `/uat`. Listener arm does not need USB; idle stop while unplugged so a leftover listener cannot grab the queue.

| What | Path (from `scripts/swarm_paths.py --id win11-pixel`) |
|---|---|
| Agent home | `%USERPROFILE%\swarm-agents\japanglify\SHALOM\swarm-bench` |
| Official clone (`swarm-grok`) | `…\official` |
| Dev clone (`agent/*`) | `…\dev` |
| GitHub Actions listener | `C:\actions-runner` — **not** under swarm-agents |

```bat
cd /d %USERPROFILE%\swarm-agents\japanglify\SHALOM\swarm-bench\official
git pull origin main
scripts\swarm-grok.cmd
```

Thin `cmd.exe` PATH: `scripts\swarm-path.ps1` hunts system Git (HKLM GitForWindows), GitHub CLI, pwsh, Python, adb, JDK. `swarm-path.cmd` always launches `%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe` then imports `SWARM_GIT` / `SWARM_GH` / PATH. Not `where.exe`.

First machine: [paths.md](paths.md) (`swarm-bootstrap.ps1` / `swarm-bootstrap.sh`). Grok CLI is the other client.

`swarm-grok.py` cds to the official clone from its own location. `C:\actions-runner` is only `Runner.Listener` / `swarm-run-loop.cmd`.

CLI ritual is **not** this page. It is [prompt-bench.md](../swarm-conductor/prompt-bench.md) ([raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt-bench.md)). This page is github.com + when to use that ritual.

Grok CLI default effort on this host is **medium** (`%USERPROFILE%\.grok\config.toml`, set by `swarm-bench-runner.ps1`). Fleet cap (SuperGrok window, per-role clamp, model): [budget.json](budget.json) / [budget.md](../swarm-conductor/budget.md). Effective flags: `python3 scripts/swarm_budget.py --role swarm-bench --issue-effort xhigh --argv`. Omit `--effort` unless that prints one.

## Shutdown and restart

**Null start** (no transcript). One script, both OS: [budget.md](../swarm-conductor/budget.md).

```bat
scripts\swarm-grok.cmd
```

```sh
./scripts/swarm-grok
```

`--resume` is healthy continue. `--issue-effort xhigh` if the issue asks and the cap allows. No `-r`/`-c`/`-p` on Restore.

| Situation | Do | Do not |
|---|---|---|
| **Idle stop** | `/quit` then `scripts\swarm-bench-stop.cmd` | Leave a disconnected `Runner.Listener` |
| **Leave listener** (unattended `/uat`) | `/quit` only | |
| **Normal start** | `scripts\swarm-grok.cmd --resume` (add `--issue-effort` only if the issue is not medium **and** the cap allows) | Slurp a new brain if the last session was healthy |
| **Restore** | Null start (above) | `--resume` / `--continue` a session you killed because it failed |
| **Runner offline** | Restore | A second `/uat` |
| **You** | Never type `pwsh` | The CLI runs `swarm-bench-runner.ps1` |

`--resume` and `--continue` replay a transcript. Restore is Null start (role file as `PROMPT`).

## Two install paths (never both)

| Path | When |
|---|---|
| Grok CLI + `adb` | You are at the box, iterating. GitHub is silent |
| `/uat` on the **issue** | You are on github.com. Actions uses the listener |

If Actions already owns the phone, the CLI does not `adb install`.

## Commands (github.com, saved replies)

Bodies exactly: `/accept` `/block` `/uat` `/kick` `/clip-ok`. [How](saved-replies.md). Spec: [commands.md](commands.md).

| You type | What you are telling the swarm |
|---|---|
| `/accept` | Intake ACCEPTED. Cloud conductor only. Does **not** start a bench worker |
| `/block` | Stop intake |
| `/uat` | Install this issue’s `agent/<n>-*` on the Pixel via the **same** win11-pixel listener |
| `/kick` or `/kick win11-pixel` | Mailbox: list pending. Does **not** install, does **not** occupy swarm-bench |
| `/clip-ok` | Splice compact clip (you or the reporter) |

## Per step

| Step | Host | You do |
|---|---|
| intake | `grok-cloud` | `/accept` on the issue. Do not start Grok CLI for this |
| classify / fix | `win11-pixel` | **Normal start**. `--effort xhigh` only if that is the issue label |
| uat | same box, Actions listener | `/uat` on github.com. Unlock the phone. Listener already up. No second `/uat` |
| stall | GitHub-hosted watchdog | **UAT still queued** (~20 min): **Restore** the CLI. Do not `/uat` again |
| done | Grok `japanglify-uat-complete` | APP_ONLY when the workflow **completes**. Issue already has installed/failed |

## Major problem: listener down

`/uat` comments can look successful (“dispatched”) while the bench job sits **queued**. Ping is red if `/actions/runners` says `offline`. `GITHUB_TOKEN` in Actions often 403s that API (unknown, not green). Owner `gh` can list `SHALOM-swarm-bench`.

After **20 minutes queued**, watchdog posts **UAT still queued** (`swarm-uat-queued`). That is not a second `/uat`.

Proof the host is up: GitHub runner **online**, or `Runner.Listener.exe`. HKCU Run / `swarm-run-loop.cmd` is the supervisor. Grok is not.

## Exceptions log

| When | What | Why |
|---|---|---|
| 2026-08-21 | `workflow_dispatch` #5/#7 runs 31–32 cancelled | empty concurrency group |
| 2026-08-21 | `workflow_dispatch` #5 [run 35](https://github.com/brianreborn/japanglify/actions/runs/32489983569) and #7 [run 36](https://github.com/brianreborn/japanglify/actions/runs/32490006022) | re-fire after concurrency fix; bench **queued** — listener not proven |
| 2026-08-21 | #5 [run 42](https://github.com/brianreborn/japanglify/actions/runs/32493916092) | listener still not taking jobs |
| 2026-08-21 | #5 [run 43](https://github.com/brianreborn/japanglify/actions/runs/32497992026) cancelled | USB paused; do not fail adb on an idle-offline bring-up |

Re-fire via `workflow_dispatch` **only** when a `/uat` job is not queued and never reached **UAT installed**. Do not use this because phone UAT failed — that stays the same pull request + a later `/uat`.

## Do not

- Type `pwsh`
- `/uat` or `adb install` from Grok CLI while Actions owns the phone
- `--resume` or `--continue` a session you shut down because it failed
- `--effort medium` (already the host default)
- Start a second `agent/*` for #5 or #7
- `/accept` #6 until you mean to start that work
- Assemble on `ubuntu-latest` for yourself
- Pin `pool-bench-windows` (dormant)
