#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
DASH="$CORE/BaseDashboardActivity.kt"
NAV="$CORE/DashboardNavigation.kt"
SHORTCUTS="$CORE/DashboardShortcutManager.kt"
NOTIFIER="$CORE/LiveUpdateNotifier.kt"
WORKER="$CORE/LiveDashboardBackgroundWorker.kt"
HELPIFY="$ROOT/implementation/android-native/helpify"
MYDEALER="$ROOT/implementation/android-native/mydealer"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$NAV" ]] \
  && pass "dashboard navigation contract" || fail "dashboard navigation contract"

[[ -s "$SHORTCUTS" ]] \
  && pass "dynamic shortcut manager" || fail "dynamic shortcut manager"

grep -Fq 'override fun onNewIntent(intent: Intent)' "$DASH" \
  && pass "singleTop navigation handling" || fail "singleTop navigation handling"

grep -Fq 'DashboardNavigation.parse' "$DASH" \
  && pass "dashboard intent parsing" || fail "dashboard intent parsing"

grep -Fq 'DashboardShortcutManager.install' "$DASH" \
  && pass "dashboard shortcuts installed" || fail "dashboard shortcuts installed"

grep -Fq 'EXTRA_SYNC_NOW' "$NAV" \
  && pass "synchronize-now navigation extra" \
  || fail "synchronize-now navigation extra"

grep -Fq 'SECTION_UPDATES = "События"' "$NAV" \
  && pass "events deep-link destination" || fail "events deep-link destination"

grep -Fq 'ShortcutInfo.Builder' "$SHORTCUTS" \
  && pass "Android dynamic shortcuts" || fail "Android dynamic shortcuts"

grep -Fq 'setContentIntent(pendingIntent)' "$NOTIFIER" \
  && pass "notification content navigation" || fail "notification content navigation"

grep -Fq '"Открыть события"' "$NOTIFIER" \
  && pass "notification events action" || fail "notification events action"

grep -Fq 'dashboardActivityClass: Class<*>' "$WORKER" \
  && pass "background notification activity target" \
  || fail "background notification activity target"

grep -Fq 'DashboardActivity::class.java' \
  "$HELPIFY/src/main/java/app/helpsiffyy/mobile/HelpifyBackgroundSyncWorker.kt" \
  && pass "Helpify worker navigation target" \
  || fail "Helpify worker navigation target"

grep -Fq 'DashboardActivity::class.java' \
  "$MYDEALER/src/main/java/app/mydealers/mobile/MyDealerBackgroundSyncWorker.kt" \
  && pass "MyDealer worker navigation target" \
  || fail "MyDealer worker navigation target"

grep -Fq 'android:launchMode="singleTop"' "$HELPIFY/src/main/AndroidManifest.xml" \
  && pass "Helpify singleTop dashboard" || fail "Helpify singleTop dashboard"

grep -Fq 'android:scheme="helpsiffyy"' "$HELPIFY/src/main/AndroidManifest.xml" \
  && pass "Helpify custom deep link" || fail "Helpify custom deep link"

grep -Fq 'android:host="helpsiffyy.app"' "$HELPIFY/src/main/AndroidManifest.xml" \
  && pass "Helpify web deep link" || fail "Helpify web deep link"

grep -Fq 'android:launchMode="singleTop"' "$MYDEALER/src/main/AndroidManifest.xml" \
  && pass "MyDealer singleTop dashboard" || fail "MyDealer singleTop dashboard"

grep -Fq 'android:scheme="mydealers"' "$MYDEALER/src/main/AndroidManifest.xml" \
  && pass "MyDealer custom deep link" || fail "MyDealer custom deep link"

grep -Fq 'android:host="mydealers.app"' "$MYDEALER/src/main/AndroidManifest.xml" \
  && pass "MyDealer web deep link" || fail "MyDealer web deep link"

grep -Fq 'versionCode 1600' "$HELPIFY/build.gradle" \
  && pass "Helpify versionCode 1600" || fail "Helpify versionCode 1600"

grep -Fq "versionName '0.16.0'" "$HELPIFY/build.gradle" \
  && pass "Helpify versionName 0.16.0" || fail "Helpify versionName 0.16.0"

grep -Fq 'versionCode 1600' "$MYDEALER/build.gradle" \
  && pass "MyDealer versionCode 1600" || fail "MyDealer versionCode 1600"

grep -Fq "versionName '0.16.0'" "$MYDEALER/build.gradle" \
  && pass "MyDealer versionName 0.16.0" || fail "MyDealer versionName 0.16.0"

if grep -RIniE 'reconstruction|retrospective|synthetic|реконструк|ретроспектив|синтет' \
  "$NAV" "$SHORTCUTS" "$NOTIFIER" "$DASH" >/tmp/android-navigation-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-navigation-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-navigation-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
