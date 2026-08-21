# Kickoffs

Effort lives on the **GitHub issue**, then the **fleet cap**. Matcher: [budget.md](budget.md) / `scripts/swarm_budget.py`. Start: `scripts/swarm-grok.py` (cmd + sh trampolines). On the Swarm Bench host the Grok CLI default is **medium**.

1. Label `effort:low` | `effort:medium` | `effort:high` | `effort:xhigh`
2. First line of the issue body: `**Effort: high**`
3. If both missing: **medium** — omit `--effort`
4. **Effective** = min(issue, fleet cap, per-role), then `SWARM_EFFORT_MIN`/`SWARM_EFFORT_MAX` on this host. `swarm-grok` passes flags only when they are not the host default.

Do not debate remaining SuperGrok % in chat. Change the issue label and/or `docs/japanglify/budget.json`. A cap change is the **next** process start, not `/effort` mid-session.

Swarm Bench **shutdown and restart** (canonical): [prompt-bench.md](prompt-bench.md).

**Null start** (Restore / first this logon):

```bat
scripts\swarm-grok.cmd
```

```sh
./scripts/swarm-grok
```

**Normal start** (healthy): add `--resume`. Named issue: `--issue-effort xhigh` (still clamped by the cap).

| Situation | Do |
|---|---|
| **Idle stop** | `/quit` then `scripts\swarm-bench-stop.cmd` (listener down) |
| **Leave listener** | `/quit` only — unattended `/uat` |
| **Normal start** (healthy Grok) | `swarm-grok --resume` |
| **Restore** | Null start (above). No `--resume`. Then idle stop unless arming. |

`--resume` / `--continue` are only for **healthy** product work.

Conductor cloud automations use [prompt.md](prompt.md) the same way (that file **is** their context). They stay at `perRole` **low**.

| Level | Use |
|---|---|
| `low` | Mechanical one-shot; cloud automations |
| `medium` | Default. Bootstrap; domain tests |
| `high` | Overlay / timing / a11y on device — only if cap allows |
| `xhigh` | Classification + fix generation — only if cap allows (burn a SuperGrok window) |

When you post a **state change** (handoff, installed, failed), append a usage fence from `scripts/swarm-usage.py`. Cost rides that comment. Do not invent `grokCredits`. Spec: [usage.md](usage.md).
