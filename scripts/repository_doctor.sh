#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
PASS=0
WARN=0
FAIL=0
TMP_SECRET_REPORT="$(mktemp)"
trap 'rm -f "$TMP_SECRET_REPORT"' EXIT

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

cd "$ROOT"

[[ -f README.md ]] && pass "README" || fail "README"
[[ -f LICENSE.md ]] && pass "license" || fail "license"
[[ -f SECURITY.md ]] && pass "security policy" || fail "security policy"
[[ -f CONTRIBUTING.md ]] && pass "contribution guide" || fail "contribution guide"
[[ -f VERSION ]] && pass "VERSION" || fail "VERSION"

if git diff --check --cached >/dev/null 2>&1 && git diff --check >/dev/null 2>&1; then
  pass "Git whitespace"
else
  fail "Git whitespace"
fi

# Configuration templates such as .env.example are expected and must remain tracked.
secret_files="$(
  git ls-files \
    | grep -E '(^|/)(\.env$|\.env\.(local|production|staging)$|id_rsa|id_ed25519|credentials\.json|.*\.(pem|key|p12|jks|keystore|sql|dump|backup)$)' \
    | grep -Ev '(^|/)\.env\.example$' \
    || true
)"

if [[ -z "$secret_files" ]]; then
  pass "no tracked secret or backup files"
else
  fail "tracked sensitive files detected"
  printf '%s\n' "$secret_files"
fi

# Scan only high-confidence secret formats. Ordinary source variables named
# password, token or secret are not credentials by themselves.
high_confidence_patterns=(
  '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
  'AKIA[0-9A-Z]{16}'
  'ASIA[0-9A-Z]{16}'
  'gh[pousr]_[A-Za-z0-9_]{30,}'
  'github_pat_[A-Za-z0-9_]{40,}'
  'xox[baprs]-[A-Za-z0-9-]{20,}'
  'sk_live_[A-Za-z0-9]{20,}'
)

for pattern in "${high_confidence_patterns[@]}"; do
  git grep -nEI "$pattern" -- \
    ':!*.example' \
    ':!scripts/repository_doctor.sh' \
    >> "$TMP_SECRET_REPORT" 2>/dev/null || true
done

if [[ -s "$TMP_SECRET_REPORT" ]]; then
  fail "possible embedded secrets"
  sort -u "$TMP_SECRET_REPORT"
else
  pass "no obvious embedded secrets"
fi

for script in scripts/*.sh patches/*/patch.sh; do
  [[ -e "$script" ]] || continue
  if ! bash -n "$script"; then
    fail "Bash syntax: $script"
  fi
done

if (( FAIL == 0 )); then
  pass "Bash syntax"
fi

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
