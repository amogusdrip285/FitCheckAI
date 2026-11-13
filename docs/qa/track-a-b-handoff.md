# Track A sau bàn giao Track B

Ngày 12/11/2026, tích hợp từ `main` tại `3332c65` và kiểm bản **FitCheck QA** trên Samsung SM-F721B, Android 16 API 36. Gói QA là `com.fitcheckai.tracka.physicalqa`. SHA-256 APK QA là `f69b426a60ff222f59e5f6f1f0e969b34a61dbcb4d8ea2410fbff62702dbccb7`.

| Phần kiểm | Kết quả |
|---|---|
| Build và JVM | Debug app, ba APK instrumentation và `lintDebug` thành công. 194 ca JVM đạt, 1 ca bỏ qua, 0 lỗi. |
| Proxy B | 125/125 ca Vitest đạt và TypeScript typecheck thành công. |
| Android thật | 7/7 ca instrumentation đạt, gồm Profile/Room, MediaStore, chia sẻ URI và probe ML Kit. |
| CameraX | Từ chối quyền vẫn mở được trình chọn ảnh. Preview phục hồi sau xoay màn hình và sau khi đưa app xuống nền. Camera được nhả khi app ở nền. Chụp và lưu 5/5 ảnh trên Samsung bằng APK QA của lần kiểm trước. |
| Độ bền | Ba lần force-stop sau khi chụp giữ 25 món, 1 tombstone và 27 hash GARMENT. Sau khi cập nhật bản Today mới và kiểm UI, mở lại app vẫn đối chiếu đủ 27 hash. Đã trả quyền camera QA và cấu hình xoay màn hình về trạng thái trước bài thử. |
| Today CT-02 | Bản cập nhật hiển thị **Phối cơ bản** khi proxy chưa cấu hình. Phối lại đổi tập món. Phối quanh một món giữ đúng món đã chọn. Sửa món làm ẩn bộ cũ, **Thử lại** đọc revision mới. Test JVM dùng fake B và kiểm chặn mặc/chia sẻ với revision cũ. |
| Chia sẻ | Today tạo JPEG từ ảnh mặt trước của bộ được chọn. Android Sharesheet mở, ảnh ghép đọc được và hủy quay lại Today. Ca FileProvider URI đạt trong instrumentation. |
| Fixtures | Validator đạt với 21 món, 24 ảnh, 3 cặp. Script dựng nhãn eval tạo 4 case cặp và dùng các nhãn đã chốt trong manifest. |

Ca JVM bị bỏ qua kiểm FileProvider trên Windows. Ca URI tương ứng đã đạt trên Android thật. Lint ghi một cảnh báo `DataExtractionRules` ở manifest app.

## ML Kit trên Samsung

Model báo `READY` và trả PNG cho 6/6 mẫu, mỗi nhóm một ảnh. Probe kiểm ảnh gốc còn nguyên bytes sau khi chạy. Sau khi force-stop và mở lại FitCheck QA, thao tác **Tách nền** trên quần jeans hiển thị ảnh xem trước trực tiếp trong app. Thời gian dưới đây đo riêng bước xử lý trên một điện thoại.

| Nhóm | Thời gian | Rà trực quan |
|---|---:|---|
| Áo khoác kẻ đỏ đen | 1.154 ms | Cần kiểm tra. Còn vật thể phía dưới gấu áo. |
| Áo sơ mi trắng | 703 ms | Cần kiểm tra. Còn móc áo và viền nền sát vai. |
| Quần jeans | 770 ms | Dùng được để xem trước. Giữ hai ống và khoảng trống giữa hai chân. |
| Đầm hoa | 755 ms | Dùng được để xem trước. Giữ dáng và họa tiết, còn viền mỏng ở gấu. |
| Giày | 728 ms | Dùng được để xem trước. Giữ đủ đôi, có viền nhỏ quanh đế. |
| Mũ | 741 ms | Dùng được để xem trước. Giữ được đường bao. |

Rà bằng mắt ở kích thước xem trước. **4/6** dùng được và **2/6** cần người dùng xem lại. Bộ mẫu hiện chưa có mask chuẩn để tính độ chính xác pixel. Luồng tách nền giữ ảnh gốc để người dùng tiếp tục chỉnh và lưu.

## Điểm và phần chờ tích hợp

- Track A đã chấm 17 bộ ở 6 case của B-004. Tất cả đạt từ 3/5 ở cả ngữ cảnh và giải thích theo cột A. Gate chung cần điểm reviewer B và chạy trong deadline demo. [Bảng điểm reviewer A](../../eval/ai/results/2026-11-12T12-48-27-674-recommendation.md#kết-quả-rà-soát-của-track-a).
- Nhóm xác nhận proxy chưa deploy. INT-002 cần proxy HTTPS đang bật hai dịch vụ và mã demo. Phần nhận diện B đã đo trên proxy local theo deadline riêng. [Eval B](../../eval/ai/results/2026-11-12-luna-summary.md) · [Tự chấm demo](../../Scorings.MD).
- Kiểm chia sẻ qua hai ứng dụng nhận và sign-off release của D vẫn theo checklist. Phỏng vấn và pilot hiện có n=0. [Task Board](../execution/TASK_BOARD.md) · [Kế hoạch pilot](../research/track-a-study-plan.md).

Log, JSON và ảnh đối chiếu nằm trong `build/outputs/track-a-b-handoff/`. Snapshot độ bền của năm ảnh chụp nằm trong `build/outputs/track-a-device/tracka-camera-api36-restarts/`.

## Chạy lại

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:model:test :core:ai:testDebugUnitTest :core:database:testDebugUnitTest :core:imaging:testDebugUnitTest :core:datastore:testDebugUnitTest :feature:wardrobe:testDebugUnitTest :feature:today:testDebugUnitTest :feature:onboarding:testDebugUnitTest
.\gradlew.bat :core:datastore:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest :core:imaging:connectedDebugAndroidTest
```

Chạy `npm ci`, `npm test` và `npm run typecheck` trong `proxy/`. Lệnh đối chiếu Room và hash ảnh sau force-stop ở [QA máy thật](track-a-physical-device.md#chạy-lại).
