# Swarm Bench

Local starting set for **build + domain test + interactive `adb` + issue/pull-request handoff**. Grok CLI on a home computer (Windows or Unix). Not Swarm Conductor.

The Pixel is on the USB cable. Remote GitHub-hosted assemble is slower and cannot tap the device. **Do not wait on ubuntu-latest** for this loop.

Paste: [swarm-bench-kickoff.md](swarm-bench-kickoff.md). **Effort: medium** (`grok --effort medium`). Do not leave the CLI default `high`.

Lease: `scripts/swarm-lease.py --from github --write` against [hosts.json](hosts.json) (named reservations + wildcard pools).

`/uat` on an official issue is a **GitHub Actions** job: `runs-on: [self-hosted, Windows, swarm-bench]`. That is a different process from Grok CLI. Install and leave it running:

```powershell
pwsh -File scripts/swarm-bench-runner.ps1
```

Without that service, `/uat` is a dead click. A queued `/uat` will pick up as soon as the service starts — no need to comment again.

## Host

Pick the workstation that has:

- JDK + Gradle wrapper
- Android SDK / `adb`
- The Pixel attached (`adb devices`)
- A clone of **electrobrian/japanglify**, branch `agent/*` (or `BETA-2` to branch from)
- GitHub Actions runner labeled `swarm-bench` (for `/uat` only)

Windows and Unix (Linux, macOS, FreeBSD Linuxulator) are both valid. The OS is not the role. **Windows 11 + Pixel USB is the usual owner bench.**

```text
cd <electrobrian-japanglify>
adb devices
grok --effort medium
```

Tell it you are **Swarm Bench**. Point it at `docs/japanglify/cutover.md` if the bug already has a branch.

## Windows 11 owner loop (minimize network)

Two ways to install a debug APK on the Pixel:

| Path | When |
|---|---|
| Grok CLI, local `gradlew` + `adb` | You are at the box, iterating |
| `/uat` on the official issue | You are on github.com; the runner does the same assemble+install |

You can open the pull request from GitHub or from the console (`gh pr create`). Either way, **this machine** builds and UATs. Do not send assemble to `ubuntu-latest` for yourself.

Least traffic (preferred when you are already in the CLI):

```
local edit → ./gradlew → adb install → you try it on the Pixel
     → repeat until UAT passes
          → git push agent/*
               → then open/update the one pull request into BETA-2
```

Network until handoff: none that matters (Gradle cache and SDK already on disk; USB is not the internet). The pull request is how *other people* get tester APKs. You already have the APK from `adb`.

If the pull request **already exists** (opened from the site or `gh`):

```text
gh pr checkout <number>
# same local build + adb; do not wait for “Build test APKs”
```

Put `[skip ci]` on the commit message while you are the only tester, so electrobrian’s `test-apks.yml` does not upload three APKs you will not install. Drop `[skip ci]` when you want those links for someone else.

| Path | What crosses the network |
|---|---|
| Swarm Bench, UAT, then push | `git push` of the branch |
| `/uat` via Actions runner | `git clone` of the agent branch + comment |
| `ubuntu-latest` tester APKs | fetch + SDK + three APK uploads |

## Starting capabilities

| May | Must not |
|---|---|
| `:domain:test`, assemble tester/debug APKs locally | `gradle.assemble-release`, real keystore |
| `adb install`, logcat, directed taps you asked for | Merge `main`, publish `/latest` |
| Push `agent/*`, open/update **one** pull request into `BETA-2` | A second pull request for the same bug |
| Comment on the official **issue** (handoff only) | `/accept` (that is the owner, or Conductor recording it) |
| Talk in ordinary English in the CLI | File a new issue for a failed UAT |
| Run `swarm-bench-runner.ps1` so `/uat` has a listener | Register the runner on a machine without the Pixel |

Pushing `agent/*` may *also* trigger cloud tester APKs for other people. That is a side effect. Your UAT APK is the one you just `adb install`’d.

## Inner loop

```
build locally → adb install → you try it on the Pixel
     → tell CLI what happened → patch → repeat
          → when you want a handoff: git push (same branch)
```

GitHub **issue** = scoreboard. GitHub **pull request** = the bits. CLI = the work. `/uat` = GitHub asking this same box to install.
