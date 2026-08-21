# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — whole comment, github.com.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` | Pixel install (needs `swarm-bench` runner) |
| `/clip-shrink` | Actions `swarm-clip.yml` (owner) | mpdecimate the first video on the issue |
| `/clip-ok` | same (owner **or reporter**) | Unlink the original from the thread |

`/clip-ok` does **not** wipe GitHub’s CDN. It deletes or edits the comment so the big file is gone from the issue. The compact file lives on prerelease tag `clip-<issue>` (not `/latest`).

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
