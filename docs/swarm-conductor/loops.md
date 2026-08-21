# Loop guards

Components talk only through GitHub comments. A comment must not be a command for the thing that just wrote it.

| Writer | Must not trigger |
|---|---|
| UAT dispatch / installed (`swarm-bench-uat`) | another `/uat` |
| Watchdog ready (`swarm-uat-ready`, text mentions `/uat`) | `/uat` — **never** `contains('/uat')` in YAML |
| Clip offer / splice (`swarm-clip-compact`, bot) | another shrink |
| Conductor sticky (`swarm-conductor-status`) | `/accept` `/uat` |
| Grok CLI on the bench | `/uat`, `/accept`, a second `agent/*` |

Rules in force:

- Commands match **start or end of a line** (`scripts/swarm_cmd.py`). `` `/uat` `` mid-line is documentation, not a command. Trailing newline is ok. YAML never uses `contains('/uat')`.
- Bots (`github-actions[bot]`) never run `/uat` or clip.
- Duplicate “UAT dispatched” comments are suppressed; the bench job may still run (`cancel-in-progress` per issue).
- Watchdog ready-pings **once** per issue until a UAT marker exists. It does not install.
- `/accept` does not spawn a worker. Do not add that without a new identity (or you get accept → agent → accept).
- Grok CLI init starts the Actions **listener**, then **`/quit`**. Product work is `grok --effort <issue> --resume`. Inner-loop `adb` and GitHub `/uat` on the same Pixel at once is a race — still do not do it.
