# Bugs, pull requests, and tester APKs

Japanglify development is coordinated by **Swarm Conductor** (Grok + GitHub Actions). You do not need to know the internals. You only need to know which GitHub object to talk to, and which APK is safe to install.

This page is also a section in the [root README](../../README.md#bugs-pull-requests-and-tester-apks).

## Issues vs pull requests

An **issue** is the bug or request (“the chip vanishes”). A **pull request** is a proposed change plus the tester builds for that change.

| | Issue | Pull request |
|---|---|---|
| What it is | The problem | The fix being tried |
| Where | This repo ([brianreborn/japanglify](https://github.com/brianreborn/japanglify)) | Development fork ([electrobrian/japanglify](https://github.com/electrobrian/japanglify)), into `BETA-2` |
| What you write | The bug. The owner accepts with `/accept` or a 👍 on the **issue body** | What happened when you tried the APK, in ordinary English |
| What not to write | “lgtm”, “go”, or a second copy of the same bug | A new issue for the same failed UAT |
| Merge / `/latest` | Never from an issue comment | Never from a tester APK |

The official issue stays the **scoreboard** (open until the fix ships). The PR is where commits, checks, and APK links appear. One bug → one live PR. A failed test does **not** open a second PR.

## Which APK to install

| Build | Where | Signature | Use it for |
|---|---|---|
| **`/latest`** (`app-release.apk`) | [This repo’s Releases](https://github.com/brianreborn/japanglify/releases/latest) | Real official keystore | Daily use |
| **Tester APKs** | A comment on the development PR (`pr-N-build-M`) | Ephemeral CI key | Trying a fix |

Tester APKs are **not** upgrades of `/latest`. Android will refuse or leave you on a mix of signatures. **Uninstall Japanglify first**, then install the tester. After UAT, uninstall the tester and return to `/latest` (or wait for the next official release).

Each tester comment has three files:

- **Downloadable release** — closest to production, still a tester key (prefer this for UAT)
- **Downloadable debug** — easier logcat
- **Bundled debug** — dictionaries inside; larger; for offline UAT

A new commit on the **same** PR publishes a new tag (`pr-8-build-41`, then `pr-8-build-42`, …). Use the **newest** comment’s links. Older tester builds are obsolete.

## Trying a fix (including a second round)

1. File or find the **issue on this repo**. Watch that issue.
2. When the owner accepts it, a PR shows up on the development fork. The issue will link it.
3. Wait for a **Tester APKs** comment on that PR. Install as above. Reproduce the bug.
4. **If it works** — say so on the PR (and on the issue if you like). The owner merges to official later. That is *not* automatic.
5. **If it does not UAT** — do **not** open a new issue and do **not** `/accept` again. Comment on the **same PR** (what you tapped, what you expected, what happened). The owner can tell the agent to fix forward on that branch.
6. A new commit on that PR **automatically builds a new tester APK set**. Uninstall the previous tester, install the new links, try again. That is round two (and three, …). Several retries per PR are expected; a new PR is not.
7. If you only need the APKs rebuilt from the **same** commit (lost the link, download failed), ask the owner to re-run **Build test APKs** on the PR. That does not require a new issue either.

```
you report a bug          →  official issue
owner /accept             →  one PR + tester APKs
you try it, it fails      →  comment on that PR
agent pushes a fix        →  new tester APKs on the same PR
you try again             →  repeat until UAT passes
owner merges official     →  later /latest (real key)
```

## Owner (admin) notes

- **Issues:** whole-comment `/accept` or `/block`, or 👍 on the issue body. That is the only intake gate. Free-form on an issue does not start work.
- **PRs:** free-form English is the instruction channel. Keep product work on `electrobrian` `agent/*` → `BETA-2`. Swarm Conductor docs may use `chore/*` PRs on this repo.
- **Retries:** same PR, new commit, new `pr-N-build-M`. Do not open a second PR for a failed UAT.
- **Ship:** you merge to `main`; official signed `/latest` is a separate home-machine step. Tester keys never become `/latest`.

More detail: [cutover.md](cutover.md). Swarm Conductor itself (generic, no Japanese/Android): [../swarm-conductor/README.md](../swarm-conductor/README.md).
