#!/usr/bin/env bash
# Busy-polls `adb exec-out screencap` until two consecutive frames come back
# byte-identical (the UI has settled) instead of a blind fixed `sleep` before
# a single capture. A fixed sleep either wastes real time waiting past when
# the screen already settled, or races it and captures a mid-transition
# frame — this adapts to whichever happens on the device right now.
#
# Usage: adb-shot.sh <output.png> [max_seconds] [poll_interval_seconds]
#   adb-shot.sh /tmp/out.png            # defaults: 5s cap, 0.2s poll
#   adb-shot.sh /tmp/out.png 8 0.3
set -euo pipefail

out="${1:?usage: adb-shot.sh <output.png> [max_seconds] [poll_interval_seconds]}"
max_seconds="${2:-3}"
interval="${3:-0.15}"

prev="$(mktemp)"
cur="$(mktemp)"
trap 'rm -f "$prev" "$cur"' EXIT

start=$(date +%s)
adb exec-out screencap -p > "$prev" 2>/dev/null || true

while :; do
  now=$(date +%s)
  if [[ $((now - start)) -ge "$max_seconds" ]]; then
    break
  fi
  adb exec-out screencap -p > "$cur" 2>/dev/null || true
  if [[ -s "$cur" ]] && cmp -s "$prev" "$cur"; then
    break
  fi
  cp "$cur" "$prev"
  sleep "$interval"
done

cp "$cur" "$out"
echo "$out"
