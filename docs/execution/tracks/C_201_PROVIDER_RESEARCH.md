# C-201 — Khảo sát VTO Provider, Quyền Dùng và Privacy

- **Task ID:** `C-201`
- **Track / Owner:** Track C — Virtual Try-On
- **Ngày kiểm tra tài liệu:** `2026-11-11`
- **Trạng thái thực thi:** `IMPLEMENTED` & `TESTED` (Desk review hoàn tất từ nguồn chính thức; **0 cuộc gọi API trả phí** được thực hiện trong bước nghiên cứu này)

---

## 1. Bối cảnh phần cứng cục bộ & Loại trừ suy diễn local GPU

Trước khi so sánh dịch vụ đám mây và mô hình mã nguồn mở tự host, phần cứng máy phát triển hiện tại đã được kiểm tra thực nghiệm:

- **Hệ điều hành:** Ubuntu 24.04 LTS (`x86_64`)
- **RAM / Disk:** 31 GiB RAM, 223 GiB trống trên NVMe
- **GPU:** `Intel Corporation Raptor Lake-P [Intel Graphics]` — **KHÔNG có GPU rời NVIDIA / CUDA** (`nvidia-smi` không tồn tại).

### Đánh giá nhanh nhóm mô hình mở tự chạy (`CatVTON` & `IDM-VTON`)

| Mô hình | Giấy phép (License) | Yêu cầu VRAM / Phần cứng | Hỗ trợ nhiều món / Mặt sau | Kết luận cho FitCheckAI |
|---|---|---|---|---|
| **CatVTON** (`github.com/Zheng-Chong/CatVTON`) | `CC BY-NC-SA 4.0` (Phi thương mại, ShareAlike) — `DOCUMENTED` | `< 8 GB VRAM` CUDA ở `1024×768` (SD1.5 inpainting + DensePose/SCHP) — `DOCUMENTED` | Thiết kế cho 1 garment mỗi lượt, ưu tiên góc chính diện (frontal); ảnh lưng dễ lỗi parsing/ảo giác mặt trước — `DOCUMENTED` | **LOẠI TRỪ CHO LOCAL RUNTIME**: Không có GPU NVIDIA trên máy dev; giấy phép `NC-SA` hạn chế mở rộng thương mại (`Scorings.MD` tiêu chí 3). |
| **IDM-VTON** (`github.com/yisol/IDM-VTON`) | `CC BY-NC-SA 4.0` (Phi thương mại) — `DOCUMENTED` | `12–16+ GB VRAM` CUDA (SDXL + GarmentNet + IP-Adapter) — `DOCUMENTED` | Chia pipeline đơn chiếc (`upper_body`, `lower_body`, `dresses`); thiên hướng frontal VITON-HD/DressCode — `DOCUMENTED` | **LOẠI TRỪ CHO LOCAL RUNTIME**: Vượt khả năng phần cứng local (không có CUDA GPU) và vướng `CC BY-NC-SA 4.0`. |

> **Kết luận hạ tầng:** Không tự huấn luyện diffusion model và không dựng cụm GPU riêng. Tập trung đánh giá sâu **2 ứng viên API thương mại chính**: **FASHN AI API (`tryon-v1.6` & `tryon-max`)** và **Google Cloud Vertex AI Virtual Try-On (`virtual-try-on-001`)**.

---

## 2. Nguồn tài liệu chính thức đã xác minh (`2026-11-11`)

1. **FASHN AI Official API & SDK Reference** (kiểm tra ngày `2026-11-11`, đối chiếu SDK `fashn@0.15.0` & tài liệu `docs.fashn.ai`):
   - Prediction API (`POST https://api.fashn.ai/v1/run`, `GET https://api.fashn.ai/v1/status/{id}`, `GET https://api.fashn.ai/v1/credits`): `https://docs.fashn.ai/api-reference/tryon-max`, `https://docs.fashn.ai/guides/tryon-parameters-guide`
   - Data Retention & Privacy: `https://docs.fashn.ai/api-overview/data-retention-privacy`
   - Pricing & Credits: `https://fashn.ai/pricing#api`
   - Official Agent Skill Reference: `https://github.com/fashn-AI/fashn-skill` (`skills/fashn/SKILL.md`, `skills/fashn/reference.md`)
2. **Google Cloud Vertex AI Virtual Try-On Documentation** (kiểm tra ngày `2026-11-11`):
   - Capability & Model Guide: `https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/generate-virtual-try-on-images`
   - REST Schema (`VirtualTryOnModelInstance`): `https://docs.cloud.google.com/gemini-enterprise-agent-platform/reference/rest/Shared.Types/VirtualTryOnModelInstance`
   - Vertex AI Pricing & Model Lifecycle (`virtual-try-on-001` trên `aiplatform.googleapis.com`): tài liệu Vertex AI Generative Vision API.

---

## 3. Ma trận năng lực chi tiết (Capability Matrix)

Quy ước nhãn bằng chứng nghiêm ngặt:
- `DOCUMENTED`: Được nêu rõ trong tài liệu chính thức của nhà cung cấp (kèm điều kiện).
- `MEASURED`: Đã đo thực nghiệm trên bộ test của dự án (hiện tại **chưa có mục nào `MEASURED` live** vì chưa gọi API trả phí).
- `UNKNOWN`: Tài liệu không cam kết hoặc chưa kiểm chứng thực nghiệm trên dữ liệu thật của nhóm.
- `UNSUPPORTED`: Tài liệu xác nhận không hỗ trợ trong endpoint chuẩn.

| Hạng mục đánh giá | Ứng viên 1A: FASHN `tryon-v1.6` | Ứng viên 1B: FASHN `tryon-max` | Ứng viên 2: Google Vertex AI `virtual-try-on-001` |
|---|---|---|---|
| **Trạng thái model (`2026-11-11`)** | `DOCUMENTED` — Đang hoạt động (`model_name: "tryon-v1.6"`), tối ưu tốc độ & chi phí (864×1296, ~1MP). | `DOCUMENTED` — Flagship hiện hành (`model_name: "tryon-max"`), độ phân giải `1k` / `2k` / `4k`. | `DOCUMENTED` — Đang hoạt động nhưng có lịch **deprecation / shutdown vào `2027-03-15`**. |
| **Giao thức API & Job Lifecycle** | `DOCUMENTED` — Async job: `POST /v1/run` trả `id` ngay → poll `GET /v1/status/{id}` (`starting` → `in_queue` → `processing` → `completed` \| `failed`). Hỗ trợ webhook (không chữ ký HMAC, nên ưu tiên server poll). | `DOCUMENTED` — Cùng giao thức async `POST /v1/run` + `GET /v1/status/{id}` với `tryon-v1.6`. | `DOCUMENTED` — Synchronous HTTP prediction: `POST .../models/virtual-try-on-001:predict` (proxy vẫn cần bọc thành async/job contract `CT-06` cho client Android). |
| **Đầu vào ảnh người (`Person Image`)** | `DOCUMENTED` — `model_image` (HTTPS URL hoặc `data:image/...;base64,...`). Có kiểm tra pose (`PoseError` nếu không phát hiện dáng người). | `DOCUMENTED` — `model_image` (URL hoặc data-URI base64). | `DOCUMENTED` — `instances[].personImage` (`bytesBase64Encoded` hoặc `gcsUri`). Tối ưu cho ảnh người đứng thẳng, nhìn rõ dáng. |
| **Bảo toàn danh tính & dáng người thực tế** | `UNKNOWN` (chưa `MEASURED`) — Docs nêu giữ pose/identity nhưng kết quả thực tế trên người Việt/ảnh tự chụp phải qua rubric C-203. | `UNKNOWN` (chưa `MEASURED`) — Docs nêu bảo toàn identity/pose tốt hơn v1.6, nhưng bắt buộc đo qua C-203. | `UNKNOWN` (chưa `MEASURED`) — Docs nêu giữ người và ánh sáng, nhưng bắt buộc đo qua C-203. |
| **Danh mục trang phục hỗ trợ (`Categories`)** | `DOCUMENTED`: `tops`, `bottoms`, `one-pieces` (`auto` mặc định). `UNSUPPORTED`: giày, mũ, túi, trang sức riêng lẻ. | `DOCUMENTED`: quần áo (`tops`, `bottoms`, `one-pieces`) **VÀ** mở rộng sang `shoes`, `bags`, `hats`, `jewelry` qua `product_image` + `prompt`. | `DOCUMENTED`: áo (`tops`), quần/váy (`bottoms`), đầm (`dresses`). `UNSUPPORTED`: giày dép, kính, mũ, trang sức, phụ kiện. |
| **Số món đồ mỗi request (`Multi-garment`)** | `UNSUPPORTED` cho native multi-item trong 1 call (`garment_image` là 1 ảnh đơn). Muốn thử `TOP + BOTTOM` phải gọi tuần tự 2 pass (rủi ro trôi chất lượng pass 1 — hiện `UNKNOWN`). | `UNSUPPORTED` trong schema chuẩn `tryon-max` (`product_image` là 1 ảnh đơn). Endpoint `agent/images` là luồng agentic khác, chưa kiểm chứng deterministic. Sequential 2-pass hiện `UNKNOWN`. | `UNSUPPORTED` cho full multi-item outfit chuẩn (mỗi lần predict thiết kế xoay quanh 1 món quần áo mục tiêu; sequential 2-pass là `UNKNOWN`). |
| **Số biến thể vs Số góc nhìn (`Samples != Views`)** | `DOCUMENTED` — `num_samples: 1..4` chỉ sinh nhiều biến thể cùng góc nhìn của `model_image`, **KHÔNG** sinh nhiều góc nhìn trước/sau. | `DOCUMENTED` — `num_images: 1..4` chỉ sinh biến thể cùng góc nhìn, **KHÔNG** phải multi-view. | `DOCUMENTED` — `sampleCount: 1..4` chỉ sinh biến thể cùng góc nhìn, **KHÔNG** phải multi-view. |
| **Thử đồ mặt trước (`Front-view VTO`)** | `DOCUMENTED` (cần `MEASURED` ở C-203). | `DOCUMENTED` (cần `MEASURED` ở C-203). | `DOCUMENTED` (cần `MEASURED` ở C-203). |
| **Thử đồ mặt sau từ ảnh tham chiếu thật (`Back-view`)** | `UNKNOWN` — API không cấm gửi `personBack` + `garmentBack`, nhưng `PoseError` hoặc thiên kiến mặt trước có thể xảy ra; chưa được docs cam kết riêng. | `UNKNOWN` — Có thể truyền `personBack` + `garmentBack` kèm `prompt`, nhưng độ trung thực mặt lưng chưa được cam kết chính thức, cần C-204 đo. | `UNKNOWN` — Docs khuyến nghị ảnh chính diện (front-facing); khả năng xử lý `personBack` + `garmentBack` chưa được cam kết. |
| **Cặp trước/sau nhất quán (`Native Paired Consistency`)** | `UNSUPPORTED` — Không có endpoint nhận đồng thời `(personFront, personBack, garmentFront, garmentBack)` để sinh cặp đồng bộ. Hai lần gọi độc lập có consistency `UNKNOWN`. | `UNSUPPORTED` cho native joint paired generation. Hai job độc lập không bảo đảm đồng nhất nếp gấp/độ dài/ánh sáng (`UNKNOWN` cho tới khi đo C-204). | `UNSUPPORTED` cho native joint paired generation. Hai request độc lập có consistency `UNKNOWN`. |
| **Độ trễ ước tính (`Latency`)** | `DOCUMENTED` — `performance` ~5–7s, `balanced` ~8–10s, `quality` ~12–19s (chưa gồm upload/queue spike). | `DOCUMENTED` — Khoảng `20–120s` tùy `generation_mode` (`balanced`\|`quality`) và `resolution` (`1k`\|`2k`\|`4k`). Có rủi ro vượt mục tiêu demo `≤60s`/view nếu chọn `quality`/`2k+`. | `DOCUMENTED` / `UNKNOWN` — Thường ~8–25s mỗi call đồng bộ tùy vùng/quota GCP, cần đo thực tế. |
| **Chi phí (`Pricing`)** | `DOCUMENTED` — `1 credit / ảnh` (~`$0.0488–$0.075` tùy gói; top-up tự phục vụ tối thiểu ~`$7.50`). **Job lỗi (`failed`) không trừ credit.** | `DOCUMENTED` — `balanced`: 2 credits (`1k`), 3 (`2k`), 4 (`4k`); `quality`: 3 (`1k`), 4 (`2k`), 5 (`4k`) (~`$0.10–$0.375` / ảnh). **Job lỗi không trừ credit.** | `DOCUMENTED` — `$0.06 / ảnh` sinh ra theo biểu giá pay-as-you-go Vertex AI. |
| **Hủy job (`Cancel`)** | `UNSUPPORTED` qua REST chuẩn (không có `DELETE/POST cancel` hủy tức thời trên hàng đợi FASHN; client/proxy chỉ hủy theo dõi cục bộ và không bảo đảm hoàn credit nếu job đã chạy). | `UNSUPPORTED` tương tự `tryon-v1.6`. | `UNSUPPORTED` (request đồng bộ, chỉ đóng kết nối HTTP nhưng không đảm bảo dừng tính phí phía upstream). |
| **Chính sách lưu trữ, huấn luyện & xóa từ xa (`Privacy & Deletion`)** | `DOCUMENTED` — **Không dùng ảnh/output của khách hàng để train model** mặc định. Output CDN tự xóa sau **72 giờ**; nếu bật **`return_base64: true`** thì ảnh **không lưu lên CDN**, chỉ tồn tại tạm trên endpoint status tối đa **60 phút** (`<base64>_expired`). **Không có API xóa tức thời từng job phía server** (`remoteDeletionState = BASE64_EPHEMERAL_60M` hoặc `EXPIRES_AUTOMATICALLY_72H`). | `DOCUMENTED` — Giống `tryon-v1.6` (`return_base64: true` là cấu hình tối ưu quyền riêng tư cho FitCheckAI). | `DOCUMENTED` — Chạy trong ranh giới Google Cloud Project (enterprise data governance, không dùng dữ liệu khách hàng trả phí để train mặc định nếu cấu hình chuẩn). Không cần GCS bucket nếu dùng `bytesBase64Encoded`, nhưng cần kiểm tra cấu hình abuse-monitoring/logging của project GCP cụ thể (`UNKNOWN` khi chưa có project). |
| **Yêu cầu tài khoản & Khả dụng hiện tại** | Cần tài khoản Developer tại `app.fashn.ai/api` + `FASHN_API_KEY` + nạp credit/trial. Hiện tại trong workspace: **CHƯA CÓ KEY**. | Giống `tryon-v1.6` (dùng chung `FASHN_API_KEY`). | Cần Google Cloud Project bật Billing + Vertex AI API + Service Account IAM + quota khu vực (`us-central1`). Hiện tại trong workspace: **CHƯA CÓ PROJECT/CREDENTIALS**. |

---

## 4. Khuyến nghị lựa chọn Provider cho Track C

### 4.1. Primary Provider khuyến nghị: **FASHN AI API (`tryon-v1.6` làm mặc định tốc độ/chi phí, `tryon-max` ở `1k + balanced` làm tùy chọn độ chi tiết cao)**

**Lý do kỹ thuật và sản phẩm:**
1. **Khớp tự nhiên với kiến trúc Async Job của `CT-06`**: `POST /v1/run` → `GET /v1/status/{id}` ánh xạ trực tiếp sang `submit()` → `observe()` mà không giữ kết nối HTTP dài dễ timeout trên Cloudflare Worker (`proxy/`).
2. **Bảo vệ quyền riêng tư vượt trội nhờ `return_base64: true`**:
   - Cho phép gửi ảnh đầu vào dạng `data:image/jpeg;base64,...` trực tiếp từ proxy lên FASHN (không cần tạo S3/GCS public hay signed URL cho ảnh cơ thể nhạy cảm của người dùng).
   - Khi bật `return_base64: true`, FASHN **không lưu ảnh kết quả lên CDN 72 giờ**, dữ liệu base64 tự hết hạn sau tối đa 60 phút, và FASHN cam kết không dùng dữ liệu để huấn luyện mô hình.
3. **Kiểm soát ngân sách minh bạch**:
   - Header `x-fashn-credits-used` và endpoint `GET /v1/credits` giúp proxy và POC runner ghi nhận chính xác từng credit tiêu thụ; các lần lỗi (`PoseError`, `PipelineError`) không bị trừ credit.
   - `tryon-v1.6` (`balanced`, ~8–10s, 1 credit ≈ `$0.05–$0.075`) nằm gọn trong mục tiêu độ trễ demo `≤ 60s` cho 1 view và `≤ 120s` cho 2 view tuần tự; trong khi `tryon-max` (`20–120s`, 2 credits ở `1k balanced`) có thể vượt ngưỡng 60s nếu hàng đợi đông, nên chỉ bật sau khi đo latency thực tế ở C-203.

### 4.2. Backup Provider khuyến nghị: **Google Cloud Vertex AI Virtual Try-On (`virtual-try-on-001`)**

**Vai trò dự phòng:**
- Nếu nhóm đã có sẵn Google Cloud credits từ chương trình sinh viên/hackathon (xác minh qua Track B `PRE-003`), có thể dùng adapter dự phòng gọi `virtual-try-on-001:predict` với `bytesBase64Encoded` (`$0.06/ảnh`, không cần GCS bucket công khai).
- **Lưu ý rủi ro:** Model `virtual-try-on-001` có lịch ngừng hoạt động vào `2027-03-15` và yêu cầu thiết lập IAM/OAuth2 phức tạp hơn trong Cloudflare Worker so với Bearer key của FASHN.

---

## 5. Đánh giá Quyền riêng tư (Privacy Assessment) & Ràng buộc Thiết kế

1. **Nguyên tắc dữ liệu tối thiểu (`Data Minimization`)**:
   - Chỉ gửi ảnh `personFront` (và `personBack` nếu và chỉ nếu người dùng chọn chế độ 2 góc nhìn VÀ capability cho phép) cùng các ảnh garment thuộc **1 outfit duy nhất đã chọn**.
   - Tuyệt đối không gửi toàn bộ tủ đồ, không gửi `heightCm`/`weightKg` hay UUID hồ sơ nội bộ lên VTO provider.
2. **Không dùng ảnh mặt trước giả làm mặt sau**:
   - Nếu thiếu `personBack` hoặc thiếu `garmentBack` của món đồ được chọn, hệ thống chặn ngay ở client/server với mã `NEEDS_INPUT` trước khi tốn phí API, đồng thời đề xuất chế độ **Chỉ mặt trước (`FRONT_ONLY`)**.
3. **Tính trung thực về xóa dữ liệu từ xa (`Honest Remote Deletion`)**:
   - Vì cả FASHN lẫn Vertex AI prediction không cung cấp API xóa tức thời một bản ghi inference vừa chạy trong bộ nhớ đệm ngắn hạn, UI và `DeletionReceipt` **bắt buộc** báo rõ:
     - Dữ liệu trên thiết bị (ảnh kết quả, bản sao upload tạm, job metadata) đã xóa ngay lập tức.
     - Phía nhà cung cấp AI: không lưu CDN công khai (khi dùng `return_base64: true`), bản đệm trạng thái tự động hết hạn tối đa sau 60 phút (hoặc 72 giờ nếu dùng CDN mode), không sử dụng để huấn luyện AI. Tuyệt đối không tuyên bố "đã xóa vĩnh viễn tức thì trên máy chủ đối tác".
4. **Giới hạn sản phẩm bắt buộc hiển thị trên UI**:
   - Ảnh đầu ra là mô phỏng AI 2D; không xác định kích cỡ (size), không đo cơ thể, không bảo đảm quần áo ngoài đời vừa vặn.
   - Khi chỉ áp dụng được 1 món đồ (`maxGarmentsPerJob = 1` cho `TOP` hoặc `DRESS`), UI phải ghi rõ **"Mô phỏng món: [Tên món]"** và liệt kê các món còn lại trong `unappliedItemIds` (ví dụ quần, giày, phụ kiện).

---

## 6. Ước tính Ngân sách cho POC & Runtime Gate (Đề xuất để Nhóm/User phê duyệt)

| Giai đoạn | Số lượng cuộc gọi tối đa đề xuất | Ước tính với FASHN `tryon-v1.6` (1 cr/ảnh) | Ước tính với FASHN `tryon-max` `1k balanced` (2 cr/ảnh) | Ước tính với Vertex AI (`$0.06`/ảnh) |
|---|---|---|---|---|
| **C-203 (Front POC — 3 cases + tối đa 2 retry kỹ thuật)** | Tối đa `5` ảnh | `5 credits` (~`$0.38`) | `10 credits` (~`$0.75`) | ~`$0.30` |
| **C-204 (Paired Back + Multi-garment probe — 3 cặp + 2 pass probe)** | Tối đa `8` ảnh | `8 credits` (~`$0.60`) | `16 credits` (~`$1.20`) | ~`$0.48` |
| **C-211 / C-212 (Runtime E2E verification + Demo rehearsal)** | Tối đa `12` ảnh | `12 credits` (~`$0.90`) | `24 credits` (~`$1.80`) | ~`$0.72` |
| **Tổng trần ngân sách đề xuất (Hard Cap)** | **Tối đa `25` ảnh** | **`25 credits` (~`$1.88`)** | **`50 credits` (~`$3.75`)** | **~`$1.50`** |

---

## 7. Điều kiện Mở khóa POC Live & Blockers hiện tại

Mặc dù tài liệu kỹ thuật đã rõ ràng, Track C **KHÔNG gọi API live** ở thời điểm này do 3 điều kiện bắt buộc trong `VTO_FEASIBILITY.md` chưa được xác nhận:

1. **`PRE-001` (Owner: Track B / BTC Hackathon):** Xác nhận quy định của *Codex Community Hackathon Hanoi (14/11/2026)* về việc chạy live API POC trước khung giờ thi chính thức và yêu cầu disclosure.
2. **`PRE-003` / `DEC-04` (Owner: User / Track B+C):** Cung cấp `FASHN_API_KEY` (hoặc GCP Vertex AI credentials) đã có sẵn và duyệt trần ngân sách cụ thể (ví dụ `25 credits` / `$2.00` hard cap).
3. **`C-202` (Owner: Track C / Tình nguyện viên nhóm):** Bổ sung tối thiểu 3 bộ ảnh người thật (`personFront` + `personBack`) và quần áo thật có văn bản đồng ý (consent) rõ ràng cho mục đích upload lên FASHN/Google.

→ **Hành động tiếp theo của Track C:** Chuyển sang hoàn tất `C-202` (manifest & synthetic technical fixtures), `C-203`/`C-204` (POC harness với `FakeVtoProviderAdapter` + `FashnProviderAdapter` có kill-switch), `C-205` (ghi nhận Gate = `BLOCKED`), và triển khai đầy đủ toàn bộ nền tảng Android & Proxy (`C-206`, `C-210`, `C-208`, `C-209`, `C-207`, `D-002`).
