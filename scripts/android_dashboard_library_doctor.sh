#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
DASH="$CORE/BaseDashboardActivity.kt"
STORE="$CORE/DashboardLibraryStore.kt"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$STORE" ]] \
  && pass "dashboard library store" || fail "dashboard library store"

grep -Fq 'fun toggleFavorite' "$STORE" \
  && pass "persistent favorites" || fail "persistent favorites"

grep -Fq 'fun recordRecent' "$STORE" \
  && pass "recent view recording" || fail "recent view recording"

grep -Fq 'const val MAX_RECENT = 20' "$STORE" \
  && pass "recent view limit" || fail "recent view limit"

grep -Fq 'favoritesOnly' "$DASH" \
  && pass "favorites-only filter" || fail "favorites-only filter"

grep -Fq 'private fun renderRecentSection' "$DASH" \
  && pass "recent views section" || fail "recent views section"

grep -Fq 'setOnLongClickListener' "$DASH" \
  && pass "long-press favorite action" || fail "long-press favorite action"

grep -Fq 'private fun shareDashboardItem' "$DASH" \
  && pass "card sharing" || fail "card sharing"

grep -Fq 'Intent.ACTION_SEND' "$DASH" \
  && pass "Android share intent" || fail "Android share intent"

grep -Fq 'RECENT_SECTION = "Недавние"' "$DASH" \
  && pass "recent section navigation" || fail "recent section navigation"

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
  "$STORE" "$DASH" >/tmp/android-library-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-library-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-library-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
