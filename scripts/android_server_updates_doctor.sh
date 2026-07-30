#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
DASH="$CORE/BaseDashboardActivity.kt"
STORE="$CORE/LiveUpdateStore.kt"
NOTIFIER="$CORE/LiveUpdateNotifier.kt"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$STORE" ]] \
  && pass "live update store" || fail "live update store"

[[ -s "$NOTIFIER" ]] \
  && pass "system notification helper" || fail "system notification helper"

grep -Fq 'fun capture(' "$STORE" \
  && pass "server change capture" || fail "server change capture"

grep -Fq 'previousRaw == null' "$STORE" \
  && pass "first sync notification suppression" \
  || fail "first sync notification suppression"

grep -Fq 'LiveUpdateKind.NEW_ITEM' "$STORE" \
  && pass "new item detection" || fail "new item detection"

grep -Fq 'LiveUpdateKind.STATUS_CHANGED' "$STORE" \
  && pass "status change detection" || fail "status change detection"

grep -Fq 'const val MAX_EVENTS = 60' "$STORE" \
  && pass "notification centre limit" || fail "notification centre limit"

grep -Fq 'NotificationChannel' "$NOTIFIER" \
  && pass "Android notification channel" || fail "Android notification channel"

grep -Fq 'private fun renderUpdatesSection' "$DASH" \
  && pass "in-app notification centre" || fail "in-app notification centre"

grep -Fq 'private fun openUpdateSettings' "$DASH" \
  && pass "notification preferences UI" || fail "notification preferences UI"

grep -Fq 'liveUpdateStore.capture' "$DASH" \
  && pass "fresh payload change detection" || fail "fresh payload change detection"

grep -Fq 'liveUpdateNotifier.notifyChanges' "$DASH" \
  && pass "system notification dispatch" || fail "system notification dispatch"

grep -Fq 'UPDATES_SECTION = "События"' "$DASH" \
  && pass "events section navigation" || fail "events section navigation"

grep -Fq 'android.permission.POST_NOTIFICATIONS' \
  "$ROOT/implementation/android-native/helpify/src/main/AndroidManifest.xml" \
  && pass "Helpify notification permission" \
  || fail "Helpify notification permission"

grep -Fq 'android.permission.POST_NOTIFICATIONS' \
  "$ROOT/implementation/android-native/mydealer/src/main/AndroidManifest.xml" \
  && pass "MyDealer notification permission" \
  || fail "MyDealer notification permission"

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
  "$STORE" "$NOTIFIER" "$DASH" >/tmp/android-updates-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-updates-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-updates-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
