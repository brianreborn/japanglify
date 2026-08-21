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

A fat screen recording (mp4 / mov / **webm**) is transcoded automatically to compact **VP9 WebM**. Already-small files are left alone. GitHub’s inline player is mp4/mov only; the compact copy is a release link. `/clip-ok` still requires agreement.

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
