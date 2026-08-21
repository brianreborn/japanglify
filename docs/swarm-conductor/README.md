# Swarm Conductor

Generic CI coordinator. A **role is only a starting set of capabilities**.
The conductor dispatches; it does not implement, build, or sign.

## Who runs what (do not stack these)

| Command / job | Runner | Identity |
|---|---|---|
| `/accept` `/block` | Grok automation `japanglify-swarm-conductor` | posts as the trusted actor |
| `/uat` | Actions `swarm-conductor-uat.yml` | `github-actions[bot]` → self-hosted `swarm-bench` |
| quota trip | Actions `swarm-watchdog.yml` (schedule) | `github-actions[bot]` |
| JSON + lease/usage self-test | Actions `conductor-config.yml` | `ubuntu-latest` |

There is **no** Actions `/accept` workflow. GitHub’s `/` dropdown cannot hold our commands — use [Saved replies](https://github.com/settings/replies).

`GITHUB_TOKEN` cannot list self-hosted runners (admin API). Do not call it.

Worker **cost** (wall, CPU, optional Grok credits) is a fence on the same state-change comment, not a heartbeat. See [usage.md](usage.md).

This directory has **no product domain**. Japanese linguistics, Android
intents, APKs, and in-flight Japanglify bugs live under `docs/japanglify/`.

**GitHub names:** an **issue** is a bug or request; a **pull request** is a
proposed merge. Do not abbreviate pull request as “PR” in user-facing text.

Kickoffs always state Grok CLI **effort**. See [kickoffs.md](kickoffs.md).

To reuse: copy `docs/swarm-conductor/`, `conductor-config.yml`, `swarm-watchdog.yml`, and write a new `.github/swarm-conductor.json` plus a project overlay. Do not copy `docs/japanglify/` or `docs/GLOSSARY.md`. Instance `/uat` is Japanglify-only (`swarm-conductor-uat.yml`).
