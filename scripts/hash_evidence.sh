#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <evidence-directory>" >&2
  exit 2
fi

DIR=$(realpath "$1")
if [[ ! -d "$DIR" ]]; then
  echo "Directory not found: $DIR" >&2
  exit 3
fi

ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$ROOT/evidence/manifests/evidence-$STAMP.sha256"

find "$DIR" -type f -print0 \
  | sort -z \
  | xargs -0 sha256sum > "$OUT"

printf 'Created %s\n' "$OUT"
