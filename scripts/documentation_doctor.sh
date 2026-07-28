#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
DOCS="$ROOT/docs/complete"
PASS=0
FAIL=0
pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$DOCS/Helpify_MyDealer_Complete_Technical_Documentation_v1.1.pdf" ]] && pass "technical documentation PDF" || fail "technical documentation PDF"
[[ -s "$DOCS/Helpify_MyDealer_Complete_Technical_Documentation_v1.1.docx" ]] && pass "technical documentation DOCX" || fail "technical documentation DOCX"
[[ -s "$DOCS/SHA256SUMS.txt" ]] && pass "documentation checksums" || fail "documentation checksums"
printf 'PASS=%d FAIL=%d\n' "$PASS" "$FAIL"
(( FAIL == 0 ))
