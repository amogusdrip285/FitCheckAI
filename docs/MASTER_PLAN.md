# FitCheckAI — Master Plan (bản nháp để duyệt)

> Nguồn: `Requirements.MD`, `Scorings.MD` · Ngày: 2026-11-10 · Trạng thái: **DRAFT — chờ duyệt**

---

## 0. Tóm tắt 1 dòng

App Android "tủ đồ số + stylist AI": chụp quần áo → AI tách nền, gắn nhãn, lưu tủ đồ → mỗi sáng AI phối đồ theo **thời tiết + dịp + phong cách**, giải thích lý do, gợi ý phương án khác, và theo dõi tần suất mặc để tận dụng đồ ít dùng.

## 1. Đọc hiểu bảng điểm → nguyên tắc thiết kế

| Tiêu chí | Điểm | Hệ quả cho kế hoạch |
|---|---|---|
| 1. Demo AI trên điện thoại thật | **40** | Ưu tiên số 1 là **độ ổn định**. Dữ liệu phải lưu bền (đóng app hoàn toàn, mở lại vẫn còn tủ đồ). AI phải chạy *live* trên máy thật, không mock. Phải có **đường lui khi mạng yếu** để không rơi xuống mức "chạy một phần" (20đ). |
| 2. Vấn đề thật của người dùng | 35 | Cần **bằng chứng**: khảo sát + phỏng vấn + số liệu sử dụng thật từ bản beta. App nên tự đo được "thời gian chọn đồ" và "% tủ đồ được dùng" để làm bằng chứng AI giúp trực tiếp. |
| 3. Mở rộng thị trường | 25 | Cần kế hoạch GTM cụ thể (người dùng đầu tiên là ai, kênh nào) + **người dùng thực tế** trước ngày demo. |
| Hòa điểm | — | Tiêu chí 1 > 2 > 3 ⇒ khi thiếu thời gian, cắt tính năng phụ chứ không cắt độ ổn định. |

**Nguyên tắc:** *Ổn định > Ít tính năng nhưng chạy thật > Nhiều tính năng.*

## 2. Phạm vi sản phẩm

### MVP (bắt buộc cho demo)
1. **Thêm đồ**: chụp/chọn ảnh → tách nền (on-device) → AI nhận diện *loại, màu, chất liệu, kiểu dáng, độ trang trọng, mùa* → người dùng sửa nhanh nếu sai → lưu.
2. **Tủ đồ số**: lưới ảnh đã tách nền, lọc theo loại/màu/mùa.
3. **Phối đồ hôm nay**: lấy thời tiết hiện tại + chọn dịp (đi học, đi làm, hẹn hò, tiệc, thể thao…) → AI trả 1 bộ chính + 2 phương án, mỗi bộ có **lời giải thích**.
4. **"Mặc bộ này"**: ghi nhận lượt mặc → thống kê tần suất, highlight **đồ bị bỏ quên** (lâu không mặc) và ưu tiên chúng trong gợi ý.
5. **Hồ sơ phong cách**: onboarding chọn 3–5 style (minimal, streetwear, công sở…) + feedback 👍/👎 trên từng gợi ý.

### Nice-to-have (chỉ làm khi MVP ổn định)
- Lịch outfit theo tuần, chia sẻ ảnh outfit (kênh lan truyền cho tiêu chí 3).
- "Phối quanh 1 món": chọn 1 item → AI xây bộ quanh nó.
- Gợi ý món nên mua để lấp khoảng trống tủ đồ (hướng monetization).

### Ngoài phạm vi
iOS, virtual try-on, mạng xã hội đầy đủ, thương mại điện tử.

## 3. Kiến trúc kỹ thuật (đề xuất)

```
┌──────────────── Android app (Kotlin + Jetpack Compose) ────────────────┐
│ UI (Compose)  →  ViewModel  →  Repository                              │
│                                  ├─ Room DB + ảnh trong internal storage│  ← bền khi đóng/mở app
│                                  ├─ ML Kit Subject Segmentation         │  ← tách nền ON-DEVICE
│                                  ├─ WeatherClient (Open-Meteo, no key)  │
│                                  └─ AiClient ──HTTPS──┐                 │
│ Fallback: RuleBasedStylist (offline, luật màu/thời tiết/dịp)           │
└────────────────────────────────────────────────────────┼───────────────┘
                                                         ▼
                         Proxy mỏng (Cloudflare Worker) — giữ API key, rate-limit
                                                         ▼
                         Claude API: vision tagging + outfit reasoning (JSON schema)
```

**Lý do chọn:**
- **Kotlin + Compose (native)**: chỉ cần Android, truy cập trực tiếp ML Kit/CameraX, ít rủi ro build hơn cross-platform. *(Phương án thay thế: Flutter nếu team quen Dart hơn.)*
- **Tách nền on-device (ML Kit)**: nhanh, miễn phí, chạy cả khi không mạng → luôn có "khoảnh khắc AI" khi demo.
- **LLM vision cho tagging + phối đồ**: chất lượng nhận diện/giải thích tốt hơn hẳn model nhỏ; trả về JSON có schema để parse ổn định.
  - Tagging: `claude-haiku-5-5` (nhanh, rẻ) · Phối đồ + giải thích: `claude-sonnet-5-5`.
- **Proxy**: **không nhúng API key vào APK** (dễ bị decompile). Worker giữ key, giới hạn request/thiết bị.
- **RuleBasedStylist fallback**: nếu mạng lỗi/timeout, app vẫn phối được đồ (hiển thị nhãn "chế độ offline") → bảo vệ 40 điểm tiêu chí 1.

**Data model (sơ bộ):**
- `ClothingItem(id, imagePath, category, subCategory, colors[], pattern, material, formality 1–5, seasons[], styleTags[], createdAt, wearCount, lastWornAt)`
- `Outfit(id, itemIds[], occasion, weatherSnapshot, explanation, source[AI|RULE], createdAt)`
- `WearLog(id, outfitId, wornAt)` · `Feedback(outfitId, liked, reason?)` · `UserProfile(styles[], gender/fit prefs, city)`

**Lưu ý chất lượng:** giảm kích thước ảnh trước khi gửi (~768px), cache kết quả tag theo hash ảnh, timeout + retry 1 lần rồi fallback, validate JSON từ AI trước khi lưu.

## 4. Lộ trình (chia theo phase, mỗi phase có thể demo được)

| Phase | Nội dung | Kết quả kiểm chứng |
|---|---|---|
| **P0 – Nền móng** | Khởi tạo project Compose, Room, CameraX, cấu trúc module, CI build APK | APK cài được trên máy thật |
| **P1 – Tủ đồ** | Chụp → tách nền ML Kit → lưu Room → lưới tủ đồ | Đóng hẳn app, mở lại: đồ vẫn còn |
| **P2 – AI tagging** | Proxy + Claude vision → tự điền thuộc tính, màn hình sửa nhanh | Tag đúng ≥ 80% trên bộ 30 ảnh test |
| **P3 – Stylist** | Weather + dịp + profile → 3 outfit có giải thích; RuleBased fallback | Tắt mạng vẫn ra gợi ý |
| **P4 – Tracking** | "Mặc bộ này", thống kê, đồ bị bỏ quên, feedback 👍/👎 | Màn hình Insights có số liệu thật |
| **P5 – Beta & bằng chứng** | Phát APK cho 15–30 người dùng thật, thu metric + phỏng vấn | Số liệu cho tiêu chí 2 & 3 |
| **P6 – Hardening demo** | Test trên 2–3 máy thật, kịch bản demo, dữ liệu mẫu đẹp sẵn | Chạy kịch bản demo 10 lần liên tiếp không lỗi |

> P5 (người dùng thật) nên **bắt đầu song song từ sau P3**, không đợi cuối — đây là thứ tốn thời gian nhất và quyết định tiêu chí 2–3.

## 5. Nhánh không-code (tiêu chí 2 & 3 = 60 điểm)

**Bằng chứng vấn đề (tiêu chí 2):**
- Khảo sát Google Form 50–100 người (sinh viên/người đi làm 18–28): thời gian chọn đồ mỗi sáng, % đồ hiếm khi mặc, tần suất cảm giác "không có gì để mặc".
- 5–8 phỏng vấn sâu, trích dẫn nguyên văn.
- Trích dẫn số liệu ngành (tỷ lệ quần áo ít được mặc, v.v. — cần tra nguồn cụ thể).
- **Before/after từ beta**: thời gian chọn đồ, % tủ đồ được mặc sau 1–2 tuần.

**Mở rộng thị trường (tiêu chí 3):**
- **Người dùng đầu tiên**: sinh viên HUST & các trường lân cận → group Facebook trường, CLB, Threads/TikTok "outfit hôm nay".
- **Vòng lặp lan truyền**: ảnh outfit share kèm watermark app.
- **Mở rộng**: VN (tiếng Việt, thời tiết nhiệt đới, dịp đặc thù: Tết, đám cưới…) → Đông Nam Á (khí hậu tương tự) → toàn cầu (đa ngôn ngữ; LLM giúp bản địa hóa gần như miễn phí).
- **Monetization**: freemium (giới hạn số lần phối AI/ngày) → affiliate gợi ý mua món còn thiếu (Shopee/Lazada) → đối tác thương hiệu.
- Đối thủ tham khảo để định vị: Whering, Acloset, Indyx, Stylebook — điểm khác biệt: tối ưu cho thời tiết/dịp tại VN + giải thích bằng tiếng Việt + tập trung "tận dụng đồ sẵn có".

## 6. Rủi ro chính & cách giảm

| Rủi ro | Giảm thiểu |
|---|---|
| Mạng tại địa điểm demo yếu | RuleBased fallback + hotspot 4G dự phòng + dữ liệu tủ đồ có sẵn |
| AI tag sai / JSON hỏng | Schema validation, màn hình sửa nhanh, retry |
| Lộ API key | Proxy, không key trong APK |
| Ảnh nặng làm chậm | Resize trước khi upload, tách nền on-device |
| Không đủ người dùng thật | Bắt đầu beta sớm (sau P3) |
| Chi phí API | Haiku cho tagging, cache theo hash ảnh, rate-limit ở proxy |

## 7. Kiểm thử

- Unit test: RuleBasedStylist, parser JSON AI, repository (mục tiêu ≥ 80% cho tầng domain/data).
- Instrumented test: luồng thêm đồ → phối đồ trên máy thật.
- Checklist demo: cold start (force-stop → mở lại), tắt mạng, xoay màn hình, máy cấu hình thấp.

## 8. Câu hỏi cần anh/chị chốt trước khi bắt đầu

1. **Deadline demo** là ngày nào? Team mấy người, ai làm mobile/AI/thuyết trình?
2. **Stack**: đồng ý Kotlin + Compose native, hay team quen Flutter/React Native hơn?
3. **AI provider**: dùng Claude API qua proxy (cần tài khoản + key) — OK không? Hay có ràng buộc dùng Gemini/OpenAI/miễn phí?
4. Ngôn ngữ app: chỉ tiếng Việt, hay Việt + Anh ngay từ đầu (hỗ trợ luận điểm "toàn cầu")?
5. Có máy Android thật nào để test (model/Android version)?
