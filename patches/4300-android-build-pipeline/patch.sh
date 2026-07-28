#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_ID="4300"
PATCH_DESCRIPTION="Build Helpify and MyDealer Android debug packages"
PATCH_REQUIRES_ROOT=1
PATCH_SUPPORTS_ROLLBACK=0

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"

check(){
  "$ROOT/scripts/android_build_doctor.sh" source
  command -v docker >/dev/null
}

apply(){
  "$ROOT/scripts/android_build.sh"
}

verify(){
  "$ROOT/scripts/android_build_doctor.sh" all
}

rollback(){
  printf '%s\n' 'Build artifacts are immutable. Publish a newer application version.' >&2
  return 1
}

case "${1:-}" in
  id) printf '%s\n' "$PATCH_ID" ;;
  description) printf '%s\n' "$PATCH_DESCRIPTION" ;;
  requires-root) printf '%s\n' "$PATCH_REQUIRES_ROOT" ;;
  supports-rollback) printf '%s\n' "$PATCH_SUPPORTS_ROLLBACK" ;;
  check) check ;;
  apply) apply ;;
  verify) verify ;;
  rollback) rollback ;;
  *) printf 'Usage: %s {id|description|requires-root|supports-rollback|check|apply|verify|rollback}\n' "$0" >&2; exit 64 ;;
esac
