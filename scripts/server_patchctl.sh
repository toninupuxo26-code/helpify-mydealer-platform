#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  printf 'ERROR: server_patchctl.sh must be run as root (use sudo).\n' >&2
  exit 1
fi

install -d -m 0755 \
  /var/lib/helpify-mydealer/patch-state \
  /var/log/helpify-mydealer/patches \
  /var/lock/helpify-mydealer

export PATCH_STATE_DIR="/var/lib/helpify-mydealer/patch-state"
export PATCH_LOG_DIR="/var/log/helpify-mydealer/patches"
export PATCH_LOCK_DIR="/var/lock/helpify-mydealer/patch.lock"

exec "$REPO_ROOT/scripts/patchctl.sh" "$@"
