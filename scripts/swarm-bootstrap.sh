#!/bin/sh
# Fresh Swarm Bench checkout. Download this first; Grok CLI is the other client.
# curl -fsSL https://raw.githubusercontent.com/brianreborn/japanglify/main/scripts/swarm-bootstrap.sh | sh
set -eu

OFFICIAL_REPO="brianreborn/japanglify"
DEV_REPO="electrobrian/japanglify"
OFFICIAL_BRANCH="main"
DEV_BRANCH="BETA-2"
START=0
RUNNER=0
DRY=0

for a in "$@"; do
  case "$a" in
    --start) START=1 ;;
    --runner) RUNNER=1 ;;
    --dry-run) DRY=1 ;;
    -h|--help)
      echo "usage: swarm-bootstrap.sh [--dry-run] [--runner] [--start]"
      echo "  clones {home}/src/{owner}/{repo}  (override src with SWARM_SRC)"
      exit 0
      ;;
  esac
done

home="${HOME:-}"
if [ -z "$home" ]; then
  echo "HOME is not set" >&2
  exit 1
fi
src="${SWARM_SRC:-$home/src}"
official="$src/$OFFICIAL_REPO"
dev="$src/$DEV_REPO"

echo "official=$official"
echo "dev=$dev"
echo "runner=$home/actions-runner"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing $1" >&2
    return 1
  fi
}

need git
if ! command -v grok >/dev/null 2>&1; then
  echo "warn: grok CLI not on PATH (install it; this script does not)" >&2
fi
if ! command -v python3 >/dev/null 2>&1 && ! command -v python >/dev/null 2>&1; then
  echo "warn: python3 not on PATH (needed for swarm-grok / swarm_paths)" >&2
fi

ensure() {
  url="$1"
  dir="$2"
  branch="$3"
  if [ "$DRY" -eq 1 ]; then
    if [ -d "$dir/.git" ]; then
      echo "would pull $dir ($branch)"
    else
      echo "would clone $url -> $dir ($branch)"
    fi
    return 0
  fi
  mkdir -p "$(dirname "$dir")"
  if [ -d "$dir/.git" ]; then
    git -C "$dir" fetch origin
    git -C "$dir" checkout "$branch"
    git -C "$dir" pull --ff-only origin "$branch" || git -C "$dir" pull --ff-only
  else
    git clone --branch "$branch" "https://github.com/$url.git" "$dir"
  fi
}

ensure "$OFFICIAL_REPO" "$official" "$OFFICIAL_BRANCH"
ensure "$DEV_REPO" "$dev" "$DEV_BRANCH"

if [ "$DRY" -eq 1 ]; then
  echo "next: $official/scripts/swarm-grok"
  exit 0
fi

if [ "$RUNNER" -eq 1 ]; then
  if command -v pwsh >/dev/null 2>&1; then
    pwsh -File "$official/scripts/swarm-bench-runner.ps1"
  else
    echo "warn: --runner is the Windows listener; on Unix leave it unless you self-host" >&2
  fi
fi

echo "next: $official/scripts/swarm-grok"
echo "     (product work in $dev)"
if [ "$START" -eq 1 ]; then
  exec "$official/scripts/swarm-grok"
fi
