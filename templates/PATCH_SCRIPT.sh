#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_ID="0000"
PATCH_DESCRIPTION="Replace with a concise, immutable description"
PATCH_REQUIRES_ROOT=0
PATCH_SUPPORTS_ROLLBACK=0

patch_check() {
  # Check prerequisites only. Do not modify the system here.
  return 0
}

patch_apply() {
  # The operation must be idempotent: running it twice must be safe.
  return 0
}

patch_verify() {
  # Return 0 only when the intended end state is present and healthy.
  return 1
}

patch_rollback() {
  # Optional. Enable only when rollback is deterministic and safe.
  return 1
}

case "${1:-}" in
  id) printf '%s\n' "$PATCH_ID" ;;
  description) printf '%s\n' "$PATCH_DESCRIPTION" ;;
  requires-root) printf '%s\n' "$PATCH_REQUIRES_ROOT" ;;
  supports-rollback) printf '%s\n' "$PATCH_SUPPORTS_ROLLBACK" ;;
  check) patch_check ;;
  apply) patch_apply ;;
  verify) patch_verify ;;
  rollback) patch_rollback ;;
  *)
    printf 'Usage: %s {id|description|requires-root|supports-rollback|check|apply|verify|rollback}\n' "$0" >&2
    exit 64
    ;;
esac
