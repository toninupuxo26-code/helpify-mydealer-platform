#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
DASH="$CORE/BaseDashboardActivity.kt"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$CORE/LiveDashboardCache.kt" ]] \
  && pass "offline live dashboard cache" || fail "offline live dashboard cache"

[[ -s "$CORE/ActionHistoryStore.kt" ]] \
  && pass "persistent action history" || fail "persistent action history"

grep -Fq 'restoreCachedLiveData' "$DASH" \
  && pass "cache restored before network refresh" \
  || fail "cache restored before network refresh"

grep -Fq 'liveCache.save' "$DASH" \
  && pass "successful server payload cached" \
  || fail "successful server payload cached"

grep -Fq 'showingCachedData' "$DASH" \
  && pass "cached mode state" || fail "cached mode state"

grep -Fq 'renderNavigation' "$DASH" \
  && pass "dashboard navigation controls" || fail "dashboard navigation controls"

grep -Fq 'matchesQuery' "$DASH" \
  && pass "dashboard text search" || fail "dashboard text search"

grep -Fq 'availableSections' "$DASH" \
  && pass "dynamic section filters" || fail "dynamic section filters"

grep -Fq 'actionableOnly' "$DASH" \
  && pass "actionable cards filter" || fail "actionable cards filter"

grep -Fq 'renderHistorySection' "$DASH" \
  && pass "action history section" || fail "action history section"

grep -Fq 'actionHistory.add' "$DASH" \
  && pass "action result recording" || fail "action result recording"

grep -Fq 'versionCode 1600' \
  "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionCode 1600" || fail "Helpify versionCode 1600"

grep -Fq "versionName '0.16.0'" \
  "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionName 0.16.0" || fail "Helpify versionName 0.16.0"

grep -Fq 'versionCode 1600' \
  "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionCode 1600" || fail "MyDealer versionCode 1600"

grep -Fq "versionName '0.16.0'" \
  "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionName 0.16.0" || fail "MyDealer versionName 0.16.0"

if grep -RIniE 'reconstruction|retrospective|synthetic|реконструк|ретроспектив|синтет' \
  "$CORE/LiveDashboardCache.kt" \
  "$CORE/ActionHistoryStore.kt" \
  "$DASH" >/tmp/android-dashboard-ux-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-dashboard-ux-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-dashboard-ux-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
