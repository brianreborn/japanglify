#!/usr/bin/env python3
"""Parse /kick [host] — owner backchannel, not UAT and not intake."""

from __future__ import annotations

import json
import sys

HOSTS = ("all", "win11-pixel", "grok-cloud", "github-actions")


def parse_target(body: str) -> str:
    for line in (body or "").replace("\r", "").split("\n"):
        s = line.strip()
        if s.startswith("`") and s.endswith("`"):
            continue
        if s.startswith("/kick"):
            rest = s[len("/kick") :].strip()
            if not rest:
                return "all"
            token = rest.split()[0]
            return token if token in HOSTS else token
        if s == "/kick" or s.endswith(" /kick"):
            return "all"
    return "all"


def plan(target: str) -> dict:
    if target not in HOSTS:
        return {"ok": False, "target": target, "error": "unknown host"}
    bench = target in ("all", "win11-pixel")
    cloud = target in ("all", "grok-cloud", "github-actions")
    return {
        "ok": True,
        "target": target,
        "bench": bench,
        "cloud": cloud,
        "note": "Kick is a mailbox. Members poll GitHub; no inbound HTTPS on the box.",
    }


def self_test() -> int:
    failed = 0

    def check(body, want):
        nonlocal failed
        got = parse_target(body)
        ok = got == want
        print("ok" if ok else "FAIL", repr(body), "->", got, "want", want)
        failed += not ok

    check("/kick", "all")
    check("/kick all", "all")
    check("/kick win11-pixel", "win11-pixel")
    check("please\n/kick grok-cloud", "grok-cloud")
    check("`/kick`", "all")  # no line command — default all is only if no /kick; quoted skip
    # quoted line skipped, no other /kick → default all
    p = plan("win11-pixel")
    assert p["bench"] and not p["cloud"], p
    p = plan("all")
    assert p["bench"] and p["cloud"], p
    p = plan("nope")
    assert not p["ok"], p
    print("swarm-kick self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    body = sys.stdin.read() if not sys.argv[1:] else " ".join(sys.argv[1:])
    if body.startswith("@"):
        target = body[1:].strip() or "all"
    elif body.startswith("/kick") or "\n" in body:
        target = parse_target(body)
    else:
        target = body.strip() or "all"
    row = plan(target)
    json.dump(row, sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 0 if row.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
