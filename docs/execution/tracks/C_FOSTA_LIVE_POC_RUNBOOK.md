# FitCheckAI Track C — FOSTA Luna (`gpt-6-luna`) Experimental Live & Android E2E Runbook

> **OWNER:** Track C (`feature/tryon`, `proxy/src/tryon`)
> **Branch:** `lamb`
> **Provider song song:** `OpenAITryOnProvider` (`TRYON_PROVIDER=openai`, chính thức) + `FostaTryOnProvider` (`TRYON_PROVIDER=fosta`, proxy bên thứ ba thử nghiệm `https://codex.fosta.app/v1/responses`, model `gpt-6-luna`).
> **Mặc định an toàn:** `TRYON_LIVE_ENABLED=false` (0 cuộc gọi AI thật, 0 tiêu thụ quota).

---

## 1. Trạng thái kiểm chứng thực tế vs Logic/Mock (Honesty Gate)

| Tổ hợp `(View × Garment × Mode)` | Logic / Unit / Offline Mock (`41/41` Vitest + JUnit + Emulator E2E) | Kiểm chứng AI thật (`gpt-6-luna` LIVE) | Nhãn hiển thị trên Android |
| :--- | :---: | :---: | :--- |
| `FRONT + TOP + ORIGINAL_PHOTO` | **PASS** | **ĐÃ CHẠY THỬ 1 MẪU QUA CLI** (`eval_01`) | Mặc định khi mở màn hình FOSTA |
| `FRONT + TOP + STUDIO_WHITE` | **PASS** | **ĐÃ CHẠY THỬ 1 MẪU QUA CLI** (`eval_01_studio`) | Có ghi chú tư thế Studio |
| `FRONT + BOTTOM` (`ORIGINAL_PHOTO` / `STUDIO_WHITE`) | **PASS** | **CHƯA KIỂM CHỨNG LIVE** | `THỬ NGHIỆM (EXPERIMENTAL)` |
| `FRONT + TOP+BOTTOM` (`ORIGINAL_PHOTO` / `STUDIO_WHITE`) | **PASS** | **CHƯA KIỂM CHỨNG LIVE** | `THỬ NGHIỆM (EXPERIMENTAL)` |
| `BACK + TOP / BOTTOM / TOP+BOTTOM` (`ORIGINAL_PHOTO` / `STUDIO_WHITE`) | **PASS** (bắt buộc ảnh mặt sau thật, chặn nhân bản hash mặt trước) | **CHƯA KIỂM CHỨNG LIVE** | `THỬ NGHIỆM (EXPERIMENTAL)` |

---

## 2. Cấu hình biến môi trường riêng tư (Ngoài Git)

Tạo hoặc cập nhật file `~/.fitcheckai_private/tryon.env` (quyền `chmod 600`, tuyệt đối không đặt trong repository):

```bash
mkdir -p ~/.fitcheckai_private
chmod 700 ~/.fitcheckai_private
cat << 'EOF' > ~/.fitcheckai_private/tryon.env
TRYON_PROVIDER=fosta
FOSTA_API_KEY=sk-fosta-THAY_KEY_CUA_BAN_TAI_DAY
FOSTA_MODEL=gpt-6-luna
FOSTA_ENDPOINT=https://codex.fosta.app/v1/responses
FOSTA_TIMEOUT_MS=240000

# Giữ false khi kiểm thử Offline Mock; chỉ đổi thành true khi chủ động chạy AI thật:
TRYON_LIVE_ENABLED=false
FOSTA_APPROVE_QUOTA=false
TRYON_OFFLINE_MOCK_FOSTA=true

# Giới hạn cứng số lượt gọi LIVE trước khi khóa sổ cái:
TRYON_LIVE_MAX_REQUESTS=1
EOF
chmod 600 ~/.fitcheckai_private/tryon.env
```

---

## 3. Khởi chạy Local Try-On Server (`127.0.0.1:8788` / Emulator `10.0.2.2:8788`)

### 3.1. Chế độ Offline Mock FOSTA (0 tốn quota, kiểm tra toàn bộ UI & luồng dữ liệu)

```bash
export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:$PATH"
TRYON_PROVIDER=fosta \
TRYON_LIVE_ENABLED=false \
TRYON_OFFLINE_MOCK_FOSTA=true \
./proxy/node_modules/.bin/vite-node proxy/scripts/tryon_local_server.ts
```

### 3.2. Chế độ FOSTA LIVE thật (`gpt-6-luna` — tiêu thụ đúng 1 request được duyệt)

Khi bạn sẵn sàng chạy thử AI thật trên Android Emulator:

```bash
export PATH="/home/lamb/Project/FocusLab/.tooling/node/bin:$PATH"
set -a && source ~/.fitcheckai_private/tryon.env && set +a
TRYON_PROVIDER=fosta \
TRYON_LIVE_ENABLED=true \
FOSTA_APPROVE_QUOTA=true \
TRYON_OFFLINE_MOCK_FOSTA=false \
TRYON_LIVE_MAX_REQUESTS=1 \
./proxy/node_modules/.bin/vite-node proxy/scripts/tryon_local_server.ts
```

Kiểm tra preflight trước khi bấm nút trên Android:

```bash
curl -s http://127.0.0.1:8788/v2/tryon/preflight | jq .
```

Khi sẵn sàng LIVE, preflight trả về `"provider": "fosta"`, `"readyForLiveCall": true`, `"remainingRequests": 1`.

---

## 4. Nạp ảnh thật vào Pixel 6 Emulator & Thao tác trực tiếp trên `TryOnScreen`

### Cách A — Chọn ảnh trực tiếp từ Gallery trên máy ảo

1. Đẩy ảnh của bạn vào thư mục `Pictures` trên máy ảo:
   ```bash
   /home/lamb/Android/Sdk/platform-tools/adb push /duong/dan/anh_nguoi_truoc.jpg /sdcard/Pictures/
   /home/lamb/Android/Sdk/platform-tools/adb push /duong/dan/anh_ao_truoc.jpg /sdcard/Pictures/
   ```
2. Mở màn hình Demo ở chế độ **Backend FOSTA Luna**:
   ```bash
   /home/lamb/Android/Sdk/platform-tools/adb shell am start \
     -n com.fitcheckai.feature.tryon.test/com.fitcheckai.feature.tryon.demo.TryOnDemoActivity \
     --es tryon_demo_mode backend_fosta
   ```
3. Trong **Bảng Demo** ở đầu màn hình, bấm:
   - **`Chọn Người (Trước)`** / **`Chọn Người (Sau)`**
   - **`Chọn Áo (Trước)`** / **`Chọn Áo (Sau)`**
   - **`Chọn Quần (Trước)`** / **`Chọn Quần (Sau)`**

### Cách B — Đặt ảnh vào thư mục riêng tư `files/vto_real_inputs/` của ứng dụng

Ứng dụng tự động nhận diện các tên file chuẩn (`.jpg`, `.jpeg`, `.png`, `.webp`) trong `files/vto_real_inputs/`:
- `person_front.*`, `person_back.*`
- `top_front.*` (hoặc `garment_front.*`), `top_back.*` (hoặc `garment_back.*`)
- `bottom_front.*`, `bottom_back.*`

### Các bước thao tác trên `TryOnScreen`:

1. **Mục 1 (Bộ trang phục đang chọn):** Chọn nhanh tổ hợp **`Chỉ thay Áo (TOP)`**, **`Chỉ thay Quần (BOTTOM)`**, hoặc **`Cả Áo + Quần (TOP+BOTTOM)`**.
2. **Mục 2 (Ảnh toàn thân & Góc nhìn):**
   - Chọn góc nhìn: **`Chỉ mặt trước`** (`FRONT_ONLY`) hoặc **`Chỉ mặt sau`** (`BACK_ONLY` — chỉ bật khi có đủ ảnh người mặt sau + ảnh mặt sau của các món đã chọn, không trùng hash với mặt trước).
   - Chọn chế độ khung hình: **`Ảnh gốc (ORIGINAL_PHOTO)`** hoặc **`Nền trắng Studio (STUDIO_WHITE)`**.
3. **Mục 3 (Quyền riêng tư & Xác nhận tải ảnh):** Tích ô đồng ý gửi ảnh (consent gắn chặt với `providerId = "fosta"` và danh sách ảnh đã chọn; nếu đổi mode/view/món đồ, ô consent tự động reset).
4. Bấm **`Tạo ảnh thử đồ (Mặt trước)`** (hoặc **`Tạo ảnh thử đồ (Mặt sau)`**).
5. Khi ảnh trả về (khoảng 60–180 giây với `gpt-6-luna` LIVE, hoặc tức thì với Offline Mock):
   - Xem ảnh kết quả với nhãn **`AI LIVE — FOSTA (Proxy thử nghiệm)`** (hoặc **`GIẢ LẬP / MOCK (FAKE)`** khi LIVE tắt).
   - Bấm **`So sánh ảnh gốc`** để đối chiếu trực tiếp với ảnh người đầu vào.
   - Bấm **`Xóa kết quả & ảnh tạm`** để xóa ngay file cục bộ và bản ghi tạm trên local server.

---

## 5. Khóa lại LIVE ngay sau khi thử nghiệm

1. Nhấn `Ctrl+C` trên cửa sổ chạy `tryon_local_server.ts`.
2. Đảm bảo `~/.fitcheckai_private/tryon.env` giữ `TRYON_LIVE_ENABLED=false` và `FOSTA_APPROVE_QUOTA=false`.
