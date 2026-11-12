# PRE-003 — Provider và ngân sách AI (recognition + recommendation)

> Owner: B · Trạng thái: **DRAFT — BLOCKED ở phần account/budget** cho tới khi nhóm điền các ô `TODO(nhóm)`.
> Không lưu secret trong file này. Không gọi API có phí khi lập tài liệu. Ngân sách VTO của C tách riêng.

## 1. Yêu cầu kỹ thuật với provider (từ CT-02/05/07)

| Yêu cầu | Vì sao | Cách proxy đang dùng |
|---|---|---|
| Vision nhiều ảnh trong một request | Recognition gửi FRONT + BACK | 2 image block base64 |
| Structured output theo JSON schema | Output parse được, enum hợp lệ | `output_config.format` (zod → JSON schema) |
| Tắt auto-retry, timeout ≤15s | Không tốn tiền ngầm, CT-07 deadline | SDK `maxRetries: 0`, `timeout: 15000` |
| Spend limit thật ở phía provider | Daily cap của proxy chỉ best-effort | **TODO(nhóm)**: đặt trên console |
| Không train trên dữ liệu gửi lên / retention rõ | Ảnh garment rời máy người dùng | **TODO(nhóm)**: kiểm điều khoản account |

## 2. Ứng viên (tối đa 2 — theo card)

Proxy hiện có adapter **Anthropic** (`proxy/src/shared/model.ts`). Đổi provider = viết adapter mới cùng interface `ModelClient`; route/validator/test giữ nguyên.

| | A. Claude Opus 5.5 (`claude-opus-5-5`) — mặc định hiện tại | B. Claude Haiku 5.5 (`claude-haiku-5-5`) — phương án rẻ |
|---|---|---|
| Giá niêm yết / 1M token (in / out) | $4 / $20 | $0.10 / $0.50 (prompt ≤100K token) |
| Vision nhiều ảnh, structured output | Có | Có |
| Ghi chú | Chất lượng giải thích tốt hơn dự kiến; đắt hơn ~40× | Cần đo chất lượng giải thích theo rubric B-004 trước khi chọn |
| Đổi sang | mặc định | `AI_MODEL = "claude-haiku-5-5"` trong `wrangler.toml` |

Giá lấy từ bảng giá Anthropic (bản cache 2026-11-03) — **kiểm lại trên trang giá chính thức trước khi chốt**.

### Giai đoạn test: Gemini free tier (quyết định 11/11/2026)

Để phát triển/test không tốn tiền, proxy có thêm adapter **Gemini** (`AI_PROVIDER = "gemini"`, model mặc định `gemini-3.5-flash-lite`, có free tier theo trang giá Google AI Studio kiểm ngày 11/11/2026).

- Đo ngày 11/11/2026 với key free tier của nhóm: `gemini-3.8-flash` timeout >40s / HTTP 503 ngay cả với request tối thiểu; `gemini-3.5-flash-lite` trả 200 trong ~1–3s qua proxy; `gemini-2.5-flash` trả 404 (không còn cho người dùng mới). ⇒ mặc định flash-lite. Có thể thử lại 3.8-flash bằng `AI_MODEL` khi hết quá tải.
- Request format `generateContent` + `responseJsonSchema` đã xác nhận chạy live (eval B-004 lần đầu, xem `eval/ai/results/`).

- **Privacy:** ở free tier, Google ghi "Used to improve our products: Yes" ⇒ chỉ gửi ảnh garment test nhóm tự chụp, **không** gửi ảnh/dữ liệu người dùng thật. Demo với người dùng thật cần chuyển sang tier trả phí hoặc provider khác.
- **Giới hạn:** free tier có rate limit thấp (trang giá không ghi số cụ thể) ⇒ có thể gặp `429 RATE_LIMITED`; eval chạy tuần tự, ít call.
- **Evidence:** kết quả eval phải ghi provider/model thật đã dùng. Chất lượng đo trên Gemini **không** chứng minh chất lượng trên Claude và ngược lại; đổi provider ⇒ chạy lại B-004/B-006.

### Giai đoạn pitching: gateway OpenAI-compatible FOSTA (quyết định 12/11/2026)

Nhóm muốn demo/pitching bằng model GPT nên proxy có thêm adapter **`openai`** (`proxy/src/shared/openai.ts`) gọi Chat Completions chuẩn OpenAI; FOSTA dùng qua `OPENAI_BASE_URL = https://codex.fosta.app/v1`, model mặc định `gpt-6-luna` (đổi từ `gpt-6.1-sol` ngày 12/11/2026 để tiết kiệm: đo được luna nhanh ngang sol — recognition 18.9–19.8s vs 19.1–19.9s, schema nhỏ 9.0s vs 8.6–10.4s — vì phần lớn độ trễ nằm ở gateway; chất lượng giải thích của luna **chưa chấm** theo rubric B-004; FOSTA còn có `gpt-6.1-sol`, `gpt-6-astra`; tên đầy đủ trong `/v1/models` có tiền tố `cx/`). Key nằm ở `.dev.vars`/`wrangler secret` — không APK, không git.

- **Không phải OpenAI trực tiếp.** FOSTA là gateway bên thứ ba: ảnh garment và metadata tủ đồ đi qua máy chủ của họ. Chưa có điều khoản retention/training/giá công khai được nhóm đọc ⇒ chỉ dùng ảnh test (vd. `data/fixtures/modern-clothing`), không ảnh người dùng thật. `/health` ghi `provider = "openai@codex.fosta.app"` để consent không gọi nhầm là OpenAI.
- **Đo ngày 12/11/2026** (laptop dev → FOSTA, mỗi cấu hình 1–3 lần, mẫu nhỏ):
  - `response_format: json_schema` **bị bỏ qua** (trả văn bản tự do) ⇒ adapter dùng function calling bắt buộc (`tool_choice` + `strict`). Tool strict vẫn có lúc bỏ key nullable (`sameItem`) ⇒ adapter điền `null` cho key nullable bị thiếu, key bắt buộc khác vẫn bị zod chặn.
  - Vision chạy: ảnh áo thun in hình nhận đúng `TOP`, `BLACK/WHITE/RED`, `PRINTED`.
  - `/v2/outfits` qua proxy local: 3/3 lần 200 trong **10.4–13.3s**, 1 bộ hợp lệ + shortfall đúng fixture CT-02.
  - `/v2/recognition`: model mất **~19–25s** (sol/luna, effort low/none, ảnh 1024px hay gốc đều vậy) ⇒ **vượt deadline provider 15s** ⇒ qua proxy trả `504 UPSTREAM_TIMEOUT` 2/2 lần. Muốn dùng recognition qua FOSTA phải nới deadline CT-07 (provider/proxy/client + `withTimeoutOrNull(20_000)` ở A) — **quyết định nhóm, chưa đổi**.
- **Chạy song song (quyết định 12/11/2026, tạm cho tới khi test UI trên app thật):** outfits = GPT qua FOSTA, recognition = Gemini (`AI_PROVIDER=openai`, `AI_PROVIDER_RECOGNITION=gemini`). Đo cùng ngày qua proxy local: outfits 2/3 lần 200 (13.4–14.3s), 1 lần 504 ở mốc 15s; recognition qua Gemini 2/2 lần 504. Gọi thẳng Gemini: text 2.5–6.8s; recognition 1 ảnh 1/3 OK (8.2s), 2/3 HTTP 503 ⇒ free tier đang quá tải. **Hiện chưa cấu hình nào ổn định dưới deadline 15s**; xem lại khi test UI thật.
- **Toàn bộ luna + nới deadline để test (quyết định 12/11/2026, chiều):** `AI_MODEL=gpt-6-luna` cho cả hai service, `PROVIDER_TIMEOUT_MS=120000` / `PROXY_DEADLINE_MS=125000` chỉ trong `.dev.vars` (mặc định code/wrangler vẫn 15000/18000). Lý do: khi chạy thật nhóm sẽ dùng API OpenAI trực tiếp; mục tiêu bây giờ là kiểm model trả **đúng**, không phải nhanh.
  - FOSTA rất thất thường cùng buổi: "Xin chao" luna 10.5s, sol 46.6s; outfits tủ 6 món luna 2/2 lần timeout ở 60s, gọi thẳng luna >180s và sol 115s → 502; ~30 phút sau lại chạy tốt ⇒ độ trễ do gateway, không suy ra được từ model.
  - Lần chạy tốt qua proxy local (limit 120s): outfits tủ 6 món xin 5 bộ — **2/2 lần 200, 5 bộ hợp lệ, không shortfall, 23.4–24.8s**, giải thích tiếng Việt cụ thể theo màu/hoạ tiết/dịp; recognition luna — FRONT 17.8s, cặp khớp 21.6s, cặp chéo 24.5s, cả 3 nhận đúng `TOP / BLACK,WHITE,RED / PRINTED`; cặp chéo có `VIEWS_LOOK_LIKE_DIFFERENT_ITEMS` + mọi field cần review ✔.
  - Đây là smoke test (n nhỏ), chưa phải eval B-004/B-006 có reviewer chấm.
- Chất lượng/latency đo trên FOSTA không chứng minh cho OpenAI trực tiếp hay provider khác; đổi provider ⇒ chạy lại B-004/B-006.

## 3. Ước lượng chi phí mỗi call (THÔ — phải thay bằng số đo usage thật ở B-004/B-006)

Giả định: ảnh derivative ≤1024px cạnh dài ⇒ ~1.4K token/ảnh (≈ rộng×cao/750); system prompt ~0.6–0.8K token; output gồm cả thinking.

| Call | Input ước tính | Output ước tính | Opus 5.5 | Haiku 5.5 | Trần do `max_tokens` (Opus) |
|---|---|---|---|---|---|
| Recognition, 1 ảnh | ~2.2K | ~0.5–1K | ~$0.02–0.03 | <$0.001 | 4K out ⇒ ≤ ~$0.09 |
| Recognition, 2 ảnh | ~3.6K | ~0.5–1K | ~$0.03–0.04 | <$0.001 | ≤ ~$0.10 |
| Recommendation, 20 món, 5 bộ | ~2.5K | ~1–3K | ~$0.03–0.07 | ~$0.002 | 8K out ⇒ ≤ ~$0.17 |

## 4. Ngân sách đề xuất (nhóm quyết)

| Hạng mục | Số call | Opus 5.5 (ước) | Haiku 5.5 (ước) |
|---|---|---|---|
| B-004: 10 case recommendation live + chạy lại 1 lần | ~20 | ~$1.5–3.5 | <$0.1 |
| B-006: 3 case recognition live + chạy lại | ~6 | ~$0.3 | <$0.01 |
| Dev/smoke + INT-002 | ~50 | ~$2–4 | <$0.2 |
| Demo + diễn tập | ~100 | ~$4–7 | <$0.3 |
| **Tổng đề xuất** | ~180 | **~$8–15 → đặt spend limit $20** | **~$1 → spend limit $5** |

## 5. Quyết định cần chốt — TODO(nhóm)

| # | Câu hỏi | Trả lời | Người chịu trách nhiệm | Ngày |
|---|---|---|---|---|
| 1 | Nhóm đã có account Anthropic (hoặc provider khác) dùng được ngay chưa? Có credit/thẻ không? | | | |
| 2 | Model cho recognition / recommendation (có thể khác nhau sau khi đo) | | | |
| 3 | Spend limit đặt trên console là bao nhiêu? Đã đặt chưa (ảnh chụp màn hình lưu riêng, không có key)? | | | |
| 4 | Ai giữ API key; key chỉ nằm ở `wrangler secret` | | | |
| 5 | Account Cloudflare để deploy Worker (free plan đủ: bundle ~314 KB gzip) | | | |
| 6 | Điều khoản dữ liệu/retention của account đã đọc và chấp nhận cho ảnh garment | | | |

Khi chưa có (1), (3), (5): B-001 live, B-004, B-006, INT-002 ở trạng thái **BLOCKED**; mock/test vẫn chạy (89 test). Không nhúng key vào APK để "chạy tạm".

## 6. Kill switch và vận hành

- Tắt nhanh: đặt `SERVICE_RECOGNITION` / `SERVICE_OUTFITS` = `"off"` rồi `wrangler deploy` (hoặc sửa biến trên dashboard Cloudflare).
- Tắt tuyệt đối: xoá/rotate API key trên console provider.
- Daily cap mặc định 200 call/service/isolate — **không** phải trần tiền.
