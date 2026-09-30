#!/usr/bin/env bash
# Runs the content and translation checks of the audit and fails when they report a problem that
# is not in the reviewed baseline (audit findings M9, L5, L14).
#
# The baselines in audit/remediation/baselines/ list the problems known at the time of the audit
# remediation. Fixing one of them makes this script print it as fixed; then update the baseline:
#   audit/remediation/scripts/check-content.sh --update
#
# Usage: audit/remediation/scripts/check-content.sh [--update]
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
BASELINES="$REPO/audit/remediation/baselines"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
status=0

check() { # <checker> <baseline name>
	local current="$WORK/$2.txt" baseline="$BASELINES/$2.txt"
	python3 "$REPO/$1" > "$current"
	if [ "${UPDATE:-}" = 1 ]; then
		cp "$current" "$baseline"
		echo "$2: baseline updated"
		return
	fi
	# Problems are the "  - " lines; the counts in the headings follow from them.
	local new fixed
	new="$(comm -13 <(grep '^  - ' "$baseline" | sort -u) <(grep '^  - ' "$current" | sort -u))"
	fixed="$(comm -23 <(grep '^  - ' "$baseline" | sort -u) <(grep '^  - ' "$current" | sort -u))"
	echo "$2: $(grep -c '^  - ' "$current") problems, $(printf '%s' "$new" | grep -c . || true) not in the baseline, $(printf '%s' "$fixed" | grep -c . || true) fixed since the baseline"
	if [ -n "$fixed" ]; then
		echo "Fixed since the baseline (run with --update to record):"
		echo "$fixed"
	fi
	if [ -n "$new" ]; then
		echo "New problems:"
		echo "$new"
		status=1
	fi
}

[ "${1:-}" = "--update" ] && UPDATE=1
check audit/check_content.py content
check audit/check_translation_formats.py translation-formats
exit $status
