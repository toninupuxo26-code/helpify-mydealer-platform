#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
HELPIFY="$ROOT/implementation/android-native/helpify/src/main/java/app/helpsiffyy/mobile"
MYDEALER="$ROOT/implementation/android-native/mydealer/src/main/java/app/mydealers/mobile"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$CORE/ScenarioStore.kt" ]] && pass "ScenarioStore" || fail "ScenarioStore"
grep -Fq 'AlertDialog.Builder' "$CORE/BaseDashboardActivity.kt" \
  && pass "interactive scenario dialogs" || fail "interactive scenario dialogs"
grep -Fq 'dashboardMetrics' "$CORE/BaseDashboardActivity.kt" \
  && pass "dashboard metrics" || fail "dashboard metrics"
grep -Fq 'resetAll' "$CORE/BaseDashboardActivity.kt" \
  && pass "scenario reset action" || fail "scenario reset action"

helpify_count="$(grep -c 'DashboardCard(' "$HELPIFY/HelpifyScenarioCatalog.kt" || true)"
mydealer_count="$(grep -c 'DashboardCard(' "$MYDEALER/MyDealerScenarioCatalog.kt" || true)"

if (( helpify_count >= 20 )); then
  pass "Helpify scenarios: $helpify_count"
else
  fail "Helpify scenarios below 20: $helpify_count"
fi

if (( mydealer_count >= 24 )); then
  pass "MyDealer scenarios: $mydealer_count"
else
  fail "MyDealer scenarios below 24: $mydealer_count"
fi

grep -Fq 'versionCode 1000' "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionCode 1000" || fail "Helpify versionCode 1000"
grep -Fq "versionName '0.10.0'" "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionName 0.10.0" || fail "Helpify versionName 0.10.0"
grep -Fq 'versionCode 1000' "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionCode 1000" || fail "MyDealer versionCode 1000"
grep -Fq "versionName '0.10.0'" "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionName 0.10.0" || fail "MyDealer versionName 0.10.0"

if grep -RIniE 'reconstruction|retrospective|synthetic|реконструк|ретроспектив|синтет' \
  "$CORE" "$HELPIFY" "$MYDEALER" >/tmp/android-scenario-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-scenario-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-scenario-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
