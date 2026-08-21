# Swarm cutover — existing work map

**In-flight fork work is the source of truth until UAT.**

## Repos

| Repo | Role |
|---|---|
| [brianreborn/japanglify](https://github.com/brianreborn/japanglify) | Official issues, `/accept`, `/latest`, real keystore |
| [electrobrian/japanglify](https://github.com/electrobrian/japanglify) | `agent/*` branches, PRs into `BETA-2`, ephemeral tester APKs |

Agents never push to `brianreborn/main`. Official never runs `gradle:assemble-tester`.

## Live product bugs

| Official issue | Fork work | Action |
|---|---|---|
| [#5 chip problems](https://github.com/brianreborn/japanglify/issues/5) | [PR #8](https://github.com/electrobrian/japanglify/pull/8) `agent/5-chip-persistence` | Keep PR. Scoreboard = official #5 |
| [#6 live adjustment](https://github.com/brianreborn/japanglify/issues/6) | none yet | Stay here. New branch only after accept |
| [#7 proper names](https://github.com/brianreborn/japanglify/issues/7) | [issue #9](https://github.com/electrobrian/japanglify/issues/9) + [PR #12](https://github.com/electrobrian/japanglify/pull/12) | Do not restart PR #12 |

## Fork-only (do not move)

| Item | Why |
|---|---|
| [issue #2](https://github.com/electrobrian/japanglify/issues/2) + [PR #4](https://github.com/electrobrian/japanglify/pull/4) | Agent chore / handoff dry-run |
| [PR #10](https://github.com/electrobrian/japanglify/pull/10) | Tester APK download CI |
| [PR #11](https://github.com/electrobrian/japanglify/pull/11) | Windows dashboard |

## Accept skins (same predicate)

Actor **brianreborn**, object **this official issue** (not a review comment):

- Whole-comment `/accept`
- Whole-comment `/block`
- 👍 on the **issue body**

That only marks the issue accepted/blocked. It does not merge, does not sign, does not open a second PR.

## Promotion path (unchanged)

```
official issue + /accept
  → work on electrobrian agent/* → PR into BETA-2
      → tester APKs (ephemeral key)
          → you UAT on Pixel
              → PR fork → brianreborn/main
                  → you merge
                      → home official-signer + real keystore
```
