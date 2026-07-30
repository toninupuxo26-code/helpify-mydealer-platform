#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
AUTH="$ROOT/implementation/android-native/core/src/main/java/app/shared/core/BaseAuthActivity.kt"
DASH="$ROOT/implementation/android-native/core/src/main/java/app/shared/core/BaseDashboardActivity.kt"
SCENARIO="$ROOT/implementation/android-native/core/src/main/java/app/shared/core/ScenarioStore.kt"

python3 - "$AUTH" "$DASH" "$SCENARIO" <<'PY'
from pathlib import Path
import sys

auth = Path(sys.argv[1]).read_text(encoding="utf-8")
dash = Path(sys.argv[2]).read_text(encoding="utf-8")
scenario = Path(sys.argv[3]).read_text(encoding="utf-8")

checks = [
    (
        "authentication prefix newline literal",
        r'prefix = "Демонстрационные аккаунты:\n"' in auth,
    ),
    (
        "authentication separator newline literal",
        r'separator = "\n"' in auth,
    ),
    (
        "dashboard profile newline literals",
        r'profileText.text = "${user.name}\n${user.email}\nРоль: ${user.role}"'
        in dash,
    ),
    (
        "dashboard card uses buildString",
        "text = buildString {" in dash,
    ),
    (
        "dashboard card description newline literal",
        r'append("\n${card.description}")' in dash,
    ),
    (
        "dashboard scenario progress",
        "val progress = if (card.steps.isEmpty())" in dash
        and r'" · $step/${card.steps.size}"' in dash,
    ),
    (
        "dashboard refresh function retained",
        "private fun refreshProfile()" in dash,
    ),
    (
        "dashboard reset action",
        "scenarioStore.resetAll()" in dash,
    ),
    (
        "scenario state persistence",
        "getSharedPreferences" in scenario
        and "fun advance" in scenario
        and "fun resetAll" in scenario,
    ),
]

failures = 0
for label, result in checks:
    if result:
        print(f"PASS  {label}")
    else:
        print(f"FAIL  {label}")
        failures += 1

for label, text in (
    ("authentication", auth),
    ("dashboard", dash),
):
    if r"\\n" in text:
        print(f"FAIL  {label} contains double-backslash newline sequences")
        failures += 1
    else:
        print(f"PASS  {label} contains single-backslash newline sequences")

passed = len(checks) + 2 - failures
print(f"PASS={passed} FAIL={failures}")
raise SystemExit(1 if failures else 0)
PY
