#!/usr/bin/env bash
# OWNER: Track C — Interactive helper for testing FitCheck VTO Demo (MOCK & LIVE)
# Usage: ./proxy/scripts/demo_try_cases.sh [--help|check|mock|live|snap <name>|stop]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

export ANDROID_HOME="${ANDROID_HOME:-/home/lamb/Android/Sdk}"
export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

PORT="${TRYON_PORT:-8788}"
ENV_FILE="${TRYON_ENV_FILE:-${HOME}/.fitcheckai_private/tryon.env}"
STATE_DIR="${TRYON_STATE_DIR:-${HOME}/.fitcheckai_private/vto_server_state}"
LEDGER_FILE="${STATE_DIR}/tryon_jobs_ledger.json"
SHOTS_DIR="${HOME}/fitcheck_try_shots"
BASELINE_FILE="/tmp/fitcheck_try_baseline_used.txt"

DEMO_APK="${REPO_ROOT}/feature/tryon/build/outputs/apk/FitCheck-VTO-Demo-debug.apk"
DEMO_PKG="com.fitcheckai.feature.tryon.test"
DEMO_ACT="${DEMO_PKG}/com.fitcheckai.feature.tryon.demo.TryOnDemoActivity"

print_help() {
  cat <<'EOF'
FitCheck VTO Demo — Trợ lý tự kiểm thử các ca ACC2 (MOCK & LIVE)

Cách dùng:
  ./proxy/scripts/demo_try_cases.sh --help      Hiện hướng dẫn này
  ./proxy/scripts/demo_try_cases.sh check       Kiểm tra máy ảo, APK demo, 8 album ảnh & preflight/quota
  ./proxy/scripts/demo_try_cases.sh mock        Bật FOSTA Mock ($0.00), cài/mở FitCheck VTO Demo, in ca M1..M5
  ./proxy/scripts/demo_try_cases.sh live        Kiểm tra cổng an toàn + hỏi YES rồi mới bật FOSTA LIVE, in ca L1..L4
  ./proxy/scripts/demo_try_cases.sh snap <tên>  Chụp màn hình máy ảo vào ~/fitcheck_try_shots/ & in usedLiveRequests
  ./proxy/scripts/demo_try_cases.sh stop        Dừng backend tại cổng 8788 (giữ nguyên sổ cái quota)

Quy tắc an toàn:
  - Script KHÔNG BAO GIỜ tự sửa hay in bí mật trong ~/.fitcheckai_private/tryon.env.
  - Tài liệu hướng dẫn chi tiết từng bước: docs/execution/tracks/C_TRY_IT.md
EOF
}

require_device() {
  local state boot
  state="$(adb get-state </dev/null 2>/dev/null || true)"
  if [[ "${state}" != "device" ]]; then
    echo "[ERROR] Không thấy máy ảo/thiết bị ADB đang sẵn sàng (state='${state:-none}'). Hãy khởi động Pixel_6 trước." >&2
    exit 1
  fi
  boot="$(adb shell getprop sys.boot_completed </dev/null 2>/dev/null | tr -d '\r\n' || true)"
  if [[ "${boot}" != "1" ]]; then
    echo "[ERROR] Thiết bị chưa khởi động xong (sys.boot_completed='${boot}')." >&2
    exit 1
  fi
}

fetch_preflight_json() {
  curl -fsS --max-time 3 "http://127.0.0.1:${PORT}/v2/tryon/preflight" 2>/dev/null || true
}

read_ledger_used() {
  python3 - "${LEDGER_FILE}" <<'PY'
import json, os, sys
path = sys.argv[1]
if not os.path.isfile(path):
    print("0")
    raise SystemExit(0)
try:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    val = int(data.get("usedLiveRequests", 0) or 0)
    print(str(max(0, val)))
except Exception:
    print("0")
PY
}

read_used_live_requests() {
  local preflight_json
  preflight_json="$(fetch_preflight_json)"
  if [[ -n "${preflight_json}" ]]; then
    python3 - "${preflight_json}" <<'PY'
import json, sys
try:
    data = json.loads(sys.argv[1])
    print(str(int(data.get("usedLiveRequests", 0) or 0)))
except Exception:
    print("0")
PY
  else
    read_ledger_used
  fi
}

save_baseline_if_missing() {
  local cur="$1"
  if [[ ! -f "${BASELINE_FILE}" ]]; then
    printf "%s\n" "${cur}" >"${BASELINE_FILE}"
  fi
}

print_mock_cases() {
  cat <<'EOF'

=== DANH SÁCH CA TỰ THỬ — MOCK ($0.00, 0 lượt LIVE) ===
  M1: Mặt trước 4 món (Áo + Quần + Mũ + Giày) — chọn đủ 4 ảnh *_front + person_front,
      kiểm tra "Sẽ thử: Áo + Quần + Mũ + Giày" & nhắc chân rõ, tick đồng ý -> "Tạo ảnh thử đồ".
  M2: Bỏ ảnh Áo & Quần -> chỉ còn "Sẽ thử: Mũ + Giày", tick đồng ý -> "Tạo ảnh thử đồ".
  M3: Bỏ hết cả 4 món -> kiểm tra "Chưa chọn món nào — hãy thêm ảnh cho ít nhất một ô ở trên"
      và nút "Tạo ảnh thử đồ" bị khóa.
  M4: Mặt sau với Áo (top_back) + Mũ KHÔNG có ảnh sau (chỉ hat_front) -> chọn "Chỉ mặt sau",
      kiểm tra chip mở bình thường & hiện nhắc "Mũ/Giày chưa có ảnh sau: AI sẽ tự suy ra mặt sau...".
  M5: Từ M4 (đang ở "Chỉ mặt sau"), bấm "Bỏ ảnh sau" trên thẻ Áo -> KHÔNG tự nhảy về Mặt trước,
      nút bị khóa kèm lỗi đỏ "Không thể tạo mặt sau: thiếu ảnh sau của Áo.".

Chụp kiểm chứng sau mỗi ca:
  ./proxy/scripts/demo_try_cases.sh snap <tên_ca>
EOF
}

print_live_cases() {
  cat <<'EOF'

=== DANH SÁCH CA TỰ THỬ — FOSTA LIVE (Mỗi lần bấm Tạo = 1 lượt thật) ===
  L1 (1 lượt): Mặt trước · "Ảnh gốc" — Áo + Quần + Mũ + Giày (person_front + 4 *_front).
  L2 (1 lượt): Mặt trước · "Ảnh gốc" — chỉ Mũ hoặc Mũ + Giày (bấm "Bỏ ảnh" ở Áo & Quần; kiểm tra áo/quần gốc không đổi).
  L3 (1 lượt): "Chỉ mặt sau" · "Ảnh gốc" — Áo + Quần đủ ảnh sau + Mũ chỉ có ảnh trước (AI tự suy ra mặt sau mũ).
  L4 (Tùy chọn nếu còn quota): "Nền trắng studio" cho bộ nhiều món.

Nhớ chụp sau mỗi lượt bấm Tạo & kiểm tra delta lượt dùng:
  ./proxy/scripts/demo_try_cases.sh snap <tên_ca>
Khi thử xong, chạy:
  ./proxy/scripts/demo_try_cases.sh stop
EOF
}

cmd_check() {
  echo "=== FitCheck VTO Demo — Kiểm tra môi trường ==="
  require_device
  local state installed media_raw preflight_json used_now
  state="$(adb get-state </dev/null)"
  echo "[1] Máy ảo ADB: OK (${state}, sys.boot_completed=1)"

  if [[ -f "${DEMO_APK}" ]]; then
    echo "[2] APK Demo: ĐÃ CÓ (${DEMO_APK}, $(du -h "${DEMO_APK}" | awk '{print $1}'))"
  else
    echo "[2] APK Demo: CHƯA BUILD (${DEMO_APK}) — chạy 'mock' hoặc ./proxy/scripts/demo_build_apk.sh"
  fi

  installed="$(adb shell pm list packages "${DEMO_PKG}" </dev/null 2>/dev/null | tr -d '\r' || true)"
  if [[ "${installed}" == *"${DEMO_PKG}"* ]]; then
    echo "[3] Gói cài trên Pixel_6: ĐÃ CÀI (${DEMO_PKG})"
  else
    echo "[3] Gói cài trên Pixel_6: CHƯA CÀI (${DEMO_PKG})"
  fi

  media_raw="$(adb shell content query --uri content://media/external/images/media --projection bucket_display_name:_display_name </dev/null 2>/dev/null || true)"
  python3 - "${media_raw}" <<'PY'
import re, sys
buckets = set()
for line in sys.argv[1].splitlines():
    m = re.search(r"bucket_display_name=([^,\r\n]+)", line)
    if m:
        buckets.add(m.group(1).strip())
expected = {
    "person_front", "person_back",
    "top_front", "top_back",
    "bottom_front", "bottom_back",
    "hat_front", "shoes_front",
}
missing = sorted(expected - buckets)
print(f"[4] Album MediaStore trên máy ảo: {len(buckets)} album -> {sorted(buckets)}")
if missing:
    print(f"    [CẢNH BÁO] Còn thiếu {len(missing)} album chuẩn ACC2: {missing}")
else:
    print("    Đủ cả 8 album thử đồ chuẩn ACC2 (person/top/bottom Trước+Sau + hat_front + shoes_front).")
PY

  preflight_json="$(fetch_preflight_json)"
  if [[ -n "${preflight_json}" ]]; then
    python3 - "${preflight_json}" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
print(
    "[5] Backend cổng 8788: ĐANG CHẠY | "
    f"provider={d.get('provider')} | liveEnabled={d.get('liveEnabled')} | "
    f"offlineMockEnabled={d.get('offlineMockEnabled')} | "
    f"usedLiveRequests={d.get('usedLiveRequests')}/{d.get('maxRequests')} | "
    f"remainingLiveRequests={d.get('remainingLiveRequests')}"
)
PY
    used_now="$(read_used_live_requests)"
  else
    used_now="$(read_ledger_used)"
    echo "[5] Backend cổng 8788: ĐANG TẮT | Sổ cái bền vững ghi nhận usedLiveRequests=${used_now}"
  fi
  save_baseline_if_missing "${used_now}"
}

ensure_demo_apk_built() {
  if [[ ! -f "${DEMO_APK}" ]]; then
    echo "[FitCheck Demo] Chưa thấy APK Demo, đang build..."
    "${SCRIPT_DIR}/demo_build_apk.sh"
  fi
}

install_and_launch_demo() {
  require_device
  ensure_demo_apk_built
  echo "[FitCheck Demo] Đang cài APK Demo (giữ nguyên 8 album MediaStore)..."
  adb install -r -t "${DEMO_APK}" </dev/null
  echo "[FitCheck Demo] Đang mở FitCheck VTO Demo (backend_fosta)..."
  adb shell am force-stop "${DEMO_PKG}" </dev/null || true
  adb shell am start -n "${DEMO_ACT}" \
    --es tryon_demo_mode backend_fosta \
    --ez tryon_collapse_controls true </dev/null
}

cmd_mock() {
  ensure_demo_apk_built
  "${SCRIPT_DIR}/demo_start_mock_backend.sh" --daemon
  local baseline
  baseline="$(read_used_live_requests)"
  printf "%s\n" "${baseline}" >"${BASELINE_FILE}"
  install_and_launch_demo
  echo "[FitCheck Demo] Chế độ MOCK sẵn sàng (\$0.00). Baseline usedLiveRequests=${baseline}"
  print_mock_cases
}

cmd_live() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "[TỪ CHỐI] Không tìm thấy ${ENV_FILE}. Tạo ngoài repo từ proxy/scripts/tryon.env.example trước." >&2
    exit 1
  fi

  local check_out
  check_out="$(
    python3 - "${ENV_FILE}" "${LEDGER_FILE}" <<'PY'
import json, os, sys
env_path, ledger_path = sys.argv[1], sys.argv[2]
vals = {}
with open(env_path, "r", encoding="utf-8") as f:
    for raw in f:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        vals[k.strip()] = v.strip().strip('"').strip("'")

key = vals.get("FOSTA_API_KEY", "")
has_valid_key = (
    "VALID"
    if (key and "REPLACE" not in key.upper() and "YOUR_" not in key.upper() and len(key) >= 10)
    else "INVALID"
)
live_enabled = vals.get("TRYON_LIVE_ENABLED", "false").lower() == "true"
fosta_approve = vals.get("FOSTA_APPROVE_QUOTA", "false").lower() == "true"
try:
    max_req = int(vals.get("TRYON_LIVE_MAX_REQUESTS", "1") or "1")
except ValueError:
    max_req = 1

used = 0
if os.path.isfile(ledger_path):
    try:
        with open(ledger_path, "r", encoding="utf-8") as lf:
            used = int(json.load(lf).get("usedLiveRequests", 0) or 0)
    except Exception:
        used = 0
remaining = max(0, max_req - used)
print(f"HAS_KEY={has_valid_key}")
print(f"LIVE_ENABLED={'true' if live_enabled else 'false'}")
print(f"FOSTA_APPROVE={'true' if fosta_approve else 'false'}")
print(f"USED={used}")
print(f"MAX={max_req}")
print(f"REMAINING={remaining}")
PY
  )"

  local HAS_KEY LIVE_ENABLED FOSTA_APPROVE USED MAX REMAINING
  eval "${check_out}"

  local missing_reasons=()
  if [[ "${HAS_KEY}" != "VALID" ]]; then
    missing_reasons+=("FOSTA_API_KEY chưa hợp lệ")
  fi
  if [[ "${LIVE_ENABLED}" != "true" ]]; then
    missing_reasons+=("TRYON_LIVE_ENABLED chưa đặt true")
  fi
  if [[ "${FOSTA_APPROVE}" != "true" ]]; then
    missing_reasons+=("FOSTA_APPROVE_QUOTA chưa đặt true")
  fi

  if (( ${#missing_reasons[@]} > 0 )); then
    echo "[TỪ CHỐI BẬT LIVE] Chưa đủ điều kiện trong ~/.fitcheckai_private/tryon.env:" >&2
    for r in "${missing_reasons[@]}"; do
      echo "  - ${r}" >&2
    done
    echo "Hãy tự chỉnh các cờ trên trong ~/.fitcheckai_private/tryon.env rồi chạy lại (dùng 'mock' nếu chỉ cần kiểm thử \$0.00)." >&2
    exit 1
  fi

  if (( REMAINING <= 0 )); then
    echo "[TỪ CHỐI BẬT LIVE] Đã hết hạn mức lượt LIVE (đã dùng ${USED}/${MAX}, còn 0 lượt)." >&2
    echo "Tăng TRYON_LIVE_MAX_REQUESTS trong ~/.fitcheckai_private/tryon.env nếu bạn chủ đích cấp thêm lượt." >&2
    exit 1
  fi

  echo "========================================================================"
  echo "CẢNH BÁO QUOTA FOSTA LIVE:"
  echo "  Mỗi lần bấm 'Tạo ảnh thử đồ' = 1 lượt gọi AI thật."
  echo "  Hạn mức còn lại: ${REMAINING} lượt (đã dùng ${USED}/${MAX})."
  echo "========================================================================"
  printf "Nhập chính xác chữ YES để bật FOSTA LIVE: "
  local reply=""
  if ! IFS= read -r reply; then
    echo ""
    echo "[HỦY] Không nhận được xác nhận YES."
    exit 1
  fi
  if [[ "${reply}" != "YES" ]]; then
    echo "[HỦY] Bạn nhập '${reply}' (không phải YES). Giữ nguyên tắt LIVE."
    exit 1
  fi

  "${SCRIPT_DIR}/demo_start_fosta_live_backend.sh" --daemon
  local baseline
  baseline="$(read_used_live_requests)"
  printf "%s\n" "${baseline}" >"${BASELINE_FILE}"
  install_and_launch_demo
  echo "[FitCheck Demo] Chế độ FOSTA LIVE đang mở. Baseline usedLiveRequests=${baseline} (còn ${REMAINING} lượt)."
  print_live_cases
}

cmd_snap() {
  local raw_name="${1:-}"
  if [[ -z "${raw_name}" ]]; then
    echo "[ERROR] Cần truyền tên ảnh: ./proxy/scripts/demo_try_cases.sh snap <tên>" >&2
    exit 1
  fi
  local safe_name
  safe_name="$(printf "%s" "${raw_name}" | tr -cs 'A-Za-z0-9_-' '_' | sed 's/^_*//;s/_*$//')"
  if [[ -z "${safe_name}" ]]; then
    safe_name="shot"
  fi
  require_device
  mkdir -p "${SHOTS_DIR}"
  local ts out_file
  ts="$(date +%Y%m%d_%H%M%S)"
  out_file="${SHOTS_DIR}/${ts}_${safe_name}.png"
  adb exec-out screencap -p </dev/null >"${out_file}"
  if [[ ! -s "${out_file}" ]]; then
    rm -f "${out_file}"
    echo "[ERROR] Chụp màn hình thất bại (file rỗng)." >&2
    exit 1
  fi
  local cur_used base_used delta
  cur_used="$(read_used_live_requests)"
  if [[ -f "${BASELINE_FILE}" ]]; then
    base_used="$(cat "${BASELINE_FILE}" 2>/dev/null || echo "${cur_used}")"
  else
    base_used="${cur_used}"
    printf "%s\n" "${base_used}" >"${BASELINE_FILE}"
  fi
  delta=$((cur_used - base_used))
  echo "[SNAP OK] Đã lưu ảnh: ${out_file} ($(du -h "${out_file}" | awk '{print $1}'))"
  echo "[QUOTA]   usedLiveRequests=${cur_used} (đầu phiên=${base_used}, chênh lệch=+${delta})"
}

cmd_stop() {
  "${SCRIPT_DIR}/demo_stop_backend.sh"
  local used_now
  used_now="$(read_ledger_used)"
  echo "[QUOTA] Sổ cái bền vững hiện ghi nhận usedLiveRequests=${used_now}."
  echo "[NHẮC AN TOÀN] Nếu bạn vừa tự bật TRYON_LIVE_ENABLED=true trong ~/.fitcheckai_private/tryon.env, hãy đặt lại TRYON_LIVE_ENABLED=false sau khi thử xong."
}

SUBCMD="${1:---help}"
case "${SUBCMD}" in
  --help|-h|help)
    print_help
    ;;
  check)
    cmd_check
    ;;
  mock)
    cmd_mock
    ;;
  live)
    cmd_live
    ;;
  snap)
    shift || true
    cmd_snap "${1:-}"
    ;;
  stop)
    cmd_stop
    ;;
  *)
    echo "[ERROR] Lệnh không hợp lệ: ${SUBCMD}" >&2
    print_help >&2
    exit 1
    ;;
esac
