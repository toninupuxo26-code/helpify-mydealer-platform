#!/usr/bin/env bash
set -Eeuo pipefail

# Produces a sanitized VPS capability report. It intentionally omits IP
# addresses, host name, users, SSH keys, environment variables and secrets.

has() { command -v "$1" >/dev/null 2>&1; }
value_or_unknown() { local value="$1"; [[ -n "$value" ]] && printf '%s' "$value" || printf 'unknown'; }

printf 'HELPIFY_MYDEALER_VPS_INVENTORY=1\n'
printf 'generated_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
printf 'kernel=%s\n' "$(uname -sr)"
printf 'architecture=%s\n' "$(uname -m)"
printf 'cpu_count=%s\n' "$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf 'unknown')"
printf 'memory_mib=%s\n' "$(awk '/MemTotal:/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || printf 'unknown')"
printf 'root_disk_total_gib=%s\n' "$(df -Pk / | awk 'NR==2 {printf "%d", $2/1024/1024}')"
printf 'root_disk_free_gib=%s\n' "$(df -Pk / | awk 'NR==2 {printf "%d", $4/1024/1024}')"
printf 'effective_uid=%s\n' "$EUID"

if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  source /etc/os-release
  printf 'os_id=%s\n' "$(value_or_unknown "${ID:-}")"
  printf 'os_version=%s\n' "$(value_or_unknown "${VERSION_ID:-}")"
  printf 'os_pretty=%s\n' "$(value_or_unknown "${PRETTY_NAME:-}")"
else
  printf 'os_id=unknown\nos_version=unknown\nos_pretty=unknown\n'
fi

printf 'virtualization='; if has systemd-detect-virt; then systemd-detect-virt 2>/dev/null || printf 'none'; else printf 'unknown'; fi; printf '\n'
printf 'systemd_state='; if has systemctl; then systemctl is-system-running 2>/dev/null || printf 'unknown'; else printf 'unavailable'; fi; printf '\n'
printf 'ntp_synchronized='; if has timedatectl; then timedatectl show -p NTPSynchronized --value 2>/dev/null || printf 'unknown'; else printf 'unknown'; fi; printf '\n'

for command_name in git curl openssl rsync tar unzip ufw nft iptables docker; do
  if has "$command_name"; then
    printf 'command_%s=yes\n' "$command_name"
  else
    printf 'command_%s=no\n' "$command_name"
  fi
done

if has docker; then
  printf 'docker_version=%s\n' "$(docker version --format '{{.Client.Version}}' 2>/dev/null || printf 'unavailable')"
  printf 'docker_daemon_reachable='; if docker info >/dev/null 2>&1; then printf 'yes'; else printf 'no'; fi; printf '\n'
  printf 'docker_compose_version=%s\n' "$(docker compose version --short 2>/dev/null || printf 'unavailable')"
fi

printf 'listeners_80_443=';
if has ss; then
  if ss -ltn 2>/dev/null | awk 'NR>1 {print $4}' | grep -Eq '(^|:)(80|443)$'; then printf 'occupied'; else printf 'free'; fi
else
  printf 'unknown'
fi
printf '\n'

printf 'REPORT_SAFE_TO_SHARE=yes\n'
