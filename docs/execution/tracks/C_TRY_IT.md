# Track C — Hướng dẫn Tự thử Thử đồ Ảo (MOCK & FOSTA LIVE)

> **Lưu ý phạm vi hiện tại:** Toàn bộ luồng thử đồ Phase C dưới đây được kiểm trên ứng dụng **`FitCheck VTO Demo`** chạy độc lập trên máy ảo `Pixel_6`. Phần nối hoàn chỉnh từ `:app` thuộc **Track D** — trên `origin/main` (`fdf75e6`), `:app` mới nối điều hướng (`TodayScreen` → `"Thử đồ với bộ này"`) tới cổng khóa `CoreModeVirtualTryOn` (`MỘT PHẦN`, chưa mở màn chọn/gửi ảnh thật); nhánh unmerged `origin/feat/demo-wardrobe-tryon-wiring` (`235f477`) của nhóm đang tiếp tục nối phần đó.

- **Tài liệu liên quan:** [Cảnh báo vận hành (`C_WARNINGS.md`)](C_WARNINGS.md) · [Hướng dẫn nối (`C_INTEGRATION.md`)](C_INTEGRATION.md) · [Bàn giao (`C_HANDOFF.md`)](C_HANDOFF.md)
- **Script hỗ trợ:** `proxy/scripts/demo_try_cases.sh`
- **Thư mục lưu ảnh chụp đối chiếu:** `~/fitcheck_try_shots/` *(nằm ngoài Git)*

---

## 1. Lệnh điều khiển nhanh (`demo_try_cases.sh`)

```bash
# Kiểm tra máy ảo Pixel_6, APK Demo, đủ 8 album ảnh và trạng thái backend/quota
./proxy/scripts/demo_try_cases.sh check

# Bật chế độ MOCK ($0.00, 0 lượt LIVE), cài/mở FitCheck VTO Demo và in ca M1..M5
./proxy/scripts/demo_try_cases.sh mock

# Bật FOSTA LIVE (kiểm tra tryon.env + còn lượt, hỏi xác nhận chính xác YES) và in ca L1..L4
./proxy/scripts/demo_try_cases.sh live

# Chụp màn hình máy ảo vào ~/fitcheck_try_shots/ kèm số lượt LIVE đã dùng & độ lệch
./proxy/scripts/demo_try_cases.sh snap <tên_ca>

# Dừng server local ở cổng 8788 (giữ nguyên sổ cái đếm quota)
./proxy/scripts/demo_try_cases.sh stop
```

---

## 2. Giải thích từng vùng trên màn hình `"Thử đồ"`

1. **Thanh tiêu đề Demo (đầu màn hình Demo) & thanh `"Thử đồ"`:**
   - Thanh Demo luôn hiển thị trạng thái kết nối backend (ví dụ `FITCHECK VTO DEMO — BACKEND FOSTA LUNA` · `Kết nối OK: FOSTA MOCK (LIVE=OFF • Chi phí $0.00)`), nút **`"Làm mới"`** (đọc lại trạng thái server), **`"Tối"`** / **`"Sáng"`**, và **`"Tùy chỉnh"`** (bảng kỹ thuật dev, không thuộc giao diện người dùng cuối).
   - Bên dưới tiêu đề **`"Thử đồ"`**, ở chế độ MOCK có dòng thông báo **`"Chế độ kiểm thử · Kết quả giả lập, không gọi AI thực tế."`**; khi nối FOSTA LIVE có dòng chấm xanh **`"Đang kết nối dịch vụ thử đồ trực tiếp"`**.

2. **`"Ảnh người dùng"`:**
   - Gồm 2 cột **`"Mặt trước"`** và **`"Mặt sau"`** với nút **`"Chọn ảnh"`** / **`"Thay ảnh"`** (góc nào chưa chọn trong chế độ không nạp mẫu sẽ có thêm nút từ Hồ sơ ở dưới: `"Thêm ảnh mặt trước từ Hồ sơ"` / `"Bổ sung ảnh mặt sau từ Hồ sơ"`).
   - *Lưu ý khi thử LIVE:* ở bản Demo, ô `Ảnh người dùng` / `Áo` / `Quần` mặt trước có thể hiển thị sẵn hình phác họa mẫu (`SAMPLE PERSON INPUT`); trước khi bấm Tạo ở LIVE hãy bấm **`"Thay ảnh"`** để chọn đúng ảnh thật từ Gallery. `Mũ` và `Giày` luôn khởi tạo trống.

3. **`"Trang phục muốn thử"` (4 thẻ theo thứ tự cố định `Áo`, `Quần`, `Mũ`, `Giày`):**
   - Quy tắc *"Có ảnh thì ghép"*: thẻ nào đang có ảnh hiện dòng **`"Sẵn sàng ghép"`** và dấu tích chọn; thẻ trống hiện **`"Chưa chọn ảnh (không ghép)"`** và không bị gửi lên AI.
   - Nút **`"Bỏ ảnh"`** (góc trên bên phải mỗi thẻ đang có ảnh): xóa sạch cả ảnh trước và ảnh sau của riêng món đó.
   - Cột trái **`"Mặt trước"`**: nút **`"Chọn ảnh"`** / **`"Thay ảnh"`**.
   - Cột phải:
     - Với **`Áo`** và **`Quần`**: tiêu đề **`"Mặt sau"`** (bắt buộc nếu chọn góc mặt sau).
     - Với **`Mũ`** và **`Giày`**: tiêu đề **`"Mặt sau (không bắt buộc, AI sẽ tự đoán)"`**.
     - Nút **`"Chọn ảnh sau"`** / **`"Thay ảnh sau"`**; khi cột mặt sau đang có ảnh sẽ hiện thêm nút **`"Bỏ ảnh sau"`** ngay bên dưới để xóa **chỉ riêng ảnh mặt sau** mà vẫn giữ nguyên ảnh mặt trước.

4. **`"Phạm vi thử đồ"`:**
   - Hiển thị dòng tóm tắt trực tiếp: ví dụ **`"Sẽ thử: Áo + Quần + Mũ + Giày"`**, hoặc **`"Chưa chọn món nào — hãy thêm ảnh cho ít nhất một ô ở trên"`** khi cả 4 thẻ đều trống.
   - Khi ô `Giày` có ảnh, hiện thêm nhắc: **`"Ảnh người cần thấy rõ chân để ghép giày đẹp hơn"`**.

5. **`"Chế độ khung hình & góc nhìn"`:**
   - Nhóm **`"Khung hình"`**: chọn **`"Ảnh gốc"`** (giữ nguyên tư thế & bối cảnh ảnh người) hoặc **`"Nền trắng studio"`** (dựng dáng thẳng trên nền trắng sáng đều).
   - Nhóm **`"Góc nhìn"`**: chọn **`"Mặt trước"`** hoặc **`"Chỉ mặt sau"`** (bản OpenAI có thêm `"Trước và sau"`).
   - Nếu đang chọn **`"Chỉ mặt sau"`** mà `Mũ` hoặc `Giày` chỉ có ảnh mặt trước (chưa có ảnh sau), nút `"Chỉ mặt sau"` **vẫn mở bình thường** và hiện thông báo lưu ý: **`"Mũ/Giày chưa có ảnh sau: AI sẽ tự suy ra mặt sau, có thể không giống mẫu thật."`** (nội dung nêu đúng tên món thiếu ảnh sau).

6. **`"Đồng ý xử lý ảnh"` & Nút tạo:**
   - Ô tick **`"Tôi đồng ý gửi những ảnh đã chọn cho riêng lượt thử đồ 2D này."`** — tự động bỏ tick mỗi khi bạn thêm, thay hoặc bỏ bất kỳ ảnh nào.
   - **Quy tắc nghiêm Mặt trước / Mặt sau:** Nếu góc nhìn đang chọn thiếu ảnh bắt buộc (ảnh người hoặc ảnh `Áo`/`Quần` ở góc đó), ứng dụng **không tự nhảy ngầm về `"Mặt trước"`** mà giữ nguyên góc đang chọn, khóa nút tạo và hiện khung báo lỗi đỏ ngay trên nút tạo:
     - Ví dụ thiếu ảnh sau của Áo: **`"Không thể tạo mặt sau: thiếu ảnh sau của Áo."`**
     - Ví dụ thiếu ảnh người và Quần mặt trước: **`"Không thể tạo mặt trước: thiếu ảnh người mặt trước, ảnh trước của Quần."`**
   - Nút chính: **`"Tạo ảnh thử đồ"`** (ở Mặt trước) hoặc **`"Tạo ảnh thử đồ · Mặt sau"`** (ở Mặt sau).
   - Khi đang xử lý: hiện **`"Đang chuẩn bị ảnh…"`** / **`"Đang tạo ảnh thử đồ…"`**, dòng **`"Có thể mất 1–2 phút. Bạn có thể để màn hình mở trong lúc chờ."`** và nút **`"Hủy lượt thử"`**.

7. **`"Kết quả thử đồ"`:**
   - Chip **`"So sánh ảnh gốc"`** / **`"Đang xem ảnh gốc"`** để lật đối chiếu ngay trên khung ảnh tỉ lệ `3:4` (giữ trọn đầu và chân).
   - Bên dưới có tuyên bố giới hạn tính năng, nút chính **`"Tạo lượt thử mới"`**, nút **`"Xóa kết quả này"`** và **`"Chọn bộ trang phục khác"`**.

---

## 3. Cách chọn đúng ảnh trong bộ chọn ảnh hệ thống (`8` album)

Trên `Pixel_6` đã nạp sẵn **8 thư mục (album)** tương ứng từng ô:

1. Khi bấm **`"Chọn ảnh"`** / **`"Thay ảnh"`** / **`"Chọn ảnh sau"`**, bộ chọn ảnh Android Photo Picker hiện ra.
2. Chạm vào tab **`Albums`** (hoặc biểu tượng thư mục bên cạnh tab `Photos`) để mở danh sách thư mục.
3. Chọn đúng ảnh theo tên album:
   - Người: `person_front` (mặt trước), `person_back` (mặt sau)
   - Áo: `top_front` (mặt trước), `top_back` (mặt sau)
   - Quần: `bottom_front` (mặt trước), `bottom_back` (mặt sau)
   - Mũ: `hat_front` *(mặt sau không bắt buộc)*
   - Giày: `shoes_front` *(mặt sau không bắt buộc)*

> **Tránh chọn trùng cùng 1 file ảnh** cho cả cột Mặt trước và Mặt sau của cùng một người hoặc cùng một món đồ (backend sẽ chặn lỗi trùng mã băm mà không trừ quota).

---

## 4. Danh sách ca thử gợi ý — Chế độ MOCK (`$0.00`, `0` lượt LIVE)

Khởi động trước: `./proxy/scripts/demo_try_cases.sh mock`

| Ca | Thao tác thực hiện | Kết quả mong đợi & Dấu hiệu ĐẠT | Dấu hiệu LỖI |
|---|---|---|---|
| **M1: Mặt trước 4 món (`Áo + Quần + Mũ + Giày`)** | Trên `"Ảnh người dùng"` chọn `person_front`; ở 4 thẻ trang phục chọn `top_front`, `bottom_front`, `hat_front`, `shoes_front`; giữ `"Mặt trước"`, tick đồng ý → bấm `"Tạo ảnh thử đồ"`. Chạy `snap m1_front_4items`. | Hiện `"Sẽ thử: Áo + Quần + Mũ + Giày"` và `"Ảnh người cần thấy rõ chân để ghép giày đẹp hơn"`. Trả về kết quả Mock và nút `"So sánh ảnh gốc"` hoạt động (`usedLiveRequests` không đổi). | Thiếu món trong dòng `"Sẽ thử:"`, mất nhắc chân rõ hoặc văng lỗi. |
| **M2: Bỏ `Áo` & `Quần` → chỉ còn `Mũ + Giày`** | Bấm `"Tạo lượt thử mới"`, cuộn lên thẻ `Áo` bấm `"Bỏ ảnh"`, thẻ `Quần` bấm `"Bỏ ảnh"`. Kiểm tra tick đồng ý tự bỏ → tick lại → bấm `"Tạo ảnh thử đồ"`. Chạy `snap m2_hat_shoes`. | Dòng tóm tắt đổi thành `"Sẽ thử: Mũ + Giày"`; ô đồng ý tự reset; sau khi tick lại thì gửi và ra kết quả thành công. | Không cho tạo khi không có Áo/Quần, hoặc quên reset đồng ý. |
| **M3: Bỏ hết cả 4 món → kiểm tra khóa nút** | Bấm `"Bỏ ảnh"` nốt ở thẻ `Mũ` và `Giày`. Quan sát khu `"Phạm vi thử đồ"` và nút `"Tạo ảnh thử đồ"`. Chạy `snap m3_zero_locked`. | Hiện `"Chưa chọn món nào — hãy thêm ảnh cho ít nhất một ô ở trên"`; nút `"Tạo ảnh thử đồ"` bị mờ (vô hiệu) kèm gợi ý `"Chọn ít nhất một món trang phục (Áo, Quần, Mũ hoặc Giày) để tiếp tục."`. | Nút Tạo vẫn bấm được khi `0` món. |
| **M4: Mặt sau `Áo` + `Mũ` (không có ảnh sau `Mũ`)** | Chọn `person_back`; trên `Áo` chọn `top_front` + `top_back`; trên `Mũ` chỉ chọn `hat_front` (để trống mặt sau); chọn chip `"Chỉ mặt sau"`; tick đồng ý → bấm `"Tạo ảnh thử đồ · Mặt sau"`. Chạy `snap m4_back_hat_infer`. | Chip `"Chỉ mặt sau"` vẫn bật dù `Mũ` thiếu ảnh sau; hiện thông báo `"Mũ chưa có ảnh sau: AI sẽ tự suy ra mặt sau, có thể không giống mẫu thật."`; tạo ảnh mặt sau thành công. | Khóa chip `"Chỉ mặt sau"` hoặc báo lỗi đỏ vì `Mũ` chưa có ảnh sau. |
| **M5: Bỏ ảnh sau `Áo` khi đang ở `"Chỉ mặt sau"`** | Bấm `"Tạo lượt thử mới"`, cuộn lên thẻ `Áo` và bấm **`"Bỏ ảnh sau"`** (giữ nguyên ảnh mặt trước `Áo`). Chạy `snap m5_back_missing_top`. | Góc nhìn **vẫn giữ nguyên ở `"Chỉ mặt sau"`** (không tự nhảy về `"Mặt trước"`); ngay trên nút `"Tạo ảnh thử đồ · Mặt sau"` hiện khung lỗi đỏ **`"Không thể tạo mặt sau: thiếu ảnh sau của Áo."`** và nút Tạo bị khóa. Chọn lại `top_back` thì lỗi biến mất ngay. | Tự động rơi về `"Mặt trước"`, hoặc `"Bỏ ảnh sau"` xóa nhầm cả ảnh mặt trước `Áo`. |

---

## 5. Danh sách ca thử gợi ý — Chế độ FOSTA LIVE (`gpt-6-luna`)

> Mỗi lần bấm **`"Tạo ảnh thử đồ"`** ở chế độ LIVE = **`1` lượt thật**. Kiểm tra số lượt còn lại bằng `./proxy/scripts/demo_try_cases.sh check` trước khi bắt đầu. Bật bằng `./proxy/scripts/demo_try_cases.sh live` (chỉ hoạt động khi trong `~/.fitcheckai_private/tryon.env` bạn đã chủ động đặt `TRYON_LIVE_ENABLED=true`, `FOSTA_APPROVE_QUOTA=true`, còn lượt và gõ `YES`).

| Ca | Cấu hình khuyên thử (tối ưu cho 3 lượt còn lại) | Điểm cần soi bằng mắt trên ảnh kết quả thật |
|---|---|---|
| **L1 *(Ưu tiên 1 · 1 lượt)*** | **`"Mặt trước"` + `"Ảnh gốc"` — đủ 4 món (`Áo + Quần + Mũ + Giày`)**: chọn `person_front` + `top_front` + `bottom_front` + `hat_front` + `shoes_front`. | Dùng chip `"So sánh ảnh gốc"`: kiểm tra khuôn mặt/tóc/dáng/nền gốc có bị biến dạng không; áo, quần, mũ trên đầu và giày ở chân có đúng màu sắc, vị trí, tỷ lệ tự nhiên không. |
| **L2 *(Ưu tiên 2 · 1 lượt)*** | **`"Mặt trước"` + `"Ảnh gốc"` — chỉ `Mũ` hoặc `Mũ + Giày`**: bấm `"Bỏ ảnh"` trên thẻ `Áo` và `Quần`, chỉ giữ ảnh `Mũ` (hoặc `Mũ + Giày`). | Áo và quần đang mặc trong ảnh người gốc phải **giữ nguyên**, chỉ thay đúng mũ / giày đã chọn. |
| **L3 *(Ưu tiên 3 · 1 lượt)*** | **`"Chỉ mặt sau"` + `"Ảnh gốc"` — `Áo + Quần` đủ ảnh sau + `Mũ` chỉ có ảnh trước**: chọn `person_back`, `top_back`, `bottom_back`, kèm `hat_front` (không chọn ảnh sau Mũ). | Lưng áo và lưng quần phải khớp ảnh mặt sau thật; phần sau của mũ được AI tự suy diễn có đồng màu/kiểu hợp lý với mặt trước `hat_front` không. |
| **L4 *(Tùy chọn khi còn quota)*** | **`"Nền trắng studio"`** cho 2–4 món bất kỳ. | Phông trắng đều, không cắt mất đầu hoặc mũi giày trong khung `3:4`. |

**Các thông báo lỗi có thể gặp khi chạy LIVE & cách xử lý:**
- **`BUDGET_EXCEEDED`:** Đã chạm trần `TRYON_LIVE_MAX_REQUESTS` trong `~/.fitcheckai_private/tryon.env` (không trừ thêm lượt). Chuyển sang `./proxy/scripts/demo_try_cases.sh mock` để tiếp tục kiểm thử `$0.00`.
- **`QUÁ THỜI GIAN CHỜ` / FOSTA `incomplete` (> 120s–180s):** Đợi hết tiến trình, bấm `"Tạo lượt thử mới"` nếu muốn thử lại với ít món hơn.
- **Lỗi `NEEDS_INPUT` (ví dụ chọn nhầm ảnh mặt trước giống hệt mặt sau):** Backend chặn ngay trước bước trừ quota (`0` lượt tiêu tốn); chọn lại ảnh khác góc cho mặt sau.
- **`UNKNOWN_SUBMISSION` / `409 CONFLICT`:** Xảy ra nếu mạng bị ngắt ngay khi vừa gửi job; bấm **`"Hủy lượt thử"`** hoặc **`"Xóa kết quả này"`** rồi thử lại.

Sau mỗi lần bấm Tạo ở LIVE, chụp kiểm chứng bằng: `./proxy/scripts/demo_try_cases.sh snap <tên_ca>`.

---

## 6. Nếu bị kẹt

1. **Nút `"Tạo ảnh thử đồ"` bị mờ không bấm được:**
   - Kiểm tra xem bạn đã chọn ít nhất `1` món trong 4 ô (`Áo`, `Quần`, `Mũ`, `Giày`) chưa.
   - Đọc ngay phía trên nút tạo xem có khung lỗi đỏ `"Không thể tạo mặt trước/mặt sau: thiếu ..."` không (nếu đang ở `"Chỉ mặt sau"` mà chưa chọn `person_back` hoặc ảnh sau của `Áo`/`Quần`, hãy bổ sung hoặc chuyển về `"Mặt trước"`).
   - Kiểm tra lại ô tick **`"Tôi đồng ý gửi những ảnh đã chọn cho riêng lượt thử đồ 2D này."`** (ứng dụng tự bỏ tick mỗi lần bạn thay/bỏ ảnh).
2. **Đổi backend giữa `mock` và `live` nhưng app vẫn giữ trạng thái cũ:**
   - Bấm nút **`"Làm mới"`** trên thanh tiêu đề `FITCHECK VTO DEMO` ở đầu màn hình để app gọi lại `/v2/tryon/capabilities`.
3. **Kẹt phiên đang chạy / `409 CONFLICT`:**
   - Bấm **`"Hủy lượt thử"`** hoặc **`"Xóa kết quả này"`** ngay trong màn Thử đồ.
4. **Kết thúc buổi thử:**
   - Chạy `./proxy/scripts/demo_try_cases.sh stop` để tắt server ở cổng `8788`.
   - Nếu vừa tự mở `TRYON_LIVE_ENABLED=true` trong `~/.fitcheckai_private/tryon.env`, nhớ đặt lại `TRYON_LIVE_ENABLED=false` để tránh tiêu tốn quota ngoài ý muốn.
