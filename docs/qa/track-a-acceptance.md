# Track A v2 — nghiệm thu phần độc lập

> Snapshot nghiệm thu bản đầu `80d33d8`. Xem [bổ sung độc lập sau merge main](track-a-independent-followup.md)
> cho recovery, lifecycle preview, Room native và provenance dataset cập nhật; các con số/hash bên dưới là lịch sử bản đầu.

Ngày chạy: **11/11/2026**, múi giờ Asia/Ho_Chi_Minh. Nhánh `track/a-wardrobe-imaging`, base `origin/main` `7d995a5`, source bàn giao `80d33d8`.
Người dùng xác nhận chưa có điện thoại Android thật hoặc nhánh B/D riêng; làm phần độc lập trước.
Đây là evidence của implementation A trong checkout hiện tại, **không phải 23/23 card DONE hoặc sản phẩm AI end-to-end**.

## Môi trường và build

Windows, JDK 21.0.13, Gradle 8.13, AGP 8.9.1, Kotlin 2.1.20, Room 2.7.1, SDK 35.
Thiết bị chạy: AVD `fitcheck-track-a`, `emulator-5554`, Android 15/API 35, 720×1600, density 280. Không có máy thật.
Không gỡ app/clear storage khi nâng bản. Database/ảnh cũ được giữ để kiểm migration thực tế.

Build đầy đủ hoàn tất **18:54:04 +07:00**, exit 0: debug, release unsigned, unit tests, lint và coverage gate.

| Kiểm tra | Kết quả |
|---|---|
| JVM/Android unit tests toàn repo | 90 discovered: **89 PASS, 1 SKIP Windows**, 0 fail/error |
| Trong đó A: model / database / imaging / wardrobe | 15 / 25 / 14 (1 skip) / 29 |
| Android instrumentation | **1/1 PASS**, `OutfitShareDeviceTest` trên API 35 |
| Database line coverage | **337/373 = 90,35%**, gate ≥80% |
| Lint | 0 errors; warnings: dependency/AGP version, DataExtractionRules, UseKtx; không claim zero-warning |
| Fixture validator | PASS: 21 garments, 24 photos, 3 pairs, 6 categories |
| APK assets | Debug có đúng 24 ảnh thật; release có 0 fixture; cả hai không chứa seed minh họa legacy |

SHA-256 APK debug: `f2444f51f8bc854b2f6941eb215e90b5d00518fac9b11514fab71f37fc409956`.
SHA-256 APK release unsigned: `178109ce813a64dcd42b90bfa088e34dc896abb63f01ed5a978c02d6ad4bd548`.
Artifact ở `app/build/outputs/apk/`; APK không commit, release chưa ký phát hành.

Lệnh kiểm:

```powershell
python -m pip install -r tools/data/requirements.txt
python tools/data/validate_fixtures.py
.\gradlew.bat :core:imaging:connectedDebugAndroidTest assembleDebug assembleRelease testDebugUnitTest :core:model:test lintDebug :core:database:verifyDatabaseCoverage --continue --console=plain --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
python tools/qa/check_android_state.py --serial emulator-5554 --force-stops 3 --check-media --label final
```

Trên Windows, AndroidX FileProvider 1.15 kiểm path root với dấu `/` nhưng JVM host trả `\`; ca Robolectric URI được skip có lý do,
**cùng boundary được chạy trực tiếp bằng `OutfitShareDeviceTest` trên Android**. CI Ubuntu vẫn chạy ca Robolectric.
Không nới FileProvider root hoặc giảm coverage threshold để làm test xanh. Ca thiết bị xác minh content URI đọc được, chỉ cấp quyền đọc,
không cấp URI cho original private, reject PERSON/missing ref, cleanup collage không xóa original; không gửi ảnh cho ai.

## Kết quả chức năng trên emulator

| Case / card liên quan | Thao tác và kết quả thực tế | Outcome |
|---|---|---|
| Migration / A-001 | Nâng từ schema 1 có 30 item, wearCount tổng 9; 30 hàng cũ/counters không đổi, tạo đủ UUID/alias và v2 rows | PASS local |
| Dataset / PRE-004, A-002 | Import chủ động 21 món/24 ảnh/3 cặp; 30 → 51 item; nhập lần hai thêm 0, bỏ qua 21 | PASS local |
| Manual / A-003 | Tạo món QA hai ảnh bằng Photo Picker, cho lưu category UNKNOWN và thuộc tính chưa biết; không tự gọi AI | PASS local |
| Two views / A-002 | Tạo món QA với DDC front/back đúng chiều; ID/view/hash tách biệt, hiển thị đủ hai mặt | PASS local |
| Search / BACK-012 | Gõ nhanh `DDC` vẫn đúng chuỗi, hiện đúng áo DDC. Lỗi đảo ký tự đã sửa bằng state nhập đồng bộ và có regression test | PASS local |
| Edit / BACK-012 | Món QA revision 1 → 2; sửa tên/category TOP/màu WHITE+RED có MANUAL provenance; thay riêng back, front ref giữ nguyên, file back cũ bị dọn | PASS local |
| Cancel delete / BACK-012 | Mở dialog rồi Giữ lại; toàn bộ rows không đổi | PASS local |
| Delete / BACK-012 | Chỉ xóa món QA do phép thử tạo; tombstone revision 3, payload trống, hai ảnh đã lưu bị dọn; item khác và rows v1 không đổi | PASS local |
| Durability / A-004 | Trước sửa: 3 force-stop/reopen giữ 52 item/56 hash. Bản cuối sau xóa: 3 lần nữa giữ 51 item, 1 tombstone, 30 rows legacy và 54 hash ảnh | PASS storage |
| Camera / BACK-002 | Deny/permanent-deny vẫn có picker; cấp quyền lại mở CameraX, chụp một frame AVD vào front draft và discard; không đưa ảnh cảnh giả lập vào dataset | PASS emulator smoke; physical matrix NOT RUN |
| OutfitVisual / A-006 | Component tests chọn đúng card thứ 5, missing/stale không cho chọn; resolver kiểm ID/revision; chưa nối real Today v2 | PASS component; integration NOT RUN |
| Recognition UI / BACK-005 | Unit/fake: consent khớp purpose/provider/config/request/generation/views; stale preview bị chặn; sửa tay thắng response muộn; timeout/cancel/restore không upload | PASS local; AI live NOT RUN |

Bản APK cuối đã cài nâng cấp thành công. Kiểm UI offline với font 1,3×, 360 dp và dark mode thấy đủ 51 item/ảnh, search/filter/add có thể truy cập;
đây là smoke layout, không phải accessibility audit đầy đủ. Đã trả font/density/theme/network về cấu hình ban đầu.
`v2-final/summary.json` báo PASS cho 3 force-stop và toàn bộ 54 hash; mở lại màn Tủ đồ vẫn hiển thị 51 món.
Logcat AndroidRuntime không có exception trong lần kiểm cuối. Kiểm 465 đường dẫn Markdown nội bộ không có đích file bị thiếu.

Evidence cục bộ (ignored, không commit DB/ảnh private): `build/outputs/ui-review/` có migration, dataset import,
`v2-fast-search-fixed.png`, `v2-corrected-manually.png`, `v2-delete-confirmation.png`, `v2-edit-delete-result.json` và DB snapshots trước/sau.
`build/outputs/track-a-device/` giữ kết quả script force-stop/hash. Script chỉ kiểm storage/bytes, không thay thế thao tác UI hay máy thật.

## Dữ liệu và phạm vi chất lượng

[Bộ CC0](../../data/README.md): tải 5.756 file, giữ 5.096 ảnh có nhãn sau audit, chia contributor groups 3.693/605/798.
[Fixtures](../../data/fixtures/modern-clothing/manifest.json): 21 món/24 ảnh thật, 6 category, 3 cặp đã đối chiếu nguồn và hình.
Validator kiểm UUID, file/hash, quyền, size, EXIF và negative cases. Nhãn bulk chưa kiểm tay toàn bộ; fixtures do trợ lý rà soát, chưa B adjudicate.
Không dùng các số này để báo accuracy recognition/VTO; chưa có model live hoặc tập đánh giá mù.
Đồ đời thường hiện đại không đồng nghĩa ảnh thuộc xu hướng năm 2026. Polyvore chưa tải; [đánh giá và điều kiện](../../data/POLYVORE_ASSESSMENT.md).

## Gate còn mở và cách tiếp tục

- **Máy thật:** PRE-002, A-004, CameraX 5 lần/camera background/permission matrix, chất lượng và model readiness ML Kit cần chạy trên thiết bị.
- **D/B/C review:** shared v2 candidate, MediaStore ownership, ProfileStorage, Today invalidation và selected-outfit/VTO chưa được owner nghiệm thu.
  Today/Profile/Insights trong checkout vẫn là v1/stub; nút “Phối đồ cùng món này” đi tới consumer v1, không chứng minh v2 journey đã xong.
- **Recognition thật:** binding mặc định `UnavailableRecognition`; A có form/consent/merge/fallback, client/model/API và evaluation do B cung cấp.
  Chưa có accuracy, latency hoặc chi phí live; không tự dùng fake thay live.
- **Share/sync:** local share API + component callback và metadata roundtrip có tests. Chưa nối Today hoặc thử 2 ứng dụng nhận;
  không có cloud upload/restore, tài khoản hay sync ảnh người. Xem [sync boundary](../track-a-sync-boundary.md).
- **Recovery nâng cao:** chưa kill process tại từng điểm ghi file/registry; orphan chưa lập chỉ mục có thể còn lại. Chưa đo storage pressure/low-memory trên máy thật.
- **Research/pilot:** [hồ sơ n=0](../research/track-a-study-plan.md), chưa liên hệ người tham gia. Cost-model arithmetic dùng số giả định có nhãn;
  không phải báo giá, usage/revenue thật hoặc beta thành công.

[P0 case index](p0-case-index.md) ánh xạ đủ 46 P0 acceptance/owner; gate liên track giữ NOT RUN.
Trạng thái duy nhất ở [Task board](../execution/TASK_BOARD.md); hướng dẫn chạy/tích hợp ở [Track A v2](../track-a-v2.md).
