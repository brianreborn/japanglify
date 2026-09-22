#!/usr/bin/env python3
"""Exclusive UAT lock on $HOME/$ADBUUID using POSIX lockf (fcntl.lockf).

Windows uses msvcrt.locking on the same path (':' in serials becomes '_'
because NTFS forbids ':' in names). Unix keeps the serial verbatim.

Usage:
  ADBUUID=<serial> adb-uat-lock.py [--timeout SECS] [--status] -- cmd [args...]
"""
from __future__ import annotations

import argparse
import os
import sys
import time


def die(msg: str, code: int = 2) -> None:
    sys.stderr.write("adb-uat-lock: %s\n" % msg)
    raise SystemExit(code)


def lock_path() -> str:
    uuid = os.environ.get("ADBUUID", "").strip()
    if not uuid:
        die("ADBUUID is required (ADB serial / host id)")
    if "/" in uuid or "\\" in uuid or uuid in (".", ".."):
        die("ADBUUID must be a single path segment, got %r" % uuid)
    home = os.environ.get("HOME") or os.path.expanduser("~")
    name = uuid.replace(":", "_") if os.name == "nt" else uuid
    return os.path.join(home, name)


def lock_exclusive(fd: int, timeout: float) -> None:
    deadline = time.monotonic() + timeout if timeout >= 0 else None
    if os.name == "nt":
        import msvcrt

        os.lseek(fd, 0, os.SEEK_SET)
        while True:
            try:
                msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
                return
            except OSError:
                if deadline is not None and time.monotonic() >= deadline:
                    die("timeout waiting for lock %s" % lock_path(), 1)
                time.sleep(0.2)
    import fcntl

    while True:
        try:
            fcntl.lockf(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            return
        except OSError:
            if deadline is not None and time.monotonic() >= deadline:
                die("timeout waiting for lock %s" % lock_path(), 1)
            time.sleep(0.2)


def unlock(fd: int) -> None:
    if os.name == "nt":
        import msvcrt

        os.lseek(fd, 0, os.SEEK_SET)
        try:
            msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
        except OSError:
            pass
    else:
        import fcntl

        try:
            fcntl.lockf(fd, fcntl.LOCK_UN)
        except OSError:
            pass


def _hostname() -> str:
    if hasattr(os, "uname"):
        return os.uname().nodename
    return os.environ.get("COMPUTERNAME", "")


def write_holder(fd: int) -> None:
    body = "pid=%s\nuuid=%s\nhost=%s\n" % (
        os.getpid(),
        os.environ.get("ADBUUID", ""),
        _hostname(),
    )
    os.lseek(fd, 0, os.SEEK_SET)
    os.ftruncate(fd, 0)
    os.write(fd, body.encode("utf-8"))
    os.fsync(fd)


def cmd_status() -> int:
    path = lock_path()
    if not os.path.exists(path):
        sys.stdout.write("free %s (no file)\n" % path)
        return 0
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        body = fh.read().strip() or "(empty)"
    sys.stdout.write("lockfile %s\n%s\n" % (path, body))
    return 0


def cmd_run(argv: list[str], timeout: float) -> int:
    if not argv:
        die("missing command after --")
    path = lock_path()
    flags = os.O_RDWR | os.O_CREAT
    if os.name == "nt":
        flags |= getattr(os, "O_BINARY", 0)
    fd = os.open(path, flags, 0o644)
    lock_exclusive(fd, timeout)
    write_holder(fd)
    env = os.environ.copy()
    env.setdefault("ANDROID_SERIAL", env["ADBUUID"])
    try:
        import subprocess

        return subprocess.call(argv, env=env)
    finally:
        unlock(fd)
        os.close(fd)


def main() -> int:
    p = argparse.ArgumentParser(description="Share ADB UAT hosts via $HOME/$ADBUUID lockf")
    p.add_argument("--timeout", type=float, default=300.0, help="seconds to wait for lock (default 300)")
    p.add_argument("--status", action="store_true", help="print lock file; do not run a command")
    p.add_argument("cmd", nargs=argparse.REMAINDER, help="command after --")
    args = p.parse_args()
    if args.status:
        return cmd_status()
    cmd = list(args.cmd)
    if cmd and cmd[0] == "--":
        cmd = cmd[1:]
    return cmd_run(cmd, args.timeout)


if __name__ == "__main__":
    raise SystemExit(main())
