#!/usr/bin/env bash
# OWNER: Track C — Start FitCheck Try-On Local Server in FOSTA MOCK mode ($0.00, LIVE=OFF)
# Usage:
#   ./proxy/scripts/demo_start_mock_backend.sh           # Run in foreground
#   ./proxy/scripts/demo_start_mock_backend.sh --daemon  # Run in background and verify /v2/tryon/preflight
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROXY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:${PATH}"

PORT="${TRYON_PORT:-8788}"
PID_FILE="/tmp/fitcheck_tryon_backend_${PORT}.pid"
LOG_FILE="/tmp/fitcheck_tryon_mock_backend_${PORT}.log"

"${SCRIPT_DIR}/demo_stop_backend.sh" >/dev/null 2>&1 || true

# Force clean Mock environment (ignores any live settings in tryon.env so Mock mode is 100% $0.00)
export TRYON_ENV_FILE="/dev/null"
export TRYON_BIND_HOST="127.0.0.1"
export TRYON_PORT="${PORT}"
export TRYON_PROVIDER="fosta"
export TRYON_LIVE_ENABLED="false"
export TRYON_OFFLINE_MOCK_FOSTA="true"
export FOSTA_MODEL="gpt-6-luna"

cd "${PROXY_DIR}"

if [[ "${1:-}" == "--daemon" ]]; then
  echo "[FitCheck Mock Backend] Starting FOSTA Mock server in background on http://127.0.0.1:${PORT} (Emulator: http://10.0.2.2:${PORT})..."
  nohup npx tsx scripts/tryon_local_server.ts >"${LOG_FILE}" 2>&1 &
  SERVER_PID=$!
  echo "${SERVER_PID}" >"${PID_FILE}"

  for _ in {1..25}; do
    if curl -fsS "http://127.0.0.1:${PORT}/v2/tryon/preflight" >/dev/null 2>&1; then
      echo "[FitCheck Mock Backend] READY (PID=${SERVER_PID}, Log=${LOG_FILE}):"
      curl -fsS "http://127.0.0.1:${PORT}/v2/tryon/preflight"
      echo ""
      exit 0
    fi
    sleep 0.4
  done

  echo "[ERROR] Mock backend failed to respond on port ${PORT}. Log output:" >&2
  cat "${LOG_FILE}" >&2 || true
  exit 1
else
  echo "[FitCheck Mock Backend] Starting FOSTA Mock server on http://127.0.0.1:${PORT} (Emulator: http://10.0.2.2:${PORT})"
  echo "[FitCheck Mock Backend] Safety: TRYON_LIVE_ENABLED=false | TRYON_OFFLINE_MOCK_FOSTA=true | Cost=\$0.00"
  echo "[FitCheck Mock Backend] Press Ctrl+C to stop."
  exec npx tsx scripts/tryon_local_server.ts
fi
