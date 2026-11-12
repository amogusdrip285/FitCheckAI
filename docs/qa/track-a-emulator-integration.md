# Track A trên Android emulator

Ngày 12/11/2026, bản debug được kiểm trên `emulator-5554` (API 35). Luồng UI dùng gói QA riêng `com.fitcheckai.tracka.qa` với dữ liệu thử.

| Kiểm thử | Kết quả |
|---|---|
| Unit test | 80/80 ca đạt trong AI, Tủ đồ, Today, ProfileStorage và Onboarding. |
| INT-001 native | 1/1 ca đạt với Android SQLite, FileProfileStorage và LocalMediaStore. Dùng cùng owner ID cho GARMENT/PERSON để kiểm scope. Mở lại store ba lần giữ nguyên refs và bytes. Xóa ảnh sau rồi xóa hồ sơ chỉ dọn ảnh PERSON. |
| Onboarding và bộ ảnh | Bỏ qua hồ sơ vẫn dùng Tủ đồ và nhập được 21 món, 24 ảnh. APK debug chứa nhãn `BLACK` cho đầm `a06aab12` và chi tiết món trên emulator hiển thị màu **Đen**. |
| Today | Sửa tên một món trong gợi ý làm ẩn bộ phối cũ. Sau **Thử lại**, gợi ý hiển thị tên mới. Hủy xóa giữ món thử, xác nhận xóa làm ẩn bộ phối chứa món đó. |
| A-004 emulator | Ba lần force-stop và khởi động lại giữ nguyên 20 món, 1 tombstone và hồ sơ. 23 hash ảnh garment và 2 hash ảnh hồ sơ khớp metadata. |
| Xóa hồ sơ trên UI | Xác nhận xóa dọn hai tệp PERSON và metadata hồ sơ. Khởi động lại vào onboarding. So sánh Room giữ nguyên toàn bộ 20 món, 1 tombstone, revision và 23 hash ảnh garment. |
| Recognition | Sáu ca Ktor MockEngine kiểm upload hai mặt, consent, thay đổi capability, FRONT_ONLY, giới hạn byte, revision và phiên hết hiệu lực. App với URL mặc định hiển thị đường nhập tay. |

Evidence trên máy nằm ở `build/outputs/track-a-integration/` và hai summary `build/outputs/track-a-device/int001-qa-restarts/summary.json`, `build/outputs/track-a-device/int001-qa-profile-cleared/summary.json`.
Snapshot Room của QA nằm trong thư mục build. Script chỉ ghi kết quả tổng hợp cho dữ liệu hồ sơ.

| APK | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `c139477e98100069dc69be499d353f06d70a652f2c2a92f8b5e5ea3e2a78b79c` |
| APK QA cài trên emulator | `a836dbb1750a2693fefaf707cde9cd8f454e02101f0fb1803733cac453d330eb` |

## Chạy lại

```powershell
python tools/data/validate_fixtures.py
node eval/ai/tools/labels-from-track-a.mjs --fixtures data/fixtures/modern-clothing
.\gradlew.bat :app:assembleDebug :core:ai:testDebugUnitTest :feature:wardrobe:testDebugUnitTest :feature:today:testDebugUnitTest :core:datastore:testDebugUnitTest :feature:onboarding:testDebugUnitTest
.\gradlew.bat :core:datastore:connectedDebugAndroidTest
python tools/qa/check_android_state.py --serial emulator-5554 --force-stops 3 --check-media --check-profile --label int001-new-run
```

Trước lệnh cuối, mở bản debug, nhập bộ ảnh vào Tủ đồ rồi chọn hai ảnh thử khác nhau trong Hồ sơ. Chạy khi app đang idle. Thêm `--package` nếu dùng gói QA riêng.
Sáu ca HTTP dùng proxy giả lập. Gate live INT-002 cần proxy HTTPS đang bật recognition và mã demo. Today dùng stub phối đồ hiện tại. Gate máy Android thật và xác nhận của D vẫn theo kế hoạch. [Hướng dẫn chạy](../track-a-v2.md) · [Task board](../execution/TASK_BOARD.md).
