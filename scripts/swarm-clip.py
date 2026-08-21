#!/usr/bin/env python3
"""Shrink a still-heavy issue video, then remove the original from the thread after /clip-ok.

GitHub does not let us delete the CDN blob. /clip-ok only unlinks it from the issue
(delete or edit the comment / issue body). The compact copy is a prerelease tag clip-<n>.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.request import Request, urlopen

MARKER = "<!-- swarm-clip-compact -->"
VIDEO_RE = re.compile(
    r"https://(?:github\.com/user-attachments/assets/[A-Za-z0-9-]+|"
    r"(?:private-)?user-images\.githubusercontent\.com/[^\s)"']+)"
)


def gh(*args: str, input_text: str | None = None) -> str:
    cmd = ["gh", "api", *args]
    return subprocess.check_output(
        cmd, text=True, input=input_text, stderr=subprocess.STDOUT
    )


def cfg() -> dict:
    return json.loads(Path(".github/swarm-conductor.json").read_text(encoding="utf-8"))


def repo() -> str:
    return os.environ["GH_REPO"]


def load_issue(n: str) -> dict:
    return json.loads(gh(f"repos/{repo()}/issues/{n}"))


def comments(n: str) -> list[dict]:
    return json.loads(gh(f"repos/{repo()}/issues/{n}/comments", "--paginate"))


def videos_in(text: str) -> list[str]:
    return VIDEO_RE.findall(text or "")


def download(url: str, dest: Path) -> None:
    token = os.environ["GH_TOKEN"]
    req = Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/octet-stream",
            "User-Agent": "japanglify-swarm-clip",
        },
    )
    with urlopen(req, timeout=60) as resp, dest.open("wb") as f:
        f.write(resp.read())


def post(n: str, body: str) -> None:
    gh(
        "-X",
        "POST",
        f"repos/{repo()}/issues/{n}/comments",
        "--input",
        "-",
        input_text=json.dumps({"body": body}),
    )


def cmd_shrink(n: str) -> int:
    issue = load_issue(n)
    found: list[tuple[str, str, int | None]] = []
    for url in videos_in(issue.get("body") or ""):
        found.append(("body", url, None))
    for c in comments(n):
        if MARKER in (c.get("body") or ""):
            continue
        for url in videos_in(c.get("body") or ""):
            found.append((f"comment:{c['id']}", url, c["id"]))
    if not found:
        post(n, f"{MARKER}\nNo original video URL found on this issue.")
        return 0

    src = Path("clip-raw.bin")
    small = Path(f"clip-{n}.mp4")
    where, url, _ = found[0]
    download(url, src)
    raw_kb = src.stat().st_size / 1024
    subprocess.check_call(
        [sys.executable, "scripts/uat-clip.py", "--from", str(src), "-o", str(small)]
    )
    if not small.is_file():
        raise SystemExit("shrink produced no file")
    small_kb = small.stat().st_size / 1024
    tag = f"clip-{n}"
    subprocess.call(
        ["gh", "release", "delete", tag, "--yes", "--cleanup-tag"],
        stderr=subprocess.DEVNULL,
    )
    subprocess.check_call(
        [
            "gh",
            "release",
            "create",
            tag,
            str(small),
            "--prerelease",
            "--title",
            f"compact clip for issue {n}",
            "--notes",
            "Still-heavy bug video after mpdecimate. Not an app release. Not /latest.",
        ]
    )
    asset = f"https://github.com/{repo()}/releases/download/{tag}/{small.name}"
    post(
        n,
        f"{MARKER}\n## Compact clip\n\n"
        f"Source: `{where}` ({raw_kb:.0f} KB) → [{small.name}]({asset}) ({small_kb:.0f} KB).\n\n"
        f"If this still shows the bug, comment `/clip-ok` (owner or reporter). "
        f"That **unlinks** the original from this thread. GitHub may keep the old CDN URL until they purge it.",
    )
    return 0


def strip_urls(text: str) -> str:
    return VIDEO_RE.sub("(original video removed after /clip-ok)", text or "")


def cmd_ok(n: str, actor: str) -> int:
    c = cfg()
    issue = load_issue(n)
    reporter = (issue.get("user") or {}).get("login") or ""
    allowed = {c["trustedActor"], reporter}
    if actor not in allowed:
        print("ignored actor", actor)
        return 0
    if issue.get("body") and videos_in(issue["body"]):
        new_body = strip_urls(issue["body"])
        gh(
            "-X",
            "PATCH",
            f"repos/{repo()}/issues/{n}",
            "--input",
            "-",
            input_text=json.dumps({"body": new_body}),
        )
    for row in comments(n):
        body = row.get("body") or ""
        if MARKER in body:
            continue
        if not videos_in(body):
            continue
        gh("-X", "DELETE", f"repos/{repo()}/issues/comments/{row['id']}")
    post(
        n,
        f"{MARKER}\n**Original unlinked** by @{actor} via /clip-ok. Compact clip is the `clip-{n}` prerelease. "
        f"The old URL may still resolve on GitHub's CDN.",
    )
    return 0


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: swarm-clip.py shrink|ok <issue>", file=sys.stderr)
        return 2
    op, n = sys.argv[1], sys.argv[2]
    actor = os.environ.get("ACTOR") or ""
    if op == "shrink":
        return cmd_shrink(n)
    if op == "ok":
        return cmd_ok(n, actor)
    raise SystemExit(f"unknown {op}")


if __name__ == "__main__":
    raise SystemExit(main())
