#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
MODE="${1:-all}"
PASS=0
WARN=0
FAIL=0

pass(){ PASS=$((PASS+1)); printf 'PASS  %s\n' "$*"; }
warn(){ WARN=$((WARN+1)); printf 'WARN  %s\n' "$*"; }
fail(){ FAIL=$((FAIL+1)); printf 'FAIL  %s\n' "$*"; }

check_source(){
  local app product expected_version
  while read -r app product expected_version; do
    [[ -f "$ROOT/$app/app/Http/Controllers/Api/AuthController.php" ]] && pass "$product auth controller" || fail "$product auth controller missing"
    [[ -f "$ROOT/$app/app/Http/Middleware/AuthenticateApiToken.php" ]] && pass "$product token middleware" || fail "$product token middleware missing"
    [[ -f "$ROOT/$app/database/migrations/2021_09_15_000000_create_demo_authentication_tables.php" ]] && pass "$product auth migration" || fail "$product auth migration missing"
    grep -Fq "Route::post('/login'" "$ROOT/$app/routes/api.php" && pass "$product login route" || fail "$product login route missing"
    grep -Fq "'application_version' => '$expected_version'" "$ROOT/$app/routes/api.php" && pass "$product API version $expected_version" || fail "$product API version mismatch"
    if command -v php >/dev/null 2>&1; then
      while IFS= read -r file; do
        php -l "$file" >/dev/null || { fail "$product PHP syntax: $file"; return; }
      done < <(find "$ROOT/$app/app/Http" "$ROOT/$app/database/migrations" "$ROOT/$app/routes" -type f -name '*.php' -print)
      pass "$product authentication PHP syntax"
    else
      warn "php unavailable; $product authentication PHP syntax skipped"
    fi
  done <<DATA
implementation/helpify/backend helpify 0.8.0
implementation/mydealer/backend mydealer 0.8.0
DATA
}

post_json(){
  local base="$1" path="$2" body="$3"
  curl -fsS -H 'Content-Type: application/json' -X POST "${base}${path}" --data "$body"
}

check_product(){
  local product="$1" base="$2" email="$3" expected_role="$4"
  local body token code http

  body="$(curl -fsS "${base}/api/auth/capabilities" 2>/dev/null || true)"
  grep -q "\"product\":\"${product}\"" <<<"$body" && pass "$product capabilities endpoint" || { fail "$product capabilities endpoint"; return; }
  grep -q "\"${expected_role}\"" <<<"$body" && pass "$product role capability" || fail "$product role capability"

  body="$(post_json "$base" '/api/auth/login' "{\"email\":\"${email}\",\"password\":\"demo123\"}" 2>/dev/null || true)"
  token="$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' <<<"$body" | head -1)"
  [[ -n "$token" ]] && pass "$product seeded login" || { fail "$product seeded login"; return; }

  body="$(curl -fsS -H "Authorization: Bearer ${token}" "${base}/api/auth/me" 2>/dev/null || true)"
  grep -q "\"email\":\"${email}\"" <<<"$body" && pass "$product authenticated profile" || fail "$product authenticated profile"

  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer ${token}" -X POST "${base}/api/auth/logout" --data '{}' >/dev/null 2>&1 || true
  http="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${token}" "${base}/api/auth/me" 2>/dev/null || true)"
  [[ "$http" == '401' ]] && pass "$product logout revokes token" || fail "$product logout revokes token"

  body="$(post_json "$base" '/api/auth/password/forgot' "{\"email\":\"${email}\"}" 2>/dev/null || true)"
  code="$(sed -n 's/.*"demo_reset_code":"\([0-9][0-9]*\)".*/\1/p' <<<"$body" | head -1)"
  [[ "$code" =~ ^[0-9]{6}$ ]] && pass "$product test recovery code" || { fail "$product test recovery code"; return; }

  body="$(post_json "$base" '/api/auth/password/reset' "{\"email\":\"${email}\",\"code\":\"${code}\",\"password\":\"demo123\"}" 2>/dev/null || true)"
  grep -q 'Password was updated' <<<"$body" && pass "$product password reset" || fail "$product password reset"

  http="$(curl -sS -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -X POST "${base}/api/auth/login" --data "{\"email\":\"${email}\",\"password\":\"wrong-password\"}" 2>/dev/null || true)"
  [[ "$http" == '401' ]] && pass "$product invalid credentials rejected" || fail "$product invalid credentials rejected"
}

check_runtime(){
  [[ $EUID -eq 0 ]] || { warn 'runtime checks skipped without root'; return; }
  command -v curl >/dev/null 2>&1 || { fail 'curl unavailable'; return; }

  check_product helpify http://127.0.0.1:18081 customer@example.test customer
  check_product mydealer http://127.0.0.1:18082 buyer@example.test buyer
}

case "$MODE" in
  source) check_source ;;
  runtime) check_runtime ;;
  all) check_source; check_runtime ;;
  *) printf 'Usage: %s [source|runtime|all]\n' "$0" >&2; exit 64 ;;
esac

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
