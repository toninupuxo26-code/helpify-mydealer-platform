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
  local controller="$ROOT/implementation/mydealer/backend/app/Http/Controllers/Api/MarketController.php"
  local migration="$ROOT/implementation/mydealer/backend/database/migrations/2021_10_15_000000_create_mydealer_workflow_tables.php"
  local routes="$ROOT/implementation/mydealer/backend/routes/api.php"
  local client="$ROOT/implementation/mydealer/web-app/demo/assets/api-client.js"
  local app="$ROOT/implementation/mydealer/web-app/demo/assets/app.js"

  [[ -f "$controller" ]] && pass 'MyDealer market controller' || fail 'MyDealer market controller missing'
  [[ -f "$migration" ]] && pass 'MyDealer workflow migration' || fail 'MyDealer workflow migration missing'
  grep -Fq "prefix('market')" "$routes" && pass 'MyDealer market route group' || fail 'MyDealer market route group missing'
  grep -Fq "Route::post('/orders/checkout'" "$routes" && pass 'MyDealer checkout route' || fail 'MyDealer checkout route missing'
  grep -Fq "Route::post('/orders/{orderId}/messages'" "$routes" && pass 'MyDealer order chat route' || fail 'MyDealer order chat route missing'
  grep -Fq 'listProducts: function' "$client" && pass 'MyDealer web market API client' || fail 'MyDealer web market API client missing'
  grep -Fq 'API.createProduct' "$app" && pass 'MyDealer web product API integration' || fail 'MyDealer web product API integration missing'
  grep -Fq 'API.addCartItem' "$app" && pass 'MyDealer web cart API integration' || fail 'MyDealer web cart API integration missing'
  grep -Fq 'API.checkout' "$app" && pass 'MyDealer web checkout API integration' || fail 'MyDealer web checkout API integration missing'
  grep -Fq 'API.sendOrderMessage' "$app" && pass 'MyDealer web order chat integration' || fail 'MyDealer web order chat integration missing'

  if grep -Eq 'products:[[:space:]]*\[|orders:[[:space:]]*\[|db\.orders\.unshift|db\.products\.unshift' "$app"; then
    fail 'MyDealer web still contains browser-local marketplace seed data'
  else
    pass 'MyDealer marketplace data is not seeded in browser storage'
  fi

  if command -v php >/dev/null 2>&1; then
    php -l "$controller" >/dev/null && php -l "$migration" >/dev/null && php -l "$routes" >/dev/null \
      && pass 'MyDealer workflow PHP syntax' || fail 'MyDealer workflow PHP syntax'
  else
    warn 'php unavailable; MyDealer workflow PHP syntax skipped'
  fi

  if command -v node >/dev/null 2>&1; then
    node --check "$client" >/dev/null && node --check "$app" >/dev/null \
      && pass 'MyDealer workflow JavaScript syntax' || fail 'MyDealer workflow JavaScript syntax'
  else
    warn 'node unavailable; MyDealer workflow JavaScript syntax skipped'
  fi
}

post_json(){
  local url="$1" token="$2" body="$3"
  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $token" -X POST "$url" --data "$body"
}

login(){
  local email="$1"
  curl -fsS -H 'Content-Type: application/json' -X POST http://127.0.0.1:18082/api/auth/login \
    --data "{\"email\":\"$email\",\"password\":\"demo123\"}" | jq -r '.token // empty'
}

check_runtime(){
  local buyer_token vendor_token body product_id order_id message_id name

  [[ $EUID -eq 0 ]] || { warn 'runtime checks skipped without root'; return; }
  command -v curl >/dev/null || { fail 'curl unavailable'; return; }
  command -v jq >/dev/null || { fail 'jq unavailable'; return; }

  body="$(curl -fsS http://127.0.0.1:18082/api/health 2>/dev/null || true)"
  jq -e '.status == "ok" and .application_version == "0.8.0"' <<<"$body" >/dev/null \
    && pass 'MyDealer workflow API health 0.8.0' || { fail 'MyDealer workflow API health 0.8.0'; return; }

  buyer_token="$(login buyer@example.test)"
  vendor_token="$(login vendor@example.test)"
  [[ -n "$buyer_token" ]] && pass 'MyDealer buyer workflow login' || { fail 'MyDealer buyer workflow login'; return; }
  [[ -n "$vendor_token" ]] && pass 'MyDealer vendor workflow login' || { fail 'MyDealer vendor workflow login'; return; }

  name="Doctor product $(date -u +%Y%m%dT%H%M%SZ)"
  body="$(post_json http://127.0.0.1:18082/api/market/products "$vendor_token" "{\"name\":\"$name\",\"category\":\"Деликатесы\",\"price\":15,\"unit\":\"1 шт.\",\"emoji\":\"🌿\",\"description\":\"тестовый проверка каталога.\"}")"
  product_id="$(jq -r '.product.id // empty' <<<"$body")"
  [[ "$product_id" =~ ^[0-9]+$ ]] && jq -e '.product.status == "moderation"' <<<"$body" >/dev/null \
    && pass 'MyDealer vendor product creation' || { fail 'MyDealer vendor product creation'; return; }

  body="$(post_json "http://127.0.0.1:18082/api/market/products/$product_id/publish" "$vendor_token" '{}')"
  jq -e '.product.status == "published"' <<<"$body" >/dev/null \
    && pass 'MyDealer test product moderation' || { fail 'MyDealer test product moderation'; return; }

  body="$(post_json http://127.0.0.1:18082/api/market/cart/items "$buyer_token" "{\"product_id\":$product_id,\"quantity\":2}")"
  jq -e --argjson product "$product_id" '.items[] | select(.productId == $product and .quantity >= 2)' <<<"$body" >/dev/null \
    && pass 'MyDealer buyer cart persistence' || { fail 'MyDealer buyer cart persistence'; return; }

  body="$(post_json http://127.0.0.1:18082/api/market/orders/checkout "$buyer_token" '{}')"
  order_id="$(jq -r '.orders[0].id // empty' <<<"$body")"
  [[ "$order_id" =~ ^[0-9]+$ ]] && jq -e '.orders[0].status == "new"' <<<"$body" >/dev/null \
    && pass 'MyDealer buyer checkout' || { fail 'MyDealer buyer checkout'; return; }

  body="$(curl -fsS -H "Authorization: Bearer $vendor_token" http://127.0.0.1:18082/api/market/orders)"
  jq -e --argjson order "$order_id" '.orders[] | select(.id == $order and .status == "new")' <<<"$body" >/dev/null \
    && pass 'MyDealer vendor receives order' || fail 'MyDealer vendor receives order'

  body="$(post_json "http://127.0.0.1:18082/api/market/orders/$order_id/status" "$vendor_token" '{"status":"confirmed"}')"
  jq -e '.order.status == "confirmed"' <<<"$body" >/dev/null \
    && pass 'MyDealer vendor confirms order' || fail 'MyDealer vendor confirms order'

  body="$(post_json "http://127.0.0.1:18082/api/market/orders/$order_id/messages" "$buyer_token" '{"text":"Сообщение проверки покупателя"}')"
  message_id="$(jq -r '.message_id // empty' <<<"$body")"
  [[ "$message_id" =~ ^[0-9]+$ ]] && pass 'MyDealer buyer sends order message' || fail 'MyDealer buyer sends order message'

  body="$(curl -fsS -H "Authorization: Bearer $vendor_token" "http://127.0.0.1:18082/api/market/orders/$order_id/messages")"
  jq -e '.messages | length >= 2' <<<"$body" >/dev/null && pass 'MyDealer vendor reads order messages' || fail 'MyDealer vendor reads order messages'

  body="$(post_json "http://127.0.0.1:18082/api/market/orders/$order_id/status" "$vendor_token" '{"status":"completed"}')"
  jq -e '.order.status == "completed"' <<<"$body" >/dev/null \
    && pass 'MyDealer vendor completes order' || fail 'MyDealer vendor completes order'

  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $buyer_token" -X POST http://127.0.0.1:18082/api/auth/logout --data '{}' >/dev/null || true
  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $vendor_token" -X POST http://127.0.0.1:18082/api/auth/logout --data '{}' >/dev/null || true
}

check_web(){
  local body
  [[ $EUID -eq 0 ]] || { warn 'web checks skipped without root'; return; }
  body="$(curl -kfsS --resolve mydealers.app:443:127.0.0.1 https://mydealers.app/demo/ 2>/dev/null || true)"
  grep -Fq 'mydealer-web-demo' <<<"$body" && pass 'MyDealer deployed marketplace web marker' || fail 'MyDealer deployed marketplace web marker'
  [[ -f /var/www/mydealers.app/current/demo/assets/api-client.js ]] \
    && grep -Fq 'listProducts: function' /var/www/mydealers.app/current/demo/assets/api-client.js \
    && pass 'MyDealer deployed market API client' || fail 'MyDealer deployed market API client'
  [[ -f /var/www/mydealers.app/current/demo/assets/app.js ]] \
    && grep -Fq 'MYSQL DEMO' /var/www/mydealers.app/current/demo/assets/app.js \
    && pass 'MyDealer deployed MySQL workflow UI' || fail 'MyDealer deployed MySQL workflow UI'
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
