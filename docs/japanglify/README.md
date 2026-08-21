# Japanglify instance of Swarm Conductor

This folder is the **project overlay**. Do not put it in the generic conductor.

GitHub **issue** = bug report. GitHub **pull request** = proposed merge. Not a “problem report.”

## Who owns which file (do not duplicate ritual)

| Topic | File that wins | Everyone else |
|---|---|---|
| Grok CLI brain, shutdown/restart | [../swarm-conductor/prompt-bench.md](../swarm-conductor/prompt-bench.md) | Point. Do not invent a second paste |
| Issue intake (cloud Grok) | [../swarm-conductor/prompt.md](../swarm-conductor/prompt.md) | Silent ignore of `/uat` `/kick` |
| Pull-request follow | [../swarm-conductor/prompt-pr.md](../swarm-conductor/prompt-pr.md) | Zero comments on plain issues |
| `/commands` table | [commands.md](commands.md) | admin, saved-replies |
| Owner github.com cheat sheet | [admin.md](admin.md) | Does not replace prompt-bench |
| Fleet effort/model cap (SuperGrok) | [budget.json](budget.json) + [budget.md](../swarm-conductor/budget.md) | Issue `effort:*` is a request |
| Tester APKs / names | [ci-for-humans.md](ci-for-humans.md) | root README |
| Live bugs | [cutover.md](cutover.md) | Effort labels live on the issues |
| Event bus | [../swarm-conductor/events.md](../swarm-conductor/events.md) | kick, ping, loops |
| Host leases + workdirs | [hosts.json](hosts.json) + [paths.md](paths.md) | `swarm-lease.py`, `swarm_paths.py` |

If two files disagree on CLI stop/start, **prompt-bench.md wins.** If they disagree on effective effort/model, **budget.json + swarm_budget.py win.**

| File | What |
|---|---|
| `ci-for-humans.md` | User/admin CI: issues, pull requests, tester APKs |
| `swarm-bench.md` | Local Grok CLI **policy** (capabilities, two install paths) |
| `swarm-bench-kickoff.md` | Long bootstrap. Not the paste if prompt-bench exists |
| `../swarm-conductor/prompt-bench.md` | **Paste** for Grok CLI |
| `budget.json` | SuperGrok window cap (effort + model) |
| `paths.md` | `{home}/src/{owner}/{repo}` — same on every host |
| `hosts.json` | Checked-in host leases (DHCP table + wildcard pools + workdir template) |
| `instance.json` | Repos, Android caps, Japanese-NLP rules |
| `cutover.md` | Live bugs, fork pull requests, smoke record |
| `admin.md` | Owner github.com + when to Restore |
| `../GLOSSARY.md` | Furigana / romaji / mora / Kuromoji (product linguistics) |

Swarm Conductor itself: `docs/swarm-conductor/` (effort convention: `docs/swarm-conductor/kickoffs.md`).
