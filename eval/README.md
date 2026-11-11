# eval/ — đo chất lượng AI (OWNER: Track B)

Eval chạy **qua proxy** (`/v2/outfits`, `/v2/recognition`) — đo đúng thứ app sẽ nhận, gồm cả validator của server.
Bộ kiểm invariant trong `ai/lib/invariants.mjs` viết **độc lập** với code proxy để bắt cả lỗi validator.

```
eval/ai/
├─ cases/recommendation.json     # B-004: 10 case cố định (guard / ai / unit) + tủ đồ metadata tổng hợp
├─ recognition/labels.example.json  # B-006: mẫu nhãn — copy thành labels.json (gitignored)
├─ images/                       # ảnh garment PRE-004 (gitignored, không commit)
├─ results/                      # report .md (cột chấm điểm để reviewer điền) + .json
├─ run-recommendation.mjs
└─ run-recognition.mjs
```

## Chạy

Cần Node 20+. Proxy chạy local (`cd proxy && npm run dev`, có `proxy/.dev.vars`) hoặc URL đã deploy.

```bash
# PowerShell: $env:FITCHECK_ACCESS_CODE="..." ; bash: export FITCHECK_ACCESS_CODE=...
node eval/ai/run-recommendation.mjs --base-url http://127.0.0.1:8787
node eval/ai/run-recognition.mjs --base-url http://127.0.0.1:8787
```

Mặc định **không tốn tiền**: chỉ chạy case `guard` (proxy phải chặn trước AI) và liệt kê case AI là `NOT_RUN`.
Thêm `--confirm-spend` để gọi model thật — chỉ khi PRE-003 đã có account + spend limit + budget được duyệt.
Giới hạn số call: `--max-ai-calls N`. Chạy một phần: `--only R05,R06`. Recognition đo theo field mọi món: `--all-front`.

## Đọc kết quả

- Invariant (ID trong tủ, revision, slot, số lượng, trùng bộ, signature, envelope lỗi) phải **100% PASS**.
- Chất lượng giải thích: 2 reviewer (B và A) điền cột điểm trong report theo thang 1–5 ở `docs/execution/CONTRACTS.md` → "Protocol đánh giá". Đạt khi ≥3/5 ở cả hai tiêu chí, cả hai reviewer, cho ít nhất 3 case live (đánh dấu `live≥3`).
- Model/token/chi phí: response không chứa usage — xem `npx wrangler tail` (log JSON theo `requestId`).
- Mẫu nhỏ ⇒ ghi denominator và giới hạn; không suy tỷ lệ production, p90 latency, độ chính xác material/fit.
- Đổi prompt (`proxy/src/*/prompt.ts`, tăng `*_PROMPT_VERSION`) ⇒ chạy lại và commit report mới.
