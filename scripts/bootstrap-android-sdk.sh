#!/usr/bin/env bash
# Install a minimal Android command-line SDK under ./sdk (no Android Studio).
#
# On FreeBSD: downloads the *Linux* SDK packages and brandelf(1)-marks every
# newly unpacked Linux ELF so the Linuxulator can execute aapt2, adb, etc.
#
# Domain tests need no SDK — only :app:assemble* does.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/freebsd-linuxulator.sh
source "$ROOT/scripts/lib/freebsd-linuxulator.sh"
export_linuxulator_env

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$ROOT/sdk}}"
BRAND="$ROOT/scripts/lib/brand-linux-elfs.sh"
chmod +x "$BRAND" "$ROOT/scripts/prepare-freebsd-build.sh" 2>/dev/null || true

ON_FREEBSD=0
if is_freebsd; then
  ON_FREEBSD=1
  echo "==> FreeBSD host: using Linux Android SDK + Linuxulator"
  ensure_linuxulator
fi

mkdir -p "$SDK_ROOT/cmdline-tools"

# Host detection for Google's package naming. FreeBSD always uses Linux bits.
if [[ "$ON_FREEBSD" -eq 1 ]]; then
  PLATFORM=linux
else
  UNAME_S_LC="$(uname -s 2>/dev/null | tr '[:upper:]' '[:lower:]' || true)"
  case "$UNAME_S_LC" in
    linux*)  PLATFORM=linux ;;
    darwin*) PLATFORM=mac ;;
    mingw*|msys*|cygwin*|windows*) PLATFORM=win ;;
    freebsd*|openbsd*|netbsd*|dragonfly*) PLATFORM=linux ;;
    *)
      echo "Unsupported OS for SDK bootstrap: $(uname -s)" >&2
      exit 1
      ;;
  esac
fi

echo "==> SDK platform package: $PLATFORM  →  $SDK_ROOT"

CMDLINE_ZIP="commandlinetools-${PLATFORM}-11076708_latest.zip"
CMDLINE_URL="https://dl.google.com/android/repository/${CMDLINE_ZIP}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

brand_tree() {
  if [[ "$ON_FREEBSD" -eq 1 ]]; then
    bash "$BRAND" "$@"
  fi
}

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" && \
      ! -f "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Downloading Android command-line tools ($CMDLINE_ZIP) ..."
  curl -fsSL -o "$TMP/cmdtools.zip" "$CMDLINE_URL"
  unzip -q "$TMP/cmdtools.zip" -d "$TMP"
  # Zip layouts vary: either cmdline-tools/{bin,lib} or cmdline-tools/latest/...
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  if [[ -d "$TMP/cmdline-tools/bin" ]]; then
    mv "$TMP/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  elif [[ -d "$TMP/cmdline-tools/latest" ]]; then
    mv "$TMP/cmdline-tools/latest" "$SDK_ROOT/cmdline-tools/latest"
  else
    # Fall back: first directory under extract root
    mv "$TMP/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest" 2>/dev/null \
      || mv "$TMP"/* "$SDK_ROOT/cmdline-tools/latest"
  fi
  brand_tree "$SDK_ROOT/cmdline-tools"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -f "$SDKMANAGER" ]]; then
  echo "sdkmanager not found at $SDKMANAGER" >&2
  exit 1
fi
chmod +x "$SDKMANAGER" || true

echo "Installing platform-tools, android-35, build-tools;35.0.0 ..."
# sdkmanager is a shell script + JAR (Java) — runs natively on FreeBSD JVM.
# On FreeBSD, JAVA_TOOL_OPTIONS must include -Dos.name=Linux or the Linux
# native package set (platform-tools, build-tools) is not offered.
if [[ "$ON_FREEBSD" -eq 1 ]]; then
  export_linuxulator_env
  echo "    JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-}"
fi

# Accept licenses non-interactively, then install packages (fail hard if missing).
# Avoid `yes | …` SIGPIPE (exit 141) by feeding a finite stream of y's.
feed_yes() {
  # shellcheck disable=SC2034
  for _ in $(seq 1 300); do printf 'y\n'; done
}

feed_yes | run_with_linux_os_name "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true
if ! feed_yes | run_with_linux_os_name "$SDKMANAGER" --sdk_root="$SDK_ROOT" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"; then
  echo "sdkmanager install failed" >&2
  exit 1
fi

# Brand everything the package manager just unpacked (aapt2, adb, …).
brand_tree "$SDK_ROOT"

# Persist for Gradle
# Gradle local.properties requires escaped paths on Windows only; FreeBSD/Unix plain.
printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT/local.properties"
# Hint for our FreeBSD Gradle hook
if [[ "$ON_FREEBSD" -eq 1 ]]; then
  {
    echo "japanglify.freebsd=true"
    echo "japanglify.sdk.branded=true"
  } >> "$ROOT/local.properties"
fi

echo "Wrote $ROOT/local.properties"
echo "SDK ready at $SDK_ROOT"

if [[ "$ON_FREEBSD" -eq 1 ]]; then
  echo
  echo "FreeBSD: re-run branding after any future SDK/Gradle native download:"
  echo "  ./scripts/prepare-freebsd-build.sh"
  echo "Build APK:"
  echo "  ./gradlew :app:assembleDebug"
else
  echo "Build APK with: ./gradlew :app:assembleDebug"
fi
