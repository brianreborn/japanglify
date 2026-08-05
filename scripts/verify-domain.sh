#!/usr/bin/env bash
# Compile and unit-test the pure-JVM :domain module — no Android SDK required.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Prefer a modern LTS JDK if the default is too new/old for Gradle.
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

if [[ ! -x ./gradlew ]]; then
  echo "gradlew missing" >&2
  exit 1
fi

if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "Fetching gradle-wrapper.jar ..."
  bash scripts/setup-gradle-wrapper.sh
fi

echo "==> JAVA_HOME=${JAVA_HOME:-"(default PATH)"}"
echo "==> :domain:test + :domain:runDemo (no Android SDK)"
./gradlew :domain:test :domain:runDemo --console=plain
echo
echo "Domain module OK."
