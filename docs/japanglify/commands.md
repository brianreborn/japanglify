# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — bodies exactly `/accept`, `/block`, `/uat`. On an issue: `/saved-replies` → pick → submit. Whole comment, github.com, as owner.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` | Pixel install (needs `swarm-bench` runner) |

Grok App comments do **not** run `/uat` (Actions). Browser comments as `brianreborn` do. `/accept` is the opposite: the Grok automation is the live gate; there is no Actions `/accept` workflow.

Do not paste a command table onto the issue.
