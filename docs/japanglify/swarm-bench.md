# Swarm Bench

Local starting set for **build + domain test + interactive `adb` + issue/pull-request handoff**. Grok CLI on a home computer (Windows or Unix). Not Swarm Conductor.

The Pixel is on the USB cable. Remote GitHub-hosted assemble is slower and cannot tap the device. **Do not wait on Actions** for this loop.

## Host

Pick the workstation that has:

- JDK + Gradle wrapper
- Android SDK / `adb`
- The Pixel attached (`adb devices`)
- A clone of **electrobrian/japanglify**, branch `agent/*` (or `BETA-2` to branch from)

Windows and Unix (Linux, macOS, FreeBSD Linuxulator) are both valid. The OS is not the role. **Windows 11 + Pixel USB is the usual owner bench.**

```text
cd <electrobrian-japanglify>
adb devices
grok
```

Tell it you are **Swarm Bench**. Point it at `docs/japanglify/cutover.md` if the bug already has a branch.

## Windows 11 owner loop (minimize network)

You can open the pull request from GitHub or from the console (`gh pr create`). Either way, **this machine** builds and UATs. Do not send assemble to `ubuntu-latest` for yourself.

Least traffic (preferred):

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

Do **not** install a GitHub self-hosted runner for this unless you want GitHub to *dispatch* the job. A runner still `git fetch`es and usually uploads artifacts. Grok CLI on the same box is the short path.

| Path | What crosses the network |
|---|---|
| Swarm Bench, UAT, then push | `git push` of the branch |
| Pull request already open, bench continues | `git fetch` / later push |
| `ubuntu-latest` tester APKs | fetch + SDK + three APK uploads |

## Starting capabilities

| May | Must not |
|---|---|
| `:domain:test`, assemble tester/debug APKs locally | `gradle.assemble-release`, real keystore |
| `adb install`, logcat, directed taps you asked for | Merge `main`, publish `/latest` |
| Push `agent/*`, open/update **one** pull request into `BETA-2` | A second pull request for the same bug |
| Comment on the official **issue** (handoff only) | `/accept` (that is the owner, or Conductor recording it) |
| Talk in ordinary English in the CLI | File a new issue for a failed UAT |

Pushing `agent/*` may *also* trigger cloud tester APKs for other people. That is a side effect. Your UAT APK is the one you just `adb install`’d.

## Inner loop

```
build locally → adb install → you try it on the Pixel
     → tell CLI what happened → patch → repeat
          → when you want a handoff: git push (same branch)
```

GitHub **issue** = scoreboard. GitHub **pull request** = the bits. CLI = the work.
