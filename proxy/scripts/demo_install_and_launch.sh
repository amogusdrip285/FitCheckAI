#!/usr/bin/env bash
# OWNER: Track C — Install and launch FitCheck VTO Demo on Pixel_6 Emulator
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

export ANDROID_HOME="${ANDROID_HOME:-/home/lamb/Android/Sdk}"
export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

AVD_NAME="${AVD_NAME:-Pixel_6}"
APK_PATH="${REPO_ROOT}/feature/tryon/build/outputs/apk/FitCheck-VTO-Demo-debug.apk"
DEMO_MODE="${TRYON_DEMO_MODE:-backend_fosta}"

if [[ ! -f "${APK_PATH}" ]]; then
  echo "[FitCheck VTO Demo] APK not found at ${APK_PATH}. Building now..."
  "${SCRIPT_DIR}/demo_build_apk.sh"
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "[FitCheck VTO Demo] Starting Android Emulator (${AVD_NAME})..."
  nohup "${ANDROID_HOME}/emulator/emulator" -avd "${AVD_NAME}" -no-snapshot-save -no-audio >/tmp/fitcheck_emulator.log 2>&1 &
  echo "[FitCheck VTO Demo] Waiting for emulator to boot..."
  adb wait-for-device
  while [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    sleep 2
  done
  sleep 2
fi

echo "[FitCheck VTO Demo] Installing ${APK_PATH}..."
adb install -r -t "${APK_PATH}"

# Seed sample images into Android Gallery (/sdcard/Pictures/FitCheckDemoGallery/) so Gallery picker always has photos ready
GALLERY_DIR="/sdcard/Pictures/FitCheckDemoGallery"
adb shell mkdir -p "${GALLERY_DIR}"

python3 - <<'PY'
import os, struct, zlib

out_dir = "/tmp/fitcheck_demo_gallery_seed"
os.makedirs(out_dir, exist_ok=True)

def write_solid_png(path, width, height, r, g, b):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            # subtle vertical gradient + center frame
            in_box = (width * 0.22 <= x <= width * 0.78) and (height * 0.18 <= y <= height * 0.82)
            cr = min(255, max(0, r + (25 if in_box else 0) - int(y * 18 / height)))
            cg = min(255, max(0, g + (22 if in_box else 0) - int(y * 18 / height)))
            cb = min(255, max(0, b + (18 if in_box else 0) - int(y * 18 / height)))
            raw.extend((cr, cg, cb))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 6))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

samples = [
    ("01_person_front_gallery.png", (215, 198, 182)),
    ("02_person_back_gallery.png", (196, 184, 172)),
    ("03_top_front_gallery.png", (178, 92, 66)),
    ("04_top_back_gallery.png", (152, 78, 56)),
    ("05_bottom_front_gallery.png", (62, 82, 108)),
    ("06_bottom_back_gallery.png", (52, 70, 94)),
]
for name, (r, g, b) in samples:
    write_solid_png(os.path.join(out_dir, name), 384, 512, r, g, b)
PY

for img in /tmp/fitcheck_demo_gallery_seed/*.png; do
  base="$(basename "${img}")"
  adb push "${img}" "${GALLERY_DIR}/${base}" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://${GALLERY_DIR}/${base}" >/dev/null || true
done

echo "[FitCheck VTO Demo] Launching FitCheck VTO Demo (mode=${DEMO_MODE})..."
adb shell am force-stop com.fitcheckai.feature.tryon.test || true
adb shell am start -n com.fitcheckai.feature.tryon.test/com.fitcheckai.feature.tryon.demo.TryOnDemoActivity \
  --es tryon_demo_mode "${DEMO_MODE}" \
  --ez tryon_collapse_controls true

echo "[FitCheck VTO Demo] Launched on Emulator! App label in Launcher: 'FitCheck VTO Demo'."
