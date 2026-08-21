#!/usr/bin/env python3
"""Usage envelope attached to a Swarm state-change comment."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone

MARKER_OPEN = "<!-- swarm-usage"
MARKER_CLOSE = "-->"
FENCE_RE = re.compile(
    r"<!-- swarm-usage\s*(\{.*?\})\s*-->",
    re.DOTALL,
)


def utcnow() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse(body: str):
    m = FENCE_RE.search(body or "")
    if not m:
        return None
    return json.loads(m.group(1))


def envelope(
    *,
    role: str,
    state: str,
    host=None,
    issue=None,
    effort=None,
    source="self",
    wall_sec=None,
    cpu_sec=None,
    rss_mb=None,
    gh_billable_min=None,
    grok_credits=None,
    at=None,
):
    delta = {}
    pairs = (
        ("wallSec", wall_sec),
        ("cpuSec", cpu_sec),
        ("rssMb", rss_mb),
        ("ghBillableMin", gh_billable_min),
        ("grokCredits", grok_credits),
    )
    for k, v in pairs:
        if v is None:
            continue
        delta[k] = round(float(v), 3)
    row = {
        "schema": 1,
        "at": at or utcnow(),
        "role": role,
        "state": state,
        "source": source,
        "delta": delta,
    }
    if host:
        row["host"] = host
    if issue is not None:
        row["issue"] = int(issue)
    if effort:
        row["effort"] = effort
    return row


def human(row: dict) -> str:
    bits = [f"usage `{row.get('role')}` `{row.get('state')}`"]
    if row.get("host"):
        bits.append(str(row["host"]))
    if row.get("effort"):
        bits.append(f"effort {row['effort']}")
    d = row.get("delta") or {}
    if "wallSec" in d:
        bits.append(f"wall {d['wallSec']}s")
    if "cpuSec" in d:
        bits.append(f"cpu {d['cpuSec']}s")
    if "rssMb" in d:
        bits.append(f"rss {d['rssMb']}MB")
    if "ghBillableMin" in d:
        bits.append(f"gh {d['ghBillableMin']}min")
    if "grokCredits" in d:
        bits.append(f"grok {d['grokCredits']}")
    else:
        bits.append("grok credits unknown")
    return " · ".join(bits)


def render(row: dict) -> str:
    body = json.dumps(row, separators=(",", ":"), ensure_ascii=True)
    return f"{MARKER_OPEN}\n{body}\n{MARKER_CLOSE}\n{human(row)}\n"


def merge(comment: str, row: dict) -> str:
    block = render(row).rstrip() + "\n"
    if FENCE_RE.search(comment or ""):
        return FENCE_RE.sub(lambda _: block.strip(), comment, count=1)
    if comment and not comment.endswith("\n"):
        comment += "\n"
    return (comment or "") + "\n" + block


def self_test() -> int:
    row = envelope(
        role="swarm-bench",
        state="installed",
        host="WIN11",
        issue=5,
        effort="high",
        wall_sec=412.2,
        cpu_sec=88.1,
        grok_credits=None,
        at="2026-08-21T13:20:00Z",
    )
    text = render(row)
    back = parse(text)
    assert back["delta"]["wallSec"] == 412.2, back
    assert "grokCredits" not in back["delta"], back
    assert "unknown" in human(row)
    merged = merge("## Swarm Bench\n**UAT installed**\n", row)
    assert parse(merged)["issue"] == 5
    empty = envelope(role="watchdog", state="ready")
    assert empty["delta"] == {}
    print("swarm-usage self-test ok")
    return 0


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--role", default="swarm-bench")
    p.add_argument("--state", default="installed")
    p.add_argument("--host")
    p.add_argument("--issue", type=int)
    p.add_argument("--effort")
    p.add_argument("--source", default="self")
    p.add_argument("--wall-sec", type=float)
    p.add_argument("--cpu-sec", type=float)
    p.add_argument("--rss-mb", type=float)
    p.add_argument("--gh-billable-min", type=float)
    p.add_argument("--grok-credits", type=float)
    args = p.parse_args()
    if args.self_test:
        return self_test()
    row = envelope(
        role=args.role,
        state=args.state,
        host=args.host,
        issue=args.issue,
        effort=args.effort,
        source=args.source,
        wall_sec=args.wall_sec,
        cpu_sec=args.cpu_sec,
        rss_mb=args.rss_mb,
        gh_billable_min=args.gh_billable_min,
        grok_credits=args.grok_credits,
    )
    sys.stdout.write(render(row))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
