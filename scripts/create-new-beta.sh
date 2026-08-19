#!/usr/bin/env bash
# Create a new beta: bump versionCode + versionName, then build the two
# signed release APKs (downloadable + bundled).
#
# Usage:
#   scripts/create-new-beta.sh 1.0.0-beta3
#   scripts/create-new-beta.sh 1.0.0-beta3 5     # explicit versionCode
#
# After success the APKs are at the usual locations:
#   app/build/outputs/apk/downloadable/release/app-downloadable-release.apk
#   app/build/outputs/apk/bundled/release/app-bundled-release.apk
#
# You can then run scripts/push-beta-to-gh.sh to publish them under a
# prerelease tag (e.g. v1.0.0-beta3).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/freebsd-linuxulator.sh
source "$ROOT/scripts/lib/freebsd-linuxulator.sh"
export_linuxulator_env

# Prefer a known-good JDK
if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in /usr/local/openjdk21 /usr/local/openjdk17 \
      /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-17-openjdk; do
    if [[ -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi

if [[ ! -f "$ROOT/local.properties" ]] && [[ ! -d "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/nonexistent}}" ]]; then
  echo "No SDK configured — bootstrapping into $ROOT/sdk ..."
  bash "$ROOT/scripts/bootstrap-android-sdk.sh"
fi

if is_freebsd; then
  bash "$ROOT/scripts/prepare-freebsd-build.sh"
fi

if [[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]]; then
  echo "Usage: $0 <versionName> [versionCode]"
  echo "Example: $0 1.0.0-beta3"
  echo "Example: $0 1.0.0-beta3 5"
  echo
  echo "This script:"
  echo "  1. Bumps versionCode / versionName in app/build.gradle.kts"
  echo "  2. Builds the two signed release APKs (downloadable + bundled)"
  echo
  echo "After it succeeds, use scripts/push-beta-to-gh.sh to publish."
  exit 0
fi

NEW_NAME="$1"
NEW_CODE="${2:-}"

BUILD_GRADLE="$ROOT/app/build.gradle.kts"

# Read current values
CUR_CODE=$(grep -E '^\s*versionCode\s*=' "$BUILD_GRADLE" | head -1 | sed -E 's/.*= *([0-9]+).*/\1/')
CUR_NAME=$(grep -E '^\s*versionName\s*=' "$BUILD_GRADLE" | head -1 | sed -E 's/.*= *"([^"]+)".*/\1/')

echo "Current: versionCode=$CUR_CODE versionName=$CUR_NAME"

if [[ -z "$NEW_CODE" ]]; then
  NEW_CODE=$((CUR_CODE + 1))
fi

echo "New:     versionCode=$NEW_CODE versionName=$NEW_NAME"

# Update the file (portable-ish sed; backup then remove)
sed -i.bak -E "s/(versionCode = )[0-9]+/\1${NEW_CODE}/" "$BUILD_GRADLE"
sed -i.bak -E 's/(versionName = ")[^"]+"/\1'"${NEW_NAME}"'"/' "$BUILD_GRADLE"
rm -f "${BUILD_GRADLE}.bak"

echo "Updated $BUILD_GRADLE"

cd "$ROOT"

echo "Building signed release APKs for both flavors..."
./gradlew :app:assembleDownloadableRelease :app:assembleBundledRelease --console=plain

APK_D="$ROOT/app/build/outputs/apk/downloadable/release/app-downloadable-release.apk"
APK_B="$ROOT/app/build/outputs/apk/bundled/release/app-bundled-release.apk"

for f in "$APK_D" "$APK_B"; do
  if [[ ! -f "$f" ]]; then
    echo "ERROR: Expected APK not found: $f"
    exit 1
  fi
done

# Verify using aapt from the SDK we just ensured
AAPT=$(find "$ROOT/sdk" -path '*build-tools*/aapt' 2>/dev/null | sort -V | tail -1 || true)
if [[ -n "$AAPT" && -x "$AAPT" ]]; then
  echo "Verifying with $AAPT"
  "$AAPT" dump badging "$APK_D" | grep -E 'package:|versionCode|versionName' | cat
  "$AAPT" dump badging "$APK_B" | grep -E 'package:|versionCode|versionName' | cat
else
  echo "aapt not found for verification (APKs were still built)."
fi

echo
echo "=== Beta build complete ==="
echo "downloadable: $APK_D"
echo "bundled:      $APK_B"
echo
echo "Next step (when ready to publish the beta area on GitHub):"
echo "  scripts/push-beta-to-gh.sh v${NEW_NAME}"
echo
echo "Or with explicit notes:"
echo "  scripts/push-beta-to-gh.sh v${NEW_NAME} --notes-file RELNOTES.md"