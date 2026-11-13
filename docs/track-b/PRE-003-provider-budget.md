# PRE-003 — Provider và ngân sách AI (recognition + recommendation)

> Owner: B · Cập nhật 13/11/2026 · Trạng thái: **ĐỀ XUẤT của B — BLOCKED ở account/ngân sách** cho tới khi nhóm xác nhận mục 5.
> Không lưu secret trong file này. Ngân sách VTO của C tách riêng. Không gọi API có phí khi lập tài liệu này (giá đọc từ trang chính thức, token lấy từ log các lần eval đã chạy).

## Tóm tắt đề xuất

| | Đề xuất |
|---|---|
| Provider cho pitching, demo và chạy thật | **OpenAI chính chủ** (`api.openai.com`), model **`gpt-6-luna`** cho cả recognition và recommendation — **không qua FOSTA** (quyết định nhóm 13/11/2026) |
| Dự phòng | `gpt-6.1-sol` nếu chất lượng luna không đủ; Gemini `gemini-3.5-flash-lite` (free tier, **chỉ dữ liệu test**) nếu chưa có key OpenAI |
| FOSTA (gateway bên thứ ba) | **Dùng tạm từ giờ tới lúc pitching** vì key OpenAI chỉ có lúc pitching (13/11/2026): deploy với `OPENAI_BASE_URL` = FOSTA + `OPENAI_COMPAT_API_KEY`, deadline nới (proxy 60s, app `fitcheck.aiTimeoutMs=60000`). Không dùng khi pitching, chỉ ảnh/dữ liệu test. Cách chuyển: [runbook B-001](B-001-deploy-runbook.md#6-chuyển-sang-openai-chính-chủ-lúc-pitching) |
| Ngân sách dev + eval + demo | ước **< $1** với luna (~$5 với sol) ⇒ đặt **spend limit $10** trên console OpenAI |
| Còn chặn | account OpenAI + spend limit + người giữ key; account Cloudflare để deploy proxy HTTPS; đo lại latency trên OpenAI chính chủ (app cắt ở 20s) |

## 1. Yêu cầu kỹ thuật với provider (CT-02/05/07)

| Yêu cầu | Vì sao | Proxy đang làm |
|---|---|---|
| Vision nhiều ảnh trong một request | Recognition gửi FRONT + BACK | 2 image part base64 (data URL với `openai`) |
| Structured output theo JSON schema | Output parse được, enum hợp lệ | `openai`: function calling bắt buộc (`tool_choice` + `strict`); `gemini`: `responseJsonSchema`; `anthropic`: `output_config.format`. Validator ngữ nghĩa luôn chạy sau |
| Không auto-retry, có deadline | Không tốn tiền ngầm (CT-07) | `maxRetries: 0`; provider 15s / proxy 18s mặc định, chỉnh bằng `PROVIDER_TIMEOUT_MS` / `PROXY_DEADLINE_MS`; route có thể khai `deadlineMs` riêng |
| Spend limit thật phía provider | Daily cap của proxy chỉ best-effort theo isolate | **Cần nhóm đặt trên console** (mục 5) |
| Không train trên dữ liệu gửi lên / retention rõ | Ảnh garment rời máy người dùng | **Cần nhóm đọc điều khoản account** (mục 5) |
| Key chỉ ở server | Không nằm trong APK/git/log | `wrangler secret` / `.dev.vars` (gitignore). `OPENAI_API_KEY` = OpenAI chính chủ (dùng chung với C), `OPENAI_COMPAT_API_KEY` = gateway, không dùng chéo |

## 2. Ma trận provider/model (tối đa 2 ứng viên chính theo card)

Giá: trang giá chính thức OpenAI `developers.openai.com/api/docs/pricing`, đọc 13/11/2026, standard tier, ngữ cảnh ≤272K token, USD / 1M token. **Kiểm lại trước khi chốt.**

| | A. `gpt-6-luna` — **đề xuất** | B. `gpt-6.1-sol` — dự phòng chất lượng |
|---|---|---|
| Giá input / cached input / output | $0.10 / $0.01 / $0.50 | $2.00 / $0.10 / $10.00 |
| Vision nhiều ảnh | Có — đã chạy cặp FRONT+BACK (qua FOSTA) | Có (qua FOSTA) |
| Structured output | Qua FOSTA: tool strict chạy, `response_format` bị gateway bỏ qua. **Chưa kiểm trên OpenAI chính chủ** | như A |
| Chất lượng đo được | B-004: invariant 0 lỗi, 2 reviewer chấm 17/17 bộ ≥3/5 (A 3,94/3,94; B 3,94/3,82). B-006: category 24/25, màu 23/25, pattern 21/25 | Chưa eval đầy đủ |
| Latency đo được | Qua FOSTA 12–43s/call — **do gateway** (cùng prompt, sol không nhanh hơn); chưa đo trên OpenAI chính chủ | Qua FOSTA tương tự hoặc chậm hơn |
| Đổi sang | `AI_PROVIDER=openai`, `AI_MODEL=gpt-6-luna` (mặc định của adapter) | `AI_MODEL=gpt-6.1-sol` hoặc `AI_MODEL_<SERVICE>` |

Provider khác có sẵn adapter trong proxy, không phải ứng viên chính:

- **Gemini** `gemini-3.5-flash-lite` (free tier): 1,3–7,2s/call ngày 11/11, B-006 25/25/25 (nhãn review sau khi xem Gemini nên nghiêng về Gemini). Free tier: Google ghi "Used to improve our products: Yes" và hay 503 khi quá tải (12/11: 2/3 call lỗi) ⇒ **chỉ test**.
- **Anthropic** (`claude-opus-5-5` mặc định adapter): giữ code + test, nhóm không có kế hoạch account.

## 3. Chi phí mỗi call — từ token thật

Token lấy từ log Worker của eval luna 12/11/2026 (`inputTokens` / `outputTokens` do FOSTA trả về; OpenAI chính chủ có thể đếm token ảnh/reasoning khác ⇒ dùng hệ số an toàn ×5 khi lập ngân sách).

| Call (n đo) | Input token (min · trung vị · max) | Output token | luna / call | sol / call | Trần do `max_tokens` (luna · sol) |
|---|---|---|---|---|---|
| Recommendation (6) | 674 · 982 · 998 | 56 · 170 · 225 | ~$0.0002 | ~$0.004 | 8 000 out ⇒ ≤ $0.004 · ≤ $0.08 |
| Recognition 1 ảnh (21) | 1 946 | 112 · 129 · 144 | ~$0.0003 | ~$0.005 | 4 000 out ⇒ ≤ $0.002 · ≤ $0.044 |
| Recognition 2 ảnh (4) | ~3 057 | ~140 | ~$0.0004 | ~$0.0075 | ≤ $0.002 · ≤ $0.046 |

Cách tính: input × giá input + output × giá output (không tính cache). Ví dụ recommendation luna: 982 × $0.10/1M + 170 × $0.50/1M ≈ $0.00018.

## 4. Ngân sách đề xuất (nhóm quyết)

| Hạng mục | Số call | luna (ước ×5 an toàn) | sol (ước ×5 an toàn) |
|---|---|---|---|
| Eval lại trên OpenAI chính chủ: B-004 (6 AI case × 2) + B-006 (25 case) | ~40 | ~$0.06 | ~$1.0 |
| Dev/smoke + INT-002 (A/D thử trên máy) | ~60 | ~$0.08 | ~$1.4 |
| Demo + diễn tập | ~100 | ~$0.15 | ~$2.3 |
| **Tổng** | ~200 | **~$0.3** | **~$4.7** |
| Trần tuyệt đối nếu mọi call chạm `max_tokens` | ~200 | ≤ $0.8 | ≤ $16 |

⇒ Đề xuất **spend limit $10** trên console OpenAI (đủ cho luna dư nhiều lần, đủ cho sol ở mức dùng thật; trần tuyệt đối sol vượt $10 chỉ khi mọi call hỏng ở mức tối đa). Daily cap proxy giữ `200` call/service/isolate.

## 5. Quyết định cần chốt (DEC-03/04)

| # | Câu hỏi | Đề xuất của B | Người chịu trách nhiệm | Trạng thái |
|---|---|---|---|---|
| 1 | Account provider dùng được ngay, có thẻ/credit? | Tạm FOSTA (đã có key) tới pitching; OpenAI chính chủ lấy key **lúc pitching** | nhóm | FOSTA: sẵn; OpenAI: chờ pitching |
| 2 | Model recognition / recommendation | `gpt-6-luna` cho cả hai; đổi riêng từng service bằng `AI_MODEL_<SERVICE>` nếu cần | B | Đề xuất, chờ nhóm |
| 3 | Spend limit trên console | $10, ảnh chụp lưu riêng (không có key) | người giữ account — TODO | **BLOCKED** |
| 4 | Ai giữ API key | 1 người; key chỉ ở `wrangler secret put OPENAI_API_KEY` | nhóm — TODO | **BLOCKED** |
| 5 | Account Cloudflare deploy Worker | free plan; bundle nhỏ | nhóm — TODO | **BLOCKED** |
| 6 | Điều khoản dữ liệu/retention | đọc điều khoản API OpenAI cho ảnh garment trước khi gửi ảnh thật | người giữ account — TODO | **BLOCKED** |
| 7 | Deadline app (20s) với latency thật | đo trên OpenAI chính chủ trước; nếu >15s thì A/D/B thống nhất nới deadline phía app | B đo, A/D quyết | Chờ (1)–(5) |

Khi chưa có (1), (3), (4), (5): B-001 live, INT-002 vẫn **BLOCKED**; mock, fake, fallback RULES và proxy local vẫn chạy. Không nhúng key vào APK để "chạy tạm".

## 6. Kill switch và vận hành

- Tắt nhanh: `SERVICE_RECOGNITION` / `SERVICE_OUTFITS` = `"off"` rồi `wrangler deploy` (hoặc sửa biến trên dashboard Cloudflare).
- Tắt tuyệt đối: xoá/rotate API key trên console provider.
- Daily cap mặc định 200 call/service/isolate — **không** phải trần tiền.
- Đổi provider/model ⇒ `/health.providers` đổi ⇒ consent nhận diện cũ tự hết hiệu lực; chạy lại B-004/B-006.

## 7. Nhật ký đo đạc

### Gemini free tier (11/11/2026)

- `gemini-3.8-flash` timeout >40s / HTTP 503 kể cả request tối thiểu; `gemini-3.5-flash-lite` 200 trong ~1–3s; `gemini-2.5-flash` 404 ⇒ mặc định flash-lite.
- `generateContent` + `responseJsonSchema` chạy live (eval B-004/B-006 lần 1, `eval/ai/results/2026-11-11*`).
- 12/11: text 2.5–6.8s; recognition 1 ảnh 1/3 OK (8.2s), 2/3 HTTP 503.

### FOSTA (12/11/2026) — gateway bên thứ ba, không phải OpenAI trực tiếp

- `/v1/models` liệt kê `cx/gpt-6-luna`, `cx/gpt-6.1-sol`, `cx/gpt-6-astra`… (nhận cả tên không tiền tố). Chưa có điều khoản retention/training/giá công khai ⇒ chỉ ảnh test.
- `response_format: json_schema` bị bỏ qua ⇒ adapter dùng function calling bắt buộc; tool strict vẫn có lúc bỏ key nullable (`sameItem`) ⇒ adapter điền `null` cho key nullable bị thiếu.
- Rất thất thường cùng một buổi: "Xin chao" luna 10.5s, sol 46.6s; outfits tủ 6 món luna 2/2 timeout ở 60s, gọi thẳng luna >180s, sol 115s → 502; ~30 phút sau chạy tốt.
- Lần chạy tốt (deadline nới 120s): outfits 5 bộ hợp lệ 23–25s; recognition 18–24s, cặp chéo có cảnh báo đúng.
- Eval đầy đủ trên luna: [`eval/ai/results/2026-11-12-luna-summary.md`](../../eval/ai/results/2026-11-12-luna-summary.md).

### Prompt

- `outfits-v2.1` (13/11/2026): không ghi số/tên trường nội bộ ("độ trang trọng 3") trong giải thích — cả hai reviewer cùng trừ điểm lỗi này ở lượt `outfits-v2.0`. **Chưa eval lại trên v2.1.**
