# Deployment patches

Application and infrastructure changes are delivered as small, idempotent Bash patches.

Each patch implements `check`, `apply`, `verify` and, where safe, `rollback`.
New patch directory names use a numeric prefix and a concise purpose.
