# Kickoffs

Effort lives on the **GitHub issue**, not in chat.

1. Label `effort:low` | `effort:medium` | `effort:high` | `effort:xhigh`
2. First line of the issue body: `**Effort: high** (`grok --effort high`)`
3. If both missing: **medium** (do not use the CLI default `high`)

```text
grok --effort <from the issue>
```

In a running session: `/effort <from the issue>` after reading the issue.

Bootstrap (clone, `adb`, lease) is always **medium**. Switch to the issue’s level when product work starts.

| Level | Use |
|---|---|
| `low` | Mechanical one-shot |
| `medium` | Default; domain tests; design hash-out |
| `high` | Overlay / timing / a11y on device |
| `xhigh` | grok-4.6 only; rare |

Do not debate effort in a Conductor thread. Change the label on the issue.

When you post a **state change** (handoff, installed, failed), append a usage fence from `scripts/swarm-usage.py`. Cost rides that comment. Do not open a second comment for metrics. Do not invent `grokCredits`. Spec: [usage.md](usage.md).
