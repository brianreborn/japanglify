#!/usr/bin/env python3
"""Match /commands at the start or end of a line, never mid-line quotes.

Do not scan a body for commands unless the actor is in the allow list for that
command. Unauthorized hits belong in a warning log, not in the handler.
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

COMMANDS = {
    "/accept": ("trusted",),
    "/block": ("trusted",),
    "/uat": ("trusted",),
    "/kick": ("trusted",),
    "/clip-shrink": ("trusted",),
    "/clip-ok": ("trusted", "reporter"),
}


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


def first_command(body: str, cmds: list[str] | None = None) -> str | None:
    for c in cmds or COMMANDS:
        if has_command(body, c):
            return c
    return None


def allowed_for(cmd: str, *, actor: str, trusted: str, reporter: str = "") -> bool:
    who = COMMANDS.get(cmd) or ()
    if "trusted" in who and actor == trusted:
        return True
    if "reporter" in who and reporter and actor == reporter:
        return True
    return False


def authorized_command(
    body: str,
    *,
    actor: str,
    trusted: str,
    reporter: str = "",
    cmds: list[str] | None = None,
) -> str | None:
    """Return the command only if this actor may run it. Does not log."""
    cmd = first_command(body, cmds)
    if not cmd:
        return None
    if allowed_for(cmd, actor=actor, trusted=trusted, reporter=reporter):
        return cmd
    return None


def warn_unauthorized(
    body: str,
    *,
    actor: str,
    trusted: str,
    reporter: str = "",
    issue: str = "",
    url: str = "",
) -> str | None:
    """If a command is present and actor may not run it, print a warning line.

    Writes GITHUB_STEP_SUMMARY when that env is set. Never posts to the issue.
    """
    cmd = first_command(body)
    if not cmd:
        return None
    if allowed_for(cmd, actor=actor, trusted=trusted, reporter=reporter):
        return None
    row = {
        "actor": actor,
        "cmd": cmd,
        "issue": issue,
        "url": url,
    }
    line = f"unauthorized {cmd} from @{actor} on #{issue} {url}".strip()
    print(f"::warning::{line}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"- `{json.dumps(row, separators=(',', ':'))}`\n")
    return line


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
        ("/kick", "/kick", True),
        ("/kick win11-pixel", "/kick", True),
        ("do not `/kick`", "/kick", False),
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
    if authorized_command("/uat", actor="eve", trusted="brianreborn"):
        print("FAIL unauthorized /uat must be None")
        failed += 1
    else:
        print("ok unauthorized /uat skipped")
    if authorized_command("/kick", actor="eve", trusted="brianreborn"):
        print("FAIL unauthorized /kick")
        failed += 1
    else:
        print("ok unauthorized /kick skipped")
    if authorized_command("/kick win11-pixel", actor="brianreborn", trusted="brianreborn") != "/kick":
        print("FAIL owner /kick")
        failed += 1
    else:
        print("ok owner /kick")
    if authorized_command("/clip-ok", actor="eve", trusted="brianreborn", reporter="eve") != "/clip-ok":
        print("FAIL reporter /clip-ok")
        failed += 1
    else:
        print("ok reporter /clip-ok")
    if authorized_command("/uat", actor="eve", trusted="brianreborn", reporter="eve"):
        print("FAIL reporter /uat")
        failed += 1
    else:
        print("ok reporter /uat skipped")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    if "--warn" in sys.argv:
        hit = warn_unauthorized(
            os.environ.get("BODY") or "",
            actor=os.environ.get("ACTOR") or "",
            trusted=os.environ.get("TRUSTED") or "brianreborn",
            reporter=os.environ.get("REPORTER") or "",
            issue=os.environ.get("ISSUE") or "",
            url=os.environ.get("COMMENT_URL") or "",
        )
        return 0
    if len(sys.argv) < 3:
        print("usage: swarm_cmd.py --self-test | --warn | <cmd> <body-file|->", file=sys.stderr)
        return 2
    cmd = sys.argv[1]
    raw = sys.stdin.read() if sys.argv[2] == "-" else open(sys.argv[2], encoding="utf-8").read()
    print("yes" if has_command(raw, cmd) else "no")
    return 0 if has_command(raw, cmd) else 1


if __name__ == "__main__":
    raise SystemExit(main())
