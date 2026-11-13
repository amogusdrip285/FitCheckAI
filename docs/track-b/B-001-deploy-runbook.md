# B-001 — Runbook deploy proxy lên Cloudflare Workers

> Owner: B · 13/11/2026. Mục tiêu: có endpoint **HTTPS public** `https://fitcheck-proxy.<subdomain>.workers.dev` cho app (A/D) và INT-002.
> Cloudflare Workers **free plan** là đủ (bundle đã build thử: 1,9 MB, **317 KiB gzip**). Không tạo tài nguyên trả phí.
> Không dán key/mã truy cập vào chat, git, issue hay ảnh chụp.

## 0. Cần có trước

| Thứ | Ai lo | Ghi chú |
|---|---|---|
| Account Cloudflare (free) | nhóm — người giữ account | Tạo tại dash.cloudflare.com; bật subdomain `workers.dev` ở mục Workers & Pages |
| Key OpenAI chính chủ + spend limit | người giữ account OpenAI | Đề xuất spend limit $10 ([PRE-003](PRE-003-provider-budget.md)) |
| Node 20+, repo đã `npm install` trong `proxy/` | người deploy | |

## 1. Đăng nhập (người deploy tự làm — cần trình duyệt)

```bash
cd proxy
npx wrangler login      # mở trình duyệt, đăng nhập Cloudflare, bấm Allow
npx wrangler whoami     # kiểm đúng account
```

## 2. Đặt secrets (người deploy tự gõ giá trị — wrangler hỏi, không hiện ra màn hình)

```bash
npx wrangler secret put OPENAI_API_KEY      # key OpenAI chính chủ
npx wrangler secret put SESSION_SECRET      # chuỗi ngẫu nhiên ≥32 ký tự (tạo lệnh dưới)
npx wrangler secret put DEMO_ACCESS_CODE    # mã demo đưa cho A/D, chỉ gồm chữ, số, . _ -
```

Tạo `SESSION_SECRET` ngẫu nhiên: `node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"`.
**Không** đặt `OPENAI_COMPAT_API_KEY`/key FOSTA trên bản deploy — pitching dùng OpenAI chính chủ.

## 3. Deploy lần đầu với dịch vụ TẮT (không tốn tiền)

`wrangler.toml` mặc định `AI_PROVIDER = "openai"`, `OPENAI_BASE_URL = ""` (OpenAI chính chủ), `SERVICE_* = "off"`.

```bash
npx wrangler deploy --dry-run --outdir /tmp/fitcheck-bundle   # kiểm build, không upload
npx wrangler deploy                                          # in ra URL https://fitcheck-proxy.<subdomain>.workers.dev
```

Smoke test khi dịch vụ còn tắt (chỉ kiểm session/auth/kill switch, **không gọi AI**):

```bash
FITCHECK_ACCESS_CODE=<mã demo> node proxy/scripts/smoke.mjs --base-url https://fitcheck-proxy.<subdomain>.workers.dev
```

Kỳ vọng: `/health` 200, mã sai 401, mã đúng có token, không token 401, các route AI trả 503 (kill switch).

## 4. Bật dịch vụ và smoke live (tốn ~1 call, ≈ $0,0002 với `gpt-6-luna`)

Sửa `proxy/wrangler.toml`: `SERVICE_RECOGNITION = "on"`, `SERVICE_OUTFITS = "on"` (không commit thay đổi này nếu nhóm muốn mặc định tắt), rồi:

```bash
npx wrangler deploy
FITCHECK_ACCESS_CODE=<mã demo> node proxy/scripts/smoke.mjs --base-url https://fitcheck-proxy.<subdomain>.workers.dev --live
```

Kỳ vọng `8/8 PASS`. Nếu live > 15s: script cảnh báo — ghi số đo vào PRE-003 mục 7 và báo A/D (app cắt ở 20s).
Xem log từng request (requestId, status, latency, model, token; không có ảnh/PII): `npx wrangler tail`.

## 5. Giao cho A/D (INT-002)

- URL: `https://fitcheck-proxy.<subdomain>.workers.dev` ⇒ đặt trong `~/.gradle/gradle.properties`:
  `fitcheck.proxyUrl=https://…workers.dev` và `fitcheck.accessCode=<mã demo>` (recommendation); nhận diện ảnh nhập mã trên màn hình.
- Gửi mã demo qua kênh riêng của nhóm, không qua repo/issue.
- Eval lại trên OpenAI chính chủ (PRE-003, B-004/B-006): `node eval/ai/run-recommendation.mjs --base-url <URL> --confirm-spend` (xem [eval/README](../../eval/README.md)).

## 6. Tắt / thu hồi

| Mức | Làm gì |
|---|---|
| Tắt một dịch vụ | `SERVICE_<X> = "off"` rồi `npx wrangler deploy` (hoặc sửa biến trên dashboard) |
| Đổi mã demo | `npx wrangler secret put DEMO_ACCESS_CODE` (token cũ vẫn sống tới hạn `SESSION_TTL_SECONDS`) |
| Vô hiệu mọi token ngay | `npx wrangler secret put SESSION_SECRET` với giá trị mới |
| Dừng tốn tiền tuyệt đối | Xoá/rotate key trên console OpenAI |
| Gỡ hẳn Worker | `npx wrangler delete` |

Giới hạn thật: daily cap (200 call/service) và rate limit (20/phút/session) đếm **theo isolate**, best-effort — trần tiền thật là spend limit ở console OpenAI. Try-on (`/v2/tryon`) **chưa mount** — xem [review C-207](C-207-mount-review.md).
