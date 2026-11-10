# Track D — Profile, Insights & Evidence

**Sở hữu:** `core:ui`, `core:datastore`, `core:analytics`, `feature:onboarding`, `feature:insights`, `docs/research/`
**Implement:** `ProfileRepository`, `WearLogRepository`, `MetricsTracker`
**Dùng của người khác:** `WardrobeRepository` (A), DAO `WearLog/Feedback` trong `core:database` (A tạo sẵn ở Sprint 0)

## Mục tiêu
1. **Trong app**: onboarding cá nhân hóa, màn hình Insights chứng minh "tận dụng tủ đồ tốt hơn".
2. **Ngoài app**: thu **bằng chứng thật** + **người dùng thật** cho tiêu chí 2 (35đ) và 3 (25đ).

## Phạm vi — Code

### D1. `core:ui`
- Design system: màu, typography, spacing token, component dùng chung (ItemCard, Chip, PrimaryButton, EmptyState, Skeleton). Chọn hướng thị giác rõ ràng (vd. editorial/thời trang), không dùng nguyên Material mặc định.
- Ổn định API component sớm (tuần đầu) để A, C dùng.

### D2. `core:datastore` + onboarding
- `ProfileRepository` (Proto/Preferences DataStore): styles, ghi chú dáng người, thành phố.
- `feature:onboarding`: 3 màn: chào → chọn 3–5 phong cách (ảnh minh họa) → thành phố/quyền vị trí → (tùy chọn) hướng dẫn thêm 5 món đầu tiên. Chỉ hiện lần đầu.
- Kích hoạt tải trước model ML Kit (gọi API của A qua interface nếu cần — đề xuất thêm `ImageSegmenter.warmUp()` ở Sprint 0).

### D3. `WearLogRepository` + `feature:insights`
- Ghi wear log + feedback (dùng DAO sẵn có).
- Insights: % tủ đồ đã mặc trong 30 ngày, top món hay mặc, **"Đồ bị bỏ quên"** (> 30 ngày chưa mặc) với nút "Phối quanh món này" (deep link sang Today với `focusItemId` — route định sẵn ở Sprint 0), lịch sử outfit.
- Biểu đồ đơn giản (Compose Canvas hoặc thư viện nhẹ).

### D4. `core:analytics`
- `MetricsTracker`: ghi event vào bảng/file local + (tùy chọn) Firebase Analytics để tổng hợp dữ liệu beta.
- Metric chính: thời gian chọn outfit, outfit/ngày, tỷ lệ 👍, % tủ đồ được dùng, tỷ lệ fallback, số món đã thêm.
- Màn hình debug "Export metrics" (CSV) cho phân tích beta.
- Crash reporting (Firebase Crashlytics) — quan trọng cho độ ổn định demo.

## Phạm vi — Không code (`docs/research/`)
| Hạng mục | Kết quả |
|---|---|
| Khảo sát | Google Form 50–100 người 18–28; báo cáo số liệu (thời gian chọn đồ, % đồ ít mặc, cảm giác "không có gì để mặc") |
| Phỏng vấn | 5–8 người, ghi chú + trích dẫn |
| Nghiên cứu ngành | Số liệu có nguồn về tủ đồ ít dùng, thị trường thời trang/app VN & SEA |
| Đối thủ | Bảng so sánh Whering, Acloset, Indyx, Stylebook — định vị khác biệt |
| Beta | Firebase App Distribution / Google Play Internal Testing; tuyển 15–30 người; nhóm Zalo/Messenger hỗ trợ |
| GTM | Kế hoạch: người dùng đầu tiên (sinh viên HUST) → VN → SEA → toàn cầu; kênh, vòng lặp chia sẻ, monetization |
| Pitch | Slide + kịch bản demo (phối hợp cả nhóm ở M4) |

## Task & thứ tự
1. **Ngày 1**: phát khảo sát (mất thời gian chờ phản hồi nhất), `core:ui` token cơ bản
2. Onboarding + `ProfileRepository`
3. `WearLogRepository` + `MetricsTracker`
4. Insights với seed data
5. Phỏng vấn, phân tích khảo sát
6. M3: phát beta, theo dõi crash & metric hàng ngày
7. Tổng hợp bằng chứng before/after, viết GTM, slide

## Definition of Done
- [ ] Onboarding chỉ hiện 1 lần; force-stop → mở lại giữ profile
- [ ] Insights khớp dữ liệu wear log (test)
- [ ] ≥ 50 phản hồi khảo sát, ≥ 5 phỏng vấn, ≥ 15 người dùng beta có dữ liệu thật
- [ ] Coverage ≥ 80% cho `core:datastore`, `core:analytics`, logic Insights
- [ ] Analytics không thu ảnh/PII; có thông báo quyền riêng tư trong onboarding
