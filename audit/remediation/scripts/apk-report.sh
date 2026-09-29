#!/usr/bin/env bash
# Prints size facts about a built APK, as Markdown.
#
# Usage: audit/remediation/scripts/apk-report.sh <app.apk> [--check]
#
# With --check the script fails when the APK contains files that the app never reads at runtime
# (translation sources) or calls Java APIs that are missing on the app's minimum API level.
set -euo pipefail

APK="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
CHECK="${2:-}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

python3 - "$APK" > "$WORK/report.md" <<'EOF'
import collections, os, sys, zipfile
apk = sys.argv[1]
z = zipfile.ZipFile(apk)
entries = [e for e in z.infolist() if not e.filename.endswith('/')]
print(f'### APK report: `{os.path.basename(apk)}`\n')
print(f'- File size: **{os.path.getsize(apk):,} bytes** ({os.path.getsize(apk) / 1e6:.1f} MB)')
print(f'- Entries: {len(entries):,}')
groups = collections.defaultdict(lambda: [0, 0, 0])
for e in entries:
    parts = e.filename.split('/')
    key = '/'.join(parts[:2]) if parts[0] == 'assets' else parts[0] if len(parts) > 1 else '(root files)'
    g = groups[key]
    g[0] += 1; g[1] += e.compress_size; g[2] += e.file_size
print('\n| Path | Files | Stored bytes | Uncompressed bytes |\n|---|---:|---:|---:|')
for key, (n, c, u) in sorted(groups.items(), key=lambda kv: -kv[1][1]):
    print(f'| `{key}` | {n:,} | {c:,} | {u:,} |')
tr = collections.defaultdict(lambda: [0, 0])
for e in entries:
    if e.filename.startswith('assets/translation/'):
        ext = os.path.splitext(e.filename)[1] or '(none)'
        tr[ext][0] += 1; tr[ext][1] += e.compress_size
print('\n| Translation asset type | Files | Stored bytes |\n|---|---:|---:|')
for ext, (n, c) in sorted(tr.items()):
    print(f'| `{ext}` | {n} | {c:,} |')
unused = sum(n for ext, (n, c) in tr.items() if ext != '.mo')
print(f'\nTRANSLATION_FILES_NOT_READ_AT_RUNTIME={unused}')
EOF

# Math.clamp exists on Android only from API 35. D8 may rewrite it into a backport; report which one the dex contains.
DEXDUMP="$(ls -d "${ANDROID_HOME:-/nonexistent}"/build-tools/*/dexdump 2>/dev/null | sort -V | tail -1 || true)"
if [ -n "$DEXDUMP" ]; then
	(cd "$WORK" && unzip -q -o "$APK" 'classes*.dex')
	DIRECT=0
	for dex in "$WORK"/classes*.dex; do
		n=$("$DEXDUMP" -d "$dex" | grep -c 'Ljava/lang/Math;.clamp' || true)
		DIRECT=$((DIRECT + n))
	done
	echo "" >> "$WORK/report.md"
	echo "- Direct calls to \`java.lang.Math.clamp\` (API 35+) in dex: $DIRECT" >> "$WORK/report.md"
	echo "MATH_CLAMP_DIRECT_CALLS=$DIRECT" >> "$WORK/report.md"
else
	echo "MATH_CLAMP_DIRECT_CALLS=unknown (dexdump not found)" >> "$WORK/report.md"
fi

cat "$WORK/report.md"

if [ "$CHECK" = "--check" ]; then
	status=0
	if ! grep -q '^TRANSLATION_FILES_NOT_READ_AT_RUNTIME=0$' "$WORK/report.md"; then
		echo "::error::The APK contains translation files that are not read at runtime (only .mo files are)." >&2
		status=1
	fi
	if grep -q '^MATH_CLAMP_DIRECT_CALLS=[1-9]' "$WORK/report.md"; then
		echo "::error::The dex calls java.lang.Math.clamp, which does not exist below API 35." >&2
		status=1
	fi
	exit $status
fi
