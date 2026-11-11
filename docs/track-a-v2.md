# Track A v2 — chạy độc lập và bàn giao

Ngày cập nhật: 11/11/2026. Phạm vi người dùng xác nhận: làm phần độc lập trước, chưa có máy Android thật hoặc nhánh B/D riêng.
Không coi build thành công, fake AI hoặc test giả lập là nghiệm thu 100% các gate trong kế hoạch.

## Chạy trên máy tính

1. Cài JDK 21 và Android SDK 35; tạo Android emulator API 28+ (đang kiểm với API 35). `local.properties` chỉ chứa đường dẫn SDK máy mình, không commit.
2. Chạy `gradlew.bat :app:assembleDebug` trên Windows hoặc `./gradlew :app:assembleDebug` trên macOS/Linux.
3. Mở emulator, chạy `gradlew.bat :app:installDebug`, mở FitCheck AI. Qua onboarding hiện tại rồi chọn **Tủ đồ**.
4. Chọn **Ảnh kiểm thử** → **Thêm bộ ảnh vào tủ đồ**. Lần đầu thêm 21 món/24 ảnh; nhập lại bỏ qua món đã có, không ghi đè tủ hiện hữu.
5. Thử tìm `áo`, `jeans`, lọc category/color, mở món có 2 mặt, sửa tên/nhãn, thay riêng ảnh sau và lưu. Tắt app/mở lại kiểm dữ liệu.
6. Thử món mới chỉ có ảnh trước. Có thể lưu với category “Chưa xác định”; không tự điền chất liệu/mùa/phong cách.
7. Xóa chỉ món thử của mình; hủy dialog phải giữ dữ liệu. Nhãn fixture cần được kiểm lại trước khi dùng làm dữ liệu cá nhân.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Release không có bộ fixtures; không dùng `adb uninstall` hoặc Clear storage khi kiểm migration.
Chỉ đặt `fitcheck.proxyUrl` không kích hoạt nhận diện v2: cần client B-007 và thay binding `ClothingRecognition`.

## Kiến trúc đã áp dụng

Checkout có sẵn Gradle multi-module/Hilt. Giữ kiến trúc này để có thể build/test với code B/C/D v1 hiện hữu.
Namespace `com.fitcheckai.core.model.wardrobe` chứa **đề xuất implementation contract chung v2**, để D/B/C review theo
[alignment với checkout](execution/SPRINT0_TO_V2_ALIGNMENT.md). Không tạo thêm một contract v2 cạnh tranh trong feature A.
Các type v1 chỉ giữ để consumer cũ compile; không ghi S-002/PRE-007 của D là DONE hộ D.

| Boundary | Implementation / consumer |
|---|---|
| `WardrobeRepository` v2 | `RoomWardrobeRepository`; `WardrobeListViewModel`, `WardrobeEditorViewModel` |
| `MediaStore` | `LocalMediaStore`, một Hilt singleton; GARMENT/PERSON/TRYON_RESULT cùng API |
| `ClothingRecognition` | `UnavailableRecognition` cho tới khi B giao implementation; test dùng `FakeRecognition` |
| v1 wardrobe | `LegacyWardrobeBridge` chỉ đọc v2, giữ counters v1 đã có; không có write v1 cạnh tranh |
| Reusable visual | `OutfitVisual`, `ItemThumbnail`, `OutfitVisualList`, `resolveOutfitItems`; DTO/callback, không NavHost/provider |
| Fixtures | `core:model/src/testFixtures`; ảnh thật chỉ ở debug assets, import chủ động |
| Share prototype | `OutfitShare.prepare` trả `ACTION_SEND`/content URI đọc một collage; component nhận callback `onShare` |
| Sync prototype | `GarmentTransfer`/mapping metadata, [quy tắc conflict và privacy](track-a-sync-boundary.md); chưa upload/restore cloud |

v1 không biểu diễn UNKNOWN/nullable formality: bridge bỏ item chưa có category, dùng default formality 2 chỉ cho consumer v1,
không ghi default đó vào v2 hay coi là annotation. Wear tracking nằm ngoài scope v2; bridge từ chối write/markWorn bằng lỗi rõ ràng.
Today/Profile/Insights v1 chưa tương đương journey v2; không dùng màn stub này để chứng minh AI, ProfileStorage hay selected VTO đã xong.

## Dữ liệu và độ bền

Room schema 2 thêm `wardrobe_v2`, `wardrobe_cleanup`, `wardrobe_legacy_ids`; giữ các bảng v1 để bảo toàn dữ liệu cũ.
Migration giữ UUID đã hợp lệ; ID seed cũ được map sang UUID xác định, có bảng alias. Giữ createdAt, bản v2 đầu revision 1.
Các nhãn cũ được đánh dấu cần review vì nguồn v1 có thể là stub. Thiếu file ảnh không reset DB: giữ item để thay ảnh.
Không dùng destructive migration; không tự seed ảnh minh họa khi tạo DB nữa.

Mỗi lần sửa cần `expectedRevision`; stale write trả CONFLICT, item đã xóa trả STALE_INPUT. Update giữ ID/createdAt và tăng revision.
Delete ghi tombstone, loại khỏi snapshot/flow ngay, xóa payload rồi cleanup ảnh sau transaction. Cleanup có journal và retry khi đọc lại.
Snapshot ID được tạo từ danh sách ID/revision; `isCurrent` và `resolveOutfitItems` giúp consumer chặn chọn outfit stale/deleted.
Consumer D/C phải kiểm snapshot lại trước action/upload; events chỉ giúp làm mới UI, không thay thế kiểm revision ở boundary.

Ảnh import JPEG/PNG được xoay theo EXIF, xóa metadata, thu nhỏ tối đa 2.048 px, giới hạn 4 MiB; input tối đa 32 MiB/100 MP.
Ảnh mỗi mặt có UUID/hash, không ghi đè file cũ khi thay view. Hai mặt không được dùng cùng content hash, nhưng hash khác không chứng minh cùng garment;
UI yêu cầu người dùng chọn đúng cặp, fixture mismatch vẫn cần người/provider review. Không claim có model phát hiện nhầm cặp.
GARMENT ở private files; PERSON/TRYON_RESULT và registry ở noBackupFilesDir. Manifest app hiện `allowBackup=false`.
`read` xác minh tham chiếu đã đăng ký, path canonical, bytes/hash; không cho consumer dùng đường dẫn tùy ý để đọc file private.
Derivative có `derivedFromMediaId`, preset caps; xóa original dọn cả descendants. Segmentation không ghi đè original.

Draft được giữ trong SavedStateHandle qua recreation; ảnh draft đã copy vào private storage. Cancel dọn ảnh mới không thuộc item đã lưu.
Ảnh garment draft bị bỏ lại do process chết được thu hồi khi mở tủ sau 7 ngày nếu không có item đã lưu tham chiếu; PERSON/results không thuộc chính sách này.
Thu hồi này áp dụng file đã có registry. Crash đúng giữa ghi file và ghi registry có thể để lại file chưa được lập chỉ mục;
chưa có kiểm thử kill-at-every-write hay bộ quét orphan cho trường hợp đó. Derivative của original đang dùng được giữ tới khi original bị xóa.
SavedStateHandle không phải bảo đảm khôi phục draft sau force-stop; A-004 kiểm dữ liệu **đã lưu**. Không hứa phục hồi mọi draft khi Android xóa task.

## Recognition và imaging

Chỉ bấm **Gợi ý bằng AI** rồi xác nhận đúng provider/views mới tạo request. Consent gắn purpose, request ID/generation, config version và media IDs/views.
Provider đổi config, revoked consent, sai request hay thiếu capability phải bị từ chối. Timeout 20 giây, có hủy và đường tự điền.
Field đã sửa tay thắng kết quả trả chậm; thay ảnh/hủy/lưu làm request cũ vô hiệu. FAKE hiện nhãn rõ trong test, không thành AI_LIVE.
Các trường UNKNOWN/null không bị đổi thành nhãn đoán chắc chắn. Kết quả chưa biết không giữ provenance AI cũ.

CameraX bind theo lifecycle, xin quyền khi dùng; từ chối vẫn chọn Photo Picker được. Chọn một route CameraX, không bổ sung camera intent trùng chức năng.
Chụp mới đi qua cùng pipeline MediaStore. Cold-model/offline segmentation có đường bỏ qua. Readiness dựa trên ModuleInstallClient;
preview chỉ hiện thành công khi có foreground thực, không trả ảnh gốc giả làm kết quả tách nền.
Nguồn kỹ thuật: [ML Kit subject segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android),
[ModuleInstallClient](https://developers.google.com/android/guides/module-install-apis).

## Kiểm thử và bàn giao

```powershell
python tools/data/validate_fixtures.py
.\gradlew.bat :core:model:test :core:database:testDebugUnitTest :core:imaging:testDebugUnitTest :feature:wardrobe:testDebugUnitTest
.\gradlew.bat assembleDebug testDebugUnitTest :core:model:test lintDebug :core:database:verifyDatabaseCoverage
```

Máy RAM thấp dùng `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" -Pkotlin.compiler.execution.strategy=in-process`.
Báo cáo unit tests trong mỗi module `build/reports/tests/`; coverage ở `core/database/build/reports/jacoco/coverageReport/`.
Evidence và hạn chế thực tế nằm ở [QA](qa/track-a-acceptance.md), trạng thái card chỉ cập nhật tại [board](execution/TASK_BOARD.md).
[Báo cáo v1](track-a-verification.md) là lịch sử, không được dùng thay kết quả v2.

Điểm D/B/C cần review: namespace/API v2, singleton media ownership, ProfileStorage chưa có, binding recognition, invalidation/stale outfit,
consent/revoke qua job, adapter đường dẫn sang request bytes và gate artifact máy thật. Không có API key hoặc paid resource được tự provision.
