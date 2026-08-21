# Swarm Conductor

Generic CI coordinator. A **role is only a starting set of capabilities**.
The conductor dispatches; it does not implement, build, or sign.

This directory has **no product domain**. Japanese linguistics, Android
intents, APKs, and in-flight Japanglify bugs live under `docs/japanglify/`.

**GitHub names (do not invent others):** an **issue** is a bug or request;
a **pull request** is a proposed merge. There is no “problem report” object.
Do not abbreviate pull request as “PR” in user-facing text.

**Swarm Conductor** (this directory) is cloud dispatch. **Swarm Bench** is the
local starting set (build + device + handoff) — instance overlay, not generic
conductor. Bench must not assemble on GitHub-hosted runners.

Kickoffs always state Grok CLI **effort**. See [kickoffs.md](kickoffs.md).

## What is generic

- `/accept` `/block` (whole comment) and issue-body `+1` from a configured trusted actor
- One sticky status comment, edited in place
- Watchdog that can **cancel**, never `assignment.propose`
- Role/cap algebra (`roles.json`)
- Instance file `.github/swarm-conductor.json` (who is trusted, quotas, where the project map is)
- Kickoff **effort** required (`kickoffs.md`)

## What is not

| Concern | Lives in |
|---|---|
| Furigana, romaji, gloss, Kuromoji, 固有名詞 | `docs/GLOSSARY.md` (product) |
| Proper-name UAT, chip bugs, Pixel | `docs/japanglify/cutover.md` |
| Fork vs official, keystore, tester APKs | `docs/japanglify/instance.json` |
| Gradle / adb / `secret.gh-*` | Japanglify capability overlay, not core roles |
| Swarm Bench Grok CLI paste | `docs/japanglify/swarm-bench-kickoff.md` |

To reuse Swarm Conductor on another repo: copy `docs/swarm-conductor/`, the two workflows, and write a new `.github/swarm-conductor.json` plus a project cutover. Do not copy `docs/japanglify/` or `docs/GLOSSARY.md`.
