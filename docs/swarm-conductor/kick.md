# Kick backchannel

Mailbox on GitHub. **No inbound HTTPS** on laptops. Canonical parser: `scripts/swarm_kick.py` (`scripts/swarm-kick.py` is a shim).

`/kick` (owner, whole line) or Actions **workflow_dispatch** `swarm-kick.yml`.

Kick is **ubuntu-only**. It must never occupy `[self-hosted, swarm-bench]` — that runner is the thing we are trying to wake.

**Grok CLI is not the supervisor.** `scripts/swarm-bench-runner.ps1` installs a hidden `swarm-run-loop.cmd` around `Runner.Listener` and persists it with HKCU Run (Smart App Control often blocks Scheduled Task). `swarm-kick-watch.ps1` restarts a dead listener even when the mailbox is empty. After that, the Grok window can `/quit`.

| You type | Who wakes |
|---|---|
| `/kick` or `/kick all` | cloud pending-list + watch mailbox for `win11-pixel` |
| `/kick win11-pixel` | same (cloud list + watch mailbox) |
| `/kick grok-cloud` | only cloud pending-list |

What a member does when kicked:

- **Cloud:** list UAT/kick runs (`queued` / `waiting` / `in_progress`). Does not `/uat` or `/accept`.
- **Watch** (`scripts/swarm-kick-watch.ps1` on SHALOM): outbound poll every 20s. Restarts the listener if it is down (job or no job).
- **Do not** schedule a swarm-bench job from kick. Proof the listener is up is GitHub runner status **online**, or UAT's first bench step.

After ubuntu `dispatch`, the UAT **run** is `in_progress` while the bench **job** may still be queued. Watch and ping must look at the job, not only `--status queued` on the run. Ping also reads `/actions/runners` so a USB-cancelled last job cannot hide `offline`.

If a bench job stays queued **20 minutes**, watchdog comments **UAT still queued**. That is not a kick and not a second `/uat`.
