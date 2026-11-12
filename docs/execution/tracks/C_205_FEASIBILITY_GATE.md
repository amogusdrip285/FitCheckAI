# C-205 — Báo cáo Feasibility Gate và Quyết định Phạm vi Virtual Try-On

- **Task ID:** `C-205`
- **Track / Owner:** Track C — Virtual Try-On (phối hợp review với Track B về cost/protocol, Track A về rubric hình ảnh, Track D về release mode)
- **Ngày ghi nhận:** `2026-11-11`
- **Nguồn chuẩn đối chiếu:** [`docs/execution/VTO_FEASIBILITY.md`](../VTO_FEASIBILITY.md), [`C_201_PROVIDER_RESEARCH.md`](C_201_PROVIDER_RESEARCH.md), [`C_202_DATASET_READINESS.md`](C_202_DATASET_READINESS.md)
- **Trạng thái task C-205:** `IMPLEMENTED` & `TESTED` (Gate document hoàn tất trung thực theo quy tắc `VTO_FEASIBILITY.md`: *"C-205 DONE có thể là BLOCKED/NO_GO. M4-F chỉ FRONT/DUAL PASS khi kết quả đo tương ứng đạt"*).

---

## 1. Quyết định Chính thức Hiện hành (Gate Decision)

> ### **DECISION: `BLOCKED`**
> - **Ánh xạ sang runtime `TryOnCapability.decision`:** `FeasibilityDecision.UNKNOWN`
> - **Ánh xạ sang `runtimeVerifiedViews`:** `emptySet()`
> - **Trạng thái Milestone `M4-F` & `M4-R`:** **CHƯA PASS** (Không giả mạo `GO_FRONT_ONLY` hay `GO_DUAL` khi chưa có 3 case đo thực tế trên API thật và 2 reviewer chấm điểm).

---

## 2. Bảng Hồ sơ Evidence chuẩn theo `VTO_FEASIBILITY.md`

| Trường | Nội dung ghi nhận |
|---|---|
| **Gate** | `C-205`, Revision `v1.0` (`2026-11-11`), Owner: Track C (chờ đồng kiểm B/A/D khi mở live). Quyết định: **`BLOCKED`** do chưa cấp API credentials, chưa duyệt trần chi phí live, chưa xác nhận `PRE-001` cho pre-event live calls, và chưa nạp 3 bộ ảnh tình nguyện viên thật có consent ngoài Git. |
| **Capability (từ `C-201`)** | Ứng viên chính: **FASHN AI** (`tryon-v1.6` mặc định tốc độ/chi phí, `tryon-max` tùy chọn `1k balanced`), `return_base64: true`. Categories tài liệu hỗ trợ: `TOP`, `BOTTOM`, `DRESS` (`UNSUPPORTED` giày/phụ kiện trên `tryon-v1.6`). `maxGarmentsPerJob = 1` mỗi pass chuẩn; **KHÔNG có native paired multi-view** (`supportsNativePairedViews = false` — nếu chạy trước+sau phải gửi 2 request độc lập và đo consistency thực tế). Dự phòng: **Google Cloud Vertex AI** `virtual-try-on-001` (`$0.06/ảnh`, lịch ngừng hỗ trợ `2027-03-15`). |
| **Input (`C-202`)** | Đã hoàn thiện `technical_fixtures_manifest.json` (3 positive structural cases: `TOP`, `DRESS`, `TOP+BOTTOM+SHOES` + 6 negative cases) cho kiểm thử kỹ thuật offline (`eligibleForLiveQualityGate = false`). Bộ 3 cặp ảnh người thật (`subject-01..03`) kèm consent đã ký ngoài Git hiện **chưa có**. |
| **Measurements (`C-203`/`C-204`)** | Harness `eval/tryon/poc_runner.py` đã sẵn sàng và vượt toàn bộ unit tests trên `FakeVtoProviderAdapter`. Số lần gọi live thực tế hiện tại: **`0 attempts`**, chi phí đã tiêu: **`$0.00` / `0 credits`**. |
| **Quality** | Chưa chấm điểm live (`NOT RUN` trên dữ liệu thật). Đã khóa sẵn rubric trước thử nghiệm: 3/3 case phải đạt `≥ 4/5` từ cả 2 reviewer độc lập (C và A) cho Identity, Silhouette/Pose, Garment Fidelity, Coverage/Provenance (và Pair Consistency `≥ 4/5` nếu xét `GO_DUAL`); không lấy điểm trung bình bù case hỏng. |
| **Privacy** | Đã xác minh chính sách FASHN: không dùng ảnh/output để train model; chế độ `return_base64: true` không đẩy kết quả lên CDN 72h và tự hết hạn sau 60 phút; không có API xóa tức thời phía server nên `remoteDeletionState` báo trung thực `BASE64_EPHEMERAL_60M` (hoặc `NOT_UPLOADED` khi chặn local). Toàn bộ cổng thu hồi consent & chặn late-result đã được hiện thực hóa ở `C-210`. |
| **Product Impact** | 1. Ứng dụng ở chế độ mặc định phát hành an toàn theo **`CORE` mode**: màn hình Try-On hiển thị trung thực trạng thái tính năng chưa bật live (hoặc gắn nhãn rõ `BẢN XEM TRƯỚC MÔ PHỎNG (FAKE / DEV)` trong môi trường phát triển).<br>2. Core journey (Profile → Wardrobe → Today gợi ý 1–5 outfit) hoàn toàn không bị chặn.<br>3. Tuyệt đối không dùng ảnh flat-lay/collage/mannequin để tuyên bố đã đạt Virtual Try-On, và không hứa độ vừa vặn/kích cỡ ngoài đời. |

---

## 3. Trần Ngân sách & Độ trễ Đề xuất (Khóa trước khi mở lại C-203 / C-204)

- **Latency target (Quality/Demo Gate):**
  - `FRONT`: `≤ 60s` / case từ lúc submit tới lúc nhận đủ ảnh.
  - `DUAL` (2 request độc lập tuần tự/song song): `≤ 120s` / cặp hoàn chỉnh.
  - *(Technical timeout bảo vệ treo kết nối theo `CT-07` vẫn giữ `120s` cho front và `180s` cho pair).*
- **Budget cap đề xuất cần phê duyệt:**
  - Giai đoạn POC (`C-203` + `C-204`): tối đa **`13 credits`** trên `tryon-v1.6` (~`$0.98`) hoặc **`26 credits`** trên `tryon-max` `1k balanced` (~`$1.95`), cứng ngắt qua cờ `--max-attempts` và `--budget-usd` trong `eval/tryon/poc_runner.py`.

---

## 4. Điều kiện Mở lại (Reopen Criteria) để nâng từ `BLOCKED` lên `GO_FRONT_ONLY` / `GO_DUAL` / `NO_GO`

1. **Xác nhận thể lệ (`PRE-001` — Track B):** BTC *Codex Community Hackathon Hanoi* cho phép chạy thử nghiệm trong cửa sổ hiện tại (hoặc bước vào cửa sổ thi chính thức ngày `14/11/2026`).
2. **Cấp khóa & Ngân sách (`PRE-003` — User / Track B+C):** Cung cấp `FASHN_API_KEY` vào biến môi trường máy chủ/local (ngoài Git) và phê duyệt trần credit ở Mục 3.
3. **Nạp dữ liệu có Consent (`C-202` — Nhóm):** Đặt 3 cặp ảnh tình nguyện viên + quần áo thật và 3 file JSON consent vào `~/.cache/fitcheckai-private/tryon-poc/`.
4. **Thực thi & Chấm điểm (`C-203` → `C-204`):**
   - Nếu `C-203` đạt 3/3 case `≥ 4/5` (cả 2 reviewer) nhưng `C-204` trượt consistency mặt lưng hoặc multi-garment làm hỏng món đầu → Chốt **`GO_FRONT_ONLY`** (chỉ bật mặt trước, giới hạn 1 garment hoặc dress, ghi rõ `unappliedItemIds`).
   - Nếu cả `C-203` và `C-204` đều đạt 3/3 cặp `≥ 4/5` trong ngân sách và độ trễ → Chốt **`GO_DUAL`** (vẫn ghi rõ `supportsNativePairedViews = false` nếu dùng 2 request độc lập).
   - Nếu ngay cả `C-203` trượt chất lượng hoặc vượt ngân sách/quyền riêng tư → Chốt **`NO_GO`**, giữ nguyên `CORE` mode.
