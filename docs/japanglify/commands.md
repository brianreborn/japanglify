# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — whole comment, github.com.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` | Pixel install (needs `swarm-bench` runner) |
| `/clip-shrink` | Actions `swarm-clip.yml` (owner, **also auto**) | Offer a compact copy **unless** the clip already looks small (≤512 KB) |
| `/clip-ok` | same (owner **or reporter**) | Put that compact file in the original comment |

A fat screen recording on an issue or comment is transcoded automatically. Already-small files are left alone (no extra comment). `/clip-shrink` is a manual retry. `/clip-ok` still requires agreement.

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
