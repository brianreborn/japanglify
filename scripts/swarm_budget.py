#!/usr/bin/env python3
"""Effective Grok effort/model: issue, fleet cap, per-role, then host env band."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_PATH = Path("docs/japanglify/budget.json")
HOST_DEFAULT_EFFORT = "medium"
ORDER = ("low", "medium", "high", "xhigh")
ENV_MIN = "SWARM_EFFORT_MIN"
ENV_MAX = "SWARM_EFFORT_MAX"


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
        return list(order).index(str(level).strip().lower())
    except ValueError:
        return None


def clamp(*levels: str | None, order: list[str] | tuple[str, ...] = ORDER) -> str:
    ranks = [rank(x, order) for x in levels if rank(x, order) is not None]
    if not ranks:
        return HOST_DEFAULT_EFFORT
    return list(order)[min(ranks)]


def env_level(env: dict, key: str, order: tuple[str, ...] = ORDER) -> str | None:
    raw = (env.get(key) or "").strip().lower()
    if not raw:
        return None
    if rank(raw, order) is None:
        return None
    return raw


def band(effort: str, lo: str | None, hi: str | None, order: tuple[str, ...] = ORDER) -> str:
    """Raise to min, then cut to max. If min > max, max wins (wallet)."""
    r = rank(effort, order)
    if r is None:
        r = rank(HOST_DEFAULT_EFFORT, order) or 0
    lo_i = rank(lo, order)
    hi_i = rank(hi, order)
    if lo_i is not None and hi_i is not None and lo_i > hi_i:
        lo_i = hi_i
    if lo_i is not None:
        r = max(r, lo_i)
    if hi_i is not None:
        r = min(r, hi_i)
    return list(order)[r]


def first_model(*models: str | None) -> str | None:
    for m in models:
        if m:
            return m
    return None


def decide(
    budget: dict,
    *,
    role: str,
    issue_effort: str | None = None,
    issue_model: str | None = None,
    now=None,
    env: dict | None = None,
) -> dict:
    env = env if env is not None else dict(os.environ)
    order = tuple(budget.get("order") or ORDER)
    cap = budget.get("cap") or {}
    role_cfg = (budget.get("perRole") or {}).get(role) or {}
    fleet_effort = cap.get("effort") if cap_active(budget, now) else None
    fleet_model = cap.get("model") if cap_active(budget, now) else None
    lo = env_level(env, ENV_MIN, order)
    hi = env_level(env, ENV_MAX, order)
    effort = clamp(issue_effort, fleet_effort, role_cfg.get("effort"), order=order)
    effort = band(effort, lo, hi, order)
    model = first_model(role_cfg.get("model"), fleet_model, issue_model, env.get("SWARM_MODEL") or None)
    requested = issue_effort or HOST_DEFAULT_EFFORT
    return {
        "role": role,
        "effort": effort,
        "model": model,
        "argvEffort": None if effort == HOST_DEFAULT_EFFORT else effort,
        "argvModel": model,
        "clamped": effort != requested,
        "envMin": lo,
        "envMax": hi,
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
    empty = {}
    failed = 0

    def check(name, got, **want):
        nonlocal failed
        ok = all(got.get(k) == v for k, v in want.items())
        print("ok" if ok else "FAIL", name, got)
        failed += not ok

    check(
        "xhigh-clamped-to-medium",
        decide(budget, role="swarm-bench", issue_effort="xhigh", env=empty),
        effort="medium",
        argvEffort=None,
        clamped=True,
    )
    check(
        "conductor-stays-low",
        decide(budget, role="swarm-conductor", issue_effort="xhigh", env=empty),
        effort="low",
        argvEffort="low",
        clamped=True,
    )
    check(
        "unlabeled-is-medium",
        decide(budget, role="swarm-bench", issue_effort=None, env=empty),
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
        decide(expired, role="swarm-bench", issue_effort="high", env=empty),
        effort="high",
        argvEffort="high",
        clamped=False,
    )
    check(
        "env-max-cuts-xhigh",
        decide(expired, role="swarm-bench", issue_effort="xhigh", env={ENV_MAX: "medium"}),
        effort="medium",
        envMax="medium",
    )
    check(
        "env-min-raises-low",
        decide(expired, role="swarm-bench", issue_effort="low", env={ENV_MIN: "high"}),
        effort="high",
        envMin="high",
    )
    check(
        "env-min-gt-max-uses-max",
        decide(expired, role="swarm-bench", issue_effort="low", env={ENV_MIN: "xhigh", ENV_MAX: "medium"}),
        effort="medium",
    )
    check(
        "env-max-cuts-conductor-low-stays-low",
        decide(budget, role="swarm-conductor", issue_effort="xhigh", env={ENV_MAX: "high"}),
        effort="low",
    )
    with_model = {
        **budget,
        "cap": {"effort": "medium", "model": "grok-build", "until": None},
    }
    check(
        "model-cap",
        decide(with_model, role="swarm-bench", issue_effort="medium", env=empty),
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
