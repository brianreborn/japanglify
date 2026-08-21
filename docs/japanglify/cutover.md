# Japanglify cutover map

Project-specific. Swarm Conductor reads this as the **instance work map**, not as conductor spec.

**In-flight fork work is the source of truth until UAT.** Human-facing CI: [ci-for-humans.md](ci-for-humans.md) and the root README section.

## Repos

| Repo | Role |
|---|---|
| [brianreborn/japanglify](https://github.com/brianreborn/japanglify) | Official issues, `/accept`, `/latest`, real keystore |
| [electrobrian/japanglify](https://github.com/electrobrian/japanglify) | `agent/*` branches, PRs into `BETA-2`, ephemeral tester APKs |

## Live product bugs

| Official issue | Fork work | Action |
|---|---|---|
| [#5 chip problems](https://github.com/brianreborn/japanglify/issues/5) | [PR #8](https://github.com/electrobrian/japanglify/pull/8) `agent/5-chip-persistence` | Keep PR. Scoreboard = official #5 |
| [#6 live adjustment](https://github.com/brianreborn/japanglify/issues/6) | none yet | Stay here. New branch only after accept |
| [#7 proper names](https://github.com/brianreborn/japanglify/issues/7) | [issue #9](https://github.com/electrobrian/japanglify/issues/9) + [PR #12](https://github.com/electrobrian/japanglify/pull/12) | Do not restart PR #12. Domain: Kuromoji `名詞,固有名詞`, no extra names dictionary |

## Fork-only (do not move)

| Item | Why |
|---|---|
| [issue #2](https://github.com/electrobrian/japanglify/issues/2) + [PR #4](https://github.com/electrobrian/japanglify/pull/4) | Agent chore / handoff dry-run |
| [PR #10](https://github.com/electrobrian/japanglify/pull/10) | Tester APK download CI |
| [PR #11](https://github.com/electrobrian/japanglify/pull/11) | Windows dashboard |

## UAT retries (same PR)

Failed UAT does **not** open a new issue or a second PR. Comment on the existing development PR; the next push publishes `pr-N-build-(M+1)`. Uninstall the previous tester first (ephemeral key ≠ `/latest`). Re-run **Build test APKs** only when you need the same commit rebuilt. Cap: `testerReleasesPerPr` in [instance.json](instance.json) (currently 5).

## Promotion

```
official issue + /accept
  → Swarm Conductor may propose
  → electrobrian agent/* → PR into BETA-2
      → tester APKs (ephemeral key)
          → Pixel UAT
              → if fail: comment on that PR → new commit → new tester APKs (repeat)
              → if pass: PR fork → brianreborn/main
                  → you merge
                      → home official-signer + real keystore
```

Japanese writing-system vocabulary: `docs/GLOSSARY.md`. Do not copy that into conductor.
