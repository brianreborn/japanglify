#!/usr/bin/env bash
# Execute static analysis build target across all Japanglify modules.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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

if [[ ! -x ./gradlew ]]; then
  echo "gradlew missing" >&2
  exit 1
fi

echo "==> Running static analysis target: ./gradlew staticAnalysis"
./gradlew staticAnalysis --console=plain
echo
echo "==> Static analysis passed cleanly."
