# FitCheck AI

App Android quản lý tủ đồ và phát triển các luồng gợi ý phối đồ/try-on theo [Workflow v2](TEAM_WORKFLOW.md).
Track A hiện quản lý ảnh thật, thông tin món đồ và lưu trữ local. Các tính năng AI live còn cần implementation và evidence của track tương ứng.

- Kế hoạch tổng: [`docs/MASTER_PLAN.md`](docs/MASTER_PLAN.md)
- Chia track & luật chống conflict: [`docs/plans/00-overview-and-sprint0.md`](docs/plans/00-overview-and-sprint0.md)
- Plan từng track: [A](docs/plans/10-track-a-wardrobe.md) · [B](docs/plans/20-track-b-ai.md) · [C](docs/plans/30-track-c-stylist.md) · [D](docs/plans/40-track-d-insights.md)
- Contract proxy: [`docs/contracts/proxy-api.md`](docs/contracts/proxy-api.md)

Kế hoạch thực thi mới: [Workflow v2](TEAM_WORKFLOW.md), [Task board](docs/execution/TASK_BOARD.md),
[Contracts v2](docs/execution/CONTRACTS.md). Track A đã triển khai adapter độc lập trong kiến trúc module hiện có,
Room schema 2 và namespace `core.model.wardrobe`; không tự thay toàn bộ composition root của D.
Xem [hướng dẫn Track A v2](docs/track-a-v2.md), [dataset](data/README.md), [bảng nghiệm thu](docs/qa/track-a-acceptance.md).
Phần recovery/preview lifecycle và kiểm thử Android bổ sung sau merge: [QA độc lập mới nhất](docs/qa/track-a-independent-followup.md).

## Yêu cầu
- JDK 17+ (đã test với JDK 21)
- Android SDK: platform 35, build-tools 35 (Android Studio tự cài)
- Node 20+ (chỉ cho `proxy/`)

## Chạy app
```bash
# local.properties (không commit): sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
./gradlew assembleDebug                 # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # cài lên máy thật đang cắm USB (bật USB debugging)
./gradlew testDebugUnitTest :core:model:test
```
Khi Track B tích hợp client thật, cấu hình proxy bằng `./gradlew installDebug -Pfitcheck.proxyUrl=https://<your-worker>.workers.dev`.

## Chạy proxy
```bash
cd proxy
npm install
npm test
npm run dev        # cần proxy/.dev.vars: ANTHROPIC_API_KEY=...
```

## Cấu trúc module

| Module | Owner | Nội dung |
|---|---|---|
| `app` | FROZEN | Application, MainActivity, NavHost, bottom nav |
| `core:model` | HOTSPOT | Domain model, **interface contract**, `Routes` |
| `core:ui` | D | Theme, component dùng chung |
| `core:database` | A | Room schema 2, revision/tombstone/cleanup journal, bridge đọc v1 |
| `core:imaging` | A | MediaStore dùng chung, CameraX, ML Kit tùy chọn |
| `core:ai` | B | Client gọi proxy: `ClothingTagger`, `AiOutfitGenerator` |
| `core:weather` | C | `WeatherProvider` (Open-Meteo) |
| `core:stylist` | C | `OutfitService` (AI + fallback rule-based) |
| `core:datastore` | D | `ProfileRepository`, `WearLogRepository` |
| `core:analytics` | D | `MetricsTracker` |
| `feature:wardrobe` | A | Tủ đồ, thêm đồ, chi tiết |
| `feature:today` | C | Màn "Hôm nay" |
| `feature:onboarding` | D | Onboarding |
| `feature:insights` | D | Thống kê, đồ bị bỏ quên |
| `proxy/` | B | Cloudflare Worker giữ API key |
| `build-logic/` | FROZEN | Convention plugin Gradle |

## Trạng thái triển khai
**Track A v2:** Room, UUID/revision, ảnh trước bắt buộc/sau tùy chọn, Photo Picker/CameraX,
form thủ công có giá trị chưa biết, tìm kiếm/lọc category/color, sửa/xóa/thay riêng từng mặt,
MediaStore có giới hạn ảnh/hash/cleanup, component `OutfitVisual` dùng lại qua DTO và callbacks.
Ảnh garment ở `filesDir/media/GARMENT`, ảnh PERSON/TRYON_RESULT và registry ở `noBackupFilesDir`.
Schema 1 được migrate; file cũ được giữ để đọc/repair, không reset database.

**Dataset:** 5.096 ảnh có nhãn được lọc từ bộ CC0; 21 món/24 ảnh thật và 3 cặp trước/sau được đóng gói trong debug.
Repo còn có [100 ảnh bulk mẫu](data/samples/modern-clothing-100/README.md), kèm nhãn gốc và giấy phép; bulk đầy đủ giữ local, 100 ảnh mẫu không đóng gói APK.
Tủ đồ → **Ảnh kiểm thử** → **Thêm bộ ảnh vào tủ đồ**. Không tự seed khi tạo database; release không đóng gói fixtures.
Nguồn/quyền, scripts tái tạo, giới hạn nhãn và đánh giá Polyvore nằm tại [data/README.md](data/README.md).

**AI:** Track A bind `UnavailableRecognition` cho đến khi B giao client v2. Không tự chuyển sang fake AI.
Form thủ công vẫn lưu được; fake v2 nằm trong `core:model` testFixtures, không được đóng gói trong APK.
ML Kit tách nền là tùy chọn, kiểm tra model readiness và giữ ảnh gốc. Không báo chất lượng máy thật nếu chưa chạy.
Các màn Today/Profile/Insights của checkout hiện còn code v1/stub; xem bảng nghiệm thu trước khi gọi đây là sản phẩm end-to-end hoàn chỉnh.

Kiểm tra đầy đủ:
```bash
./gradlew assembleDebug testDebugUnitTest :core:model:test lintDebug :core:database:verifyDatabaseCoverage
```
`verifyDatabaseCoverage` yêu cầu line coverage ≥ 80% cho code database viết tay
(không tính code Room/Hilt sinh tự động, DI và boilerplate entity).
Xem hướng dẫn tích hợp trong [Track A v2](docs/track-a-v2.md) và kết quả/giới hạn trong [QA](docs/qa/track-a-acceptance.md).

## Quy trình git
- Nhánh `track/<a|b|c|d>-<việc>`, PR nhỏ, rebase lên `main` trước khi merge, merge ít nhất 1 lần/ngày.
- CI chạy build + unit test + lint cho mọi PR. `main` luôn phải build được.
- Luật file chung: xem mục 5 của [`00-overview-and-sprint0.md`](docs/plans/00-overview-and-sprint0.md).
