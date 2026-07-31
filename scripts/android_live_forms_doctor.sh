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

grep -Fq 'data class LiveActionForm' "$CORE/LiveWorkflowModels.kt" \
  && pass "live action form model" || fail "live action form model"

grep -Fq 'enum class LiveFormFieldType' "$CORE/LiveWorkflowModels.kt" \
  && pass "live form field types" || fail "live form field types"

grep -Fq 'private fun openLiveActionForm' "$CORE/BaseDashboardActivity.kt" \
  && pass "dynamic Android form dialog" || fail "dynamic Android form dialog"

grep -Fq 'private fun validateLiveForm' "$CORE/BaseDashboardActivity.kt" \
  && pass "form validation" || fail "form validation"

grep -Fq 'confirmationMessage' "$CORE/BaseDashboardActivity.kt" \
  && pass "action confirmations" || fail "action confirmations"

grep -Fq 'seed-task-pack' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
  && pass "Helpify task data pack" || fail "Helpify task data pack"

grep -Fq 'seed-offers:' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
  && pass "Helpify offer data pack" || fail "Helpify offer data pack"

grep -Fq 'LiveFormFieldType.DECIMAL' "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
  && pass "Helpify numeric forms" || fail "Helpify numeric forms"

grep -Fq 'seed-product-pack' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
  && pass "MyDealer product data pack" || fail "MyDealer product data pack"

grep -Fq 'seed-cart:' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
  && pass "MyDealer cart data pack" || fail "MyDealer cart data pack"

grep -Fq 'LiveFormFieldType.INTEGER' "$MYDEALER/MyDealerLiveWorkflowRepository.kt" \
  && pass "MyDealer quantity form" || fail "MyDealer quantity form"

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
  "$CORE/LiveWorkflowModels.kt" \
  "$HELPIFY/HelpifyLiveWorkflowRepository.kt" \
  "$MYDEALER/MyDealerLiveWorkflowRepository.kt" >/tmp/android-forms-vocabulary.$$; then
  fail "public vocabulary"
  cat /tmp/android-forms-vocabulary.$$
else
  pass "public vocabulary"
fi
rm -f /tmp/android-forms-vocabulary.$$

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
