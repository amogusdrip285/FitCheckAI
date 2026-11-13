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
App gọi AI qua proxy: `./gradlew installDebug -Pfitcheck.proxyUrl=https://<your-worker>.workers.dev -Pfitcheck.accessCode=<mã demo>`
(đặt hai property này trong `~/.gradle/gradle.properties` thay vì gõ mỗi lần; không commit mã). Nhận diện ảnh chỉ nhận proxy HTTPS và cho nhập mã trên màn hình.

## Chạy proxy
```bash
cd proxy
npm install
npm test
npm run dev        # copy proxy/.dev.vars.example thành proxy/.dev.vars rồi điền key (không commit)
```
Pitching/demo dùng OpenAI chính chủ: `AI_PROVIDER=openai`, `OPENAI_API_KEY=...`, model mặc định `gpt-6-luna`.
Provider, ngân sách và lý do chọn: [PRE-003](docs/track-b/PRE-003-provider-budget.md). Wire format và biến môi trường: [proxy-api.md](docs/contracts/proxy-api.md).

## Cấu trúc module

| Module | Owner | Nội dung |
|---|---|---|
| `app` | FROZEN | Application, MainActivity, NavHost, bottom nav |
| `core:model` | HOTSPOT | Domain model, **interface contract**, `Routes` |
| `core:ui` | D | Theme, component dùng chung |
| `core:database` | A | Room schema 2, revision/tombstone/cleanup journal, bridge đọc v1 |
| `core:imaging` | A | MediaStore dùng chung, CameraX, ML Kit tùy chọn |
| `core:ai` | B | Client v2 gọi proxy: `ProxyOutfitRecommendation` (+ `rerollAfter`), `ProxyRecognitionClient` (A viết, B review), fallback `BasicOutfitFallback`, thời tiết theo thành phố `CityWeatherResolver` (Open-Meteo); stub v1 cho consumer cũ |
| `core:weather` | C | `WeatherProvider` (Open-Meteo) |
| `core:stylist` | C | `OutfitService` (AI + fallback rule-based) |
| `core:datastore` | D | `ProfileRepository`, `WearLogRepository` |
| `core:analytics` | D | `MetricsTracker` |
| `feature:wardrobe` | A | Tủ đồ, thêm đồ, chi tiết |
| `feature:today` | C | Màn "Hôm nay" |
| `feature:onboarding` | D | Onboarding |
| `feature:insights` | D | Thống kê, đồ bị bỏ quên |
| `proxy/` | B | Cloudflare Worker giữ API key: `/v2/outfits`, `/v2/recognition`, session, kill switch, cap; `src/tryon/` của C chưa mount |
| `build-logic/` | FROZEN | Convention plugin Gradle |

## Trạng thái triển khai
**Track A v2:** Room, UUID/revision, ảnh trước bắt buộc/sau tùy chọn, Photo Picker/CameraX,
form thủ công có giá trị chưa biết, tìm kiếm/lọc category/color, sửa/xóa/thay riêng từng mặt,
MediaStore có giới hạn ảnh/hash/cleanup, component `OutfitVisual` dùng lại qua DTO và callbacks.
Ảnh garment ở `filesDir/media/GARMENT`, ảnh PERSON/TRYON_RESULT và registry ở `noBackupFilesDir`.
Schema 1 được migrate; file cũ được giữ để đọc/repair, không reset database.

**Dataset Track A** dùng catalog Kaggle Fashion Product Images (Small) v1. Sau audit có **24.856 ảnh** thuộc 6 nhóm, kèm nhãn gốc và nhãn quy đổi của app. Script tải nguồn, tạo chỉ mục và xuất mẫu trên máy phát triển. [Cách chuẩn bị catalog](data/README.md#catalog-kaggle).

Bản debug có bộ kiểm thử riêng gồm 21 món, 24 ảnh thật và 3 cặp trước/sau.
Tủ đồ → **Ảnh kiểm thử** → **Thêm bộ ảnh vào tủ đồ**. Không tự seed khi tạo database; release không đóng gói fixtures.
Nguồn/quyền, scripts tái tạo, giới hạn nhãn và đánh giá Polyvore nằm tại [data/README.md](data/README.md).

**AI (Track B):** `core:ai` bind client v2 thật cho recognition và recommendation; khi proxy chưa cấu hình, client trả lỗi có kiểu, app vẫn cho nhập tay và có bộ phối cơ bản offline (`source = RULES`). Không tự chuyển sang fake AI.
Proxy có adapter OpenAI (gồm gateway tương thích), Gemini, Anthropic; chưa deploy HTTPS nên AI live mới chạy qua proxy local. Eval: [luna 12/11](eval/ai/results/2026-11-12-luna-summary.md).
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
