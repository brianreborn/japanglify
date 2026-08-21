#!/usr/bin/env python3
"""Cloud ping of swarm hosts. Observes GitHub; does not /uat or enqueue if bench is already queued.

An in_progress *workflow run* is NOT a live listener. Ubuntu dispatch makes the
UAT run in_progress while the swarm-bench *job* is still queued. Only a job
labeled swarm-bench with status in_progress means the listener took it.

Runner API status is the idle ping: cancelled USB UAT must not hide an offline
SHALOM-swarm-bench.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from datetime import datetime, timezone

ACTIVE = ("win11-pixel", "github-actions", "grok-cloud")
# Never include swarm-ping.yml — that job is ubuntu and would false-green the bench.
# Kick is ubuntu-only (mailbox); do not treat its runs as bench jobs.
BENCH_WORKFLOWS = ("swarm-conductor-uat.yml",)
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


def swarm_runner_status(runners: list[dict] | None) -> tuple[str, list[str]]:
    """'online' | 'offline' | 'missing' | 'unknown', names."""
    if runners is None:
        return "unknown", []
    hits = []
    for r in runners:
        labels = []
        for x in r.get("labels") or []:
            if isinstance(x, dict):
                labels.append(x.get("name") or "")
            else:
                labels.append(str(x))
        if "swarm-bench" in labels:
            hits.append(r)
    if not hits:
        return "missing", []
    names = [str(r.get("name") or "?") for r in hits]
    if any((r.get("status") or "").lower() == "online" for r in hits):
        return "online", names
    return "offline", names


def classify_win11(bench_jobs: list[dict], runners: list[dict] | None = None) -> dict:
    queued = [j for j in bench_jobs if (j.get("status") or "") in WAITING]
    running = [j for j in bench_jobs if j.get("status") == "in_progress"]
    done = [j for j in bench_jobs if j.get("status") == "completed"]
    rs, names = swarm_runner_status(runners)
    named = (", ".join(names) if names else "swarm-bench")
    if running:
        j = running[0]
        return {
            "id": "win11-pixel",
            "ok": True,
            "state": "in_progress",
            "ageSec": age_sec(j.get("created_at")),
            "note": f"listener took {j.get('workflow')}",
            "queued": len(queued),
            "runner": rs,
        }
    if rs == "online":
        return {
            "id": "win11-pixel",
            "ok": True,
            "state": "online",
            "note": f"{named} idle online" + (f" ({len(queued)} job assigning)" if queued else ""),
            "queued": len(queued),
            "runner": rs,
        }
    if queued:
        oldest = max(age_sec(j.get("created_at")) or 0 for j in queued)
        return {
            "id": "win11-pixel",
            "ok": False,
            "state": "queued",
            "ageSec": oldest,
            "note": f"listener not taking swarm-bench ({rs})",
            "queued": len(queued),
            "runner": rs,
        }
    if rs in {"offline", "missing"}:
        return {
            "id": "win11-pixel",
            "ok": False,
            "state": rs,
            "note": f"{named} is {rs} — start scripts/swarm-bench-runner.ps1 (Grok is not the supervisor)",
            "queued": 0,
            "runner": rs,
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
                "note": "last bench cancelled/skipped — USB UAT paused; runner status unknown",
                "queued": 0,
                "runner": rs,
            }
        ok = conclusion == "success"
        return {
            "id": "win11-pixel",
            "ok": ok,
            "state": conclusion,
            "ageSec": age_sec(j.get("updated_at") or j.get("created_at")),
            "note": "last completed swarm-bench job",
            "queued": 0,
            "runner": rs,
        }
    return {
        "id": "win11-pixel",
        "ok": None,
        "state": "unknown",
        "note": "no recent swarm-bench jobs; no runner status",
        "queued": 0,
        "runner": rs,
    }


def classify_grok_cloud() -> dict:
    return {
        "id": "grok-cloud",
        "ok": None,
        "state": "event-driven",
        "note": "no Actions ping; japanglify-swarm-conductor is GitHub issue_comment",
    }


def snapshot(config_runs: list[dict], bench_jobs: list[dict], runners: list[dict] | None = None) -> dict:
    hosts = [
        classify_win11(bench_jobs, runners),
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
                "id": r.get("id"),
                "status": r.get("status"),
                "conclusion": r.get("conclusion"),
                "created_at": r.get("created_at"),
                "updated_at": r.get("updated_at"),
                "html_url": r.get("html_url"),
                "workflow": workflow,
            }
        )
    return out


def jobs_labeled_bench(jobs: list[dict], workflow: str, run_url: str) -> list[dict]:
    """Keep only the self-hosted swarm-bench job, never ubuntu dispatch/report."""
    out = []
    for j in jobs:
        labels = j.get("labels") or []
        if "swarm-bench" not in labels:
            continue
        out.append(
            {
                "status": j.get("status"),
                "conclusion": j.get("conclusion"),
                "created_at": j.get("created_at"),
                "updated_at": j.get("completed_at") or j.get("started_at") or j.get("created_at"),
                "workflow": workflow,
                "html_url": run_url,
                "runner": j.get("runner_name"),
            }
        )
    return out


def collect_runners(repo: str) -> list[dict] | None:
    try:
        data = gh_api(f"repos/{repo}/actions/runners")
        return data.get("runners") or []
    except subprocess.CalledProcessError as e:
        print(f"gh api runners failed: {e}", file=sys.stderr)
        return None


def collect(repo: str) -> dict:
    config_runs = gh_runs(repo, "conductor-config.yml")
    bench_jobs: list[dict] = []
    for wf in BENCH_WORKFLOWS:
        try:
            runs = gh_runs(repo, wf, limit=5)
        except subprocess.CalledProcessError as e:
            print(f"gh api {wf} failed: {e}", file=sys.stderr)
            continue
        for r in runs:
            rid = r.get("id")
            if not rid:
                continue
            try:
                data = gh_api(f"repos/{repo}/actions/runs/{rid}/jobs")
            except subprocess.CalledProcessError as e:
                print(f"gh api jobs {rid} failed: {e}", file=sys.stderr)
                continue
            bench_jobs.extend(jobs_labeled_bench(data.get("jobs") or [], wf, r.get("html_url") or ""))
    bench_jobs.sort(key=lambda j: j.get("created_at") or "", reverse=True)
    print("bench_jobs", json.dumps(bench_jobs[:8]), file=sys.stderr)
    runners = collect_runners(repo)
    print("runners", json.dumps([{"name": r.get("name"), "status": r.get("status")} for r in (runners or [])]), file=sys.stderr)
    return snapshot(config_runs, bench_jobs, runners)


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
            [{"status": "waiting", "created_at": "2026-08-21T14:46:00Z", "workflow": "swarm-conductor-uat.yml"}]
        ),
        False,
        "queued",
    )
    check(
        "bench-running",
        classify_win11(
            [{"status": "in_progress", "created_at": "2026-08-21T14:46:00Z", "workflow": "swarm-conductor-uat.yml"}]
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
    offline = [{"name": "SHALOM-swarm-bench", "status": "offline", "labels": [{"name": "swarm-bench"}]}]
    online = [{"name": "SHALOM-swarm-bench", "status": "online", "labels": [{"name": "swarm-bench"}]}]
    check(
        "offline-hides-not-behind-usb-cancel",
        classify_win11(
            [{"status": "completed", "conclusion": "cancelled", "created_at": "2026-08-21T15:07:00Z", "workflow": "uat"}],
            offline,
        ),
        False,
        "offline",
    )
    check(
        "online-idle-after-usb-cancel",
        classify_win11(
            [{"status": "completed", "conclusion": "cancelled", "created_at": "2026-08-21T15:07:00Z", "workflow": "uat"}],
            online,
        ),
        True,
        "online",
    )
    check(
        "actions-in-progress-is-up",
        classify_github_actions(
            [{"status": "in_progress", "conclusion": None, "updated_at": "2026-08-21T15:10:00Z"}]
        ),
        True,
        "in_progress",
    )
    # Ubuntu dispatch job must not count as the listener.
    ignored = jobs_labeled_bench(
        [{"labels": ["ubuntu-latest"], "status": "in_progress", "name": "dispatch"}],
        "swarm-conductor-uat.yml",
        "https://example/1",
    )
    assert ignored == [], ignored
    kept = jobs_labeled_bench(
        [{"labels": ["self-hosted", "Windows", "swarm-bench"], "status": "queued", "created_at": "t"}],
        "swarm-conductor-uat.yml",
        "https://example/1",
    )
    assert len(kept) == 1 and kept[0]["status"] == "queued", kept
    assert "swarm-ping.yml" not in BENCH_WORKFLOWS
    assert "swarm-kick.yml" not in BENCH_WORKFLOWS
    paused = snapshot(
        [{"conclusion": "success", "updated_at": "2026-08-21T14:58:00Z", "html_url": "x"}],
        [{"status": "completed", "conclusion": "cancelled", "created_at": "2026-08-21T15:07:00Z", "workflow": "uat"}],
    )
    assert paused["hosts"][0]["ok"] is None
    assert paused["hosts"][1]["ok"] is True
    assert paused["ok"] is True, paused
    paused_offline = snapshot(
        [{"conclusion": "success", "updated_at": "2026-08-21T14:58:00Z", "html_url": "x"}],
        [{"status": "completed", "conclusion": "cancelled", "created_at": "2026-08-21T15:07:00Z", "workflow": "uat"}],
        offline,
    )
    assert paused_offline["hosts"][0]["ok"] is False
    assert paused_offline["ok"] is False, paused_offline
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
    # Offline runner is a fail — USB pause is not a live host.
    down = [h for h in row["hosts"] if h.get("ok") is False]
    return 1 if down else 0


if __name__ == "__main__":
    raise SystemExit(main())
