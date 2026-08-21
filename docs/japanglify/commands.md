# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — whole comment, github.com.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` | Pixel install (needs `swarm-bench` runner) |
| `/clip-shrink` | Actions `swarm-clip.yml` (owner) | mpdecimate only if the clip is **over 512 KB** |
| `/clip-ok` | same (owner **or reporter**) | Put the compact clip in the original comment, unlink the big one |

Prefer an **already-small** screen recording on the issue (drag-drop, ~15s, a few hundred KB). Then we do nothing. `/clip-shrink` is the fallback. `/clip-ok` does not wipe GitHub’s CDN.

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
