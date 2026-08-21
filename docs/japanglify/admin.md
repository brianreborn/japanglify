# Owner only

Not for reporters. Public repo, but this page is the admin cheat sheet.

**One live bench:** `win11-pixel` — Grok CLI + the Actions listener, same Windows logon session, Pixel on USB.

## Major problem: listener start failure

`/uat` does **nothing** unless `Runner.Listener` is running in the logged-in Windows session. Grok CLI `/quit` / `--resume` does **not** revive a dead Actions job. Cloud watchdog cannot list runners (`GITHUB_TOKEN` 403). The owner must not type `pwsh`.

If the CLI fails to start (or keep) the listener:

- `/uat` comments look successful ("dispatched")
- bench jobs sit **queued** then die
- `--resume` does not pick them up
- there is no routine recovery except telling that same CLI to start `scripts/swarm-bench-runner.ps1` itself

**Invariant:** bootstrap must print `Runner.Listener` running (or start it) **before** `/quit`. The logon scheduled task is backup, not the proof. Treat listener-down as a P0 on Swarm Bench, not a docs miss.

Live evidence 2026-08-21: [#5 run 35](https://github.com/brianreborn/japanglify/actions/runs/32489983569) and [#7 run 36](https://github.com/brianreborn/japanglify/actions/runs/32490006022) still queued on `self-hosted, Windows, swarm-bench` after dispatch.

## Relaunch (you type this; never `pwsh`)

In the running Grok CLI:

```text
/quit
```

Same clone directory:

```text
grok --effort xhigh --resume
```

`--resume` is the same cwd, lease, and listener. `--effort` must be on that process start (issues #5 #6 #7 are `xhigh`).

If it asks for a first message, paste **exactly**:

```text
Swarm Bench on win11-pixel. If Runner.Listener is not in this logon session, start scripts/swarm-bench-runner.ps1 yourself — do not ask me to type pwsh. Prove it with Get-Process Runner.Listener before you stop. Do not /uat. Do not adb install. Actions owns Pixel UAT. Unlock the phone when a job is queued. Continue existing agent/5-chip-persistence and agent/electrobrian-9-proper-names. No second pull request. #6 waits for /accept.
```

Validate: after resume it reports lease `win11-pixel`, one `adb` `device`, and **`Runner.Listener` running**. It does not install an APK.

## Commands (github.com, saved replies)

Bodies exactly: `/accept` `/block` `/uat` `/clip-ok`. [How](saved-replies.md).

| You type | What you are telling the swarm |
|---|---|---|
| `/accept` | Intake ACCEPTED. Cloud conductor only. Does **not** start a bench worker |
| `/block` | Stop intake |
| `/uat` | Install this issue’s `agent/<n>-*` on the Pixel via the **same** win11-pixel listener |
| `/clip-ok` | Splice compact clip (you or the reporter) |

## Per step

| Step | Host | You do |
|---|---|---|
| intake | `grok-cloud` | `/accept` on the issue. Do not start Grok CLI for this |
| classify / fix | `win11-pixel` | `/quit` then `grok --effort xhigh --resume` |
| uat | same box, Actions listener | `/uat` on github.com. Unlock the phone. Listener must already be up. No second `/uat` |
| watchdog | GitHub-hosted | Nothing. **Ready for Pixel UAT** is your cue |

## Exceptions log

| When | What | Why |
|---|---|---|
| 2026-08-21 | `workflow_dispatch` #5/#7 runs 31–32 cancelled | empty concurrency group |
| 2026-08-21 | `workflow_dispatch` #5 [run 35](https://github.com/brianreborn/japanglify/actions/runs/32489983569) and #7 [run 36](https://github.com/brianreborn/japanglify/actions/runs/32490006022) | re-fire after concurrency fix; bench **queued** — listener not proven |

Re-fire via `workflow_dispatch` **only** when a `/uat` job is not queued and never reached **UAT installed**. Do not use this because phone UAT failed — that stays the same pull request + a later `/uat`.

## Do not

- Type `pwsh` — the CLI starts the listener
- `/uat` or `adb install` from Grok CLI while Actions owns the phone
- Start a second `agent/*` for #5 or #7
- `/accept` #6 until you mean to start that work
- Assemble on `ubuntu-latest`
- Pin `pool-bench-windows` (dormant)
