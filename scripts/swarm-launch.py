#!/usr/bin/env python3
"""Launch the per-host worker for one issue step. Default is dry-run (print plan, do not spawn).

Intake is grok-cloud (already the Grok automation). Classify/fix/uat are swarm-bench.
Watchdog is github-actions. Wrong host → NAK, no spawn. Never /uat from this script.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import swarm_lease_api  # filled below if import fails — we load lease helpers inline

HOSTS = ROOT / "docs/japanglify/hosts.json"
ROLES = ROOT / "docs/swarm-conductor/roles.json"
INSTANCE = ROOT / "docs/japanglify/instance.json"

# step → who may run it. spawn false = already a long-running worker (do not start a second).
STEPS = {
    "intake": {
        "role": "swarm-conductor",
        "leaseIds": ["grok-cloud"],
        "spawn": False,
        "process": "grok-automation:japanglify-swarm-conductor",
        "effort": "medium",
        "never": ["/uat", "adb", "gradle", "git.push-agent-branch"],
    },
    "classify": {
        "role": "swarm-bench",
        "leaseIds": [
            "win11-pixel",
            "pool-bench-windows",
            "unix-pixel",
            "pool-bench-unix",
        ],
        "spawn": True,
        "process": "grok --effort {effort} --resume",
        "effortFromIssue": True,
        "never": ["/uat", "/accept", "adb.install"],
    },
    "fix": {
        "role": "swarm-bench",
        "leaseIds": [
            "win11-pixel",
            "pool-bench-windows",
            "unix-pixel",
            "pool-bench-unix",
        ],
        "spawn": True,
        "process": "grok --effort {effort} --resume",
        "effortFromIssue": True,
        "never": ["/uat", "/accept"],
    },
    "uat": {
        "role": "swarm-bench",
        "leaseIds": [
            "win11-pixel",
            "pool-bench-windows",
            "unix-pixel",
            "pool-bench-unix",
        ],
        "spawn": False,
        "process": "actions:swarm-conductor-uat.yml (owner /uat)",
        "effort": None,
        "never": ["grok --effort", "second /uat"],
    },
    "watchdog": {
        "role": "watchdog",
        "leaseIds": ["github-actions"],
        "spawn": False,
        "process": "actions:swarm-watchdog.yml",
        "effort": None,
        "never": ["adb", "gradle", "/accept"],
    },
}


def load_json(p: Path) -> dict:
    return json.loads(p.read_text(encoding="utf-8"))


def lease_by_id(table: dict, lid: str) -> dict | None:
    for row in table.get("leases") or []:
        if row.get("id") == lid:
            return row
    return None


def plan(step: str, *, issue: str, lease_id: str | None, effort: str | None) -> dict:
    spec = STEPS.get(step)
    if not spec:
        return {"status": "nak", "reason": f"unknown step {step}", "steps": list(STEPS)}
    table = load_json(HOSTS)
    inst = load_json(INSTANCE)
    if lease_id:
        lease = lease_by_id(table, lease_id)
        if not lease:
            return {"status": "nak", "reason": f"no lease {lease_id}"}
    else:
        # default host for this step (first allowed id)
        lease = lease_by_id(table, spec["leaseIds"][0])
        lease_id = spec["leaseIds"][0]
    role = lease.get("role")
    if role != spec["role"]:
        return {
            "status": "nak",
            "reason": f"lease {lease_id} role {role} cannot run step {step} (need {spec['role']})",
            "step": step,
            "lease": lease_id,
            "role": role,
        }
    if lease_id not in spec["leaseIds"]:
        return {
            "status": "nak",
            "reason": f"lease {lease_id} not in {spec['leaseIds']} for {step}",
            "step": step,
        }
    eff = effort
    if spec.get("effortFromIssue"):
        mapped = (inst.get("uat") or {}).get("issues") or {}
        # effort is on the GitHub issue; dry-run uses flag or xhigh placeholder from owner policy
        eff = effort or spec.get("effort") or "xhigh"
    elif spec.get("effort"):
        eff = spec["effort"]
    proc = spec["process"].format(effort=eff or "medium")
    return {
        "status": "ack",
        "issue": issue,
        "step": step,
        "lease": lease_id,
        "role": role,
        "spawn": spec["spawn"],
        "process": proc,
        "effort": eff,
        "never": spec["never"],
        "note": "dry-run; this script does not start grok or post /uat",
    }


def self_test() -> int:
    failed = 0

    def check(got: dict, want_status: str, **contains):
        nonlocal failed
        ok = got.get("status") == want_status
        for k, v in contains.items():
            if got.get(k) != v:
                ok = False
        print("ok" if ok else "FAIL", got.get("status"), got.get("step"), got.get("lease"), got.get("reason", got.get("process")))
        failed += not ok

    check(plan("intake", issue="5", lease_id="grok-cloud", effort=None), "ack", role="swarm-conductor", spawn=False)
    check(plan("intake", issue="5", lease_id="win11-pixel", effort=None), "nak")
    check(plan("intake", issue="5", lease_id="github-actions", effort=None), "nak")
    check(plan("classify", issue="5", lease_id="win11-pixel", effort="xhigh"), "ack", role="swarm-bench", spawn=True)
    check(plan("classify", issue="5", lease_id="grok-cloud", effort="xhigh"), "nak")
    check(plan("classify", issue="5", lease_id="github-actions", effort="xhigh"), "nak")
    check(plan("fix", issue="7", lease_id="pool-bench-windows", effort="xhigh"), "ack", spawn=True)
    check(plan("uat", issue="5", lease_id="win11-pixel", effort=None), "ack", spawn=False)
    check(plan("uat", issue="5", lease_id="github-actions", effort=None), "nak")
    check(plan("watchdog", issue="5", lease_id="github-actions", effort=None), "ack", spawn=False)
    check(plan("watchdog", issue="5", lease_id="win11-pixel", effort=None), "nak")
    check(plan("watchdog", issue="5", lease_id="grok-cloud", effort=None), "nak")
    bog = plan("explode", issue="5", lease_id="grok-cloud", effort=None)
    check(bog, "nak")
    return 1 if failed else 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--step", choices=list(STEPS), help="intake|classify|fix|uat|watchdog")
    p.add_argument("--issue", default="")
    p.add_argument("--id", help="lease id (default: canonical host for the step)")
    p.add_argument("--effort", default="")
    p.add_argument("--dry-run", action="store_true", default=True)
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args()
    if args.self_test:
        return self_test()
    if not args.step:
        print("usage: swarm-launch.py --step intake --issue 5 [--id grok-cloud]", file=sys.stderr)
        return 2
    row = plan(args.step, issue=args.issue, lease_id=args.id, effort=args.effort or None)
    print(json.dumps(row, indent=2))
    return 0 if row.get("status") == "ack" else 2


if __name__ == "__main__":
    raise SystemExit(main())
