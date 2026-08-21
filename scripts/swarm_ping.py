#!/usr/bin/env python3
"""Cloud ping of swarm hosts. Observes GitHub; does not /uat or enqueue if bench is already queued."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from datetime import datetime, timezone

ACTIVE = ("win11-pixel", "github-actions", "grok-cloud")
# Never include swarm-ping.yml — that job is ubuntu and would false-green the bench.
BENCH_WORKFLOWS = ("swarm-conductor-uat.yml", "swarm-kick.yml")
WAITING = {"queued", "waiting", "pending", "requested"}
IDLE_DONE = {"cancelled", "skipped"}


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def parse_ts(s: str | None) -> datetime | None:
    if not s:
        return None
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def age_sec(ts: str | None, now: datetime | None = None) -> int | None:
    t = parse_ts(ts)
    if not t:
        return None
    return int(((now or now_utc()) - t).total_seconds())


def classify_github_actions(config_runs: list[dict]) -> dict:
    if not config_runs:
        return {"id": "github-actions", "ok": None, "state": "unknown", "note": "no conductor-config runs"}
    last = config_runs[0]
    status = last.get("status") or ""
    if status in WAITING or status == "in_progress":
        return {
            "id": "github-actions",
            "ok": True,
            "state": status,
            "run": last.get("html_url"),
            "ageSec": age_sec(last.get("updated_at") or last.get("created_at")),
            "note": "Swarm Conductor config running",
        }
    ok = last.get("conclusion") == "success"
    return {
        "id": "github-actions",
        "ok": ok,
        "state": last.get("conclusion") or status or "unknown",
        "run": last.get("html_url"),
        "ageSec": age_sec(last.get("updated_at") or last.get("created_at")),
        "note": "last Swarm Conductor config",
    }


def classify_win11(bench_jobs: list[dict]) -> dict:
    queued = [j for j in bench_jobs if (j.get("status") or "") in WAITING]
    running = [j for j in bench_jobs if j.get("status") == "in_progress"]
    done = [j for j in bench_jobs if j.get("status") == "completed"]
    if running:
        j = running[0]
        return {
            "id": "win11-pixel",
            "ok": True,
            "state": "in_progress",
            "ageSec": age_sec(j.get("created_at")),
            "note": f"listener took {j.get('workflow')}",
            "queued": len(queued),
        }
    if queued:
        oldest = max(age_sec(j.get("created_at")) or 0 for j in queued)
        return {
            "id": "win11-pixel",
            "ok": False,
            "state": "queued",
            "ageSec": oldest,
            "note": "listener not taking swarm-bench (do not enqueue another ping job)",
            "queued": len(queued),
        }
    if done:
        j = done[0]
        conclusion = j.get("conclusion") or "completed"
        if conclusion in IDLE_DONE:
            return {
                "id": "win11-pixel",
                "ok": None,
                "state": conclusion,
                "ageSec": age_sec(j.get("updated_at") or j.get("created_at")),
                "note": "last bench cancelled/skipped — USB UAT paused or concurrency; not a live up",
                "queued": 0,
            }
        ok = conclusion == "success"
        return {
            "id": "win11-pixel",
            "ok": ok,
            "state": conclusion,
            "ageSec": age_sec(j.get("updated_at") or j.get("created_at")),
            "note": "last completed swarm-bench job",
            "queued": 0,
        }
    return {
        "id": "win11-pixel",
        "ok": None,
        "state": "unknown",
        "note": "no recent swarm-bench jobs",
        "queued": 0,
    }


def classify_grok_cloud() -> dict:
    return {
        "id": "grok-cloud",
        "ok": None,
        "state": "event-driven",
        "note": "no Actions ping; japanglify-swarm-conductor is GitHub issue_comment",
    }


def snapshot(config_runs: list[dict], bench_jobs: list[dict]) -> dict:
    hosts = [
        classify_win11(bench_jobs),
        classify_github_actions(config_runs),
        classify_grok_cloud(),
    ]
    live = [h for h in hosts if h.get("ok") is True]
    down = [h for h in hosts if h.get("ok") is False]
    return {
        "schema": 1,
        "at": now_utc().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "active": list(ACTIVE),
        "hosts": hosts,
        "ok": len(down) == 0 and any(h.get("ok") is True for h in hosts),
        "summary": f"up {len(live)} / down {len(down)} / unknown {len(hosts) - len(live) - len(down)}",
    }


def gh_api(path: str) -> dict:
    raw = subprocess.check_output(["gh", "api", path], text=True)
    return json.loads(raw)


def gh_runs(repo: str, workflow: str, limit: int = 8) -> list[dict]:
    data = gh_api(f"repos/{repo}/actions/workflows/{workflow}/runs?per_page={limit}")
    out = []
    for r in data.get("workflow_runs") or []:
        out.append(
            {
                "status": r.get("status"),
                "conclusion": r.get("conclusion"),
                "created_at": r.get("created_at"),
                "updated_at": r.get("updated_at"),
                "html_url": r.get("html_url"),
                "workflow": workflow,
            }
        )
    return out


def collect(repo: str) -> dict:
    config_runs = gh_runs(repo, "conductor-config.yml")
    bench_jobs: list[dict] = []
    for wf in BENCH_WORKFLOWS:
        try:
            bench_jobs.extend(gh_runs(repo, wf, limit=8))
        except subprocess.CalledProcessError as e:
            print(f"gh api {wf} failed: {e}", file=sys.stderr)
            continue
    bench_jobs.sort(key=lambda j: j.get("created_at") or "", reverse=True)
    print("bench_jobs", json.dumps(bench_jobs[:8]), file=sys.stderr)
    return snapshot(config_runs, bench_jobs)


def markdown(row: dict) -> str:
    lines = [
        f"## Swarm ping",
        f"{row['summary']} at `{row['at']}`",
        "",
        "| host | ok | state | age | note |",
        "|---|---|---|---|---|",
    ]
    for h in row["hosts"]:
        age = h.get("ageSec")
        age_s = f"{age}s" if age is not None else "—"
        ok = {True: "yes", False: "no"}.get(h.get("ok"), "—")
        lines.append(f"| `{h['id']}` | {ok} | `{h.get('state')}` | {age_s} | {h.get('note') or ''} |")
    lines.append("")
    lines.append("Does not `/uat`. Does not enqueue a bench job if one is already queued.")
    return "\n".join(lines) + "\n"


def self_test() -> int:
    failed = 0

    def check(name, got, want_ok, want_state):
        nonlocal failed
        ok = got.get("ok") is want_ok and got.get("state") == want_state
        print("ok" if ok else "FAIL", name, got)
        failed += not ok

    check(
        "actions-success",
        classify_github_actions([{"conclusion": "success", "updated_at": "2026-08-21T14:58:00Z"}]),
        True,
        "success",
    )
    check(
        "bench-queued",
        classify_win11(
            [{"status": "queued", "created_at": "2026-08-21T14:46:00Z", "workflow": "swarm-conductor-uat.yml"}]
        ),
        False,
        "queued",
    )
    check(
        "bench-waiting",
        classify_win11(
            [{"status": "waiting", "created_at": "2026-08-21T14:46:00Z", "workflow": "swarm-kick.yml"}]
        ),
        False,
        "queued",
    )
    check(
        "bench-running",
        classify_win11(
            [{"status": "in_progress", "created_at": "2026-08-21T14:46:00Z", "workflow": "swarm-kick.yml"}]
        ),
        True,
        "in_progress",
    )
    check(
        "bench-cancelled-is-unknown",
        classify_win11(
            [{"status": "completed", "conclusion": "cancelled", "created_at": "2026-08-21T15:07:00Z", "workflow": "uat"}]
        ),
        None,
        "cancelled",
    )
    check(
        "actions-in-progress-is-up",
        classify_github_actions(
            [{"status": "in_progress", "conclusion": None, "updated_at": "2026-08-21T15:10:00Z"}]
        ),
        True,
        "in_progress",
    )
    assert "swarm-ping.yml" not in BENCH_WORKFLOWS
    paused = snapshot(
        [{"conclusion": "success", "updated_at": "2026-08-21T14:58:00Z", "html_url": "x"}],
        [{"status": "completed", "conclusion": "cancelled", "created_at": "2026-08-21T15:07:00Z", "workflow": "uat"}],
    )
    assert paused["hosts"][0]["ok"] is None
    assert paused["hosts"][1]["ok"] is True
    assert paused["ok"] is True, paused
    row = snapshot(
        [{"conclusion": "success", "updated_at": "2026-08-21T14:58:00Z", "html_url": "x"}],
        [{"status": "queued", "created_at": "2026-08-21T14:46:00Z", "workflow": "uat"}],
    )
    assert row["hosts"][0]["ok"] is False
    assert markdown(row).startswith("## Swarm ping")
    print("swarm_ping self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    repo = os.environ.get("GH_REPO") or "brianreborn/japanglify"
    if "--repo" in sys.argv:
        repo = sys.argv[sys.argv.index("--repo") + 1]
    row = collect(repo)
    json.dump(row, sys.stdout, indent=2)
    sys.stdout.write("\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(markdown(row))
    # Cloud-only ping is still a pass if github-actions is up and bench is paused (cancelled).
    down = [h for h in row["hosts"] if h.get("ok") is False]
    return 1 if down else 0


if __name__ == "__main__":
    raise SystemExit(main())
