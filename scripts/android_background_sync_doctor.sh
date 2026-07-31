#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core"
CORE_JAVA="$CORE/src/main/java/app/shared/core"
HELPIFY="$ROOT/implementation/android-native/helpify/src/main/java/app/helpsiffyy/mobile"
MYDEALER="$ROOT/implementation/android-native/mydealer/src/main/java/app/mydealers/mobile"
DASH="$CORE_JAVA/BaseDashboardActivity.kt"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

grep -Fq "api 'androidx.work:work-runtime-ktx:2.7.1'" "$CORE/build.gradle" \
  && pass "WorkManager 2.7.1 pinned" || fail "WorkManager 2.7.1 pinned"

[[ -s "$CORE_JAVA/BackgroundSyncStore.kt" ]] \
  && pass "background sync settings store" || fail "background sync settings store"

[[ -s "$CORE_JAVA/BackgroundSyncScheduler.kt" ]] \
  && pass "background sync scheduler" || fail "background sync scheduler"

[[ -s "$CORE_JAVA/LiveDashboardBackgroundWorker.kt" ]] \
  && pass "shared background worker" || fail "shared background worker"

grep -Fq 'val enabled: Boolean = false' "$CORE_JAVA/BackgroundSyncStore.kt" \
  && pass "background sync disabled by default" \
  || fail "background sync disabled by default"

grep -Fq 'SUPPORTED_INTERVALS = listOf(15, 30, 60, 180)' \
  "$CORE_JAVA/BackgroundSyncStore.kt" \
  && pass "supported sync intervals" || fail "supported sync intervals"

grep -Fq 'NetworkType.CONNECTED' "$CORE_JAVA/BackgroundSyncScheduler.kt" \
  && pass "network constraint" || fail "network constraint"

grep -Fq 'setRequiresBatteryNotLow(true)' \
  "$CORE_JAVA/BackgroundSyncScheduler.kt" \
  && pass "battery constraint" || fail "battery constraint"

grep -Fq 'ExistingPeriodicWorkPolicy.KEEP' \
  "$CORE_JAVA/BackgroundSyncScheduler.kt" \
  && pass "stable periodic schedule" || fail "stable periodic schedule"

grep -Fq 'ExistingPeriodicWorkPolicy.REPLACE' \
  "$CORE_JAVA/BackgroundSyncScheduler.kt" \
  && pass "interval update rescheduling" || fail "interval update rescheduling"

grep -Fq 'LiveDashboardCache(applicationContext, productName)' \
  "$CORE_JAVA/LiveDashboardBackgroundWorker.kt" \
  && pass "background cache refresh" || fail "background cache refresh"

grep -Fq 'LiveUpdateNotifier(applicationContext, productName)' \
  "$CORE_JAVA/LiveDashboardBackgroundWorker.kt" \
  && pass "background notification dispatch" \
  || fail "background notification dispatch"

grep -Fq 'Result.retry()' "$CORE_JAVA/LiveDashboardBackgroundWorker.kt" \
  && pass "transient background retry" || fail "transient background retry"

[[ -s "$HELPIFY/HelpifyBackgroundSyncWorker.kt" ]] \
  && pass "Helpify background worker" || fail "Helpify background worker"

[[ -s "$MYDEALER/MyDealerBackgroundSyncWorker.kt" ]] \
  && pass "MyDealer background worker" || fail "MyDealer background worker"

grep -Fq '"helpify-live-background-sync"' "$HELPIFY/DashboardActivity.kt" \
  && pass "Helpify unique work name" || fail "Helpify unique work name"

grep -Fq '"mydealer-live-background-sync"' "$MYDEALER/DashboardActivity.kt" \
  && pass "MyDealer unique work name" || fail "MyDealer unique work name"

grep -Fq 'private fun backgroundSyncSummary' "$DASH" \
  && pass "background sync dashboard status" \
  || fail "background sync dashboard status"

grep -Fq 'Фоновое обновление через WorkManager' "$DASH" \
  && pass "background sync settings UI" || fail "background sync settings UI"

grep -Fq 'requestBackgroundSyncNow()' "$DASH" \
  && pass "manual background sync action" || fail "manual background sync action"

grep -Fq 'versionCode 1500' \
  "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionCode 1500" || fail "Helpify versionCode 1500"

grep -Fq "versionName '0.15.0'" \
  "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionName 0.15.0" || fail "Helpify versionName 0.15.0"

grep -Fq 'versionCode 1500' \
  "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionCode 1500" || fail "MyDealer versionCode 1500"

grep -Fq "versionName '0.15.0'" \
  "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionName 0.15.0" || fail "MyDealer versionName 0.15.0"

if grep -RIniE 'reconstruction|retrospective|synthetic|реконструк|ретроспектив|синтет' \
  "$CORE_JAVA/BackgroundSyncStore.kt" \
  "$CORE_JAVA/BackgroundSyncScheduler.kt" \
  "$CORE_JAVA/LiveDashboardBackgroundWorker.kt" \
  "$HELPIFY/HelpifyBackgroundSyncWorker.kt" \
  "$MYDEALER/MyDealerBackgroundSyncWorker.kt" \
  "$DASH" >/tmp/android-background-sync-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-background-sync-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-background-sync-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
