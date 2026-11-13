#!/usr/bin/env bash
# OWNER: Track C — Single-command launcher for FitCheck VTO Demo (FOSTA Mock Backend + Emulator + APK)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=================================================================="
echo "  FITCHECK VTO DEMO — ONE-COMMAND LAUNCHER (FOSTA MOCK • \$0.00)"
echo "=================================================================="

# 1. Start FOSTA Mock Backend in background
"${SCRIPT_DIR}/demo_start_mock_backend.sh" --daemon

# 2. Build (if needed), install APK, seed Gallery, and open FitCheck VTO Demo on Pixel_6
"${SCRIPT_DIR}/demo_install_and_launch.sh"

echo "=================================================================="
echo "  READY! Bạn có thể thao tác trực tiếp trên màn hình Pixel_6:"
echo "  - Bấm 'Chọn từ Thư viện' trên thẻ Người hoặc Áo/Quần để chọn ảnh"
echo "  - Chọn Ảnh gốc / Studio nền trắng, Áo / Quần / Áo + Quần, Mặt trước / Mặt sau"
echo "  - Tích Consent và bấm 'Tạo ảnh thử đồ' để nhận kết quả Mock ngay"
echo "  - Khi muốn dừng backend: ./proxy/scripts/demo_stop_backend.sh"
echo "=================================================================="
