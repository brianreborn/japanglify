#!/usr/bin/env python3
"""Record a short Pixel UAT clip and shrink still-heavy footage.

Stock Android screenrecord is ~4 Mbps CBR (15s ≈ 7.5 MB) even when the chip
does not move. This records at 400 kbps, then ffmpeg mpdecimate + 6 fps.
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


def shrink(src: Path, dest: Path) -> Path:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("ffmpeg not on PATH; keeping record as-is", file=sys.stderr)
        if src.resolve() != dest.resolve():
            dest.write_bytes(src.read_bytes())
        return dest
    run(
        [
            ffmpeg,
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            str(src),
            "-an",
            "-vf",
            "mpdecimate,fps=6,scale=720:-2",
            "-c:v",
            "libx264",
            "-preset",
            "fast",
            "-crf",
            "32",
            "-tune",
            "stillimage",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            str(dest),
        ]
    )
    return dest


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--record", action="store_true", help="adb screenrecord on the Pixel")
    p.add_argument("--from", dest="src", type=Path, help="shrink an existing mp4")
    p.add_argument("--seconds", type=int, default=15)
    p.add_argument("-o", "--out", type=Path, default=Path("uat-small.mp4"))
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
