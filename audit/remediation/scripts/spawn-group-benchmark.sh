#!/usr/bin/env bash
# Runs audit/remediation/benchmarks/SpawnGroupBenchmark.java (audit finding M7) against the
# classes compiled by jvm-tests-without-sdk.sh, which must have run first with the same work dir.
#
# Usage: audit/remediation/scripts/spawn-group-benchmark.sh [work-dir]
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
WORK="${1:-${TMPDIR:-/tmp}/andors-trail-jvm-tests}"
[ -d "$WORK/classes" ] || { echo "Run jvm-tests-without-sdk.sh $WORK first." >&2; exit 1; }
CP="$WORK/runtime-overrides:$WORK/classes:$WORK/framework-overrides:$WORK/lib/android-all-14-robolectric-10818077.jar"

mkdir -p "$WORK/benchmark-classes"
javac --release 21 -nowarn -d "$WORK/benchmark-classes" -cp "$CP" "$REPO/audit/remediation/benchmarks/SpawnGroupBenchmark.java"
java -cp "$WORK/benchmark-classes:$CP" com.gpl.rpg.AndorsTrail.model.actor.SpawnGroupBenchmark "$REPO/AndorsTrail/res"
