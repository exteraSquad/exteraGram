#!/usr/bin/env bash
# Compiles com.exteragram.messenger.inline.{math,rates,units,time,docs} with the Kotlin compiler already present in the
# Gradle cache and runs MathExpressionTests. Does not touch the Android build.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
M2="$HOME/.gradle/caches/modules-2/files-2.1"
OUT="${TMPDIR:-/tmp}/extera-inline-tests"

pick() {
    find "$M2/$1" -name "$2" ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null | sort -V | tail -1
}

version_from_gradle() {
    grep -o "kotlin-stdlib:[0-9][0-9.]*" "$ROOT/TMessagesProj/build.gradle" | head -1 | cut -d: -f2
}

VERSION="$(version_from_gradle)"
COMPILER="$(pick org.jetbrains.kotlin/kotlin-compiler-embeddable "kotlin-compiler-embeddable-$VERSION.jar")"
[ -n "$COMPILER" ] || COMPILER="$(pick org.jetbrains.kotlin/kotlin-compiler-embeddable 'kotlin-compiler-embeddable-*.jar')"
if [ -z "$COMPILER" ]; then
    echo "kotlin-compiler-embeddable not found in $M2 — run a Gradle build once to populate the cache" >&2
    exit 2
fi

STDLIB="$(pick org.jetbrains.kotlin/kotlin-stdlib "kotlin-stdlib-$VERSION.jar")"
[ -n "$STDLIB" ] || STDLIB="$(pick org.jetbrains.kotlin/kotlin-stdlib 'kotlin-stdlib-*.jar')"

CP="$COMPILER:$STDLIB"
for artifact in kotlin-script-runtime kotlin-daemon-embeddable kotlin-reflect; do
    jar="$(pick "org.jetbrains.kotlin/$artifact" "$artifact-*.jar")"
    [ -n "$jar" ] && CP="$CP:$jar"
done
for path in org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm org.jetbrains.intellij.deps/trove4j org.jetbrains/annotations; do
    jar="$(pick "$path" '*.jar')"
    [ -n "$jar" ] && CP="$CP:$jar"
done

rm -rf "$OUT"
mkdir -p "$OUT"

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -nowarn \
    -cp "$STDLIB" \
    -d "$OUT" \
    "$ROOT"/TMessagesProj/src/main/java/com/exteragram/messenger/inline/math/*.kt \
    "$ROOT"/TMessagesProj/src/main/java/com/exteragram/messenger/inline/rates/*.kt \
    "$ROOT"/TMessagesProj/src/main/java/com/exteragram/messenger/inline/units/*.kt \
    "$ROOT"/TMessagesProj/src/main/java/com/exteragram/messenger/inline/time/*.kt \
    "$ROOT"/TMessagesProj/src/main/java/com/exteragram/messenger/inline/docs/*.kt \
    "$ROOT"/Tools/inline/MathExpressionTests.kt 2>&1 | grep -v '^WARNING' || true

if [ ! -d "$OUT/com" ]; then
    echo "compilation failed" >&2
    exit 1
fi

exec java -cp "$OUT:$STDLIB" MathExpressionTestsKt
