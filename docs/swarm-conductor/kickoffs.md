# Kickoffs

Effort lives on the **GitHub issue**, not in chat.

1. Label `effort:low` | `effort:medium` | `effort:high` | `effort:xhigh`
2. First line of the issue body: `**Effort: high** (`grok --effort high`)`
3. If both missing: **medium** (do not use the CLI default `high`)

Swarm Bench **shutdown and restart** (canonical): [prompt-bench.md](prompt-bench.md). Policy copy: [swarm-bench.md](../japanglify/swarm-bench.md).

**Null start** (no transcript — Restore / first this logon). In the clone that has `prompt-bench.md`:

PowerShell:

```text
git pull origin main
grok --effort medium (Get-Content -Raw docs/swarm-conductor/prompt-bench.md)
```

bash:

```text
git pull origin main
grok --effort medium "$(< docs/swarm-conductor/prompt-bench.md)"
```

`PROMPT` is the first TUI message (the role file). Do not pass `--resume`, `-r`, `--continue`, `-c`, `-p`, or `--single`. Restore effort is **medium**, not `low`.

| Situation | Do |
|---|---|
| **Normal stop** | `/quit`. Stay logged on. Listener stays. |
| **Normal start** (healthy) | `grok --effort <from the issue> --resume` — same cwd |
| **Restore** | Null start (above) |

Bootstrap is always **medium**. Do not `/effort` mid-session. `--resume` / `--continue` are only for **healthy** product work. A session you shut down because it failed must not be continued — the transcript would recur.

Conductor cloud automations use [prompt.md](prompt.md) the same way (that file **is** their context).

| Level | Use |
|---|---|
| `low` | Mechanical one-shot |
| `medium` | Bootstrap; domain tests |
| `high` | Overlay / timing / a11y on device |
| `xhigh` | Classification + fix generation when the owner sets the label |

Do not debate effort in a Conductor thread. Change the label on the issue.

When you post a **state change** (handoff, installed, failed), append a usage fence from `scripts/swarm-usage.py`. Cost rides that comment. Do not open a second comment for metrics. Do not invent `grokCredits`. Spec: [usage.md](usage.md).
