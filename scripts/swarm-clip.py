#!/usr/bin/env python3
"""Auto-offer a compact clip when a fat video lands on an issue.

Accepts GitHub attachment URLs and .webm/.mp4/.mov links. Compact file is VP9 WebM.
Already-small (≤ SMALL_KB) is left alone (quiet on auto). /clip-ok splices.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parent))
import swarm_cmd  # noqa: E402

MARKER = "<!-- swarm-clip-compact -->"
SMALL_KB = 512
VIDEO_RE = re.compile(
    r"https://(?:github\.com/user-attachments/assets/[A-Za-z0-9-]+|"
    r"(?:private-)?user-images\.githubusercontent\.com/[^\s)"']+|"
    r"github\.com/[\w.-]+/[\w.-]+/(?:releases/download|raw)/[^\s)"']+\.(?:webm|mp4|mov)|"
    r"[\w.-]+/[^\s)"']+\.webm)",
    re.I,
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
    urls = VIDEO_RE.findall(text or "")
    return [u for u in urls if f"/releases/download/clip-" not in u]


def compact_url(n: str, name: str = "") -> str:
    asset = name or f"clip-{n}.webm"
    return f"https://github.com/{repo()}/releases/download/clip-{n}/{asset}"


def splice(text: str, compact: str) -> str:
    return VIDEO_RE.sub(compact, text or "")


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


def patch_issue(n: str, body: str) -> None:
    gh(
        "-X",
        "PATCH",
        f"repos/{repo()}/issues/{n}",
        "--input",
        "-",
        input_text=json.dumps({"body": body}),
    )


def patch_comment(cid: int, body: str) -> None:
    gh(
        "-X",
        "PATCH",
        f"repos/{repo()}/issues/comments/{cid}",
        "--input",
        "-",
        input_text=json.dumps({"body": body}),
    )


def first_video(n: str) -> tuple[str, str, int | None] | None:
    issue = load_issue(n)
    for url in videos_in(issue.get("body") or ""):
        return ("body", url, None)
    for c in comments(n):
        if MARKER in (c.get("body") or ""):
            continue
        for url in videos_in(c.get("body") or ""):
            return (f"comment:{c['id']}", url, c["id"])
    return None


def already_offered(n: str) -> bool:
    for c in comments(n):
        body = c.get("body") or ""
        if MARKER in body and "Compact clip offered" in body:
            return True
    return False


def cmd_shrink(n: str, *, auto: bool) -> int:
    found = first_video(n)
    if not found:
        if not auto:
            post(n, f"{MARKER}\nNo original video URL found on this issue.")
        return 0
    if auto and already_offered(n):
        print("already offered")
        return 0

    src = Path("clip-raw.bin")
    small = Path(f"clip-{n}.webm")
    where, url, _ = found
    download(url, src)
    raw_kb = src.stat().st_size / 1024
    if raw_kb <= SMALL_KB:
        if not auto:
            post(
                n,
                f"{MARKER}\nAlready looks compact ({raw_kb:.0f} KB ≤ {SMALL_KB} KB) at `{where}`. "
                f"Not transcoding. Leave it.",
            )
        else:
            print(f"already small {raw_kb:.0f} KB")
        return 0
    subprocess.check_call(
        [sys.executable, "scripts/uat-clip.py", "--from", str(src), "-o", str(small)]
    )
    produced = small if small.is_file() else small.with_suffix(".mp4")
    if not produced.is_file():
        raise SystemExit("shrink produced no file")
    small_kb = produced.stat().st_size / 1024
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
            str(produced),
            "--prerelease",
            "--title",
            f"compact clip for issue {n}",
            "--notes",
            "Still-heavy bug video after mpdecimate (WebM VP9, or mp4 fallback). Not an app release. Not /latest.",
        ]
    )
    asset = compact_url(n, produced.name)
    post(
        n,
        f"{MARKER}\n## Compact clip offered\n\n"
        f"Source: `{where}` ({raw_kb:.0f} KB) → [{produced.name}]({asset}) ({small_kb:.0f} KB).\n\n"
        f"If this still shows the bug, comment `/clip-ok` (owner or reporter). "
        f"That puts this file **in the original comment** and unlinks the big one from the thread.",
    )
    return 0


def cmd_ok(n: str, actor: str) -> int:
    c = cfg()
    issue = load_issue(n)
    reporter = (issue.get("user") or {}).get("login") or ""
    allowed = {c["trustedActor"], reporter}
    if actor not in allowed:
        print("ignored actor", actor)
        return 0
    name = f"clip-{n}.webm"
    mp4 = f"clip-{n}.mp4"
    compact = compact_url(n, name)
    for row in comments(n):
        body = row.get("body") or ""
        if MARKER in body and ".mp4" in body and ".webm" not in body:
            compact = compact_url(n, mp4)
            name = mp4
            break
        if MARKER in body and name in body:
            compact = compact_url(n, name)
            break
    swapped = 0
    if issue.get("body") and videos_in(issue["body"]):
        patch_issue(n, splice(issue["body"], compact))
        swapped += 1
    for row in comments(n):
        body = row.get("body") or ""
        if MARKER in body:
            continue
        if not videos_in(body):
            continue
        patch_comment(row["id"], splice(body, compact))
        swapped += 1
    for row in comments(n):
        if MARKER in (row.get("body") or ""):
            gh("-X", "DELETE", f"repos/{repo()}/issues/comments/{row['id']}")
    post(
        n,
        f"{MARKER}\n**Spliced** by @{actor} via /clip-ok ({swapped} original slot(s) → {name}). "
        f"Reporter text stayed.",
    )
    return 0


def main() -> int:
    n = os.environ.get("ISSUE") or (sys.argv[2] if len(sys.argv) > 2 else "")
    op = (sys.argv[1] if len(sys.argv) > 1 else "") or os.environ.get("OP") or ""
    actor = os.environ.get("ACTOR") or ""
    raw = os.environ.get("BODY") or ""
    if not n:
        print("usage: swarm-clip.py shrink|ok <issue>", file=sys.stderr)
        return 2
    if op == "ok" or swarm_cmd.has_command(raw, "/clip-ok"):
        return cmd_ok(n, actor)
    if swarm_cmd.has_command(raw, "/clip-shrink"):
        return cmd_shrink(n, auto=False)
    return cmd_shrink(n, auto=True)


if __name__ == "__main__":
    raise SystemExit(main())
