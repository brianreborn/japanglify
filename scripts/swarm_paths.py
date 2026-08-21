#!/usr/bin/env python3
"""Canonical workdirs for a swarm host. Same layout on every machine.

{home}/src/{owner}/{repo}
  Windows home = %USERPROFILE%
  Unix home    = $HOME
  override     = $SWARM_SRC (replaces {home}/src)

Listener (not a git clone):
  Windows         C:\\actions-runner
  Unix            {home}/actions-runner
  github-actions  $GITHUB_WORKSPACE
  grok-cloud      none
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path, PurePosixPath, PureWindowsPath

ROOT = Path(__file__).resolve().parent.parent
HOSTS = ROOT / "docs" / "japanglify" / "hosts.json"
INSTANCE = ROOT / "docs" / "japanglify" / "instance.json"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def family_of(os_name: str | None) -> str:
    n = (os_name or "").lower()
    if n.startswith("win"):
        return "windows"
    if n in {"cloud"}:
        return "cloud"
    return "unix"


def flavour(family: str):
    return PureWindowsPath if family == "windows" else PurePosixPath


def home_dir(family: str, env: dict | None = None):
    env = env if env is not None else os.environ
    if family == "windows":
        raw = env.get("USERPROFILE") or env.get("HOME")
    else:
        raw = env.get("HOME") or env.get("USERPROFILE")
    fl = flavour(family)
    if raw:
        return fl(raw)
    return fl(str(Path.home()))


def src_dir(family: str, env: dict | None = None, tmpl: dict | None = None):
    env = env if env is not None else os.environ
    fl = flavour(family)
    if env.get("SWARM_SRC"):
        return fl(env["SWARM_SRC"])
    src_name = (tmpl or {}).get("srcDir") or "src"
    return home_dir(family, env) / src_name


def repo_path(src, repo: str | None):
    if not repo:
        return None
    p = src
    for part in repo.replace("\\", "/").split("/"):
        if part:
            p = p / part
    return p


def runner_path(lease: dict, family: str, home, env: dict | None = None, tmpl: dict | None = None):
    env = env if env is not None else os.environ
    fl = flavour(family)
    override = (lease.get("workdir") or {}).get("runner")
    if override:
        return fl(override.replace("{home}", str(home)))
    spec = (tmpl or {}).get("runner") or {}
    lid = lease.get("id")
    if lid == "grok-cloud" or family == "cloud":
        return None
    if lid == "github-actions":
        ws = env.get("GITHUB_WORKSPACE")
        return fl(ws) if ws else None
    if family == "windows":
        return fl(spec.get("windows") or r"C:\actions-runner")
    unix = spec.get("unix") or "{home}/actions-runner"
    return fl(unix.replace("{home}", str(home)))


def resolve(
    lease: dict,
    instance: dict,
    *,
    family: str | None = None,
    env: dict | None = None,
    tmpl: dict | None = None,
) -> dict:
    env = env if env is not None else dict(os.environ)
    family = family or family_of(lease.get("os"))
    tmpl = tmpl or {}
    home = home_dir(family, env)
    src = src_dir(family, env, tmpl)
    official_repo = instance.get("officialRepo") or "brianreborn/japanglify"
    dev_repo = lease.get("devRepo") or instance.get("devRepo")
    local = lease.get("workdir") or {}
    official = flavour(family)(local["official"]) if local.get("official") else repo_path(src, official_repo)
    dev = flavour(family)(local["dev"]) if local.get("dev") else repo_path(src, dev_repo)
    runner = runner_path(lease, family, home, env, tmpl)
    if family == "cloud" or lease.get("id") == "grok-cloud":
        official = None
        dev = None
        runner = None
    if lease.get("id") == "github-actions":
        ws = env.get("GITHUB_WORKSPACE")
        official = flavour(family)(ws) if ws else official
        dev = None
    def s(p):
        return None if p is None else str(p)
    return {
        "id": lease.get("id"),
        "role": lease.get("role"),
        "family": family,
        "home": s(home),
        "src": s(src),
        "officialRepo": official_repo,
        "devRepo": dev_repo,
        "official": s(official),
        "dev": s(dev),
        "runner": s(runner),
    }


def table_and_instance() -> tuple[dict, dict]:
    return load_json(HOSTS), load_json(INSTANCE)


def lease_by_id(table: dict, lid: str) -> dict:
    for lease in table.get("leases") or []:
        if lease.get("id") == lid:
            return lease
    raise KeyError(lid)


def self_test() -> int:
    table, inst = table_and_instance()
    tmpl = table.get("workdir") or {}
    failed = 0

    def check(name, got, **want):
        nonlocal failed
        ok = all(got.get(k) == v for k, v in want.items())
        print("ok" if ok else "FAIL", name, {k: got.get(k) for k in want})
        failed += not ok

    win = resolve(
        lease_by_id(table, "win11-pixel"),
        inst,
        family="windows",
        env={"USERPROFILE": r"C:\Users\brian"},
        tmpl=tmpl,
    )
    check(
        "win11-pixel",
        win,
        official=r"C:\Users\brian\src\brianreborn\japanglify",
        dev=r"C:\Users\brian\src\electrobrian\japanglify",
        runner=r"C:\actions-runner",
    )
    unix = resolve(
        lease_by_id(table, "unix-pixel"),
        inst,
        family="unix",
        env={"HOME": "/home/u"},
        tmpl=tmpl,
    )
    check(
        "unix-pixel",
        unix,
        official="/home/u/src/brianreborn/japanglify",
        dev="/home/u/src/electrobrian/japanglify",
        runner="/home/u/actions-runner",
    )
    cloud = resolve(lease_by_id(table, "grok-cloud"), inst, family="cloud", env={}, tmpl=tmpl)
    check("grok-cloud", cloud, official=None, dev=None, runner=None)
    gha = resolve(
        lease_by_id(table, "github-actions"),
        inst,
        family="unix",
        env={"HOME": "/home/runner", "GITHUB_WORKSPACE": "/home/runner/work/japanglify/japanglify"},
        tmpl=tmpl,
    )
    check(
        "github-actions",
        gha,
        official="/home/runner/work/japanglify/japanglify",
        dev=None,
        runner="/home/runner/work/japanglify/japanglify",
    )
    over = resolve(
        lease_by_id(table, "win11-pixel"),
        inst,
        family="windows",
        env={"USERPROFILE": r"C:\Users\brian", "SWARM_SRC": r"D:\swarm"},
        tmpl=tmpl,
    )
    check(
        "SWARM_SRC",
        over,
        official=r"D:\swarm\brianreborn\japanglify",
        src=r"D:\swarm",
    )
    print("swarm_paths self-test ok" if failed == 0 else f"FAIL {failed}")
    return 1 if failed else 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--id", required=True, help="lease id (win11-pixel, unix-pixel, …)")
    p.add_argument("--json", action="store_true")
    args = p.parse_args()
    table, inst = table_and_instance()
    row = resolve(lease_by_id(table, args.id), inst, tmpl=table.get("workdir") or {})
    if args.json:
        json.dump(row, sys.stdout, indent=2)
        sys.stdout.write("\n")
        return 0
    for k in ("id", "role", "home", "src", "official", "dev", "runner"):
        print(f"{k}={row.get(k) or ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
