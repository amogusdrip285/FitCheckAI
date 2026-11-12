# Track A trên điện thoại Android thật

Ngày 12/11/2026, kiểm commit `2afa010` trên Samsung SM-F721B chạy Android 16 (API 36, arm64). Bản **FitCheck QA** dùng gói riêng `com.fitcheckai.tracka.physicalqa` và dữ liệu thử. SHA-256 của APK cài trên điện thoại khớp file build `b355e59d56780a4b1f2117ca955dab7f003ad00a7e70938bd0fab28e4224132a`.

| Ca kiểm | Kết quả |
|---|---|
| Cài và mở app | Bản QA khởi động từ APK debug. Bỏ qua hồ sơ vẫn vào Tủ đồ và nhập đủ 21 món, gồm 3 món có cặp ảnh trước/sau. |
| Nhãn tham chiếu | Chi tiết **Đầm đen** hiển thị màu **Đen** trên điện thoại. |
| Android instrumentation | 6 ca đạt. Gồm Profile/MediaStore, migration Room, rollback và cập nhật đồng thời, khôi phục file và chia sẻ qua URI. |
| Hồ sơ và trình chọn ảnh Android 16 | Chọn ảnh trước rồi hủy chọn ảnh sau giữ đúng ảnh trước. Chọn lại và lưu được hai ảnh thử riêng trong scope PERSON. |
| Sửa/xóa món và Today | Sửa tên món có hai mặt làm ẩn bộ phối cũ. **Thử lại** hiển thị tên mới. Hủy xóa giữ món, xác nhận xóa làm ẩn bộ phối chứa món đó. |
| Nhập ảnh trùng | Chọn cùng ảnh cho hai mặt bị từ chối. Bỏ bản nháp quay về Tủ đồ. |
| A-004 sau 3 lần force-stop | Sau khi dọn hai ảnh nguồn thử khỏi thư viện, khởi động lại ba lần giữ 20 món, 1 tombstone và hồ sơ. 22 hash ảnh GARMENT và 2 hash ảnh PERSON khớp metadata. |
| INT-001 xóa hồ sơ | Hai tệp PERSON và metadata hồ sơ được dọn. Mở lại app vào onboarding. Toàn bộ ID, revision, dữ liệu 20 món, tombstone và 22 hash ảnh GARMENT khớp với trước khi xóa. |

Ca tách nền ML Kit báo `UNAVAILABLE` và được bỏ qua. Today dùng gợi ý thử để kiểm cập nhật sau thay đổi tủ đồ. Kiểm nhận diện live cần proxy HTTPS và mã demo của nhóm. [Hướng dẫn chạy Track A](../track-a-v2.md).

## Chạy lại

```powershell
.\gradlew.bat :core:datastore:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest :core:imaging:connectedDebugAndroidTest
$deviceSerial = "<serial từ adb devices>"
$debugPackage = "com.fitcheckai"
python tools/qa/check_android_state.py --serial $deviceSerial --package $debugPackage --force-stops 3 --check-media --check-profile --label tracka-device-new-run
```

Nhập bộ ảnh thử và hai ảnh hồ sơ thử trước khi chạy script. Log và ảnh QA lưu trong `build/outputs/track-a-physical/`. Snapshot và kết quả đối chiếu nằm trong `build/outputs/track-a-device/`.
