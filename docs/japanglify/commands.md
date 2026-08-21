# Issue commands

GitHub’s `/` dropdown in a comment is **only** GitHub’s own helpers (`/table`, `/code`, `/saved-replies`, …). A repo **cannot** add `/accept` there.

Put ours in **Saved replies** (that is the dropdown):

1. [github.com/settings/replies](https://github.com/settings/replies)
2. Add three replies, body exactly:
   - `/accept`
   - `/block`
   - `/uat`
3. On an issue, type `/saved-replies`, pick one, submit. Whole comment, github.com, as owner.

| Reply | What |
|---|---|
| `/accept` | Intake: accept the bug |
| `/block` | Stop |
| `/uat` | Swarm Bench: assemble + `adb install` on the Pixel |

Do not paste a command table onto the issue. Grok App comments do not run Actions.
