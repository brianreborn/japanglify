# GitHub is the event bus (do not poll Grok)

GitHub already long-polls and webhooks. Do not 2-minute busy-wait in a Grok chat.

The bench does **not** poll the conductor. The conductor does **not** poll SHALOM. Mailbox is GitHub.

```mermaid
sequenceDiagram
  participant You
  participant Conductor as Conductor (cloud)
  participant GH as GitHub Actions queue
  participant Watch as kick-watch.ps1 on SHALOM
  participant Listener as Runner.Listener on SHALOM
  participant Bench as assemble plus adb

  You->>GH: /uat or workflow_dispatch
  Conductor->>GH: ubuntu dispatch, then bench job labels swarm-bench
  Note over GH: run is in_progress; bench job still queued
  loop outbound every 20s
    Watch->>GH: any in_progress or queued UAT?
    Watch->>Listener: start if dead
  end
  loop long-poll (GitHub protocol)
    Listener->>GH: any job for me?
  end
  GH->>Listener: bench job
  Listener->>Bench: run it
  Bench->>GH: logs plus UAT installed comment
  GH->>You: workflow_run_completed
```

| Event | Native mechanism |
|---|---|
| Job available for SHALOM | `Runner.Listener` long-polls Actions (this **is** the backend poll) |
| Listener dead after ubuntu dispatch | `swarm-kick-watch.ps1` sees `in_progress` UAT (run) + queued bench **job** |
| Bench finished | same workflow `report` job (`needs: bench`) |
| Owner notice | Grok automation `japanglify-uat-complete` on `workflow_run_completed` |
| `/kick` | Actions `swarm-kick.yml` **ubuntu mailbox only**. Never occupies swarm-bench |
| Queued bench job > 20 min | Watchdog `swarm-watchdog.yml` every 10 min posts `swarm-uat-queued` once, **for that issue's bench job only** |

`workflow_run_completed` never fires while a job is **queued**. An `in_progress` **run** after ubuntu dispatch is **not** a live listener — only a `swarm-bench` **job** with status `in_progress` is.
