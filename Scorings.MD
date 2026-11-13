1. Demo AI trên điện thoại thật — tối đa 40 điểm

* 40 điểm: Đóng hẳn rồi mở lại ứng dụng trên điện thoại thật; luồng AI chính chạy trực tiếp.


* 20 điểm: Ứng dụng mở lại được trên điện thoại thật nhưng luồng AI chỉ chạy một phần hoặc chưa ổn định.


* 0 điểm: Không mở lại được ứng dụng trên điện thoại thật, AI không chạy trực tiếp, hoặc chỉ demo bằng trình duyệt, giả lập, ảnh hay video.



2. Vấn đề thật của người dùng — tối đa 35 điểm

* 35 điểm: Nêu rõ người dùng và khó khăn họ gặp, có bằng chứng, đồng thời cho thấy AI giúp trực tiếp.


* 18 điểm: Vấn đề khá rõ nhưng bằng chứng hoặc lợi ích của AI còn hạn chế.


* 0 điểm: Chưa làm rõ người dùng, vấn đề hoặc giá trị sản phẩm.



3. Khả năng mở rộng thị trường — tối đa 25 điểm

* 25 điểm: Giải thích rõ cách tiếp cận người dùng đầu tiên và mở rộng ra toàn cầu; đã có người dùng thực tế là một lợi thế.


* 13 điểm: Có hướng mở rộng nhưng cách tiếp cận người dùng hoặc lập luận còn yếu.


* 0 điểm: Chưa có hướng tiếp cận người dùng cụ thể, hoặc chỉ khẳng định chung rằng “ai cũng cần”.



Nếu hòa điểm: ưu tiên lần lượt tiêu chí 1, tiêu chí 2 rồi tiêu chí 3.

## Tự chấm nội bộ ngày 12/11/2026

| Tiêu chí | Điểm | Bằng chứng |
|---|---:|---|
| Demo AI trên điện thoại thật | 20/40 | Samsung API 36 mở lại app sau force-stop và chạy tách nền ML Kit trực tiếp trong form Tủ đồ. Today đang dùng phối cơ bản. Nhận diện và phối đồ live còn chờ proxy HTTPS. [QA tích hợp trên Samsung](docs/qa/track-a-b-handoff.md) · [Eval B](eval/ai/results/2026-11-12-luna-summary.md). |
| Vấn đề thật của người dùng | 18/35 | Đề bài mô tả khó khăn khi chọn và tận dụng quần áo đang có. Luồng thử nghiệm thể hiện cách tạo tủ và phối đồ. Nghiên cứu người dùng hiện có n=0. [Đề bài](Requirements.MD) · [Kế hoạch nghiên cứu](docs/research/track-a-study-plan.md). |
| Khả năng mở rộng thị trường | 13/25 | Kế hoạch chọn sinh viên HUST làm nhóm đầu tiên rồi mở rộng sang Việt Nam và Đông Nam Á. Kênh tiếp cận và chuyển đổi còn ở mức giả thuyết, số người tham gia pilot là 0. [Kế hoạch Track D](docs/plans/40-track-d-insights.md) · [Kế hoạch pilot](docs/research/track-a-study-plan.md). |
| **Tổng** | **51/100** | Tự chấm theo rubric trong repo. |

Điểm 51/100 đánh giá mức sẵn sàng của demo toàn dự án. Phần chấm giải thích recommendation theo nhiệm vụ B-004 dùng thang 1–5. [Điểm reviewer A](eval/ai/results/2026-11-12T12-48-27-674-recommendation.md#kết-quả-rà-soát-của-track-a).

Ngày 13/11/2026 giữ **51/100**. Bản CORE mới mở lại được trên Samsung, ML Kit chạy 6/6 mẫu và Today phối cơ bản khi mất mạng. Hai luồng AI qua proxy chưa được nghiệm thu trên điện thoại. Nghiên cứu người dùng và pilot vẫn có n=0. [Hồi quy CORE](docs/qa/track-a-core-regression.md).
