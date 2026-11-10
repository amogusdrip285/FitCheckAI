# Proxy API contract (v1)

> OWNER: Track B · Consumer: `core:ai` (Android). Đổi contract ⇒ tăng version (`/v2`), giữ `/v1` cho tới khi app cập nhật.

Base URL: `BuildConfig.PROXY_URL` (gradle property `fitcheck.proxyUrl`).
Mọi request: `Content-Type: application/json`, header `X-Device-Id: <uuid ổn định theo cài đặt>`.
Mọi lỗi: HTTP 4xx/5xx + body
```json
{ "error": { "code": "RATE_LIMITED", "message": "Thử lại sau 60 giây" } }
```
| code | HTTP | Ý nghĩa |
|---|---|---|
| `BAD_REQUEST` | 400 | Thiếu field / sai kiểu / ảnh quá lớn |
| `MISSING_DEVICE_ID` | 400 | Không có `X-Device-Id` |
| `RATE_LIMITED` | 429 | Quá giới hạn theo thiết bị |
| `BUDGET_EXCEEDED` | 503 | Hết ngân sách AI trong ngày |
| `AI_INVALID_OUTPUT` | 502 | AI trả sai schema sau khi retry |
| `UPSTREAM_ERROR` | 502 | Lỗi từ Claude API |
| `NOT_IMPLEMENTED` | 501 | Endpoint chưa làm (Sprint 0) |

---

## `GET /health`
`200 {"status":"ok","version":"<git sha|dev>"}`

## `POST /v1/tag`
Nhận diện thuộc tính một món đồ từ ảnh (đã resize ≤ 768px, JPEG/WebP).

Request
```json
{ "imageBase64": "<…>", "mimeType": "image/jpeg", "imageSha256": "<hex>" }
```
- `imageBase64` ≤ 1.5 MB sau decode. `mimeType` ∈ `image/jpeg`, `image/webp`, `image/png`.

Response `200`
```json
{
  "attributes": {
    "category": "TOP",
    "subCategory": "Áo sơ mi",
    "colors": ["#B9CDE5", "#FFFFFF"],
    "pattern": "striped",
    "material": "cotton",
    "formality": 3,
    "seasons": ["SPRING", "SUMMER", "AUTUMN"],
    "styleTags": ["office", "minimal"]
  },
  "confidence": 0.87
}
```
- Giá trị enum trùng tên với `core:model` (`Category`, `Season`). `colors` dạng `#RRGGBB`, màu chủ đạo đứng đầu. `formality` 1–5.
- `subCategory` bằng **tiếng Việt**.

## `POST /v1/outfits`
Phối đồ từ tủ đồ (chỉ gửi **text**, không gửi ảnh).

Request
```json
{
  "items": [
    { "id": "seed-t1", "category": "TOP", "subCategory": "Áo thun trắng", "colors": ["#F5F5F2"],
      "pattern": "solid", "formality": 1, "seasons": ["SUMMER"], "styleTags": ["basic"],
      "wearCount": 3, "daysSinceWorn": 12 }
  ],
  "weather": { "tempC": 31.5, "feelsLikeC": 35.0, "rainProbability": 60, "condition": "Mưa rào", "city": "Hà Nội" },
  "occasion": "WORK",
  "profile": { "styles": ["Minimal", "Công sở"], "fitNotes": null },
  "avoidItemIds": ["seed-b2"],
  "focusItemId": null,
  "count": 3,
  "locale": "vi"
}
```
- `items` ≤ 150. `weather` có thể `null`. `count` 1–3.

Response `200`
```json
{
  "outfits": [
    {
      "itemIds": ["seed-t3", "seed-b3", "seed-s1"],
      "explanation": "Trời 31°C và có thể mưa nên chọn sơ mi cotton thoáng…",
      "alternativesHint": "Đổi sneaker sang giày da nếu có họp với khách."
    }
  ]
}
```
Bảo đảm từ server (đã validate trước khi trả):
- Mọi `itemIds` thuộc `items` của request, không trùng lặp trong một outfit.
- Mỗi outfit có (`TOP` + `BOTTOM`) hoặc `DRESS`; có `SHOES` nếu tủ đồ có giày.
- Nếu có `focusItemId`, mọi outfit chứa món đó.
- `explanation` tiếng Việt, ≤ 300 ký tự.

Client tự gán `id` và `source = AI` khi map sang `Outfit`.
