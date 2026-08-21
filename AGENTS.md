# Agents

Two starting sets. Do not mix them.

| Role | Where | Does | Must not |
|---|---|---|---|
| **Swarm Conductor** | GitHub Actions + Grok cloud | `/accept` scoreboard, watchdog | Gradle, `adb`, keystore, merge `main` |
| **Swarm Bench** | Grok CLI on the box with the Pixel (Windows or Unix) | Local build, `:domain:test`, `adb`, one pull request on `electrobrian` | Remote assemble, real keystore, `/latest` |

- Conductor: `docs/swarm-conductor/`
- Bench: `docs/japanglify/swarm-bench.md`
- Instance map: `docs/japanglify/cutover.md`
- Linguistics: `docs/GLOSSARY.md` (not conductor)

GitHub **issue** = bug report. GitHub **pull request** = proposed merge. Not a “problem report.”
