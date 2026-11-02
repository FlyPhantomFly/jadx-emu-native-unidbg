#!/usr/bin/env bash
# Test, build and install the backend as the only jadx-emu-native-unidbg jar in the jadx-emu extensions folder.
# Usage: scripts/deploy.sh [version]
# The jadx-emu engine (EMU_VERSION, default 0.3.0) has to be in ~/.m2 already.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXT="$HOME/.config/jadx/plugins-data/jadx-emu/extensions"
cd "$ROOT"
[ -x ./gradlew ] || { echo "no ./gradlew in $ROOT" >&2; exit 1; }
if [ -n "${1:-}" ]; then export VERSION="$1"; fi
./gradlew --no-daemon --console=plain clean test shadowJar
JAR="$(ls -t build/libs/jadx-emu-native-unidbg-*.jar | grep -v -- '-thin' | head -n 1)"
mkdir -p "$EXT"
rm -f "$EXT"/jadx-emu-native-unidbg-*.jar
cp "$JAR" "$EXT/"
echo "installed: $EXT/$(basename "$JAR") ($(du -h "$JAR" | cut -f1))"
[ "$(wc -c < "$JAR")" -gt 5000000 ] || echo "!! the jar is small. unidbg doesn't look bundled, and it won't run without it."
ls -la "$EXT"
echo "Restart jadx. The log should show: registered native backend 'unidbg'"
