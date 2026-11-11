# Track A — bổ sung phần độc lập sau bàn giao

Ngày 11/11/2026, tiếp tục từ `main` `114d3cf` (đã merge bản A đầu). Người dùng yêu cầu hoàn thiện phần chưa cần máy thật/code B/D,
cập nhật docs và push tiếp lên main bằng tài khoản `NQKhaixyz`. Không thay contract shared, schema database, code B/D hay bật recognition giả.

## Các thay đổi đã làm

| Phần | Hành vi và bằng chứng kiểm |
|---|---|
| Orphan sau interruption | Quét file GARMENT UUID cũ hơn 7 ngày, kể cả `.new`/`.bak` chưa có index. Giữ saved refs, file mới, registry/journal/sidecar, tên không quản lý; không đi qua symlink hoặc sang PERSON/results. Unit và `MediaRecoveryDeviceTest` chạy trên Android. |
| Ancestor của derivative | Nếu item giữ derivative, bảo vệ toàn bộ chuỗi ảnh gốc của nó khi thu hồi draft; không xóa theo riêng direct refs. Có regression test. |
| Cleanup lỗi cục bộ | Một journal lỗi không ngăn xử lý các journal hợp lệ hoặc hàng đợi Room khác. Mục lỗi giữ để retry; không xóa/quét rộng để “sửa” dữ liệu không đọc được. |
| Hủy tại dispatcher boundary | Media import/derive và file foreground vừa ghi được dọn nếu coroutine bị hủy trước khi caller nhận ref/path; original không bị thay. |
| Preview tách nền | `GarmentCutoutProcessor` riêng cho garment; state generation chặn readiness/result cũ. Retry, đổi view, skip và đóng screen dọn preview; output tới muộn cũng được dọn. Bảy test điều khiển thời gian gồm unavailable/downloading/error/missing/late/cancel/close/invalid original return. |
| Room native | 3 ca Android SQLite/media thật: save/reopen/replace/delete, migration 1→2 giữ counters/ảnh, trigger SQL abort rollback cả row và cleanup, 8 writer qua 2 repository chỉ một revision thắng. Chạy trong package test riêng. |
| Provenance dataset | 21 record có `fieldProvenance`, `referenceFields`, trạng thái independent review PENDING; `groundTruthFields=[]`. Giữ nguyên 24 file ảnh và giá trị nhãn hiện có. Validator chặn claim ground truth chưa kiểm độc lập. |
| Mẫu bulk chia sẻ | Đúng 100 ảnh CC0, 6 category, nhãn gốc và giấy phép ở `data/samples/modern-clothing-100`. Chọn từ train, không trùng fixtures; có script tái tạo/validator trong CI. Bulk đầy đủ vẫn local theo yêu cầu mới nhất. |
| Script evidence | Mỗi `--label` phải mới để không lẫn WAL cũ; kiểm `PRAGMA integrity_check` trước so rows. Chạy khi app idle; không hỗ trợ thu snapshot đồng thời người dùng đang sửa dữ liệu. |

Các ca unit mô phỏng trạng thái interruption và cancellation, không phải bằng chứng đã kill process tại mọi instruction của Android/filesystem.
Media recovery không tự xóa file hỏng đang được item tham chiếu hoặc tái tạo nhãn/ảnh mất; UI vẫn cho người dùng sửa/thay ảnh.

## Kết quả chạy

- Build debug, release unsigned, lint và gate database coverage: **PASS**, lần kiểm cuối code Android lúc 19:31 ngày 11/11/2026 (UTC+7).
- JVM/Robolectric: **101 ca được phát hiện, 100 PASS, 1 SKIP**, không failure/error. Ca skip là kiểm FileProvider trên Windows; URI/FileProvider đã được kiểm bằng Android instrumentation.
- Android API 35: **6 ca được phát hiện, 5 PASS, 1 SKIP**, không failure/error. Ca skip là probe ML Kit vì module chưa sẵn sàng, chi tiết bên dưới.
- Database line coverage: **341/377 = 90,45%**, đạt gate 80%.
- Fixtures: **21 món, 24 ảnh, 3 cặp đã đối chiếu nguồn, 6 category**; validator hash/quyền/provenance PASS.
- Mẫu bulk: **100 ảnh, 6 category, 2,63 MiB**; validator hash/nhãn nguồn/license/metadata PASS. Đây là kiểm toàn vẹn dữ liệu, chưa phải xác nhận từng nhãn đúng với ảnh.
- App thật trên emulator: mở món có cặp ảnh, yêu cầu tách nền, thấy trạng thái model đang tải và ảnh gốc; bỏ chỉnh sửa giữ nguyên toàn bộ row `wardrobe_v2`.
- Force-stop/reopen **3 lần**, kiểm integrity/bytes PASS: **51 món active, 1 tombstone, 30 row legacy, 54 media hash**. Evidence tại `build/outputs/track-a-device/independent-followup/summary.json`.

SHA-256 APK local của lần kiểm này (CI có thể cho byte khác do môi trường build):

| APK | SHA-256 |
|---|---|
| Debug | `3469b17fba0f7ead7f2b1bca942b4b2d73c8c2803eac543758e6f61a8623f6c1` |
| Release unsigned | `7bda8b287bf8bcce7687ba5617ea910a4bc24c92c91f62bb6242172291bf5005` |

Debug có 24 ảnh fixtures; release không có fixtures. Mẫu bulk 100 ảnh chỉ nằm trong repo, không thêm assets vào APK.

Lệnh cho teammate (JDK 21, SDK 35 và một emulator/thiết bị kết nối):

```powershell
python tools/data/validate_fixtures.py
python tools/data/package_modern_sample.py
.\gradlew.bat :core:database:connectedDebugAndroidTest :core:imaging:connectedDebugAndroidTest
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest :core:model:test lintDebug :core:database:verifyDatabaseCoverage
python tools/qa/check_android_state.py --serial emulator-5554 --force-stops 3 --check-media --label independent-followup
```

Dùng `--max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'` nếu máy ít RAM.
Các report ở `*/build/reports/`, `*/build/outputs/androidTest-results/connected/debug/`, và `build/outputs/track-a-device/` đều ignored.
Script không clear storage, không gỡ app và không upload DB/ảnh.

## Tách nền ML Kit: evidence thật và giới hạn

Đã thêm `SegmentationRuntimeProbeTest`: xin tải module, kiểm readiness có giới hạn, rồi chỉ chạy 6 ảnh thật thuộc 6 category khi READY.
Report ghi elapsed time, tình trạng output/original fallback, kích thước và phần pixel foreground; không có mask ground truth nên không gọi đó là accuracy.

Lần chạy API 35 trên AVD hiện tại: **UNAVAILABLE, 0 ảnh được đánh giá**, ca chất lượng được JUnit SKIP rõ ràng.
Google Play services `24.23.35 (190800-646585959)`; Android báo mạng VALIDATED nhưng log module downloader có `Zapp module request failed`
và không tải thành công `mlkit.segmentation.subject`. Chưa xác định nguyên nhân gốc; không suy từ việc có mạng rằng endpoint module hoạt động.
Evidence nằm trong `core/imaging/build/outputs/androidTest-results/connected/debug/.../testlog/test-results.log` và logcat của probe.
Report JSON gọn được chép vào `build/outputs/track-a-independent/segmentation-runtime.json`.

Fallback UI và việc giữ original/cleanup được kiểm độc lập bằng tests; chất lượng model READY và cold-model/offline trên máy thật vẫn chưa nghiệm thu.
Teammate có môi trường tải được module có thể chạy lại cùng probe để tạo report, rồi đối chiếu trực quan cạnh/chi tiết trên 6 ảnh.
Ảnh test ở debug/androidTest assets có manifest nguồn/quyền; không gửi ảnh ra recognition provider hoặc dùng ảnh người.

## Phần còn phải có điều kiện bên ngoài

- Máy thật/D witness: readiness, camera rotation/background/5 ảnh, storage pressure và end-to-end recovery.
- B/D/C: review shared contracts; client recognition thật, ProfileStorage, Today invalidation/selection và VTO.
- Share/sync: local collage/URI và metadata prototype đã có; Today wiring, app nhận thực, policy cloud/consumer review chưa có.
- User research/pilot/localization/monetization: [hồ sơ n=0](../research/track-a-study-plan.md), chưa có người tham gia, chi phí/usage hay validation để suy kết luận.

Danh sách này không chuyển các gate ngoài phạm vi thành DONE. [Board](../execution/TASK_BOARD.md) giữ trạng thái chính thức;
[hướng dẫn v2](../track-a-v2.md) là điểm nhận code và [báo cáo trước](track-a-acceptance.md) giữ lịch sử lần bàn giao đầu.
