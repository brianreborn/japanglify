#!/usr/bin/env python3
"""Record a short Pixel UAT clip and shrink still-heavy footage.

Input: mp4 / mov / webm (whatever ffmpeg reads). Compact output defaults to
VP9 WebM — smaller on still UI than CBR H.264. GitHub's inline player is
mp4/mov only; compact files live on a release link anyway.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

PHONE = "/sdcard/Download/japanglify-uat.mp4"


def kb(p: Path) -> str:
    return "%.1f KB" % (p.stat().st_size / 1024)


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    print("+", " ".join(cmd), file=sys.stderr)
    return subprocess.run(cmd, check=True, **kw)


def record(seconds: int, dest: Path) -> Path:
    adb = shutil.which("adb")
    if not adb:
        raise SystemExit("adb not on PATH")
    run([adb, "shell", "rm", "-f", PHONE])
    run(
        [
            adb,
            "shell",
            "screenrecord",
            "--size",
            "720x1600",
            "--bit-rate",
            "400000",
            "--time-limit",
            str(seconds),
            PHONE,
        ]
    )
    dest.parent.mkdir(parents=True, exist_ok=True)
    run([adb, "pull", PHONE, str(dest)])
    run([adb, "shell", "rm", "-f", PHONE])
    return dest


def _vf() -> str:
    return "mpdecimate,fps=6,scale=720:-2"


def shrink(src: Path, dest: Path) -> Path:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("ffmpeg not on PATH; keeping record as-is", file=sys.stderr)
        if src.resolve() != dest.resolve():
            dest.write_bytes(src.read_bytes())
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    webm = dest.suffix.lower() != ".mp4"
    if webm and dest.suffix.lower() != ".webm":
        dest = dest.with_suffix(".webm")
    if webm:
        cmd = [
            ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(src), "-an", "-vf", _vf(),
            "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "36",
            "-cpu-used", "4", "-row-mt", "1", "-deadline", "good",
            "-pix_fmt", "yuv420p", str(dest),
        ]
        try:
            run(cmd)
            return dest
        except subprocess.CalledProcessError:
            print("VP9 failed; falling back to H.264 mp4", file=sys.stderr)
            dest = dest.with_suffix(".mp4")
    run(
        [
            ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(src), "-an", "-vf", _vf(),
            "-c:v", "libx264", "-preset", "fast", "-crf", "32",
            "-tune", "stillimage", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", str(dest),
        ]
    )
    return dest


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--record", action="store_true", help="adb screenrecord on the Pixel")
    p.add_argument("--from", dest="src", type=Path, help="shrink an existing mp4/mov/webm")
    p.add_argument("--seconds", type=int, default=15)
    p.add_argument("-o", "--out", type=Path, default=Path("uat-small.webm"))
    args = p.parse_args()

    raw = Path("uat-raw.mp4")
    if args.record:
        record(args.seconds, raw)
        print("recorded", raw, kb(raw))
        src = raw
    elif args.src:
        src = args.src
        print("source", src, kb(src))
    else:
        p.print_help()
        return 2

    out = shrink(src, args.out)
    print("out", out, kb(out))
    if raw.is_file() and raw.resolve() != out.resolve():
        raw.unlink()
        print("deleted", raw)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
