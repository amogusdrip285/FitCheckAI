# B-001 — Runbook deploy proxy lên Cloudflare Workers

> Owner: B · 13/11/2026. Mục tiêu: có endpoint **HTTPS public** `https://fitcheck-proxy.<subdomain>.workers.dev` cho app (A/D) và INT-002.
> Cloudflare Workers **free plan** là đủ (bundle đã build thử: 1,9 MB, **317 KiB gzip**). Không tạo tài nguyên trả phí.
> Không dán key/mã truy cập vào chat, git, issue hay ảnh chụp.

## Trạng thái deploy

**13/11/2026 (bản 3, version `91d53b85`): deploy từ bản đã merge `garmentType` của A** — `/health` báo `recognitionGarmentTypeVersion: 1`; smoke 8/8 PASS; ảnh sơ mi flannel có `garmentTypeVersion` ⇒ `garmentType: SHIRT` + tag `áo sơ mi` (14,5s), không có ⇒ `áo sơ mi` (11,7s); tủ UUID 20 món 200, 5 bộ (13,7s).

**13/11/2026 (bản 2, version `915bac2c`): deploy lại với `outfits-v2.2` + `recognition-v2.2`** — smoke 8/8 PASS; tủ UUID 6/20 món 200, 5 bộ (12,8s/16,0s); ảnh sơ mi flannel thật ⇒ `áo sơ mi`, CHECKED, RED/BLACK (11,3s).

**13/11/2026: đã deploy lần đầu** `https://fitcheck-proxy.long06hehe.workers.dev` (giai đoạn FOSTA, `gpt-6-luna`, cả hai service bật, deadline proxy 60s/65s).
- `proxy/scripts/smoke.mjs --live`: **8/8 PASS** (live outfits 9,9s).
- Đo thêm trên bản deploy: outfits tủ 6 món/5 bộ 23,6–25,6s (2/2 OK); recognition FRONT 17,7s, cặp khớp 20,0s, cặp chéo 23,5s (cảnh báo khác món đúng).
- Repo `gradle.properties` đã đặt `fitcheck.proxyUrl` = URL trên và `fitcheck.aiTimeoutMs=60000`; ViewModel nhận diện (A) dùng chung `AiTimeouts.requestMs`. Mỗi máy build chỉ cần thêm `fitcheck.accessCode=<mã demo>` vào `~/.gradle/gradle.properties` (mã gửi qua kênh riêng). Lưu ý: mã được nhúng vào APK debug — chỉ là bảo vệ mức demo (BACK-011).

## Hai giai đoạn

| Giai đoạn | Provider | Khi nào |
|---|---|---|
| **Tạm thời (hiện tại)** | `gpt-6-luna` qua gateway **FOSTA** | Từ giờ tới lúc pitching — key OpenAI chỉ có lúc pitching (quyết định 13/11/2026). Chỉ dữ liệu/ảnh test |
| **Pitching / demo** | OpenAI chính chủ `gpt-6-luna` | Khi có key OpenAI: đổi secret + bỏ biến FOSTA, redeploy (mục 7) |

Repo giữ cấu hình của giai đoạn pitching (`OPENAI_BASE_URL = ""`). Giai đoạn tạm **ghi đè lúc deploy** bằng `--var`, không sửa file.

## 0. Cần có trước

| Thứ | Ai lo | Ghi chú |
|---|---|---|
| Account Cloudflare (free) | nhóm — người giữ account | Tạo tại dash.cloudflare.com; bật subdomain `workers.dev` ở mục Workers & Pages |
| Key FOSTA (giai đoạn tạm) | người đang giữ key FOSTA | Không có spend limit công khai ⇒ giữ daily cap của proxy; đổi key nếu đã lộ |
| Key OpenAI chính chủ + spend limit | người giữ account OpenAI — **lúc pitching** | Đề xuất spend limit $10 ([PRE-003](PRE-003-provider-budget.md)) |
| Node 20+, repo đã `npm install` trong `proxy/` | người deploy | |

## 1. Đăng nhập (người deploy tự làm — cần trình duyệt)

```bash
cd proxy
npx wrangler login      # mở trình duyệt, đăng nhập Cloudflare, bấm Allow
npx wrangler whoami     # kiểm đúng account
```

## 2. Đặt secrets (người deploy tự gõ giá trị — wrangler hỏi, không hiện ra màn hình)

```bash
npx wrangler secret put OPENAI_COMPAT_API_KEY   # giai đoạn tạm: key FOSTA
npx wrangler secret put SESSION_SECRET          # chuỗi ngẫu nhiên ≥32 ký tự (tạo lệnh dưới)
npx wrangler secret put DEMO_ACCESS_CODE        # mã demo đưa cho A/D, chỉ gồm chữ, số, . _ -
```

Tạo `SESSION_SECRET` ngẫu nhiên: `node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"`.
Proxy chỉ gửi `OPENAI_COMPAT_API_KEY` tới gateway (`OPENAI_BASE_URL` khác `api.openai.com`) và `OPENAI_API_KEY` tới OpenAI chính chủ — không dùng chéo.

## 3. Deploy lần đầu với dịch vụ TẮT (không tốn tiền)

`wrangler.toml` mặc định `AI_PROVIDER = "openai"`, `OPENAI_BASE_URL = ""` (OpenAI chính chủ), `SERVICE_* = "off"`.
Giai đoạn tạm ghi đè gateway và nới deadline vì FOSTA mất 15–40s/call:

```bash
npx wrangler deploy --dry-run --outdir /tmp/fitcheck-bundle   # kiểm build, không upload
npx wrangler deploy --var OPENAI_BASE_URL:https://codex.fosta.app/v1 --var PROVIDER_TIMEOUT_MS:60000 --var PROXY_DEADLINE_MS:65000
```

Lệnh deploy in ra URL `https://fitcheck-proxy.<subdomain>.workers.dev`. **Lần deploy nào cũng phải lặp lại các `--var` này** cho tới khi chuyển sang OpenAI chính chủ.

Smoke test khi dịch vụ còn tắt (chỉ kiểm session/auth/kill switch, **không gọi AI**):

```bash
FITCHECK_ACCESS_CODE=<mã demo> node proxy/scripts/smoke.mjs --base-url https://fitcheck-proxy.<subdomain>.workers.dev
```

Kỳ vọng: `/health` 200, mã sai 401, mã đúng có token, không token 401, các route AI trả 503 (kill switch).

## 4. Bật dịch vụ và smoke live (tốn ~1 call, ≈ $0,0002 với `gpt-6-luna`)

Bật cả hai dịch vụ bằng thêm `--var` (không cần sửa file):

```bash
npx wrangler deploy --var OPENAI_BASE_URL:https://codex.fosta.app/v1 --var PROVIDER_TIMEOUT_MS:60000 --var PROXY_DEADLINE_MS:65000 --var SERVICE_RECOGNITION:on --var SERVICE_OUTFITS:on
FITCHECK_SMOKE_TIMEOUT_MS=70000 FITCHECK_ACCESS_CODE=<mã demo> node proxy/scripts/smoke.mjs --base-url https://fitcheck-proxy.<subdomain>.workers.dev --live
```

Kỳ vọng `8/8 PASS`. `/health.providers` phải ghi `openai@codex.fosta.app` (consent nêu đúng gateway). Ghi latency đo được vào PRE-003 mục 7.
Xem log từng request (requestId, status, latency, model, token; không có ảnh/PII): `npx wrangler tail`.

## 5. Giao cho A/D (INT-002)

- URL: `https://fitcheck-proxy.<subdomain>.workers.dev` ⇒ đặt trong `~/.gradle/gradle.properties`:
  `fitcheck.proxyUrl=https://…workers.dev`, `fitcheck.accessCode=<mã demo>` (recommendation; nhận diện ảnh nhập mã trên màn hình) và,
  **trong giai đoạn FOSTA**, `fitcheck.aiTimeoutMs=60000` để client app chờ đủ lâu (mặc định 20000 sẽ timeout phần lớn call).
- `fitcheck.aiTimeoutMs` áp cho client gợi ý phối đồ và client nhận diện trong `core:ai` (`AiTimeouts.requestMs`). ViewModel nhận diện của A
  (`feature/wardrobe` `WardrobeViewModels`) còn hằng `withTimeoutOrNull(20_000)` riêng — A cần đổi sang `AiTimeouts.requestMs` thì nhận diện qua FOSTA mới không bị cắt ở 20s.
- Gửi mã demo qua kênh riêng của nhóm, không qua repo/issue.
- Eval lại trên OpenAI chính chủ (PRE-003, B-004/B-006): `node eval/ai/run-recommendation.mjs --base-url <URL> --confirm-spend` (xem [eval/README](../../eval/README.md)).

## 6. Chuyển sang OpenAI chính chủ lúc pitching

```bash
npx wrangler secret put OPENAI_API_KEY          # key OpenAI chính chủ
npx wrangler secret delete OPENAI_COMPAT_API_KEY # bỏ key FOSTA khỏi Worker
npx wrangler deploy --var SERVICE_RECOGNITION:on --var SERVICE_OUTFITS:on   # KHÔNG còn --var OPENAI_BASE_URL / deadline nới
FITCHECK_ACCESS_CODE=<mã demo> node proxy/scripts/smoke.mjs --base-url https://fitcheck-proxy.<subdomain>.workers.dev --live
```

`/health.providers` phải đổi thành `openai@api.openai.com` (consent cũ tự hết hiệu lực). Nếu latency trên OpenAI chính chủ < 15s thì bỏ `fitcheck.aiTimeoutMs` ở app (về 20000). Chạy lại eval B-004/B-006 trên OpenAI chính chủ.

## 7. Tắt / thu hồi

| Mức | Làm gì |
|---|---|
| Tắt một dịch vụ | `SERVICE_<X> = "off"` rồi `npx wrangler deploy` (hoặc sửa biến trên dashboard) |
| Đổi mã demo | `npx wrangler secret put DEMO_ACCESS_CODE` (token cũ vẫn sống tới hạn `SESSION_TTL_SECONDS`) |
| Vô hiệu mọi token ngay | `npx wrangler secret put SESSION_SECRET` với giá trị mới |
| Dừng tốn tiền tuyệt đối | Xoá/rotate key trên console OpenAI |
| Gỡ hẳn Worker | `npx wrangler delete` |

Giới hạn thật: daily cap (200 call/service) và rate limit (20/phút/session) đếm **theo isolate**, best-effort — trần tiền thật là spend limit ở console OpenAI. Try-on (`/v2/tryon`) **chưa mount** — xem [review C-207](C-207-mount-review.md).
