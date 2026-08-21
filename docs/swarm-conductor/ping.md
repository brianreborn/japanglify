# Swarm ping (host reliability)

This is the **ping**, not UAT. Cloud observes GitHub. It does not install, and it does **not** enqueue a self-hosted job if `swarm-bench` is already queued.

Active hosts: `win11-pixel`, `github-actions`, `grok-cloud`. Pools and `unix-pixel` are dormant and not pinged.

| Host | How we know |
|---|---|
| `github-actions` | last `conductor-config.yml` conclusion |
| `win11-pixel` | **runner API** (`SHALOM-swarm-bench` status) first. Then jobs labeled `swarm-bench`. queued job + offline = listener down. `in_progress` job = listener took it. USB-cancelled last bench is **not** a live up if the runner is `offline`. |
| `grok-cloud` | event-driven (no Actions ping) |

Schedule: every 15 minutes + **workflow_dispatch** `swarm-ping.yml`.

A **red** ping run means a live host is **down** (`ok: false`) — queued `swarm-bench` job the listener is not taking, **or** the runner API says `offline`. Cancelled/skipped last bench (USB UAT paused) is **unknown** only when runner status is missing; with `offline` it is red.

`/kick` is the ubuntu wake mailbox. Ping only looks. Kick never occupies swarm-bench.
