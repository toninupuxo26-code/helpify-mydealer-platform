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
  local product backend admin_web
  for product in helpify mydealer; do
    backend="$ROOT/implementation/$product/backend"
    admin_web="$ROOT/implementation/$product/admin/web"
    [[ -f "$backend/app/Http/Controllers/Api/AdminController.php" ]] && pass "$product admin controller" || fail "$product admin controller missing"
    [[ -f "$backend/app/Http/Middleware/RequireAdminRole.php" ]] && pass "$product admin role middleware" || fail "$product admin role middleware missing"
    [[ -f "$backend/database/migrations/2021_11_01_000000_add_demo_administrator_user.php" ]] && pass "$product admin migration" || fail "$product admin migration missing"
    grep -Fq "prefix('admin')" "$backend/routes/api.php" && pass "$product admin route group" || fail "$product admin route group missing"
    grep -Fq "registration_roles" "$backend/config/demo.php" && pass "$product public registration role separation" || fail "$product public registration role separation missing"
    [[ -f "$admin_web/index.html" && -f "$admin_web/assets/admin.js" && -f "$admin_web/assets/api-client.js" && -f "$admin_web/assets/admin.css" ]] \
      && pass "$product admin web assets" || fail "$product admin web assets missing"
    grep -Fq "${product}-admin-web" "$admin_web/index.html" && pass "$product admin web marker" || fail "$product admin web marker missing"
    grep -Fq "API.dashboard" "$admin_web/assets/admin.js" && pass "$product admin API integration" || fail "$product admin API integration missing"

    if command -v php >/dev/null 2>&1; then
      php -l "$backend/app/Http/Controllers/Api/AdminController.php" >/dev/null \
        && php -l "$backend/app/Http/Middleware/RequireAdminRole.php" >/dev/null \
        && php -l "$backend/database/migrations/2021_11_01_000000_add_demo_administrator_user.php" >/dev/null \
        && php -l "$backend/routes/api.php" >/dev/null \
        && pass "$product admin PHP syntax" || fail "$product admin PHP syntax"
    else
      warn "php unavailable; $product admin PHP syntax skipped"
    fi

    if command -v node >/dev/null 2>&1; then
      node --check "$admin_web/assets/api-client.js" >/dev/null \
        && node --check "$admin_web/assets/admin.js" >/dev/null \
        && pass "$product admin JavaScript syntax" || fail "$product admin JavaScript syntax"
    else
      warn "node unavailable; $product admin JavaScript syntax skipped"
    fi
  done
}

login(){
  local base="$1" email="$2"
  curl -fsS -H 'Content-Type: application/json' -X POST "$base/api/auth/login" \
    --data "{\"email\":\"$email\",\"password\":\"demo123\"}" | jq -r '.token // empty'
}

check_product_runtime(){
  local product="$1" base="$2" normal_email="$3" list_path="$4"
  local admin_token normal_token body http

  body="$(curl -fsS "$base/api/auth/capabilities" 2>/dev/null || true)"
  jq -e '.administrator_console == true and (.roles | index("admin") | not)' <<<"$body" >/dev/null \
    && pass "$product capabilities hide admin from registration" || fail "$product capabilities hide admin from registration"

  admin_token="$(login "$base" admin@example.test)"
  [[ -n "$admin_token" ]] && pass "$product administrator login" || { fail "$product administrator login"; return; }

  body="$(curl -fsS -H "Authorization: Bearer $admin_token" "$base/api/admin/dashboard" 2>/dev/null || true)"
  jq -e --arg product "$product" '.product == $product and (.statistics.users_total | type == "number")' <<<"$body" >/dev/null \
    && pass "$product administrator dashboard" || fail "$product administrator dashboard"

  body="$(curl -fsS -H "Authorization: Bearer $admin_token" "$base/api/admin/users" 2>/dev/null || true)"
  jq -e '.users[] | select(.email == "admin@example.test" and .role == "admin")' <<<"$body" >/dev/null \
    && pass "$product administrator user register" || fail "$product administrator user register"

  body="$(curl -fsS -H "Authorization: Bearer $admin_token" "$base$list_path" 2>/dev/null || true)"
  jq -e 'type == "object"' <<<"$body" >/dev/null \
    && pass "$product administrator domain listing" || fail "$product administrator domain listing"

  normal_token="$(login "$base" "$normal_email")"
  [[ -n "$normal_token" ]] || { fail "$product normal user login for access test"; return; }
  http="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $normal_token" "$base/api/admin/dashboard" 2>/dev/null || true)"
  [[ "$http" == '403' ]] && pass "$product non-admin access rejected" || fail "$product non-admin access rejected"

  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $admin_token" -X POST "$base/api/auth/logout" --data '{}' >/dev/null || true
  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $normal_token" -X POST "$base/api/auth/logout" --data '{}' >/dev/null || true
}

check_runtime(){
  [[ $EUID -eq 0 ]] || { warn 'runtime checks skipped without root'; return; }
  command -v curl >/dev/null || { fail 'curl unavailable'; return; }
  command -v jq >/dev/null || { fail 'jq unavailable'; return; }
  check_product_runtime helpify http://127.0.0.1:18081 customer@example.test /api/admin/tasks
  check_product_runtime mydealer http://127.0.0.1:18082 buyer@example.test /api/admin/products
}

check_web(){
  local body
  [[ $EUID -eq 0 ]] || { warn 'web checks skipped without root'; return; }

  body="$(curl -kfsS --resolve helpsiffyy.app:443:127.0.0.1 https://helpsiffyy.app/admin/ 2>/dev/null || true)"
  grep -Fq 'helpify-admin-web' <<<"$body" && pass 'Helpify deployed admin web marker' || fail 'Helpify deployed admin web marker'
  [[ -f /var/www/helpsiffyy.app/current/admin/assets/admin.js ]] && pass 'Helpify deployed admin assets' || fail 'Helpify deployed admin assets'

  body="$(curl -kfsS --resolve mydealers.app:443:127.0.0.1 https://mydealers.app/admin/ 2>/dev/null || true)"
  grep -Fq 'mydealer-admin-web' <<<"$body" && pass 'MyDealer deployed admin web marker' || fail 'MyDealer deployed admin web marker'
  [[ -f /var/www/mydealers.app/current/admin/assets/admin.js ]] && pass 'MyDealer deployed admin assets' || fail 'MyDealer deployed admin assets'
}

case "$MODE" in
  source) check_source ;;
  runtime) check_runtime ;;
  web) check_web ;;
  all) check_source; check_runtime; check_web ;;
  *) printf 'Usage: %s [source|runtime|web|all]\n' "$0" >&2; exit 64 ;;
esac

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
