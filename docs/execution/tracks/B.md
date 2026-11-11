# Track B — AI Recognition & Recommendation

[Workflow](../../../TEAM_WORKFLOW.md) · [Board](../TASK_BOARD.md) · [Contracts](../CONTRACTS.md) · [Milestones](../DEPENDENCIES_AND_INTEGRATION.md)

Task card ở file này là nguồn scope/acceptance. Trạng thái chỉ ở Board; mọi task còn TODO. ID cũ giữ ổn định dù prefix khác owner: **owner trong bảng là người chịu trách nhiệm hiện tại**. Chọn task có dependency đạt theo priority, không theo ngày. Mọi đường dẫn source là deliverable tương lai, chưa có implementation.

## Hàng đợi P0

<a id="pre-001"></a>

## PRE-001 — Chốt thể lệ và disclosure

**Mục tiêu:** Chốt thể lệ và disclosure.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-001 |
| 2. Task name | Chốt thể lệ và disclosure |
| 3. Description | Ghi phần đã tồn tại và điều kiện xây mới; hỏi BTC các điểm chưa công bố nếu cần, không tự suy diễn rubric. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | Thông báo BTC, trang Luma, snapshot tài liệu hiện có; DEC-05/08 trong review. |
| 8. Deliverables | Readiness record: quy định thử nghiệm/POC trước sự kiện, disclosure trước/sau, yêu cầu nộp. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Có nguồn, ngày kiểm tra, danh mục pre-existing; không coi rubric nội bộ là chính thức khi chưa có bằng chứng. |
| 11. Testing | D review nguồn/thông báo cuối và disclosure; không có test code. |
| 12. Estimated effort | 20–35 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | BTC trả lời muộn; đăng ký chưa được duyệt. |
| 14. Fallback / Simplification | Nếu chưa xác nhận quyền thử nghiệm trước, chỉ research/contract/environment; không viết core trước cửa sổ được phép. |
| 15. Definition of Done | Có rule matrix và câu hỏi chưa được BTC trả lời; không gọi chưa xác nhận thành được phép. |

1. Ghi baseline hash/file, public templates và mọi spike nếu được phép.
2. Xác nhận với BTC việc pre-event POC/model trials và phần mới đáng kể cần xây; không mặc định nối API là đủ.
3. Ghi yêu cầu demo/submit/rubric với nguồn; nhóm tự liên hệ BTC, audit không gửi tin.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="pre-003"></a>

## PRE-003 — Xác minh provider và ngân sách

**Mục tiêu:** Xác minh provider và ngân sách.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-003 |
| 2. Task name | Xác minh provider và ngân sách |
| 3. Description | Chọn một provider/model có thể dùng trong ngày và account proxy đã có quyền truy cập; không mua tài nguyên. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | CT-02/05; danh mục provider hiện hành và tài khoản nhóm; không mặc định credit tương thích. |
| 8. Deliverables | Matrix model: vision nhiều ảnh, structured output, region/quota, privacy, giá thực kiểm tra, budget và người giữ key. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Có quyết định có/không thể dùng với bằng chứng account; nếu chưa có quota/credit thì ghi BLOCKED rõ. |
| 11. Testing | Checklist read-only account; review tính toán chi phí bởi D; không gọi API có phí trong audit. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Credit khác provider; region/model access; không có hard budget cap. |
| 14. Fallback / Simplification | Dùng provider khác chỉ khi nhóm đã có quyền/ngân sách và adapter fit CT-02; không có thì không thể cam kết AI live. |
| 15. Definition of Done | DEC-03/04 có người chịu trách nhiệm và trạng thái, không lưu secrets vào repository. |

1. So tối đa 2 provider cho tagging/recommendation; kiểm model/account thực, không hardcode tên từ Master Plan.
2. Kiểm account proxy và ngân sách: recognition/recommendation tách khỏi budget VTO của C.
3. Ghi readiness/blocked, kill switch và giới hạn request; live smoke chỉ khi có quyền và budget.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="pre-008"></a>

## PRE-008 — Phân vai theo kỹ năng và rehearsal rủi ro

**Mục tiêu:** Phân vai theo kỹ năng và rehearsal rủi ro.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-008 |
| 2. Task name | Phân vai theo kỹ năng và rehearsal rủi ro |
| 3. Description | Xác minh năng lực thực tế để không gán ngầm A/B/C/D theo tên. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | Bốn thành viên; runbook, readiness và khả năng đã tự khai/đã làm trước đây. |
| 8. Deliverables | Bảng nhận vai/kỹ năng/backup và capacity thực tế theo milestone, không gán tên trong audit. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Mỗi vai có một người nhận; A hỗ trợ D thiết bị, C hỗ trợ B payload; tách root integration D khỏi AI ownership B, mỗi module tự sửa lỗi. |
| 11. Testing | Tabletop 10 phút với cả nhóm; không cần code. |
| 12. Estimated effort | 20–35 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Chưa đủ 4 người hoặc không ai quen Android. |
| 14. Fallback / Simplification | Giảm scope UI trước; nếu không có người dựng Android thì phải đánh giá lại kế hoạch, không giữ lịch giả. |
| 15. Definition of Done | Có owner thực tế và ai thay ai khi bị chặn, nhưng tài liệu audit vẫn dùng vai A/B/C/D. |

1. Xác định A quen Room/media, B HTTP/AI, C image API/async, D Android/lifecycle; ghi khoảng giờ thực có.
2. Đi qua block API/VTO privacy/Gradle; B nhận giấy tờ, A hỗ trợ QA, C sở hữu TryOn UI.
3. Nếu D vượt capacity, giảm UI polish hoặc bàn giao component có owner rõ; không mặc định coding agent xóa bottleneck.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-004"></a>

## BACK-004 — Recognition proxy cho ảnh trước/sau

**Mục tiêu:** Recognition proxy cho ảnh trước/sau.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-004 |
| 2. Task name | Recognition proxy cho ảnh trước/sau |
| 3. Description | Triển khai POST /v2/recognition cho front/back và structured ClothingAttributes theo CT-05; đây là P0 độc lập với endpoint recommendation. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [B-001](../tracks/B.md#b-001), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-05 RecognitionRequest/Result; photo refs/upload consent garment; provider capability PRE-003. |
| 8. Deliverables | proxy/recognition route/schema/prompt/validator + test fixtures. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Missing back vẫn tag front; unknown/conflicting fields có cờ sửa; ảnh khác món không silently merge; no invalid enums. |
| 11. Testing | HTTP mock vision, bad mime,missing back,conflict,timeout; live eval ở B-006. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Ảnh tăng chi phí/PII; model chưa hỗ trợ mode chọn. |
| 14. Fallback / Simplification | Manual edit vẫn lưu; M3 recognition chưa pass nếu chỉ mock/manual. |
| 15. Definition of Done | Recognition contract tests pass; B-007 cung cấp Android client riêng, A không tự viết provider adapter. |

1. Nhận front bắt buộc/back optional; kiểm cùng item revision, mime/decoded bytes và cap ảnh.
2. Model trả attributes nullable + evidence/conflicts theo field; không suy đoán material như chắc chắn.
3. Validate enums và ID/revision; trước/sau mâu thuẫn báo NEEDS_REVIEW, không last-write-wins.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="int-002"></a>

## INT-002 — Tích hợp recognition và recommendation live

**Mục tiêu:** Tích hợp recognition và recommendation live.

| Trường | Nội dung |
|---|---|
| 1. Task ID | INT-002 |
| 2. Task name | Tích hợp recognition và recommendation live |
| 3. Description | Thay fake generator bằng HTTPS client và chạy vertical slice thật. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [INT-001](../tracks/A.md#int-001), [B-004](../tracks/B.md#b-004), [B-006](../tracks/B.md#b-006), [B-007](../tracks/B.md#b-007), [BACK-005](../tracks/A.md#back-005), [C-003](../tracks/D.md#c-003), [A-006](../tracks/A.md#a-006) |
| 7. Input / Output contracts | Real adapters, CT-02/05, device data và consent garment upload. |
| 8. Deliverables | M3 traces: tag ảnh thật→sửa/lưu→1–5 outfits từ tủ có hình/giải thích. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Recognition và recommendation có live requestId thật; không ID ngoài tủ; không gọi VTO trong flow này. |
| 11. Testing | Device+server traces; D hỗ trợ Today, A kiểm tag/data; budget được duyệt. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | HTTPS/DNS/token sai; API latency/quota. |
| 14. Fallback / Simplification | Chuyển hotspot, sửa config, dùng provider khác chỉ đã được xác minh; RULES không cho gate này PASS. |
| 15. Definition of Done | M3 PASS riêng từng service, không lấy recommendation để che recognition chưa chạy. |

1. B hướng dẫn bindings, D đổi root; A nối tag; chọn profile/context thật không gửi body ảnh.
2. Tag item front/back, sửa nếu cần, save; yêu cầu tối đa5 bộ, chấp nhận ít hơn với reason hợp lệ.
3. Force-stop/reopen và gọi mới; đối chiếu IDs/revisions/outputcount; owner sửa đúng module.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="b-001"></a>

## B-001 — Proxy HTTPS và hàng rào chi phí

**Mục tiêu:** Proxy HTTPS và hàng rào chi phí.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-001 |
| 2. Task name | Proxy HTTPS và hàng rào chi phí |
| 3. Description | Dựng Worker nhỏ tại sự kiện, có health và bảo vệ trước khi route gọi provider. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [PRE-001](../tracks/B.md#pre-001), [PRE-003](../tracks/B.md#pre-003), [PRE-007](../tracks/D.md#pre-007) |
| 7. Input / Output contracts | CT-02/05/06/07; quyền API/hosting/budget đã xác nhận; route registration seam. |
| 8. Deliverables | proxy bootstrap/auth/limits/error envelope, HTTPS health, route mount cho recognition/outfits/tryon. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Auth sai/oversize/disabled không gọi upstream; C handler mount được qua seam; key không có ở APK/log. |
| 11. Testing | HTTP integration 200 health,401,413,503; mock upstream xác nhận số call bằng 0 cho request bị chặn. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Shared proxy root conflict; quotas không global; account chưa ready. |
| 14. Fallback / Simplification | Demo kín, token phiên ngắn hạn và host miễn phí đã có quyền; không public beta. Thiếu quyền hosting/budget thì BLOCKED, không nhúng key. |
| 15. Definition of Done | Security HTTP tests pass; public beta còn cần BACK-011; không claim production secure từ demo token. |

1. Giữ key ở server; short-lived session auth, input/output caps và kill switch theo service.
2. Expose handler registration; B không sửa C tryon/**, C test handler độc lập trước mount.
3. Kiểm rate-limit/budget thực; sanitize logs, reject arbitrary image URLs; không default deploy trả phí.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="b-002"></a>

## B-002 — Recommendation tối đa 5 outfit có giải thích

**Mục tiêu:** Recommendation tối đa 5 outfit có giải thích.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-002 |
| 2. Task name | Recommendation tối đa 5 outfit có giải thích |
| 3. Description | Gọi một model đã chọn với metadata tủ đồ và dịp; trả output hợp lệ CT-02. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [B-001](../tracks/B.md#b-001), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-02 OutfitRequest/Outfit; style/height optional, weather nullable, text request, count 1–5. |
| 8. Deliverables | proxy/outfits prompt/schema/semantic validator và tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không ID ngoài tủ/duplicate sets/>5; tủ không đủ trả INSUFFICIENT hoặc ít bộ với reason; không bịa thời tiết/fit từ chiều cao. |
| 11. Testing | Unit/HTTP fixtures 1/5/fewer outfits, invalid IDs, profile null, stale weather, injection, duplicate sets. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Model không hỗ trợ schema mode đang định dùng; output không nhất quán; latency. |
| 14. Fallback / Simplification | Dùng text JSON + parser strict nếu structured output chưa xác minh; không bỏ semantic validation; vẫn một model. |
| 15. Definition of Done | Validator/schema tests pass; C-102/LATER-006 bổ sung sâu reroll/focus không cần đổi schema. |

1. Dùng wardrobe snapshot ID/revision và profile projection tối thiểu; text user là data, không override system constraints.
2. Sinh 1…requestedCount bộ distinct, giải thích dùng context đã có; không đủ bộ thì trả ít hơn với reason.
3. Check allowed IDs/current snapshot, slots, duplicate sets, focus/exclusions và max count; reject invalid output, không auto retry tốn tiền.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="b-003"></a>

## B-003 — Android client cancellable và parser

**Mục tiêu:** Android client cancellable và parser.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-003 |
| 2. Task name | Android client cancellable và parser |
| 3. Description | Implement OutfitRecommendation Android client độc lập với proxy thật bằng fixture CT-02; parse và validate response trước khi trả UI. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-02/07, fake HTTP responses; URL/session config do root D/B cung cấp. |
| 8. Deliverables | ai/RecommendationClient DTO/parser/error mapping/tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Valid list 1–5 parse đúng; invalid IDs/revision/count/timeout thành typed failure; không throw vào UI. |
| 11. Testing | Unit parser và integration mock HTTP server cho lỗi/membership/cancellation. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Library chưa tải; JSON field mismatch; URL local laptop không truy cập từ máy. |
| 14. Fallback / Simplification | Dùng một HTTP stack đã có trong template; không thêm SDK provider vào Android. |
| 15. Definition of Done | Client fixtures pass, D có adapter nhận list outfit và errors v2. |

1. Serialize metadata/profile projection, không upload ảnh người hoặc cân nặng mặc định; TLS và session auth.
2. Timeout/cancel theo CT-07, user retry riêng; không retry do recomposition.
3. Validate response/request snapshot và source; D lấy interface, không tham chiếu provider DTO.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="b-004"></a>

## B-004 — Đánh giá recommendation live và tính hợp lệ

**Mục tiêu:** Đánh giá recommendation live và tính hợp lệ.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-004 |
| 2. Task name | Đánh giá recommendation live và tính hợp lệ |
| 3. Description | Xác nhận model/account/endpoint thực hoạt động; đo chất lượng có giới hạn mẫu. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [B-002](../tracks/B.md#b-002), [B-003](../tracks/B.md#b-003), [PRE-004](../tracks/A.md#pre-004) |
| 7. Input / Output contracts | CT-02, rubric 10 case và budget đã đồng ý; labels/model revision. |
| 8. Deliverables | Report 10 fixtures + ít nhất 3 live requests đủ đồ, IDs/reasons/latency/cost và sai số mẫu. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | 0 invalid IDs/duplicates tới client; 3 live cases có explanation ≥3/5 theo rubric; nếu fail ghi quality fail không chỉ HTTP 200. |
| 11. Testing | Unit validator + live smoke đã được phép; mocks cho lỗi cost/network. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Network/provider outage; explanation chung chung; ngân sách chưa đồng ý. |
| 14. Fallback / Simplification | Nếu budget không đủ 3 live cases thì ghi BLOCKED/chưa đủ evidence; giảm mẫu chỉ phục vụ spike, không đạt acceptance hoặc thay mock thành LIVE. |
| 15. Definition of Done | Evidence đủ để INT-002 kiểm Android; không coi server smoke là app đã hoạt động. |

1. Chạy fixtures 1–5 count, ít đồ, text riêng, profile null, weather stale; invariants phải pass toàn bộ.
2. Gọi 3 case live đủ đồ, 2 reviewer B/A chấm phù hợp dịp/giải thích 1–5; ghi output count thực.
3. Ghi latency từng call, budget/cost, lỗi và prompt version; không claim p90 từ mẫu nhỏ.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="b-005"></a>

## B-005 — Fake AI và compatibility fixtures

**Mục tiêu:** Fake AI và compatibility fixtures.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-005 |
| 2. Task name | Fake AI và compatibility fixtures |
| 3. Description | Fake ClothingRecognition, OutfitRecommendation và WeatherProvider cho các trạng thái v2. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / foundation / M1 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-02/05/07 JSON và request IDs/revisions. |
| 8. Deliverables | ai/fakes, proxy fixtures schemas và tests tương thích. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | A sửa tag, D render five outfits và lỗi bằng fake; fixture không có body PII. |
| 11. Testing | JSON roundtrip Kotlin/server fixtures; owner A/D review. |
| 12. Estimated effort | 25–40 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Server/client tự viết enum khác nhau. |
| 14. Fallback / Simplification | Chọn fixtures tối thiểu shared version, không thêm SDK provider. |
| 15. Definition of Done | Fake services compile và consumer không chờ account/API. |

1. Recognition front/back success/conflict/timeout; recommendation 1/5/fewer/invalid IDs.
2. Weather null/stale/current; fake generator không gọi mạng.
3. Tạo assertion output source FAKE chỉ dev; real response phải phân biệt rõ.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="b-006"></a>

## B-006 — Evaluation recognition hai góc nhìn

**Mục tiêu:** Evaluation recognition hai góc nhìn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-006 |
| 2. Task name | Evaluation recognition hai góc nhìn |
| 3. Description | Bộ fixtures và live eval nhỏ trước khi claim nhận diện tốt. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [BACK-004](../tracks/B.md#back-004), [PRE-004](../tracks/A.md#pre-004) |
| 7. Input / Output contracts | CT-05; 6 item/3 cặp, labels giữ cố định, approved budget. |
| 8. Deliverables | eval/recognition report theo field/view/model/prompt/latency/cost. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Enums hợp lệ 100% tới client; live matched/front-only có category đúng theo labels; conflict không bị lưu âm thầm; failed chất lượng phải ghi. |
| 11. Testing | Unit + live eval có evidence requestId, không lưu ảnh người. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Mẫu nhỏ; provider không hỗ trợ multi-image. |
| 14. Fallback / Simplification | Tag front trước và đánh back unsupported/review; không claim dual recognition nếu chưa có evidence. |
| 15. Definition of Done | Report có denominator và limitation, M3 nhận diện chỉ PASS ở capability thật đã kiểm. |

1. Chạy unit fixtures cho missing back/conflict/malformed và loại thông tin không biết.
2. Mục tiêu 3 case live: front-only, matched pair, conflicting pair; chỉ FRONT_ONLY thì chặn pair UNSUPPORTED_CAPABILITY trước billing, mock conflict và ghi giới hạn theo CONTRACTS.
3. A/B chấm category/color/unknown/conflict; báo n thực và lỗi, không gộp category với material accuracy.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="b-007"></a>

## B-007 — Android recognition client và upload có giới hạn

**Mục tiêu:** Android recognition client và upload có giới hạn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | B-007 |
| 2. Task name | Android recognition client và upload có giới hạn |
| 3. Description | Client nhận MediaRef của garment, gửi ảnh được user đồng ý qua proxy. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-01 MediaStore; CT-05/07; fake HTTP server. |
| 8. Deliverables | ai/RecognitionClient + upload/parser tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Upload front/back đúng slot; oversized bị chặn; malformed/timeout không crash; consent false không gọi network. |
| 11. Testing | Unit upload mapping/parser; HTTP mock consent/size/errors; INT-002 live. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Base64 inflate size, ảnh sai scope. |
| 14. Fallback / Simplification | Chỉ front upload nếu capability không nhận dual; user được báo, manual edit vẫn có. |
| 15. Definition of Done | A có adapter fake/real cùng interface; no duplicate upload do lifecycle. |

1. Đọc derivative ảnh garment đúng view/revision; không gửi ảnh profile trong request tagging.
2. Kiểm decoded bytes/MIME, timeout/cancel; response stale trả lỗi hoặc review, không tự save.
3. Map enum/conflict/unknown; no implicit retry; key provider chỉ server.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

## Hàng đợi P1

<a id="back-011"></a>

## BACK-011 — Củng cố bảo mật/quota trước public beta

**Mục tiêu:** Củng cố bảo mật/quota trước public beta.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-011 |
| 2. Task name | Củng cố bảo mật/quota trước public beta |
| 3. Description | Thay demo token bằng cơ chế quota/session phù hợp phân phối rộng; quyết định hosting dựa trên budget thực. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P1 / quality / M6 |
| 6. Dependencies | [B-004](../tracks/B.md#b-004) |
| 7. Input / Output contracts | Threat model CT-07, budgets từng service và C-210 privacy rules; không tự tạo paid infrastructure. |
| 8. Deliverables | Design quota/auth, implementation giới hạn đã chọn và monitoring tối thiểu; operational runbook. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Replay/parallel requests không vượt quota thiết kế; kill switch có hiệu lực; không provider key/log PII; ghi rõ phần cap không thể bảo đảm. |
| 11. Testing | Integration auth/quota concurrency + operational disable test; secret scan artifact. |
| 12. Estimated effort | 120–240 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Budget cap không hard, distributed state cần dịch vụ mới; ước lượng phụ thuộc lựa chọn. |
| 14. Fallback / Simplification | Giữ pilot có giám sát, không mở public nếu chưa có bảo vệ; chia tiếp task sau design nếu vượt 4 giờ. |
| 15. Definition of Done | B và D ký readiness beta; trần tiền có chủ sở hữu, không giả hard cap từ rate limiter best-effort. |

1. Đánh giá auth/session và quota atomic/global; không dùng device ID do client khai làm quyền truy cập.
2. Thiết lập budget alert/cap thực được provider hỗ trợ, kill switch và rotation; log requestId/status/latency không ảnh.
3. Load-test scope nhỏ và abuse/replay cases; privacy retention/delete policy; nếu cần hạ tầng trả phí xin quyết định trước.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="later-006"></a>

## LATER-006 — Phối quanh một món có contract xuyên suốt

**Mục tiêu:** Phối quanh một món có contract xuyên suốt.

| Trường | Nội dung |
|---|---|
| 1. Task ID | LATER-006 |
| 2. Task name | Phối quanh một món có contract xuyên suốt |
| 3. Description | Thực hiện focus-item từ Wardrobe/Today tới AI; nếu có RULES fallback cũng phải giữ focus, không chỉ thêm deep link. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P1 / quality / M3 |
| 6. Dependencies | [B-002](../tracks/B.md#b-002) |
| 7. Input / Output contracts | CT-02 focusItemId thuộc snapshot; A wardrobe CTA, D Today state. |
| 8. Deliverables | Focus prompt/validator + contract fixtures, consumer integration guidance. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Chọn focus từ Wardrobe/Today thì mọi bộ success chứa item đó; ID lạ bị chặn; tủ thiếu không bịa bộ. |
| 11. Testing | Unit membership/focus invariant, integration CTA→request→response, device back navigation. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Focus có category chưa được baseline hỗ trợ; route và service lệch. |
| 14. Fallback / Simplification | Chỉ hỗ trợ TOP/BOTTOM/DRESS trước; ẩn CTA category khác, giữ error rõ. |
| 15. Definition of Done | Focus tests pass và consumer biết error semantics; không có route placeholder được claim feature. |

1. Validate focus ID tồn tại/không deleted; mọi outfit success phải chứa focus.
2. B cung cấp parser/error; A/D nối CTA và route qua D-007, không thêm dependency Insights.
3. Unsupported category/thiếu slot báo lỗi, không silently drop focus.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-002"></a>

## C-002 — Fallback tối thiểu và phát hiện thiếu đồ

**Mục tiêu:** Fallback tối thiểu và phát hiện thiếu đồ.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-002 |
| 2. Task name | Fallback tối thiểu và phát hiện thiếu đồ |
| 3. Description | Ghép một bộ cơ bản offline bằng item hiện có; không đóng vai AI. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P1 / quality / M3 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-01/02; focus/exclusions khi có; RULES source. |
| 8. Deliverables | ai/BasicOutfitFallback + pure Kotlin tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Empty/chỉ TOP trả thiếu đồ; TOP+BOTTOM và DRESS trả hợp lệ; không duplicate hay ID mới. |
| 11. Testing | Unit tests ma trận đủ/thiếu/ordering/unique; chạy không cần Android/network. |
| 12. Estimated effort | 15–25 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Kỳ vọng 3 bộ hoặc hiểu fallback là personalized AI. |
| 14. Fallback / Simplification | Giữ đúng một quy tắc và một bộ; không hứa always-success khi tủ thiếu. |
| 15. Definition of Done | Invariants pass, D có fallback optional; thiếu fallback vẫn hiện lỗi hữu ích, không block AI core. |

1. Chọn một DRESS hoặc TOP+BOTTOM từ snapshot; không ép có 5 bộ.
2. Tôn trọng focus/exclusions nếu có thể; nếu không đủ thì trả Insufficient, không bỏ constraints âm thầm.
3. Explanation template và source RULES, không nhận là AI/live or try-on.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-101"></a>

## C-101 — Thời tiết một thành phố có timestamp

**Mục tiêu:** Thời tiết một thành phố có timestamp.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-101 |
| 2. Task name | Thời tiết một thành phố có timestamp |
| 3. Description | Resolve thời tiết cho city người dùng chọn, không yêu cầu GPS; trả timestamp/source/staleness theo CT-02. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P1 / quality / M3 |
| 6. Dependencies | [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-02 WeatherSnapshot nullable; city key đã chọn; provider terms phải kiểm khi làm. |
| 8. Deliverables | ai/weather provider mapping/cache + tests; attribution/time cho Today. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Offline/timeout weather không chặn outfit; không hiện forecast cũ như current; attribution nhìn thấy. |
| 11. Testing | Mock HTTP/unit mapping timestamp; máy thật offline; regression CT-02 không weather. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Field API/giấy phép chưa kiểm; thay schema gây quá giờ. |
| 14. Fallback / Simplification | Dùng weather=null và giải thích chưa có dữ liệu; không nhập số thời tiết giả thành đo live. |
| 15. Definition of Done | Mapping/expiry tests pass, unknown không biến thành thời tiết bịa; không cần đợi INT-002. |

1. Lấy weather theo city người dùng chọn; nếu chưa chọn thì unknown, không đoán location.
2. Snapshot validAt/fetchedAt/source, stale flag, timeout độc lập; không block recommendation.
3. Bổ sung context đã validate vào request; lỗi/null vẫn phối đồ, D hiển thị freshness/attribution.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-102"></a>

## C-102 — Phối lại tránh bộ vừa chọn

**Mục tiêu:** Phối lại tránh bộ vừa chọn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-102 |
| 2. Task name | Phối lại tránh bộ vừa chọn |
| 3. Description | Cho user chủ động xin một bộ khác khi tủ có lựa chọn, vẫn từng request riêng. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P1 / quality / M3 |
| 6. Dependencies | [B-002](../tracks/B.md#b-002) |
| 7. Input / Output contracts | CT-02 regenerationOfRequestId/excludeOutfitSignatures/freeText; không dùng wear history. |
| 8. Deliverables | Recommendation reroll semantics, fixture response fewer/insufficient và tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Tủ chỉ có 1 bộ báo không có lựa chọn khác hợp lệ; không auto retry tiêu tiền; source đúng. |
| 11. Testing | Unit set comparison; fixtures 1 bộ/2 bộ, integration request cancel. |
| 12. Estimated effort | 40–70 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | AI trả lại cùng bộ; tăng chi phí/contract change. |
| 14. Fallback / Simplification | Ẩn CTA reroll khi capability chưa có; recommendation core vẫn trả 1–5 bộ hợp lệ, không bịa ID. |
| 15. Definition of Done | Reroll contract tests pass; UI button ở D được integration kiểm, không chặn M3 core nếu chưa bật. |

1. Canonical signature từ sorted item IDs để tránh bộ y hệt khi user yêu cầu khác.
2. Giữ constraints/text, chỉ một call user-triggered; có thể trả ít bộ hơn, lý do rõ.
3. Công bố semantics cho D C-003; server/client cùng reject excluded sets.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.
