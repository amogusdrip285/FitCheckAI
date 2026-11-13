#!/usr/bin/env bash
# OWNER: Track C — Stop local FitCheck Try-On backend on port 8788
# Preserves durable quota ledger in ~/.fitcheckai_private/vto_server_state/
set -euo pipefail

PORT="${TRYON_PORT:-8788}"
PID_FILE="/tmp/fitcheck_tryon_backend_${PORT}.pid"

if [[ -f "${PID_FILE}" ]]; then
  PID="$(cat "${PID_FILE}" 2>/dev/null || true)"
  if [[ -n "${PID}" ]] && kill -0 "${PID}" 2>/dev/null; then
    echo "[FitCheck Backend] Stopping server PID ${PID}..."
    kill "${PID}" 2>/dev/null || true
    sleep 1
  fi
  rm -f "${PID_FILE}"
fi

# Also clean up any remaining tryon_local_server.ts processes on this port
PIDS="$(pgrep -f "tryon_local_server.ts" || true)"
if [[ -n "${PIDS}" ]]; then
  echo "[FitCheck Backend] Terminating remaining tryon_local_server.ts process(es): ${PIDS}"
  kill ${PIDS} 2>/dev/null || true
  sleep 1
fi

echo "[FitCheck Backend] Stopped (port ${PORT} is free; quota ledger preserved)."
