# B-004 + B-006 — Eval lần 2 trên `gpt-6-luna` qua FOSTA (12/11/2026)

Nguồn chi tiết: [`2026-11-12T12-48-27-674-recommendation.md`](2026-11-12T12-48-27-674-recommendation.md) và [`2026-11-12T12-57-44-896-recognition.md`](2026-11-12T12-57-44-896-recognition.md).

| | |
|---|---|
| Provider / model | `openai@codex.fosta.app` (gateway bên thứ ba) · `gpt-6-luna` cho cả recommendation và recognition |
| Proxy | `wrangler dev` local, prompt `outfits-v2.0` / `recognition-v2.1`, deadline **nới để test**: provider 120s, proxy 125s, client eval 130s (`FITCHECK_EVAL_TIMEOUT_MS`). Mặc định CT-07 vẫn 15/18/20s |
| Dữ liệu | Cùng 10 case recommendation (`eval/ai/cases/recommendation.json`) và cùng 21 món / 3 cặp fixtures Track A (`data/fixtures/modern-clothing`, nhãn `labels.track-a.json` — manifest A đã gồm review nhãn của B) như lần Gemini 11/11 |

## B-004 — recommendation

- 8/10 case chạy (2 case "unit" đã phủ bằng test proxy), **invariant FAIL 0**; 6 call AI; guard tủ rỗng/chỉ TOP chặn trước AI.
- R05 tủ đủ ⇒ 5 bộ khác tập món; R03/R04 trả 1 bộ + `NOT_ENOUGH_DISTINCT_COMBINATIONS`; R06 focus váy đỏ ⇒ mọi bộ chứa váy; R09 freeText cố ghi đè luật vẫn đúng slot; R10 giải thích ghi rõ thời tiết là dữ liệu cũ.
- Latency AI 12.5–33.0s (Gemini 11/11: 1.3–3.0s). Chỉ đo được vì đã nới deadline; với 15s mặc định, 5/6 case sẽ timeout.
- Văn phong: cụ thể theo màu/hoạ tiết/dịp; **nhược điểm thấy được**: vài câu lộ trường nội bộ ("độ trang trọng 3", "độ trang trọng 4") — nên sửa prompt nếu giữ luna.
- **Chưa đạt B-004**: thiếu điểm của 2 reviewer B và A (bảng `__ / __` trong report).

## B-006 — recognition

| Đo trên 25 case | luna 12/11 | Gemini flash-lite 11/11 (cùng nhãn) |
|---|---|---|
| Category | 24/25 | 25/25 |
| Màu chính có trong dự đoán | 23/25 | 25/25 |
| Pattern | 21/25 | 25/25 |
| Cặp chéo có `VIEWS_LOOK_LIKE_DIFFERENT_ITEMS` + mọi field review | ✔ | ✔ |
| Guard thiếu consent (403, không gọi AI) | ✔ | ✔ |
| Latency | 14.3–42.8s | 1.8–7.2s |

Chỗ luna lệch nhãn:

- Category: sơ mi flannel kẻ đỏ-đen — nhãn OUTERWEAR, luna TOP (ca biên đã biết: TOP/OUTERWEAR).
- Màu: váy hoạ tiết nhãn BEIGE/PURPLE/GREEN, luna GRAY/PINK/PURPLE; cặp chéo trả `colors` rỗng (mọi field đã đánh dấu review — chấp nhận được).
- Pattern (4): quần kẻ sọc → OTHER; giày xanh trơn → STRIPED; giày trắng trơn → OTHER; phụ kiện in hình → SOLID.

**Đọc đúng con số:** nhãn hiện tại đã được B review *sau khi xem dự đoán của Gemini* (11/11) ⇒ so sánh nghiêng về Gemini; 25/25 của Gemini không phải accuracy độc lập. Kết luận được: luna nhận đúng loại đồ gần như toàn bộ, hay sai **pattern trên giày/phụ kiện**, và chậm hơn nhiều qua FOSTA. Muốn so công bằng cần ảnh mới, gán nhãn trước khi chạy model. Mẫu nhỏ, không phải tỷ lệ production.
