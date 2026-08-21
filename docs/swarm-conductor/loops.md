# Loop guards

Components talk only through GitHub comments. A comment must not be a command for the thing that just wrote it.

| Writer | Must not trigger |
|---|---|
| UAT dispatch / installed / failed (`swarm-bench-uat`) | another `/uat` |
| Kick sent / bench woke (`swarm-kick`) | another `/kick` |
| Queue stall (`swarm-uat-queued`) | `/uat` or `/kick` |
| Watchdog ready (`swarm-uat-ready`, text mentions `/uat`) | `/uat` — **never** `contains('/uat')` in YAML |
| Clip offer / splice (`swarm-clip-compact`, bot) | another shrink |
| Conductor sticky (`swarm-conductor-status`) | `/accept` `/uat` `/kick` |
| Conductor Grok automation | `/uat` `/kick` `/clip-*` — **silent ignore, no “ignoring” comment** |
| Grok CLI on the bench | `/uat`, `/accept`, a second `agent/*` |

Rules in force:

- Commands match **start or end of a line** (`scripts/swarm_cmd.py`). Mid-line quotes are documentation, not a command. YAML never uses `contains('/uat')` or `contains('/kick')`.
- Bots (`github-actions[bot]`) never run `/uat`, `/kick`, or clip.
- A failed bench job is reported from **ubuntu** (`report` job).
- `/kick` does not `/uat` or `/accept`. Spec: [kick.md](kick.md).
- Queued > 20 min is [events.md](events.md), not a Grok poll.
- `/accept` does not spawn a worker.
- Conductor never replies “plain issue, not a PR”. Prompt: [prompt.md](prompt.md).
