# FitCheck AI

App Android "tủ đồ số + stylist AI": chụp quần áo → AI tách nền, gắn nhãn → mỗi ngày AI phối đồ theo thời tiết, dịp, phong cách và giải thích lý do.

- Kế hoạch tổng: [`docs/MASTER_PLAN.md`](docs/MASTER_PLAN.md)
- Chia track & luật chống conflict: [`docs/plans/00-overview-and-sprint0.md`](docs/plans/00-overview-and-sprint0.md)
- Plan từng track: [A](docs/plans/10-track-a-wardrobe.md) · [B](docs/plans/20-track-b-ai.md) · [C](docs/plans/30-track-c-stylist.md) · [D](docs/plans/40-track-d-insights.md)
- Contract proxy: [`docs/contracts/proxy-api.md`](docs/contracts/proxy-api.md)

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
Dùng proxy thật: `./gradlew installDebug -Pfitcheck.proxyUrl=https://<your-worker>.workers.dev`

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
| `core:database` | A | Room (mọi entity/DAO), `WardrobeRepository`, seed data |
| `core:imaging` | A | CameraX, ML Kit tách nền |
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

## Trạng thái Sprint 0
Mọi interface đã có **stub** (tìm `SPRINT 0 STUB` / `TODO(Track X)`) và đã bind Hilt trong module của owner.
Owner thay stub bằng implementation thật **trong module của mình** rồi đổi 1 dòng `@Binds` — không cần đụng file của track khác.

Luồng chạy được ngay: Onboarding → Hôm nay (outfit từ stub AI) → "Mặc bộ này" → Insights cập nhật; Tủ đồ hiển thị 18 món mẫu.

## Quy trình git
- Nhánh `track/<a|b|c|d>-<việc>`, PR nhỏ, rebase lên `main` trước khi merge, merge ít nhất 1 lần/ngày.
- CI chạy build + unit test + lint cho mọi PR. `main` luôn phải build được.
- Luật file chung: xem mục 5 của [`00-overview-and-sprint0.md`](docs/plans/00-overview-and-sprint0.md).
