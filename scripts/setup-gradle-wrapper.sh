#!/usr/bin/env bash
# Download Gradle wrapper JAR + generate a local Gradle dist if needed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

WRAPPER_DIR="$ROOT/gradle/wrapper"
JAR="$WRAPPER_DIR/gradle-wrapper.jar"
mkdir -p "$WRAPPER_DIR"

if [[ ! -f "$JAR" ]]; then
  echo "Downloading gradle-wrapper.jar ..."
  # Pin to the Gradle 8.9 release tree so the jar matches gradle-wrapper.properties
  curl -fsSL -o "$JAR" \
    "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar" \
  || curl -fsSL -o "$JAR" \
    "https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
fi

if [[ ! -x "$ROOT/gradlew" ]]; then
  echo "gradlew should already exist; check scripts/." >&2
fi

chmod +x "$ROOT/gradlew" 2>/dev/null || true
echo "Wrapper ready: $JAR"
