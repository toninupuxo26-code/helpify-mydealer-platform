#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
MODE="${1:-all}"; PASS=0; WARN=0; FAIL=0
pass(){ PASS=$((PASS+1)); echo "PASS  $*"; }; warn(){ WARN=$((WARN+1)); echo "WARN  $*"; }; fail(){ FAIL=$((FAIL+1)); echo "FAIL  $*"; }
check_source(){
  local p marker f
  while read -r p marker; do
    [[ -f "$ROOT/$p/index.html" ]] && pass "$p index" || fail "$p index missing"
    grep -q "$marker" "$ROOT/$p/index.html" && pass "$p marker" || fail "$p marker missing"
    [[ -s "$ROOT/$p/assets/app.css" && -s "$ROOT/$p/assets/api-client.js" && -s "$ROOT/$p/assets/app.js" ]] && pass "$p assets" || fail "$p assets missing"
    if command -v node >/dev/null 2>&1; then node --check "$ROOT/$p/assets/api-client.js" >/dev/null && node --check "$ROOT/$p/assets/app.js" >/dev/null && pass "$p JavaScript syntax" || fail "$p JavaScript syntax"; else warn "node unavailable; JavaScript syntax skipped"; fi
    if grep -Rqi 'web.archive.org\|web-static.archive.org\|wm-ipp' "$ROOT/$p"; then fail "$p contains Wayback runtime references"; else pass "$p has no Wayback runtime references"; fi
  done <<DATA
implementation/helpify/web-app/demo helpify-web-demo
implementation/mydealer/web-app/demo mydealer-web-demo
DATA
}
check_vps(){
  local domain marker
  while read -r domain marker; do
    [[ -f "/var/www/$domain/current/demo/index.html" ]] && pass "$domain deployed files" || { fail "$domain deployed files missing"; continue; }
    local body=""
    body="$(curl -kfsS --resolve "$domain:443:127.0.0.1" "https://$domain/demo/" 2>/dev/null || true)"
    if grep -q "$marker" <<<"$body"; then pass "$domain HTTPS demo"; else
      body="$(curl -fsS -H "Host: $domain" "http://127.0.0.1/demo/" 2>/dev/null || true)"
      if grep -q "$marker" <<<"$body"; then pass "$domain HTTP demo"; else fail "$domain demo response"; fi
    fi
  done <<DATA
helpsiffyy.app helpify-web-demo
mydealers.app mydealer-web-demo
DATA
}
case "$MODE" in source) check_source;; vps) check_vps;; all) check_source; [[ $EUID -eq 0 ]] && check_vps || warn 'VPS checks skipped without root';; *) echo "Usage: $0 [source|vps|all]" >&2; exit 64;; esac
echo "PASS=$PASS WARN=$WARN FAIL=$FAIL"; ((FAIL==0))
