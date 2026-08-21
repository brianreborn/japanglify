# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

A command counts when it is the **start or end of a line** (token, not a prefix). Mid-line quotes do not fire:

| Counts | Does not |
|---|---|
| `/uat` | comment `` `/uat` `` as the whole comment |
| `please /uat` | see `/uat` in docs |
| `/uat please` | `/uat-map` |

Matcher: `scripts/swarm_cmd.py`. YAML never uses `contains('/uat')`.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — [saved-replies.md](saved-replies.md). github.com.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` (owner) | Install that issue’s `agent/<n>-*` on the Pixel |
| `/clip-shrink` | Actions `swarm-clip.yml` (owner, **also auto**) | Offer a compact copy **unless** already small (≤512 KB) |
| `/clip-ok` | same (owner **or reporter**) | Put that compact file in the original comment |

**New bugs:** you do **not** edit `instance.json`. `/uat` finds `electrobrian` `agent/<issue>-*` (open pull request, else the ref). No branch yet → it says so; do not invent one. Watchdog comments **Ready for Pixel UAT** once when that branch exists — that is your cue to `/uat` on github.com.

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
