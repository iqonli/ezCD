#!/bin/sh
# Use a preinstalled Gradle and cached dependencies; never invoke a downloading wrapper.
set -eu
cd "$(dirname "$0")"
if [ -n "${EZCD_GRADLE:-}" ]; then
    exec "$EZCD_GRADLE" --offline :app:assembleDebug "$@"
fi
if command -v gradle >/dev/null 2>&1; then
    exec gradle --offline :app:assembleDebug "$@"
fi
printf '%s\n' 'No local Gradle found. Set EZCD_GRADLE to your existing Gradle executable.' >&2
exit 1
