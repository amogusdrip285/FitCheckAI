# C-202 — Báo cáo Sẵn sàng Bộ Dữ liệu POC & Quản trị Consent (Dataset Readiness)

- **Task ID:** `C-202`
- **Track / Owner:** Track C — Virtual Try-On
- **Ngày cập nhật:** `2026-11-11`
- **Trạng thái thực thi:**
  - Bộ fixture kỹ thuật & trình kiểm tra hợp đồng (`eval/tryon/fixtures/technical_fixtures_manifest.json`): **`IMPLEMENTED` & `TESTED` (`FAKE_ONLY` / Synthetic Technical Fixtures)**.
  - Bộ 3 cặp ảnh người thật có consent (`personFront` + `personBack` + `garmentFront` + `garmentBack`): **`BLOCKED` (Chưa có ảnh tình nguyện viên đã ký consent ngoài Git)** → Chặn chạy live quality `C-203`/`C-204` đúng theo Definition of Done của `C-202`.

---

## 1. Phân định rõ Hai Tầng Dữ liệu (Không trộn lẫn Ground Truth)

Theo quy tắc tại `docs/execution/tracks/C.md#c-202` và `CONTRACTS.md` (`CT-01`, `CT-06`, `CT-07`):

1. **Tầng 1 — Synthetic Technical Fixtures (Có trong repo tại `eval/tryon/fixtures/technical_fixtures_manifest.json`):**
   - Dùng ảnh PNG/JPEG lập trình tổng hợp (synthetic solid/geometric pattern buffers sinh tự động trong bộ nhớ hoặc thư mục tạm ngoài Git khi chạy test), **tuyệt đối không chứa ảnh người thật**, không lấy ảnh người trên Internet.
   - Phục vụ kiểm thử tự động 100% logic validation, lắp ghép payload, cổng consent, cổng ngân sách, phát hiện thiếu ảnh lưng, phát hiện lệch góc nhìn, stale revision, deleted media và tách món không hỗ trợ.
   - Mọi bản ghi mang cờ `provenance: "SYNTHETIC_TECHNICAL_FIXTURE"` và `eligibleForLiveQualityGate: false`.

2. **Tầng 2 — Private Consented Real-Person POC Dataset (Ngoài Git hoàn toàn):**
   - Đường dẫn quy ước cục bộ ngoài repository: `~/.cache/fitcheckai-private/tryon-poc/` (quyền thư mục `0700`, quyền file `0600`, **không bao giờ nằm trong working tree Git**).
   - **Trạng thái kiểm tra thực tế ngày `2026-11-11`:** Thư mục chưa có ảnh tình nguyện viên và chưa có hồ sơ consent đã ký. Do đó, Track C **không tự tạo ảnh mặt sau giả**, **không tải ảnh người từ Internet**, và đánh dấu rõ phần dữ liệu người thật đang thiếu để nhóm bổ sung trước khi mở `C-203` live.

---

## 2. Danh mục 9 Test Cases trong `technical_fixtures_manifest.json`

### A. 3 Positive Structural Cases (Đủ cấu trúc theo yêu cầu C-202)

| Case ID | Mô tả cấu trúc Outfit | Person Views | Garment Views | Mục đích kiểm thử |
|---|---|---|---|---|
| `case-pos-01-single-top` | **1 món đơn (`TOP`)**: Áo sơ mi trắng cổ bẻ | `FRONT` + `BACK` (khác hash) | `top-01`: `FRONT` + `BACK` (có chi tiết xếp ly lưng khác mặt trước) | Kiểm thử single-garment front (`C-203`) và paired independent front+back (`C-204`). |
| `case-pos-02-single-dress` | **1 đầm liền (`DRESS`)**: Đầm midi đất nung | `FRONT` + `BACK` (khác hash) | `dress-01`: `FRONT` + `BACK` (có đường khóa kéo/nơ lưng) | Kiểm thử category `one-pieces`/`DRESS` cả hai góc nhìn. |
| `case-pos-03-top-bottom-shoes` | **Nhiều món (`TOP` + `BOTTOM` + `SHOES`)**: Áo thun kem + Quần âu đen + Giày loafer | `FRONT` + `BACK` (khác hash) | `top-02` (`F+B`), `bottom-01` (`F+B`), `shoes-01` (`F` only) | Kiểm thử lọc `SHOES` sang `unappliedItemIds` (khi dùng `tryon-v1.6`) và probe tuần tự 2-pass `TOP -> BOTTOM`. |

### B. 6 Negative Fixtures bắt buộc (Kiểm thử chặn trước khi gọi API / trừ tiền)

| Case ID | Lỗi giả lập | Mã lỗi kỳ vọng (`CT-06`/`CT-07`) | Hành vi bắt buộc |
|---|---|---|---|
| `case-neg-01-missing-person-back` | Yêu cầu `FRONT + BACK` nhưng `personImages.back = null` | `NEEDS_INPUT` | Chặn trước upload; gợi ý chụp thêm ảnh lưng hoặc đổi sang `FRONT` only; **không nhân bản `personFront` làm `personBack`**. |
| `case-neg-02-missing-garment-back` | Yêu cầu `FRONT + BACK` nhưng `garmentImages[itemId].back = null` | `NEEDS_INPUT` | Chặn trước upload; báo đúng `itemId` thiếu ảnh mặt sau. |
| `case-neg-03-wrong-view-pairing` | `MediaRef` đặt ở slot `back` nhưng có `view = FRONT` (hoặc trùng `contentHash` với `front`) | `INVALID_REQUEST` | Phát hiện gán nhầm/nhân đôi ảnh mặt trước thành mặt sau; từ chối ngay. |
| `case-neg-04-deleted-media` | `MediaRef` trong outfit tham chiếu tới file đã bị xóa (`deleted = true` / missing bytes) | `STALE_INPUT` | Chặn ngay, yêu cầu chọn lại trang phục/ảnh mới. |
| `case-neg-05-stale-revision` | `outfitSnapshot` giữ `revision = 1` trong khi kho hiện tại đã lên `revision = 2` | `STALE_INPUT` | Chặn ngay để tránh render nhầm món đồ cũ đã sửa ảnh. |
| `case-neg-06-unsupported-garment` | Outfit hoặc `selectedItemIds` chỉ gồm `ACCESSORY` / `SHOES` trên cấu hình không hỗ trợ | `UNSUPPORTED_CAPABILITY` | Chặn trước upload, không tốn credit vô ích. |

---

## 3. Mẫu Hồ sơ Đồng ý Riêng tư (Private Consent Record Schema) cho Tình nguyện viên

Khi nhóm thu thập đủ 3 tình nguyện viên nội bộ cho `C-203`/`C-204`, mỗi tình nguyện viên dùng mã giả danh (`subject-01`, `subject-02`, `subject-03` — không lưu tên thật/SĐT trong manifest kỹ thuật) và lập file JSON ngoài Git theo mẫu:

```json
{
  "consentGrantId": "consent-poc-subj01-20261114-001",
  "subjectPseudonym": "subject-01",
  "purpose": "TRYON_POC_EVALUATION",
  "providerId": "fashn-ai",
  "providerEndpoint": "https://api.fashn.ai/v1/run",
  "providerConfigVersion": "tryon-v1.6-base64-ephemeral",
  "allowedViews": ["FRONT", "BACK"],
  "selectedMediaIds": ["media-person-01-front", "media-person-01-back", "media-top-01-front", "media-top-01-back"],
  "allowLiveDemoDisplay": false,
  "allowPublicSlideInclusion": false,
  "retentionAcknowledged": "Base64 output expires within 60 minutes on FASHN status endpoint; no model training; local files stored in 0700 private directory outside Git.",
  "revocationContactAndProcedure": "Run `python3 eval/tryon/poc_runner.py --purge-subject subject-01` to wipe local inputs/outputs immediately.",
  "confirmedBySubject": true,
  "issuedAtIsoUtc": "2026-11-14T04:00:00Z",
  "revokedAtIsoUtc": null
}
```

> **Lưu ý quan trọng:** Trường `allowLiveDemoDisplay` và `allowPublicSlideInclusion` tách biệt hoàn toàn với quyền `TRYON_POC_EVALUATION`. Đồng ý cho chạy thử kỹ thuật **không** đồng nghĩa được phép chiếu ảnh cơ thể lên slide thuyết trình (`D-002`/`D-003`).

---

## 4. Danh sách Hạng mục Còn Thiếu để Mở Khóa Live Dataset (`Owner & Action`)

| Hạng mục còn thiếu | Owner chịu trách nhiệm | Điều kiện nghiệm thu để gỡ `BLOCKED` cho C-203/C-204 |
|---|---|---|
| 3 bộ ảnh tình nguyện viên thật (`personFront` + `personBack`, đứng thẳng, đủ sáng, thấy toàn thân) | Nhóm (Track C điều phối cùng Track A `PRE-004`) | Lưu tại `~/.cache/fitcheckai-private/tryon-poc/subjects/`, kiểm tra `contentHash` khác nhau giữa front/back. |
| 3 bộ ảnh quần áo thật khớp 3 case (`TOP`, `DRESS`, `TOP+BOTTOM`) có đủ mặt trước và mặt sau thật | Track A (`PRE-004`) bàn giao sang Track C | Không dùng ảnh mặt trước nhân bản làm mặt sau; có chi tiết mặt lưng để chấm `C-204`. |
| 3 bản ghi Consent hợp lệ cho đúng provider được chọn (`fashn-ai` hoặc `vertex-ai`) | Track C + từng tình nguyện viên | Có `confirmedBySubject: true`, tách riêng quyền POC và quyền trình chiếu demo. |
