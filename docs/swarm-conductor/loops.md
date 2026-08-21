# Loop guards

Components talk only through GitHub comments. A comment must not be a command for the thing that just wrote it.

| Writer | Must not trigger |
|---|---|
| UAT dispatch / installed / failed (`swarm-bench-uat`) | another `/uat` |
| Watchdog ready (`swarm-uat-ready`, text mentions `/uat`) | `/uat` — **never** `contains('/uat')` in YAML |
| Clip offer / splice (`swarm-clip-compact`, bot) | another shrink |
| Conductor sticky (`swarm-conductor-status`) | `/accept` `/uat` |
| Conductor Grok automation | `/uat` `/clip-*` — **silent ignore, no “ignoring” comment** |
| Grok CLI on the bench | `/uat`, `/accept`, a second `agent/*` |

Rules in force:

- Commands match **start or end of a line** (`scripts/swarm_cmd.py`). `` `/uat` `` mid-line is documentation, not a command. Trailing newline is ok. YAML never uses `contains('/uat')`.
- Bots (`github-actions[bot]`) never run `/uat` or clip.
- A failed bench job is reported from **ubuntu** (`report` job). The self-hosted runner cannot comment if it lost communication.
- Watchdog ready-pings **once** per issue until a UAT marker exists. It does not install.
- `/accept` does not spawn a worker.
- Conductor never replies “plain issue, not a PR” or “Automation is PR-only.” Prompt: [prompt.md](prompt.md).
- Grok CLI init starts the Actions **listener**, then **`/quit`**. Product work is `grok --effort <issue> --resume`.
