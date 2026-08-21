# Kickoffs

Every kickoff **states effort** as the first operational line. Grok CLI defaults to `high` if you omit it. That is usually the wrong default.

```text
Effort: <low|medium|high|xhigh>
grok --effort <level>
```

In a running session: `/effort <level>` before the rest of the paste.

| Level | Use |
|---|---|
| `low` | Mechanical one-shot (too thin for lease/`adb` checks) |
| `medium` | Swarm Bench bootstrap + local UAT (clone, lease, assemble, `adb`) |
| `high` | Nasty overlay/timing/a11y hunt, or conductor/policy design |
| `xhigh` | grok-4.6 only; not bench bootstrap |

Do not leave effort implicit. Product kickoff text lives in the instance overlay (Japanglify: `docs/japanglify/swarm-bench-kickoff.md`).
