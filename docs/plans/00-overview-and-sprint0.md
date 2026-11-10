# 00 — Tổng quan chia track & Sprint 0 (Contracts)

> Tham chiếu: [`../MASTER_PLAN.md`](../MASTER_PLAN.md) · Trạng thái: **DRAFT — chờ duyệt**
> Giả định: Kotlin + Jetpack Compose + Hilt + Room, AI qua proxy (chốt lại nếu đổi stack).

## 1. Chiến lược chống merge conflict

1. **Mỗi track sở hữu một nhóm Gradle module/thư mục riêng.** Một file chỉ có **một owner**.
2. **Sprint 0 dựng sẵn toàn bộ khung**: tất cả module, interface, model, Hilt binding, navigation route, dependency — trước khi tách 4 người.
3. **Giao tiếp giữa các track chỉ qua interface trong `core:model`**. Mỗi interface có sẵn **stub trả dữ liệu giả** ⇒ không ai phải chờ ai.
4. **Owner thay ruột stub bằng implementation thật** bên trong module của mình — không động vào file của người khác.
5. File dùng chung (hotspot) có luật riêng (mục 4).

## 2. Bốn track

| Track | Tên | Sở hữu | Plan |
|---|---|---|---|
| **A** | Wardrobe & Imaging | `core:database`, `core:imaging`, `feature:wardrobe` | [10-track-a-wardrobe.md](10-track-a-wardrobe.md) |
| **B** | AI & Proxy | `proxy/` (Cloudflare Worker), `core:ai`, `eval/` | [20-track-b-ai.md](20-track-b-ai.md) |
| **C** | Stylist & Weather | `core:weather`, `core:stylist`, `feature:today` | [30-track-c-stylist.md](30-track-c-stylist.md) |
| **D** | Profile, Insights & Evidence | `core:ui`, `core:datastore`, `core:analytics`, `feature:onboarding`, `feature:insights`, `docs/research/` | [40-track-d-insights.md](40-track-d-insights.md) |

Track D kiêm phần **bằng chứng người dùng + beta + GTM** (tiêu chí 2–3), vì phần code của D nhẹ nhất.

## 3. Cấu trúc repo sau Sprint 0

```
FitCheckAI/
├─ app/                      # MainActivity, NavHost, Application — FROZEN sau Sprint 0
├─ core/
│  ├─ model/                 # domain model + interface — HOTSPOT (luật riêng)
│  ├─ ui/                    # theme, design system           (D)
│  ├─ database/              # Room DB, entity, DAO           (A)
│  ├─ imaging/               # CameraX, ML Kit, file storage  (A)
│  ├─ ai/                    # Claude client qua proxy        (B)
│  ├─ weather/               # Open-Meteo + vị trí            (C)
│  ├─ stylist/               # RuleBased + Resilient generator (C)
│  ├─ datastore/             # UserProfile (DataStore)        (D)
│  └─ analytics/             # metric local cho bằng chứng    (D)
├─ feature/
│  ├─ wardrobe/              # (A)
│  ├─ today/                 # (C)
│  ├─ onboarding/            # (D)
│  └─ insights/              # (D)
├─ proxy/                    # Cloudflare Worker TS           (B)
├─ eval/                     # bộ ảnh test + script đo        (B)
├─ docs/  research/          # khảo sát, phỏng vấn, GTM       (D)
├─ gradle/libs.versions.toml # HOTSPOT
└─ .github/CODEOWNERS
```

## 4. Contracts (định nghĩa trong Sprint 0 — `core:model`)

### Domain model
```kotlin
enum class Category { TOP, BOTTOM, DRESS, OUTERWEAR, SHOES, ACCESSORY }
enum class Occasion { SCHOOL, WORK, DATE, PARTY, SPORT, CASUAL, FORMAL_EVENT }
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

data class ClothingAttributes(
  val category: Category, val subCategory: String,
  val colors: List<String>,          // hex hoặc tên màu chuẩn
  val pattern: String, val material: String?,
  val formality: Int,                // 1..5
  val seasons: Set<Season>, val styleTags: Set<String>,
)
data class ClothingItem(
  val id: String, val imagePath: String, val attributes: ClothingAttributes,
  val createdAt: Instant, val wearCount: Int, val lastWornAt: Instant?,
)
data class WeatherSnapshot(val tempC: Double, val feelsLikeC: Double,
  val rainProbability: Int, val condition: String, val city: String)
data class StyleProfile(val styles: Set<String>, val fitNotes: String?, val city: String?)
data class OutfitRequest(val items: List<ClothingItem>, val weather: WeatherSnapshot?,
  val occasion: Occasion, val profile: StyleProfile, val avoidItemIds: Set<String>)
enum class OutfitSource { AI, RULES }
data class Outfit(val id: String, val itemIds: List<String>, val explanation: String,
  val alternativesHint: String?, val source: OutfitSource)
```

### Interface + owner
| Interface | Owner implement | Consumer |
|---|---|---|
| `WardrobeRepository` (observeItems, getItem, save, update, delete) | A | C, D |
| `ImageSegmenter` (removeBackground(uri) → path) | A | A |
| `ClothingTagger` (tag(imagePath) → `Result<ClothingAttributes>`) | B | A |
| `AiOutfitGenerator` (generate(req) → `Result<List<Outfit>>`) | B | C |
| `WeatherProvider` (current() → `Result<WeatherSnapshot>`) | C | C |
| `OutfitService` (suggest(occasion, focusItemId) → `OutfitSuggestions`; AI → fallback rules) | C | C, D |
| `WearLogRepository` (logWear(outfit) — tự gọi `markWorn`, observeLogs, recordFeedback) | D | C, D |
| `ProfileRepository` (observe, save) | D | C, D |
| `MetricsTracker` (track(event)) | D | A, C, D |

Mỗi interface có class `Stub…` trong **module của owner**, đã được bind bằng Hilt `@Module` trong module đó. Owner thay stub = sửa file của chính mình.

### Navigation
`core:model/nav/Routes.kt` chứa hằng route. Mỗi feature xuất `fun NavGraphBuilder.xxxGraph(navController)`; `app` gọi 4 hàm này **một lần duy nhất** trong Sprint 0.

### Proxy API (giữa B và chính B — nhưng cố định để A/C test được bằng curl)
`docs/contracts/proxy-api.md`: `POST /v1/tag` (ảnh base64 → `ClothingAttributes` JSON), `POST /v1/outfits` (`OutfitRequest` rút gọn → danh sách outfit). Header `X-Device-Id`, lỗi chuẩn `{error:{code,message}}`.

## 5. Luật cho hotspot

| File | Luật |
|---|---|
| `core:model/**` | Chỉ **thêm** (field có default, interface method mới có default impl). Đổi/xóa phải PR riêng, 4 người duyệt. |
| `gradle/libs.versions.toml` | Sprint 0 thêm sẵn mọi dependency dự kiến. Sau đó chỉ thêm dòng mới, xếp theo alphabet; rebase trước khi merge. |
| `settings.gradle.kts`, `app/**` | Frozen. Cần sửa ⇒ báo nhóm, 1 PR nhỏ riêng. |
| `core:database` schema | Owner A. Track khác cần bảng mới ⇒ mở issue, A thêm (Room migration). |
| `core:ui` | Owner D. Track khác dùng, không sửa; cần component chung ⇒ issue cho D, hoặc tự viết local trong feature trước. |

## 6. Git workflow
- Nhánh: `track/a-…`, `track/b-…`; PR nhỏ (≤ 400 dòng), merge vào `main` **ít nhất 1 lần/ngày**, luôn rebase lên `main` trước.
- `CODEOWNERS` map thư mục → người, PR chạm thư mục người khác cần họ duyệt.
- CI (GitHub Actions): `./gradlew assembleDebug testDebugUnitTest lint` cho mọi PR; `proxy/` chạy `npm test` riêng.
- `main` luôn build được APK cài lên máy thật.

## 7. Sprint 0 — checklist (1 người làm, ~1 ngày, 3 người còn lại làm việc không-code/đọc tài liệu)
- [x] Gradle multi-module đủ module ở mục 3, version catalog đủ dependency
- [x] `core:model`: model + interface + `Routes` (gồm `ImageSegmenter.warmUp()`, route `today?focusItemId=` cho deep link từ Insights)
- [x] Mỗi module owner: `Stub…` impl + Hilt module bind
- [x] Mỗi feature: 1 màn hình placeholder + `xxxGraph()`; `app` có bottom nav: Hôm nay / Tủ đồ / Insights
- [x] `core:ui`: theme cơ bản (màu, typography) — D tinh chỉnh sau
- [x] Seed data: 18 `ClothingItem` mẫu (ô màu thay ảnh, `seed://`) trong `core:database` (debug) để C/D làm UI thật
- [x] `docs/contracts/proxy-api.md`
- [x] CI + CODEOWNERS + PR template
- [ ] APK debug cài chạy trên máy thật (build OK — cần thử trên điện thoại)

## 8. Mốc tích hợp chung

| Mốc | Điều kiện |
|---|---|
| **M1 – Stubs out** | Mọi track đã thay stub chính bằng impl thật (có thể chưa đẹp) |
| **M2 – E2E** | Chụp đồ → tag AI → lưu → phối đồ AI → "mặc bộ này" → Insights cập nhật, trên máy thật |
| **M3 – Beta** | APK phát cho người dùng thật (D), crash-free, analytics chạy |
| **M4 – Demo freeze** | Chỉ fix bug; kịch bản demo chạy 10/10 lần (force-stop → mở lại) |

Mỗi mốc: cả nhóm cùng test 30 phút trên máy thật, ghi bug vào issue gắn label track.
