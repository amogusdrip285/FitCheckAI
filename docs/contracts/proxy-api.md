# Proxy API contract (v2)

> OWNER: Track B · Consumer: Android AI clients (B-003 recommendation, B-007 recognition), Track C (mount `tryon` qua seam).
> Nguồn domain: [CONTRACTS.md v2](../execution/CONTRACTS.md) CT-02/05/07. File này mô tả **wire format** proxy đang triển khai trong `proxy/src`.
> `/v1/*` của Sprint 0 đã gỡ (chưa từng được triển khai, chỉ trả 501).

Trạng thái: implementation + unit/HTTP tests với model giả (B-001/B-002/BACK-004). **Chưa có evidence live**: cần PRE-003 (provider/budget), deploy và B-004/B-006.

**Android client:** recommendation (B-003) — `core/ai/.../proxy/ProxyOutfitRecommendation` (bind vào `OutfitRecommendation`) qua `ProxyTransport` (session token, deadline 20s, map lỗi CT-07, không auto-retry, mỗi `requestId` chỉ gửi một lần); mã truy cập demo từ gradle property `fitcheck.accessCode` (đặt trong `~/.gradle/gradle.properties`, không commit; trống ⇒ `UNSUPPORTED_CAPABILITY`, không gọi mạng). Recognition (B-007) — nhóm chọn ngày 12/11/2026 giữ `core/ai/ProxyRecognitionClient` do A viết (bind vào `ClothingRecognition`), mã truy cập nhập trên UI (`RecognitionSetup.authorize`); B đã review và sửa để đọc `providers.recognition`, nhận `openai@host`, bỏ qua field `/health` mới. Hai client đang có hai cơ chế lấy session khác nhau — nên hợp nhất ở INT-002. Client kiểm lại mọi invariant response; một bộ/field sai cấu trúc ⇒ bỏ cả response. Test chỉ dùng MockEngine, chưa chạy với proxy thật.

## Chung

- Base URL: `BuildConfig.PROXY_URL` (gradle property `fitcheck.proxyUrl`). Chỉ HTTPS.
- Request có body: `Content-Type: application/json`, UTF-8.
- Route cần auth: header `Authorization: Bearer <session token>` (lấy từ `POST /v2/session`).
- `requestId` trong body (UUID do client sinh) được dùng làm requestId của log/lỗi; thiếu thì server tự sinh.
- Deadline: client 20s, proxy 18s, provider 15s. **Server không auto-retry**; client chỉ retry khi người dùng bấm lại và `retryable=true`.

### Thứ tự chặn (request bị chặn không bao giờ gọi provider)

route → auth (401) → kill switch (503) → rate limit/session (429) → body cap (413) → validate (400/403/409) → daily cap (503 `BUDGET_EXCEEDED`) → provider.

### Error envelope

```json
{ "error": { "code": "INSUFFICIENT_WARDROBE", "message": "…", "requestId": "…", "retryable": false } }
```

`message` là tiếng Việt, không chứa dữ liệu người dùng, không phải để parse — client map theo `code`.

| code | HTTP | retryable | Khi nào |
|---|---|---|---|
| `INVALID_REQUEST` | 400 (404 nếu sai endpoint) | false | Sai schema/field lạ/JSON hỏng/content-type sai/ảnh hỏng/sha256 lệch |
| `UNAUTHORIZED` | 401 | false | Thiếu/sai/hết hạn token; sai mã truy cập |
| `CONSENT_REQUIRED` | 403 | false | Không có ConsentGrant hợp lệ cho đúng ảnh gửi lên |
| `PAYLOAD_TOO_LARGE` | 413 | false | Vượt cap body hoặc cap ảnh |
| `INSUFFICIENT_WARDROBE` | 409 | false | Tủ không tạo nổi một bộ hợp lệ / ràng buộc quá chặt |
| `RATE_LIMITED` | 429 | true | Quá request/phút của session, hoặc provider rate-limit |
| `UNSUPPORTED_CAPABILITY` | 400 | false | Capability không hỗ trợ (vd. FRONT_ONLY nhưng gửi ảnh sau; focus vào món UNKNOWN) |
| `UNSUPPORTED_CAPABILITY` | **503** | false | Service đang tắt (kill switch), provider/session chưa cấu hình |
| `BUDGET_EXCEEDED` | 503 | false | Hết daily cap của service |
| `INVALID_AI_OUTPUT` | 502 | true/false | AI trả sai schema, cụt, hoặc mọi bộ đều không hợp lệ |
| `UPSTREAM_TIMEOUT` | 504 | true | Provider quá hạn |
| `UPSTREAM_ERROR` | 502 (500 nội bộ) | tùy | Lỗi provider, AI từ chối, lỗi server |

> Đề xuất cho D (PRE-007): CT-07 chưa có mã riêng cho "service tắt"; tạm dùng `UNSUPPORTED_CAPABILITY` + HTTP 503. Nếu nhóm muốn mã `SERVICE_DISABLED`, cần đổi contract và client cùng lúc.

---

## `GET /health`

Không cần auth.

```json
{ "status": "ok", "version": "dev", "schemaVersion": 2,
  "services": { "recognition": true, "outfits": true, "tryon": false },
  "recognitionViews": "DUAL", "provider": "openai@codex.fosta.app", "providerConfigured": true,
  "providers": { "recognition": { "provider": "gemini", "model": "gemini-3.5-flash-lite", "configured": true },
                 "outfits":     { "provider": "openai@codex.fosta.app", "model": "gpt-6.1-sol", "configured": true } } }
```

Client dùng `services`/`recognitionViews` để ẩn CTA chưa khả dụng; đây không phải bằng chứng AI chạy tốt. `provider`/`providerConfigured` là cấu hình chung; `providers.<service>` là provider thật từng service dùng (có thể khác nhau) — Android recognition lấy `providers.recognition` để hiện trong consent và tính `configVersion` (đổi provider/model ⇒ consent cũ không còn khớp).

## `POST /v2/session`

Đổi mã truy cập demo lấy token ngắn hạn (mặc định 12 giờ). Giới hạn 10 lần thử/phút/IP.

Request `{ "accessCode": "…" }` → Response `200 { "token": "v1.<sessionId>.<exp>.<sig>", "expiresAt": <epochMillis> }`.

Đây là cơ chế **demo kín**: mã truy cập nằm trong app/được nhập tay nên không phải bảo mật cho public beta (BACK-011).

## `POST /v2/outfits` — recommendation (CT-02)

Chỉ metadata, không ảnh, không local path. Body ≤ 64 KiB. Mọi object `strict`: field lạ (vd. `weightKg`, `imagePath`) ⇒ 400.

Request

```json
{"schemaVersion":2,"requestId":"r1","wardrobeSnapshotId":"s1",
 "items":[{"id":"top1","revision":1,"attributes":{"category":"TOP","colors":["WHITE"]}},
          {"id":"bottom1","revision":1,"attributes":{"category":"BOTTOM","colors":["BLACK"]}}],
 "profile":{"heightCm":null,"stylePreferences":["minimal"],"preferredCategories":[]},
 "occasion":"WORK","styleOverrides":[],"freeText":"Tôi thích đơn giản","weather":null,
 "requestedCount":5,"focusItemId":null,"excludeOutfitSignatures":[],"regenerationOfRequestId":null}
```

| Field | Ràng buộc |
|---|---|
| `items` | 1–50, `id` không trùng. `attributes`: `category` bắt buộc; `colors` ≤3 enum; `pattern`, `styleTags` ≤5×32, `material` ≤40, `formality` 1–5, `seasonTags` ≤4 trong `SPRING·SUMMER·AUTUMN·WINTER` — đều tùy chọn |
| `profile` | `null` hoặc `{heightCm?, stylePreferences ≤5, preferredCategories}`. Không cân nặng/ảnh/UUID người dùng |
| `occasion` | `CASUAL·SCHOOL·WORK·DATE·PARTY·SPORT·FORMAL_EVENT` |
| `freeText` | ≤500 ký tự; là dữ liệu sở thích, không thể ghi đè luật |
| `weather` | `null` hoặc `{cityLabel,tempC,condition,validAt,fetchedAt,source,isStale}` |
| `requestedCount` | 1–5 |
| `focusItemId` | `null` hoặc id trong `items`, category khác `UNKNOWN` |
| `excludeOutfitSignatures` | ≤5 signature (xem dưới) |

Response `200`

```json
{ "requestId": "r1",
  "outfits": [{ "id": "<uuid server gán>", "requestId": "r1", "wardrobeSnapshotId": "s1",
                "items": [{"itemId":"top1","revision":1},{"itemId":"bottom1","revision":1}],
                "explanation": "…tiếng Việt, 1–500 ký tự…", "source": "AI_LIVE",
                "createdAt": 1794621600000, "signature": "bottom1|top1" }],
  "shortfallReason": "NOT_ENOUGH_DISTINCT_COMBINATIONS" }
```

Bảo đảm từ server:

- 1…`requestedCount` bộ; **không bao giờ** success với danh sách rỗng (khi đó là `409 INSUFFICIENT_WARDROBE` hoặc `502 INVALID_AI_OUTPUT`).
- Mọi `itemId` thuộc request, `revision` lấy từ request; không trùng món trong bộ; không trùng tập món giữa các bộ.
- Slot: (1 TOP + 1 BOTTOM) hoặc 1 DRESS; thêm tối đa 1 OUTERWEAR/SHOES/ACCESSORY; không DRESS+TOP/BOTTOM; không UNKNOWN; giày không bắt buộc.
- Có `focusItemId` ⇒ mọi bộ chứa món đó. Không trả bộ có signature trong `excludeOutfitSignatures`.
- Bộ sai bị **loại cả bộ** (không sửa). Trả ít hơn yêu cầu ⇒ `shortfallReason`: `NOT_ENOUGH_DISTINCT_COMBINATIONS`, `CONSTRAINTS_TOO_STRICT`, hoặc `INVALID_OUTFITS_REMOVED` (validator đã loại bộ sai của AI).
- Tủ không có DRESS và không đủ TOP+BOTTOM ⇒ `409 INSUFFICIENT_WARDROBE` **không gọi AI**.
- `id` outfit gán một lần khi server nhận response; client lưu lại, không sinh mới khi render.

**Signature canonical:** các `itemId` sắp xếp tăng dần (so sánh chuỗi), nối bằng `|` — khớp `outfitSignature()` trong `core/model/recommendation` (Track D). Ví dụ `["t2","b1","s1"]` ⇒ `"b1|s1|t2"`. ID không được chứa `|`.

Client vẫn phải so `items[].revision` với repository hiện tại trước khi hiển thị/chọn Try-On (CT-02): món đã sửa/xóa ⇒ stale.

## `POST /v2/recognition` — nhận diện thuộc tính (CT-05)

Body ≤ 4 MiB; mỗi ảnh ≤ 1 MiB sau decode; JPEG/PNG/WebP (kiểm magic bytes khớp `mimeType`). Ảnh gửi dưới dạng base64 — **không nhận URL**. Không gửi ảnh người/profile.

Request

```json
{ "schemaVersion": 2, "requestId": "rec-1", "itemId": "item-1", "inputRevision": 3,
  "front": { "view": "FRONT", "mediaId": "m-front", "mimeType": "image/jpeg", "sha256": "<hex, tùy chọn>", "dataBase64": "…" },
  "back":  { "view": "BACK",  "mediaId": "m-back",  "mimeType": "image/jpeg", "dataBase64": "…" },
  "consentGrant": { "grantId": "g1", "purpose": "RECOGNITION", "mediaIds": ["m-front","m-back"],
                    "issuedAt": 1794621600000, "revokedAt": null, "userConfirmed": true } }
```

- `back` tùy chọn (`null`/bỏ qua). Ảnh sau phải khác ảnh trước (khác `mediaId` và khác bytes) — không nhân đôi ảnh trước.
- `consentGrant` phải: `purpose = RECOGNITION`, `userConfirmed = true`, `revokedAt = null`, `mediaIds` phủ mọi ảnh gửi lên. Sai ⇒ `403 CONSENT_REQUIRED`, không gọi AI.
- Server ở chế độ `FRONT_ONLY` (xem `/health`) mà gửi `back` ⇒ `400 UNSUPPORTED_CAPABILITY` trước khi tính tiền; client gửi lại chỉ ảnh trước và báo người dùng.

Response `200`

```json
{ "requestId": "rec-1", "itemId": "item-1", "inputRevision": 3,
  "suggestedAttributes": { "category": "TOP", "colors": ["WHITE"], "pattern": "SOLID",
                           "styleTags": ["tối giản"], "material": null, "formality": 2, "seasonTags": ["SUMMER"] },
  "needsReviewFields": ["material"],
  "viewEvidence": { "category": ["FRONT"], "colors": ["FRONT"] },
  "warnings": [],
  "source": "AI_LIVE" }
```

- Đây là **gợi ý**, server không lưu. Client (A — BACK-005) chỉ áp lên field người dùng chưa sửa và bỏ kết quả nếu `inputRevision` đã cũ.
- `needsReviewFields`: field AI không chắc, front/back mâu thuẫn, hoặc giá trị bị chuẩn hóa (vd. >3 màu, formality ngoài 1–5).
- `warnings`: `VIEWS_LOOK_LIKE_DIFFERENT_ITEMS` (hai ảnh có vẻ khác món ⇒ mọi field cần review), `CATEGORY_UNKNOWN`.
- `viewEvidence`: field ⇒ ảnh nào cho thấy; không bao giờ chứa `BACK` khi không gửi ảnh sau.
- `category: UNKNOWN` lưu nháp được nhưng recommendation không dùng như TOP.
- `seasonTags` chỉ gồm `SPRING·SUMMER·AUTUMN·WINTER`, khớp `ClothingAttributes.validate()` trong `core/model/wardrobe` (Track A).

**Map sang Kotlin `core/model/wardrobe` (B-007 client làm):** field trong `needsReviewFields`/`viewEvidence` — `category→CATEGORY`, `colors→COLORS`, `pattern→PATTERN`, `material→MATERIAL`, `formality→FORMALITY`, `seasonTags→SEASONS`, `styleTags→STYLES`. ConsentGrant Kotlin → wire: `id→grantId`, `confirmedByUser→userConfirmed`, `mediaIds`, `issuedAt`, `revokedAt`, `providerConfigVersion`, `purpose`.

## Mount route của track khác (Track C — Try-On)

C export `RouteDef[]` từ `proxy/src/tryon/**` (interface ở `proxy/src/shared/registry.ts`: `method`, `path` có thể chứa `:param`, `service: "tryon"`, `maxBodyBytes`, `handle(body, ctx)`), test độc lập bằng `createApp([...tryonRoutes], deps)`, rồi gửi patch thêm vào `ROUTES` ở `proxy/src/index.ts`. Route được hưởng sẵn auth, kill switch `SERVICE_TRYON`, rate limit, body cap, deadline và log an toàn; gọi `ctx.reserveUpstreamCall()` ngay trước khi gọi provider.

## Vận hành

- Provider: `AI_PROVIDER = "anthropic" | "gemini" | "openai"` (adapter ở `proxy/src/shared/model.ts`, `gemini.ts`, `openai.ts`; wire format client không đổi). `AI_MODEL` trống ⇒ mặc định `claude-opus-5-5` / `gemini-3.5-flash-lite` / `gpt-6.1-sol`. Ghi đè từng service: `AI_PROVIDER_RECOGNITION|OUTFITS`, `AI_MODEL_RECOGNITION|OUTFITS` (trống ⇒ cấu hình chung; `AI_MODEL` chung chỉ áp khi service cùng provider chung). `openai` dùng `OPENAI_API_KEY` + `OPENAI_BASE_URL` (HTTPS, mặc định `https://api.openai.com/v1`; gateway FOSTA `https://codex.fosta.app/v1`) và `/health.provider` = `openai@<host>`. FOSTA: outfits ~10–13s, recognition ~19–25s (vượt deadline 15s hiện tại) — xem `docs/track-b/PRE-003-provider-budget.md`. **Gemini free tier chỉ để test**: Google được dùng dữ liệu gửi lên để cải thiện sản phẩm — không gửi ảnh người dùng thật; `/health` báo `provider`.
- Secrets: `ANTHROPIC_API_KEY` hoặc `GEMINI_API_KEY` (theo provider), `SESSION_SECRET`, `DEMO_ACCESS_CODE` (xem `proxy/wrangler.toml`, `proxy/.dev.vars.example`). Key không bao giờ nằm trong APK/git/log.
- Bật/tắt: `SERVICE_RECOGNITION|OUTFITS|TRYON = "on"|"off"` (mặc định off). Đổi biến ⇒ redeploy.
- `DAILY_CAP_*` và rate limit đếm **theo isolate**, best-effort — không phải quota toàn cục hay trần tiền. Trần tiền thật: spend limit ở console provider (PRE-003). Quota atomic/toàn cục: BACK-011.
- Log mỗi request là một dòng JSON: `requestId, route, service, status, latencyMs, errorCode, model, inputTokens, outputTokens, accepted, rejected, promptVersion`. Không ảnh/base64/freeText/profile/token.
