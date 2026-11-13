#!/usr/bin/env bash
# Prove that the public API permits explicit nullable payloads but rejects
# non-null contract violations. Run against a disposable copy, not src/main.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/src/main/java/bbb/hercules/fixtures"
cp "$root/pom.xml" "$tmp/pom.xml"
cp -R "$root/src/main/java/." "$tmp/src/main/java/"
cp "$root/src/nullness-fixtures/positive/NullableConsumer.java" \
   "$tmp/src/main/java/bbb/hercules/fixtures/"

if ! (cd "$tmp" && "$root/mvnw" -B -ntp -Dmaven.test.skip=true compile) \
        > "$tmp/positive.log" 2>&1; then
    cat "$tmp/positive.log"
    echo "ERROR: valid nullable consumer did not compile" >&2
    exit 1
fi
echo "PASS: explicitly nullable generic payloads compile"

cp "$root/src/nullness-fixtures/negative/InvalidConsumer.java" \
   "$tmp/src/main/java/bbb/hercules/fixtures/"
if (cd "$tmp" && "$root/mvnw" -B -ntp -Dmaven.test.skip=true compile) \
        > "$tmp/negative.log" 2>&1; then
    echo "ERROR: NullAway accepted invalid non-null contracts" >&2
    exit 1
fi
if ! grep -Fq '[NullAway]' "$tmp/negative.log" || \
   ! grep -Fq 'InvalidConsumer.java' "$tmp/negative.log"; then
    cat "$tmp/negative.log"
    echo "ERROR: negative fixture failed for a reason other than NullAway" >&2
    exit 1
fi
diagnostics="$(grep -F 'InvalidConsumer.java:' "$tmp/negative.log" | \
    grep -F '[NullAway]' | sed 's/^\[ERROR\] //' | sort -u | wc -l)"
if (( diagnostics < 3 )); then
    cat "$tmp/negative.log"
    echo "ERROR: expected 3 distinct NullAway contract failures, got $diagnostics" >&2
    exit 1
fi
echo "PASS: NullAway rejects all 3 invalid contracts"
grep -F '[NullAway]' "$tmp/negative.log" | sort -u
