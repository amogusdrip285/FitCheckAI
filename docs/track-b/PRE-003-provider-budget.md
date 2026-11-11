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
