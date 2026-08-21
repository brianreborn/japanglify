# Bugs, pull requests, and tester APKs

Japanglify development is coordinated by **Swarm Conductor** (Grok + GitHub Actions). You do not need to know the internals. You only need to know which GitHub object to talk to, and which APK is safe to install.

This page is also a section in the [root README](../../README.md#bugs-issues-and-pull-requests).

## Names (read this first)

GitHub has **two** conversation types. They are not nicknames for each other.

| Say | GitHub object | Is not |
|---|---|---|
| **Issue** (or **bug report**) | [Issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/about-issues) | A code change |
| **Pull request** | [Pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests) | A bug report |

There is no GitHub object called a “problem report.” If you mean a bug, file an **issue**.

This project **does not use the letters “PR” in user-facing text**, because they get read as “problem report.” GitHub itself still numbers pull requests (`#8`) and names tester tags `pr-<pull-request-number>-build-<n>` — that `pr-` prefix is GitHub’s pull-request number, not an issue number.

## Issues vs pull requests

An **issue** is the bug or request (“the chip vanishes”). A **pull request** is a proposed change plus the tester builds for that change.

| | Issue (bug report) | Pull request (change) |
|---|---|---|
| What it is | The problem | The fix being tried |
| Where | This repo ([brianreborn/japanglify](https://github.com/brianreborn/japanglify)) | Development fork ([electrobrian/japanglify](https://github.com/electrobrian/japanglify)), into `BETA-2` |
| What you write | The bug. The owner accepts with `/accept` or a 👍 on the **issue body** | What happened when you tried the APK, in ordinary English |
| What not to write | “lgtm”, “go”, or a second copy of the same bug | A new issue for the same failed UAT |
| Merge / `/latest` | Never from an issue comment | Never from a tester APK |

The official issue stays the **scoreboard** (open until the fix ships). The pull request is where commits, checks, and APK links appear. One bug → one live pull request. A failed test does **not** open a second pull request.

Grok CLI **effort** is on the issue: label `effort:low` / `effort:medium` / `effort:high` / `effort:xhigh`, and a first-line `**Effort:**`. Unlabeled → medium. Change the label; do not debate it in chat.

## Owner local UAT — Grok CLI, not the issue tracker

The Pixel is on your desk. Cloud Swarm Conductor cannot `adb` it. For **your** UAT loop, use **Grok CLI** on the workstation that has the phone (and Gradle). Talk in ordinary English: “chip still gone in 1s, try again.”

Do **not** file issues, `/accept`, or GitHub comments for each retry. That is handoff, not the inner loop.

| Loop | Tool | GitHub |
|---|---|---|
| You + Pixel, iterating | Grok CLI + `adb` | Silent until you hand off |
| Other testers / public scoreboard | — | Issue (why) + pull request (bits + tester APKs) |
| Incoming bug from someone else | — | They file an **issue**; you `/accept` once |

Handoff (the only times the issue system should see you):

1. **Start:** `/accept` on the official issue (or you already did).
2. **During:** when you want someone else to try, **push** the same `agent/*` branch so CI publishes a new tester APK. One sentence on the pull request is enough.
3. **Done:** UAT passed or you stopped — comment on the **issue** (scoreboard) and the **pull request**. You still merge official; CLI does not.

Stay on `electrobrian` `agent/*`. CLI-as-owner still does not push `main` or use the real keystore unless you are actually shipping `/latest`.

## Which APK to install

| Build | Where | Signature | Use it for |
|---|---|---|
| **`/latest`** (`app-release.apk`) | [This repo’s Releases](https://github.com/brianreborn/japanglify/releases/latest) | Real official keystore | Daily use |
| **Tester APKs** | A comment on the development pull request (tag `pr-<number>-build-<n>`) | Ephemeral CI key | Trying a fix |

Tester APKs are **not** upgrades of `/latest`. Android will refuse or leave you on a mix of signatures. **Uninstall Japanglify first**, then install the tester. After UAT, uninstall the tester and return to `/latest` (or wait for the next official release).

Each tester comment has three files:

- **Downloadable release** — closest to production, still a tester key (prefer this for UAT)
- **Downloadable debug** — easier logcat
- **Bundled debug** — dictionaries inside; larger; for offline UAT

A new commit on the **same** pull request publishes a new tag (`pr-8-build-41`, then `pr-8-build-42`, …). Use the **newest** comment’s links. Older tester builds are obsolete.

## Trying a fix (including a second round)

1. File or find the **issue on this repo**. Watch that issue.
2. When the owner accepts it, a pull request shows up on the development fork. The issue will link it.
3. Wait for a **Tester APKs** comment on that pull request. Install as above. Reproduce the bug.
4. **If it works** — say so on the pull request (and on the issue if you like). The owner merges to official later. That is *not* automatic.
5. **If it does not UAT** — do **not** open a new issue and do **not** `/accept` again. Comment on the **same pull request** (what you tapped, what you expected, what happened). The owner can tell the agent to fix forward on that branch.
6. A new commit on that pull request **automatically builds a new tester APK set**. Uninstall the previous tester, install the new links, try again. That is round two (and three, …). Several retries per pull request are expected; a new pull request is not.
7. If you only need the APKs rebuilt from the **same** commit (lost the link, download failed), ask the owner to re-run **Build test APKs** on the pull request. That does not require a new issue either.

```
you report a bug          →  official issue
owner /accept             →  one pull request + tester APKs
you try it, it fails      →  comment on that pull request
agent pushes a fix        →  new tester APKs on the same pull request
you try again             →  repeat until UAT passes
owner merges official     →  later /latest (real key)
```

## Owner (admin) notes

- **Issues:** whole-comment `/accept` or `/block`, or 👍 on the issue body. That is the only intake gate. Free-form on an issue does not start work.
- **Effort:** set `effort:*` on the issue when you file or accept it. Bench reads that; do not pick effort in chat.
- **Pull requests:** free-form English is the instruction channel. Keep product work on `electrobrian` `agent/*` → `BETA-2`. Swarm Conductor docs may use `chore/*` pull requests on this repo.
- **Retries:** same pull request, new commit, new `pr-<number>-build-<n>` tag. Do not open a second pull request for a failed UAT.
- **Ship:** you merge to `main`; official signed `/latest` is a separate home-machine step. Tester keys never become `/latest`.
- **Local UAT:** Grok CLI + `adb` on the Pixel workstation. GitHub is handoff only.

More detail: [cutover.md](cutover.md). Swarm Conductor itself (generic, no Japanese/Android): [../swarm-conductor/README.md](../swarm-conductor/README.md).
