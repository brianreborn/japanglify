# Kickoffs

Effort lives on the **GitHub issue**, then the **fleet cap**. Matcher: [budget.md](budget.md) / `scripts/swarm_budget.py`. On the Swarm Bench host the Grok CLI default is **medium** (`%USERPROFILE%\.grok\config.toml`, written by `scripts/swarm-bench-runner.ps1`).

1. Label `effort:low` | `effort:medium` | `effort:high` | `effort:xhigh`
2. First line of the issue body: `**Effort: high** (`grok --effort high`)`
3. If both missing: **medium** — omit `--effort`
4. **Effective** = min(issue, `budget.json` `cap.effort`, `perRole[role]`). `--effort` / `--model` only when effective is not the host default.

Do not debate remaining SuperGrok % in chat. Change the issue label and/or `docs/japanglify/budget.json`. A cap change is the **next** process start, not `/effort` mid-session.

Swarm Bench **shutdown and restart** (canonical): [prompt-bench.md](prompt-bench.md). Policy copy: [swarm-bench.md](../japanglify/swarm-bench.md).

**Null start** (no transcript — Restore / first this logon). In the clone that has `prompt-bench.md`:

PowerShell:

```text
git pull origin main
grok (Get-Content -Raw docs/swarm-conductor/prompt-bench.md)
```

bash:

```text
git pull origin main
grok "$(< docs/swarm-conductor/prompt-bench.md)"
```

No `--effort` unless `swarm_budget.py --argv` prints one. No `--resume`, `-r`, `--continue`, `-c`, `-p`.

| Situation | Do |
|---|---|
| **Normal stop** | `/quit`. Stay logged on. Listener stays. |
| **Normal start** (healthy) | `grok --resume` plus whatever `swarm_budget.py --argv` prints |
| **Restore** | Null start (above) |

`--resume` / `--continue` are only for **healthy** product work.

Conductor cloud automations use [prompt.md](prompt.md) the same way (that file **is** their context). They stay at `perRole` **low**.

| Level | Use |
|---|---|
| `low` | Mechanical one-shot; cloud automations |
| `medium` | Default. Bootstrap; domain tests |
| `high` | Overlay / timing / a11y on device — only if cap allows |
| `xhigh` | Classification + fix generation — only if cap allows (burn a SuperGrok window) |

When you post a **state change** (handoff, installed, failed), append a usage fence from `scripts/swarm-usage.py`. Cost rides that comment. Do not invent `grokCredits`. Spec: [usage.md](usage.md).
