#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <sha256-manifest>" >&2
  exit 2
fi

sha256sum -c "$1"
