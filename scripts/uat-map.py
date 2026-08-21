#!/usr/bin/env python3
"""Map an official issue number to the electrobrian agent branch / pull request.

instance.json uat.issues is an override. Else: open pull request whose head is
agent/<issue>-*, else body/title mentions brianreborn/japanglify#<issue>, else a matching ref.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path


def instance() -> dict:
    return json.loads(Path("docs/japanglify/instance.json").read_text(encoding="utf-8"))


def gh_json(path: str):
    return json.loads(subprocess.check_output(["gh", "api", path], text=True))


def _row(issue: str, inst: dict, uat: dict, *, branch: str, pull, source: str) -> dict:
    return {
        "issue": str(issue),
        "devRepo": inst["devRepo"],
        "branch": branch,
        "pull": pull,
        "applicationId": uat.get("applicationId") or "com.japanglify.app",
        "source": source,
    }


def resolve(issue: str, *, fetch: bool = True) -> dict | None:
    inst = instance()
    uat = inst.get("uat") or {}
    row = (uat.get("issues") or {}).get(str(issue))
    if row:
        return {
            "issue": str(issue),
            "devRepo": row.get("devRepo") or inst["devRepo"],
            "branch": row["branch"],
            "pull": row.get("pull"),
            "applicationId": uat.get("applicationId") or "com.japanglify.app",
            "source": "instance.json",
        }
    if not fetch:
        return None
    dev = inst["devRepo"]
    official = inst.get("officialRepo") or "brianreborn/japanglify"
    prefix = f"agent/{issue}"
    link_re = re.compile(rf"(?:{re.escape(official)}#{issue}\b)")
    pulls = gh_json(f"repos/{dev}/pulls?state=open&per_page=50")
    hits = []
    linked = []
    for p in pulls:
        if p.get("draft"):
            continue
        head = ((p.get("head") or {}).get("ref")) or ""
        item = _row(issue, inst, uat, branch=head, pull=p["number"], source="pull")
        if head == prefix or head.startswith(prefix + "-"):
            hits.append(item)
            continue
        blob = f"{p.get('title') or ''} {p.get('body') or ''}"
        if link_re.search(blob):
            linked.append(item)
    if hits:
        hits.sort(key=lambda r: -int(r["pull"]))
        return hits[0]
    if linked:
        linked.sort(key=lambda r: -int(r["pull"]))
        linked[0]["source"] = "pull-body"
        return linked[0]
    try:
        refs = gh_json(f"repos/{dev}/git/matching-refs/heads/{prefix}")
    except subprocess.CalledProcessError:
        refs = []
    branches = []
    for r in refs:
        name = (r.get("ref") or "").removeprefix("refs/heads/")
        if name == prefix or name.startswith(prefix + "-"):
            branches.append(name)
    if not branches:
        return None
    branches.sort()
    return _row(issue, inst, uat, branch=branches[0], pull=None, source="ref")


def self_test() -> int:
    got = resolve("5", fetch=False)
    if not got or got["branch"] != "agent/5-chip-persistence":
        print("FAIL instance #5", got)
        return 1
    missing = resolve("6", fetch=False)
    if missing is not None:
        print("FAIL #6 should be unset in JSON", missing)
        return 1
    print("uat-map self-test ok")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    if len(sys.argv) < 2:
        print("usage: uat-map.py <issue>|--self-test", file=sys.stderr)
        return 2
    fetch = os.environ.get("UAT_MAP_FETCH", "1") != "0"
    row = resolve(sys.argv[1], fetch=fetch)
    if row is None:
        print("{}")
        return 1
    print(json.dumps(row))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
