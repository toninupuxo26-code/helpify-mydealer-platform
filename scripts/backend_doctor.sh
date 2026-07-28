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
  local app product
  while read -r app product; do
    [[ -f "$ROOT/$app/composer.json" ]] && pass "$product composer.json" || fail "$product composer.json missing"
    grep -q '"laravel/framework": "8.83.27"' "$ROOT/$app/composer.json" && pass "$product Laravel version pinned" || fail "$product Laravel version not pinned"
    [[ -f "$ROOT/$app/routes/api.php" ]] && grep -q "'/health'" "$ROOT/$app/routes/api.php" && pass "$product health route" || fail "$product health route missing"
    [[ -x "$ROOT/$app/docker/entrypoint.sh" ]] && pass "$product container entrypoint" || fail "$product entrypoint missing or not executable"
    if command -v php >/dev/null 2>&1; then
      while IFS= read -r file; do php -l "$file" >/dev/null || { fail "$product PHP syntax: $file"; return; }; done < <(find "$ROOT/$app" -type f -name '*.php' -print)
      pass "$product PHP syntax"
    else
      warn "php unavailable; $product PHP syntax skipped"
    fi
  done <<DATA
implementation/helpify/backend helpify
implementation/mydealer/backend mydealer
DATA

  if grep -Eq 'local[[:space:]]+domain="\$1".*\$\{domain\}' "$ROOT/patches/3010-backend-infrastructure-baseline/patch.sh"; then
    fail 'unsafe Bash local declaration in patch 3010'
  else
    pass 'patch 3010 Bash local declarations are nounset-safe'
  fi

  [[ -f "$ROOT/infrastructure/backend/docker-compose.yml" ]] && pass 'backend compose file' || fail 'backend compose file missing'
  grep -q '127.0.0.1:18081:80' "$ROOT/infrastructure/backend/docker-compose.yml" && pass 'Helpify API loopback binding' || fail 'Helpify API binding missing'
  grep -q '127.0.0.1:18082:80' "$ROOT/infrastructure/backend/docker-compose.yml" && pass 'MyDealer API loopback binding' || fail 'MyDealer API binding missing'
  if grep -RqsE '(MYSQL_ROOT_PASSWORD|DB_PASSWORD|APP_KEY)=(base64:)?[A-Za-z0-9+/=]{20,}$' "$ROOT/infrastructure/backend" "$ROOT/implementation/helpify/backend/.env.example" "$ROOT/implementation/mydealer/backend/.env.example"; then
    fail 'possible committed runtime secret'
  else
    pass 'no runtime secrets detected in tracked templates'
  fi
}

json_field(){
  local url="$1" field="$2"
  curl -kfsS "$url" 2>/dev/null | sed -n "s/.*\"$field\":\"\([^\"]*\)\".*/\1/p" | head -1
}

check_runtime(){
  [[ $EUID -eq 0 ]] || { warn 'runtime checks skipped without root'; return; }
  command -v docker >/dev/null 2>&1 && pass 'Docker available' || { fail 'Docker unavailable'; return; }
  docker compose version >/dev/null 2>&1 && pass 'Docker Compose available' || { fail 'Docker Compose unavailable'; return; }
  systemctl is-active --quiet docker && pass 'Docker service active' || fail 'Docker service inactive'

  local compose="$ROOT/infrastructure/backend/docker-compose.yml"
  local env_file="/etc/helpify-mydealer/backend.env"
  [[ -f "$env_file" ]] && pass 'backend runtime environment exists' || { fail 'backend runtime environment missing'; return; }
  [[ "$(stat -c '%a' "$env_file" 2>/dev/null || true)" == '600' ]] && pass 'backend runtime environment mode 600' || fail 'backend runtime environment permissions'

  if docker compose --env-file "$env_file" -f "$compose" ps --status running --services 2>/dev/null | grep -qx 'helpify-api'; then pass 'Helpify API container running'; else fail 'Helpify API container not running'; fi
  if docker compose --env-file "$env_file" -f "$compose" ps --status running --services 2>/dev/null | grep -qx 'mydealer-api'; then pass 'MyDealer API container running'; else fail 'MyDealer API container not running'; fi
  if docker compose --env-file "$env_file" -f "$compose" ps --status running --services 2>/dev/null | grep -qx 'mysql'; then pass 'MySQL container running'; else fail 'MySQL container not running'; fi
  if docker compose --env-file "$env_file" -f "$compose" ps --status running --services 2>/dev/null | grep -qx 'redis'; then pass 'Redis container running'; else fail 'Redis container not running'; fi

  local body
  body="$(curl -fsS http://127.0.0.1:18081/api/health 2>/dev/null || true)"
  grep -q '"product":"helpify"' <<<"$body" && grep -q '"status":"ok"' <<<"$body" && pass 'Helpify local API health' || fail 'Helpify local API health'
  body="$(curl -fsS http://127.0.0.1:18082/api/health 2>/dev/null || true)"
  grep -q '"product":"mydealer"' <<<"$body" && grep -q '"status":"ok"' <<<"$body" && pass 'MyDealer local API health' || fail 'MyDealer local API health'

  body="$(curl -kfsS --resolve helpsiffyy.app:443:127.0.0.1 https://helpsiffyy.app/api/health 2>/dev/null || true)"
  grep -q '"product":"helpify"' <<<"$body" && pass 'Helpify same-origin HTTPS API' || fail 'Helpify same-origin HTTPS API'
  body="$(curl -kfsS --resolve mydealers.app:443:127.0.0.1 https://mydealers.app/api/health 2>/dev/null || true)"
  grep -q '"product":"mydealer"' <<<"$body" && pass 'MyDealer same-origin HTTPS API' || fail 'MyDealer same-origin HTTPS API'
}

case "$MODE" in
  source) check_source ;;
  runtime) check_runtime ;;
  all) check_source; check_runtime ;;
  *) printf 'Usage: %s [source|runtime|all]\n' "$0" >&2; exit 64 ;;
esac

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
