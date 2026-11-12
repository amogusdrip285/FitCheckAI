# Track C — OpenAI Virtual Try-On Live POC Runbook

**Phạm vi sở hữu:** Track C (`:feature:tryon`, `proxy/src/tryon/`, `proxy/scripts/`, `eval/tryon/`)
**Branch:** `lamb`
**Trạng thái mặc định hiện tại:** `TRYON_LIVE_ENABLED=false` (Khóa cứng LIVE — **\$0.00 phát sinh**)
**Trạng thái kiểm thử:** `OFFLINE/MOCK PASS` • **`LIVE NOT TESTED`**

---

## 1. Minh bạch nguồn tiền & Quy chế Hackathon (Codex Community Hackathon Hanoi — 14/11/2026)

### 1.1. Phân biệt rõ các nguồn tín dụng (Credits)
1. **Codex credits (tối đa \$100 nếu đủ điều kiện & được duyệt):** Dành riêng cho công cụ lập trình Codex; **KHÔNG thể** dùng để thanh toán lệnh gọi HTTP tới `https://api.openai.com/v1/images/edits`.
2. **OpenAI Platform API credits (tối đa \$50 tại sự kiện tùy phê duyệt, hoặc tự nạp trước):** Đây là nguồn duy nhất thanh toán cho `POST /v1/images/edits`. Tài khoản ChatGPT Plus / Pro / Business **không tự động** kèm API credits của OpenAI Platform.
3. **API Organization Verification:** Một số tổ chức OpenAI mới cần hoàn tất xác minh tại **Platform Settings → Organization → General → Verify Organization** trước khi được phép gọi họ model GPT Image (`gpt-image-2`, `gpt-image-1.5`). Nếu chưa xác minh, hệ thống sẽ dịch lỗi `403` thành thông báo rõ ràng và dừng ngay (không retry).

### 1.2. Bảng kê minh bạch phần việc trước và trong Hackathon

| Phân lớp công việc | Thời điểm thực hiện | Phạm vi cụ thể |
|---|---|---|
| **1. Nền tảng có sẵn trong Repo (`HEAD = 7d995a5`)** | Trước Track C | `:core:model`, `:core:ui`, khung ứng dụng Android cơ sở, Cloudflare Worker proxy khung của Track B. |
| **2. Khung tích hợp OpenAI chuẩn bị trước sự kiện (Pre-Hackathon Scaffold)** | Hoàn thiện trước `14/11/2026` (`$0.00` live spend) | `OpenAITryOnProvider` (`POST /v1/images/edits` multipart `image[]`), kiểm tra magic bytes & EXIF `0x0112`, `LocalDiskTryOnMediaStore`, `JsonLedgerTryOnJobStore`, `tryon_local_server.ts` (`127.0.0.1:8788`), `OpenAiImageEditsMock`, cổng khóa `TRYON_LIVE_ENABLED=false`, giới hạn `TRYON_LIVE_MAX_REQUESTS=1` và bộ kiểm thử offline/emulator. |
| **3. Thực nghiệm Live & Tinh chỉnh tại sự kiện (Hackathon / Live Session)** | Khi người dùng cấp `OPENAI_API_KEY` + xác nhận API credits | Cấu hình `~/.fitcheckai_private/tryon.env`, chạy 1 lượt `FRONT_ONLY` có kiểm soát trên ảnh thật có consent, đánh giá chất lượng thực tế (khuôn mặt, form áo, chữ/logo in trên áo), quyết định cổng `GO_FRONT_ONLY` / `NO_GO` và nối luồng demo cuối cùng. |

---

## 2. Danh mục Model OpenAI Images & Ước tính chi phí mỗi lượt mặt trước (`1024x1536`)

> Đã đối chiếu tài liệu chính thức OpenAI Images Edit API:
> - `gpt-image-1` và `dall-e-2`/`dall-e-3` đã ngừng hỗ trợ (`2026-10-06` / `2026-05-12`) và bị chặn cứng trong code.
> - GPT Image luôn trả ảnh qua `b64_json` (không gửi `response_format`).
> - Tham số `input_fidelity` chỉ hỗ trợ trên `gpt-image-1.5`; code tự động lược bỏ tham số này khi gọi dòng `gpt-image-2*`.

| `OPENAI_IMAGE_MODEL` | Chất lượng (`OPENAI_IMAGE_QUALITY`) | Kích thước (`OPENAI_IMAGE_SIZE`) | Ước tính tối đa / 1 lượt `FRONT_ONLY` (gồm 2 ảnh input + 1 ảnh output) | Ghi chú lựa chọn |
|---|---|---|---|---|
| `gpt-image-2` *(Mặc định)* | `medium` | `1024x1536` | **~\$0.06 – \$0.08** | Thế hệ GPT Image 2 cân bằng chi tiết khuôn mặt, nếp vải và tốc độ. |
| `gpt-image-1.5` | `medium` (`input_fidelity=high`) | `1024x1536` | **~\$0.06 – \$0.08** | Hỗ trợ tường minh `input_fidelity="high"` để giữ chi tiết ảnh gốc. |
| `gpt-image-1-mini` | `medium` | `1024x1536` | **~\$0.015 – \$0.025** | Rẻ nhất để kiểm tra thông luồng kỹ thuật đầu tiên. |
| `gpt-image-2` / `gpt-image-1.5` | `high` | `1024x1536` | **~\$0.20 – \$0.26** | Chỉ bật khi cần so sánh độ nét chữ in/logo nhỏ sau khi bản `medium` đã đạt. |

---

## 3. Preflight Checklist 10 điểm bắt buộc trước khi bật LIVE

Chỉ chuyển `TRYON_LIVE_ENABLED=true` khi **cả 10 mục sau đều đạt**:

- [ ] **1. OpenAI API Key hợp lệ:** Có biến `OPENAI_API_KEY=sk-...` lưu trong `~/.fitcheckai_private/tryon.env` (`chmod 600`), tuyệt đối không đặt trong thư mục Git.
- [ ] **2. Xác nhận OpenAI Platform API Credits > \$0:** Đã kiểm tra tại `platform.openai.com/settings/organization/billing/overview` (không nhầm với Codex credits hay gói ChatGPT).
- [ ] **3. Organization Verification sẵn sàng:** Tài khoản/tổ chức OpenAI đã xác minh quyền truy cập GPT Image (nếu dashboard yêu cầu).
- [ ] **4. Cố định `OPENAI_IMAGE_MODEL`:** Đã chọn rõ `gpt-image-2`, `gpt-image-1.5` hoặc `gpt-image-1-mini` với `OPENAI_IMAGE_QUALITY=medium` và `OPENAI_IMAGE_SIZE=1024x1536`.
- [ ] **5. Giới hạn cứng 1 request (`TRYON_LIVE_MAX_REQUESTS=1`):** Đảm bảo tối đa đúng 1 lệnh gọi trả phí cho lần thử đầu tiên.
- [ ] **6. Trần ngân sách USD (`TRYON_LIVE_MAX_ESTIMATED_USD=0.25`):** Và `TRYON_BUDGET_CONFIRMED=true`.
- [ ] **7. Cặp ảnh thật đạt chuẩn nằm ngoài Git:**
  - `person_front.jpg`: Ảnh đứng thẳng toàn thân mặt trước, đủ sáng, không che mặt/thân, định dạng JPEG/PNG/WebP.
  - `garment_front.jpg`: Ảnh 1 chiếc áo rõ toàn bộ cổ áo – tay áo – gấu áo, nền gọn tự nhiên (không tự xóa nền thô).
- [ ] **8. Xác nhận Consent rõ ràng:** Người trong ảnh là chính bạn (hoặc người đã đồng ý rõ ràng cho lượt thử này) và bạn tự tích ô xác nhận trên `TryOnScreen`.
- [ ] **9. Sổ cái bền vững hoạt động (`durableStoreActive: true`):** Máy chủ cục bộ ghi ledger nguyên tử tại `~/.fitcheckai_private/vto_server_state/tryon_jobs_ledger.json`.
- [ ] **10. Sẵn sàng khóa lại ngay sau lượt chạy:** Mở sẵn file `~/.fitcheckai_private/tryon.env` để đổi `TRYON_LIVE_ENABLED=false` ngay khi nhận kết quả đầu tiên.

---

## 4. Quy trình chạy 1 lần thử LIVE có kiểm soát (End-to-End trên Android Emulator)

### Bước 1 — Tạo thư mục riêng tư ngoài Git và đặt 2 ảnh đầu vào
```bash
mkdir -p ~/.fitcheckai_private/vto_input ~/.fitcheckai_private/vto_server_state
chmod 700 ~/.fitcheckai_private ~/.fitcheckai_private/vto_input ~/.fitcheckai_private/vto_server_state

# Copy ảnh thật của bạn vào thư mục riêng tư ngoài repo:
#   ~/.fitcheckai_private/vto_input/person_front.jpg
#   ~/.fitcheckai_private/vto_input/garment_front.jpg
```

### Bước 2 — Tạo file cấu hình riêng `~/.fitcheckai_private/tryon.env`
```bash
cp /home/lamb/Project/FitCheckAI/proxy/scripts/tryon.env.example ~/.fitcheckai_private/tryon.env
chmod 600 ~/.fitcheckai_private/tryon.env
```
Mở `~/.fitcheckai_private/tryon.env` và điền thông số cho **đúng 1 lượt thử đầu tiên**:
```dotenv
TRYON_HOST=127.0.0.1
TRYON_PORT=8788
TRYON_PROVIDER=openai

OPENAI_API_KEY=sk-proj-REPLACE_WITH_YOUR_REAL_KEY
OPENAI_IMAGE_MODEL=gpt-image-2
OPENAI_IMAGE_QUALITY=medium
OPENAI_IMAGE_SIZE=1024x1536
OPENAI_INPUT_FIDELITY=high
OPENAI_OUTPUT_FORMAT=png

TRYON_FEASIBILITY_DECISION=GO_FRONT_ONLY
TRYON_OFFLINE_MOCK_OPENAI=false
TRYON_BUDGET_CONFIRMED=true
TRYON_LIVE_MAX_REQUESTS=1
TRYON_LIVE_MAX_ESTIMATED_USD=0.25

# Chỉ đổi dòng này thành true ngay trước khi bấm nút thử 1 lần:
TRYON_LIVE_ENABLED=true
```

### Bước 3 — Khởi động FitCheck Local Try-On Server và kiểm tra Preflight
Ở Terminal 1:
```bash
cd /home/lamb/Project/FitCheckAI/proxy
./node_modules/.bin/vite-node scripts/tryon_local_server.ts
```
Ở Terminal 2, chạy kiểm tra Preflight (0 tốn phí):
```bash
curl -s http://127.0.0.1:8788/v2/tryon/preflight | python3 -m json.tool
```
Xác nhận JSON trả về `"readyForLivePocCall": true`, `"blockers": []`, `"maxLiveRequests": 1`.

### Bước 4 — Đưa cặp ảnh vào bộ nhớ riêng của ứng dụng trên `Pixel_6` và chạy `TryOnScreen`
1. Khởi động máy ảo `Pixel_6` và cài gói debug demo `:feature:tryon` (nếu chưa cài):
   ```bash
   ANDROID_HOME=/home/lamb/Android/Sdk /home/lamb/Android/Sdk/emulator/emulator -avd Pixel_6 &
   cd /home/lamb/Project/FitCheckAI
   ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew \
     -I /home/lamb/.gemini/antigravity/brain/cb920fe9-1c24-4143-98ab-87a249d30e9b/scratch/tryon_include.init.gradle.kts \
     :feature:tryon:installDebugAndroidTest
   ```
2. Nạp 2 tệp ảnh thật vào thư mục riêng tư `files/vto_real_inputs` của package debug:
   ```bash
   ADB=/home/lamb/Android/Sdk/platform-tools/adb
   PKG=com.fitcheckai.feature.tryon.test
   $ADB shell "run-as $PKG mkdir -p files/vto_real_inputs"
   cat ~/.fitcheckai_private/vto_input/person_front.jpg | $ADB shell "run-as $PKG sh -c 'cat > files/vto_real_inputs/person_front.jpg'"
   cat ~/.fitcheckai_private/vto_input/garment_front.jpg | $ADB shell "run-as $PKG sh -c 'cat > files/vto_real_inputs/garment_front.jpg'"
   ```
   *(Hoặc bạn có thể bấm trực tiếp nút **"Chọn ảnh Người (Trước)"** và **"Chọn ảnh Quần áo"** trên Bảng Demo của ứng dụng).*
3. Mở trực tiếp `TryOnDemoActivity` ở chế độ **`Backend OpenAI E2E (10.0.2.2:8788)`**:
   ```bash
   $ADB shell am start -n com.fitcheckai.feature.tryon.test/com.fitcheckai.feature.tryon.demo.TryOnDemoActivity \
     --es tryon_demo_mode backend_openai
   ```
4. Trên màn hình `Pixel_6`:
   - Kiểm tra nhãn đầu vào đã nhận ảnh thật (`Đã sẵn sàng`), chọn **Bộ 2: Dạo phố (1 Áo Polo)** hoặc món áo cần thử, giữ góc nhìn **Mặt trước (`FRONT_ONLY`)**.
   - Tích chọn ô **Consent** cho riêng lượt thử này.
   - Bấm **"Tạo ảnh thử đồ (Mặt trước)"** đúng **1 lần** và đợi ~15–35 giây.
   - Khi hoàn tất, ảnh kết quả hiển thị nhãn **`AI LIVE (OpenAI)`**, cho phép bấm **"So sánh ảnh gốc"** để đối chiếu khuôn mặt, dáng đứng, form áo và chữ/logo trên áo.

### Bước 5 — Khóa lại LIVE ngay lập tức & Dọn dữ liệu tạm (Bắt buộc)
1. Trong `~/.fitcheckai_private/tryon.env`, đổi ngay:
   ```dotenv
   TRYON_LIVE_ENABLED=false
   ```
   *(Máy chủ `tryon_local_server.ts` tự đọc lại `tryon.env` ở mỗi request, nên cổng LIVE bị khóa tức thì mà không cần khởi động lại hay sửa code).*
2. Trên ứng dụng Android, bấm **"Xóa kết quả & ảnh tạm"** (hoặc **"Xóa ảnh & Rút Consent (C-210)"**) để xóa tệp PNG kết quả trên thiết bị và bản ghi tạm trên FitCheck server.
3. Nếu không còn nhu cầu thử tiếp, xóa ảnh đầu vào tạm trên máy ảo:
   ```bash
   $ADB shell "run-as com.fitcheckai.feature.tryon.test rm -rf files/vto_real_inputs"
   ```

---

## 5. Chạy thử từ CLI POC Runner (Tùy chọn không cần mở Emulator)

Nếu muốn kiểm tra nhanh `POST /v1/images/edits` và xuất bảng chấm rubric JSON tại `~/.fitcheckai_private/vto_poc_out`:
```bash
# Chế độ Offline Mock ($0.00, không gọi mạng ngoài):
python3 /home/lamb/Project/FitCheckAI/eval/tryon/poc_runner.py \
  --mode fake \
  --provider openai \
  --offline-mock-openai \
  --auto-manifest-from-folder \
  --private-in ~/.fitcheckai_private/vto_input \
  --private-out ~/.fitcheckai_private/vto_poc_out
```

---

## 6. Đề xuất ghép nối an toàn với Track A, Track B và Track D

Track C giữ nguyên ranh giới module, không tự sửa các tệp dùng chung. Khi nhóm hợp nhất (`INT-001` / `S-002`), chỉ cần áp dụng 3 điểm nối nhỏ sau:

1. **Track A (`Wardrobe & Imaging`):**
   - Implement `TryOnMediaStorePort` (hoặc tái sử dụng trực tiếp [`LocalDiskTryOnMediaStore`](file:///home/lamb/Project/FitCheckAI/feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/client/LocalDiskTryOnMediaStore.kt)) trỏ vào thư mục lưu ảnh tủ đồ riêng tư của ứng dụng, ánh xạ `ClothingItem` sang `ImagePair(front, back)`.
2. **Track B (`proxy/src/index.ts` & Backend):**
   - Mount `createTryOnRouter({ store })` từ [`proxy/src/tryon/handler.ts`](file:///home/lamb/Project/FitCheckAI/proxy/src/tryon/handler.ts) cho các đường dẫn `/v2/tryon/*`.
   - Khi chạy trên Cloudflare Workers production, cung cấp `TryOnJobStore` bền vững (Durable Object / D1 triển khai cùng giao diện với [`JsonLedgerTryOnJobStore`](file:///home/lamb/Project/FitCheckAI/proxy/src/tryon/store.ts)) và cấu hình `OPENAI_API_KEY` bằng `wrangler secret put OPENAI_API_KEY`.
3. **Track D (`settings.gradle.kts`, `:app` Navigation & `ProfileStorage`):**
   - Thêm `include(":feature:tryon")` vào `settings.gradle.kts`.
   - Cấp `TryOnProfilePort` từ kho hồ sơ người dùng (`D-007`) và nối `TryOnRoute` từ màn hình chi tiết bộ phối đồ đã chọn.
