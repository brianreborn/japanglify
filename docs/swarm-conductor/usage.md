# Usage on state change

Workers report cost **only when they already have a state change to post**
(ACCEPTED, UAT installed, handoff, failed, BLOCKED). No heartbeat. No extra
GitHub comment just for metrics.

The envelope is a hidden fence on that same comment. Humans see one line.
Watchdog / a dashboard can `sum` later. This is generic Conductor, not Japanglify.

**Budget knob is not this fence.** Remaining SuperGrok is [budget.md](budget.md). This fence is a **receipt**. Do not scrape the meter.

## Fence

```
<!-- swarm-usage
{JSON}
-->
```

```json
{
  "schema": 1,
  "at": "2026-08-21T13:20:00Z",
  "role": "swarm-bench",
  "host": "WIN11-PIXEL",
  "issue": 5,
  "state": "installed",
  "effort": "high",
  "delta": {
    "wallSec": 412.2,
    "cpuSec": 88.1,
    "rssMb": 1200,
    "ghBillableMin": 0,
    "grokCredits": 1.4
  },
  "source": "self"
}
```

**Omit a key rather than guess.** `0` means measured zero. Missing means unknown.

| Field | Who can fill it |
|---|---|
| `wallSec` | Always. Clock of *this* state change, not lifetime of the machine |
| `cpuSec` | Process CPU if the OS gives it (Windows `TotalProcessorTime`, Unix `getrusage`). Else omit |
| `rssMb` | Peak RSS of the work process if cheap. Else omit |
| `ghBillableMin` | GitHub-hosted jobs only. Self-hosted is `0` to GitHub, still report `wallSec` |
| `grokCredits` | **Only if the Grok CLI / API printed a number.** Do not scrape the SuperGrok meter screenshot. Do not invent remaining weekly %. Effort/model flags are `swarm_budget.py`; credits are after-the-fact |

`delta` is since the previous state change from this worker on this issue (or since job start if first). Do not send a running total unless you also send `delta`.

## Where it lands

| State change | Comment |
|---|---|
| `/accept` `/block` | Sticky `<!-- swarm-conductor-status -->` — Grok automation; usually **no** numbers (it did almost no work) |
| `/uat` dispatched | `<!-- swarm-bench-uat -->` — ubuntu gate; tiny `ghBillableMin`, no Pixel |
| `/uat` installed / failed | same marker, **bench** fills `wallSec` / `cpuSec` |
| Agent handoff | ordinary issue comment from the worker, fence at the bottom |

Watchdog ready-pings carry **no** usage (the watchdog did not do the product work).

## Script

```text
python3 scripts/swarm-usage.py --role swarm-bench --state installed --issue 5 --wall-sec 412
```

Prints the fence + one human line. Pipe it into the comment body. `--self-test` on CI.

## Dashboard

Do not add a second store. Parse fences on the official issue thread (and, later, open pull-request comments). Sum `delta.wallSec` / `delta.grokCredits` per issue and per host. Host CPU *right now* is still the machine’s problem; this envelope is **job cost**, not a live `top`.
