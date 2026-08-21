#!/usr/bin/env python3
"""Effective Grok effort/model: min(issue request, fleet cap, per-role cap)."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_PATH = Path("docs/japanglify/budget.json")
HOST_DEFAULT_EFFORT = "medium"
ORDER = ("low", "medium", "high", "xhigh")


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_until(s: str | None) -> datetime | None:
    if not s:
        return None
    t = datetime.fromisoformat(s.replace("Z", "+00:00"))
    if t.tzinfo is None:
        t = t.replace(tzinfo=timezone.utc)
    return t


def cap_active(budget: dict, now: datetime | None = None) -> bool:
    cap = budget.get("cap") or {}
    until = parse_until(cap.get("until"))
    if until is None:
        return True
    n = now or datetime.now(timezone.utc)
    if n.tzinfo is None:
        n = n.replace(tzinfo=timezone.utc)
    return n < until


def rank(level: str | None, order: list[str] | tuple[str, ...] = ORDER) -> int | None:
    if not level:
        return None
    try:
        return list(order).index(level)
    except ValueError:
        return None


def clamp(*levels: str | None, order: list[str] | tuple[str, ...] = ORDER) -> str:
    ranks = [rank(x, order) for x in levels if rank(x, order) is not None]
    if not ranks:
        return HOST_DEFAULT_EFFORT
    return list(order)[min(ranks)]


def first_model(*models: str | None) -> str | None:
    for m in models:
        if m:
            return m
    return None


def decide(budget: dict, *, role: str, issue_effort: str | None = None, issue_model: str | None = None, now=None) -> dict:
    order = tuple(budget.get("order") or ORDER)
    cap = budget.get("cap") or {}
    role_cfg = (budget.get("perRole") or {}).get(role) or {}
    fleet_effort = cap.get("effort") if cap_active(budget, now) else None
    fleet_model = cap.get("model") if cap_active(budget, now) else None
    effort = clamp(issue_effort, fleet_effort, role_cfg.get("effort"), order=order)
    model = first_model(role_cfg.get("model"), fleet_model, issue_model)
    return {
        "role": role,
        "effort": effort,
        "model": model,
        "argvEffort": None if effort == HOST_DEFAULT_EFFORT else effort,
        "argvModel": model,
        "clamped": effort != (issue_effort or HOST_DEFAULT_EFFORT),
    }


def argv_flags(row: dict) -> list[str]:
    out = []
    if row.get("argvEffort"):
        out.extend(["--effort", row["argvEffort"]])
    if row.get("argvModel"):
        out.extend(["--model", row["argvModel"]])
    return out


def self_test() -> int:
    budget = {
        "order": list(ORDER),
        "cap": {"effort": "medium", "model": None, "until": None},
        "perRole": {
            "swarm-conductor": {"effort": "low"},
            "swarm-bench": {"effort": None},
        },
    }
    failed = 0

    def check(name, got, **want):
        nonlocal failed
        ok = all(got.get(k) == v for k, v in want.items())
        print("ok" if ok else "FAIL", name, got)
        failed += not ok

    check(
        "xhigh-clamped-to-medium",
        decide(budget, role="swarm-bench", issue_effort="xhigh"),
        effort="medium",
        argvEffort=None,
        clamped=True,
    )
    check(
        "conductor-stays-low",
        decide(budget, role="swarm-conductor", issue_effort="xhigh"),
        effort="low",
        argvEffort="low",
        clamped=True,
    )
    check(
        "unlabeled-is-medium",
        decide(budget, role="swarm-bench", issue_effort=None),
        effort="medium",
        argvEffort=None,
        clamped=False,
    )
    expired = {
        **budget,
        "cap": {"effort": "low", "model": None, "until": "2020-01-01T00:00:00Z"},
    }
    check(
        "until-past-lifts-fleet-cap",
        decide(expired, role="swarm-bench", issue_effort="high"),
        effort="high",
        argvEffort="high",
        clamped=False,
    )
    with_model = {
        **budget,
        "cap": {"effort": "medium", "model": "grok-build", "until": None},
    }
    check(
        "model-cap",
        decide(with_model, role="swarm-bench", issue_effort="medium"),
        model="grok-build",
        argvModel="grok-build",
    )
    assert argv_flags({"argvEffort": None, "argvModel": None}) == []
    assert argv_flags({"argvEffort": "xhigh", "argvModel": None}) == ["--effort", "xhigh"]
    print("swarm_budget self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--budget", type=Path, default=DEFAULT_PATH)
    p.add_argument("--role", required=True)
    p.add_argument("--issue-effort", default=None)
    p.add_argument("--issue-model", default=None)
    p.add_argument("--argv", action="store_true", help="print grok flags only")
    args = p.parse_args()
    budget = load(args.budget)
    row = decide(budget, role=args.role, issue_effort=args.issue_effort, issue_model=args.issue_model)
    if args.argv:
        flags = argv_flags(row)
        if flags:
            print(" ".join(flags))
        return 0
    json.dump(row, sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
