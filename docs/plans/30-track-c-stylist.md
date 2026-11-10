# Track C — Stylist & Weather

**Sở hữu:** `core:weather`, `core:stylist`, `feature:today`
**Implement:** `WeatherProvider`, `OutfitService`
**Dùng của người khác:** `WardrobeRepository` (A), `AiOutfitGenerator` (B), `ProfileRepository` + `WearLogRepository` + `MetricsTracker` (D) — tất cả có stub từ Sprint 0.

## Mục tiêu
Màn hình **"Hôm nay"** là trung tâm demo: mở app → thấy thời tiết → chọn dịp → trong vài giây có 3 bộ đồ từ tủ đồ thật, kèm **lời giải thích** → bấm "Mặc bộ này". **Luôn ra kết quả**, kể cả khi mất mạng.

## Phạm vi

### C1. `core:weather`
- Open-Meteo (không cần key): nhiệt độ, cảm giác như, xác suất mưa, mã thời tiết → tiếng Việt.
- Vị trí: quyền coarse location; từ chối ⇒ dùng `city` trong profile (geocoding Open-Meteo) ⇒ mặc định Hà Nội.
- Cache snapshot 30 phút; offline ⇒ dùng snapshot gần nhất + nhãn "cập nhật lúc…".

### C2. `core:stylist`
- `RuleBasedStylist` (thuần Kotlin, không phụ thuộc Android — dễ test):
  - Lọc theo mùa/nhiệt độ (ngưỡng: <18°C cần outerwear, >30°C ưu tiên đồ mỏng), mưa ⇒ tránh giày vải/đồ trắng.
  - Lọc formality theo dịp (bảng `Occasion → khoảng formality`).
  - Phối màu: neutral + 1 màu nhấn, tránh 2 họa tiết; chấm điểm, lấy top 3 khác nhau.
  - Ưu tiên đồ lâu chưa mặc (`lastWornAt`), tránh `avoidItemIds` (mặc hôm qua).
  - Sinh giải thích từ template ("Trời 16°C và có mưa nên…").
- `ResilientOutfitService` implement `OutfitService`: gọi `AiOutfitGenerator` với timeout ⇒ lỗi/timeout/ kết quả rỗng ⇒ `RuleBasedStylist`; gắn `source` để UI hiện nhãn "Offline".
- Xử lý tủ đồ thiếu (không có giày, < 3 món) ⇒ thông báo hữu ích thay vì crash.

### C3. `feature:today`
- Header thời tiết + chip chọn dịp (nhớ lựa chọn cuối).
- Carousel 3 outfit: ảnh các món xếp dạng flat-lay, đoạn giải thích, badge "Đồ bạn lâu chưa mặc".
- Hành động: **Mặc bộ này** (→ `WearLogRepository.logWear` + `WardrobeRepository.markWorn`), 👍/👎, **Phối lại**.
- Trạng thái loading có skeleton (không màn trắng), error state rõ ràng.
- Event: `outfit_requested`, `outfit_shown` (latency, source), `outfit_worn`, `outfit_feedback`, thời gian từ mở màn hình → "Mặc bộ này" (**metric chính cho tiêu chí 2**).

## Ngoài phạm vi
Prompt AI (B), lưu wear log/thống kê (D), thêm đồ (A).

## Task & thứ tự
1. `RuleBasedStylist` + unit test (TDD, bắt đầu ngay từ ngày 1 — không phụ thuộc ai)
2. `core:weather` + test với mock server
3. UI "Hôm nay" chạy với seed data + RuleBased
4. `ResilientOutfitService` (AI stub → B thật)
5. Hành động mặc/feedback + metric
6. Đánh bóng giao diện flat-lay, animation chuyển outfit

## Definition of Done
- [ ] Bật chế độ máy bay: vẫn ra 3 outfit hợp lệ
- [ ] Không bao giờ gợi ý áo khoác dày khi > 30°C (test)
- [ ] Mỗi outfit đủ thành phần hợp lệ, không trùng lặp giữa 3 phương án
- [ ] Coverage ≥ 80% cho `core:stylist`, `core:weather`
- [ ] Từ mở app tới thấy outfit (AI) ≤ 10s trên 4G

## Rủi ro
- AI chậm khi demo ⇒ hiển thị kết quả RuleBased ngay, thay bằng AI khi về (progressive) — cân nhắc nếu p90 > 8s.
