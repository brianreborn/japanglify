# Swarm Conductor

Generic CI coordinator. A **role is only a starting set of capabilities**.
The conductor dispatches; it does not implement, build, or sign.

## Who runs what (do not stack these)

| Command / job | Runner | Identity |
|---|---|---|
| `/accept` `/block` | Grok automation `japanglify-swarm-conductor` | posts as the trusted actor |
| owner free-form on a **pull request** | Grok automation `japanglify-swarm-conductor-pr-follow` | same; **zero comments** on plain issues |
| `/uat` | Actions `swarm-conductor-uat.yml` | `github-actions[bot]` → self-hosted `swarm-bench` |
| `/kick` | Actions `swarm-kick.yml` | wake a member |
| ping (host reliability) | Actions `swarm-ping.yml` every 15 min | observes GitHub; does not enqueue if bench is queued |
| UAT finished | Grok automation `japanglify-uat-complete` | `workflow_run_completed`, APP_ONLY |
| quota / 20min queue stall | Actions `swarm-watchdog.yml` (every 10 min) | `github-actions[bot]` |
| JSON + lease/usage self-test | Actions `conductor-config.yml` | `ubuntu-latest` |
| Swarm Bench **CLI** on SHALOM | Grok CLI; load [prompt-bench.md](prompt-bench.md) | not an automation; `/quit` does not kill the listener |

GitHub is the event bus. Spec: [events.md](events.md). Kick: [kick.md](kick.md). Ping: [ping.md](ping.md).

## Canonical Grok Automation prompts

Every Japanglify Grok automation has a file in this directory. Open **Raw**, copy all, paste into **that** automation only. Do not mix.

| Grok Automation | File | Raw |
|---|---|---|
| `japanglify-swarm-conductor` | [prompt.md](prompt.md) | [raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt.md) |
| `japanglify-swarm-conductor-pr-follow` | [prompt-pr.md](prompt-pr.md) | [raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt-pr.md) |
| `japanglify-swarm-conductor-webhook` (retired) | [prompt-webhook.md](prompt-webhook.md) | [raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt-webhook.md) |
| `japanglify-uat-complete` | [prompt-uat-complete.md](prompt-uat-complete.md) | [raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt-uat-complete.md) |
| Grok CLI Swarm Bench (not an automation) | [prompt-bench.md](prompt-bench.md) | [raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt-bench.md) |

CLI **normal stop** is `/quit` (listener stays). **Normal start** is `grok --effort <issue> --resume`. **Restore** (first logon, or a session you killed because it failed): no `--resume`; paste the raw prompt-bench file.

Personal automations (e.g. Weekly Twitter recap) are **not** in this repo.

There is **no** Actions `/accept` workflow. GitHub’s `/` dropdown cannot hold our commands — use [Saved replies](https://github.com/settings/replies).

`GITHUB_TOKEN` cannot list self-hosted runners (admin API). Do not call it.

Worker **cost** (wall, CPU, optional Grok credits) is a fence on the same state-change comment, not a heartbeat. See [usage.md](usage.md).

This directory has **no product domain**. Japanese linguistics, Android
intents, APKs, and in-flight Japanglify bugs live under `docs/japanglify/`.

**GitHub names:** an **issue** is a bug or request; a **pull request** is a
proposed merge. Do not abbreviate pull request as “PR” in user-facing text.

Kickoffs always state Grok CLI **effort**. See [kickoffs.md](kickoffs.md).

To reuse: copy `docs/swarm-conductor/`, `conductor-config.yml`, `swarm-watchdog.yml`, and write a new `.github/swarm-conductor.json` plus a project overlay. Do not copy `docs/japanglify/` or `docs/GLOSSARY.md`. Instance `/uat` is Japanglify-only (`swarm-conductor-uat.yml`).
