#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
python3 - \
  "$ROOT/implementation/android-native/core/src/main/java/app/shared/core/BaseAuthActivity.kt" \
  "$ROOT/implementation/android-native/core/src/main/java/app/shared/core/BaseDashboardActivity.kt" <<'PY'
from pathlib import Path
import sys
auth = Path(sys.argv[1]).read_text(encoding='utf-8')
dash = Path(sys.argv[2]).read_text(encoding='utf-8')
checks = [
 ('authentication prefix newline literal', r'prefix = "Демонстрационные аккаунты:\n"' in auth),
 ('authentication separator newline literal', r'separator = "\n"' in auth),
 ('dashboard profile newline literals', r'profileText.text = "${current.name}\n${current.email}\nРоль: ${current.role}"' in dash),
 ('dashboard card newline literals', r'text = "${card.title}\n${card.description}"' in dash),
 ('dashboard refresh function retained', 'private fun refreshProfile()' in dash),
]
fail = 0
for label, ok in checks:
    print(('PASS  ' if ok else 'FAIL  ') + label)
    fail += 0 if ok else 1
print(f'PASS={len(checks)-fail} FAIL={fail}')
raise SystemExit(1 if fail else 0)
PY
