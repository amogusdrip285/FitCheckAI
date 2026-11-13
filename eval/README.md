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
Nhãn recognition lấy từ fixtures A trong repo: `node eval/ai/tools/labels-from-track-a.mjs --fixtures data/fixtures/modern-clothing`,
rồi chạy với `--labels labels.track-a.json --images-dir data/fixtures/modern-clothing`.
Provider chậm: script cắt mỗi request ở 20s như app; nới bằng `FITCHECK_EVAL_TIMEOUT_MS=130000` **cùng lúc** với
`PROVIDER_TIMEOUT_MS`/`PROXY_DEADLINE_MS` trong `proxy/.dev.vars`, và ghi rõ trong report là đã nới.

## Đọc kết quả

- Invariant (ID trong tủ, revision, slot, số lượng, trùng bộ, signature, envelope lỗi) phải **100% PASS**.
- Chất lượng giải thích: 2 reviewer (B và A) điền cột điểm trong report theo thang 1–5 ở `docs/execution/CONTRACTS.md` → "Protocol đánh giá". Đạt khi ≥3/5 ở cả hai tiêu chí, cả hai reviewer, cho ít nhất 3 case live (đánh dấu `live≥3`).
- Model/token/chi phí: response không chứa usage — xem `npx wrangler tail` (log JSON theo `requestId`).
- Mẫu nhỏ ⇒ ghi denominator và giới hạn; không suy tỷ lệ production, p90 latency, độ chính xác material/fit.
- Đổi prompt (`proxy/src/*/prompt.ts`, tăng `*_PROMPT_VERSION`) ⇒ chạy lại và commit report mới.
- Token/latency từng call: log Worker (dòng JSON có `inputTokens`, `outputTokens`, `latencyMs` theo `requestId`).

## Kết quả hiện có

| Ngày | Provider / model | B-004 recommendation | B-006 recognition |
|---|---|---|---|
| 11/11/2026 | Gemini `gemini-3.5-flash-lite` (free tier) | 8/10 case, invariant 0 lỗi | [25 case](ai/results/2026-11-11-B-006-summary.md): 25/25/25 sau review nhãn |
| 12/11/2026 | `gpt-6-luna` qua gateway FOSTA, deadline nới | 8/10 case, invariant 0 lỗi; **A và B chấm 17/17 bộ ≥3/5** | 24/25 category, 23/25 màu, 21/25 pattern |

Tổng hợp: [luna 12/11](ai/results/2026-11-12-luna-summary.md). Chưa có lượt chạy trên OpenAI chính chủ (chờ key, PRE-003) và trên prompt `outfits-v2.2` / `recognition-v2.2`. Lưu ý: case eval dùng ID ngắn (`t1`…); app gửi UUID — lỗi UUID của luna chỉ lộ khi chạy với UUID (đã sửa ở v2.2), nên lượt eval sau nên thêm case ID là UUID.
