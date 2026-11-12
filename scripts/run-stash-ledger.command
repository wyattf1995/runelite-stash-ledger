#!/bin/bash
set -euo pipefail
STASH_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
STASH_JAR="$STASH_DIR/stash-ledger-0.1.1-all.jar"
if [[ ! -f "$STASH_JAR" ]]; then
  printf '%s\n' 'Keep this launcher next to stash-ledger-0.1.1-all.jar.' >&2
  exit 1
fi
if [[ -x /Applications/RuneLite.app/Contents/Resources/jre/bin/java ]]; then
  STASH_JAVA=/Applications/RuneLite.app/Contents/Resources/jre/bin/java
elif command -v java >/dev/null 2>&1; then
  STASH_JAVA="$(command -v java)"
else
  printf '%s\n' 'RuneLite or Java 11+ is required to start this development build.' >&2
  exit 1
fi
STASH_JVM_ARGS=(-ea)
if [[ "$(uname -s)" == Darwin ]]; then
  # RuneLite's macOS UI needs the same module access as its official launcher.
  STASH_JVM_ARGS+=(--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED)
fi
exec "$STASH_JAVA" "${STASH_JVM_ARGS[@]}" -jar "$STASH_JAR" --developer-mode --debug
