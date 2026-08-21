# Swarm Bench

Local starting set for **build + domain test + interactive `adb` + issue/pull-request handoff**. Grok CLI on a home computer (Windows or Unix). Not Swarm Conductor.

Remote GitHub-hosted assemble is slower and cannot tap the device. **Do not wait on ubuntu-latest** for this loop.

**CLI brain (paste this, not a chat dump):** [prompt-bench.md](../swarm-conductor/prompt-bench.md) ([raw](https://raw.githubusercontent.com/brianreborn/japanglify/main/docs/swarm-conductor/prompt-bench.md)). If this file disagrees with that one on `/quit` / `--resume` / listener, **prompt-bench wins**. Long bootstrap: [swarm-bench-kickoff.md](swarm-bench-kickoff.md). Host CLI default effort is **medium**.

Lease: `scripts/swarm-lease.py --from github --write` against [hosts.json](hosts.json) (named reservations + wildcard pools).

`/uat` on an official issue is a **GitHub Actions** job: `runs-on: [self-hosted, Windows, swarm-bench]`. That is a different process from Grok CLI. Grok starts the listener **once** per Restore; Grok is not the supervisor:

```powershell
pwsh -File scripts/swarm-bench-runner.ps1
```

Without that listener, `/uat` is a dead click. A queued `/uat` will pick up as soon as the listener is **online** — no need to comment again.

## Shutdown and restart

Canonical table: [prompt-bench.md](../swarm-conductor/prompt-bench.md) (that file wins). Owner cheat sheet only points here: [admin.md](admin.md).

**Null start:** `scripts\swarm-grok.cmd` or `./scripts/swarm-grok` ([budget.md](../swarm-conductor/budget.md)). `--resume` for a healthy session. No `-c`, `-p`.

| Situation | Do |
|---|---|
| **Normal stop** | `/quit`. Stay logged on to Windows. Do not kill `Runner.Listener`. |
| **Normal start** (last session was healthy) | `scripts\swarm-grok.cmd --resume` / `./scripts/swarm-grok --resume` |
| **Restore** (first this logon, or you killed a bad session) | Null start. |
| **Runner offline** on GitHub | Restore. |
| **Log off / reboot** | HKCU Run starts the loop. Grok optional. |

`--resume` / `--continue` after a bad shutdown would replay the failure. Do not `taskkill` the listener as a restart.

## Host

Pick the workstation that has:

- JDK + Gradle wrapper
- Android SDK / `adb`
- The Pixel attached (`adb devices`) when you want phone UAT
- A clone of **electrobrian/japanglify**, branch `agent/*` (or `BETA-2` to branch from)
- GitHub Actions runner labeled `swarm-bench` (for `/uat` only)

Windows and Unix (Linux, macOS, FreeBSD Linuxulator) are both valid. The OS is not the role. **Windows 11 + Pixel USB is the usual owner bench.**

```text
cd %USERPROFILE%\src\electrobrian\japanglify    # Windows
cd $HOME/src/electrobrian/japanglify            # Unix
```

Official clone (Grok start) is `{home}/src/brianreborn/japanglify`. Convention: [paths.md](paths.md). Start: `scripts\swarm-grok.cmd` / `./scripts/swarm-grok`.

Tell it you are **Swarm Bench**. Point it at `docs/japanglify/cutover.md` if the bug already has a branch. USB can be unplugged; the listener must still start.

## Windows 11 owner loop (minimize network)

Two ways to install a debug APK on the Pixel. **Never both at once.**

| Path | When |
|---|---|
| Grok CLI, local `gradlew` + `adb` | You are at the box, iterating. GitHub silent |
| `/uat` on the official issue | You are on github.com; the runner does the same assemble+install |

If a `/uat` job is queued or running, this CLI does not `adb install`.

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
| Run `swarm-bench-runner.ps1` **once** so `/uat` has a listener | Stay in chat to keep the listener alive; `/uat` from this CLI |

Pushing `agent/*` may *also* trigger cloud tester APKs for other people. That is a side effect. Your UAT APK is the one you just `adb install`’d.

## Inner loop

```
build locally → adb install → you try it on the Pixel
     → tell CLI what happened → patch → repeat
          → when you want a handoff: git push (same branch)
```

GitHub **issue** = scoreboard. GitHub **pull request** = the bits. CLI = the work. `/uat` = GitHub asking this same box to install.
