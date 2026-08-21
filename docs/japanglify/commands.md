# Issue commands

GitHub’s `/` dropdown is only GitHub helpers (`/table`, `/code`, `/saved-replies`). A repo cannot add `/accept` there.

Saved replies — [github.com/settings/replies](https://github.com/settings/replies) — whole comment, github.com.

| Reply | Who runs it | What |
|---|---|---|
| `/accept` | Grok automation `japanglify-swarm-conductor` | Intake ACCEPTED |
| `/block` | same | Intake BLOCKED |
| `/uat` | Actions `swarm-conductor-uat.yml` | Pixel install (needs `swarm-bench` runner) |
| `/clip-shrink` | Actions `swarm-clip.yml` (owner) | mpdecimate the first video on the issue |
| `/clip-ok` | same (owner **or reporter**) | Put the compact clip in the original comment, unlink the big one |

`/clip-ok` keeps the reporter’s text and swaps the video URL in place. It does **not** wipe GitHub’s CDN; the old URL may still resolve. Compact file is prerelease `clip-<issue>` (not `/latest`). GitHub’s inline player is for their attachment CDN — a release URL may show as a link.

Grok App comments do **not** run `/uat` or `/clip-*`. `/accept` is the Grok automation.
