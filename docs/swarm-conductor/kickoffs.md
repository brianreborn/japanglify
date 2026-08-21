# Kickoffs

Effort lives on the **GitHub issue**, not in chat.

1. Label `effort:low` | `effort:medium` | `effort:high` | `effort:xhigh`
2. First line of the issue body: `**Effort: high** (`grok --effort high`)`
3. If both missing: **medium** (do not use the CLI default `high`)

Bootstrap (clone, `adb`, lease, runner) is always **medium**.
When product work starts, **do not** `/effort` mid-session. Canonical relaunch:

```text
/quit
grok --effort <from the issue> --resume
```

`--resume` keeps the same clone/lease/runner context. `--effort` must be on that process start. Same cwd.

| Level | Use |
|---|---|
| `low` | Mechanical one-shot |
| `medium` | Bootstrap; domain tests |
| `high` | Overlay / timing / a11y on device |
| `xhigh` | Classification + fix generation when the owner sets the label |

Do not debate effort in a Conductor thread. Change the label on the issue.

When you post a **state change** (handoff, installed, failed), append a usage fence from `scripts/swarm-usage.py`. Cost rides that comment. Do not open a second comment for metrics. Do not invent `grokCredits`. Spec: [usage.md](usage.md).
