# Virtual Try-On — feasibility và runtime gate

[Track C](tracks/C.md) · [Contracts](CONTRACTS.md) · [Milestones](DEPENDENCIES_AND_INTEGRATION.md) · [Decisions](REVIEW_AND_DECISIONS.md)

Chưa chạy POC, chưa xác nhận account/budget hoặc output nào. Đây là protocol cho C-201…205 và C-211/212. Các ngưỡng là **đề xuất cần chốt trước thử nghiệm**, không phải năng lực/SLA provider. Không thực hiện lại nghiên cứu trong lượt tiếp tục tài liệu này.

## Giới hạn của nguồn hiện có

Nguồn đã đối chiếu trong lần revise trước: [FASHN Try-On Max](https://docs.fashn.ai/api-reference/tryon-max), [tham số](https://docs.fashn.ai/guides/tryon-parameters-guide), [retention/privacy](https://docs.fashn.ai/api-overview/data-retention-privacy), [Google Virtual Try-On](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/generate-virtual-try-on-images), [model instance](https://docs.cloud.google.com/gemini-enterprise-agent-platform/reference/rest/Shared.Types/VirtualTryOnModelInstance). C-201 cần kiểm lại khi chọn provider; đây không phải xác nhận quyền account hoặc cập nhật API trong lượt này.

API nhận ảnh người/garment không tự bảo đảm cặp front/back nhất quán hoặc toàn bộ outfit nhiều món. Nhiều samples không đồng nghĩa nhiều góc. Hai request độc lập phải đánh giá như hai request, không ghi native paired support. Retention, quyền xóa, vùng xử lý và chi phí phải xác nhận theo cấu hình/account; không suy “không training” thành “không lưu ảnh”. Chưa chọn provider mặc định.

## Trước khi gửi ảnh

| Điều kiện | Owner / evidence |
|---|---|
| BTC cho phép thử nghiệm trong cửa sổ đang dùng | B PRE-001, nguồn thông báo và disclosure spike |
| Account/model/category/view dùng được; retention/deletion được hiểu | C C-201, record có version/date; B review |
| Ảnh người/garment có quyền và consent đúng provider/purpose | C C-202, manifest private và cách revoke; ảnh ngoài Git |
| Trần tiền, số attempts, budget còn lại và kill switch | B+C ghi số cụ thể; chưa chốt số thì BLOCKED live |
| Lưu private, không log ảnh, cleanup POC | C review trước C-203; POC không chờ Android runtime |
| Runtime có storage thật và consent/revoke an toàn | C-210 tests + C-212 real-store integration, trước mở upload cho user |

Thiếu quyền/account/budget là BLOCKED. Có bằng chứng capability không đáp ứng hoặc nhóm từ chối privacy/cost là NO_GO. C-201/C-202 có thể hoàn thành báo cáo thiếu điều kiện; C-205 ghi quyết định mà không đợi API không dùng được. Không gọi báo cáo này là thử nghiệm PASS.

## Bộ thử và rubric đề xuất

C-203 chạy ít nhất 3 case front; C-204 dùng ít nhất 3 cặp có personFront/personBack và garmentFront/garmentBack thực. Cố định inputs, labels, outfit revisions, provider/config và tiêu chí trước thử. Có single garment/dress; kiểm TOP+BOTTOM nếu muốn claim multi-item. Chỉ một outfit mỗi job; subset ghi applied/unapplied item IDs. Không dùng front thay ảnh lưng.

Hai reviewer chấm độc lập (C và A; B đối soát cost/protocol). Điểm 1–5: 1 sai rõ, 3 có lỗi ảnh hưởng sử dụng, 4 có lỗi nhỏ không thay đổi garment/pose, 5 giữ tốt chi tiết quan sát được. Không dùng nhận diện sinh trắc hoặc suy ra số đo.

| Tiêu chí | PASS đề xuất từng case / cả hai reviewer |
|---|---|
| Identity/appearance khi thấy mặt | ≥4; không đổi người rõ rệt; ảnh lưng không ép tiêu chí khuôn mặt |
| Silhouette/body và pose | ≥4; không reshape rõ, thêm/mất chi hoặc crop che lỗi |
| Garment fidelity | ≥4 từng món được claim: category, màu, hoa văn/logo, chiều dài/layer; không thay món khác |
| Pair consistency | DUAL: ≥4 cho hình thể, màu/layer/chi tiết giữa hai góc; lưng đúng reference |
| Coverage/provenance | Applied IDs khớp snapshot; mọi món bị bỏ/góc chưa đạt hiển thị rõ |

GO cần **3/3 case** đạt các tiêu chí áp dụng; không lấy trung bình che case hỏng. Ghi mọi retry/failure, không cherry-pick. Bất đồng reviewer thì chưa PASS cho tới review chung có lý do. Ba case là gate nhỏ cho demo, chưa chứng minh reliability trên người dùng thực.

Latency target đề xuất: mỗi case front ≤60s; cặp hoàn chỉnh ≤120s, từ submit đến đủ output; báo từng lần và max, không gọi p95 với n=3. Khác technical timeout CT-07 (120s front/180s pair). Cost gồm mọi attempt/cả hai view và nằm trong trần per-case/tổng ngân sách đã chốt; thiếu số được duyệt thì không GO. Đổi ngưỡng phải version rubric **trước** thử mới, không hạ ngưỡng sau khi xem ảnh để gọi PASS.

## Quyết định và tác động

| Decision C-205 | Evidence bắt buộc | Tiếp theo / tác động |
|---|---|---|
| BLOCKED | Thiếu điều kiện cụ thể, owner và điều kiện mở lại | Capability UNKNOWN; tiếp tục fake/privacy/core, chưa live |
| NO_GO | Evidence giới hạn hoặc quyết định privacy/cost có lý do | CORE; mục tiêu VTO chưa đạt, collage không thay VTO |
| GO_FRONT_ONLY | C-203 measured PASS + account/privacy/cost/latency đạt | Runtime front; công bố chưa có mặt sau |
| GO_DUAL | C-203/204 paired PASS, coverage rõ, các điều kiện trên | Runtime dual; không claim native paired nếu hai requests |

C-205 DONE có thể là BLOCKED/NO_GO. M4-F chỉ FRONT/DUAL PASS khi kết quả đo tương ứng đạt; M4-R cần runtime. C-208/C-209 làm với fake trước GO; C-207 cần GO và durable job store được quyền dùng. C-210 có thể xây bằng fake; upload runtime cần cleanup/consent wiring thật. C-211 chỉ nghiệm thu DUAL; C-212 nghiệm thu FRONT hoặc DUAL, DUAL cần C-211 PASS. Dual thất bại chỉ được mở FRONT khi front đã nghiệm thu riêng.

## Mẫu evidence khi thực thi

Không có hàng PASS sẵn. Metadata công khai phải bỏ PII; input/output private theo consent/retention đã chốt.

| Trường | Nội dung cần ghi |
|---|---|
| Gate | Task ID, report revision, reviewers, thời điểm, decision/lý do |
| Capability | Provider/model/config, categories/views, max garments, native paired hay independent |
| Input | Case ID, outfit/item revisions, views, consent reference; không public ảnh/path PII |
| Measurements | Job/request IDs, attempts kể cả lỗi, latency từng view/cặp, cost/cách tính và budget còn |
| Quality | Điểm từng reviewer/tiêu chí/case, lỗi, coverage và kết luận |
| Privacy | Retention/deletion, local cleanup, revoke/late completion, remote deletion state |
| Product | Mode được phép, claim bị loại, task còn mở, điều kiện thử lại |

Runtime thêm double-submit/idempotency, unknown submission không tự trả phí lại, restart resume GET, partial/back fail, expiry, stale input, delete/revoke chặn late result. Transport SUCCEEDED không tự là quality PASS hoặc runtime PASS.
