#!/usr/bin/env python3
"""DHCP-style host lease: ask GitHub (or the local clone) who this machine is."""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import socket
import subprocess
import sys
import urllib.request
from pathlib import Path

LEASE_PATH = Path("docs/japanglify/hosts.json")
ID_FILE = Path(".swarm-host-id")
OUT_FILE = Path(".swarm-host.json")
RAW_URL = (
    "https://raw.githubusercontent.com/brianreborn/japanglify/main/"
    "docs/japanglify/hosts.json"
)


def repo_root() -> Path:
    here = Path.cwd().resolve()
    for p in [here, *here.parents]:
        if (p / ".git").exists():
            return p
    return here


def facts() -> dict:
    os_name = platform.system().lower()
    family = "windows" if os_name.startswith("win") else "unix"
    adb = shutil.which("adb")
    devices = []
    if adb:
        try:
            out = subprocess.check_output(
                [adb, "devices"], text=True, timeout=8, stderr=subprocess.DEVNULL
            )
            for line in out.splitlines()[1:]:
                parts = line.split()
                if len(parts) >= 2 and parts[1] == "device":
                    devices.append(parts[0])
        except (OSError, subprocess.SubprocessError):
            pass
    return {
        "hostname": socket.gethostname(),
        "os": os_name,
        "osFamily": family,
        "python": sys.version.split()[0],
        "adb": adb,
        "adbDevices": devices,
        "cwd": str(Path.cwd()),
    }


def load_table(root: Path, source: str) -> tuple[dict, str]:
    if source != "github":
        candidates = [
            root / LEASE_PATH,
            Path.cwd() / LEASE_PATH,
            Path(__file__).resolve().parent.parent / LEASE_PATH,
        ]
        for local in candidates:
            if local.is_file():
                return json.loads(local.read_text(encoding="utf-8")), str(local)
        if source == "local":
            raise FileNotFoundError(LEASE_PATH)
    req = urllib.request.Request(RAW_URL, headers={"User-Agent": "swarm-lease"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8")), RAW_URL


def resolve_id(args_id: str | None, f: dict, table: dict) -> str | None:
    if args_id:
        return args_id
    env = os.environ.get("SWARM_HOST_ID")
    if env:
        return env.strip()
    if ID_FILE.is_file():
        return ID_FILE.read_text(encoding="utf-8").strip() or None
    host = f["hostname"].lower()
    for lease in table.get("leases", []):
        names = [n.lower() for n in lease.get("matchHostnames") or [] if n]
        if host in names:
            return lease["id"]
    return None


def find_lease(table: dict, host_id: str) -> dict | None:
    for lease in table.get("leases", []):
        if lease.get("id") == host_id:
            return lease
    return None


def offer(f: dict) -> dict:
    family = f["osFamily"]
    suggested = "win11-pixel" if family == "windows" else "unix-pixel"
    return {
        "id": suggested,
        "role": "swarm-bench",
        "os": family,
        "device": "adb" if f["adbDevices"] else None,
        "devRepo": "electrobrian/japanglify",
        "branch": "BETA-2",
        "notes": "Offered from %s; add matchHostnames and check in." % f["hostname"],
        "matchHostnames": [f["hostname"]],
        "never": [
            "gradle.assemble-release",
            "secret.keystore-release",
            "ci.remote-assemble",
        ],
        "detected": f,
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--id", help="lease id (else SWARM_HOST_ID, .swarm-host-id, hostname)")
    p.add_argument(
        "--from",
        dest="source",
        choices=("auto", "local", "github"),
        default="auto",
        help="auto: local clone if present, else GitHub main",
    )
    p.add_argument("--write", action="store_true", help="write .swarm-host.json and .swarm-host-id")
    p.add_argument(
        "--offer",
        action="store_true",
        help="print a lease stub to check in if this machine is unknown",
    )
    args = p.parse_args()

    root = repo_root()
    os.chdir(root)
    f = facts()
    src = "github" if args.source == "github" else "auto"
    table, loaded_from = load_table(root, src)
    host_id = resolve_id(args.id, f, table)
    lease = find_lease(table, host_id) if host_id else None

    if lease is None:
        stub = offer(f)
        print(
            json.dumps(
                {
                    "status": "nak",
                    "message": "No lease. Check this stub into docs/japanglify/hosts.json (leases), then re-run.",
                    "loadedFrom": loaded_from,
                    "offer": stub,
                },
                indent=2,
            )
        )
        if args.offer and args.write:
            ID_FILE.write_text(stub["id"] + "\n", encoding="utf-8")
            OUT_FILE.write_text(json.dumps({"status": "nak", "offer": stub}, indent=2) + "\n")
        return 2

    ack = {
        "status": "ack",
        "id": lease["id"],
        "role": lease.get("role"),
        "os": lease.get("os"),
        "device": lease.get("device"),
        "devRepo": lease.get("devRepo"),
        "branch": lease.get("branch"),
        "never": lease.get("never") or [],
        "loadedFrom": loaded_from,
        "facts": f,
    }
    print(json.dumps(ack, indent=2))
    if args.write:
        ID_FILE.write_text(lease["id"] + "\n", encoding="utf-8")
        OUT_FILE.write_text(json.dumps(ack, indent=2) + "\n", encoding="utf-8")
        print("wrote", ID_FILE, "and", OUT_FILE, file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
