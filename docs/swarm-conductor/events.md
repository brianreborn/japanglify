# GitHub is the event bus (do not poll Grok)

GitHub already long-polls and webhooks. Do not 2-minute busy-wait in a Grok chat.

The bench does **not** poll the conductor. The conductor does **not** poll SHALOM. Mailbox is GitHub.

**Grok CLI is not the supervisor.** A hidden `swarm-run-loop.cmd` + HKCU Run + `swarm-kick-watch.ps1` keep `Runner.Listener` alive for the logon session.

```mermaid
sequenceDiagram
  participant You
  participant Conductor as Conductor (cloud)
  participant GH as GitHub Actions queue
  participant Loop as run-loop plus watch on SHALOM
  participant Listener as Runner.Listener on SHALOM
  participant Bench as assemble plus adb

  Note over Loop,Listener: started once per logon; Grok may quit
  loop every 20s if Listener dead
    Loop->>Listener: start run.cmd
  end
  You->>GH: /uat or workflow_dispatch
  Conductor->>GH: ubuntu dispatch, then bench job labels swarm-bench
  Note over GH: run is in_progress; bench job still queued
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
| Listener crashed or window closed | `swarm-run-loop.cmd` restarts it; watch does too even with an empty mailbox |
| Logon | HKCU Run (Scheduled Task is optional; SAC often blocks it) |
| Bench finished | same workflow `report` job (`needs: bench`) |
| Owner notice | Grok automation `japanglify-uat-complete` on `workflow_run_completed` |
| `/kick` | Actions `swarm-kick.yml` **ubuntu mailbox only**. Never occupies swarm-bench |
| Queued bench job > 20 min | Watchdog `swarm-watchdog.yml` every 10 min posts `swarm-uat-queued` once, **for that issue's bench job only** |
| Idle host down | Ping reads `/actions/runners` — `offline` is red even after USB cancel |

`workflow_run_completed` never fires while a job is **queued**. An `in_progress` **run** after ubuntu dispatch is **not** a live listener — only a `swarm-bench` **job** with status `in_progress`, or runner status **online**, is.
