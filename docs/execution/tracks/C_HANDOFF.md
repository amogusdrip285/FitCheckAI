# Track C — Virtual Try-On (VTO) Handoff Guide

**Branch:** `lamb`
**Owner:** Track C — Virtual Try-On (`:feature:tryon`, `proxy/src/tryon`, `proxy/scripts`, `eval/tryon`)
**Tương thích với `origin/main` mới nhất:** `bb6e225` (`0` file xung đột)

---

## 1. Module Android `:feature:tryon`

Thư mục `feature/tryon/` là một Android library module độc lập gồm:
- **`ui/TryOnScreen.kt`, `ui/TryOnViewModel.kt`, `ui/TryOnNavigation.kt`**:
  - Giao diện đồng bộ visual language với Track A (`WardrobeDesign.kt`), bố cục ưu tiên hình ảnh (Image-First 1→8): ảnh người dùng (trước/sau) → thẻ ảnh thumbnail trang phục thật (`GarmentThumbnailCard`) → chọn tổ hợp (`TOP`, `BOTTOM`, `TOP+BOTTOM`) → chọn chế độ (`ORIGINAL_PHOTO`, `STUDIO_WHITE`) → chọn góc nhìn (`FRONT`, `BACK`) → xác nhận consent gắn với provider → nút tạo ảnh → khu vực kết quả `3:4` (`ContentScale.Fit`, so sánh ảnh gốc, thử lại, xóa kết quả).
- **`client/AndroidVirtualTryOnClient.kt`, `client/TryOnLocalJobStore.kt`, `client/LocalDiskTryOnMediaStore.kt`**:
  - Ghi nhận `PENDING_SUBMISSION` bền vững trước khi gọi mạng, khôi phục sau process death, chống gửi trùng (`idempotencyKey`), chuyển sang `UNKNOWN_SUBMISSION` khi đứt mạng lúc submit (không tự retry gây tốn phí kép).
  - Kiểm tra magic bytes (`JPEG`/`PNG`/`WebP`), tự động xoay chuẩn hóa ảnh chụp camera có EXIF Orientation `0x0112` sang tệp tạm trước khi upload mà không ghi đè ảnh gốc.
- **`privacy/TryOnPrivacyCoordinator.kt`, `privacy/SafeTryOnLogger.kt`**:
  - Cổng quyền riêng tư: `consentConfirmed = false` → `0` upload; thu hồi consent hoặc xóa ảnh tăng `inputGeneration` và hủy hiển thị kết quả trễ; lọc sạch đường dẫn tệp, URL, base64 và API key trong log.
- **`fake/FakeVirtualTryOnService.kt`, `demo/TryOnDemoActivity.kt`**:
  - Dịch vụ giả lập đầy đủ kịch bản (`SUCCEEDED`, `PARTIAL`, `TIMED_OUT`, `UNKNOWN_SUBMISSION`, `EXPIRED`) và màn hình Demo Workbench độc lập cho phép chuyển đổi giữa `LOCAL_FAKE`, `BACKEND_OPENAI_E2E` và `BACKEND_FOSTA_E2E`.

---

## 2. Backend `proxy/src/tryon` & `proxy/scripts`

- **`proxy/src/tryon/handler.ts` (`createTryOnRouter`)**:
  - Cung cấp các endpoint:
    - `GET /v2/tryon/capabilities`
    - `GET /v2/tryon/preflight`
    - `POST /v2/tryon/jobs`
    - `GET /v2/tryon/jobs/:jobId`
    - `POST /v2/tryon/jobs/:jobId/cancel`
    - `DELETE /v2/tryon/jobs/:jobId`
    - `GET /v2/tryon/outputs/:jobId/:view`
- **Hai Provider Adapter song song**:
  - **`fosta_provider.ts` + `fosta_prompt.ts` + `fosta_mock.ts`** (`TRYON_PROVIDER=fosta`, model `gpt-6-luna`, `POST /v1/responses` với tool `image_generation`): Hỗ trợ ma trận 12 nhánh prompt (`ORIGINAL_PHOTO` / `STUDIO_WHITE` × `FRONT` / `BACK` × `TOP` / `BOTTOM` / `TOP+BOTTOM`), cố định thứ tự ảnh đầu vào `PERSON` → `TOP` → `BOTTOM`.
  - **`openai_provider.ts` + `openai_prompt.ts` + `openai_mock.ts`** (`TRYON_PROVIDER=openai`, model `gpt-image-2`, `POST /v1/images/edits` multipart `image[]`): Giữ nguyên làm nhánh chuẩn OpenAI Images API.
- **`proxy/src/tryon/store.ts` (`JsonLedgerTryOnJobStore`)**:
  - Sổ cái JSON bền vững lưu trạng thái job, khóa idempotency và đếm hạn mức `TRYON_LIVE_MAX_REQUESTS` chống vượt ngân sách khi server khởi động lại.
- **`proxy/scripts/tryon_local_server.ts`**:
  - Máy chủ HTTP cục bộ lắng nghe tại `http://127.0.0.1:8788` (Android Emulator truy cập qua `http://10.0.2.2:8788`), tự động đọc cấu hình từ `~/.fitcheckai_private/tryon.env`.

---

## 3. Cách nối Gradle, Navigation, MediaStore và ProfileStorage với Track A / Track D

1. **Đăng ký Gradle (`settings.gradle.kts` & `app/build.gradle.kts`)**:
   - Trong `settings.gradle.kts`:
     ```kotlin
     include(":feature:tryon")
     ```
   - Trong `app/build.gradle.kts`:
     ```kotlin
     implementation(project(":feature:tryon"))
     ```
2. **Đăng ký Navigation (`app/src/main/kotlin/com/fitcheckai/app/FitCheckApp.kt`)**:
   - `origin/main` đã có sẵn `Routes.TRYON_PATTERN = "tryon/{outfitId}?snapshotId={snapshotId}"` và `Routes.tryOn(outfitId, snapshotId)` trong `core/model/nav/Routes.kt`.
   - Trong `NavHost` của `FitCheckApp.kt`, đăng ký `composable(route = Routes.TRYON_PATTERN, ...)` gọi `TryOnRoute(...)` (hoặc dùng helper `NavGraphBuilder.tryOnScreen(...)` trong `feature/tryon/ui/TryOnNavigation.kt`).
   - Từ màn `TodayScreen` (Track B), khi người dùng nhấn nút thử đồ trên 1 outfit đã chọn, gọi `navController.navigate(Routes.tryOn(outfit.id, outfit.wardrobeSnapshotId))`.
3. **Nối `ProfileStorage` (Track D) & `LocalMediaStore` (Track A)**:
   - Implement `TryOnProfilePort` bọc `ProfileStorage.observeProfile()` (lấy `bodyImages.front` / `bodyImages.back` và `revision`) và lắng nghe `ProfileStorage.invalidations` để gọi `TryOnPrivacyCoordinator.onPersonPhotoChangedOrDeleted()`.
   - Implement `TryOnMediaStorePort` dùng `LocalDiskTryOnMediaStore` (hoặc ủy quyền sang `LocalMediaStore` của `core/imaging`) để đọc tệp ảnh `GARMENT`/`PERSON` từ `MediaRef.localRelativePath`.
   - Chi tiết ánh xạ 1:1 giữa `com.fitcheckai.core.model.tryon.*` và `com.fitcheckai.feature.tryon.contract.*` được ghi rõ trong KDoc của `Sprint0ModelInterop.kt` và `C_SHARED_CONTRACTS_PROPOSAL.md`.

---

## 4. Cách mount Try-On Router vào Proxy của Track B

Trong `proxy/src/index.ts` (hoặc `proxy/src/app.ts`), chuyển tiếp các request `/v2/tryon/*` sang `createTryOnRouter()`:

```ts
import { createTryOnRouter } from "./tryon/handler";

const tryOnRouter = createTryOnRouter({
  // Mặc định an toàn: TRYON_LIVE_ENABLED=false
  providerKind: (env.TRYON_PROVIDER as "fosta" | "openai" | "fake") ?? "fosta",
  liveEnabled: env.TRYON_LIVE_ENABLED === "true",
});

// Trong hàm fetch(request, env):
const url = new URL(request.url);
if (url.pathname.startsWith("/v2/tryon/")) {
  return tryOnRouter.fetch(request);
}
```

Hoặc khi chạy demo/kiểm thử cục bộ trên máy phát triển, chạy trực tiếp `proxy/scripts/tryon_local_server.ts` tại cổng `8788`.

---

## 5. Lệnh Kiểm thử Offline (Không gọi AI LIVE, không tốn quota)

```bash
# 1. Backend Typecheck & Vitest (41/41 tests)
npm --prefix proxy run typecheck && npm --prefix proxy test

# 2. Python POC Runner Unit Tests (7/7 tests)
PYTHONPATH=eval/tryon python3 -m unittest discover -s eval/tryon -p "test_*.py" -v

# 3. Android Unit Tests & Lint cho :feature:tryon (21/21 tests, 0 lint errors)
# (Nếu chưa thêm include(":feature:tryon") vào settings.gradle.kts, dùng init script tạm chứa include(":feature:tryon"))
ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew :feature:tryon:testDebugUnitTest :feature:tryon:lintDebug

# 4. Chạy Local Server ở chế độ FOSTA Offline Mock + Android Emulator E2E (10/10 tests)
TRYON_PROVIDER=fosta TRYON_LIVE_ENABLED=false TRYON_OFFLINE_MOCK_FOSTA=true \
  ./proxy/node_modules/.bin/vite-node proxy/scripts/tryon_local_server.ts

ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew :feature:tryon:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.requireBackendFostaE2e=true
```

---

## 6. Trạng thái Chức năng (`DONE` vs `PARTIAL`)

- **`DONE`**:
  - Toàn bộ kiến trúc Android `:feature:tryon` (`TryOnScreen`, `TryOnViewModel`, `AndroidVirtualTryOnClient`, `AtomicFileTryOnLocalJobStore`, `LocalDiskTryOnMediaStore` chuẩn hóa EXIF, `TryOnPrivacyCoordinator`, `FakeVirtualTryOnService`, `TryOnDemoActivity`).
  - Toàn bộ backend `/v2/tryon/*`, `OpenAITryOnProvider`, `FostaTryOnProvider` (`gpt-6-luna`), ma trận 12 nhánh prompt (`ORIGINAL_PHOTO` / `STUDIO_WHITE` × `FRONT` / `BACK` × `TOP` / `BOTTOM` / `TOP+BOTTOM`), sổ cái bền vững `JsonLedgerTryOnJobStore`, và offline mock servers (`41/41` Vitest, `7/7` Python, `21/21` Android unit, `10/10` Emulator E2E).
  - Kiểm chứng FOSTA LIVE qua CLI cho `FRONT + TOP` ở cả 2 chế độ `ORIGINAL_PHOTO` và `STUDIO_WHITE`.
- **`PARTIAL` (Cần thực hiện khi ghép nối liên track / chạy nghiệm thu thật)**:
  - Ghép nối DI/navigation trong `app/` và `settings.gradle.kts` với `ProfileStorage` (Track D) & `LocalMediaStore` (Track A), và nối nút mở Try-On từ `TodayScreen` (Track B).
  - Các tổ hợp `BACK`, `BOTTOM` và `TOP+BOTTOM` đã hoàn tất logic + mock E2E nhưng chưa chạy nghiệm thu bằng AI LIVE trên bộ ảnh người thật (đang gắn nhãn `THỬ NGHIỆM (EXPERIMENTAL)` trung thực trên UI).

---

## 7. Lưu ý Bảo mật, API Key và Cổng `LIVE` Mặc định `OFF`

- **Mặc định `TRYON_LIVE_ENABLED=false` ở mọi nơi**: Hệ thống tuyệt đối không gọi FOSTA hay OpenAI thật nếu không bật tường minh cả `TRYON_LIVE_ENABLED=true` và cờ phê duyệt ngân sách (`FOSTA_APPROVE_QUOTA=true` hoặc `TRYON_APPROVE_BUDGET_USD`).
- **Không lưu API key hay ảnh cá nhân trong Git**:
  - Đặt cấu hình khóa thật tại `~/.fitcheckai_private/tryon.env` (ngoài repository) theo mẫu `proxy/scripts/tryon.env.example`.
  - Giữ `TRYON_LIVE_MAX_REQUESTS=1` khi thử nghiệm LIVE và đổi lại `TRYON_LIVE_ENABLED=false` ngay sau khi hoàn tất (xem chi tiết tại `C_FOSTA_LIVE_POC_RUNBOOK.md` và `C_OPENAI_LIVE_POC_RUNBOOK.md`).
