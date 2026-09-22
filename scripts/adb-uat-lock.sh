#!/bin/sh
# Exclusive UAT lock for one ADB host.
#
#   Lock file:  $HOME/$ADBUUID     (required env; ADB serial / host id)
#   Primitive:  lockf(1) when present; otherwise scripts/lib/adb-uat-lock.py
#               which calls POSIX lockf via fcntl.lockf.
#
# Usage:
#   ADBUUID=<serial> scripts/adb-uat-lock.sh [--timeout SECS] -- cmd [args...]
#   ADBUUID=<serial> scripts/adb-uat-lock.sh --status
#
# While the lock is held, ANDROID_SERIAL defaults to $ADBUUID so child adb
# talks to that host only. Other workers block on lockf until this command
# exits. Do not delete $HOME/$ADBUUID while a holder is alive (lockf -k).
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PY="$ROOT/scripts/lib/adb-uat-lock.py"
TIMEOUT=300
STATUS=0

while [ $# -gt 0 ]; do
  case "$1" in
    --timeout) TIMEOUT=$2; shift 2 ;;
    --status) STATUS=1; shift ;;
    --) shift; break ;;
    -h|--help)
      sed -n '2,16p' "$0"
      exit 0
      ;;
    *) break ;;
  esac
done

if [ -z "${ADBUUID:-}" ]; then
  echo "adb-uat-lock: ADBUUID is required" >&2
  exit 2
fi
case "$ADBUUID" in
  */*|*"\\"*|.|..)
    echo "adb-uat-lock: ADBUUID must be a single path segment" >&2
    exit 2
    ;;
esac

HOME=${HOME:-${USERPROFILE:-}}
if [ -z "$HOME" ]; then
  echo "adb-uat-lock: HOME is not set" >&2
  exit 2
fi

LOCK="$HOME/$ADBUUID"
export ANDROID_SERIAL="${ANDROID_SERIAL:-$ADBUUID}"

if [ "$STATUS" -eq 1 ]; then
  if command -v python3 >/dev/null 2>&1; then
    exec python3 "$PY" --status
  fi
  if [ -f "$LOCK" ]; then
    echo "lockfile $LOCK"
    cat "$LOCK"
  else
    echo "free $LOCK (no file)"
  fi
  exit 0
fi

if [ $# -eq 0 ]; then
  echo "adb-uat-lock: missing command after --" >&2
  exit 2
fi

# Keep the lock file (-k). -t is wait seconds; 0 is non-blocking.
if command -v lockf >/dev/null 2>&1; then
  : >>"$LOCK"
  exec lockf -k -t "$TIMEOUT" "$LOCK" "$@"
fi

if command -v python3 >/dev/null 2>&1; then
  exec python3 "$PY" --timeout "$TIMEOUT" -- "$@"
fi
if command -v python >/dev/null 2>&1; then
  exec python "$PY" --timeout "$TIMEOUT" -- "$@"
fi

echo "adb-uat-lock: no lockf(1) and no python; cannot lock $LOCK" >&2
exit 127
