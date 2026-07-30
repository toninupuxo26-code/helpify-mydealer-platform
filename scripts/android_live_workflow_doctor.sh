#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
HELPIFY="$ROOT/implementation/android-native/helpify/src/main/java/app/helpsiffyy/mobile"
MYDEALER="$ROOT/implementation/android-native/mydealer/src/main/java/app/mydealers/mobile"
MODE="${1:-source}"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

source_checks(){
  [[ -s "$CORE/LiveWorkflowModels.kt" ]] \
    && pass "live workflow models" || fail "live workflow models"
  [[ -s "$HELPIFY/HelpifyLiveWorkflowRepository.kt" ]] \
    && pass "Helpify live repository" || fail "Helpify live repository"
  [[ -s "$MYDEALER/MyDealerLiveWorkflowRepository.kt" ]] \
    && pass "MyDealer live repository" || fail "MyDealer live repository"

  grep -Fq 'liveWorkflowRepository()' "$CORE/BaseDashboardActivity.kt" \
    && pass "dashboard repository hook" || fail "dashboard repository hook"
  grep -Fq 'refreshLiveData()' "$CORE/BaseDashboardActivity.kt" \
    && pass "dashboard live refresh" || fail "dashboard live refresh"
  grep -Fq 'performLiveAction' "$CORE/BaseDashboardActivity.kt" \
    && pass "dashboard live actions" || fail "dashboard live actions"

  grep -Fq '"/work/tasks"' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
    && pass "Helpify task endpoint" || fail "Helpify task endpoint"
  grep -Fq 'create-offer:' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
    && pass "Helpify offer action" || fail "Helpify offer action"
  grep -Fq 'select-offer:' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
    && pass "Helpify offer selection action" || fail "Helpify offer selection action"
  grep -Fq 'complete-task:' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
    && pass "Helpify completion action" || fail "Helpify completion action"

  grep -Fq '"/market/products"' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
    && pass "MyDealer product endpoint" || fail "MyDealer product endpoint"
  grep -Fq 'add-cart:' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
    && pass "MyDealer cart action" || fail "MyDealer cart action"
  grep -Fq '"checkout"' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
    && pass "MyDealer checkout action" || fail "MyDealer checkout action"
  grep -Fq 'order-status:' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
    && pass "MyDealer order action" || fail "MyDealer order action"

  grep -Fq 'versionCode 1400' \
    "$ROOT/implementation/android-native/helpify/build.gradle" \
    && pass "Helpify versionCode 1400" || fail "Helpify versionCode 1400"
  grep -Fq "versionName '0.14.0'" \
    "$ROOT/implementation/android-native/helpify/build.gradle" \
    && pass "Helpify versionName 0.14.0" || fail "Helpify versionName 0.14.0"
  grep -Fq 'versionCode 1400' \
    "$ROOT/implementation/android-native/mydealer/build.gradle" \
    && pass "MyDealer versionCode 1400" || fail "MyDealer versionCode 1400"
  grep -Fq "versionName '0.14.0'" \
    "$ROOT/implementation/android-native/mydealer/build.gradle" \
    && pass "MyDealer versionName 0.14.0" || fail "MyDealer versionName 0.14.0"

  if grep -RIniE 'reconstruction|retrospective|synthetic|реконструк|ретроспектив|синтет' \
    "$CORE/LiveWorkflowModels.kt" \
    "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
    "$MYDEALER/MyDealerLiveWorkflowRepository.kt" >/tmp/android-live-vocabulary.$$; then
    fail "public vocabulary"
    cat /tmp/android-live-vocabulary.$$
  else
    pass "public vocabulary"
  fi
  rm -f /tmp/android-live-vocabulary.$$
}

runtime_checks(){
  local helpify_code mydealer_code

  helpify_code="$(curl -LksS -o /tmp/helpify-live-health.$$ -w '%{http_code}' \
    --connect-timeout 8 --max-time 15 \
    https://helpsiffyy.app/api/health || true)"
  mydealer_code="$(curl -LksS -o /tmp/mydealer-live-health.$$ -w '%{http_code}' \
    --connect-timeout 8 --max-time 15 \
    https://mydealers.app/api/health || true)"

  [[ "$helpify_code" == "200" ]] \
    && pass "Helpify public API health" \
    || warn "Helpify public API health returned HTTP ${helpify_code:-0}"

  [[ "$mydealer_code" == "200" ]] \
    && pass "MyDealer public API health" \
    || warn "MyDealer public API health returned HTTP ${mydealer_code:-0}"

  rm -f /tmp/helpify-live-health.$$ /tmp/mydealer-live-health.$$
}

case "$MODE" in
  source) source_checks ;;
  runtime) runtime_checks ;;
  all) source_checks; runtime_checks ;;
  *) printf 'Usage: %s {source|runtime|all}\n' "$0" >&2; exit 64 ;;
esac

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
