# Swarm Bench

Local starting set for **build + domain test + interactive `adb` + issue/pull-request handoff**. Grok CLI on a home computer (Windows or Unix). Not Swarm Conductor.

The Pixel is on the USB cable. Remote GitHub-hosted assemble is slower and cannot tap the device. **Do not wait on Actions** for this loop.

## Host

Pick the workstation that has:

- JDK + Gradle wrapper
- Android SDK / `adb`
- The Pixel attached (`adb devices`)
- A clone of **electrobrian/japanglify**, branch `agent/*` (or `BETA-2` to branch from)

Windows and Unix (Linux, macOS, FreeBSD Linuxulator) are both valid. The OS is not the role.

```text
cd <electrobrian-japanglify>
adb devices
grok
```

Tell it you are **Swarm Bench**. Point it at `docs/japanglify/cutover.md` if the bug already has a branch.

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
