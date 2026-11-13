# Track C — Tài liệu Bàn giao Virtual Try-On (VTO)

- **Nhánh làm việc:** `lamb`
- **Owner:** Track C (`:feature:tryon`, `proxy/src/tryon`, `proxy/scripts`, `eval/tryon`)
- **Tương thích với `origin/main`:** `f96712f` (`0` xung đột, `0` file sửa trùng)
- **Cập nhật:** `2026-11-13`

---

## Mục lục

1. [Tóm tắt](#1-tóm-tắt)
2. [Có gì trong bản này & Thay đổi từ `d55c0bb`](#2-có-gì-trong-bản-này--thay-đổi-từ-d55c0bb)
3. [Cách chạy nhanh (MOCK & LIVE)](#3-cách-chạy-nhanh-mock--live)
4. [Hợp đồng và Điểm tích hợp](#4-hợp-đồng-và-điểm-tích-hợp)
5. [Checklist phần nối còn thiếu cho Track D / Track B](#5-checklist-phần-nối-còn-thiếu-cho-track-d--track-b)
6. [Giới hạn và Rủi ro đã biết](#6-giới-hạn-và-rủi-ro-đã-biết)
7. [Kiểm thử](#7-kiểm-thử)
8. [Quy tắc bảo mật](#8-quy-tắc-bảo-mật)

---

## 1. Tóm tắt

- **Nhiệm vụ của Track C:** Cung cấp trọn bộ tính năng **Thử đồ ảo 2D trên ảnh người thật (Photorealistic 2D Virtual Try-On)** gồm module thư viện Android `:feature:tryon`, router backend `/v2/tryon/*` (hỗ trợ song song `FOSTA` `gpt-6-luna` và `OpenAI` `gpt-image-2`), chế độ mock offline (`$0.00`) và bộ công cụ chạy demo/kiểm thử trên Emulator.
- **Trạng thái hiện tại:**
  - **Thử đồ LIVE góc `FRONT` (mặt trước):** **ĐÃ THÀNH CÔNG** trên máy ảo `Pixel_6` với ảnh chụp người thật dung lượng lớn có EXIF và ảnh áo thật qua FOSTA LIVE backend.
  - **Thử đồ LIVE góc `BACK` (mặt sau với ảnh sau thật):** **CHƯA kiểm chứng LIVE**. Luồng UI, chọn ảnh mặt sau cho người và trang phục, kiểm tra thiếu ảnh và gọi backend ở góc `BACK` mới được kiểm chứng đầy đủ qua chế độ Mock E2E và unit/instrumentation test.

---

## 2. Có gì trong bản này & Thay đổi từ `d55c0bb`

### 2.1. Ba thành phần chính

1. **Module Android `:feature:tryon` (`feature/tryon/`):**
   - `contract/`: Hợp đồng công khai (`VirtualTryOn`, `TryOnRequest`, `TryOnResult`, `MediaRef`, `ImagePair`, `TryOnMediaStorePort`, `TryOnProfilePort`, `TryOnWardrobeRevisionPort`).
   - `ui/`: `TryOnScreen.kt`, `TryOnViewModel.kt`, `TryOnNavigation.kt` (`TryOnRoute`, `tryOnGraph`).
   - `client/` & `privacy/`: `AndroidVirtualTryOnClient`, `LocalDiskTryOnMediaStore` (kiểm tra magic byte, tự chuẩn hóa EXIF/resize ra file tạm), `AtomicFileTryOnLocalJobStore`, `TryOnPrivacyCoordinator`, `SafeTryOnLogger`.
   - `fake/`, `demo/` & `src/standaloneDemo/`: `FakeVirtualTryOnService`, `TryOnDemoActivity`, manifest demo tách riêng khỏi manifest thư viện.
2. **Backend Proxy `proxy/src/tryon/`:**
   - `handler.ts` (`createTryOnRouter`), `types.ts`, `store.ts` (`JsonLedgerTryOnJobStore` giữ sổ cái bền vững trên đĩa).
   - Nhánh FOSTA (`fosta_provider.ts`, `fosta_prompt.ts`, `fosta_mock.ts`) và nhánh OpenAI (`openai_provider.ts`, `openai_prompt.ts`, `openai_mock.ts`).
3. **Scripts Demo & Local Server (`proxy/scripts/`):**
   - `tryon_local_server.ts` (server HTTP tại `127.0.0.1:8788`, Emulator gọi qua `10.0.2.2:8788`).
   - `demo_build_apk.sh`, `demo_start_mock_backend.sh`, `demo_start_fosta_live_backend.sh`, `demo_install_and_launch.sh`, `demo_run_all_mock.sh`, `demo_stop_backend.sh`, `tryon.env.example`.

### 2.2. Các nhóm thay đổi từ `d55c0bb` trở đi

1. **Sửa lỗi `403 CONSENT_REQUIRED` với ảnh lớn / ảnh có EXIF (`AndroidVirtualTryOnClient.kt`):**
   - Khi `LocalDiskTryOnMediaStore` chuẩn hóa EXIF hoặc thu nhỏ ảnh người dùng thành file dẫn xuất tạm, giữ nguyên ID gốc (`derivedFromMediaId ?: id`) khi gửi lên backend để khớp đúng `consentedPersonMediaId`.
2. **Sửa lỗi FOSTA `status='incomplete'` (~101s timeout) → Prompt v2.1 (`fosta_prompt.ts`, `fosta_provider.ts`):**
   - Rút gọn toàn bộ 12 nhánh prompt (`ORIGINAL_PHOTO`/`STUDIO_WHITE` × `FRONT`/`BACK` × `TOP`/`BOTTOM`/`TOP+BOTTOM`) về ~420–620 ký tự theo văn phong POC CLI đã kiểm chứng thành công, giữ nguyên cấu trúc payload và model `gpt-6-luna`.
   - Ghi nhận an toàn trường `incomplete_details.reason` (nếu có) khi FOSTA trả về `status='incomplete'`.
3. **Hardening Provider & Quota Guard (`handler.ts`, `fosta_provider.ts`, `openai_provider.ts`, `types.ts`, `fosta_mock.ts`):**
   - Kiểm tra kích thước, định dạng MIME/magic bytes và dung lượng ảnh đầu vào *trước khi* gọi `reserveLiveSpendAtomically` — ảnh lỗi bị chặn ngay và **tốn `0` lượt quota**.
   - Tuần tự hóa kiểm tra hạn mức đồng thời (`TRYON_LIVE_MAX_REQUESTS`, `TRYON_LIVE_MAX_ESTIMATED_USD`).
   - Lọc sạch chuỗi giống API key (`sk-...`), `Bearer`, data URI/base64 trên mọi nhánh lỗi, log và refusal.
   - Bổ sung giới hạn timeout khi đọc stream phản hồi upstream.
4. **Ô ảnh Mặt trước / Mặt sau cho Áo & Quần + bỏ nạp ảnh mẫu ở chế độ LIVE (`TryOnScreen.kt`, `TryOnViewModel.kt`, `TryOnDemoActivity.kt`):**
   - Mỗi thẻ Áo/Quần (`GarmentThumbnailCard` / `SelectedGarmentsPanel`) hiển thị 2 ô ảnh (`Trước | Sau`) và 2 nút `"Chọn Trước"` / `"Chọn Sau"`, đồng bộ với `PersonPhotosPanel`.
   - Thêm `TryOnViewModel.updateGarmentImages(Map<String, ImagePair>)`; ở chế độ `BACKEND_FOSTA_E2E` không nạp sẵn ảnh mặt sau mẫu giả cho trang phục.
5. **Tách phần Demo khỏi Manifest thư viện `:feature:tryon` (`src/debug/AndroidManifest.xml`, `src/standaloneDemo/AndroidManifest.xml`, `standalone_demo.init.gradle.kts`, `src/androidTest/AndroidManifest.xml`):**
   - `feature/tryon/src/debug/AndroidManifest.xml` chỉ giữ quyền `INTERNET`.
   - Toàn bộ `TryOnDemoActivity`, `MAIN/LAUNCHER`, `android:icon`/`roundIcon`/`label` và `usesCleartextTraffic` chuyển sang `src/standaloneDemo/AndroidManifest.xml` (chỉ nạp khi truyền `-I feature/tryon/standalone_demo.init.gradle.kts`).
6. **Sửa lỗi đổi / chọn lại ảnh trên cùng một ô (`TryOnScreen.kt`, `TryOnViewModel.kt`, `TryOnDemoActivity.kt`):**
   - Khóa cache Coil `ImageRequest` theo `id:contentHash:revision` để Compose và Coil tải lại đúng ảnh mới ngay khi người dùng chọn đè.
   - Khi ảnh người thay đổi trong `observePersonProfile()`, tự đặt lại `consentConfirmed = false` và xóa kết quả thử đồ cũ.
   - Demo Workbench sinh hậu tố tăng dần (`AtomicLong`) cho `MediaRef.id` mỗi lần chọn ảnh từ thư viện.
7. **Mở khóa luồng `"Chỉ mặt sau"` (`TryOnDemoActivity.kt`, `TryOnScreen.kt`):**
   - Nút CTA hồ sơ khi đã có ảnh người mặt trước sẽ mở trực tiếp bộ chọn ảnh người mặt sau (`pickPersonBackLauncher`).
   - Bổ sung dòng gợi ý liệt kê cụ thể các ảnh mặt sau còn thiếu bên dưới chip `"Chỉ mặt sau"`.

---

## 3. Cách chạy nhanh (MOCK & LIVE)

### 3.1. Chế độ MOCK (`$0.00` — không gọi AI thật, không tốn quota)

```bash
# 1. Dựng APK demo độc lập (tạo feature/tryon/build/outputs/apk/FitCheck-VTO-Demo-debug.apk)
./proxy/scripts/demo_build_apk.sh

# 2. Khởi động FOSTA Mock Backend tại http://127.0.0.1:8788
./proxy/scripts/demo_start_mock_backend.sh --daemon

# 3. Cài APK, nạp ảnh mẫu vào Gallery máy ảo và mở app trên Pixel_6
./proxy/scripts/demo_install_and_launch.sh
```

> Có thể chạy gộp cả 3 bước bằng 1 lệnh: `./proxy/scripts/demo_run_all_mock.sh`.

### 3.2. Chế độ LIVE (Có gọi upstream thật & trừ quota)

1. **Tạo file cấu hình bảo mật nằm ngoài Git** từ mẫu [proxy/scripts/tryon.env.example](../../../proxy/scripts/tryon.env.example) (file mẫu đã liệt kê sẵn toàn bộ biến, giá trị mặc định an toàn và chú thích từng biến):

   ```bash
   mkdir -p -m 700 ~/.fitcheckai_private
   cp proxy/scripts/tryon.env.example ~/.fitcheckai_private/tryon.env
   chmod 600 ~/.fitcheckai_private/tryon.env
   ```

2. **Điền biến môi trường trong `~/.fitcheckai_private/tryon.env`** (tuyệt đối **KHÔNG** ghi khóa thật vào repo hay tài liệu):

   | Biến | Ý nghĩa |
   |---|---|
   | `TRYON_PROVIDER` | Chọn provider: `fosta` (`gpt-6-luna`) hoặc `openai` (`gpt-image-2`). |
   | `FOSTA_API_KEY` | Khóa xác thực máy chủ phía FOSTA (chỉ backend đọc). |
   | `FOSTA_ENDPOINT` | URL endpoint `/v1/responses` của FOSTA. |
   | `FOSTA_MODEL` | Tên model FOSTA sử dụng (`gpt-6-luna`). |
   | `OPENAI_API_KEY` | Khóa xác thực OpenAI nếu chạy `TRYON_PROVIDER=openai`. |
   | `TRYON_LIVE_ENABLED` | Công tắc tổng cho phép gọi mạng thật (`false` = khóa cứng MOCK; `true` = bật LIVE). |
   | `FOSTA_APPROVE_QUOTA` | Cổng xác nhận cho phép tiêu thụ quota FOSTA (`true` mới chạy FOSTA LIVE). |
   | `TRYON_BUDGET_CONFIRMED` | Cổng xác nhận ngân sách vận hành (`true` mới vượt qua preflight). |
   | `TRYON_LIVE_MAX_REQUESTS` | Trần cứng tổng số lượt gọi LIVE tối đa (ghi bền vững vào sổ cái `~/.fitcheckai_private/vto_server_state/`). |
   | `TRYON_LIVE_MAX_ESTIMATED_USD` | Trần chi phí ước tính tối đa (USD) trước khi chặn `BUDGET_EXCEEDED`. |
   | `TRYON_LOCAL_AUTH_TOKEN` | Token tùy chọn giữa app debug và server `127.0.0.1:8788` (có thể để rỗng khi chỉ bind `127.0.0.1`). |

3. **Khởi động, kiểm tra `usedLiveRequests` và dừng backend LIVE:**

   ```bash
   # Khởi động FOSTA LIVE backend (sau khi lên, bấm nút "Làm mới" trên app demo)
   ./proxy/scripts/demo_start_fosta_live_backend.sh --daemon

   # Kiểm tra trạng thái cổng an toàn và số lượt đã dùng (usedLiveRequests / remainingLiveRequests)
   curl -s http://127.0.0.1:8788/v2/tryon/preflight

   # Dừng backend (sổ cái đếm quota được giữ nguyên, không bị reset)
   ./proxy/scripts/demo_stop_backend.sh
   ```

- **Quy tắc trừ lượt:** Mỗi lần bấm **`"Tạo ảnh thử đồ"`** tính **`1` lượt gọi upstream cho mỗi góc nhìn được yêu cầu** (chọn `"Chỉ mặt trước"` hoặc `"Chỉ mặt sau"` = `1` lượt; chọn cả hai góc = `2` lượt).

---

## 4. Hợp đồng và Điểm tích hợp

### 4.1. Public API phía Android (`com.fitcheckai.feature.tryon`)

- **`contract/TryOnContracts.kt`:**
  - `VirtualTryOn`: `getCapabilities()`, `submit(TryOnRequest)`, `observeJob(jobId)`, `cancel(jobId)`, `deleteResult(jobId)` (trả về `TryOnResult`).
  - `TryOnRequest`, `TryOnResult`, `MediaRef`, `ImagePair(front: MediaRef?, back: MediaRef?)`.
  - Port tích hợp dữ liệu: `TryOnMediaStorePort`, `TryOnProfilePort`, `TryOnWardrobeRevisionPort`.
- **`ui/TryOnViewModel.kt`, `ui/TryOnScreen.kt`, `ui/TryOnNavigation.kt`:**
  - `TryOnViewModel` (gồm `updateGarmentImages(Map<String, ImagePair>)`, `setConsentConfirmed`, `submitTryOn`, `refreshCapabilities`).
  - Composable `TryOnRoute(...)` và navigation helper `NavGraphBuilder.tryOnGraph(...)` / `TryOnDestinations`.
- **`client/` & `privacy/`:**
  - `AndroidVirtualTryOnClient`, `LocalDiskTryOnMediaStore`, `AtomicFileTryOnLocalJobStore`, `TryOnPrivacyCoordinator`.

### 4.2. Backend Router `/v2/tryon/*` (`proxy/src/tryon/handler.ts` → `createTryOnRouter`)

- `GET /v2/tryon/capabilities` — thông tin provider, phiên bản config và `feasibilityDecision`.
- `GET /v2/tryon/preflight` — trạng thái LIVE/MOCK, các cổng ngân sách và `usedLiveRequests` / `remainingLiveRequests`.
- `POST /v2/tryon/jobs` — khởi tạo job thử đồ (xác thực consent, idempotency, kiểm tra ảnh hợp lệ trước khi trừ quota).
- `GET /v2/tryon/jobs/:jobId` — theo dõi trạng thái job & URL ảnh đầu ra.
- `POST /v2/tryon/jobs/:jobId/cancel` — hủy job đang chạy.
- `DELETE /v2/tryon/jobs/:jobId` — xóa job và ảnh kết quả trên backend.
- `GET /v2/tryon/outputs/:jobId/:view` — tải ảnh kết quả (`FRONT` / `BACK`).

### 4.3. Tình trạng module ở `origin/main` (`f96712f`)

- Đã xác minh ở Bước 3D (`:app:assembleDebug` thành công): nhánh `origin/main` **đã có** `include(":feature:tryon")` trong `settings.gradle.kts` và `implementation(project(":feature:tryon"))` trong `app/build.gradle.kts`, đồng thời đã nối khung `CORE` offline qua `CoreModeVirtualTryOn`, `TryOnBridge`, `TryOnDestination` và nút thử đồ ở `TodayScreen`.

---

## 5. Checklist phần nối còn thiếu cho Track D / Track B

- [x] Khai báo Gradle `include(":feature:tryon")` (`settings.gradle.kts`) và `implementation(project(":feature:tryon"))` (`app/build.gradle.kts`) trên `origin/main`.
- [ ] **Route điều hướng tới màn thử đồ (`Routes.TRYON_PATTERN`):** Hoàn thiện `tryOnDestination` trong `app/` để truyền đủ `garmentImages: Map<String, ImagePair>` (bao gồm cả ảnh `back` nếu món đồ có mặt sau) vào `TryOnViewModel` khi bật chế độ backend VTO.
- [ ] **Nút thử đồ ở `TodayScreen`:** Kiểm thử end-to-end luồng bấm `"Thử đồ với bộ này"` điều hướng qua `Routes.tryOn(outfitId, snapshotId)` với dữ liệu trang phục thật.
- [ ] **Nối `ProfileStorage` & `LocalMediaStore` thật thay cho nguồn ảnh demo:** Khi rời chế độ `CORE` offline, thay `CoreModeVirtualTryOn` bằng `AndroidVirtualTryOnClient` + `LocalDiskTryOnMediaStore` đọc ảnh từ `ProfileStorage` (`bodyImages.front`/`back`) và kho ảnh Track A, đồng thời chuyển tiếp sự kiện đổi/xóa ảnh sang `TryOnPrivacyCoordinator.onPersonPhotoChangedOrDeleted()`.
- [ ] **Mount router `/v2/tryon/*` vào proxy chính (`proxy/src/index.ts`):** Chuyển tiếp các request `/v2/tryon/*` tới `createTryOnRouter()` từ `proxy/src/tryon/handler.ts` (hiện tại mới chạy qua server độc lập `proxy/scripts/tryon_local_server.ts` cổng `8788`).
- [ ] **Cấu hình `cleartext` / `networkSecurityConfig` (`10.0.2.2`) chỉ dành cho bản Demo:** `android:usesCleartextTraffic="true"` để gọi `http://10.0.2.2:8788` đã được cô lập trong `feature/tryon/src/standaloneDemo/AndroidManifest.xml` (chỉ nạp khi truyền `-I feature/tryon/standalone_demo.init.gradle.kts`) và `src/androidTest/AndroidManifest.xml`. Khi ghép vào `:app`, `:feature:tryon` chỉ đóng góp `src/main` + `src/debug` (chỉ có quyền `INTERNET`, không mang `usesCleartextTraffic`, `TryOnDemoActivity` hay icon/label) nên `:app` **không bị ảnh hưởng**. Nếu cần `:app` bản `debug` gọi proxy local qua HTTP, Track D tự cấu hình `networkSecurityConfig` riêng trong `app/src/debug/`.

---

## 6. Giới hạn và Rủi ro đã biết

1. **`UNKNOWN_SUBMISSION` có thể kẹt phiên (`409 CONFLICT`) và không tự retry:** Nếu mất kết nối giữa lúc gửi `POST /v2/tryon/jobs`, client chuyển sang `UNKNOWN_SUBMISSION` và cố ý không tự gửi lại để tránh trừ phí kép; phiên hiện tại bị khóa đến khi hủy/xóa hoặc làm mới.
2. **Ảnh mẫu nạp vào Gallery máy ảo chỉ `480×640`:** Bộ ảnh PNG tạo sẵn trong `demo_install_and_launch.sh` dùng để kiểm thử luồng UI, không phản ánh độ sắc nét của ảnh chụp thật độ phân giải cao.
3. **`eval/tryon/poc_runner.py` nuốt JSON cắt cụt:** Script Python POC thử nghiệm độc lập có thể bỏ qua lỗi JSON bị cắt ngang ở một số ca biên (không ảnh hưởng tới `proxy/src/tryon/fosta_provider.ts`).
4. **Giao diện Demo Workbench (`TryOnDemoActivity`) chưa trau chuốt:** Thanh chọn chế độ backend/kịch bản trong bản APK demo độc lập chỉ phục vụ QA kỹ thuật; bản tích hợp trong `:app` dùng `TryOnRoute` thuần.
5. **`BACK` LIVE chưa kiểm chứng:** Mới xác minh thành công FOSTA LIVE cho `FRONT + TOP`; tổ hợp `BACK` với ảnh mặt sau thật (cũng như `BOTTOM` và `TOP+BOTTOM` LIVE) chưa được kiểm chứng trên AI thật.

---

## 7. Kiểm thử

Tổng số test hiện có: **Proxy `51`**, **Unit Android `27`**, **Instrumentation Android `13`** (`0` lỗi lint).

```bash
# 1. Backend Proxy Typecheck & Vitest (51/51 tests)
npm --prefix proxy run typecheck && npm --prefix proxy test

# 2. Android Unit Tests & Lint (27/27 tests, 0 lint errors)
ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew -I feature/tryon/standalone_demo.init.gradle.kts \
  :feature:tryon:testDebugUnitTest :feature:tryon:lintDebug

# 3. Android Instrumentation Tests trên Emulator (13 tests: VisualQA [10] + LargeImage [1] + PhotoReplace/BackUnlock [2])
# Cần bật trước: ./proxy/scripts/demo_start_mock_backend.sh --daemon
ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew -I feature/tryon/standalone_demo.init.gradle.kts \
  :feature:tryon:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.requireBackendFostaE2e=true
```

**Các instrumentation test cần cờ `testInstrumentationRunnerArguments` riêng:**
- `requireBackendFostaE2e=true`: yêu cầu chạy `TryOnScreenVisualQaTest#endToEndBackendFostaOfflineMockStudioComboAndCompareLoop` (cần FOSTA Mock Backend đang nghe ở `8788`).
- `requireStep3eLargeImageE2e=true`: yêu cầu chạy `LargeImageMockBackendE2eTest#largeCameraExifPersonAndHighResGarmentSucceedThroughMockBackend` (cần FOSTA Mock Backend hỗ trợ endpoint seed ảnh lớn EXIF `/__step3e_seed/person.jpg` và `/__step3e_seed/top.png`).
- `requireBackendOpenAiE2e=true`: yêu cầu chạy `TryOnScreenVisualQaTest#endToEndBackendOpenAiOfflineMockLoop` (cần server ở `8788` chạy với `TRYON_PROVIDER=openai TRYON_LIVE_ENABLED=false TRYON_OFFLINE_MOCK_OPENAI=true`).

---

## 8. Quy tắc bảo mật

- **Ảnh cá nhân không bao giờ vào repository:** Mọi ảnh chụp người thật / trang phục cá nhân (như `~/fitcheck_demo_photos/`) chỉ nằm ngoài Git; tuyệt đối không commit ảnh hay screenshot chứa dữ liệu cá nhân vào repo.
- **Khóa bí mật chỉ nằm ngoài repository:** `FOSTA_API_KEY`, `OPENAI_API_KEY` và `TRYON_LOCAL_AUTH_TOKEN` chỉ đặt tại `~/.fitcheckai_private/tryon.env` (`chmod 600`), không đưa vào code, test, log, tài liệu hay APK.
