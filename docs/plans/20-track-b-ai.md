# Track B — AI & Proxy

> **Kế hoạch gốc Sprint 0, giữ làm lịch sử.** Phần v1 (`/v1/tag`, Claude, `ClaudeClothingTagger`…) đã được thay bằng contract v2.
> Trạng thái hiện tại: [board](../execution/TASK_BOARD.md) · [card Track B](../execution/tracks/B.md) · [proxy-api v2](../contracts/proxy-api.md) · [PRE-003 provider/ngân sách](../track-b/PRE-003-provider-budget.md) · [cập nhật Track B trong decision log](../execution/REVIEW_AND_DECISIONS.md#cập-nhật-track-b--1113112026).

**Sở hữu:** `proxy/` (Cloudflare Worker, TypeScript), `core:ai`, `eval/`
**Implement:** `ClothingTagger`, `AiOutfitGenerator`
**Dùng của người khác:** không (track độc lập nhất)

## Mục tiêu
AI trả về **JSON hợp lệ, đúng schema, nhanh**: tag ≤ 4s, phối đồ ≤ 8s (p90) trên 4G. Không bao giờ để API key trong APK.

## Phạm vi

### B1. `proxy/` — Cloudflare Worker
- Endpoint theo `docs/contracts/proxy-api.md`: `POST /v1/tag`, `POST /v1/outfits`, `GET /health`.
- Giữ `ANTHROPIC_API_KEY` bằng Worker secret (`wrangler secret put`), **không commit**.
- Validate input (kích thước ảnh ≤ 1.5MB, số item ≤ 150), rate-limit theo `X-Device-Id` (KV hoặc Durable Object), giới hạn ngân sách/ngày.
- Gọi Claude Messages API:
  - Tag: `claude-haiku-5-5` + vision, structured output / tool-use để ép schema `ClothingAttributes`.
  - Outfit: `claude-sonnet-5-5`, đầu vào là **danh sách item dạng text** (id + thuộc tính, không gửi ảnh) + thời tiết + dịp + profile ⇒ trả 3 outfit, `itemIds` phải thuộc danh sách đầu vào.
- Validate output phía server (item id tồn tại, mỗi outfit có top+bottom hoặc dress, + shoes nếu có), retry 1 lần nếu sai.
- Log lỗi (không log ảnh/PII), trả lỗi theo chuẩn `{error:{code,message}}`.
- Test: Vitest với Claude client mock; 1 test smoke gọi thật (chạy tay).

### B2. `core:ai` — Android client
- Ktor/Retrofit + kotlinx.serialization, timeout (tag 10s, outfit 15s), 1 retry với backoff.
- `ClaudeClothingTagger`, `ClaudeOutfitGenerator` trả `Result<…>` — **không throw ra ngoài**.
- Validate lại phía client (an toàn kép), map tên màu về bảng màu chuẩn.
- Cache tag theo SHA-256 ảnh (bảng nhỏ trong DataStore/file riêng của `core:ai`, không đụng `core:database`).
- `BuildConfig.PROXY_URL` qua `local.properties`/gradle property.

### B3. `eval/` — đo chất lượng
- 30–50 ảnh quần áo thật (của nhóm) + nhãn đúng (`labels.json`).
- Script chạy qua proxy, xuất độ chính xác theo field (category, màu chính, formality ±1).
- 10 kịch bản phối đồ (tủ đồ cố định × thời tiết × dịp) → nhóm chấm 1–5 ⇒ dùng làm bằng chứng chất lượng khi thuyết trình.
- Prompt lưu thành file version (`proxy/src/prompts/*.ts`), mỗi lần đổi chạy lại eval.

## Ngoài phạm vi
Fallback rule-based (C), UI (A/C).

## Task & thứ tự
1. Viết `proxy-api.md` cùng Sprint 0 (nếu chưa)
2. Worker skeleton + `/health` + deploy (lấy URL sớm cho cả nhóm)
3. `/v1/tag` + schema + test → eval tagging
4. `/v1/outfits` + validation + test → eval phối đồ
5. `core:ai` client + thay stub → báo A, C
6. Rate-limit, budget cap, tối ưu độ trễ (prompt caching cho system prompt)

## Definition of Done
- [ ] Tag đúng category ≥ 90%, màu chính ≥ 80% trên bộ eval
- [ ] 100 request outfit liên tiếp: 0 JSON hỏng tới client, 0 item id lạ
- [ ] p90 latency đạt mục tiêu
- [ ] Grep APK (`apktool`) không thấy key; repo không có secret
- [ ] Unit test proxy + client, coverage ≥ 80%

## Rủi ro
- Wifi chặn domain workers.dev tại nơi demo ⇒ chuẩn bị hotspot 4G; có thể gắn custom domain.
- Chi phí tăng khi beta ⇒ budget cap/ngày, Haiku cho tagging, cache.
