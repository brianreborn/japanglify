# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — whole comment, github.com.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` | Pixel install (needs `swarm-bench` runner) |

Clips: drag an **already-small** screen recording onto the issue (~15s, a few hundred KB). We do not transcode reporter videos. If it is huge, re-record smaller.

Grok App comments do **not** run `/uat`. `/accept` is the Grok automation.
