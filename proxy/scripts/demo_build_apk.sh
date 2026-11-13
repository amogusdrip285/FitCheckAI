#!/usr/bin/env bash
# OWNER: Track C — Build standalone FitCheck VTO Demo APK (:feature:tryon)
# Does not require :app or Track A/B/D integration.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

export ANDROID_HOME="${ANDROID_HOME:-/home/lamb/Android/Sdk}"
export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

cd "${REPO_ROOT}"

echo "[FitCheck VTO Demo] Building standalone :feature:tryon demo APK..."
./gradlew -I feature/tryon/standalone_demo.init.gradle.kts \
  :feature:tryon:assembleDebug \
  :feature:tryon:assembleDebugAndroidTest

RAW_APK="${REPO_ROOT}/feature/tryon/build/outputs/apk/androidTest/debug/tryon-debug-androidTest.apk"
FRIENDLY_APK="${REPO_ROOT}/feature/tryon/build/outputs/apk/FitCheck-VTO-Demo-debug.apk"

if [[ ! -f "${RAW_APK}" ]]; then
  echo "[ERROR] Expected APK not found at ${RAW_APK}" >&2
  exit 1
fi

mkdir -p "$(dirname "${FRIENDLY_APK}")"
cp -f "${RAW_APK}" "${FRIENDLY_APK}"

echo "[FitCheck VTO Demo] Build complete!"
echo "  APK Path: ${FRIENDLY_APK}"
echo "  Size:     $(du -h "${FRIENDLY_APK}" | awk '{print $1}')"
