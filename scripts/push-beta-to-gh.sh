#!/usr/bin/env bash
# Push the current beta release APKs to a GitHub prerelease.
#
# This is the "push changes to beta on gh" half.
# It expects the two signed release APKs to already exist (or it can build them).
#
# Usage:
#   scripts/push-beta-to-gh.sh [tag]
#   scripts/push-beta-to-gh.sh v1.0.0-beta2
#   scripts/push-beta-to-gh.sh                  # uses v<current versionName>
#
# Options:
#   --build                 Build the release APKs first (assemble*Release)
#   --notes "text"          Release notes text
#   --notes-file FILE       Path to a markdown notes file
#   --no-explicit-names     Skip uploading the verbose beta-named copies
#
# It always uploads the conventional names that people and README expect:
#   app-release.apk
#   app-bundled-release.apk
#
# Plus (by default) explicit names like:
#   japanglify-1.0.0-beta2-downloadable.apk
#   japanglify-1.0.0-beta2-bundled.apk
#
# The tag is created as a prerelease if it does not already exist.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=lib/freebsd-linuxulator.sh
source "$ROOT/scripts/lib/freebsd-linuxulator.sh"
export_linuxulator_env

# Defaults
DO_BUILD=false
NOTES=""
NOTES_FILE=""
UPLOAD_EXPLICIT=true
TAG=""

if [[ $# -eq 0 || "$1" == "-h" || "$1" == "--help" ]]; then
  cat <<'EOF'
Usage: scripts/push-beta-to-gh.sh [tag] [options]

"Push changes to beta on gh" — publish the signed release APKs
to a GitHub prerelease tag (the beta area).

It uploads the conventional names:
  app-release.apk
  app-bundled-release.apk

Plus (by default) explicit names like:
  japanglify-1.0.0-beta2-downloadable.apk
  japanglify-1.0.0-beta2-bundled.apk

Arguments:
  [tag]         e.g. v1.0.0-beta2. If omitted, uses v<current versionName>.

Options:
  --build                 Build the release APKs first.
  --notes "text"          Use this as the release body.
  --notes-file FILE       Use this markdown file as the release body.
  --no-explicit-names     Only upload the conventional names.

Examples:
  scripts/push-beta-to-gh.sh v1.0.0-beta2 --build
  scripts/push-beta-to-gh.sh --notes-file RELNOTES.md
  scripts/push-beta-to-gh.sh v1.0.0-beta3 --no-explicit-names
EOF
  exit 0
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      DO_BUILD=true
      shift
      ;;
    --notes)
      NOTES="$2"
      shift 2
      ;;
    --notes-file)
      NOTES_FILE="$2"
      shift 2
      ;;
    --no-explicit-names)
      UPLOAD_EXPLICIT=false
      shift
      ;;
    -*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [[ -z "$TAG" ]]; then
        TAG="$1"
      else
        echo "Unexpected argument: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done

# Determine tag if not supplied
if [[ -z "$TAG" ]]; then
  # Read versionName from the source of truth
  VNAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | head -1 | sed -E 's/.*= *"([^"]+)".*/\1/')
  if [[ -z "$VNAME" ]]; then
    echo "Could not determine versionName from app/build.gradle.kts" >&2
    exit 1
  fi
  TAG="v${VNAME}"
fi

echo "Target GitHub release tag: $TAG"

APK_D="app/build/outputs/apk/downloadable/release/app-downloadable-release.apk"
APK_B="app/build/outputs/apk/bundled/release/app-bundled-release.apk"

if $DO_BUILD; then
  echo "Building signed release APKs..."
  ./gradlew :app:assembleDownloadableRelease :app:assembleBundledRelease --console=plain
fi

for f in "$APK_D" "$APK_B"; do
  if [[ ! -f "$f" ]]; then
    echo "ERROR: APK not found: $f" >&2
    echo "Run with --build or build manually first:" >&2
    echo "  ./gradlew :app:assembleDownloadableRelease :app:assembleBundledRelease" >&2
    exit 1
  fi
done

# Stage conventional names (what /latest and most links expect)
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT

cp -f "$APK_D" "$STAGE_DIR/app-release.apk"
cp -f "$APK_B" "$STAGE_DIR/app-bundled-release.apk"

CONV_D="$STAGE_DIR/app-release.apk"
CONV_B="$STAGE_DIR/app-bundled-release.apk"

UPLOAD_ARGS=("$CONV_D" "$CONV_B")

if $UPLOAD_EXPLICIT; then
  # Derive nice explicit names from the tag or versionName
  BASE="${TAG#v}"   # e.g. 1.0.0-beta2
  cp -f "$APK_D" "$STAGE_DIR/japanglify-${BASE}-downloadable.apk"
  cp -f "$APK_B" "$STAGE_DIR/japanglify-${BASE}-bundled.apk"
  UPLOAD_ARGS+=("$STAGE_DIR/japanglify-${BASE}-downloadable.apk")
  UPLOAD_ARGS+=("$STAGE_DIR/japanglify-${BASE}-bundled.apk")
fi

# Ensure gh is available
if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: gh CLI not found on PATH. Install it and run 'gh auth login'." >&2
  exit 1
fi

echo "Checking/creating prerelease $TAG ..."
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG already exists — will upload/clobber matching asset names."
else
  CREATE_ARGS=(gh release create "$TAG" --title "Japanglify ${TAG#v}" --prerelease)
  if [[ -n "$NOTES_FILE" && -f "$NOTES_FILE" ]]; then
    CREATE_ARGS+=(--notes-file "$NOTES_FILE")
  elif [[ -n "$NOTES" ]]; then
    CREATE_ARGS+=(--notes "$NOTES")
  else
    CREATE_ARGS+=(--notes "Beta build. See RELNOTES.md in the repo.")
  fi
  "${CREATE_ARGS[@]}"
fi

echo "Uploading assets to $TAG ..."
gh release upload "$TAG" "${UPLOAD_ARGS[@]}" --clobber

echo
echo "=== Pushed to GitHub beta area ==="
echo "Tag: $TAG"
echo "URL: $(gh release view "$TAG" --json url -q .url 2>/dev/null || echo "https://github.com/brianreborn/japanglify/releases/tag/$TAG")"
echo
echo "Conventional downloads:"
echo "  https://github.com/brianreborn/japanglify/releases/download/$TAG/app-release.apk"
echo "  https://github.com/brianreborn/japanglify/releases/download/$TAG/app-bundled-release.apk"
echo

if $UPLOAD_EXPLICIT; then
  BASE="${TAG#v}"
  echo "Explicit beta names:"
  echo "  https://github.com/brianreborn/japanglify/releases/download/$TAG/japanglify-${BASE}-downloadable.apk"
  echo "  https://github.com/brianreborn/japanglify/releases/download/$TAG/japanglify-${BASE}-bundled.apk"
fi

echo
echo "Assets now on the tag:"
gh release view "$TAG" --json assets -q '.assets[].name' | cat