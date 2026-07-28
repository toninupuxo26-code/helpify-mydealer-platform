#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <artifact-directory>" >&2
  exit 2
fi

DIR=$(realpath "$1")
if [[ ! -d "$DIR" ]]; then
  echo "Directory not found: $DIR" >&2
  exit 3
fi

ROOT=$(git rev-parse --show-toplevel)
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
COMMIT=$(git rev-parse HEAD)
VERSION=$(cat "$ROOT/VERSION")
OUT="$DIR/RELEASE-MANIFEST-$VERSION-$STAMP.txt"

{
  echo "repository_version=$VERSION"
  echo "git_commit=$COMMIT"
  echo "generated_at_utc=$STAMP"
  echo
  find "$DIR" -type f ! -name 'RELEASE-MANIFEST-*' -print0 \
    | sort -z \
    | xargs -0 sha256sum
} > "$OUT"

printf 'Created %s\n' "$OUT"
