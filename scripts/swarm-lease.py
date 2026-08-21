#!/usr/bin/env python3
"""DHCP-style host lease: ask GitHub who this machine is. Wildcards allowed; most specific wins."""

from __future__ import annotations

import argparse
import fnmatch
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


def _patterns(lease: dict) -> list[str]:
    m = lease.get("match") or {}
    raw = m.get("hostname") if m.get("hostname") is not None else lease.get("matchHostnames")
    if raw is None:
        return []
    if isinstance(raw, str):
        raw = [raw]
    return [p for p in raw if p]


def _field_ok(got: str, want) -> bool:
    if want is None or want == "*":
        return True
    if isinstance(want, list):
        g = got.lower()
        return any(fnmatch.fnmatch(g, str(w).lower()) for w in want if w and w != "*") or "*" in list(want)
    return fnmatch.fnmatch(got.lower(), str(want).lower())


def score_lease(lease: dict, f: dict, pinned_id: str | None) -> int:
    if pinned_id and lease.get("id") == pinned_id:
        return 10_000
    m = lease.get("match") or {}
    hostnames = _patterns(lease)
    constraints = 0
    s = 0

    if "osFamily" in m:
        constraints += 1
        if not _field_ok(f["osFamily"], m["osFamily"]):
            return 0
        s += 10 if m["osFamily"] != "*" else 1
    if "os" in m:
        constraints += 1
        if not _field_ok(f["os"], m["os"]):
            return 0
        s += 15 if m["os"] != "*" else 1
    if hostnames:
        constraints += 1
        host = f["hostname"].lower()
        hits = [p for p in hostnames if fnmatch.fnmatch(host, p.lower())]
        if not hits:
            return 0
        s += max(80 - p.count("*") * 25 - p.count("?") * 10 + min(len(p), 20) for p in hits)
    if "adbPresent" in m:
        constraints += 1
        if bool(f.get("adb")) != bool(m["adbPresent"]):
            return 0
        s += 8
    if "adbAttached" in m:
        constraints += 1
        if bool(f.get("adbDevices")) != bool(m["adbAttached"]):
            return 0
        s += 25

    if constraints == 0:
        return 0
    return s


def pick_lease(table: dict, f: dict, pinned_id: str | None) -> tuple[dict | None, list[str]]:
    if pinned_id:
        for lease in table.get("leases", []):
            if lease.get("id") == pinned_id:
                return lease, []
        return None, []
    ranked = []
    for lease in table.get("leases", []):
        sc = score_lease(lease, f, None)
        if sc > 0:
            ranked.append((sc, lease["id"], lease))
    if not ranked:
        return None, []
    ranked.sort(key=lambda t: (-t[0], t[1]))
    best = ranked[0][0]
    winners = [t for t in ranked if t[0] == best]
    return winners[0][2], [t[1] for t in winners[1:]]


def pinned_id(args_id: str | None) -> str | None:
    if args_id:
        return args_id
    env = os.environ.get("SWARM_HOST_ID")
    if env:
        return env.strip()
    if ID_FILE.is_file():
        return ID_FILE.read_text(encoding="utf-8").strip() or None
    return None


def offer(f: dict) -> dict:
    family = f["osFamily"]
    suggested = "pool-bench-windows" if family == "windows" else "pool-bench-unix"
    return {
        "id": suggested,
        "role": "swarm-bench",
        "os": family,
        "device": "adb" if f["adbDevices"] else None,
        "devRepo": "electrobrian/japanglify",
        "branch": "BETA-2",
        "notes": "Offered from %s. Prefer a pool match, or add match.hostname." % f["hostname"],
        "match": {
            "osFamily": family,
            "hostname": [f["hostname"]],
            "adbAttached": bool(f["adbDevices"]),
        },
        "never": [
            "gradle.assemble-release",
            "secret.keystore-release",
            "ci.remote-assemble",
        ],
        "detected": f,
    }


def self_test() -> int:
    table, _ = load_table(repo_root(), "local")

    def f(**kw):
        base = {
            "hostname": "x",
            "os": "linux",
            "osFamily": "unix",
            "adb": None,
            "adbDevices": [],
        }
        base.update(kw)
        return base

    cases = [
        (f(hostname="DESKTOP-1", os="windows", osFamily="windows", adb="/adb", adbDevices=["R"]), "pool-bench-windows"),
        (f(hostname="box", osFamily="unix", adb="/adb", adbDevices=["R"]), "pool-bench-unix"),
        (f(hostname="box", osFamily="windows", adb="/adb", adbDevices=[]), None),
        (f(hostname="fv-az123", os="linux", osFamily="unix"), "github-actions"),
    ]
    failed = 0
    for facts_row, want in cases:
        got, _ = pick_lease(table, facts_row, None)
        gid = None if got is None else got["id"]
        ok = gid == want
        print("self-test", "ok" if ok else "FAIL", facts_row["hostname"], "->", gid, "want", want)
        failed += not ok
    pinned, _ = pick_lease(table, f(osFamily="unix"), "win11-pixel")
    if not pinned or pinned["id"] != "win11-pixel":
        print("self-test FAIL pin")
        failed += 1
    else:
        print("self-test ok pin win11-pixel")
    named = json.loads(json.dumps(table))
    for lease in named["leases"]:
        if lease["id"] == "win11-pixel":
            lease["match"] = {"hostname": ["MY-PIXEL-PC"]}
    got, _ = pick_lease(named, f(hostname="MY-PIXEL-PC", osFamily="windows", adb="/adb", adbDevices=["R"]), None)
    if not got or got["id"] != "win11-pixel":
        print("self-test FAIL named hostname beats pool", got)
        failed += 1
    else:
        print("self-test ok named hostname beats pool")
    return 1 if failed else 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--id", help="pin a lease id (else SWARM_HOST_ID, .swarm-host-id, then wildcards)")
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
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args()

    root = repo_root()
    os.chdir(root)
    if args.self_test:
        return self_test()

    f = facts()
    src = "github" if args.source == "github" else "auto"
    table, loaded_from = load_table(root, src)
    pin = pinned_id(args.id)
    lease, ties = pick_lease(table, f, pin)

    if lease is None:
        stub = offer(f)
        print(
            json.dumps(
                {
                    "status": "nak",
                    "message": "No lease. Attach the Pixel (pool) or check in a reservation. Stub:",
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
        "match": lease.get("match") or {},
        "ties": ties,
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
