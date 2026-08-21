# Agents

Two starting sets. Do not mix them.

| Role | Where | Does | Must not |
|---|---|---|---|
| **Swarm Conductor** | Grok automation (`/accept` `/block`) + Actions watchdog | Scoreboard, quotas | Gradle, `adb`, keystore, merge `main` |
| **Swarm Bench** | Self-hosted `swarm-bench` runner **or** Grok CLI on the Pixel PC | `/uat` assemble + `adb install`; local iterate | Remote assemble, real keystore, `/latest` |

`/uat` is Actions, not the Grok intake automation. `/accept` is the Grok automation, not Actions.

Kickoffs **always** state Grok CLI effort (`docs/swarm-conductor/kickoffs.md`). Named-bug effort is the issue label `effort:*`.

- Conductor: `docs/swarm-conductor/`
- Bench: `docs/japanglify/swarm-bench.md`
- Instance map: `docs/japanglify/cutover.md`
- Linguistics: `docs/GLOSSARY.md` (not conductor)

GitHub **issue** = bug report. GitHub **pull request** = proposed merge. Not a “problem report.”
