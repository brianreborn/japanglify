# Swarm host paths

Given a lease id, the tree is always the same. Matcher: `scripts/swarm_paths.py`. Template: [hosts.json](hosts.json) `workdir`. Repos: [instance.json](instance.json).

```
{home}/src/{owner}/{repo}
```

| OS | `{home}` | Listener (not a git clone) |
|---|---|---|
| Windows | `%USERPROFILE%` | `C:\actions-runner` |
| Unix | `$HOME` | `{home}/actions-runner` |
| GitHub-hosted | n/a | `$GITHUB_WORKSPACE` |
| grok-cloud | none | none |

`$SWARM_SRC` replaces `{home}/src` on any OS.

```text
python3 scripts/swarm_paths.py --id win11-pixel
python3 scripts/swarm_paths.py --id unix-pixel --json
```

SHALOM (`win11-pixel`), if `USERPROFILE=C:\Users\brian`:

| Slot | Path |
|---|---|
| official (`swarm-grok`) | `C:\Users\brian\src\brianreborn\japanglify` |
| dev (`agent/*`) | `C:\Users\brian\src\electrobrian\japanglify` |
| runner | `C:\actions-runner` |

Do not `cd` the runner dir to start Grok. Per-lease `workdir` overrides exist only for a broken disk; do not add them because the clone “already lives somewhere else” — move the clone to the layout.
