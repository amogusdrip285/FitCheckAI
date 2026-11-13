# Track A v2 — chạy độc lập và bàn giao

Cập nhật ngày 13/11/2026. Luồng Hồ sơ, Tủ đồ, Today và độ bền dữ liệu đã kiểm trên emulator API 35 và Samsung SM-F721B API 36. Samsung đã chạy lại bản CORE sau khi nối Try-On. Nhận diện và phối đồ live còn chờ proxy HTTPS. [Hồi quy CORE trên Samsung](qa/track-a-core-regression.md).

## Chạy trên máy tính

1. Cài JDK 21 và Android SDK 35; tạo Android emulator API 28+ (đang kiểm với API 35). `local.properties` chỉ chứa đường dẫn SDK máy mình, không commit.
2. Chạy `gradlew.bat :app:assembleDebug` trên Windows hoặc `./gradlew :app:assembleDebug` trên macOS/Linux.
3. Mở emulator, chạy `gradlew.bat :app:installDebug`, mở FitCheck AI. Qua onboarding hiện tại rồi chọn **Tủ đồ**.
4. Chọn **Ảnh kiểm thử** → **Thêm bộ ảnh vào tủ đồ**. Lần đầu thêm 21 món/24 ảnh; nhập lại bỏ qua món đã có, không ghi đè tủ hiện hữu.
5. Thử tìm `áo`, `jeans`, lọc category/color, mở món có 2 mặt, sửa tên/nhãn, thay riêng ảnh sau và lưu. Tắt app/mở lại kiểm dữ liệu.
6. Thử món mới chỉ có ảnh trước. Có thể lưu với category “Chưa xác định”; không tự điền chất liệu/mùa/phong cách.
7. Xóa chỉ món thử của mình; hủy dialog phải giữ dữ liệu. Nhãn fixture cần được kiểm lại trước khi dùng làm dữ liệu cá nhân.

APK debug nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Release không chứa bộ fixtures. Để kiểm migration với dữ liệu đã lưu, cài đè bằng `adb install -r`.

## Kiến trúc đã áp dụng

Checkout có sẵn Gradle multi-module/Hilt. Giữ kiến trúc này để có thể build/test với code B/C/D v1 hiện hữu.
Namespace `com.fitcheckai.core.model.wardrobe` chứa **đề xuất implementation contract chung v2**, để D/B/C review theo
[alignment với checkout](execution/SPRINT0_TO_V2_ALIGNMENT.md). Không tạo thêm một contract v2 cạnh tranh trong feature A.
Các type v1 chỉ giữ để consumer cũ compile; không ghi S-002/PRE-007 của D là DONE hộ D.

| Boundary | Implementation / consumer |
|---|---|
| `WardrobeRepository` v2 | `RoomWardrobeRepository`; `WardrobeListViewModel`, `WardrobeEditorViewModel` |
| `MediaStore` | `LocalMediaStore`, một Hilt singleton; GARMENT/PERSON/TRYON_RESULT cùng API |
| `ClothingRecognition` | `ProxyRecognitionClient` trong `core:ai`; editor chỉ bật gửi ảnh khi proxy báo recognition khả dụng |
| `ProfileStorage` | `FileProfileStorage` dùng chung `MediaStore` với Tủ đồ; ảnh PERSON lưu ở `noBackupFilesDir` |
| v1 wardrobe | `LegacyWardrobeBridge` chỉ đọc v2, giữ counters v1 đã có; không có write v1 cạnh tranh |
| Reusable visual | `OutfitVisual`, `ItemThumbnail`, `OutfitVisualList`, `resolveOutfitItems` ở `core:ui`; Today truyền DTO và callback |
| Phối đồ | Today dùng `OutfitRecommendation` CT-02 của B, hoặc `BasicOutfitFallback` khi dịch vụ chưa cấu hình hay gặp lỗi có thể dùng offline |
| Fixtures | `core:model/src/testFixtures`; ảnh thật chỉ ở debug assets, import chủ động |
| Chia sẻ | Nút **Chia sẻ ảnh mặt trước** ở Today tạo collage GARMENT qua `OutfitShare`, rồi mở Android Sharesheet |
| Sync prototype | `GarmentTransfer`/mapping metadata, [quy tắc conflict và privacy](track-a-sync-boundary.md); chưa upload/restore cloud |

v1 không biểu diễn UNKNOWN/nullable formality: bridge bỏ item chưa có category, dùng default formality 2 chỉ cho consumer v1,
không ghi default đó vào v2 hay coi là annotation. Wear tracking nằm ngoài scope v2; bridge từ chối write/markWorn bằng lỗi rõ ràng.
Today gửi ID, revision và thuộc tính các món đủ loại, tối đa 50 món, cùng phần thông tin hồ sơ cho phép theo CT-04. Thông tin gửi gồm sở thích, loại đồ ưa dùng và chiều cao nếu có. Khi phối quanh một món, request giữ ID món đó và hiển thị lỗi nếu món đã xóa hoặc chưa có loại.

## Dữ liệu và độ bền

Room schema 2 thêm `wardrobe_v2`, `wardrobe_cleanup`, `wardrobe_legacy_ids`; giữ các bảng v1 để bảo toàn dữ liệu cũ.
Migration giữ UUID đã hợp lệ; ID seed cũ được map sang UUID xác định, có bảng alias. Giữ createdAt, bản v2 đầu revision 1.
Các nhãn cũ được đánh dấu cần review vì nguồn v1 có thể là stub. Thiếu file ảnh không reset DB: giữ item để thay ảnh.
Không dùng destructive migration; không tự seed ảnh minh họa khi tạo DB nữa.

Mỗi lần sửa cần `expectedRevision`; stale write trả CONFLICT, item đã xóa trả STALE_INPUT. Update giữ ID/createdAt và tăng revision.
Delete ghi tombstone, loại khỏi snapshot/flow ngay, xóa payload rồi cleanup ảnh sau transaction. Cleanup có journal và retry khi đọc lại.
Snapshot ID được tạo từ danh sách ID/revision; `isCurrent` và `resolveOutfitItems` giúp consumer chặn chọn outfit stale/deleted.
Consumer D/C phải kiểm snapshot lại trước action/upload; events chỉ giúp làm mới UI, không thay thế kiểm revision ở boundary.
Today chụp revision trước khi xin gợi ý và kiểm lại khi kết quả về. Sửa hoặc xóa món làm ẩn các bộ chứa revision cũ, kể cả khi Today đang ở tab nền.
Khi chọn thử đồ, Today kiểm lại revision sau khi đọc đường dẫn ảnh. Nếu bộ đồ đã cũ, app trả `STALE_INPUT` trước khi chuyển bộ sang Try-On.
**Thử lại** tạo yêu cầu từ tủ hiện tại. **Phối lại** với cùng ngữ cảnh loại các bộ vừa hiển thị, rồi cho biết nếu đã hết tổ hợp phù hợp.
Chia sẻ kiểm revision trước và sau khi ghép ảnh. Collage là JPEG trong cache, URI chỉ đọc. Người dùng chọn ứng dụng nhận hoặc hủy ở bảng chia sẻ.

Tủ đồ và Hồ sơ đọc JPEG, PNG, WebP và HEIC/HEIF theo định dạng thực tế của tệp. HEIC/HEIF cần bộ giải mã của Android trên thiết bị. App chuẩn hóa chiều ảnh, bỏ metadata và lưu thành JPEG hoặc PNG nếu có độ trong suốt. Ảnh lưu có cạnh dài tối đa 2.048 px và dung lượng tối đa 4 MiB. Tệp đầu vào tối đa 32 MiB và 100 megapixel.

Đã kiểm trên Samsung Android 16 bằng hai ảnh HEIC chọn từ album Camera trong mục **Ảnh toàn thân (tùy chọn)**. Cả hai ảnh hiển thị đúng ô và được lưu thành hai tệp JPEG riêng trong dữ liệu hồ sơ trên máy.

Ảnh mỗi mặt có UUID/hash, không ghi đè file cũ khi thay view. Hai mặt không được dùng cùng content hash, nhưng hash khác không chứng minh cùng garment;
UI yêu cầu người dùng chọn đúng cặp, fixture mismatch vẫn cần người/provider review. Không claim có model phát hiện nhầm cặp.
GARMENT ở private files; PERSON/TRYON_RESULT và registry ở noBackupFilesDir. Manifest app hiện `allowBackup=false`.
`read` xác minh tham chiếu đã đăng ký, path canonical, bytes/hash; không cho consumer dùng đường dẫn tùy ý để đọc file private.
Derivative có `derivedFromMediaId`, preset caps; xóa original dọn cả descendants. Segmentation không ghi đè original.

Draft được giữ trong SavedStateHandle qua recreation; ảnh draft đã copy vào private storage. Cancel dọn ảnh mới không thuộc item đã lưu.
Ảnh garment draft bị bỏ lại do process chết được thu hồi khi mở tủ sau 7 ngày nếu không có item đã lưu tham chiếu; PERSON/results không thuộc chính sách này.
Thu hồi bao gồm file GARMENT ghi dở/chưa có registry sau 7 ngày, chỉ trong đúng thư mục owner UUID và tên media UUID.
Giữ file nếu còn item tham chiếu, registry/journal (kể cả atomic sidecar), file mới, tên không thuộc app hoặc symlink;
không quét PERSON/TRYON_RESULT. Nếu item dùng derivative, toàn bộ chuỗi original của nó cũng được giữ.
Đã có kiểm thử trạng thái file sau interruption; chưa mô phỏng kill process tại mọi lệnh ghi hoặc storage pressure trên máy thật.
Cleanup xử lý từng mục độc lập, giữ mục lỗi để retry; một journal lỗi không ngăn dọn các ảnh khác.
SavedStateHandle không phải bảo đảm khôi phục draft sau force-stop; A-004 kiểm dữ liệu **đã lưu**. Không hứa phục hồi mọi draft khi Android xóa task.

## Recognition và imaging

Để thử nhận diện với proxy của nhóm, build với origin HTTPS đã bật `SERVICE_RECOGNITION` và có phiên demo hợp lệ.

```powershell
.\gradlew.bat :app:assembleDebug "-Pfitcheck.proxyUrl=https://your-proxy.workers.dev"
```

Ở **Tủ đồ**, mở form sửa hoặc thêm món, chọn ảnh trước, rồi bấm **Gợi ý bằng AI**. Nhập mã demo trong hộp thoại nếu phiên chưa có hoặc hết hạn.
Client đọc `/health`, lấy token ngắn hạn qua `/v2/session` và gửi ảnh đã xác nhận tới `/v2/recognition`. Token ở trong bộ nhớ trong thời gian chạy app.
Với `FRONT_ONLY`, hộp thoại chỉ hỏi gửi mặt trước. Với `DUAL`, hộp thoại liệt kê từng mặt được gửi. URL mặc định để app ở chế độ nhập tay.
Ảnh gửi là JPEG/PNG tối đa 1 MiB và cạnh dài tối đa 1.024 px. Nếu cần thu nhỏ, client dọn ảnh dẫn xuất sau request. Hợp đồng wire và cấu hình server ở [Proxy API](contracts/proxy-api.md).

Chỉ bấm **Gợi ý bằng AI** rồi xác nhận đúng provider/views mới tạo request. Consent gắn purpose, request ID/generation, config version và media IDs/views.
Thay đổi capability hoặc version proxy giữa lúc xác nhận và gửi làm request bị từ chối. Timeout 20 giây, có hủy và đường tự điền.
Field đã sửa tay thắng kết quả trả chậm; thay ảnh/hủy/lưu làm request cũ vô hiệu. FAKE hiện nhãn rõ trong test, không thành AI_LIVE.
Các trường UNKNOWN/null không bị đổi thành nhãn đoán chắc chắn. Kết quả chưa biết không giữ provenance AI cũ.

### Tag loại chi tiết

Trong **Tủ đồ → Thêm đồ**, chọn **Nhóm đồ** rồi chọn **Loại chi tiết**, ví dụ **Áo sơ mi**, **Áo polo** hoặc **Áo thun**. Tủ đồ hiển thị tag trên thẻ, tìm theo tên tag có hoặc không dấu, và có bộ lọc chính xác theo tag kết hợp với màu. Đổi nhóm trong lúc chỉnh sửa sẽ bỏ tag thuộc nhóm cũ. Món đã lưu từ phiên bản trước có thể được bổ sung tag khi chỉnh sửa.

Khi Worker công bố `recognitionGarmentTypeVersion: 1` tại `/health`, **Gợi ý bằng AI** yêu cầu thêm `garmentType`. Server chỉ giữ đề xuất cùng nhóm khi có bằng chứng từ ảnh gửi và kết quả không đánh dấu nhãn mâu thuẫn hoặc chưa chắc. Nhãn chỉnh tay được ưu tiên. Nếu proxy chưa hỗ trợ, app vẫn nhận diện nhóm và các trường cũ, còn loại chi tiết có thể chọn tay. [Wire contract](contracts/proxy-api.md#post-v2recognition--nhận-diện-thuộc-tính-ct-05).

Từ điển tiếng Việt dùng chung cho Android, pipeline dữ liệu và prompt nhận diện. Tần suất Kaggle dùng để chọn loại phổ biến và chuẩn bị ảnh đối chiếu. Các trường metadata catalog được giữ để tra cứu theo ID sản phẩm. [Hướng dẫn dữ liệu](../data/README.md#catalog-kaggle).

CameraX bind theo lifecycle, xin quyền khi dùng; từ chối vẫn chọn Photo Picker được. Chọn một route CameraX, không bổ sung camera intent trùng chức năng.
Chụp mới đi qua cùng pipeline MediaStore. Cold-model/offline segmentation có đường bỏ qua. Readiness dựa trên ModuleInstallClient;
preview chỉ hiện thành công khi có foreground thực, không trả ảnh gốc giả làm kết quả tách nền.
`GarmentCutoutProcessor` tách xử lý local khỏi UI để kiểm unavailable/downloading/no-subject/late result độc lập.
Preview là tạm thời: thử lại, đổi view, hủy hoặc đóng màn hình đều dọn derivative của preview; không thay ảnh gốc đã lưu.
Generation chặn kết quả cũ và dọn output tới muộn. File tạm được dọn cả khi coroutine bị hủy sau khi đã ghi xong ảnh.
Nguồn kỹ thuật: [ML Kit subject segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android),
[ModuleInstallClient](https://developers.google.com/android/guides/module-install-apis).

## Today với proxy của nhóm

Today dùng hai thiết lập `fitcheck.proxyUrl` và `fitcheck.accessCode` khi build demo. Dùng URL HTTPS của Worker đã bật `SERVICE_OUTFITS`, đặt mã demo trong Gradle properties của máy. Form nhận diện vẫn nhập mã demo ở hộp xác nhận gửi ảnh khi phiên hết hạn.

Với cấu hình mặc định, mở **Hôm nay** sau khi nhập bộ ảnh thử để xem **Phối cơ bản**. Mở thẻ món, bấm **Phối đồ cùng món này**, thử **Phối lại** và **Chia sẻ ảnh mặt trước**. Luồng AI live thuộc INT-002 và cần proxy HTTPS đã triển khai.

## Kiểm thử và bàn giao

Catalog Kaggle dùng cho thử nhập ảnh và đối chiếu nhãn trên PC. Chạy lệnh trong hướng dẫn để tải v1, lấy đủ metadata, tạo top 10 từng cột nhãn và xuất ảnh theo loại chi tiết. [Hướng dẫn catalog](../data/README.md#catalog-kaggle).

```powershell
python tools/data/validate_fixtures.py
python -m unittest discover -s tools/data -p test_prepare_kaggle_fashion.py
.\gradlew.bat :core:model:test :core:database:testDebugUnitTest :core:imaging:testDebugUnitTest :feature:wardrobe:testDebugUnitTest
.\gradlew.bat :core:ai:testDebugUnitTest :feature:today:testDebugUnitTest :core:datastore:testDebugUnitTest
.\gradlew.bat :core:datastore:connectedDebugAndroidTest
.\gradlew.bat assembleDebug testDebugUnitTest :core:model:test lintDebug :core:database:verifyDatabaseCoverage
```

Máy RAM thấp dùng `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" -Pkotlin.compiler.execution.strategy=in-process`.
Báo cáo unit tests trong mỗi module `build/reports/tests/`; coverage ở `core/database/build/reports/jacoco/coverageReport/`.
Báo cáo [hồi quy CORE trên Samsung](qa/track-a-core-regression.md) ghi từng ca và SHA-256 của APK đã cài. Các lần trước có [QA tích hợp A–B](qa/track-a-b-handoff.md), [QA trên máy thật](qa/track-a-physical-device.md), [QA trên emulator](qa/track-a-emulator-integration.md), [QA ban đầu](qa/track-a-acceptance.md) và [QA bổ sung độc lập](qa/track-a-independent-followup.md).
Trạng thái card ở [board](execution/TASK_BOARD.md).

Kiểm thử và triển khai tag chi tiết theo [báo cáo Samsung](qa/track-a-garment-tags.md).
[Điểm demo nội bộ](../Scorings.MD) và [điểm reviewer A cho B-004](../eval/ai/results/2026-11-12T12-48-27-674-recommendation.md#kết-quả-rà-soát-của-track-a) có thang chấm riêng.
[Báo cáo v1](track-a-verification.md) là lịch sử, không được dùng thay kết quả v2.

Điểm D/B/C cần review gồm quyền sở hữu media, luồng live qua proxy, revision khi chọn outfit cho VTO, consent qua job và gate artifact máy thật.

## Điểm nhận việc để làm song song

Nhánh bàn giao trên remote: `track/a-wardrobe-imaging`. Bản đầu `80d33d8`/`e117153` đã được merge vào main qua `114d3cf`;
người dùng yêu cầu các cải tiến độc lập tiếp theo được push lên **main** bằng `NQKhaixyz`.
Lấy bản mới bằng `git fetch origin`, rồi tích hợp `origin/main` vào nhánh riêng sau khi review; nhánh A cũ không phải điểm lấy bản mới nhất.

- **B:** review client Android theo CT-05 và chạy INT-002 với proxy live, sau khi nhóm xác nhận provider và ngân sách. Bộ test mock nằm trong `core:ai`.
- **D:** review shared contracts, DI, schema migration và component Today ở `core:ui`. Khi nối VTO, kiểm snapshot/ID/revision lại trước khi tạo job.
- **C:** nhận `MediaRef`/scope từ owner, dùng API đọc media thay vì tự dựng path; review cleanup/retention ảnh người và kết quả cùng D.
  `OutfitVisual` hiện là ảnh garment, không phải kết quả try-on.

Mỗi track giữ branch riêng; thay đổi file model/shared root cần review chung như alignment hiện hành. Không ghi nhận consumer review thay người chưa tham gia.
