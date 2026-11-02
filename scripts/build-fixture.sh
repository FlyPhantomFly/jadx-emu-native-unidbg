#!/usr/bin/env bash
# Rebuild src/test/resources/libjnitest.so from fixtures/jnitest.S. Needs the aarch64 GNU binutils (aarch64-linux-gnu-as and -ld).
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"
aarch64-linux-gnu-as -o "$T/jnitest.o" "$HERE/fixtures/jnitest.S"
aarch64-linux-gnu-ld -shared --hash-style=both -z noexecstack -soname libjnitest.so -o "$HERE/src/test/resources/libjnitest.so" "$T/jnitest.o"
rm -rf "$T"
echo "built $HERE/src/test/resources/libjnitest.so ($(wc -c < "$HERE/src/test/resources/libjnitest.so") bytes)"
