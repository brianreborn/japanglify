# Japanglify cutover map

Project-specific. Swarm Conductor reads this as the **instance work map**, not as conductor spec.

**In-flight fork work is the source of truth until UAT.** Human-facing CI: [ci-for-humans.md](ci-for-humans.md) and the root README section. Owner cheat sheet: [admin.md](admin.md). CLI ritual: [prompt-bench.md](../swarm-conductor/prompt-bench.md).

**Names:** GitHub **issue** = bug report. GitHub **pull request** = proposed merge. Do not write “PR” in user-facing text; it is not a “problem report.”

Owner Pixel UAT is **Swarm Bench**. GitHub `/uat` dispatches the self-hosted Windows runner; Grok CLI on that box is the inner loop **or** silent while Actions owns the phone. See [swarm-bench.md](swarm-bench.md). Commands: [commands.md](commands.md).

## Live (2026-08-21)

| Piece | State |
|---|---|
| `/accept` `/block` | Grok automation `japanglify-swarm-conductor` (only intake). Actions accept.yml **removed** |
| `/uat` | Actions `swarm-conductor-uat.yml` — maps `agent/<issue>-*` (JSON override optional). Owner comment on github.com |
| Ready ping | Watchdog every **10** min: once per issue when that branch exists. **That is the approval cue.** |
| Host ping | `swarm-ping.yml` every **15** min. Observes; does not `/uat` |
| Watchdog quotas / 20 min stall | `swarm-watchdog.yml` — schedule only |
| Config JSON + lease/uat-map self-test | `conductor-config.yml` on `main` |
| Webhook conductor | **retired** |
| Self-hosted `swarm-bench` | one box, `win11-pixel`. Ritual: [prompt-bench.md](../swarm-conductor/prompt-bench.md). Idle stop: `/quit` + `swarm-bench-stop`. Restore: no `--resume`. Effort from the **issue** |

## Repos

| Repo | Role |
|---|---|
| [brianreborn/japanglify](https://github.com/brianreborn/japanglify) | Official issues, `/accept`, `/latest`, real keystore |
| [electrobrian/japanglify](https://github.com/electrobrian/japanglify) | `agent/*` branches, pull requests into `BETA-2`, ephemeral tester APKs |

## Live product bugs

| Official issue | Fork work | Action |
|---|---|---|
| [#5 chip problems](https://github.com/brianreborn/japanglify/issues/5) | [pull request #8](https://github.com/electrobrian/japanglify/pull/8) `agent/5-chip-persistence` | Keep that pull request. Scoreboard = official issue #5. Effort: xhigh |
| [#6 live adjustment](https://github.com/brianreborn/japanglify/issues/6) | none yet | Stay here. New branch only after accept. Effort: xhigh |
| [#7 proper names](https://github.com/brianreborn/japanglify/issues/7) | [issue #9](https://github.com/electrobrian/japanglify/issues/9) + [pull request #12](https://github.com/electrobrian/japanglify/pull/12) | Do not restart pull request #12. Effort: xhigh |

## Fork-only (do not move)

| Item | Why |
|---|---|
| [issue #2](https://github.com/electrobrian/japanglify/issues/2) + [pull request #4](https://github.com/electrobrian/japanglify/pull/4) | Agent chore / handoff dry-run |
| [pull request #10](https://github.com/electrobrian/japanglify/pull/10) | Tester APK download CI |
| [pull request #11](https://github.com/electrobrian/japanglify/pull/11) | Windows dashboard |

## UAT retries (same pull request)

Failed UAT does **not** open a new issue or a second pull request. Comment on the existing development pull request; the next push publishes tester tag `pr-<pull-request-number>-build-(M+1)` (GitHub’s pull-request number, not an issue number). Uninstall the previous tester first (ephemeral key ≠ `/latest`). Re-run **Build test APKs** only when you need the same commit rebuilt. Cap: `testerReleasesPerPr` in [instance.json](instance.json) (currently 5).

## Promotion

```
official issue + /accept
  → Swarm Conductor may propose
  → electrobrian agent/<issue>-* → pull request into BETA-2
      → watchdog: Ready for Pixel UAT on the official issue
          → you `/uat` on github.com (approval)
              → Windows swarm-bench runner adb-installs
                  → if fail: patch on the bench → adb again; push when handing off
                  → if pass: pull request from fork → brianreborn/main
                      → you merge
                          → home official-signer + real keystore
```

Japanese writing-system vocabulary: `docs/GLOSSARY.md`. Do not copy that into conductor.
