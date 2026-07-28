#!/usr/bin/env bash
set -Eeuo pipefail

PROGRAM_NAME="patchctl"
PROGRAM_VERSION="0.2.1"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
PATCHES_DIR="${PATCHES_DIR:-$REPO_ROOT/patches}"
STATE_DIR="${PATCH_STATE_DIR:-$REPO_ROOT/.runtime/patch-state}"
LOG_DIR="${PATCH_LOG_DIR:-$REPO_ROOT/.runtime/logs}"
LOCK_DIR="${PATCH_LOCK_DIR:-$REPO_ROOT/.runtime/patch.lock}"

mkdir -p "$STATE_DIR" "$LOG_DIR"

log() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<USAGE
Usage: $PROGRAM_NAME <command> [patch-id|all]

Commands:
  list                 List discovered patches.
  status               Show application and verification status.
  check [id|all]       Validate patch scripts and run pre-checks.
  apply <id|all>       Apply pending patch(es), then verify.
  verify [id|all]      Verify applied patch(es).
  rollback <id>        Run optional rollback and remove state marker.
  doctor               Run structural checks and verify applied patches.
  version              Print patchctl version.

Environment:
  PATCHES_DIR           Patch directory (default: <repo>/patches)
  PATCH_STATE_DIR       Applied state directory
  PATCH_LOG_DIR         Log directory
  PATCH_LOCK_DIR        Lock directory
USAGE
}

cleanup_lock() {
  if [[ -d "$LOCK_DIR" && -f "$LOCK_DIR/pid" ]] && [[ "$(cat "$LOCK_DIR/pid" 2>/dev/null || true)" == "$$" ]]; then
    rm -rf -- "$LOCK_DIR"
  fi
}

acquire_lock() {
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    printf '%s\n' "$$" > "$LOCK_DIR/pid"
    trap cleanup_lock EXIT INT TERM
    return 0
  fi

  local owner="" stale_lock
  if [[ -f "$LOCK_DIR/pid" ]]; then
    owner="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  fi

  if [[ "$owner" =~ ^[0-9]+$ ]] && kill -0 "$owner" 2>/dev/null; then
    fail "another patch operation appears to be running (pid: $owner)"
  fi

  stale_lock="${LOCK_DIR}.stale.$(date -u '+%Y%m%dT%H%M%SZ').$$"
  if mv -- "$LOCK_DIR" "$stale_lock" 2>/dev/null; then
    log "stale patch lock moved to $stale_lock (previous pid: ${owner:-unknown})"
  else
    fail "patch lock exists and could not be recovered: $LOCK_DIR"
  fi

  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    fail "could not acquire patch lock after stale-lock recovery"
  fi
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
  trap cleanup_lock EXIT INT TERM
}

patch_files() {
  find "$PATCHES_DIR" -mindepth 2 -maxdepth 2 -type f -name 'patch.sh' -print 2>/dev/null | LC_ALL=C sort
}

patch_id_of() {
  local file="$1"
  bash "$file" id
}

patch_description_of() {
  local file="$1"
  bash "$file" description
}

patch_requires_root() {
  local file="$1"
  [[ "$(bash "$file" requires-root 2>/dev/null || printf '0')" == "1" ]]
}

find_patch() {
  local requested="$1" file id
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    id="$(patch_id_of "$file")"
    if [[ "$id" == "$requested" ]]; then
      printf '%s\n' "$file"
      return 0
    fi
  done < <(patch_files)
  return 1
}

state_file_for() {
  printf '%s/%s.applied\n' "$STATE_DIR" "$1"
}

script_hash() {
  sha256sum "$1" | awk '{print $1}'
}

state_value() {
  local state_file="$1" key="$2"
  awk -F= -v wanted="$key" '$1 == wanted {sub(/^[^=]*=/, ""); print; exit}' "$state_file" 2>/dev/null || true
}

validate_patch_file() {
  local file="$1" id dir_name
  bash -n "$file" || return 1
  id="$(patch_id_of "$file" 2>/dev/null || true)"
  [[ "$id" =~ ^[0-9]{4}$ ]] || {
    printf 'invalid patch id in %s: %s\n' "$file" "$id" >&2
    return 1
  }
  dir_name="$(basename "$(dirname "$file")")"
  [[ "$dir_name" == "$id"-* ]] || {
    printf 'patch directory must start with %s-: %s\n' "$id" "$dir_name" >&2
    return 1
  }
  return 0
}

validate_all_patch_files() {
  local file id seen="" count=0
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    count=$((count + 1))
    validate_patch_file "$file" || return 1
    id="$(patch_id_of "$file")"
    if grep -qxF "$id" <<< "$seen"; then
      printf 'duplicate patch id: %s\n' "$id" >&2
      return 1
    fi
    seen+="$id"$'\n'
  done < <(patch_files)
  log "patch structure valid ($count patch(es))"
}

write_state() {
  local id="$1" file="$2" state_file tmp commit
  state_file="$(state_file_for "$id")"
  tmp="${state_file}.tmp.$$"
  commit="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || printf 'not-a-git-repository')"
  {
    printf 'patch_id=%s\n' "$id"
    printf 'applied_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf 'script_sha256=%s\n' "$(script_hash "$file")"
    printf 'repository_commit=%s\n' "$commit"
  } > "$tmp"
  mv -f -- "$tmp" "$state_file"
}

is_marked_applied() {
  [[ -f "$(state_file_for "$1")" ]]
}

hash_matches_state() {
  local id="$1" file="$2" state_file expected actual
  state_file="$(state_file_for "$id")"
  [[ -f "$state_file" ]] || return 1
  expected="$(state_value "$state_file" script_sha256)"
  actual="$(script_hash "$file")"
  [[ -n "$expected" && "$expected" == "$actual" ]]
}

run_patch_action() {
  local file="$1" action="$2" id log_file
  id="$(patch_id_of "$file")"
  log_file="$LOG_DIR/${id}-${action}-$(date -u '+%Y%m%dT%H%M%SZ').log"
  log "patch $id: $action"
  if bash "$file" "$action" 2>&1 | tee "$log_file"; then
    return 0
  fi
  printf 'Patch log: %s\n' "$log_file" >&2
  return 1
}

check_root_requirement() {
  local file="$1" id
  if patch_requires_root "$file" && [[ "$EUID" -ne 0 ]]; then
    id="$(patch_id_of "$file")"
    printf 'patch %s requires root privileges\n' "$id" >&2
    return 1
  fi
}

apply_one() {
  local file="$1" id desc state_file
  id="$(patch_id_of "$file")"
  desc="$(patch_description_of "$file")"
  state_file="$(state_file_for "$id")"

  validate_patch_file "$file" || return 1
  check_root_requirement "$file" || return 1

  if [[ -f "$state_file" ]]; then
    if ! hash_matches_state "$id" "$file"; then
      printf 'patch %s script changed after application; refusing to continue\n' "$id" >&2
      return 1
    fi
    if run_patch_action "$file" verify; then
      log "patch $id already applied and verified: $desc"
      return 0
    fi
    printf 'patch %s is marked applied but verification failed\n' "$id" >&2
    return 1
  fi

  if run_patch_action "$file" verify; then
    write_state "$id" "$file"
    log "patch $id was already present; state recorded: $desc"
    return 0
  fi

  run_patch_action "$file" check || return 1
  run_patch_action "$file" apply || return 1
  run_patch_action "$file" verify || {
    printf 'patch %s applied but verification failed\n' "$id" >&2
    return 1
  }
  write_state "$id" "$file"
  log "patch $id applied successfully: $desc"
}

verify_one() {
  local file="$1" id state_file
  id="$(patch_id_of "$file")"
  state_file="$(state_file_for "$id")"
  validate_patch_file "$file" || return 1
  if [[ ! -f "$state_file" ]]; then
    printf 'patch %s is not marked as applied\n' "$id" >&2
    return 1
  fi
  if ! hash_matches_state "$id" "$file"; then
    printf 'patch %s script hash differs from applied state\n' "$id" >&2
    return 1
  fi
  run_patch_action "$file" verify
}

check_one() {
  local file="$1"
  validate_patch_file "$file" || return 1
  check_root_requirement "$file" || return 1
  run_patch_action "$file" check
}

rollback_one() {
  local file="$1" id state_file
  id="$(patch_id_of "$file")"
  state_file="$(state_file_for "$id")"
  [[ -f "$state_file" ]] || fail "patch $id is not marked as applied"
  check_root_requirement "$file" || return 1
  if ! bash "$file" supports-rollback 2>/dev/null | grep -qx '1'; then
    fail "patch $id does not declare rollback support"
  fi
  run_patch_action "$file" rollback
  rm -f -- "$state_file"
  log "patch $id rollback completed"
}

for_selected() {
  local selector="$1" callback="$2" file found=0
  if [[ "$selector" != "all" ]]; then
    file="$(find_patch "$selector" || true)"
    [[ -n "$file" ]] || fail "patch not found: $selector"
    "$callback" "$file"
    return
  fi

  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    found=1
    "$callback" "$file"
  done < <(patch_files)

  if [[ "$found" -eq 0 ]]; then
    log "no patches discovered"
  fi
}

list_patches() {
  local file id desc
  printf '%-6s %s\n' 'ID' 'DESCRIPTION'
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    id="$(patch_id_of "$file")"
    desc="$(patch_description_of "$file")"
    printf '%-6s %s\n' "$id" "$desc"
  done < <(patch_files)
}

status_patches() {
  local file id desc state status
  printf '%-6s %-12s %s\n' 'ID' 'STATUS' 'DESCRIPTION'
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    id="$(patch_id_of "$file")"
    desc="$(patch_description_of "$file")"
    state="$(state_file_for "$id")"
    status='pending'
    if [[ -f "$state" ]]; then
      if ! hash_matches_state "$id" "$file"; then
        status='drift'
      elif bash "$file" verify >/dev/null 2>&1; then
        status='verified'
      else
        status='failed'
      fi
    elif bash "$file" verify >/dev/null 2>&1; then
      status='untracked'
    fi
    printf '%-6s %-12s %s\n' "$id" "$status" "$desc"
  done < <(patch_files)
}

patch_doctor() {
  local failures=0 file id state
  validate_all_patch_files || failures=$((failures + 1))
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    id="$(patch_id_of "$file")"
    state="$(state_file_for "$id")"
    if [[ -f "$state" ]]; then
      if ! hash_matches_state "$id" "$file"; then
        printf 'FAIL patch %s: script drift detected\n' "$id" >&2
        failures=$((failures + 1))
      elif ! bash "$file" verify >/dev/null 2>&1; then
        printf 'FAIL patch %s: verification failed\n' "$id" >&2
        failures=$((failures + 1))
      else
        printf 'PASS patch %s\n' "$id"
      fi
    fi
  done < <(patch_files)
  [[ "$failures" -eq 0 ]]
}

command="${1:-help}"
selector="${2:-all}"

case "$command" in
  list)
    list_patches
    ;;
  status)
    status_patches
    ;;
  check)
    for_selected "$selector" check_one
    ;;
  apply)
    [[ $# -ge 2 ]] || fail 'apply requires a patch id or all'
    acquire_lock
    for_selected "$selector" apply_one
    ;;
  verify)
    for_selected "$selector" verify_one
    ;;
  rollback)
    [[ $# -eq 2 && "$selector" != 'all' ]] || fail 'rollback requires one patch id'
    acquire_lock
    file="$(find_patch "$selector" || true)"
    [[ -n "$file" ]] || fail "patch not found: $selector"
    rollback_one "$file"
    ;;
  doctor)
    patch_doctor
    ;;
  version)
    printf '%s %s\n' "$PROGRAM_NAME" "$PROGRAM_VERSION"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
