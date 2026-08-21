#!/usr/bin/env python3
"""Parse /kick [host] — owner backchannel, not UAT and not intake.

Kick is ubuntu-only. It must never occupy [self-hosted, swarm-bench]:
that runner is the thing we are trying to wake.
"""

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
    watch = target in ("all", "win11-pixel")
    return {
        "ok": True,
        "target": target,
        "watch": watch,
        "cloud": True,
        "bench": False,
        "note": "Kick is ubuntu mailbox only. Never occupies swarm-bench.",
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
    p = plan("win11-pixel")
    assert p["watch"] and p["cloud"] and not p["bench"], p
    p = plan("all")
    assert p["watch"] and p["cloud"] and not p["bench"], p
    p = plan("grok-cloud")
    assert (not p["watch"]) and p["cloud"] and not p["bench"], p
    p = plan("nope")
    assert not p["ok"], p
    print("swarm_kick self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    body = sys.stdin.read() if not sys.argv[1:] else " ".join(sys.argv[1:])
    if body.startswith("/kick") or "\n" in body:
        target = parse_target(body)
    else:
        target = body.strip() or "all"
    row = plan(target)
    json.dump(row, sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 0 if row.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
