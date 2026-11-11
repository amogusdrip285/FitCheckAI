# Roadmap theo trạng thái và runbook capacity

[Workflow](../../TEAM_WORKFLOW.md) · [Board](TASK_BOARD.md) · [Milestones](DEPENDENCIES_AND_INTEGRATION.md) · [Decisions](REVIEW_AND_DECISIONS.md)

Không chia task theo ngày/khung giờ cố định. Ngày sự kiện dự kiến **14/11/2026**, cửa sổ build dự kiến 11–16h UTC+7 chỉ là ràng buộc logistics cần PRE-001 xác nhận, không phải acceptance hoặc lịch giao từng task.

## Capacity và cân bằng tải

Từ card hiện tại: **46 P0 = 2.095–3.495 phút công (34h55–58h15)**. Tất cả 68 active = 3.520–5.960 phút (58h40–99h20). Bao gồm local tests của owner; không gồm chờ API/người dùng/phê duyệt. P0 là backlog sản phẩm thiết yếu, không phải lời hứa hoàn tất trong sự kiện.

| Owner | P0 phút | P1 phút | P2 phút | Tổng owner | Support ngoài task owner, dự trù cả roadmap |
|---|---:|---:|---:|---:|---:|
| A | 610–1010 | 155–260 | 525–890 | 1290–2160 | 40–70 |
| B | 560–930 | 265–485 | 0–0 | 825–1415 | 35–60 |
| C | 320–595 | 360–590 | 120–240 | 800–1425 | 20–40 |
| D | 605–960 | 0–0 | 0–0 | 605–960 | 50–90 |
| Tổng | 2095–3495 | 780–1335 | 645–1130 | 3520–5960 | 145–260 |

Support ledger đề xuất: A hỗ trợ shell/device 10–20, VTO review 15–25, artifact 15–25; B hỗ trợ VTO protocol/budget 15–25, regression/artifact 20–35; C hỗ trợ AI/fixture review 10–20, rehearsal 10–20; D hỗ trợ root seams trong INT-001/002 30–50, regression/artifact review ngoài task mình 20–40. Không cộng lại phút owner đang làm INT-001/002/003/004 hoặc D-007. Đây là dự trù capacity, không thêm dependency hoặc task ID. Ghi thời gian thực để sửa ước lượng sau checkpoint.

D có 14 P0 nhưng đã chuyển visual component/QA/local integration/regression sang A, live AI integration sang B, TryOn UI/pipeline và pitch mở rộng sang C. D giữ Profile/Today/root và điều phối; không nhận sửa hộ bug module. A có backlog future dài, không phải tất cả làm cùng phiên. C có ít P0 code hơn vì feasibility; khi GO, C-207…212 thêm **330–545 phút**, không xem đó là thời gian rảnh. B có rủi ro provider/validation cao, không giao thêm app UI.

5 giờ × 4 = 1.200 phút công danh nghĩa; trừ 30 phút ăn/nghỉ mỗi người còn **1.080 phút**, chưa trừ support/context switching/chờ build. P0 tối thiểu đã vượt trần này. Đường dependency cũng vượt cửa sổ, nên không có cơ sở cam kết full CORE hoặc DUAL từ checkout chỉ có tài liệu. Không chuyển toàn bộ code sang “chuẩn bị” để làm phép tính trông khả thi.

## Chuẩn bị hợp lệ và chọn phạm vi sự kiện

Được chuẩn bị theo luật đã xác nhận: nghiên cứu, wireflow/contracts, quyền dữ liệu, environment/device và template công khai có disclosure. POC gọi thật chỉ trong cửa sổ BTC cho phép, có consent/account/budget. Core Android, proxy nghiệp vụ, prompt sản phẩm và tích hợp cần phần phát triển mới đáng kể trong sự kiện; chỉ nối API vào sản phẩm làm sẵn không được mặc định là đủ.

**Quyết định mặc định cho kế hoạch demo: chưa cam kết full P0; giữ VTO runtime và toàn bộ P1/P2 ngoài phạm vi cam kết sự kiện.** Chỉ mở lại khi gate đạt và remaining effort/test thực sự vừa capacity. C tiếp tục feasibility được phép và báo evidence/giới hạn; không phải tự hứa dual runtime.

Khi xác nhận thể lệ và năng lực, D cùng owner ghi một scope record ở cột evidence của board: mode/partial demo, task IDs, phần acceptance định thực hiện, phần còn thiếu, remaining effort từng người, support và buffer. Đề xuất lát cắt ưu tiên khi không đủ CORE: shell → nhập garment thủ công/lưu Room → Today chọn đồ thật qua recommendation live → máy thật/artifact. Tập trung S-001/002, A-005/001/002/003, B-005/001/002/003/004, D-006, C-001/003 và kiểm liên quan; các bước tích hợp lấy từ INT cards nhưng không đánh DONE toàn card khi scope chưa đủ.

Lát cắt này **thiếu recognition, full Profile/body photos, CRUD đầy đủ hoặc VTO nếu chưa làm**; không đạt M2/M3/M5-CORE của sản phẩm mới. Đây là phương án giảm scope minh bạch, không quay lại baseline cũ như một yêu cầu thay thế. Nếu remaining effort vẫn vượt capacity, giảm phần UI/profile preview và số thao tác demo, hoặc báo chưa có demo đủ điều kiện; không hứa APK live chỉ nhờ cắt features. Owner cập nhật estimate theo kỹ năng thực, không hạ estimate để khớp 5 giờ.

## Pull queue và các checkpoint

| Khi nào | Hành động | Người chịu trách nhiệm |
|---|---|---|
| Bắt đầu phiên làm việc | Chọn task priority cao có dependency đạt, kiểm quyền thực hiện; một DOING/người | Mỗi owner |
| Boundary đủ dùng | Handoff contract revision, fixtures, tests; tích hợp nhỏ ngay | A/B/C/D theo seam |
| Có blocker | Ghi lý do, người gỡ, điều kiện mở lại; lấy việc độc lập | Owner; D điều phối ưu tiên |
| M2/M3 hoặc VTO gate có evidence mới | Cập nhật mode, remaining work/cost, không suy milestone khác đã xong | A/B/C cung cấp, D ghi quyết định |
| Thời gian còn lại bằng dự trù regression/artifact/rehearsal | Freeze feature; chỉ sửa lỗi chặn mode đã chọn | A QA, D điều phối, owner sửa |
| Artifact đổi | Retest phần ảnh hưởng và smoke luồng demo trên chính artifact | A+B+C theo mode, D đóng gói |

Dự phòng tham khảo **60–90 phút cuối phiên** cho regression, fixes, cài APK, diễn tập và nộp; không giao task theo giờ cứng và không tính thời gian đó là chỗ trống cho feature mới. Handoff liên tục; nếu gate không đạt thì giảm claim/mode, không tick DONE theo đồng hồ. Ngày chuẩn bị chỉ là cơ hội làm việc, không biến thành deadline mỗi task.

## Nhánh xử lý blocker

| Blocker | Việc vẫn làm được | Điều kiện quay lại / giới hạn |
|---|---|---|
| Shell/toolchain | A fixtures, B protocol/eval, C provider/dataset, D sửa root tối thiểu | PRE-002/S-001 thật; không gọi mockup là Android đã chạy |
| Recognition/reco API hoặc budget | B-005/003/007 fixtures, A tag-edit UI, D Today fake | B live checks; typed error offline, RULES chỉ khi C-002 làm xong |
| Room/media thực | A-003/A-006 qua fake, D profile và C state previews | INT-001/A-004; seed-only không chứng minh persistence |
| VTO NO_GO/BLOCKED | Core + C-206/C-210; C-208/209 fake nếu còn capacity | CORE unavailable VTO; mục tiêu dual còn mở |
| Front đạt, back không đạt | Runtime FRONT nếu đã C-212 PASS | Không hiển thị ảnh lưng giả hoặc claim toàn outfit nếu chỉ một garment |
| Lỗi mất dữ liệu/privacy/billing duplicate | Dừng upload/feature ảnh hưởng, owner sửa; giữ artifact đã kiểm | Retest failure case và journey trước bật lại |

Không cắt auth/consent/ID validation/persistence đúng nguồn để đổi lấy feature. Cắt P2/P1 và polish trước; cắt P0 thì công bố partial demo và giữ task chưa đạt. Không cắt recognition P0 rồi gọi M3 PASS.

## Bắt đầu độc lập và việc tiếp theo

| Track | Bắt đầu ngay | Sau contracts/root sẵn |
|---|---|---|
| A | PRE-002, PRE-004; PRE-005 nếu readiness bị chặn | A-005 → A-003/A-006; A-001/A-002 theo capacity cùng owner |
| B | PRE-001, PRE-003, PRE-008 | B-005 → client/validator; B-001 khi điều kiện account/rules đạt |
| C | C-201, C-202 | C-206/privacy; POC không cần wardrobe/profile thật nhưng cần quyền và budget |
| D | PRE-006 → PRE-007 | S-001/002 → D-006 → Profile/Today; không chờ live AI |

“Độc lập” nghĩa có việc không cần track khác hoàn tất ngay từ đầu; feature code vẫn dùng contract chung. Nếu chưa được phép code trước sự kiện thì chỉ làm readiness/thiết kế, không lách dependency bằng implementation chưa khai báo.

## Demo và sau demo

Kịch bản ngắn theo mode thực: vấn đề và n nghiên cứu thật → mở lại dữ liệu → yêu cầu phối đồ → giải thích từng outfit từ ID có thật → chọn một outfit và VTO chỉ khi runtime gate đạt → giới hạn/chi phí/quyền riêng tư → việc cần kiểm chứng tiếp. Chọn 1 hoặc ít hơn 5 bộ khi tủ không đủ khác nhau là hợp lệ có lý do. Mạng lỗi hiện lỗi/RULES đúng nhãn; video hay ảnh lưu chỉ là evidence ghi trước, không AI live.

D-003 có phần trình bày cơ bản dù D-002 pitch mở rộng P1 chưa làm. Giữ receipt/checksum và disclosure trước/sau khi nhóm thực thi. Sau sự kiện, ưu tiên P0 chưa đạt rồi P1 theo rủi ro/nhu cầu; pilot BACK-009 chỉ sau hardening BACK-011. Theo dõi pilot theo thời gian tham gia thực, không chia cứng theo tuần lịch hoặc khôi phục behavioral tracking đã park.
