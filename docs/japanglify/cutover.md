# Japanglify cutover map

Project-specific. Swarm Conductor reads this as the **instance work map**, not as conductor spec.

**In-flight fork work is the source of truth until UAT.** Human-facing CI: [ci-for-humans.md](ci-for-humans.md) and the root README section.

**Names:** GitHub **issue** = bug report. GitHub **pull request** = proposed merge. Do not write “PR” in user-facing text; it is not a “problem report.”

Owner Pixel UAT is **Grok CLI + adb**, not the issue tracker. GitHub is handoff (start / share tester APKs / done).

## Repos

| Repo | Role |
|---|---|
| [brianreborn/japanglify](https://github.com/brianreborn/japanglify) | Official issues, `/accept`, `/latest`, real keystore |
| [electrobrian/japanglify](https://github.com/electrobrian/japanglify) | `agent/*` branches, pull requests into `BETA-2`, ephemeral tester APKs |

## Live product bugs

| Official issue | Fork work | Action |
|---|---|---|
| [#5 chip problems](https://github.com/brianreborn/japanglify/issues/5) | [pull request #8](https://github.com/electrobrian/japanglify/pull/8) `agent/5-chip-persistence` | Keep that pull request. Scoreboard = official issue #5 |
| [#6 live adjustment](https://github.com/brianreborn/japanglify/issues/6) | none yet | Stay here. New branch only after accept |
| [#7 proper names](https://github.com/brianreborn/japanglify/issues/7) | [issue #9](https://github.com/electrobrian/japanglify/issues/9) + [pull request #12](https://github.com/electrobrian/japanglify/pull/12) | Do not restart pull request #12. Domain: Kuromoji `名詞,固有名詞`, no extra names dictionary |

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
  → electrobrian agent/* → pull request into BETA-2
      → tester APKs (ephemeral key)
          → Pixel UAT
              → if fail: comment on that pull request → new commit → new tester APKs (repeat)
              → if pass: pull request from fork → brianreborn/main
                  → you merge
                      → home official-signer + real keystore
```

Japanese writing-system vocabulary: `docs/GLOSSARY.md`. Do not copy that into conductor.
