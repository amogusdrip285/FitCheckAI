#!/usr/bin/env bash
# OWNER: Track C — Start FitCheck Try-On Local Server in FOSTA LIVE mode (gpt-6-luna)
# Reads FOSTA_API_KEY strictly from ~/.fitcheckai_private/tryon.env (never prints secrets).
# Usage:
#   ./proxy/scripts/demo_start_fosta_live_backend.sh           # Run in foreground
#   ./proxy/scripts/demo_start_fosta_live_backend.sh --daemon  # Run in background and verify preflight
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROXY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:${PATH}"

ENV_FILE="${TRYON_ENV_FILE:-${HOME}/.fitcheckai_private/tryon.env}"
PORT="${TRYON_PORT:-8788}"
PID_FILE="/tmp/fitcheck_tryon_backend_${PORT}.pid"
LOG_FILE="/tmp/fitcheck_tryon_live_backend_${PORT}.log"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Không tìm thấy file cấu hình bảo mật tại: ${ENV_FILE}" >&2
  echo "Hãy tạo file ${ENV_FILE} (ngoài Git) theo mẫu:" >&2
  echo "  mkdir -p ~/.fitcheckai_private && chmod 700 ~/.fitcheckai_private" >&2
  echo "  cp ${SCRIPT_DIR}/tryon.env.example ${ENV_FILE}" >&2
  echo "  chmod 600 ${ENV_FILE}" >&2
  echo "Sau đó điền FOSTA_API_KEY thật và bật TRYON_LIVE_ENABLED=true." >&2
  exit 1
fi

# Validate FOSTA_API_KEY presence without printing any secret value
HAS_VALID_KEY="$(
  python3 - "${ENV_FILE}" <<'PY'
import sys

env_path = sys.argv[1]
vals = {}
with open(env_path, "r", encoding="utf-8") as f:
    for raw in f:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        v = v.strip().strip('"').strip("'")
        vals[k.strip()] = v

key = vals.get("FOSTA_API_KEY", "")
if not key or "REPLACE" in key.upper() or "YOUR_" in key.upper() or len(key) < 10:
    print("INVALID")
else:
    print("VALID")
PY
)"

if [[ "${HAS_VALID_KEY}" != "VALID" ]]; then
  echo "[ERROR] FOSTA_API_KEY trong ${ENV_FILE} đang trống hoặc vẫn là giá trị mẫu." >&2
  echo "Vui lòng mở ${ENV_FILE} và cập nhật FOSTA_API_KEY hợp lệ trước khi chạy LIVE." >&2
  exit 1
fi

"${SCRIPT_DIR}/demo_stop_backend.sh" >/dev/null 2>&1 || true

export TRYON_ENV_FILE="${ENV_FILE}"
export TRYON_BIND_HOST="127.0.0.1"
export TRYON_PORT="${PORT}"
export TRYON_PROVIDER="fosta"
export TRYON_OFFLINE_MOCK_FOSTA="false"

cd "${PROXY_DIR}"

if [[ "${1:-}" == "--daemon" ]]; then
  echo "[FitCheck FOSTA LIVE Backend] Starting FOSTA LIVE server in background on http://127.0.0.1:${PORT}..."
  nohup npx tsx scripts/tryon_local_server.ts >"${LOG_FILE}" 2>&1 &
  SERVER_PID=$!
  echo "${SERVER_PID}" >"${PID_FILE}"

  for _ in {1..25}; do
    if curl -fsS "http://127.0.0.1:${PORT}/v2/tryon/preflight" >/dev/null 2>&1; then
      echo "[FitCheck FOSTA LIVE Backend] Server listening (PID=${SERVER_PID}). Preflight status:"
      curl -fsS "http://127.0.0.1:${PORT}/v2/tryon/preflight"
      echo ""
      echo "Gợi ý: Mở ứng dụng 'FitCheck VTO Demo' trên Emulator và bấm nút 'Làm mới' ở thanh trên cùng để nhận chế độ FOSTA LIVE mà không cần build lại APK."
      exit 0
    fi
    sleep 0.4
  done

  echo "[ERROR] FOSTA LIVE backend failed to respond on port ${PORT}. Log output:" >&2
  cat "${LOG_FILE}" >&2 || true
  exit 1
else
  echo "[FitCheck FOSTA LIVE Backend] Starting server with config from ${ENV_FILE} on http://127.0.0.1:${PORT} (Emulator: http://10.0.2.2:${PORT})"
  echo "[FitCheck FOSTA LIVE Backend] Sau khi server lên, bấm nút 'Làm mới' trên màn hình FitCheck VTO Demo để chuyển sang FOSTA LIVE."
  echo "[FitCheck FOSTA LIVE Backend] Press Ctrl+C to stop."
  exec npx tsx scripts/tryon_local_server.ts
fi
