# ADB UAT host lock protocol

Multiple workers share a small pool of phones/tablets. Before talking to a
DUT over adb, take an exclusive **lockf** on that host.

## Contract

| | |
|--|--|
| Identity | `ADBUUID` = ADB serial (USB id, or `ip:port`, or `adb-<mdns>` name) |
| Lock file | **`$HOME/$ADBUUID`** |
| Primitive | Unix `lockf(1)` if present, else `fcntl.lockf` (Python) |
| Hold | For the duration of one UAT command; released on process exit |
| Child env | `ANDROID_SERIAL` defaults to `ADBUUID` |

`ADBUUID` must be a single path segment (no `/` or `\`). On Windows, `:` in
wireless serials is stored as `_` in the filename because NTFS forbids `:`.
Unix uses the serial bytes as given.

Do not `rm` the lock file while a holder is alive. `lockf -k` keeps the inode
so waiters stay on the same file.

## Commands

```sh
export ADBUUID=27841130ae1c7ece
scripts/adb-uat-lock.sh --status
scripts/adb-uat-lock.sh --timeout 300 -- adb install -r app-downloadable-debug.apk
scripts/adb-uat-lock.sh -- ./scripts/acceptance-smoke-test.sh
```

Windows (QODESH):

```powershell
$env:ADBUUID = "27841130ae1c7ece"
.\scripts\adb-uat-lock.ps1 --status
.\scripts\adb-uat-lock.ps1 --timeout 300 -- adb -s $env:ADBUUID devices
```

## Pool (this lab)

| Host | Typical `ADBUUID` |
|------|-------------------|
| Note9 USB | `27841130ae1c7ece` |
| Revvl Tab 2 (9185W) | wireless `ip:port` (changes) or `adb-LFDINFDUBE85X45P-…` |
| Pixel 8 GrapheneOS | wireless `ip:port` or `adb-3A170DLJH0048C-…` |

One lock per host. Two workers may run UAT **in parallel** on two different
`ADBUUID`s. They must not share a serial without going through this lock.

## Status file body (best-effort)

After acquire, the locker writes `pid=`, `uuid=`, `host=` so `--status` can
show who is in the middle of a run. The advisory lock, not that text, is
authoritative.
