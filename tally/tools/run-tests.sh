#!/usr/bin/env bash
# Plain-JVM tests for the logic that has no Android in it (Rules.kt, Palettes.kt). Needs kotlinc 2.x and a JDK.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd); OUT=${TMPDIR:-/tmp}/tally-tests
rm -rf "$OUT"; mkdir -p "$OUT"
J=$ROOT/app/src/main/java/com/killswitch/tally
kotlinc -nowarn -d "$OUT" "$J/Palettes.kt" "$J/Rules.kt" "$ROOT/tools/RulesTest.kt"
KLIB=$(dirname "$(command -v kotlinc)")/../lib/kotlin-stdlib.jar
java -cp "$OUT:$KLIB" RulesTestKt "$ROOT/tools/vectors.txt"
