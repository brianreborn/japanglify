# Owner only

Not for reporters. Public repo, but this page is the admin cheat sheet.

**One live bench:** `win11-pixel` — Grok CLI + the Actions listener, same Windows logon session, Pixel on USB. Classify, fix, Gradle, `adb`, `/uat` all happen there. Do not add a second runner yet.

## Commands (github.com, saved replies)

Bodies exactly: `/accept` `/block` `/uat` `/clip-ok`. [How](saved-replies.md).

| You type | What you are telling the swarm |
|---|---|
| `/accept` | Intake ACCEPTED. Cloud conductor only. Does **not** start a bench worker |
| `/block` | Stop intake |
| `/uat` | Install this issue’s `agent/<n>-*` on the Pixel via the **same** win11-pixel listener |
| `/clip-ok` | Splice compact clip (you or the reporter) |

Unauthorized `/uat` is ignored (Actions warning log only). Mid-line quotes do not fire.

## Per step (where to look)

| Step | Host | You do |
|---|---|
| intake | `grok-cloud` | `/accept` on the issue. Do not start Grok CLI for this |
| classify / fix | `win11-pixel` | `/quit` then `grok --effort xhigh --resume` (effort is on the issue) |
| uat | `win11-pixel` listener | `/uat` on github.com. Unlock the phone. If it sits: `pwsh -File scripts/swarm-bench-runner.ps1`. No second `/uat` |
| watchdog | GitHub-hosted | Nothing. **Ready for Pixel UAT** is your cue |

Clip transcode is GitHub-hosted ffmpeg, not the Pixel box.

## Do not

- `/uat` or `adb install` from Grok CLI while Actions owns the phone
- Start a second `agent/*` for #5 or #7
- `/accept` #6 until you mean to start that work
- Assemble on `ubuntu-latest`
- Pin `pool-bench-windows` (dormant)
- Paste a command table on the issue

## Links

- Live bugs: [cutover.md](cutover.md)
- Launch dry-run: `python3 scripts/swarm-launch.py --step uat --issue 5`
- Proof CI: [conductor-config](https://github.com/brianreborn/japanglify/actions/workflows/conductor-config.yml)
