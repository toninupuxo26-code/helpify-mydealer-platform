#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ANDROID="$ROOT/implementation/android-native"
MODE="${1:-all}"
PASS=0; WARN=0; FAIL=0
pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }
file(){ [[ -s "$1" ]] && pass "$2" || fail "$2"; }
contains(){ grep -Fq -- "$2" "$1" && pass "$3" || fail "$3"; }

source_checks(){
  file "$ANDROID/settings.gradle" "Android settings"
  file "$ANDROID/build.gradle" "Android root build"
  contains "$ANDROID/settings.gradle" ":core" "core module declared"
  contains "$ANDROID/settings.gradle" ":helpify" "Helpify module declared"
  contains "$ANDROID/settings.gradle" ":mydealer" "MyDealer module declared"
  contains "$ANDROID/build.gradle" "1.5.31" "Kotlin 1.5.31 pinned"
  contains "$ANDROID/build.gradle" "7.0.4" "Android Gradle Plugin 7.0.4 pinned"
  contains "$ANDROID/gradle/wrapper/gradle-wrapper.properties" "gradle-7.0.2" "Gradle 7.0.2 pinned"
  for app in helpify mydealer; do
    file "$ANDROID/$app/build.gradle" "$app build file"
    file "$ANDROID/$app/src/main/AndroidManifest.xml" "$app manifest"
    contains "$ANDROID/$app/build.gradle" "compileSdkVersion 31" "$app compileSdk 31"
    contains "$ANDROID/$app/build.gradle" "targetSdkVersion 31" "$app targetSdk 31"
    contains "$ANDROID/$app/build.gradle" "minSdkVersion 23" "$app minSdk 23"
    contains "$ANDROID/$app/src/main/AndroidManifest.xml" "android.permission.INTERNET" "$app Internet permission"
    contains "$ANDROID/$app/src/main/AndroidManifest.xml" 'android:exported="true"' "$app exported launcher"
  done
  contains "$ANDROID/helpify/src/main/java/app/helpsiffyy/development/MainActivity.kt" "https://helpsiffyy.app/api" "Helpify API URL"
  contains "$ANDROID/mydealer/src/main/java/app/mydealers/development/MainActivity.kt" "https://mydealers.app/api" "MyDealer API URL"
  file "$ANDROID/core/src/main/java/app/shared/core/AuthRepository.kt" "shared auth repository"
  file "$ANDROID/core/src/main/java/app/shared/core/BaseAuthActivity.kt" "shared authentication UI"
  file "$ANDROID/core/src/main/java/app/shared/core/BaseDashboardActivity.kt" "shared dashboard UI"
  if grep -RqiE 'jetpack[[:space:]]*compose|androidx\.compose|composeOptions' "$ANDROID"; then
    fail "Jetpack Compose is absent"
  else
    pass "Jetpack Compose is absent"
  fi
  if find "$ANDROID" -type f \( -name '*.jks' -o -name '*.keystore' -o -name 'local.properties' \) | grep -q .; then
    fail "signing keys and local SDK paths are absent"
  else
    pass "signing keys and local SDK paths are absent"
  fi
  if grep -RInE '(API_KEY|SECRET_KEY|PRIVATE_KEY|BEGIN (RSA|OPENSSH) PRIVATE KEY)[[:space:]]*[:=]' "$ANDROID" --exclude='*.md' | grep -q .; then
    fail "runtime secrets are absent"
  else
    pass "runtime secrets are absent"
  fi
}

toolchain_checks(){
  if command -v java >/dev/null 2>&1; then
    java -version 2>&1 | head -1 | grep -Eq '11\.|version "11' && pass "Java 11 available" || warn "Java is available but not version 11"
  else
    warn "Java unavailable; build skipped"
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT:-}" ]]; then
    pass "ANDROID_SDK_ROOT available"
  else
    warn "ANDROID_SDK_ROOT unavailable; build skipped"
  fi
}

case "$MODE" in
  source) source_checks ;;
  toolchain) toolchain_checks ;;
  all) source_checks; toolchain_checks ;;
  *) printf 'Usage: %s {source|toolchain|all}\n' "$0" >&2; exit 64 ;;
esac
printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
