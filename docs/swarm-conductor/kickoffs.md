# Kickoffs

Effort lives on the **GitHub issue**, not in chat. On the Swarm Bench host the Grok CLI default is **medium** (`%USERPROFILE%\.grok\config.toml` `default_reasoning_effort`, written by `scripts/swarm-bench-runner.ps1`). Vendor CLI default is high; we do not live with that.

1. Label `effort:low` | `effort:medium` | `effort:high` | `effort:xhigh`
2. First line of the issue body: `**Effort: high** (`grok --effort high`)`
3. If both missing: **medium** — omit `--effort` (matches the host default)

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

No `--effort`, `--resume`, `-r`, `--continue`, `-c`, `-p`.

| Situation | Do |
|---|---|
| **Normal stop** | `/quit`. Stay logged on. Listener stays. |
| **Normal start** (healthy) | `grok --resume` — same cwd. Add `--effort <issue>` only if the issue is not medium |
| **Restore** | Null start (above) |

Do not `/effort` mid-session. `--resume` / `--continue` are only for **healthy** product work. A session you shut down because it failed must not be continued — the transcript would recur.

Conductor cloud automations use [prompt.md](prompt.md) the same way (that file **is** their context).

| Level | Use |
|---|---|
| `low` | Mechanical one-shot |
| `medium` | Default. Bootstrap; domain tests |
| `high` | Overlay / timing / a11y on device — pass `--effort high` |
| `xhigh` | Classification + fix generation when the owner sets the label — pass `--effort xhigh` |

Do not debate effort in a Conductor thread. Change the label on the issue.

When you post a **state change** (handoff, installed, failed), append a usage fence from `scripts/swarm-usage.py`. Cost rides that comment. Do not open a second comment for metrics. Do not invent `grokCredits`. Spec: [usage.md](usage.md).
