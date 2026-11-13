# Track A — Hồi quy CORE trên Samsung

Ngày 13/11/2026. `main` được cập nhật đến `199983d158c8fcd4f1887eff02345b08045e643a`. APK kiểm thử chứa bản sửa cục bộ tại `TodayViewModel.prepareTryOn`. Nếu tủ đồ đổi trong lúc app đọc đường dẫn ảnh, app kiểm tra lại revision trước khi chuyển bộ đồ sang Try-On và trả `STALE_INPUT`. Test hồi quy sửa món ngay trong khoảng chờ đọc ảnh và xác nhận không có bộ cũ được chuyển tiếp.

## Kết quả trên máy thật

Thiết bị Samsung SM-F721B, Android 16 (API 36). Cài đè gói riêng `com.fitcheckai.tracka.physicalqa` (FitCheck QA). SHA-256 của APK trên máy khớp file đã build.

| Ca | Kết quả | Bằng chứng |
|---|---|---|
| Cài đè và mở lại | PASS | Trước và sau cài đè có 25 món còn hiệu lực, 1 tombstone và 27 ảnh GARMENT cùng hash. Toàn bộ 26 hàng tủ đồ và JSON hồ sơ khớp. |
| Tủ đồ và nhãn màu | PASS | Mở món `Đầm đen` và thấy màu `Đen`. Đây là ca hiển thị và bảo toàn nhãn đã lưu. |
| Hồ sơ hai góc | PASS | Chọn đúng ảnh trước/sau bằng Android Photo Picker, lưu đúng scope PERSON. Hủy chọn mặt sau giữ tham chiếu mặt trước. |
| Ba lần force-stop | PASS | Cả 27 hash GARMENT và 2 hash PERSON giữ nguyên sau ba lần mở lại. |
| Xóa hồ sơ | PASS | App xóa hai ảnh PERSON và trở lại onboarding khi mở lạnh. Chọn bỏ qua, mở lại Today và kiểm đủ 26 hàng Room cùng 27 hash GARMENT. Bản sao ảnh dùng để nhập thử đã được dọn khỏi máy. |
| Today mất mạng và đổi yêu cầu | PASS | Bật chế độ máy bay, tắt Wi-Fi rồi mở app. Chọn Minimal và số bộ trong tùy chỉnh, áp dụng và nhận bộ có nhãn `Phối cơ bản`. Khôi phục cấu hình mạng sau ca kiểm. |
| Today xoay màn hình | PASS | Xoay ngang rồi dọc vẫn thấy Today và bộ phối cơ bản. Khôi phục thiết lập xoay. |
| Chọn một outfit để thử đồ | PASS trong CORE | Nút `Thử đồ với bộ này` mở màn hình của bộ đã chọn, hiện `Thử đồ ảo chưa được bật trong bản này`, rồi quay về Today. |
| Chia sẻ outfit | PARTIAL | Android Sharesheet mở từ Today, collage là JPEG đọc được, hủy quay lại Today. Kiểm thử Android cho FileProvider URI và dọn cache đạt. Chưa xác minh mở ảnh từ hai ứng dụng nhận theo LATER-003. |

## Kiểm thử tự động

| Nhóm | Kết quả |
|---|---|
| Android build và JVM | Build debug thành công. 221/222 ca PASS, 1 ca FileProvider bỏ qua trên Windows, 0 lỗi. Gồm test sửa món giữa lúc chuẩn bị Try-On. |
| Android instrumentation trên Samsung | 7/7 PASS. Datastore 1, database 3, imaging 3. Gồm migration/rollback của Room, khôi phục media, FileProvider và probe tách nền. |
| ML Kit trên Samsung | Model `READY`, tạo ảnh có foreground 6/6 mẫu trong 551–834 ms/mẫu. Probe đo runtime, chưa chấm độ chính xác vùng tách. |
| Proxy | 162/162 Vitest và typecheck đạt. |
| Fixtures và đóng gói | 2 test chuẩn hóa Kaggle và 6 test đóng gói đạt. Validator xác nhận 21 món, 24 ảnh, 3 cặp, 6 nhóm. |

Các ca CameraX đã có bằng chứng riêng trên Samsung ngày 12/11. [QA tích hợp A–B](track-a-b-handoff.md).

## APK kiểm thử

| APK | SHA-256 | Trạng thái |
|---|---|---|
| `qa-app/outputs/apk/debug/app-debug.apk` | `8359a31c1152a360ed027cde8f31644be2412e27964d6871b90ede4abdfa0f86` | FitCheck QA đang cài trên Samsung. |
| `artifacts/FitCheckAI-0.1.0-199983d-CORE.apk` | `bfeaf10dfcb948e3b11cc43f8fa2203055081c05b1f7b94db96b504c565ed269` | APK debug đóng gói cục bộ, chữ ký kiểm tra đạt. Manifest ghi `dirtyTree: true`, `providerKeyScan: clean`, `demoAccessCodeCompiledIn: false`. |

Đường dẫn trong bảng tính từ `build/outputs/track-a-core-release/`. Log build, Android instrumentation và log từng ca UI nằm cùng thư mục. Báo cáo độ bền dữ liệu nằm ở `build/outputs/track-a-device/tracka-core-phone-restarts/summary.json`. Bản nộp chính thức vẫn thuộc INT-004.

```powershell
.\gradlew.bat :app:assembleDebug :core:model:test :core:ai:testDebugUnitTest :core:database:testDebugUnitTest :core:imaging:testDebugUnitTest :core:datastore:testDebugUnitTest :feature:wardrobe:testDebugUnitTest :feature:today:testDebugUnitTest :feature:onboarding:testDebugUnitTest :feature:tryon:testDebugUnitTest
.\gradlew.bat :core:datastore:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest :core:imaging:connectedDebugAndroidTest
python tools/data/validate_fixtures.py
python -m unittest discover -s tools/release
```

## Việc còn lại

| Track | Việc và điều kiện hoàn thành |
|---|---|
| A — Tủ đồ và ảnh | `INT-003` có bằng chứng hồi quy CORE cục bộ, chờ `INT-002` và xác nhận mode của D để chốt. `LATER-003` cần thử hai ứng dụng nhận. Nghiên cứu `PRE-005` và pilot `BACK-009/010` chưa có người tham gia. |
| B — AI | `B-001` triển khai proxy HTTPS với provider, ngân sách và mã demo đã chốt. `INT-002` kiểm nhận diện và phối đồ bằng mô hình thật trên APK Android, ghi request ID và kiểm giới hạn thời gian. Hoàn tất đánh giá `B-004`/`B-006`. Thời tiết tự động (`C-101`) và bảo mật/quota trước public beta (`BACK-011`) còn mở. |
| C — Thử đồ ảo | `C-202`–`C-205` cần dữ liệu thử đủ consent, provider/ngân sách và điểm chất lượng để quyết định FRONT/DUAL. `C-207` cần nối route proxy và lưu job bền vững. `C-210` cần kiểm xóa/thu hồi consent và kết quả đến muộn bằng store thật. `C-212` và `C-211` (nếu DUAL) kiểm runtime trước khi bật. |
| D — App và bản nộp | `D-005` và `C-004` đã có thêm bằng chứng trên Samsung. `D-007` cần nghiệm thu CORE với AI live và kiểm xóa/thu hồi bằng late-result fixture cùng C. `INT-004` chốt APK có checksum và smoke test trên chính APK đó. `D-003` diễn tập và bàn giao. |

CORE vẫn dùng phối cơ bản khi proxy chưa cấu hình. AI live là app gọi mô hình qua proxy HTTPS cho nhận diện và phối đồ. ML Kit tách nền tại máy đã chạy. Điểm tự chấm vẫn **51/100** theo cùng rubric. [Bảng việc](../execution/TASK_BOARD.md) · [Điểm tự chấm](../../Scorings.MD).
