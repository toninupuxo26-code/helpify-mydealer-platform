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
  local controller="$ROOT/implementation/helpify/backend/app/Http/Controllers/Api/TaskController.php"
  local migration="$ROOT/implementation/helpify/backend/database/migrations/2021_10_01_000000_create_helpify_workflow_tables.php"
  local routes="$ROOT/implementation/helpify/backend/routes/api.php"
  local client="$ROOT/implementation/helpify/web-app/demo/assets/api-client.js"
  local app="$ROOT/implementation/helpify/web-app/demo/assets/app.js"

  [[ -f "$controller" ]] && pass 'Helpify task controller' || fail 'Helpify task controller missing'
  [[ -f "$migration" ]] && pass 'Helpify workflow migration' || fail 'Helpify workflow migration missing'
  grep -Fq "prefix('work')" "$routes" && pass 'Helpify workflow route group' || fail 'Helpify workflow route group missing'
  grep -Fq "Route::post('/tasks/{taskId}/offers'" "$routes" && pass 'Helpify offer route' || fail 'Helpify offer route missing'
  grep -Fq "Route::post('/tasks/{taskId}/messages'" "$routes" && pass 'Helpify message route' || fail 'Helpify message route missing'
  grep -Fq 'listTasks: function' "$client" && pass 'Helpify web workflow API client' || fail 'Helpify web workflow API client missing'
  grep -Fq 'API.createTask' "$app" && pass 'Helpify web task API integration' || fail 'Helpify web task API integration missing'
  grep -Fq 'API.createOffer' "$app" && pass 'Helpify web offer API integration' || fail 'Helpify web offer API integration missing'
  grep -Fq 'API.sendMessage' "$app" && pass 'Helpify web message API integration' || fail 'Helpify web message API integration missing'
  if grep -Eq 'db\.tasks|localStorage[^\n]*tasks|tasks:[[:space:]]*\[' "$app"; then
    fail 'Helpify web still contains local task database markers'
  else
    pass 'Helpify workflow is not persisted as local task data'
  fi

  if command -v php >/dev/null 2>&1; then
    php -l "$controller" >/dev/null && php -l "$migration" >/dev/null && php -l "$routes" >/dev/null \
      && pass 'Helpify workflow PHP syntax' || fail 'Helpify workflow PHP syntax'
  else
    warn 'php unavailable; Helpify workflow PHP syntax skipped'
  fi

  if command -v node >/dev/null 2>&1; then
    node --check "$client" >/dev/null && node --check "$app" >/dev/null \
      && pass 'Helpify workflow JavaScript syntax' || fail 'Helpify workflow JavaScript syntax'
  else
    warn 'node unavailable; Helpify workflow JavaScript syntax skipped'
  fi
}

post_json(){
  local url="$1" token="$2" body="$3"
  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $token" -X POST "$url" --data "$body"
}

login(){
  local email="$1"
  curl -fsS -H 'Content-Type: application/json' -X POST http://127.0.0.1:18081/api/auth/login \
    --data "{\"email\":\"$email\",\"password\":\"demo123\"}" | jq -r '.token // empty'
}

check_runtime(){
  local customer_token contractor_token body task_id offer_id message_id title

  [[ $EUID -eq 0 ]] || { warn 'runtime checks skipped without root'; return; }
  command -v curl >/dev/null || { fail 'curl unavailable'; return; }
  command -v jq >/dev/null || { fail 'jq unavailable'; return; }

  body="$(curl -fsS http://127.0.0.1:18081/api/health 2>/dev/null || true)"
  jq -e '.status == "ok" and .application_version == "0.8.0"' <<<"$body" >/dev/null \
    && pass 'Helpify workflow API health 0.8.0' || { fail 'Helpify workflow API health 0.8.0'; return; }

  customer_token="$(login customer@example.test)"
  contractor_token="$(login contractor@example.test)"
  [[ -n "$customer_token" ]] && pass 'Helpify customer workflow login' || { fail 'Helpify customer workflow login'; return; }
  [[ -n "$contractor_token" ]] && pass 'Helpify contractor workflow login' || { fail 'Helpify contractor workflow login'; return; }

  body="$(curl -fsS -H "Authorization: Bearer $customer_token" http://127.0.0.1:18081/api/work/tasks)"
  jq -e '.tasks | type == "array"' <<<"$body" >/dev/null && pass 'Helpify customer task list' || fail 'Helpify customer task list'

  title="Doctor task $(date -u +%Y%m%dT%H%M%SZ)"
  body="$(post_json http://127.0.0.1:18081/api/work/tasks "$customer_token" "{\"title\":\"$title\",\"category\":\"Электрик\",\"address\":\"Рига, тест\",\"description\":\"тестовый проверка рабочего сценария.\",\"budget\":60}")"
  task_id="$(jq -r '.task.id // empty' <<<"$body")"
  [[ "$task_id" =~ ^[0-9]+$ ]] && pass 'Helpify task creation' || { fail 'Helpify task creation'; return; }

  body="$(post_json "http://127.0.0.1:18081/api/work/tasks/$task_id/offers" "$contractor_token" '{"price":55,"distance":"1,5 км"}')"
  offer_id="$(jq -r '.offer.id // empty' <<<"$body")"
  [[ "$offer_id" =~ ^[0-9]+$ ]] && pass 'Helpify contractor offer creation' || { fail 'Helpify contractor offer creation'; return; }

  body="$(post_json "http://127.0.0.1:18081/api/work/tasks/$task_id/offers/$offer_id/select" "$customer_token" '{}')"
  jq -e '.task.status == "assigned" and .task.selectedOfferId != null' <<<"$body" >/dev/null \
    && pass 'Helpify customer selects offer' || fail 'Helpify customer selects offer'

  body="$(post_json "http://127.0.0.1:18081/api/work/tasks/$task_id/messages" "$customer_token" '{"text":"Сообщение проверки заказчика"}')"
  message_id="$(jq -r '.message_id // empty' <<<"$body")"
  [[ "$message_id" =~ ^[0-9]+$ ]] && pass 'Helpify customer sends message' || fail 'Helpify customer sends message'

  body="$(curl -fsS -H "Authorization: Bearer $contractor_token" "http://127.0.0.1:18081/api/work/tasks/$task_id/messages")"
  jq -e '.messages | length >= 1' <<<"$body" >/dev/null && pass 'Helpify contractor reads messages' || fail 'Helpify contractor reads messages'

  body="$(post_json "http://127.0.0.1:18081/api/work/tasks/$task_id/status" "$contractor_token" '{"status":"completed"}')"
  jq -e '.task.status == "completed"' <<<"$body" >/dev/null && pass 'Helpify task completion' || fail 'Helpify task completion'

  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $customer_token" -X POST http://127.0.0.1:18081/api/auth/logout --data '{}' >/dev/null || true
  curl -fsS -H 'Content-Type: application/json' -H "Authorization: Bearer $contractor_token" -X POST http://127.0.0.1:18081/api/auth/logout --data '{}' >/dev/null || true
}

check_web(){
  local body
  [[ $EUID -eq 0 ]] || { warn 'web checks skipped without root'; return; }
  body="$(curl -kfsS --resolve helpsiffyy.app:443:127.0.0.1 https://helpsiffyy.app/demo/ 2>/dev/null || true)"
  grep -Fq 'helpify-web-demo' <<<"$body" && pass 'Helpify deployed workflow web marker' || fail 'Helpify deployed workflow web marker'
  [[ -f /var/www/helpsiffyy.app/current/demo/assets/api-client.js ]] \
    && grep -Fq 'listTasks: function' /var/www/helpsiffyy.app/current/demo/assets/api-client.js \
    && pass 'Helpify deployed workflow API client' || fail 'Helpify deployed workflow API client'
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
