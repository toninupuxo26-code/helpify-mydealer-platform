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

check_source_product() {
  local product="$1"
  local path="$2"
  local index="$ROOT/$path/index.html"
  local client="$ROOT/$path/assets/api-client.js"
  local app="$ROOT/$path/assets/app.js"
  local client_line
  local app_line

  [[ -s "$client" ]] && pass "$product API client exists" || fail "$product API client missing"
  [[ -s "$app" ]] && pass "$product application script exists" || fail "$product application script missing"
  grep -Fq './assets/api-client.js' "$index" && pass "$product HTML loads API client" || fail "$product HTML does not load API client"

  client_line="$(grep -n -m1 './assets/api-client.js' "$index" | cut -d: -f1 || true)"
  app_line="$(grep -n -m1 './assets/app.js' "$index" | cut -d: -f1 || true)"
  if [[ -n "$client_line" && -n "$app_line" && "$client_line" -lt "$app_line" ]]; then
    pass "$product API client loads before application"
  else
    fail "$product script order is invalid"
  fi

  grep -Fq 'API.login' "$app" && pass "$product login uses API" || fail "$product API login integration missing"
  grep -Fq 'API.register' "$app" && pass "$product registration uses API" || fail "$product API registration integration missing"
  grep -Fq 'API.forgotPassword' "$app" && pass "$product recovery request uses API" || fail "$product API recovery request missing"
  grep -Fq 'API.resetPassword' "$app" && pass "$product password reset uses API" || fail "$product API password reset missing"

  if grep -Eq 'password[[:space:]]*===|\.password[[:space:]]*===' "$app"; then
    fail "$product still compares passwords locally"
  else
    pass "$product has no local password comparison"
  fi

  if grep -Eq "password:[[:space:]]*'demo123'" "$app"; then
    fail "$product stores demo passwords in local user records"
  else
    pass "$product local user records contain no passwords"
  fi

  if command -v node >/dev/null 2>&1; then
    node --check "$client" >/dev/null && pass "$product API client JavaScript syntax" || fail "$product API client JavaScript syntax"
    node --check "$app" >/dev/null && pass "$product application JavaScript syntax" || fail "$product application JavaScript syntax"
  else
    warn "node unavailable; $product JavaScript syntax skipped"
  fi
}

api_request() {
  local domain="$1"
  local method="$2"
  local path="$3"
  local data="${4:-}"
  local token="${5:-}"
  local url
  local -a curl_args

  curl_args=(-fsS -X "$method" -H 'Accept: application/json')
  [[ -n "$data" ]] && curl_args+=(-H 'Content-Type: application/json' --data "$data")
  [[ -n "$token" ]] && curl_args+=(-H "Authorization: Bearer $token")

  if ss -lnt 2>/dev/null | grep -Eq '[:.]443[[:space:]]'; then
    url="https://$domain$path"
    curl_args+=(-k --resolve "$domain:443:127.0.0.1")
  else
    url="http://127.0.0.1$path"
    curl_args+=(-H "Host: $domain")
  fi

  curl "${curl_args[@]}" "$url"
}

check_runtime_product() {
  local product="$1"
  local domain="$2"
  local email="$3"
  local expected_role="$4"
  local demo_page
  local capabilities
  local login
  local token
  local me
  local forgot

  demo_page="$(api_request "$domain" GET '/demo/')"
  grep -Fq './assets/api-client.js' <<<"$demo_page" && pass "$product deployed web loads API client" || fail "$product deployed web API client missing"

  capabilities="$(api_request "$domain" GET '/api/auth/capabilities')"
  jq -e --arg product "$product" '.status == "ok" and .product == $product and .authentication == "bearer-token"' <<<"$capabilities" >/dev/null \
    && pass "$product authentication capabilities" || fail "$product authentication capabilities"

  login="$(api_request "$domain" POST '/api/auth/login' "{\"email\":\"$email\",\"password\":\"demo123\"}")"
  token="$(jq -r '.token // empty' <<<"$login")"
  [[ ${#token} -ge 32 ]] && pass "$product login returns bearer token" || fail "$product login token missing"
  jq -e --arg role "$expected_role" '.user.role == $role' <<<"$login" >/dev/null \
    && pass "$product login returns expected role" || fail "$product login role mismatch"

  if [[ -n "$token" ]]; then
    me="$(api_request "$domain" GET '/api/auth/me' '' "$token")"
    jq -e --arg email "$email" '.user.email == $email' <<<"$me" >/dev/null \
      && pass "$product authenticated profile" || fail "$product authenticated profile"

    api_request "$domain" POST '/api/auth/logout' '' "$token" | jq -e '.message != null' >/dev/null \
      && pass "$product logout revokes token" || fail "$product logout"
  fi

  forgot="$(api_request "$domain" POST '/api/auth/password/forgot' "{\"email\":\"$email\"}")"
  jq -e '.demo_reset_code | type == "string" and test("^[0-9]{6}$")' <<<"$forgot" >/dev/null \
    && pass "$product test recovery code" || fail "$product test recovery code"
}

check_source() {
  check_source_product helpify implementation/helpify/web-app/demo
  check_source_product mydealer implementation/mydealer/web-app/demo
}

check_runtime() {
  command -v curl >/dev/null || { fail 'curl unavailable'; return; }
  command -v jq >/dev/null || { fail 'jq unavailable'; return; }
  check_runtime_product helpify helpsiffyy.app customer@example.test customer
  check_runtime_product mydealer mydealers.app buyer@example.test buyer
}

case "$MODE" in
  source) check_source ;;
  runtime) check_runtime ;;
  all)
    check_source
    if [[ $EUID -eq 0 ]]; then
      check_runtime
    else
      warn 'runtime checks skipped without root'
    fi
    ;;
  *) printf 'Usage: %s [source|runtime|all]\n' "$0" >&2; exit 64 ;;
esac

printf 'PASS=%s WARN=%s FAIL=%s\n' "$PASS" "$WARN" "$FAIL"
((FAIL == 0))
