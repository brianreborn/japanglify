# Owner only

Not for reporters. Public repo, but this page is the admin cheat sheet.

**One live bench:** `win11-pixel` — Grok CLI + the Actions listener, same Windows logon session, Pixel on USB.

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
Swarm Bench on win11-pixel. If Runner.Listener is not in this logon session, start scripts/swarm-bench-runner.ps1 yourself — do not ask me to type pwsh. Then stop. Do not /uat. Do not adb install. Actions owns Pixel UAT. Unlock the phone when a job is queued. Continue existing agent/5-chip-persistence and agent/electrobrian-9-proper-names. No second pull request. #6 waits for /accept.
```

Validate: after resume it reports lease `win11-pixel`, one `adb` `device`, and listener running (or it started it). It does not install an APK.

## Commands (github.com, saved replies)

Bodies exactly: `/accept` `/block` `/uat` `/clip-ok`. [How](saved-replies.md).

| You type | What you are telling the swarm |
|---|---|
| `/accept` | Intake ACCEPTED. Cloud conductor only. Does **not** start a bench worker |
| `/block` | Stop intake |
| `/uat` | Install this issue’s `agent/<n>-*` on the Pixel via the **same** win11-pixel listener |
| `/clip-ok` | Splice compact clip (you or the reporter) |

## Per step

| Step | Host | You do |
|---|---|
| intake | `grok-cloud` | `/accept` on the issue. Do not start Grok CLI for this |
| classify / fix | `win11-pixel` | `/quit` then `grok --effort xhigh --resume` |
| uat | same box, Actions listener | `/uat` on github.com. Unlock the phone. If it sits: same relaunch as above. No second `/uat` |
| watchdog | GitHub-hosted | Nothing. **Ready for Pixel UAT** is your cue |

## Do not

- Type `pwsh` — the CLI starts the listener
- `/uat` or `adb install` from Grok CLI while Actions owns the phone
- Start a second `agent/*` for #5 or #7
- `/accept` #6 until you mean to start that work
- Assemble on `ubuntu-latest`
- Pin `pool-bench-windows` (dormant)
