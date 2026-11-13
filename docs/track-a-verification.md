# Track A v1 — báo cáo lịch sử

> Báo cáo này giữ nguyên evidence **v1 trước migration**, không mô tả bản đang chạy. Bản hiện tại dùng schema 2, ảnh thật và luồng trước/sau:
> [hướng dẫn v2](track-a-v2.md), [kết quả QA hiện tại](qa/track-a-acceptance.md), [board](execution/TASK_BOARD.md).
> Các nhận xét “chưa có v2”, schema 1 và seed minh họa dưới đây chỉ đúng tại snapshot `7aba62f` trước triển khai.

## Đối chiếu với kế hoạch v2

- V2 bổ sung ảnh front/back, `MediaRef` dùng chung, revision/tombstone/snapshot, provenance cho thuộc tính nhận diện và component `OutfitVisual`. Các contract này chưa có trong implementation v1 hiện tại.
- Fixture v2 cần ít nhất 6 món, trong đó 3 cặp ảnh trước/sau đúng món và manifest quyền dùng/nhãn chuẩn. 18 hình minh họa debug hiện tại chỉ phục vụ test giao diện, chưa đạt PRE-004.
- V2 chuyển C sang Virtual Try-On, D sang foundation/Profile/Today; wear tracking và Insights được hoãn. Không tự xóa tính năng hoặc dữ liệu hiện có khi đồng bộ tài liệu.
- Đề xuất một `:app`/manual DI khác với source hiện có dùng nhiều module/Hilt. `REVIEW_AND_DECISIONS.md` còn mô tả checkout `26049f5` chưa có Android source; cần đối chiếu lại baseline, migration và ownership trước khi áp dụng thay đổi kiến trúc.

## Phạm vi bàn giao

- `core:database`: Room repository thật, CRUD, Flow, cập nhật lượt mặc nguyên tử, bảo toàn lượt mặc khi sửa từ bản chụp cũ; xóa cả ảnh thuộc quyền quản lý của app.
- `core:imaging`: CameraX, import ảnh có EXIF, decode lấy mẫu, ảnh PNG tối đa 768px trong bộ nhớ trong, ML Kit Subject Segmentation và tải trước model.
- `feature:wardrobe`: lưới, lọc kết hợp loại/màu/mùa, chụp/chọn ảnh, preview và giữ ảnh gốc, form đủ thuộc tính, nhập tay khi lỗi, khôi phục bản nháp, chi tiết/sửa/xóa, event `item_added` và `tag_corrected`.
- Chỉ thêm quyền `CAMERA`; Photo Picker dùng `PickVisualMedia`, tự fallback trên Android cũ và không cần quyền đọc bộ nhớ.

## Lưu trữ và tích hợp

- Ảnh nháp: `filesDir/wardrobe/drafts/`; ảnh đã lưu: `filesDir/wardrobe/{id}.png`. Không phụ thuộc cache hoặc quyền URI của picker sau khi import.
- Repo copy ảnh trước khi ghi Room và xóa bản copy khi ghi thất bại. Xóa ảnh chỉ được phép với file PNG con trực tiếp của thư mục wardrobe.
- Thay đổi nhãn dùng câu UPDATE riêng; không ghi đè `wearCount`, `lastWornAt`, thời gian tạo hoặc hash khi một màn chi tiết đang dùng dữ liệu cũ.
- Schema Room vẫn là **v1**, không đổi cấu trúc bảng/cột, không destructive migration. `ListCodec` ghi JSON có tiền tố và vẫn đọc được dữ liệu Unit Separator cũ. Converter nullable list/set/Instant được đăng ký cho Room.
- `ImageStore.prepareUpload()` tạo JPEG nền trắng ≤768px, quality 85 và SHA-256 của đúng byte được gửi. `DefaultPhotoProcessor` đưa đường dẫn JPEG này cho `ClothingTagger.tag()` rồi dọn file sau khi hoàn thành. API của `core:model` không đổi.
- `ImageSegmenter.warmUp()` được onboarding gọi sẵn. Manifest yêu cầu tải model lúc cài đặt; sideload có thể cần mở app có mạng lần đầu. Khi model có trên thiết bị, xử lý tách nền thực hiện tại thiết bị. Không có model/mạng/Play services vẫn lưu thủ công được.
- Mỗi bước tách nền và gắn nhãn giới hạn 8 giây; có nút tự điền ngay trong khi chờ tagger. Thời gian người dùng thêm đồ được ghi bằng `duration_ms`, không suy diễn rằng mọi lần thêm đều dưới 20 giây.
- Seed debug là 18 hình minh họa gốc có trong `core/database/src/debug/assets/seed/`. Có thể tái tạo bằng `python core/database/tools/generate_seed_assets.py` (Pillow). Không có ảnh seed trong release, không tự thêm lại seed sau khi người dùng xóa.

## Chạy kiểm tra

Yêu cầu JDK 17/21, Android SDK platform 35 + build-tools 35. `local.properties` trỏ `sdk.dir` vào SDK cục bộ.

```bash
./gradlew assembleDebug assembleRelease testDebugUnitTest :core:model:test lintDebug :core:database:verifyDatabaseCoverage
```

Trên máy ít RAM, thêm `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process`.

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Coverage: `core/database/build/reports/jacoco/coverageReport/html/index.html`.
- Test report: `<module>/build/reports/tests/testDebugUnitTest/index.html`.
- Ảnh kiểm tra trên trình giả lập (máy phát triển): `build/outputs/device-check/`.
- CI chạy cùng các kiểm tra trên và chặn khi line coverage database dưới 80%. Chỉ loại code sinh tự động, DI và boilerplate entity khỏi mẫu đo.

Các test kiểm tra: lưu 10 món và reopen database trên đĩa, CRUD/Flow của bốn DAO, lượt mặc đồng thời, chỉnh sửa dữ liệu cũ, thứ tự thời gian, ảnh thiếu, lỗi ghi, bảo vệ file ngoài thư mục, seed chỉ chạy một lần, codec tương thích cũ, resize/EXIF/alpha/JPEG/hash, timeout/offline/nhập tay, bấm lưu hai lần, khôi phục bản nháp, hủy tác vụ, lọc kết hợp và thao tác Compose.

## Kiểm thử chấp nhận trên điện thoại thật

1. Cài APK, hoàn thành onboarding khi có mạng để model được tải.
2. Thêm liên tiếp 10 món bằng cả CameraX và Photo Picker. Kiểm tra ảnh và sửa các trường trước khi lưu.
3. Force-stop app rồi mở lại, xác nhận còn đủ món và ảnh. Onboarding của Track D hiện vẫn là stub nên có thể xuất hiện lại; dữ liệu tủ đồ vẫn nằm trong Room.
4. Bật chế độ máy bay, thêm món. Khi model đã tải, kiểm tra tách nền; nếu chưa có model, kiểm tra thông báo và lưu ảnh gốc/nhập tay.
5. Từ chối quyền camera, vẫn chọn ảnh được. Bật lại quyền và thử CameraX.
6. Thử ảnh 12–48MP, ảnh xoay EXIF, ảnh nền rối, ảnh không có món đồ; kiểm tra preview, giữ ảnh gốc và không crash.
7. Sửa món, thử ba bộ lọc, xóa một món, xác nhận ảnh tương ứng bị xóa.
8. Khi Track B thay stub bằng tagger thật, kiểm tra nhãn/thời gian thêm đồ với proxy thật. Đây là kiểm thử tích hợp phụ thuộc Track B.

## Kết quả thực thi ngày 11/11/2026

Môi trường: Windows, JDK 21.0.13, Gradle 8.13, Android SDK 35; trình giả lập Android 15/API 35, Google APIs x86_64, 720×1600. Không có điện thoại thật kết nối trong lần kiểm thử này.

| Kiểm tra | Kết quả |
|---|---|
| `assembleDebug`, `assembleRelease` | Thành công; release chưa ký |
| Unit/Robolectric/Compose, gồm `core:model:test` | **56 test, 0 lỗi** |
| `lintDebug` | Thành công, **0 error/fatal** |
| Line coverage database | **92,19%** — 177/192 dòng được đo, vượt ngưỡng 80% |
| Seed trong APK | Debug: 18 ảnh; release: 0 ảnh |
| Quyền APK | Chỉ `CAMERA` là quyền runtime; không có quyền storage/media/location. `INTERNET`, `ACCESS_NETWORK_STATE` và quyền receiver nội bộ do thư viện dùng không yêu cầu hộp thoại cấp quyền |
| CameraX | Mở preview, chụp, xử lý và hủy bản nháp thành công trên camera giả lập |
| Từ chối quyền camera | Photo Picker vẫn chọn và lưu ảnh được |
| Thêm 10 món liên tiếp | 20 → 30 bản ghi, không crash; 8,06–8,61 giây/lần trong kịch bản thử |
| Force-stop và mở lại | 30/30 bản ghi và ảnh còn nguyên; so sánh ID, thuộc tính, hash, lượt mặc và ngày mặc; giải mã được toàn bộ ảnh, cạnh dài ≤768px |
| Tắt Wi-Fi và dữ liệu di động | `Active default network: none`; thêm và lưu được món thứ 31 bằng ảnh gốc |
| Sửa/xóa | Đổi tên được lưu vào SQLite; lượt mặc không đổi; xác nhận xóa loại bỏ cả bản ghi và file ảnh, còn 30 món |
| Bộ lọc kết hợp | Áo + trắng `#FFFFFF` + hè trả về 13 món, khớp SQLite; màu cuối danh sách cuộn/chọn được; xóa lọc trở lại 30 món |

Thời gian 8,06–8,61 giây ở trên đo từ khi chọn ảnh đến khi trở về tủ đồ, gồm thao tác cuộn/lưu của script. **Tagger đang là stub của Track B**, ML Kit trên trình giả lập đi vào nhánh giữ ảnh gốc. Đây không phải số đo hiệu năng AI thật, cũng chưa xác nhận chất lượng tách nền hoặc tách nền offline khi đã tải model. Các test tự động đã kiểm tra nhánh lỗi/timeout và form nhập tay.

Bằng chứng tại `build/outputs/device-check/` (không commit dữ liệu chạy thử):

- `build-verification.json`: số test, coverage, lỗi lint, SHA-256 và số seed trong từng APK.
- `series.json`, `persistence.json`: 10 lần thêm và kiểm tra dữ liệu sau force-stop.
- `offline.json`, `offline-network.txt`: thêm đồ khi thiết bị không có mạng.
- `edit-delete.json`: sửa tên và xóa cả ảnh.
- `filters.json`, `filters-final.png`: lọc kết hợp đối chiếu với dữ liệu SQLite.
- `camera-final.png`, `camera-captured-final.png`, `wardrobe-after-restart.png`: ảnh chụp màn hình thật từ trình giả lập.

Sau khi sửa chiều cao menu màu theo lỗi phát hiện trên trình giả lập, đã build lại cả hai APK, chạy lại toàn bộ test của `feature:wardrobe` và lint module này; tất cả đều thành công. Các module khác không thay đổi sau lượt kiểm tra đầy đủ.

Phần code Track A đã triển khai; nghiệm thu **trên điện thoại thật**, chất lượng/tốc độ ML Kit và tích hợp tagger thật của Track B vẫn cần thực hiện theo checklist phía trên. Không coi test Robolectric hay trình giả lập là bằng chứng đã chạy trên điện thoại thật.

## Làm mới giao diện — 11/11/2026

- Theme riêng cho wardrobe với nền kem, màu than/đất nung, hỗ trợ chế độ tối; thẻ ảnh lớn, lưới hai cột ở chiều rộng 360dp trở lên.
- Tìm kiếm tên/loại/chất liệu không phân biệt dấu tiếng Việt; bộ lọc màu/mùa trong bottom sheet, chip thể hiện bộ lọc đang áp dụng. Không đưa mã họa tiết/phong cách nội bộ vào tìm kiếm, tránh trường hợp `so mi` khớp nhầm `solid` + `minimal`.
- Màn chọn ảnh có hướng dẫn và hai hành động rõ ràng; form nhóm thông tin, chọn màu trực quan, chi tiết phụ thu gọn; nút lưu cố định phía dưới. Màn chi tiết có sửa, phối đồ và xác nhận xóa.
- `FitCheckApp` consume phần inset đã dùng cho NavHost để màn nhập liệu không cộng lặp khoảng trống khi mở bàn phím. Không đổi hợp đồng dữ liệu v1 hoặc navigation routes.

Kiểm tra bản giao diện cuối: `assembleDebug`, `assembleRelease`, `:feature:wardrobe:testDebugUnitTest`, `:feature:wardrobe:lintDebug`, `:app:lintDebug` đều thành công; **17 test, 0 lỗi, lint 0 error/fatal**. Release chưa ký. Không coi đây là lượt chạy lại toàn bộ 56 test của bản v1 phía trên.

Kiểm tra trực tiếp trên giả lập API 35:

- Thêm một món thử, sửa tên, hủy xóa, xác nhận xóa cả bản ghi/ảnh; so sánh database xác nhận 30 món có sẵn không thay đổi.
- Tìm `so mi` trả đúng 2 áo sơ mi; lọc Áo + trắng `#FFFFFF` + Hè trả 13 món, khớp database; xóa lọc trở lại 30 món.
- Kiểm tra bàn phím không che nút lưu hoặc tạo khoảng trống dư, chế độ tối, cỡ chữ 130% và viewport rộng 360dp. Đã khôi phục cài đặt hiển thị của giả lập sau khi kiểm tra.
- Đã cài APK debug mới trên giả lập để tiếp tục dùng thử. Tagger vẫn là stub, ảnh kiểm thử vẫn là hình minh họa; chưa có bằng chứng chất lượng nhận diện/tách nền trên ảnh thật.

Bằng chứng local tại `build/outputs/ui-review/`: `crud.json`, `filters.json`, `search.json`, `build-verification.json` và ảnh chụp các màn hình. Các file này không được commit.
