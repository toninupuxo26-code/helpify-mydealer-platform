#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
MODE="${1:-all}"
OUTPUT_DIR="${ANDROID_OUTPUT_DIR:-/var/lib/helpify-mydealer/android-builds/v0.10.0}"
PASS=0
WARN=0
FAIL=0

pass(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
warn(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
fail(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }

check_file(){
  [[ -s "$1" ]] && pass "$2" || fail "$2"
}

source_checks(){
  check_file "$ROOT/infrastructure/android/Dockerfile" "Android Dockerfile"
  check_file "$ROOT/infrastructure/android/packages.txt" "Android SDK package list"
  check_file "$ROOT/scripts/android_build.sh" "Android build script"
  check_file "$ROOT/patches/4300-android-build-pipeline/patch.sh" "Android build patch"

  grep -Fq 'openjdk-11-jdk-headless' "$ROOT/infrastructure/android/Dockerfile" \
    && pass "Java 11 pinned" || fail "Java 11 pinned"
  grep -Fq 'platforms;android-31' "$ROOT/infrastructure/android/Dockerfile" \
    && pass "Android platform 31 pinned" || fail "Android platform 31 pinned"
  grep -Fq 'build-tools;31.0.0' "$ROOT/infrastructure/android/Dockerfile" \
    && pass "Build Tools 31.0.0 pinned" || fail "Build Tools 31.0.0 pinned"
  grep -Fq 'GRADLE_VERSION=7.0.2' "$ROOT/infrastructure/android/Dockerfile" \
    && pass "Gradle 7.0.2 pinned" || fail "Gradle 7.0.2 pinned"

  for module in helpify mydealer; do
    grep -Fq "versionCode 1000" "$ROOT/implementation/android-native/$module/build.gradle" \
      && pass "$module versionCode 1000" || fail "$module versionCode 1000"
    grep -Fq "versionName '0.10.0'" "$ROOT/implementation/android-native/$module/build.gradle" \
      && pass "$module versionName 0.10.0" || fail "$module versionName 0.10.0"
  done

  bash -n "$ROOT/scripts/android_build.sh" \
    && pass "Android build Bash syntax" || fail "Android build Bash syntax"
  bash -n "$ROOT/patches/4300-android-build-pipeline/patch.sh" \
    && pass "Android patch Bash syntax" || fail "Android patch Bash syntax"
}

artifact_checks(){
  local helpify="$OUTPUT_DIR/Helpify-v0.10.0-debug.apk"
  local mydealer="$OUTPUT_DIR/MyDealer-v0.10.0-debug.apk"

  check_file "$helpify" "Helpify debug APK"
  check_file "$mydealer" "MyDealer debug APK"
  check_file "$OUTPUT_DIR/SHA256SUMS.txt" "APK SHA-256 manifest"
  check_file "$OUTPUT_DIR/Helpify-v0.10.0-badging.txt" "Helpify APK metadata"
  check_file "$OUTPUT_DIR/MyDealer-v0.10.0-badging.txt" "MyDealer APK metadata"
  check_file "$OUTPUT_DIR/build.env" "Android build metadata"

  if [[ -f "$OUTPUT_DIR/SHA256SUMS.txt" ]] \
    && (cd "$OUTPUT_DIR" && sha256sum -c SHA256SUMS.txt >/dev/null); then
    pass "APK integrity"
  else
    fail "APK integrity"
  fi

  grep -Fq "package: name='app.helpsiffyy.mobile'" \
    "$OUTPUT_DIR/Helpify-v0.10.0-badging.txt" 2>/dev/null \
    && pass "Helpify package identifier" || fail "Helpify package identifier"

  grep -Fq "package: name='app.mydealers.mobile'" \
    "$OUTPUT_DIR/MyDealer-v0.10.0-badging.txt" 2>/dev/null \
    && pass "MyDealer package identifier" || fail "MyDealer package identifier"
}

case "$MODE" in
  source) source_checks ;;
  artifacts) artifact_checks ;;
  all) source_checks; artifact_checks ;;
  *) printf 'Usage: %s {source|artifacts|all}\n' "$0" >&2; exit 64 ;;
esac

printf 'PASS=%d WARN=%d FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
(( FAIL == 0 ))
