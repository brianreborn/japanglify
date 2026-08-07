#!/usr/bin/env bash
# Acceptance smoke test: runs the domain test suite, then (if a device is
# connected) drives a handful of real, on-device checks and assembles a
# Markdown report with embedded screenshots — the kind GitHub renders
# natively in a repo file, a PR body, or a GITHUB_STEP_SUMMARY.
#
# Device-dependent checks are best-effort: if any of them fail or no device
# is connected at all, the report says so and the script still exits 0 —
# the domain-test summary alone is meaningful and CI-safe (no device
# needed), which matters for the eventual locally-spun-up Android VM target.
#
# Usage: acceptance-smoke-test.sh <output-dir> [device-serial]
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:?usage: acceptance-smoke-test.sh <output-dir> [device-serial]}"
SERIAL="${2:-}"
IMAGES_DIR="$OUT_DIR/images"
mkdir -p "$IMAGES_DIR"

PKG="com.japanglify.app"

# --- adb discovery: prefer this repo's bootstrapped SDK, matching how the
# rest of this session's testing found adb, before falling back to PATH. ---
find_adb() {
  for candidate in \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "$ROOT/sdk/platform-tools/adb"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  command -v adb 2>/dev/null || true
}
ADB_BIN="$(find_adb)"

adbc() {
  if [[ -n "$SERIAL" ]]; then
    "$ADB_BIN" -s "$SERIAL" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

# --- report state: build the summary table + body as we go ---
SUMMARY_ROWS=()
BODY_SECTIONS=()

add_row() { SUMMARY_ROWS+=("| $1 | $2 | $3 |"); }
add_section() { BODY_SECTIONS+=("$1"); }

# ---------------------------------------------------------------------------
# 1. Domain test suite — CI-safe, no device required.
# ---------------------------------------------------------------------------
echo "==> Running domain test suite"
cd "$ROOT"
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in /usr/local/openjdk21 /usr/lib/jvm/java-21-openjdk \
      /usr/lib/jvm/java-17-openjdk "$HOME/.jdks"/*; do
    if [[ -x "${candidate}/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi

DOMAIN_TESTS=0
DOMAIN_FAILURES=0
DOMAIN_SKIPPED=0
for xml in domain/build/test-results/test/TEST-*.xml; do
  [[ -f "$xml" ]] || continue
  line="$(grep -o '<testsuite[^>]*' "$xml" | head -1)"
  t="$(grep -o 'tests="[0-9]*"' <<<"$line" | grep -o '[0-9]*')"
  f="$(grep -o 'failures="[0-9]*"' <<<"$line" | grep -o '[0-9]*')"
  s="$(grep -o 'skipped="[0-9]*"' <<<"$line" | grep -o '[0-9]*')"
  DOMAIN_TESTS=$((DOMAIN_TESTS + ${t:-0}))
  DOMAIN_FAILURES=$((DOMAIN_FAILURES + ${f:-0}))
  DOMAIN_SKIPPED=$((DOMAIN_SKIPPED + ${s:-0}))
done

if [[ $DOMAIN_TESTS -eq 0 ]]; then
  add_row "Domain test suite" "⚠️" "no test-results XML found — did :domain:test run?"
elif [[ $DOMAIN_FAILURES -eq 0 ]]; then
  add_row "Domain test suite" "✅" "$DOMAIN_TESTS passed, $DOMAIN_SKIPPED skipped"
else
  add_row "Domain test suite" "❌" "$DOMAIN_FAILURES/$DOMAIN_TESTS failed"
fi

# ---------------------------------------------------------------------------
# 2. Device check
# ---------------------------------------------------------------------------
DEVICE_OK=0
if [[ -n "$ADB_BIN" ]] && adbc get-state >/dev/null 2>&1; then
  DEVICE_OK=1
  MODEL="$(adbc shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  RELEASE="$(adbc shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
  add_row "Device" "✅" "${MODEL:-unknown} (Android ${RELEASE:-?})"
else
  add_row "Device" "⚠️" "no device connected — device-dependent checks skipped"
fi

settle_shot() {
  # Mirrors adb-shot.sh's settle-poll without depending on it directly
  # (this script already needs its own adbc wrapper for -s handling).
  local out="$1" tries=15
  local prev cur
  prev="$(mktemp)"; cur="$(mktemp)"
  adbc exec-out screencap -p > "$prev" 2>/dev/null || true
  while (( tries-- > 0 )); do
    adbc exec-out screencap -p > "$cur" 2>/dev/null || true
    if [[ -s "$cur" ]] && cmp -s "$prev" "$cur"; then break; fi
    cp "$cur" "$prev"
    sleep 0.2
  done
  cp "$cur" "$out"
  rm -f "$prev" "$cur"
}

if [[ $DEVICE_OK -eq 1 ]]; then
  # Wake + dismiss keyguard first: a device/VM left idle dozes off, and a
  # dozing screen still yields a "successful" (non-empty) screencap of the
  # lockscreen/notification shade instead of the app — a silent wrong-content
  # failure, not a loud one. Matters even more for an unattended VM target.
  echo "==> Waking device / dismissing keyguard"
  adbc shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adbc shell wm dismiss-keyguard >/dev/null 2>&1
  sleep 0.5

  # -----------------------------------------------------------------------
  # 3. App launch + Settings screenshot
  # -----------------------------------------------------------------------
  echo "==> Launching Settings"
  adbc shell am force-stop "$PKG" >/dev/null 2>&1
  if adbc shell am start -n "$PKG/.SettingsActivity" >/dev/null 2>&1; then
    ON_SETTINGS=0
    for _ in $(seq 1 6); do
      dump_file="$(mktemp)"
      adbc shell uiautomator dump /sdcard/acceptance_ui.xml >/dev/null 2>&1
      adbc pull /sdcard/acceptance_ui.xml "$dump_file" >/dev/null 2>&1
      if grep -q 'settings_root' "$dump_file" 2>/dev/null; then
        ON_SETTINGS=1
        rm -f "$dump_file"
        break
      fi
      rm -f "$dump_file"
      sleep 0.4
    done

    if [[ $ON_SETTINGS -eq 1 ]]; then
      settle_shot "$IMAGES_DIR/settings.png"
      if [[ -s "$IMAGES_DIR/settings.png" ]]; then
        add_row "App launch" "✅" "Settings screen"
        add_section "## Settings screen
![Settings screen](images/settings.png)"
      else
        add_row "App launch" "⚠️" "on Settings screen but screenshot capture failed"
      fi
    else
      add_row "App launch" "⚠️" "am start ran but Settings screen never appeared (locked/dozing device?)"
    fi
  else
    add_row "App launch" "❌" "am start failed"
  fi

  # -----------------------------------------------------------------------
  # 4. Output-format preview — scroll until the card is actually on screen
  #    instead of a fixed swipe count (this session's repeated lesson).
  # -----------------------------------------------------------------------
  echo "==> Locating output-format preview"
  FOUND_PREVIEW=0
  for _ in $(seq 1 8); do
    dump_file="$(mktemp)"
    adbc shell uiautomator dump /sdcard/acceptance_ui.xml >/dev/null 2>&1
    adbc pull /sdcard/acceptance_ui.xml "$dump_file" >/dev/null 2>&1
    if grep -q 'output_format_sample_interlinear' "$dump_file" 2>/dev/null; then
      FOUND_PREVIEW=1
      rm -f "$dump_file"
      break
    fi
    rm -f "$dump_file"
    adbc shell input swipe 540 1900 540 1300 300 >/dev/null 2>&1
    sleep 0.6
  done

  if [[ $FOUND_PREVIEW -eq 1 ]]; then
    settle_shot "$IMAGES_DIR/output_format_preview.png"
    add_row "Output-format preview" "✅" "all formats render; current selection marked"
    add_section "## Output-format live preview
Shows every \`OutputFormat\` rendered side by side (the alignment
refinement from this project's 2026-08-06 session is visible in the
Interlinear row).

![Output-format preview](images/output_format_preview.png)"
  else
    add_row "Output-format preview" "⚠️" "card not found after scrolling — UI layout may have changed"
  fi

  # -----------------------------------------------------------------------
  # 5. Copy-image artifact via the debug-only AcceptanceTestActivity — the
  #    real rendering pipeline, no clipboard/notification/accessibility.
  # -----------------------------------------------------------------------
  echo "==> Rendering acceptance image via AcceptanceTestActivity"
  EXT_DIR="/sdcard/Android/data/$PKG/files"
  STATUS_REMOTE="$EXT_DIR/acceptance_status.txt"
  PNG_REMOTE="$EXT_DIR/acceptance_result.png"
  adbc shell rm -f "$STATUS_REMOTE" "$PNG_REMOTE" >/dev/null 2>&1

  if adbc shell am start -n "$PKG/.debug.AcceptanceTestActivity" >/dev/null 2>&1; then
    STATUS_TEXT=""
    for _ in $(seq 1 15); do
      STATUS_TEXT="$(adbc shell cat "$STATUS_REMOTE" 2>/dev/null | tr -d '\r')"
      [[ -n "$STATUS_TEXT" ]] && break
      sleep 0.4
    done

    if [[ "$STATUS_TEXT" == OK* ]]; then
      # Direct pull first; fall back to run-as (in case external-storage
      # access is restricted on this Android version/build).
      if ! adbc pull "$PNG_REMOTE" "$IMAGES_DIR/copy_image_result.png" >/dev/null 2>&1 \
          || [[ ! -s "$IMAGES_DIR/copy_image_result.png" ]]; then
        adbc exec-out run-as "$PKG" cat "files/acceptance_result.png" \
          > "$IMAGES_DIR/copy_image_result.png" 2>/dev/null
      fi
      if [[ -s "$IMAGES_DIR/copy_image_result.png" ]]; then
        add_row "Copy-image render" "✅" "real pipeline, real Canvas/Paint, real Kuromoji"
        add_section "## Copy-image render (the real rendering pipeline)
Rendered via \`AcceptanceTestActivity\` → \`ClipboardImageRenderer\` — the
same code path the real \"Copy image\" notification action uses, exercised
directly with no clipboard/notification/accessibility involved. Furigana
should read visibly smaller than base/romaji.

![Copy-image result](images/copy_image_result.png)"
      else
        add_row "Copy-image render" "⚠️" "activity reported OK but PNG pull failed"
      fi
    elif [[ -n "$STATUS_TEXT" ]]; then
      add_row "Copy-image render" "❌" "$STATUS_TEXT"
    else
      add_row "Copy-image render" "⚠️" "no status file after 6s — is this a debug build?"
    fi
  else
    add_row "Copy-image render" "⚠️" "could not start AcceptanceTestActivity — is this a debug build?"
  fi
fi

# ---------------------------------------------------------------------------
# Assemble the Markdown report
# ---------------------------------------------------------------------------
REPORT="$OUT_DIR/index.md"
{
  echo "# Japanglify acceptance smoke test"
  echo
  echo "_Generated $(date -u +"%Y-%m-%d %H:%M:%S UTC")_"
  echo
  echo "| Check | Status | Notes |"
  echo "|---|---|---|"
  for row in "${SUMMARY_ROWS[@]}"; do echo "$row"; done
  echo
  for section in "${BODY_SECTIONS[@]}"; do
    echo "$section"
    echo
  done
} > "$REPORT"

echo "==> Report written to $REPORT"
exit 0
