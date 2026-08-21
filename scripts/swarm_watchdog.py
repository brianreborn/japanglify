#!/usr/bin/env python3
"""Quota trip + UAT-ready ping + per-issue 20min queue stall. Not intake. Does not install."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UAT_M = "<!-- swarm-bench-uat -->"
READY_M = "<!-- swarm-uat-ready -->"
STALL_M = "<!-- swarm-uat-queued -->"
RUN_URL_RE = re.compile(r"https://github\.com/[\w.-]+/[\w.-]+/actions/runs/\d+")
WAITING = {"queued", "waiting", "pending", "requested"}
STALL_MIN = 20


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def parse_ts(s: str | None) -> datetime | None:
    if not s:
        return None
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def api(method: str, path: str, body: dict | None = None) -> dict | list:
    cmd = ["gh", "api", "-X", method, path]
    if body is None:
        return json.loads(subprocess.check_output(cmd, text=True))
    return json.loads(
        subprocess.check_output(cmd + ["--input", "-"], input=json.dumps(body), text=True)
    )


def mapped(n: int | str) -> dict | None:
    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "uat-map.py"), str(n)],
        text=True,
        capture_output=True,
    )
    raw = (proc.stdout or "").strip()
    if not raw or raw == "{}":
        return None
    return json.loads(raw)


def uat_state(comments: list[dict]) -> dict:
    """Last swarm-bench-uat marker wins. Earlier failed/installed do not stick."""
    last = None
    dispatched_at = None
    run_url = None
    saw_stall = False
    saw_ready = False
    saw_uat = False
    sticky_id = None
    for c in comments:
        body = c.get("body") or ""
        if "<!-- swarm-conductor-status -->" in body:
            sticky_id = c.get("id")
            continue
        if STALL_M in body:
            saw_stall = True
        if READY_M in body:
            saw_ready = True
        if UAT_M in body:
            saw_uat = True
            url_hit = RUN_URL_RE.search(body)
            if "UAT installed" in body:
                last = "installed"
                dispatched_at = None
                run_url = url_hit.group(0) if url_hit else None
            elif "UAT failed" in body:
                last = "failed"
                dispatched_at = None
                run_url = url_hit.group(0) if url_hit else None
            elif "UAT dispatched" in body:
                last = "dispatched"
                dispatched_at = parse_ts(c.get("created_at"))
                run_url = url_hit.group(0) if url_hit else None
    return {
        "last": last,
        "dispatched_at": dispatched_at,
        "run_url": run_url,
        "saw_stall": saw_stall,
        "saw_ready": saw_ready,
        "saw_uat": saw_uat,
        "sticky_id": sticky_id,
    }


def matching_queued(run_url: str | None, dispatched_at: datetime | None, queued: list[dict]) -> dict | None:
    if run_url:
        for r in queued:
            if r.get("html_url") == run_url:
                return r
        return None
    if not dispatched_at:
        return None
    for r in queued:
        created = parse_ts(r.get("created_at"))
        if created and created >= dispatched_at - timedelta(minutes=2):
            return r
    return None


def stall_decision(
    state: dict,
    queued: list[dict],
    now: datetime | None = None,
    stall_min: int = STALL_MIN,
) -> dict | None:
    """Stall only this issue's still-queued dispatch, never a sibling issue's run."""
    now = now or now_utc()
    if state.get("last") != "dispatched" or state.get("saw_stall"):
        return None
    dispatched_at = state.get("dispatched_at")
    if not dispatched_at:
        return None
    age = (now - dispatched_at).total_seconds() / 60.0
    if age < stall_min:
        return None
    hit = matching_queued(state.get("run_url"), dispatched_at, queued)
    if not hit:
        return None
    status = (hit.get("status") or "").lower()
    if status not in WAITING:
        return None
    return {"url": hit.get("html_url") or "", "age_min": int(age)}


def self_test() -> int:
    failed = 0
    now = datetime(2026, 8, 21, 15, 30, tzinfo=timezone.utc)

    def check(name, got, want):
        nonlocal failed
        ok = got == want
        print("ok" if ok else "FAIL", name, "->", got, "want", want)
        failed += not ok

    comments = [
        {
            "created_at": "2026-08-21T14:00:00Z",
            "body": f"{UAT_M}\n**UAT dispatched**\nhttps://github.com/brianreborn/japanglify/actions/runs/1",
        },
        {"created_at": "2026-08-21T14:20:00Z", "body": f"{UAT_M}\n**UAT failed** (`cancelled`)"},
        {
            "created_at": "2026-08-21T14:46:00Z",
            "body": f"{UAT_M}\n**UAT dispatched**\nhttps://github.com/brianreborn/japanglify/actions/runs/42",
        },
    ]
    st = uat_state(comments)
    check("last-wins-dispatched", st["last"], "dispatched")
    check("run-url-from-last", st["run_url"], "https://github.com/brianreborn/japanglify/actions/runs/42")

    queued = [
        {
            "html_url": "https://github.com/brianreborn/japanglify/actions/runs/99",
            "status": "queued",
            "created_at": "2026-08-21T14:50:00Z",
        }
    ]
    # Wrong run URL → no stall even if some other UAT is queued.
    check("wrong-run-no-stall", stall_decision(st, queued, now=now), None)

    queued_hit = [
        {
            "html_url": "https://github.com/brianreborn/japanglify/actions/runs/42",
            "status": "queued",
            "created_at": "2026-08-21T14:46:05Z",
        }
    ]
    d = stall_decision(st, queued_hit, now=now)
    check("re-uat-after-fail-stalls", bool(d and d["url"].endswith("/42")), True)

    failed_last = uat_state(comments[:2])
    check("failed-last-no-stall", stall_decision(failed_last, queued_hit, now=now), None)

    stalled = dict(st)
    stalled["saw_stall"] = True
    check("already-stalled", stall_decision(stalled, queued_hit, now=now), None)

    early = stall_decision(st, queued_hit, now=datetime(2026, 8, 21, 14, 50, tzinfo=timezone.utc))
    check("under-20min", early, None)

    no_url = uat_state(
        [
            {
                "created_at": "2026-08-21T14:46:00Z",
                "body": f"{UAT_M}\n**UAT dispatched** for issue #5",
            }
        ]
    )
    sibling = [
        {
            "html_url": "https://github.com/brianreborn/japanglify/actions/runs/7",
            "status": "queued",
            "created_at": "2026-08-21T13:00:00Z",
        }
    ]
    check("old-queued-not-this-dispatch", stall_decision(no_url, sibling, now=now), None)
    fresh = [
        {
            "html_url": "https://github.com/brianreborn/japanglify/actions/runs/8",
            "status": "queued",
            "created_at": "2026-08-21T14:46:10Z",
        }
    ]
    d2 = stall_decision(no_url, fresh, now=now)
    check("no-url-matches-fresh-queued", bool(d2 and d2["url"].endswith("/8")), True)

    print("swarm_watchdog self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    cfg = json.loads((ROOT / ".github/swarm-conductor.json").read_text(encoding="utf-8"))
    trusted = cfg["trustedActor"]
    heading = cfg["heading"]
    marker = cfg["statusMarker"]
    max_n = int((cfg.get("quotas") or {}).get("commentsPerIssuePerHour", 10))
    repo = os.environ.get("GH_REPO") or "brianreborn/japanglify"
    now = now_utc()
    cutoff = (now - timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    stamp = now.strftime("%Y-%m-%dT%H:%M:%SZ")

    queued_uat = [
        r
        for r in (
            api(
                "GET",
                f"repos/{repo}/actions/workflows/swarm-conductor-uat.yml/runs?status=queued&per_page=20",
            ).get("workflow_runs")
            or []
        )
        if (r.get("status") or "") in WAITING
    ]
    waiting = api(
        "GET",
        f"repos/{repo}/actions/workflows/swarm-conductor-uat.yml/runs?status=waiting&per_page=10",
    ).get("workflow_runs") or []
    queued_uat.extend(waiting)

    issues = api("GET", f"repos/{repo}/issues?state=open&per_page=50")
    for issue in issues:
        if issue.get("pull_request"):
            continue
        n = issue["number"]
        comments = api("GET", f"repos/{repo}/issues/{n}/comments?per_page=100")
        noise = 0
        for c in comments:
            login = (c.get("user") or {}).get("login") or ""
            if login in (trusted, "github-actions[bot]"):
                continue
            if (c.get("created_at") or "") >= cutoff:
                noise += 1
        st = uat_state(comments)
        if noise > max_n:
            text = (
                f"{marker}\n## {heading}\n**BLOCKED** by watchdog at {stamp}\n\n"
                f"Quota commentsPerIssuePerHour exceeded ({noise} non-owner comments in 1h).\n"
                f"No new agent assignment until @{trusted} posts /accept as the whole comment."
            )
            if st.get("sticky_id"):
                api("PATCH", f"repos/{repo}/issues/comments/{st['sticky_id']}", {"body": text})
            else:
                api("POST", f"repos/{repo}/issues/{n}/comments", {"body": text})
            continue
        decision = stall_decision(st, queued_uat, now=now)
        if decision:
            api(
                "POST",
                f"repos/{repo}/issues/{n}/comments",
                {
                    "body": (
                        f"{STALL_M}\n## Swarm Bench\n**UAT still queued** (~{decision['age_min']} min). "
                        f"`Runner.Listener` has not taken [this run]({decision['url']}).\n\n"
                        f"Start `scripts/swarm-kick-watch.ps1` on win11-pixel (Grok CLI types it). "
                        f"This is not a second `/uat`."
                    )
                },
            )
            continue
        if st.get("saw_ready") or st.get("saw_uat"):
            continue
        row = mapped(n)
        if not row:
            continue
        pull = f" pull request `{row['pull']}`" if row.get("pull") else ""
        api(
            "POST",
            f"repos/{repo}/issues/{n}/comments",
            {
                "body": (
                    f"{READY_M}\n## Ready for Pixel UAT\n\n"
                    f"`{row['devRepo']}` `{row['branch']}`{pull} is on the fork.\n\n"
                    f"@{trusted}: to install it on the bench Pixel, comment `/uat` as the **whole comment** on github.com "
                    f"(not the Grok App). That is approval. Nothing installs until you do."
                )
            },
        )
    print("watchdog ok", "queued_uat", len(queued_uat))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
