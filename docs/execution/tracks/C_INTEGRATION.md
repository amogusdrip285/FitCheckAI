# Track C — Hướng dẫn Nối Tích hợp Virtual Try-On (VTO)

- **Đối tượng đọc:** Track D (`:app`, `:feature:today`, `:core:ui`), Track B (`proxy`), Track A (`:core:imaging`, `:core:database`)
- **Đối chiếu code thật trên `origin/main`:** `3488654` (`348865475e40b40f3c10b63fdc3e6c3c8340b30f`)
- **Tài liệu liên quan:** [Cảnh báo (`C_WARNINGS.md`)](C_WARNINGS.md) · [Bàn giao (`C_HANDOFF.md`)](C_HANDOFF.md) · [Review B (`C-207`)](../../track-b/C-207-mount-review.md)
- **Cập nhật:** `2026-11-13`

---

## Mục lục

1. [Bảng Ranh giới Sở hữu](#1-bảng-ranh-giới-sở-hữu)
2. [Hợp đồng Công khai & Nguồn Dữ liệu](#2-hợp-đồng-công-khai--nguồn-dữ-liệu)
3. [Các bước Nối Đánh số](#3-các-bước-nối-đánh-số)
4. [Checklist Nghiệm thu bằng Backend Mock](#4-checklist-nghiệm-thu-bằng-backend-mock)
5. [Sự cố Thường gặp](#5-sự-cố-thường-gặp)

---

## 1. Bảng Ranh giới Sở hữu

| Nhóm đường dẫn | Owner | Quyền thao tác của Track C |
|---|---|---|
| `feature/tryon/**` | Track C | Sở hữu toàn bộ (code, UI, client, privacy, standaloneDemo, test) |
| `proxy/src/tryon/**`, `proxy/test/*tryon*` | Track C | Sở hữu toàn bộ router/provider/mock VTO |
| `proxy/scripts/tryon_local_server.ts`, `proxy/scripts/demo_*.sh`, `proxy/scripts/tryon.env.example` | Track C | Sở hữu toàn bộ script chạy demo/mock/live cục bộ |
| `eval/tryon/**`, `docs/execution/tracks/C*.md` | Track C | Sở hữu toàn bộ |
| `app/**`, `feature/today/**`, `feature/onboarding/**`, `feature/insights/**`, `core/model/**`, `core/datastore/**`, `core/ui/**` | Track D (cùng A) | **KHÔNG chạm** — Track D thực hiện wiring vào `:app` |
| `core/imaging/**`, `core/database/**`, `feature/wardrobe/**` | Track A | **KHÔNG chạm** |
| `proxy/src/index.ts`, `proxy/src/app.ts`, `proxy/src/env.ts`, `proxy/src/shared/**`, `proxy/src/recognition/**`, `proxy/src/outfits/**`, `core/ai/**` | Track B | **KHÔNG chạm** — Track B review và mount route vào `ROUTES` |

---

## 2. Hợp đồng Công khai & Nguồn Dữ liệu

| Thành phần / Port | Vị trí chính xác | Vai trò & Nguồn dữ liệu thực tế |
|---|---|---|
| `VirtualTryOn` | `feature/tryon/.../contract/TryOnContracts.kt:292-298` | Service chính (`capabilities`, `submit`, `observe`, `cancel`, `delete`). Bản tắt CORE dùng `CoreModeVirtualTryOn`; bản bật VTO dùng `AndroidVirtualTryOnClient`. |
| `TryOnMediaStorePort` | `feature/tryon/.../contract/TryOnContracts.kt:304-316` | Đọc/chuẩn hóa derivative/lưu kết quả riêng/xóa file (`LocalDiskTryOnMediaStore` tại `LocalDiskTryOnMediaStore.kt:298-305`). Nguồn ảnh gốc thuộc `MediaStore` / `LocalMediaStore` của Track A (`ImagingModule.kt:16`). |
| `TryOnProfilePort` | `feature/tryon/.../contract/TryOnContracts.kt:326-328` | Quan sát `TryOnPersonProfileSnapshot(profileId, revision, bodyImages)`. Đã có adapter `ProfileStorageTryOnPort` (`TryOnBridge.kt:85-94`) đọc từ `FileProfileStorage` của Track D (`DataStoreModule.kt:36-37`, lưu trong `context.noBackupFilesDir/profile`). |
| `TryOnWardrobeRevisionPort` | `feature/tryon/.../contract/TryOnContracts.kt:331-333` | Kiểm tra `revision` mới nhất của món đồ trước khi gửi VTO. Nguồn thuộc `WardrobeRepository` (`RoomWardrobeRepository`, `DatabaseModule.kt:42` — Track A). |
| `TryOnViewModel` | `feature/tryon/.../ui/TryOnViewModel.kt:241-250` | Quản lý state VTO, consent theo từng lượt, đổi ảnh và giữ job khi xoay màn hình; gọi `updateGarmentImages(Map<String, ImagePair>)` nếu cập nhật ảnh từ ngoài. |
| `TryOnRoute` & `tryOnGraph` | `feature/tryon/.../ui/TryOnNavigation.kt:19-43` | Đăng ký route `Routes.TRYON_PATTERN` trong `NavGraphBuilder`. |
| `TryOnPrivacyCoordinator` | `feature/tryon/.../privacy/TryOnPrivacyCoordinator.kt:79-85,515-555` | Khóa consent theo generation/id và xóa kết quả khi gọi `onPersonPhotoChangedOrDeleted`, `onGarmentChangedOrDeleted`, `onProfileCleared`. |
| `TryOnSelectionHandoff` | `core/model/.../tryon/TryOnSelection.kt:24-37` | Trao bộ đồ từ `TodayViewModel` sang `TryOnDestination` (Track D, bind `@Singleton` tại `TryOnDestination.kt:33-34`). |
| `createTryOnRouter` | `proxy/src/tryon/handler.ts:403` | Router HTTP xử lý `/v2/tryon/capabilities`, `/v2/tryon/preflight`, `/v2/tryon/jobs*`, `/v2/tryon/outputs/*` (cần adapter sang `RouteDef[]` tại `proxy/src/shared/registry.ts:41-54` khi Track B mount ở `proxy/src/index.ts:14`). |

---

## 3. Các bước Nối Đánh số

### Bước 1 — Route điều hướng `Routes.TRYON_PATTERN` trong `:app` *(ĐÃ NỐI trên `origin/main` — đã kiểm `:app:assembleDebug`)*

- **File liên quan:** `core/model/.../nav/Routes.kt:28-33`, `app/.../FitCheckApp.kt:80`, `app/.../tryon/TryOnDestination.kt:47-55`.
- **Code hiện có trên `origin/main` (`app/src/main/kotlin/com/fitcheckai/app/FitCheckApp.kt:72-81` & `TryOnDestination.kt:47-55`):**
  ```kotlin
  // FitCheckApp.kt:77-81
  onboardingGraph(navController)
  todayGraph(navController)
  wardrobeGraph(navController)
  tryOnDestination(navController)

  // TryOnDestination.kt:47-55
  fun NavGraphBuilder.tryOnDestination(navController: NavController) {
      tryOnGraph { outfitId, snapshotId ->
          TryOnRoute(
              viewModel = rememberTryOnViewModel(outfitId, snapshotId),
              onNavigateBack = { navController.popBackStack() },
              onNavigateToProfilePhotos = { navController.navigate(Routes.PROFILE) },
          )
      }
  }
  ```
- **Cách kiểm & Kết quả:** `ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.

### Bước 2 — Nút `"Thử đồ với bộ này"` ở `TodayScreen` & truyền `garmentImages` Trước/Sau *(ĐÃ NỐI trên `origin/main`; luồng bật VTO thật từ Today **chưa kiểm chứng**)*

- **File liên quan:** `feature/today/.../TodayScreen.kt:110-126`, `TodayNavigation.kt:22-25`, `TodayViewModel.kt:277-289`, `app/.../tryon/TryOnBridge.kt:76-82`.
- **Code hiện có trên `origin/main` (`TodayViewModel.kt:283-289` & `TryOnBridge.kt:76-82`):**
  ```kotlin
  // TodayViewModel.kt:283-289 — resolve cả front & back qua items.flatMap { it.images.refs() }
  val paths = withContext(Dispatchers.IO) {
      items.flatMap { it.images.refs() }.mapNotNull { ref -> media.displayPath(ref)?.let { ref.id to it } }.toMap()
  }
  checkCurrent(selected, outfit)
  tryOnHandoff.publish(TryOnSelection(outfit, items, paths))
  Routes.tryOn(outfit.id, outfit.wardrobeSnapshotId)

  // TryOnBridge.kt:76-82
  internal fun TryOnSelection.garmentImages(): Map<String, TryOnImagePair> =
      items.associate { item ->
          item.id to TryOnImagePair(
              front = item.images.front?.let { it.toTryOn(displayPaths[it.id]) },
              back = item.images.back?.let { it.toTryOn(displayPaths[it.id]) },
          )
      }
  ```
- **Lưu ý tích hợp:** Khi món đồ có ảnh mặt sau trong Room + `MediaStore`, `garmentImages()` tự map cả `front` lẫn `back`. Nếu ở `:app` người dùng chưa chụp ảnh trong tủ đồ khi mở thẳng không có selection, cần chắc chắn `TodayScreen` đã tải đủ item trước khi bấm `"Thử đồ với bộ này"`.
- **Cách kiểm & Kết quả:** `ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew :feature:today:testDebugUnitTest` → tất cả test `TodayViewModelTest` PASS (`CORE` mode). Luồng bấm từ `TodayScreen` sang backend VTO thật: **chưa kiểm chứng**.

### Bước 3 — Thay `CoreModeVirtualTryOn` bằng `AndroidVirtualTryOnClient` + `LocalDiskTryOnMediaStore` & nối đầy đủ Port *(chưa kiểm chứng trong `:app`)*

- **File cần sửa (do Track D thực hiện khi C-205 mở gate):**
  - `app/build.gradle.kts` (thêm `implementation(libs.ktor.client.core)` và `implementation(libs.ktor.client.okhttp)` để tạo `HttpClient(OkHttp)` trong `:app`).
  - `app/src/main/kotlin/com/fitcheckai/app/tryon/TryOnDestination.kt:38-85` (hiện gọi `CoreModeVirtualTryOn()` ở dòng `73`; `CoreModeVirtualTryOn.kt:19-22` khóa hoàn toàn mạng).
  - Thêm `WardrobeRepository` vào `TryOnEntryPoint` (`TryOnDestination.kt:40-44`) để cấp `TryOnWardrobeRevisionPort`.
- **Đoạn code cần thêm / thay trong `TryOnDestination.kt` *(đối chiếu từ `TryOnDemoActivity.kt:1454-1498`, `TryOnDestination.kt:66-84`, `TryOnLocalJobStore.kt:114` — chưa kiểm chứng chạy thật trong `:app`)*:**
  ```kotlin
  // 1) LocalDiskTryOnMediaStore đọc được cả filesDir/cacheDir lẫn noBackupFilesDir (nơi FileProfileStorage lưu ảnh người)
  val tryOnMediaStore = LocalDiskTryOnMediaStore(
      filesRootDir = context.filesDir,
      cacheRootDir = context.cacheDir,
      extraAllowedReadRoots = listOf(context.noBackupFilesDir),
  )

  // 2) Port kiểm tra revision tủ đồ (CT-01 -> Track C)
  val wardrobeRevisionPort = TryOnWardrobeRevisionPort { itemIds ->
      val snapshot = deps.wardrobe().getSnapshot().getOrNull() ?: return@TryOnWardrobeRevisionPort emptyMap()
      snapshot.items.filter { it.id in itemIds }.associate { it.id to it.revision }
  }

  // 3) PrivacyCoordinator + LocalJobStore bền trong noBackupFilesDir (không lên Google Cloud Backup)
  val privacyCoordinator = TryOnPrivacyCoordinator(
      mediaStore = tryOnMediaStore,
      wardrobeRevisions = wardrobeRevisionPort,
  )
  val localJobStore = AtomicFileTryOnLocalJobStore(File(context.noBackupFilesDir, "tryon_jobs_v2.json"))

  // 4) Transport & Client (thay CoreModeVirtualTryOn())
  val httpClient = HttpClient(OkHttp) {
      engine {
          config {
              connectTimeout(10, TimeUnit.SECONDS)
              readTimeout(260, TimeUnit.SECONDS)
              writeTimeout(60, TimeUnit.SECONDS)
          }
      }
  }
  val transport = KtorTryOnHttpTransport(
      httpClient = httpClient,
      baseUrl = tryOnBackendBaseUrl, // vd. HTTPS proxy hoặc http://10.0.2.2:8788 ở debug local
      localAuthToken = localAuthTokenIfAny,
  )
  val tryOnClient = AndroidVirtualTryOnClient(
      capabilityProvider = {
          transport.fetchCapabilities(sessionId).getOrElse { CoreModeVirtualTryOn.CORE_CAPABILITY }
      },
      transport = transport,
      localJobStore = localJobStore,
      mediaStore = tryOnMediaStore,
      privacyCoordinator = privacyCoordinator,
  )

  // 5) Nối ProfileStorage.invalidations (ProfileContracts.kt:62-76) ở scope cấp app/Singleton
  // Khi nhận BODY_PHOTO_REPLACED / BODY_PHOTO_REMOVED -> gọi privacyCoordinator.onPersonPhotoChangedOrDeleted(view, activeJobIds, tryOnClient)
  // Khi nhận PROFILE_CLEARED -> gọi privacyCoordinator.onProfileCleared(activeJobIds, tryOnClient)
  ```
- **Cách kiểm & Kết quả:** Dựng `:app:assembleDebug` + mở từ `TodayScreen` → `"Thử đồ với bộ này"` trên backend mock → card khóa `C-205` không còn xuất hiện, hiện đúng nút/chip khả dụng theo `/v2/tryon/capabilities`. **(Chưa kiểm chứng trong `:app`.)**

### Bước 4 — Mount `createTryOnRouter` vào `proxy/src/index.ts` *(chưa kiểm chứng trong Cloudflare Worker `proxy/src/index.ts`)*

- **File liên quan:** `proxy/src/index.ts:14-16`, `proxy/src/shared/registry.ts:41-54`, `proxy/src/app.ts:136-193`, `proxy/src/env.ts:125-137`, `proxy/src/tryon/handler.ts:65-71,395-404`, và [docs/track-b/C-207-mount-review.md](../../track-b/C-207-mount-review.md).
- **Vấn đề kiến trúc đã xác định:**
  1. `proxy/src/index.ts:14` dùng `ROUTES: readonly RouteDef[]` (`handle(body, ctx)`), trong khi `createTryOnRouter()` export `handle(request: Request, env, context)` (`handler.ts:395-404`).
  2. Sau auth của B (`app.ts:154-157`), phải truyền `sessionId: ctx.session.sessionId` và **không** tin header `x-session-id` tự khai từ client.
  3. Khai báo `maxBodyBytes: MAX_WIRE_BYTES` (`12 MiB`) và `deadlineMs: DUAL_DEADLINE_MS` (`180_000`) cho các route VTO dài (được `app.ts:33-37` kẹp tới `300_000 ms`).
  4. Chế độ `AI_LIVE` trong `handler.ts` yêu cầu `DurableTryOnJobStore` bền vững (Cloudflare KV/Durable Objects); mặc định `InMemoryTryOnJobStore` chỉ chạy mock/unit test.
- **Adapter mẫu `tryonRoutes: RouteDef[]` cho Track B/C mount vào `proxy/src/index.ts:14` *(chưa kiểm chứng trên Worker)*:**
  ```ts
  // Trong adapter mount của Track B / src/tryon/routes.ts
  import { createTryOnRouter, DUAL_DEADLINE_MS, MAX_WIRE_BYTES } from "./tryon/handler";
  import type { RouteContext, RouteDef } from "./shared/registry";

  const tryOnRouter = createTryOnRouter(/* store: Durable Object/KV khi bật AI_LIVE */);

  function forwardToTryOn(method: "GET" | "POST" | "DELETE", pathname: string, body: unknown, ctx: RouteContext, reserveCap = false): Promise<Response> {
    if (reserveCap) ctx.reserveUpstreamCall();
    const req = new Request(`https://proxy.internal${pathname}`, {
      method,
      headers: method === "POST" ? { "content-type": "application/json" } : undefined,
      body: method === "POST" && body !== null ? JSON.stringify(body) : undefined,
      signal: ctx.signal,
    });
    return tryOnRouter.handle(req, ctx.env as never, {
      sessionId: ctx.session.sessionId, // luôn lấy từ Track B auth
      requestId: ctx.requestId,
      nowMs: ctx.deps.now(),
    });
  }

  export const tryonRoutes: readonly RouteDef[] = [
    { method: "GET", path: "/v2/tryon/capabilities", service: "tryon", maxBodyBytes: 0, handle: (_b, ctx) => forwardToTryOn("GET", "/v2/tryon/capabilities", null, ctx) },
    { method: "POST", path: "/v2/tryon/jobs", service: "tryon", maxBodyBytes: MAX_WIRE_BYTES, deadlineMs: DUAL_DEADLINE_MS, handle: (body, ctx) => forwardToTryOn("POST", "/v2/tryon/jobs", body, ctx, true) },
    { method: "GET", path: "/v2/tryon/jobs/:jobId", service: "tryon", maxBodyBytes: 0, handle: (_b, ctx) => forwardToTryOn("GET", `/v2/tryon/jobs/${ctx.params.jobId}`, null, ctx) },
    { method: "POST", path: "/v2/tryon/jobs/:jobId/cancel", service: "tryon", maxBodyBytes: 0, handle: (_b, ctx) => forwardToTryOn("POST", `/v2/tryon/jobs/${ctx.params.jobId}/cancel`, null, ctx) },
    { method: "DELETE", path: "/v2/tryon/jobs/:jobId", service: "tryon", maxBodyBytes: 0, handle: (_b, ctx) => forwardToTryOn("DELETE", `/v2/tryon/jobs/${ctx.params.jobId}`, null, ctx) },
    { method: "GET", path: "/v2/tryon/outputs/:jobId/:view", service: "tryon", maxBodyBytes: 0, handle: (_b, ctx) => forwardToTryOn("GET", `/v2/tryon/outputs/${ctx.params.jobId}/${ctx.params.view}`, null, ctx) },
  ];

  // Trong proxy/src/index.ts:14 (Track B sửa)
  export const ROUTES: readonly RouteDef[] = [recognitionRoute, outfitsRoute, ...tryonRoutes];
  ```
- **Cách kiểm & Kết quả:** `npm --prefix proxy run typecheck && npm --prefix proxy test`; cấu hình `SERVICE_TRYON=on` trong `env`. *(Phần nối `KtorTryOnHttpTransport` kèm Bearer token `/v2/session` của `:app` tới Worker: **chưa kiểm chứng**.)*

### Bước 5 — URL Backend / Emulator & Cấu hình `proxy/scripts/tryon.env.example` *(ĐÃ KIỂM CHỨNG trên local server `8788`)*

| Môi trường chạy Android | URL backend VTO | Ghi chú |
|---|---|---|
| Máy host / curl | `http://127.0.0.1:8788` | Kiểm tra nhanh `/v2/tryon/preflight` |
| Emulator (`Pixel_6`) | `http://10.0.2.2:8788` | Bản Demo dùng `standaloneDemo` manifest; nếu `:app` debug gọi HTTP local thì Track D tự đặt `networkSecurityConfig` trong `app/src/debug/` (**chưa kiểm chứng trong `:app`**) |
| Máy thật qua USB | `http://127.0.0.1:8788` | Chạy trước `adb reverse tcp:8788 tcp:8788` |

Khởi tạo file cấu hình ngoài Git:
```bash
mkdir -p -m 700 ~/.fitcheckai_private
cp proxy/scripts/tryon.env.example ~/.fitcheckai_private/tryon.env
chmod 600 ~/.fitcheckai_private/tryon.env
```
- **Cách kiểm:** `./proxy/scripts/demo_start_mock_backend.sh --daemon && curl -fsS http://127.0.0.1:8788/v2/tryon/preflight` → `liveEnabled: false`, `provider: "fosta"`.

---

## 4. Checklist Nghiệm thu bằng Backend Mock (`$0.00`)

- [ ] Khởi động mock backend (`./proxy/scripts/demo_start_mock_backend.sh --daemon`) và xác nhận `GET http://127.0.0.1:8788/v2/tryon/preflight` trả `liveEnabled: false`.
- [ ] `npm --prefix proxy run typecheck && npm --prefix proxy test` PASS toàn bộ test proxy.
- [ ] `ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew :app:assembleDebug testDebugUnitTest :feature:tryon:lintDebug` PASS, `0` lỗi lint.
- [ ] Kiểm tra `aapt2 dump badging` & `xmltree` trên `app/build/outputs/apk/debug/app-debug.apk`: chỉ `MainActivity` là `LAUNCHER`, không có icon/label/cleartext của demo.
- [ ] Trên `Pixel_6` với bản mock: mở màn Thử đồ → kiểm tra badge giả lập, chọn ảnh Trước/Sau, tick đồng ý quyền riêng tư, bấm `"Tạo ảnh thử đồ"` → nhận ảnh kết quả, thử `"So sánh ảnh gốc"`, bấm `"Xóa kết quả thử đồ"` xác nhận xóa sạch khung kết quả và `consentConfirmed` tự reset khi thay ảnh người.
- [ ] (Nếu nối `:feature:tryon` hoàn chỉnh) Chạy thêm `connectedDebugAndroidTest`: 13/13 PASS với cờ `requireBackendFostaE2e=true` & `requireStep3eLargeImageE2e=true`.

---

## 5. Sự cố Thường gặp

| Mã / Hiện tượng | Nguyên nhân chính | Cách xử lý |
|---|---|---|
| **`403 CONSENT_REQUIRED`** | `consent.consentedPersonMediaId` không khớp `personImages.*.mediaId` do bước nén/chuẩn hóa tạo ID tạm mới. | Giữ `derivedFromMediaId ?: id` khi gửi lên server (`AndroidVirtualTryOnClient.kt`) và dùng `LocalDiskTryOnMediaStore.prepareUploadDerivative`. |
| **FOSTA `status='incomplete'` / timeout** | Prompt quá dài hoặc timeout proxy < 120s/180s. | Dùng đúng `fosta_prompt.ts` v2.1 (~420–620 ký tự), giữ `RouteDef.deadlineMs: 180_000`, `FOSTA_TIMEOUT_MS=240000`, `OkHttp readTimeout >= 260s`. |
| **`UNKNOWN_SUBMISSION` / `409 CONFLICT`** | Mất mạng ngay sau `POST /v2/tryon/jobs` khiến client khóa tự retry để tránh trừ lượt kép. | Không auto-retry POST; bấm **Hủy yêu cầu** hoặc **Xóa kết quả thử đồ** (hoặc chờ hết deadline phiên) rồi tạo lượt mới. |
| **`BUDGET_EXCEEDED` / hết quota** | Vượt `TRYON_LIVE_MAX_REQUESTS`, `TRYON_LIVE_MAX_ESTIMATED_USD` hoặc chưa bật `TRYON_LIVE_ENABLED` / `FOSTA_APPROVE_QUOTA`. | Kiểm tra `/v2/tryon/preflight` xem `usedLiveRequests` và `blockers`; chuyển về mock (`TRYON_LIVE_ENABLED=false`) khi phát triển/QA. |
| **Lỗi Manifest Merger / 2 icon launcher** | Gộp nhầm `src/standaloneDemo` vào `:app` hoặc đụng thuộc tính `<application>`. | Khi build `:app`, tuyệt đối **không** truyền `-I feature/tryon/standalone_demo.init.gradle.kts`; chỉ dùng init script này cho APK demo độc lập và instrumentation test của `:feature:tryon`. |
