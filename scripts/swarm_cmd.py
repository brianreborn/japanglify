#!/usr/bin/env python3
"""Match /commands at the start or end of a line, never mid-line quotes."""

from __future__ import annotations

import re
import sys


def has_command(body: str, cmd: str) -> bool:
    if not cmd.startswith("/"):
        raise ValueError(cmd)
    start = re.compile(rf"^{re.escape(cmd)}(?:\s|$)")
    end = re.compile(rf"(?:^|\s){re.escape(cmd)}$")
    for line in (body or "").replace("\r", "").split("\n"):
        s = line.strip()
        if not s:
            continue
        if s.startswith("`") and s.endswith("`"):
            continue
        if start.match(s) or end.search(s):
            return True
    return False


def first_command(body: str, cmds: list[str]) -> str | None:
    for c in cmds:
        if has_command(body, c):
            return c
    return None


def self_test() -> int:
    cases = [
        ("/uat", "/uat", True),
        ("/uat\n", "/uat", True),
        ("\n/uat\n", "/uat", True),
        ("  /uat  ", "/uat", True),
        ("/uat please", "/uat", True),
        ("please /uat", "/uat", True),
        ("please\n/uat", "/uat", True),
        ("comment `/uat` as the whole comment", "/uat", False),
        ("no second `/uat`.", "/uat", False),
        ("foo `/uat`", "/uat", False),
        ("`/uat`", "/uat", False),
        ("/uat-map", "/uat", False),
        ("see /uat in docs", "/uat", False),
        ("please /uat now", "/uat", False),
        ("/clip-ok", "/clip-ok", True),
        ("If this still shows the bug, comment `/clip-ok` (owner or reporter).", "/clip-ok", False),
        ("/accept", "/accept", True),
        ("do not `/accept`", "/accept", False),
    ]
    failed = 0
    for body, cmd, want in cases:
        got = has_command(body, cmd)
        ok = got is want
        print("ok" if ok else "FAIL", repr(body), cmd, "->", got, "want", want)
        failed += not ok
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    if len(sys.argv) < 3:
        print("usage: swarm_cmd.py --self-test | <cmd> <body-file|->", file=sys.stderr)
        return 2
    cmd = sys.argv[1]
    raw = sys.stdin.read() if sys.argv[2] == "-" else open(sys.argv[2], encoding="utf-8").read()
    print("yes" if has_command(raw, cmd) else "no")
    return 0 if has_command(raw, cmd) else 1


if __name__ == "__main__":
    raise SystemExit(main())
