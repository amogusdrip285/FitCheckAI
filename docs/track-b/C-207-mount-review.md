# Review của B: gắn route try-on của C vào proxy (C-207)

> Owner review: B (giữ `proxy/src/index.ts`, auth, limits). Ngày 13/11/2026, trên `main` `f96712f`.
> Phạm vi: chỉ **ranh giới tích hợp** giữa `proxy/src/tryon/**` (Track C) và entry của B. Không review chất lượng ảnh VTO.

## Kết luận

**Chưa mount.** Lý do quy trình: chính `handler.ts` ghi "Track B mounts this router once B-001 & C-205 GO are ready" mà gate C-205 đang **BLOCKED**. Ngoài ra còn các điểm kỹ thuật dưới đây phải xong trước khi mount. Code của C chạy độc lập tốt: 37 vitest try-on pass trong bộ 163 test proxy.

## Điểm phải xử lý trước khi mount

| # | Vấn đề | Ai | Đề xuất |
|---|---|---|---|
| 1 | **Trùng `OPENAI_API_KEY`**: B dùng nó cho gateway FOSTA (`OPENAI_BASE_URL`), C gửi nó tới `api.openai.com` ⇒ key FOSTA có thể bị gửi nhầm sang OpenAI. | B | **Đã sửa (13/11):** B dùng `OPENAI_COMPAT_API_KEY` cho gateway, `OPENAI_API_KEY` chỉ cho OpenAI chính chủ, không fallback chéo; có test. |
| 2 | **Session tự khai**: router đọc `context.sessionId` *hoặc* header `x-session-id` do client gửi. Mount sau auth của B thì header này cho phép tự xưng phiên. | C | Bỏ fallback header; chỉ nhận `sessionId` từ B (`ctx.session`). |
| 3 | **Hình dạng route**: C export `createTryOnRouter().handle(Request, env, ctx)`, không phải `RouteDef[]` (`handle(body, ctx)`). | B + C | B thêm adapter mỏng hoặc C export `tryonRoutes: RouteDef[]` (đúng [proxy-api.md](../contracts/proxy-api.md#mount-route-của-track-khác-track-c--try-on)). Chốt một cách trước khi code. |
| 4 | **Deadline**: app của B cắt mọi request ở `proxyDeadlineMs` (mặc định 18s); C cần 120s (front) / 180s (dual). | B | **Đã làm (13/11):** `RouteDef.deadlineMs?` (kẹp 1s–300s), recognition/outfits giữ 18s; test `proxy/test/routes.test.ts`. Lưu ý Worker có giới hạn CPU/thời gian — job dài nên trả `202` + poll, không giữ request mở. |
| 5 | **Lưu job**: `InMemoryTryOnJobStore` không bền trong Workers (mỗi isolate một bộ nhớ). Code đã tự chặn AI_LIVE khi store không bền. | C | Cần Durable Object/KV có quyết định ngân sách (PRE-003) trước khi bật live. |
| 6 | **Hai lớp kill switch / cap**: B có `SERVICE_TRYON` + daily cap (`reserveUpstreamCall`); C có `VTO_KILL_SWITCH`, `VTO_MODE`, `VTO_BUDGET_CAP_UNITS`, `TRYON_LIVE_*`. | B + C | Giữ cả hai; route C gọi `ctx.reserveUpstreamCall()` ngay trước provider để daily cap của B có hiệu lực. |
| 7 | **Body cap**: try-on cần ≤12 MiB wire. | B | `maxBodyBytes` riêng của route — seam đã hỗ trợ. |

## Việc B đã làm trong lượt này

- Tách key gateway/OpenAI (mục 1), cập nhật `.dev.vars.example`, `wrangler.toml`, test.
- Deadline riêng từng route (mục 4).
- Chưa sửa code của C (mục 2, 5 thuộc C).
