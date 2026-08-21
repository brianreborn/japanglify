# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

A command counts when it is the **start or end of a line** (token, not a prefix). Mid-line quotes do not fire:

| Counts | Does not |
|---|---|
| `/uat` | comment `` `/uat` `` as the whole comment |
| `please /uat` | see `/uat` in docs |
| `/uat please` | `/uat-map` |

Matcher: `scripts/swarm_cmd.py`. YAML never uses `contains('/uat')`.

Handlers **do not scan** a comment for `/commands` unless that user is allowed to run them. A stranger’s `/uat` never starts Swarm Bench. Those hits only go to the Actions warning log (`swarm-cmd-warn.yml`) — no issue comment.

**Owner github.com:** [admin.md](admin.md). **Owner Grok CLI ritual:** [prompt-bench.md](../swarm-conductor/prompt-bench.md) (normal stop `/quit`, normal start `--resume`, restore = new process + that file). This page is the command table, not the bug form.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — [saved-replies.md](saved-replies.md). github.com.

| Reply | Who may run it | What |
|---|---|---|
| `/accept` | owner | Intake ACCEPTED |
| `/block` | owner | Intake BLOCKED |
| `/uat` | owner | Install that issue’s `agent/<n>-*` on the Pixel. USB missing **holds this issue only** (label `uat-hold`). Runner stays up. `/uat` again when the phone is back. |
| `/kick` | owner | Mailbox: list pending. Does not install |
| `/clip-shrink` | owner (also auto on attach) | Offer a compact copy **unless** already small (≤512 KB) |
| `/clip-ok` | owner **or reporter** | Put that compact file in the original comment |

**New bugs:** you do **not** edit `instance.json`. `/uat` finds `electrobrian` `agent/<issue>-*` (open pull request, else the ref). No branch yet → it says so; do not invent one. Watchdog comments **Ready for Pixel UAT** once when that branch exists — that is your cue to `/uat` on github.com.

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
