# Swarm ping (host reliability)

This is the **ping**, not UAT. Cloud observes GitHub. It does not install, and it does **not** enqueue a self-hosted job if `swarm-bench` is already queued.

Active hosts: `win11-pixel`, `github-actions`, `grok-cloud`. Pools and `unix-pixel` are dormant and not pinged.

| Host | How we know |
|---|---|
| `github-actions` | last `conductor-config.yml` conclusion |
| `win11-pixel` | queued vs `in_progress` vs last completed `swarm-bench` job |
| `grok-cloud` | event-driven (no Actions ping) |

Schedule: every 15 minutes + **workflow_dispatch** `swarm-ping.yml`.

A **red** ping run means a live host is **down** (`ok: false`) — today that is a queued `swarm-bench` job the listener is not taking. Cancelled/skipped last bench (USB UAT paused) is **unknown**, not red, as long as `github-actions` is up.

`/kick` is the wake. Ping only looks.
