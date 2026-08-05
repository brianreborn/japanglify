#!/usr/bin/env bash
# One-shot debug APK build with FreeBSD Linuxulator prep when needed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/freebsd-linuxulator.sh
source "$ROOT/scripts/lib/freebsd-linuxulator.sh"
export_linuxulator_env

# Prefer a known-good JDK for Gradle when present
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

cd "$ROOT"
exec ./gradlew :app:assembleDebug "$@"
