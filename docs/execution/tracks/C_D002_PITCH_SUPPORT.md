# D-002 — Chuyển Evidence thành Kịch bản Pitch 5 phút có Giới hạn (Evidence-Backed Pitch Support)

- **Task ID:** `D-002`
- **Owner:** Track C (phối hợp review với Track B tìm overclaim và Track D xác nhận khớp APK thực tế)
- **Ngày cập nhật:** `2026-11-11`
- **Nguồn đối chiếu sự thật (Evidence Sources):**
  - Khảo sát Provider & Quyền riêng tư: [C_201_PROVIDER_RESEARCH.md](file:///home/lamb/Project/FitCheckAI/docs/execution/tracks/C_201_PROVIDER_RESEARCH.md)
  - Báo cáo Dataset & Provenance: [C_202_DATASET_READINESS.md](file:///home/lamb/Project/FitCheckAI/docs/execution/tracks/C_202_DATASET_READINESS.md)
  - Biên bản Feasibility Gate VTO: [C_205_FEASIBILITY_GATE.md](file:///home/lamb/Project/FitCheckAI/docs/execution/tracks/C_205_FEASIBILITY_GATE.md)
  - Hợp đồng Kiến trúc & Quyền riêng tư v2: [CONTRACTS.md](file:///home/lamb/Project/FitCheckAI/docs/execution/CONTRACTS.md)

---

## 1. Nguyên tắc Trung thực Bắt buộc (Zero-Overclaim Guardrails)

1. **Không thổi phồng số liệu người dùng hoặc hiệu quả tiết kiệm thời gian** khi chưa có nghiên cứu định lượng hoàn chỉnh (`PRE-005` chỉ ghi nhận đúng cỡ mẫu `n` thực tế khi nhóm thu thập xong; hiện tại ghi rõ là **giả thuyết sản phẩm cần kiểm chứng**).
2. **Phân biệt rạch ròi 3 khái niệm:**
   - **Bảng ghép món đồ (Outfit Preview / Swatch Strip):** Chỉ hiển thị các món được chọn từ tủ đồ cá nhân, không phải AI Try-On.
   - **Mô phỏng thử đồ 2D trên ảnh thật (2D Virtual Try-On Simulation):** Mô phỏng thị giác giúp hình dung cách phối màu và phom dáng tổng quát trên ảnh chụp người thật 2D.
   - **Độ vừa vặn ngoài đời thực (Physical Fit & Sizing):** FitCheckAI **không** dựng avatar 3D, **không** đo cơ thể từ ảnh và **không** cam kết quần áo mặc vừa kích cỡ ngoài đời.
3. **Minh bạch trạng thái cổng kiểm định VTO (`C-205`):**
   - Pipeline kỹ thuật end-to-end (Android UI `TryOnScreen`, `AndroidVirtualTryOnClient` khôi phục sau process death, `TryOnPrivacyCoordinator` kiểm soát consent theo từng lượt, và Server Adapter `/v2/tryon/jobs` chống gửi trùng) **đã được hiện thực hóa và kiểm thử tự động**.
   - Chế độ gọi provider AI trả phí trực tiếp (`AI_LIVE`) hiện đang giữ khóa an toàn (`BLOCKED` -> `TryOnCapability.decision = UNKNOWN`) cho tới khi hoàn tất cấp khóa API, ngân sách thử nghiệm (\$\le 1.88 / 25 credits) và bộ ảnh tình nguyện viên có đồng ý riêng (`C-202`). Khi demo ở chế độ `FAKE`, giao diện luôn gắn nhãn `CHẾ ĐỘ GIẢ LẬP DEV (FAKE)` rõ ràng.

---

## 2. Outline Tối đa 5 Slide (Gọn nhẹ, Không cần Công cụ Trả phí)

| Slide | Tiêu đề | Nội dung cốt lõi hiển thị trên màn hình | Thời lượng |
|---|---|---|---|
| **Slide 1** | **Tủ đồ đầy nhưng sáng nào cũng "không có gì để mặc"** | Bài toán tận dụng trang phục sẵn có (wardrobe utilization) thay vì mua sắm thêm; mục tiêu của FitCheckAI: nhập tủ đồ cá nhân $\rightarrow$ gợi ý tối đa 5 bộ phù hợp ngữ cảnh $\rightarrow$ mô phỏng thử đồ 2D có kiểm soát quyền riêng tư. | `0:00 – 0:45` (45s) |
| **Slide 2** | **Kiến trúc Local-First & Quyền riêng tư mặc định** | Sơ đồ 4 bước: Tủ đồ cục bộ trên máy $\rightarrow$ Chỉ gửi siêu dữ liệu tối giản khi gợi ý phối đồ $\rightarrow$ Xin `ConsentGrant` riêng cho từng lượt thử đồ $\rightarrow$ Xóa sạch bản lưu cục bộ & chặn kết quả trễ khi thu hồi quyền. | `0:45 – 1:30` (45s) |
| **Slide 3** | **Live Demo trên Thiết bị / Emulator (90–110 giây)** | Trình diễn trực tiếp trên APK thật (không dùng video dựng giả): xem bộ đồ đã chọn $\rightarrow$ kiểm tra trạng thái ảnh mặt trước/mặt sau $\rightarrow$ xác nhận quyền riêng tư $\rightarrow$ theo dõi tiến trình bất đồng bộ, xử lý lỗi một phần (`PARTIAL`) và xóa kết quả tạm. | `1:30 – 3:15` (105s) |
| **Slide 4** | **Bằng chứng Kỹ thuật & Giới hạn Thẳng thắn của AI 2D** | Bảng đối chiếu những gì hệ thống làm được (chống tính phí 2 lần khi mất mạng/xoay màn hình, tách bạch mặt trước và mặt sau) so với giới hạn thực tế (không đo size cơ thể, hai góc nhìn sinh độc lập chưa có cam kết đồng nhất tự nhiên từ provider). | `3:15 – 4:15` (60s) |
| **Slide 5** | **Kế hoạch Kiểm chứng Thị trường & Kinh tế Đơn vị** | Chi phí ước tính minh bạch theo tài liệu chính thức FASHN AI (`\$0.075`/ảnh ở `tryon-v1.6`), cơ chế trần ngân sách cứng trên proxy, và kế hoạch kiểm thử thực địa nhỏ (`n = 15–20` sinh viên/nhân viên văn phòng trẻ) sau sự kiện. | `4:15 – 5:00` (45s) |

---

## 3. Kịch bản Thuyết trình Chi tiết 5 Phút (Đọc thử ~620 từ — Khớp 4 phút 50 giây)

### Phần 1 — Mở đầu & Bài toán thực tế (`0:00 – 0:45`)
> "Chào Ban giám khảo. Hầu hết các ứng dụng thời trang AI hiện nay đều cố thuyết phục người dùng **mua thêm quần áo mới** từ sàn thương mại điện tử. FitCheckAI giải quyết bài toán ngược lại: giúp người dùng **mặc hiệu quả hơn những món đồ họ đã sở hữu trong tủ**.
>
> Hành trình cốt lõi của chúng tôi bắt đầu từ việc số hóa tủ đồ trên thiết bị, nhận diện thuộc tính trang phục có bước người dùng kiểm duyệt lại, gợi ý tối đa 5 bộ phối phù hợp hoàn cảnh trong ngày, và cuối cùng — cho phép người dùng xem trước mô phỏng 2D bộ trang phục đã chọn trên ảnh toàn thân của chính họ."

### Phần 2 — Kiến trúc Quyền riêng tư & Độ tin cậy (`0:45 – 1:30`)
> "Vì ảnh toàn thân là dữ liệu nhạy cảm, chúng tôi thiết kế FitCheckAI theo nguyên tắc **local-first và tối thiểu hóa dữ liệu**. Khi bạn lưu ảnh hồ sơ, không có bất kỳ byte ảnh nào rời khỏi điện thoại. Chỉ khi bạn chọn **một** bộ trang phục cụ thể và bấm xác nhận đồng ý cho riêng lượt thử đó, ứng dụng mới trích xuất đúng góc nhìn được phép, kiểm soát dung lượng dưới ngưỡng an toàn và gửi qua máy chủ trung gian với chế độ `return_base64` — nghĩa là ảnh kết quả không bị lưu lại 72 giờ trên CDN công khai của nhà cung cấp."

### Phần 3 — Trình diễn Thực tế trên Ứng dụng (`1:30 – 3:15` — 105 giây)
*(Mở trực tiếp màn hình ứng dụng trên máy Android / Emulator)*:
1. **Hiển thị trang phục đang chọn (`25s`):** Chỉ rõ các món được đưa vào mô phỏng (ví dụ: Áo sơ mi linen kem + Quần âu đen) và các món được hệ thống tự động ghi chú minh bạch là chưa hỗ trợ mô phỏng như Giày loafer — tuyệt đối không đánh lừa người dùng rằng một món là toàn bộ outfit.
2. **Kiểm tra góc nhìn & thiếu ảnh mặt sau (`25s`):** Cho thấy khi người dùng chỉ có ảnh mặt trước, ứng dụng không tự bịa ra ảnh mặt sau mà hướng dẫn bổ sung tại Hồ sơ hoặc cho phép chạy chế độ `Chỉ mặt trước`.
3. **Xác nhận quyền riêng tư & Tiến trình bất đồng bộ (`30s`):** Tích chọn ô đồng ý theo lượt, nhấn **Tạo ảnh thử đồ**, quan sát tiến trình xử lý và khung kết quả tỷ lệ `3:4` có nút **So sánh ảnh gốc**. *(Nếu đang chạy bản demo `FAKE` offline khi chưa bật khóa live, chỉ rõ huy hiệu `CHẾ ĐỘ GIẢ LẬP DEV` ngay trên đầu màn hình).*
4. **Xử lý lỗi trung thực & Dọn dẹp (`25s`):** Chuyển qua tab `Mặt sau` trong kịch bản lỗi một phần (`PARTIAL`) để chứng minh ứng dụng giữ nguyên thông báo lỗi góc nhìn sau chứ không ghép ảnh giả; sau đó bấm **Xóa kết quả & ảnh tạm** để xóa ngay tệp cục bộ.

### Phần 4 — Minh bạch Giới hạn Kỹ thuật của VTO 2D (`3:15 – 4:15`)
> "Tại FitCheckAI, chúng tôi coi việc công bố rõ giới hạn của AI quan trọng ngang với tính năng. Qua khảo sát tài liệu kỹ thuật chính thức của FASHN AI và Google Vertex AI VTO:
> - Thứ nhất, **chưa có API thương mại nào cam kết đồng nhất tự nhiên giữa mặt trước và mặt sau trong cùng một lệnh gọi** — nếu tạo hai mặt, đó là hai tiến trình độc lập và chi phí tăng gấp đôi.
> - Thứ hai, ảnh thử đồ 2D là **mô phỏng phối đồ thị giác**, không phải công cụ đo cơ thể hay tư vấn size may mặc.
> - Thứ ba, trên kết nối di động chập chờn, client Android và server adapter của chúng tôi lưu khóa chống trùng (`idempotencyKey`) trước khi gửi yêu cầu: nếu ứng dụng bị hệ điều hành đóng giữa chừng, hệ thống tự động nối lại việc kiểm tra trạng thái hoặc báo `UNKNOWN_SUBMISSION`, tuyệt đối không tự động gửi lại gây tốn phí hai lần."

### Phần 5 — Chi phí & Kế hoạch Kiểm chứng Tiếp theo (`4:15 – 5:00`)
> "Về tính khả thi kinh tế, mỗi lượt mô phỏng một góc nhìn có giá niêm yết khoảng `\$0.075` trên `tryon-v1.6` — vì vậy chúng tôi không bao giờ tự động sinh ảnh cho cả 5 bộ gợi ý cùng lúc mà chỉ chạy khi người dùng chọn 1 bộ duy nhất, kết hợp trần ngân sách cứng ở tầng proxy.
>
> Bước tiếp theo ngay sau vòng hoàn thiện cổng dữ liệu tình nguyện viên (`C-202`), nhóm sẽ chạy bộ đánh giá chéo 2 người chấm trên 3 cặp trang phục chuẩn (`C-203`/`C-204`) để quyết định mở chế độ `FRONT_ONLY` thực tế cho nhóm thử nghiệm đầu tiên gồm 15–20 người dùng trẻ tại đô thị. Xin cảm ơn Ban giám khảo!"

---

## 4. Bảng Đối chiếu Claim $\rightarrow$ Bằng chứng $\rightarrow$ Giới hạn (Audit Matrix cho Track B & D)

| STT | Tuyên bố trong bài Pitch (Claim) | Nguồn bằng chứng xác thực | Giới hạn bắt buộc phải nêu kèm |
|---|---|---|---|
| 1 | Chỉ thử đồ cho **1 bộ đã chọn**, không sinh hàng loạt cho 5 bộ gợi ý. | `TryOnViewModel.kt`, `CONTRACTS.md` (`CT-06`), `proxy/src/tryon/handler.ts` | Giảm chi phí API gấp 5 lần; người dùng cần bấm xác nhận cho từng bộ muốn xem. |
| 2 | Không gửi ảnh khi lưu hồ sơ; `confirmedByUser = false` đồng nghĩa **0 byte** được đọc để tải lên. | `TryOnPrivacyCoordinator.kt` + unit test `TryOnPrivacyCoordinatorTest` | Khi người dùng đã đồng ý tải lên ở chế độ `AI_LIVE`, ảnh sẽ đi qua nhà cung cấp AI bên thứ ba. |
| 3 | Chế độ `return_base64: true` của FASHN tránh lưu ảnh kết quả 72 giờ trên CDN công khai. | Tài liệu chính thức FASHN AI khảo sát ngày `2026-11-11` trong `C_201_PROVIDER_RESEARCH.md` | Nhà cung cấp vẫn giữ trạng thái tiến trình tối đa **60 phút** và không có API xóa tức thời từ xa (`BASE64_EPHEMERAL_60M`). |
| 4 | Không gửi trùng yêu cầu trả phí khi xoay màn hình hoặc ứng dụng bị tắt đột ngột. | `AndroidVirtualTryOnClient.kt` + `AtomicFileTryOnLocalJobStore` + `AndroidVirtualTryOnClientTest` | Nếu mạng đứt ngay lúc gửi `POST` trước khi nhận `jobId`, trạng thái sẽ là `UNKNOWN_SUBMISSION` để người dùng chủ động kiểm tra. |
| 5 | Phân biệt rõ kết quả mặt trước (`FRONT`), mặt sau (`BACK`) và hoàn tất một phần (`PARTIAL`). | `TryOnScreen.kt`, `FakeVirtualTryOnServiceTest`, `proxy/test/tryon.test.ts` | Chế độ `AI_LIVE` hai mặt hiện vẫn đang ở trạng thái cổng `BLOCKED` (`C_205_FEASIBILITY_GATE.md`) chờ bộ ảnh tình nguyện viên và khóa API được duyệt. |
| 6 | Không dự đoán kích cỡ quần áo hoặc số đo cơ thể. | Hiển thị cố định trên `TryOnScreen` (`PRODUCT_LIMITATION_DISCLAIMER`) | Chỉ hỗ trợ tham khảo thẩm mỹ phối màu/kiểu dáng 2D. |
