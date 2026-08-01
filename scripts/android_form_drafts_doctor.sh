#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
CORE="$ROOT/implementation/android-native/core/src/main/java/app/shared/core"
DASH="$CORE/BaseDashboardActivity.kt"
STORE="$CORE/LiveFormDraftStore.kt"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

[[ -s "$STORE" ]] \
  && pass "live form draft store" || fail "live form draft store"

grep -Fq 'fun saveDraft' "$STORE" \
  && pass "form draft persistence" || fail "form draft persistence"

grep -Fq 'fun loadDraft' "$STORE" \
  && pass "form draft restoration" || fail "form draft restoration"

grep -Fq 'fun saveTemplate' "$STORE" \
  && pass "named form templates" || fail "named form templates"

grep -Fq 'fun deleteTemplate' "$STORE" \
  && pass "template deletion" || fail "template deletion"

grep -Fq 'const val MAX_TEMPLATES = 12' "$STORE" \
  && pass "template count limit" || fail "template count limit"

grep -Fq 'TextWatcher' "$DASH" \
  && pass "form autosave watcher" || fail "form autosave watcher"

grep -Fq 'attachDraftAutosave' "$DASH" \
  && pass "draft autosave integration" || fail "draft autosave integration"

grep -Fq 'openFormTemplateMenu' "$DASH" \
  && pass "template manager UI" || fail "template manager UI"

grep -Fq 'promptTemplateName' "$DASH" \
  && pass "template naming UI" || fail "template naming UI"

grep -Fq 'Черновик формы сохранён' "$DASH" \
  && pass "saved draft card indicator" || fail "saved draft card indicator"

grep -Fq 'formDraftStore.clearDraft(user.role, card.id)' "$DASH" \
  && pass "successful action clears draft" \
  || fail "successful action clears draft"

grep -Fq 'versionCode 1700' \
  "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionCode 1700" || fail "Helpify versionCode 1700"

grep -Fq "versionName '0.17.0'" \
  "$ROOT/implementation/android-native/helpify/build.gradle" \
  && pass "Helpify versionName 0.17.0" || fail "Helpify versionName 0.17.0"

grep -Fq 'versionCode 1700' \
  "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionCode 1700" || fail "MyDealer versionCode 1700"

grep -Fq "versionName '0.17.0'" \
  "$ROOT/implementation/android-native/mydealer/build.gradle" \
  && pass "MyDealer versionName 0.17.0" || fail "MyDealer versionName 0.17.0"

if grep -RIniE 'reconstruction|retrospective|synthetic|реконструк|ретроспектив|синтет' \
  "$STORE" "$DASH" >/tmp/android-form-drafts-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-form-drafts-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-form-drafts-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
