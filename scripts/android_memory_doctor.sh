#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
P="$ROOT/implementation/android-native/gradle.properties"
PASS=0; WARN=0; FAIL=0
pass(){ echo "PASS  $*"; PASS=$((PASS+1)); }
warn(){ echo "WARN  $*"; WARN=$((WARN+1)); }
fail(){ echo "FAIL  $*"; FAIL=$((FAIL+1)); }

for item in \
  'org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m' \
  'org.gradle.workers.max=1' \
  'org.gradle.parallel=false' \
  'kotlin.compiler.execution.strategy=in-process' \
  'kotlin.incremental=false'
do
  grep -Fq "$item" "$P" && pass "$item" || fail "$item"
done

ram="$(awk '/MemTotal:/ {print int($2/1024)}' /proc/meminfo)"
swap="$(awk '/SwapTotal:/ {print int($2/1024)}' /proc/meminfo)"
total=$((ram+swap))
if (( total >= 3000 )); then
  pass "RAM+swap ${total}MB"
else
  warn "RAM+swap only ${total}MB"
fi

echo "PASS=$PASS WARN=$WARN FAIL=$FAIL"
(( FAIL == 0 ))
