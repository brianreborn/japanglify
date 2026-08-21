# Kick backchannel

Mailbox on GitHub. **No inbound HTTPS** on laptops. Canonical parser: `scripts/swarm_kick.py` (`scripts/swarm-kick.py` is a shim).

`/kick` (owner, whole line) or Actions **workflow_dispatch** `swarm-kick.yml`.

Kick is **ubuntu-only**. It must never occupy `[self-hosted, swarm-bench]` — that runner is the thing we are trying to wake.

| You type | Who wakes |
|---|---|
| `/kick` or `/kick all` | cloud pending-list + watch mailbox for `win11-pixel` |
| `/kick win11-pixel` | same (cloud list + watch mailbox) |
| `/kick grok-cloud` | only cloud pending-list |

What a member does when kicked:

- **Cloud:** list UAT/kick runs (`queued` / `waiting` / `in_progress`). Does not `/uat` or `/accept`.
- **Watch** (`scripts/swarm-kick-watch.ps1` on SHALOM): outbound poll every 20s. If a UAT run is queued **or in_progress** and the listener is down, start it.
- **Do not** schedule a swarm-bench job from kick. Proof the listener is up is UAT's first bench step.

After ubuntu `dispatch`, the UAT **run** is `in_progress` while the bench **job** may still be queued. Watch and ping must look at the job, not only `--status queued` on the run.

`swarm-bench-runner.ps1` starts the watch companion. Grok CLI on the box should run that once; the owner never types `pwsh`.

If a bench job stays queued **20 minutes**, watchdog comments **UAT still queued**. That is not a kick and not a second `/uat`.
