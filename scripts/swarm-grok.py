#!/usr/bin/env python3
"""Start Grok CLI (Windows or Unix) with fleet budget flags.

Null start slurps docs/swarm-conductor/prompt-bench.md as the first message.
--resume is a healthy continue (no slurp). Same argv on cmd.exe, PowerShell, sh.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parent
sys.path.insert(0, str(SCRIPTS))

from swarm_budget import DEFAULT_PATH, argv_flags, decide, load  # noqa: E402

PROMPT = ROOT / "docs" / "swarm-conductor" / "prompt-bench.md"
BUDGET = ROOT / "docs" / "japanglify" / "budget.json"


def grok_bin() -> str:
    for name in ("grok", "grok.exe"):
        hit = shutil.which(name)
        if hit:
            return hit
    raise SystemExit("grok not on PATH")


def git_pull() -> None:
    git = shutil.which("git")
    if not git:
        print("warn: git not on PATH; skip pull", file=sys.stderr)
        return
    r = subprocess.run([git, "pull", "origin", "main"], cwd=ROOT)
    if r.returncode != 0:
        print("warn: git pull failed; continuing with this tree", file=sys.stderr)


def build_argv(*, resume: bool, role: str, issue_effort: str | None, issue_model: str | None) -> list[str]:
    path = BUDGET if BUDGET.is_file() else ROOT / DEFAULT_PATH
    budget = load(path)
    row = decide(budget, role=role, issue_effort=issue_effort, issue_model=issue_model)
    args = argv_flags(row)
    if resume:
        args.append("--resume")
    else:
        if not PROMPT.is_file():
            raise SystemExit(f"missing {PROMPT}")
        args.append(PROMPT.read_text(encoding="utf-8"))
    return args


def self_test() -> int:
    failed = 0

    def check(name, cond):
        nonlocal failed
        print("ok" if cond else "FAIL", name)
        failed += not cond

    os.chdir(ROOT)
    args = build_argv(resume=False, role="swarm-bench", issue_effort="xhigh", issue_model=None)
    check("null-start-slurps-role", any("Swarm Bench prompt" in a for a in args))
    check("xhigh-clamped-omits-effort", "--effort" not in args)
    check("no-resume-on-null", "--resume" not in args)
    r = build_argv(resume=True, role="swarm-bench", issue_effort=None, issue_model=None)
    check("resume-flag", r[-1] == "--resume")
    check("resume-no-slurp", not any("Swarm Bench prompt" in a for a in r))
    low = build_argv(resume=True, role="swarm-conductor", issue_effort="xhigh", issue_model=None)
    check("conductor-low", low[:2] == ["--effort", "low"] and low[-1] == "--resume")
    print("swarm-grok self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    os.chdir(ROOT)
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--resume", action="store_true", help="healthy continue; do not slurp the role file")
    p.add_argument("--role", default="swarm-bench")
    p.add_argument("--issue-effort", default=os.environ.get("ISSUE_EFFORT") or None)
    p.add_argument("--issue-model", default=os.environ.get("ISSUE_MODEL") or None)
    p.add_argument("--no-pull", action="store_true")
    p.add_argument("--dry-run", action="store_true", help="print grok argv; do not exec")
    args = p.parse_args()
    if not args.no_pull:
        git_pull()
    extra = build_argv(
        resume=args.resume,
        role=args.role,
        issue_effort=args.issue_effort,
        issue_model=args.issue_model,
    )
    if args.dry_run:
        grok = shutil.which("grok") or shutil.which("grok.exe") or "grok"
        shown = []
        for a in extra:
            shown.append("<prompt-bench.md>" if a.startswith("# Swarm Bench") else a)
        print(" ".join([grok, *shown]))
        return 0
    return subprocess.call([grok_bin(), *extra])


if __name__ == "__main__":
    raise SystemExit(main())
