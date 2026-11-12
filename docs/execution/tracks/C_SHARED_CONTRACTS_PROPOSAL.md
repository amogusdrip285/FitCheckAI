# Đề xuất Đồng bộ Shared Contracts v2 cho Track C (Compatibility & Integration Proposal)

- **Từ:** Track C — Virtual Try-On (`C-206`, `C-207`, `C-208`, `C-209`, `C-210`, `C-212`)
- **Gửi tới:** Track D (Owner `core/model`, `settings.gradle.kts`, `app/**`, `AndroidManifest.xml`), Track B (Owner `proxy/src/index.ts`), Track A (Owner `MediaStore`, `WardrobeRepository`)
- **Ngày lập:** `2026-11-11`
- **Nguyên tắc tuân thủ:** Track C **không tự ý sửa** bất kỳ file shared nào thuộc quyền sở hữu của Track A/B/D để tránh xung đột Git. Toàn bộ code của Track C trong `:feature:tryon` và `proxy/src/tryon/` đã được thiết kế độc lập với seam tích hợp 1:1 dưới đây.

---

## Tổng quan các điểm lệch giữa Sprint 0 (`7d995a5`) và Contracts v2 (`CONTRACTS.md`)

Hiện tại `:core:model` mới chứa các model Sprint 0 v1 (`ClothingItem.imagePath`, `Outfit(itemIds, OutfitSource.AI|RULES)`, `StyleProfile`, `WeatherProvider`, `OutfitService`) và hoàn toàn chưa có các kiểu dữ liệu `CT-01`, `CT-03`, `CT-04`, `CT-06`, `CT-07` phục vụ Virtual Try-On.

Dưới đây là đặc tả đầy đủ 6 mục bắt buộc cho từng thay đổi trên vùng shared code.

---

## Đề xuất 1 — Bổ sung VTO & Media Contracts v2 vào `:core:model`

1. **File cần thay đổi:**
   - `core/model/src/main/kotlin/com/fitcheckai/core/model/Media.kt` (mới, thuộc `S-002`)
   - `core/model/src/main/kotlin/com/fitcheckai/core/model/TryOn.kt` (mới, thuộc `S-002` — theo `CT-06` & `CT-07`)
   - `core/model/src/main/kotlin/com/fitcheckai/core/model/nav/Routes.kt` (thêm route `TRY_ON`)
2. **Lý do:**
   - Cho phép Track D (`TodayScreen`, `ProfileStorage`), Track A (`MediaStore`), và Track C (`VirtualTryOn`, `TryOnScreen`, `TryOnPrivacyCoordinator`) giao tiếp qua shared contract duy nhất đúng `CT-01`, `CT-04`, `CT-06`, `CT-07` mà không import chéo implementation.
3. **Proposed signatures:**
   ```kotlin
   package com.fitcheckai.core.model

   import kotlinx.coroutines.flow.Flow

   enum class MediaScope { GARMENT, PERSON, TRYON_RESULT }
   enum class ImageView { FRONT, BACK }

   data class MediaRef(
       val id: String,
       val ownerEntityId: String,
       val scope: MediaScope,
       val view: ImageView,
       val localRelativePath: String,
       val mimeType: String,
       val width: Int,
       val height: Int,
       val byteCount: Long,
       val contentHash: String,
       val revision: Long,
       val derivedFromMediaId: String? = null,
   )

   data class ImagePair(
       val front: MediaRef? = null,
       val back: MediaRef? = null,
   )

   enum class ConsentPurpose { RECOGNITION, TRYON }

   data class ConsentGrant(
       val id: String,
       val purpose: ConsentPurpose,
       val providerId: String,
       val providerConfigVersion: String,
       val selectedMediaIds: Set<String>,
       val allowedViews: Set<ImageView>,
       val requestGeneration: Long,
       val confirmedByUser: Boolean,
       val issuedAtEpochMillis: Long,
       val revokedAtEpochMillis: Long? = null,
   )

   enum class FeasibilityDecision { UNKNOWN, NO_GO, GO_FRONT_ONLY, GO_DUAL }
   enum class TryOnSource { AI_LIVE, FAKE }
   enum class ViewProcessState { NOT_REQUESTED, QUEUED, RUNNING, SUCCEEDED, FAILED, EXPIRED, CANCELLED }
   enum class QualityReviewState { UNREVIEWED, ACCEPTED, REJECTED }
   enum class TryOnJobStatus {
       QUEUED, RUNNING, PARTIAL, SUCCEEDED, FAILED, TIMED_OUT, UNKNOWN_SUBMISSION, CANCELLED, DELETED
   }
   enum class RemoteDeletionState {
       NOT_UPLOADED, BASE64_EPHEMERAL_60M, EXPIRES_AUTOMATICALLY_72H, PENDING_BEST_EFFORT, UNSUPPORTED, UNKNOWN
   }

   data class TryOnCapability(
       val decision: FeasibilityDecision,
       val supportedViews: Set<ImageView>,
       val supportedCategories: Set<Category>,
       val maxGarmentsPerJob: Int,
       val supportsNativePairedViews: Boolean,
       val runtimeVerifiedViews: Set<ImageView>,
       val providerId: String,
       val providerConfigVersion: String,
   )

   interface VirtualTryOn {
       suspend fun capabilities(): Result<TryOnCapability>
       suspend fun submit(request: TryOnRequest): Result<TryOnJobHandle>
       fun observe(jobId: String): Flow<TryOnResult>
       suspend fun cancel(jobId: String): Result<CancellationReceipt>
       suspend fun delete(jobId: String): Result<DeletionReceipt>
   }
   ```
   Trong `Routes.kt`:
   ```kotlin
   const val ARG_OUTFIT_ID = "outfitId"
   const val ARG_SNAPSHOT_ID = "snapshotId"
   const val TRY_ON_PATTERN = "tryon/{$ARG_OUTFIT_ID}?$ARG_SNAPSHOT_ID={$ARG_SNAPSHOT_ID}"
   fun tryOn(outfitId: String, snapshotId: String? = null): String =
       if (snapshotId == null) "tryon/${encode(outfitId)}"
       else "tryon/${encode(outfitId)}?$ARG_SNAPSHOT_ID=${encode(snapshotId)}"
   ```
4. **Consumer bị ảnh hưởng:**
   - Track D (`TodayScreen` truyền `outfitId` + snapshot sang `Routes.tryOn(...)`, `ProfileStorage` phát sự kiện xóa ảnh người sang `TryOnPrivacyCoordinator`).
   - Track A (`MediaStore` cung cấp bytes/remove cho `MediaScope.PERSON`, `GARMENT`, `TRYON_RESULT`).
   - Track C (`feature/tryon` nối adapter `Sprint0ModelInterop` / `TryOnContractPort` sang kiểu `:core:model` ngay khi `S-002` merge).
5. **Cách kiểm thử:**
   - `./gradlew :core:model:test :feature:tryon:testDebugUnitTest`
6. **Owner cần review:** **Track D** (chính), **Track A** (`MediaRef`/`MediaStore`), **Track C** (`VirtualTryOn`).

---

## Đề xuất 2 — Đăng ký module `:feature:tryon` vào Gradle & App Navigation

1. **File cần thay đổi:**
   - `settings.gradle.kts`
   - `app/build.gradle.kts`
   - `app/src/main/kotlin/com/fitcheckai/app/FitCheckApp.kt`
2. **Lý do:**
   - Module `feature/tryon` đã được Track C xây dựng hoàn chỉnh theo đúng convention `fitcheck.android.feature`. Cần Track D thêm `include(":feature:tryon")` và gọi `tryOnGraph(...)` trong `NavHost` gốc.
3. **Proposed diff:**
   - Trong `settings.gradle.kts`:
     ```kotlin
     include(":feature:tryon")
     ```
   - Trong `app/build.gradle.kts`:
     ```kotlin
     implementation(project(":feature:tryon"))
     ```
   - Trong `FitCheckApp.kt` (`NavHost`):
     ```kotlin
     tryOnGraph(
         onNavigateBack = { navController.popBackStack() },
         onNavigateToProfile = { navController.navigate(Routes.ONBOARDING) },
     )
     ```
4. **Consumer bị ảnh hưởng:**
   - `:app` composition root và điều hướng từ màn `Today` sang `TryOn`.
   - Lưu ý: Trong lúc chờ Track D cập nhật `settings.gradle.kts`, Track C kiểm thử độc lập bằng init-script Gradle tạm ngoài Git (`-I .../tryon_include.init.gradle.kts`), không chạm vào `settings.gradle.kts`.
5. **Cách kiểm thử:**
   - `./gradlew assembleDebug testDebugUnitTest lintDebug`
6. **Owner cần review:** **Track D**.

---

## Đề xuất 3 — Quy tắc Backup & Private Storage trên Android (`AndroidManifest.xml`)

1. **File cần thay đổi:**
   - `app/src/main/AndroidManifest.xml` (và `xml/data_extraction_rules.xml`, `xml/backup_rules.xml` khi Track D mở backup cho dữ liệu không nhạy cảm ở `D-004`).
2. **Lý do:**
   - Hiện tại `app/src/main/AndroidManifest.xml` đang đặt `android:allowBackup="false"`, vốn an toàn cho privacy mặc định. Tuy nhiên trên Android 12+ (`targetSdk >= 31`), hệ thống sử dụng `android:dataExtractionRules` cho cả Cloud Backup và Device-to-Device transfer. Nếu Track D thêm `dataExtractionRules` ở `D-004`, bắt buộc loại trừ thư mục chứa ảnh cơ thể, ảnh kết quả VTO và file `tryon_jobs_v2.json`.
3. **Proposed XML rules (nếu bật extraction rules):**
   ```xml
   <data-extraction-rules>
       <cloud-backup>
           <exclude domain="no_backup" path="." />
           <exclude domain="file" path="media/person" />
           <exclude domain="file" path="media/tryon_result" />
           <exclude domain="file" path="tryon/" />
       </cloud-backup>
       <device-transfer>
           <exclude domain="no_backup" path="." />
           <exclude domain="file" path="media/person" />
           <exclude domain="file" path="media/tryon_result" />
           <exclude domain="file" path="tryon/" />
       </device-transfer>
   </data-extraction-rules>
   ```
   *(Ngoài ra Track C lưu `tryon_jobs_v2.json` mặc định trong `Context.noBackupFilesDir` để được hệ điều hành loại trừ tự động).*
4. **Consumer bị ảnh hưởng:**
   - Track D (`D-004`), Track A (`A-002`), Track C (`C-208`, `C-210`).
5. **Cách kiểm thử:**
   - Kiểm tra manifest merger và đường dẫn `noBackupFilesDir` trên thiết bị/emulator.
6. **Owner cần review:** **Track D** & **Track A**.

---

## Đề xuất 4 — Mount Route `/v2/tryon/jobs*` trong Shared Proxy (`proxy/src/index.ts`)

1. **File cần thay đổi:**
   - `proxy/src/index.ts` (thuộc quyền sở hữu của Track B tại task `B-001`)
2. **Lý do:**
   - Track C đã đóng gói toàn bộ router và logic xử lý `/v2/tryon/jobs` độc lập tại `proxy/src/tryon/handler.ts` mà không sửa `proxy/src/index.ts`. Khi Track B hoàn tất `B-001` (xác thực phiên, rate-limit và budget envelope chung) và `C-205` đạt `GO`, Track B chỉ cần mount handler của Track C bằng 4 dòng code.
3. **Proposed integration snippet trong `proxy/src/index.ts`:**
   ```ts
   import { createTryOnRouter } from "./tryon/handler";

   // Bên trong fetch(request, env):
   if (pathname.startsWith("/v2/tryon/jobs")) {
     const session = await authenticateSession(request, env); // do Track B cung cấp
     if (!session.ok) return session.errorResponse;
     return tryOnRouter.handle(request, env, { sessionId: session.sessionId });
   }
   ```
4. **Consumer bị ảnh hưởng:**
   - `proxy/src/index.ts` (Track B) và `AndroidVirtualTryOnClient` (Track C).
5. **Cách kiểm thử:**
   - `cd proxy && npm run typecheck && npm test`
6. **Owner cần review:** **Track B**.
