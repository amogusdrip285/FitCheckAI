# Track C — Cảnh báo Vận hành & Tích hợp Virtual Try-On (VTO)

- **Phạm vi:** `:feature:tryon`, `proxy/src/tryon/**`, `proxy/scripts/*tryon*`, `eval/tryon/**`
- **Điểm đối chiếu `origin/main`:** `3488654` (`348865475e40b40f3c10b63fdc3e6c3c8340b30f`)
- **Cập nhật:** `2026-11-13`

---

## Mục lục

1. [Quota LIVE & Bảo mật khóa (`tryon.env`)](#1-quota-live--bảo-mật-khóa-tryonenv)
2. [Mạng Demo (`10.0.2.2` / Cleartext) & Manifest `:app`](#2-mạng-demo-10022--cleartext--manifest-app)
3. [`UNKNOWN_SUBMISSION` (Timeout mơ hồ) & Khóa phiên `409 CONFLICT`](#3-unknown_submission-timeout-mơ-hồ--khóa-phiên-409-conflict)
4. [Ràng buộc Kiểm thử Giao diện (13 bài Instrumentation phụ thuộc)](#4-ràng-buộc-kiểm-thử-giao-diện-13-bài-instrumentation-phụ-thuộc)
5. [Giao diện Track A cục bộ (`TryOnDesign.kt`) & Lộ trình nâng lên `:core:ui`](#5-giao-diện-track-a-cục-bộ-tryondesignkt--lộ-trình-nâng-lên-coreui)
6. [Giới hạn Kỹ thuật đã biết](#6-giới-hạn-kỹ-thuật-đã-biết)
7. [Ghi chú Giao diện cho nhóm (`TodayScreen`)](#7-ghi-chú-giao-diện-cho-nhóm-todayscreen)
8. [Cách phát hiện nối sai & Đầu mối liên hệ](#8-cách-phát-hiện-nối-sai--đầu-mối-liên-hệ)

---

## 1. Quota LIVE & Bảo mật khóa (`tryon.env`)

- **Trừ lượt theo từng góc nhìn:** Mỗi lần bấm **`"Tạo ảnh thử đồ"`** ở chế độ `LIVE` tiêu tốn **`1` lượt gọi thật cho mỗi góc nhìn** (`"Chỉ mặt trước"` hoặc `"Chỉ mặt sau"` = `1` lượt; `"Cả trước & sau"` = `2` lượt).
- **Mặc định tắt LIVE:** Tất cả cổng an toàn trong [proxy/scripts/tryon.env.example](../../../proxy/scripts/tryon.env.example) đều tắt (`TRYON_LIVE_ENABLED=false`, `FOSTA_APPROVE_QUOTA=false`, `TRYON_BUDGET_CONFIRMED=false`).
- **Cấm bật LIVE trong tự động hóa:** **TUYỆT ĐỐI KHÔNG** đặt `TRYON_LIVE_ENABLED=true` trong CI, cấu hình Gradle hay bất kỳ bài unit/instrumentation test nào.
- **Khóa chỉ nằm ngoài repo:** File bí mật `~/.fitcheckai_private/tryon.env` (`chmod 600`) và sổ cái đếm quota `~/.fitcheckai_private/vto_server_state/` chỉ nằm ngoài repository; không bao giờ commit `.env`, ảnh thật, APK hay in khóa (`FOSTA_API_KEY`, `OPENAI_API_KEY`) vào log hoặc tài liệu.

## 2. Mạng Demo (`10.0.2.2` / Cleartext) & Manifest `:app`

- **Không bật cleartext trong bản phát hành:** Tuyệt đối **không** bật `android:usesCleartextTraffic="true"` hoặc gắn `@xml/tryon_debug_network_security_config` (cho phép HTTP tới `10.0.2.2` / `127.0.0.1`) vào `app/src/main/AndroidManifest.xml` hay build `release` của `:app`.
- **Tách manifest demo độc lập:** Toàn bộ `TryOnDemoActivity` (`LAUNCHER`), icon/label demo và `networkSecurityConfig` nằm riêng tại `feature/tryon/src/standaloneDemo/AndroidManifest.xml` (chỉ nạp khi truyền `-I feature/tryon/standalone_demo.init.gradle.kts`) và `feature/tryon/src/androidTest/AndroidManifest.xml`.
- **Ảnh hưởng lên `:app`:** Khi `:app` phụ thuộc `:feature:tryon` thông thường, `:feature:tryon` chỉ góp `src/main` + `src/debug` (quyền `INTERNET`, `TryOnDemoActivity` có `android:exported="false"`, không launcher, không cleartext).

## 3. `UNKNOWN_SUBMISSION` (Timeout mơ hồ) & Khóa phiên `409 CONFLICT`

- **Không tự retry khi timeout mơ hồ:** Nếu mất kết nối hoặc đứt socket ngay sau khi đã gửi `POST /v2/tryon/jobs`, `AndroidVirtualTryOnClient` chuyển tiến trình sang trạng thái `TryOnJobStatus.UNKNOWN_SUBMISSION` (`SubmissionPhase.AMBIGUOUS_LOST_RESPONSE`) và **cố ý không tự động retry** để tránh gọi upstream 2 lần và trừ phí kép.
- **Khả năng khóa phiên `409 CONFLICT`:** Khi backend vẫn đang giữ tiến trình đang chạy hoặc chưa chốt của cùng `sessionId`, lần bấm gửi mới có thể nhận mã `409 CONFLICT`.
- **Cách xử lý:** Bấm **Hủy yêu cầu** hoặc **Xóa kết quả thử đồ** ngay trên màn thử đồ để dọn bản ghi phiên cục bộ/backend (hoặc chờ hết deadline `120s`/`180s` rồi bấm thử lại).

## 4. Ràng buộc Kiểm thử Giao diện (13 bài Instrumentation phụ thuộc)

- **Giữ nguyên 36 `TryOnTestTags`:** Không đổi tên, gộp hoặc xóa bất kỳ hằng số nào trong `TryOnTestTags` (`feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnScreen.kt`).
- **Chiều cao chạm tối thiểu `>= 48.dp`:** Mọi node mang `testTag` tương tác bắt buộc có vùng chạm semantics tối thiểu `48.dp` (ví dụ `TryOnChoiceChip` đặt pill `40.dp` bên trong `Box(Modifier.heightIn(min = 48.dp).selectable(...))` để qua `assertHeightIsAtLeast(48.dp)`).
- **Nằm trong vùng cuộn `verticalScroll`:** `CONSENT_CHECKBOX_ROW` và `SUBMIT_BUTTON` phải giữ **bên trong** vùng cuộn chính (không ghim thành thanh footer cố định ngoài vùng cuộn) để `performScrollTo()` hoạt động trên màn nhỏ `360×640` và `fontScale = 1.85f`.

## 5. Giao diện Track A cục bộ (`TryOnDesign.kt`) & Lộ trình nâng lên `:core:ui`

- **Nguồn token:** `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnDesign.kt` là bản sao cục bộ đối chiếu chính xác màu Sáng/Tối, typography, `Shapes`, bo góc và khoảng cách từ `feature/wardrobe/src/main/kotlin/com/fitcheckai/feature/wardrobe/WardrobeDesign.kt` trên `origin/main` commit `3488654` (`348865475e40b40f3c10b63fdc3e6c3c8340b30f`, kế thừa từ `f96712f`).
- **Không import `:feature:wardrobe`:** Các helper UI của Wardrobe đều là `internal` và kiến trúc Gradle cấm phụ thuộc ngang giữa các module `:feature:*`.
- **Khi Track D nâng theme lên `:core:ui`:** Đổi wrapper `TryOnEditorialTheme { ... }` trong `TryOnScreen.kt` sang theme chung mới của `:core:ui` (không cần sửa `TryOnViewModel`, `TryOnPrivacyCoordinator` hay `TryOnContracts`).

## 6. Giới hạn Kỹ thuật đã biết

- **Góc `BACK` ở chế độ LIVE mới thử sơ bộ:** Luồng chọn ảnh mặt sau, kiểm tra thiếu ảnh và gọi mock đã kiểm chứng E2E; trên FOSTA `LIVE` mới xác minh trọn vẹn `FRONT + TOP` (chưa xác minh đầy đủ `BACK`, `BOTTOM`, `TOP+BOTTOM` LIVE).
- **Ảnh mẫu demo chỉ `480×640`:** Bộ ảnh PNG tự tạo trong `proxy/scripts/demo_install_and_launch.sh` chỉ phục vụ kiểm thử luồng UI/Gallery, không phản ánh chất lượng ảnh chụp thật độ phân giải cao.
- **`eval/tryon/poc_runner.py` nuốt JSON cắt cụt:** Script POC Python độc lập có thể bỏ qua lỗi JSON bị cắt ngang ở một số ca biên (không ảnh hưởng tới `proxy/src/tryon/fosta_provider.ts`).
- **`MediaRef.id` đổi sau mỗi lần chọn ảnh trong Demo Workbench:** `TryOnDemoActivity` gắn hậu tố tăng dần vào `MediaRef.id` khi người dùng chọn lại ảnh; bên ngoài `:feature:tryon` không được cache cứng chỉ theo `MediaRef.id` (hãy khóa cache theo `id + contentHash + revision`).

## 7. Ghi chú Giao diện cho nhóm (`TodayScreen`)

- `TodayScreen` (`feature/today/src/main/kotlin/com/fitcheckai/feature/today/TodayScreen.kt`) hiện chỉ nhận `FitCheckTheme` từ `MainActivity.kt:28` (`core/ui/src/main/kotlin/com/fitcheckai/core/ui/theme/Theme.kt:31-59` chưa ghi đè `primaryContainer`, `secondaryContainer`, `surfaceContainer*`, `outlineVariant`), khiến chip và thẻ của màn `Hôm nay` bị tô sắc tím mặc định của Material 3 khác với màn `Tủ đồ` và `Thử đồ`. Đây là việc thuộc phạm vi Track D (`:core:ui` / `:feature:today`), ghi nhận để nhóm đồng bộ sau.

## 8. Cách phát hiện nối sai & Đầu mối liên hệ

- **5 dấu hiệu nhận biết nối sai:**
  1. **Màn Thử đồ luôn khóa Gate C-205** (*"Thử đồ ảo chưa được bật trong bản này..."*) → `app/src/main/kotlin/com/fitcheckai/app/tryon/TryOnDestination.kt:73` vẫn đang cấp `CoreModeVirtualTryOn()` thay vì `AndroidVirtualTryOnClient`.
  2. **Ảnh lớn/có EXIF bị trả `403 CONSENT_REQUIRED`** → bước tạo file upload làm đổi `MediaRef.id` nhưng bỏ trống `derivedFromMediaId`, khiến server không khớp được với `consentedPersonMediaId`.
  3. **Chip `"Chỉ mặt sau"` bị vô hiệu dù món đồ có ảnh sau** → `TodayViewModel.prepareTryOn` (`TodayViewModel.kt:284`) hoặc `garmentImages()` (`TryOnBridge.kt:76-82`) thiếu `displayPaths` cho `images.back`.
  4. **Proxy trả `404 INVALID_REQUEST`, `503 UNSUPPORTED_CAPABILITY` hoặc ngắt ở `18s`** → chưa mount `tryonRoutes` vào `ROUTES` (`proxy/src/index.ts:14`), chưa bật `SERVICE_TRYON=on` (`proxy/src/env.ts:135`), hoặc thiếu `deadlineMs: 180_000` trên `RouteDef`.
  5. **Cài `:app` bị hiện 2 icon launcher hoặc đổi tên thành `"FitCheck VTO Demo"`** → chạy nhầm `-I feature/tryon/standalone_demo.init.gradle.kts` khi build `:app` hoặc gộp nhầm `src/standaloneDemo`.
- **Đầu mối liên hệ khi gặp lỗi theo mảng:**
  - **UI Try-On, `TryOnViewModel`, `AndroidVirtualTryOnClient`, `LocalDiskTryOnMediaStore`, `proxy/src/tryon/**`:** Track C.
  - **`:core:imaging` (`LocalMediaStore`), `:core:database` (`WardrobeRepository`), `:feature:wardrobe`:** Track A.
  - **Proxy entry (`proxy/src/index.ts`), auth `/v2/session`, kill switch `SERVICE_TRYON`, daily cap/Workers KV-DO:** Track B.
  - **Điều hướng `:app`, `TryOnDestination.kt`, `TodayScreen`, `ProfileStorage`, `:core:ui` (`FitCheckTheme`):** Track D.
