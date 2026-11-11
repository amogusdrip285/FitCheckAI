# Track C — Virtual Try-On

[Workflow](../../../TEAM_WORKFLOW.md) · [Board](../TASK_BOARD.md) · [Contracts](../CONTRACTS.md) · [Milestones](../DEPENDENCIES_AND_INTEGRATION.md)

Task card ở file này là nguồn scope/acceptance. Trạng thái chỉ ở Board; mọi task còn TODO. ID cũ giữ ổn định dù prefix khác owner: **owner trong bảng là người chịu trách nhiệm hiện tại**. Chọn task có dependency đạt theo priority, không theo ngày. Mọi đường dẫn source là deliverable tương lai, chưa có implementation.

## Hàng đợi P0

<a id="c-201"></a>

## C-201 — Khảo sát VTO provider, quyền dùng và privacy

**Mục tiêu:** Khảo sát VTO provider, quyền dùng và privacy.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-201 |
| 2. Task name | Khảo sát VTO provider, quyền dùng và privacy |
| 3. Description | So tối đa 2 ứng viên qua tài liệu chính thức/account đã có; không gọi trả phí trong audit này. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / feasibility / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | VTO_FEASIBILITY sources; mục tiêu front/back, outfit nhiều món, consent. |
| 8. Deliverables | Capability matrix: views, garment slots, job model, latency/cost, retention/deletion, availability với bằng chứng. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Mỗi capability có URL/ngày hoặc measured/unknown; back consistency chưa test phải UNKNOWN. |
| 11. Testing | Desk review C+B; không cần APK/account để bắt đầu đọc docs. |
| 12. Estimated effort | 45–90 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Tài liệu không nói multi-view; chưa có quyền API; giới hạn ảnh cá nhân. |
| 14. Fallback / Simplification | Giữ shortlist với NO-GO/UNKNOWN rõ; không coi generic image editing tự động tương đương VTO. |
| 15. Definition of Done | Matrix có điều kiện cho phép POC và blocker, không claim API khả dụng cho nhóm chỉ từ docs. |

1. Kiểm API model/version, input views, số garment thật được hỗ trợ; phân biệt số samples với số góc nhìn.
2. Đọc privacy/subprocessors/retention/no-training và quyền xóa; ghi unknown nếu chưa rõ.
3. Ghi account/quota/budget riêng VTO, quyền thử nghiệm theo PRE-001; không default tạo paid resource.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-202"></a>

## C-202 — Bộ ảnh người và garment có consent cho POC

**Mục tiêu:** Bộ ảnh người và garment có consent cho POC.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-202 |
| 2. Task name | Bộ ảnh người và garment có consent cho POC |
| 3. Description | Dataset private tối thiểu 3 case front/back; người tự nguyện đồng ý riêng mục đích upload/test/demo. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / feasibility / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | Ảnh nhóm có quyền dùng; CT-01/06; có thể dùng assets licensed nếu chưa có người đồng ý. |
| 8. Deliverables | Private case manifest: pseudonymous subjectId, paired views, garment IDs, consent scope; ảnh không commit. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | 3 case có paired input và rights rõ hoặc ghi chưa đủ; không public ảnh/profile/consent identifiers. |
| 11. Testing | Human review view pairing và garment labels; no biometric face matching. |
| 12. Estimated effort | 30–60 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Thiếu consent hoặc ảnh back; dữ liệu không đa dạng. |
| 14. Fallback / Simplification | Assets người mẫu được phép dùng để thử kỹ thuật, không gọi là ảnh người dùng thật; thiếu data thì gate không PASS. |
| 15. Definition of Done | Có private manifest audit được provenance, hoặc readiness report liệt kê ảnh/rights còn thiếu và owner bổ sung; thiếu dataset thì chặn POC, không claim đủ input. |

1. Lấy front/back cùng người/pose/ánh sáng tương tự và trang phục có chi tiết mặt sau; giữ nhãn view rõ.
2. Tạo một DRESS và một TOP+BOTTOM case; thêm thiếu back/mismatch fixture.
3. Ghi consent upload theo provider, consent demo tách riêng, đường xóa; không đo cân nặng từ ảnh.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-203"></a>

## C-203 — POC front-view với một outfit được chọn

**Mục tiêu:** POC front-view với một outfit được chọn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-203 |
| 2. Task name | POC front-view với một outfit được chọn |
| 3. Description | Thử adapter tối thiểu ngoài app với input cho phép, không cần đợi wardrobe/profile thực. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / feasibility / M4-F |
| 6. Dependencies | [C-201](../tracks/C.md#c-201), [C-202](../tracks/C.md#c-202), [PRE-001](../tracks/B.md#pre-001) |
| 7. Input / Output contracts | CT-06 request prototype, consent/assets, account và approved budget từ C-201. |
| 8. Deliverables | POC script khi được phép, measured report input/output IDs, quality/latency/cost; ảnh private. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Có 3 case được đo hoặc failure report kỹ thuật; SUCCESS phải có output hợp lệ. Không account/budget = BLOCKED, không tạo evidence giả. |
| 11. Testing | Manual 2 reviewer với input/output side-by-side; timeout/errors fixture; no app required. |
| 12. Estimated effort | 60–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Identity drift, body reshape, API denied, output chỉ đẹp nhưng sai đồ. |
| 14. Fallback / Simplification | Front single garment hoặc dress, label subset rõ; collage không phải kết quả VTO. |
| 15. Definition of Done | Task thí nghiệm DONE khi có report phép thử thực kể cả kết luận fail; không đồng nghĩa gate pass. |

1. Chọn một case một-piece/single garment đúng capability; giữ nguyên source snapshot.
2. Chạy 3 case, cap attempts theo budget; ghi cả failed outputs, không cherry-pick.
3. Chấm identity/body silhouette/garment fidelity/pose theo rubric VTO_FEASIBILITY; ghi capability multi-item chưa test.

**Điều kiện nhận việc:** Chỉ gọi thật khi quyền trial/BTC, consent provider và budget đã chốt; nếu chưa, làm fixtures và giữ experiment BLOCKED.

<a id="c-204"></a>

## C-204 — POC back-view và consistency của cặp

**Mục tiêu:** POC back-view và consistency của cặp.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-204 |
| 2. Task name | POC back-view và consistency của cặp |
| 3. Description | Thử paired front/back từ source thật; phân biệt joint generation với hai request độc lập. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / feasibility / M4-F |
| 6. Dependencies | [C-203](../tracks/C.md#c-203) |
| 7. Input / Output contracts | CT-06 paired refs, C-203 outputs, rubric/cost cap. |
| 8. Deliverables | Pair evaluation 3 cases gồm back details và multi-garment probe; report front/back/symmetry/seam drift. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Chấm cả cặp, không chỉ từng ảnh đẹp; missing back không tự bịa; multi-item không supported được báo rõ. |
| 11. Testing | Paired human review ít nhất 2 reviewer; lưu lỗi và chi phí tất cả attempts. |
| 12. Estimated effort | 60–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Back hallucination, inconsistent pattern, two calls doubled cost, outfit nhiều món không hỗ trợ. |
| 14. Fallback / Simplification | Đề xuất FRONT_ONLY hoặc subset single garment bằng chứng rõ; không tự đánh DUAL hoàn tất. |
| 15. Definition of Done | Có pair report và đề nghị scope với tác động sản phẩm; M4-F DUAL chỉ PASS theo rubric riêng; runtime còn cần M4-R. |

1. Gửi đúng personBack/garmentBack; nếu endpoint chỉ 1 ảnh thì hai jobs được ghi độc lập, không coi là multi-view guarantee.
2. So logo/hoa văn/chiều dài/skin/clothing layers/pose; không cần thấy mặt ở ảnh lưng nhưng silhouette phải phù hợp.
3. Thử TOP+BOTTOM nếu supported; sequential single-garment có thể phá món trước, đo và ghi subset thực.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-205"></a>

## C-205 — Feasibility gate và quyết định phạm vi VTO

**Mục tiêu:** Feasibility gate và quyết định phạm vi VTO.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-205 |
| 2. Task name | Feasibility gate và quyết định phạm vi VTO |
| 3. Description | Review kết quả C-203/204 nếu có; ghi GO_DUAL/GO_FRONT_ONLY/NO_GO/BLOCKED theo điều kiện định lượng. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / feasibility / M4-F |
| 6. Dependencies | [C-201](../tracks/C.md#c-201), [C-202](../tracks/C.md#c-202) |
| 7. Input / Output contracts | VTO_FEASIBILITY rubric; trial results hoặc blocker documented, budget/privacy decision. |
| 8. Deliverables | Gate record: decision, supported categories/views, failure evidence, cost/latency ceiling, product impact. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | GO_FRONT_ONLY cần C-203 evidence pass; GO_DUAL cần cả C-203/204 paired pass; no body-fit accuracy claim. |
| 11. Testing | Independent reviewer đọc cả failed cases và matrix, kiểm tính số chi phí. |
| 12. Estimated effort | 30–45 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Nhầm gate report DONE với VTO implementation DONE; quá ít dữ liệu. |
| 14. Fallback / Simplification | Ghi NO_GO/BLOCKED hoặc front-only có giới hạn, tiếp tục core recommendation và UI disabled đúng. |
| 15. Definition of Done | Gate document hoàn chỉnh kể cả NO_GO; M4 vẫn chưa PASS nếu không có kết quả đo được. |

1. Nếu chưa có experiment chỉ được BLOCKED/NO_GO, không GO; ghi task bị chặn và điều kiện mở lại.
2. Nếu có kết quả, áp rubric 3 cases/2 reviewers và account/privacy/cost gates.
3. C+B review kỹ thuật/chi phí, D xác nhận UI mode, nhóm quyết định phạm vi; ghi bằng chứng chứ không bắt người dùng chọn mù.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-206"></a>

## C-206 — Fake TryOnService và preview states

**Mục tiêu:** Fake TryOnService và preview states.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-206 |
| 2. Task name | Fake TryOnService và preview states |
| 3. Description | Fake job submit/observe/cancel/delete với paired results, partial/error/expired/missing inputs. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / foundation / M1 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-06/07; non-person fixture placeholder có label DEV. |
| 8. Deliverables | tryon/fakes, state previews và contract tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Fake mô phỏng cả terminal và partial; 2 submit cùng key không có 2 jobs; D không cần account. |
| 11. Testing | Unit state transitions/ID mapping; preview no network. |
| 12. Estimated effort | 35–60 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Fake happy path che long-running failure. |
| 14. Fallback / Simplification | Giữ finite-state fake đơn giản, không queue framework. |
| 15. Definition of Done | Contract tests pass, D biết route nhận selected snapshot. |

1. Tạo deterministic jobId/idempotency behavior và per-view progress.
2. Inject timeout, front-only, back failure, consent revoked, cancelled/late completion.
3. Không dùng fake asset trông như live; badge FAKE trong dev, no real billing.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-210"></a>

## C-210 — Privacy gate và cleanup ảnh/job

**Mục tiêu:** Privacy gate và cleanup ảnh/job.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-210 |
| 2. Task name | Privacy gate và cleanup ảnh/job |
| 3. Description | Chốt consent purpose/provider/version và thực hiện upload/delete/revoke contract với fake stores. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P0 / core / M5 |
| 6. Dependencies | [C-201](../tracks/C.md#c-201), [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-01/04/06/07; provider retention facts; D owns personal storage, A media service. |
| 8. Deliverables | tryon privacy coordinator/redaction/cleanup tests; consent disclosure text và backup requirements cho D. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Consent false = 0 uploads; revoke/delete chặn hiển thị late output; tất cả private refs bị cleanup theo policy, không claim remote erase khi chưa có API. |
| 11. Testing | Unit fake delayed response/redaction; integration với real stores ở D-007; provider policy review. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Provider giữ request records; backup Android có thể copy files; hủy không hoàn tiền. |
| 14. Fallback / Simplification | Không gửi ảnh nếu retention không được nhóm/user chấp nhận; feature disabled vẫn giữ core app. |
| 15. Definition of Done | Privacy tests với fake pass và A/D nhận hooks; live rollout chỉ sau real-store privacy regression. |

1. Không upload body photos khi onboarding/save; chỉ chọn views/garments được consent cho job.
2. Xóa ảnh/rút consent: cancel best-effort, invalidate input generation, delete local derived outputs và reject late completion.
3. Ghi trạng thái remote deletion supported/pending/unsupported/unknown, tránh hứa xóa ngay ở provider; logs không base64/URLs/body/profile text.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

## Hàng đợi P1

<a id="d-002"></a>

## D-002 — Chuyển evidence thành pitch có giới hạn

**Mục tiêu:** Chuyển evidence thành pitch có giới hạn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-002 |
| 2. Task name | Chuyển evidence thành pitch có giới hạn |
| 3. Description | Tạo nội dung vấn đề, demo và kênh tiếp cận ban đầu dựa trên dữ liệu thật. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P1 / quality / M6 |
| 6. Dependencies | [PRE-005](../tracks/A.md#pre-005), [C-205](../tracks/C.md#c-205) |
| 7. Input / Output contracts | Research n thực, VTO gate report, AI live evidence sẽ bổ sung khi có. |
| 8. Deliverables | Kịch bản 5 phút, tối đa 5 slide hoặc outline, bảng claim→nguồn→giới hạn. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Mọi số liệu có nguồn; không claim beta/chất lượng hay giảm thời gian chưa đo; có kế hoạch kiểm chứng thị trường. |
| 11. Testing | B đọc tìm overclaim; đọc thử để không vượt 5 phút. |
| 12. Estimated effort | 30–45 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Evidence ít; pitch dành quá nhiều giờ thiết kế. |
| 14. Fallback / Simplification | Dùng outline/ảnh app thật, nêu mẫu nhỏ; không cần Canva hoặc công cụ trả phí. |
| 15. Definition of Done | Bản nháp pitch dùng được, dành demo thật 90–120 giây và thời gian giải thích giới hạn. |

1. Nêu vấn đề và ảnh minh họa có consent; phân biệt collage/mô phỏng/fit thật.
2. Ghi kết quả gate two-view với giới hạn, không cherry-pick một ảnh đẹp.
3. GTM giả thuyết và phần đã xây tại sự kiện; D xác nhận claim khớp APK, không dồn slide lên D.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-207"></a>

## C-207 — Server adapter VTO và job idempotency

**Mục tiêu:** Server adapter VTO và job idempotency.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-207 |
| 2. Task name | Server adapter VTO và job idempotency |
| 3. Description | C sở hữu proxy/tryon handler/job records; B chỉ mount route/auth envelope. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P1 / reliable / M4-R |
| 6. Dependencies | [B-001](../tracks/B.md#b-001), [C-205](../tracks/C.md#c-205) |
| 7. Input / Output contracts | CT-06/07; capability gate GO, private media transfer, permitted durable state mechanism. |
| 8. Deliverables | proxy/tryon adapter submit/status/cancel/delete; idempotency/cost reservation tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Concurrent same key tạo tối đa một known submission; cross-session job không đọc được; trạng thái partial/cancel/unknown không giả success. |
| 11. Testing | HTTP integration với fake provider crash/timeouts/replay/restart; upstream live khi được phép. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Durable job store chưa có quyền; exactly-once upstream không được bảo đảm; state ở Worker ephemeral. |
| 14. Fallback / Simplification | Giữ POC/front-only có giám sát và chưa claim reliable integration nếu thiếu durable idempotency; không thêm paid DB ngầm. |
| 15. Definition of Done | Job protocol tests pass, B review security seam, runtime gate còn cần C-212. |

1. Persist idempotency record trước gọi provider, scope theo session; duplicate submit trả cùng job.
2. Map child jobs per view, deadline/attempt budget; không dùng Worker memory cho trạng thái bền.
3. Nếu provider không hỗ trợ idempotency, ambiguous upstream timeout = UNKNOWN_SUBMISSION không auto resubmit; cancel không đồng nghĩa refund.

**Điều kiện nhận việc:** GO_FRONT_ONLY hoặc GO_DUAL với account/budget/state-store đã xác nhận; nếu NO_GO/BLOCKED thì chưa nhận task runtime.

<a id="c-208"></a>

## C-208 — Android VTO client và khôi phục job

**Mục tiêu:** Android VTO client và khôi phục job.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-208 |
| 2. Task name | Android VTO client và khôi phục job |
| 3. Description | Client submit/poll/cancel/delete qua interface, lưu job metadata local riêng do C owner. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P1 / reliable / M4-R |
| 6. Dependencies | [S-002](../tracks/D.md#s-002), [C-206](../tracks/C.md#c-206) |
| 7. Input / Output contracts | CT-06/07; fake HTTP, capability và consent generation; chưa cần server thật để viết. |
| 8. Deliverables | tryon/client + local job metadata store, coroutine state-machine tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Process death/reopen không duplicate POST; user cancel không tự retry; timed out/partial có hành động rõ. |
| 11. Testing | Unit fake clock + HTTP replay; device process kill/poll resume. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Lost submit response và missing persisted jobId; provider URL expired. |
| 14. Fallback / Simplification | Hiển thị UNKNOWN_SUBMISSION và cho kiểm status, không tự gọi trả phí lần nữa. |
| 15. Definition of Done | Failure/restart tests pass; input snapshots không bị lẫn người hoặc outfit. |

1. Persist requestId/jobId/input revisions trước poll; rotation chỉ attach observer.
2. Poll backoff bounded tới deadline, resume GET status sau restart; không POST lại khi status chưa rõ.
3. Cancel/delete/revoke đánh generation token để bỏ late result; expired URLs không thành ảnh valid.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-209"></a>

## C-209 — Try-On screen cho outfit đã chọn

**Mục tiêu:** Try-On screen cho outfit đã chọn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-209 |
| 2. Task name | Try-On screen cho outfit đã chọn |
| 3. Description | Tạo màn chọn views, preview consent, loading/result front/back/error, không auto-generate. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P1 / reliable / M4-R |
| 6. Dependencies | [C-206](../tracks/C.md#c-206) |
| 7. Input / Output contracts | CT-06; selected outfit snapshot và fake ProfileStorage/MediaStore; D route callback. |
| 8. Deliverables | tryon/TryOnScreen/ViewModel với result tabs và disclosure. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không tạo cho cả 5 outfits; missing back không có thumbnail back giả; incomplete pair không hiển thị DUAL complete. |
| 11. Testing | UI fake partial/timeout/consent deny/deleted photo; D review navigation seam. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | UI che lỗi back hoặc gắn completed khi quality chưa review. |
| 14. Fallback / Simplification | Front-only screen nếu gate cho phép; nếu NO_GO hiện unavailable và collateral collage có nhãn riêng. |
| 15. Definition of Done | UI tests pass không cần server live; C sửa UI VTO, D không nhận ownership này. |

1. Hiển thị outfit được chọn, person front/back input và views thực có, capability supported.
2. User nhấn tạo sau consent; thiếu back hướng dẫn bổ sung hoặc chọn front-only có nhãn.
3. Result per-view có completed/failed/expired; dòng mô phỏng không đảm bảo fit; reject/hide ảnh có severe quality fail.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="c-211"></a>

## C-211 — Đánh giá runtime hai mặt và nhiều món

**Mục tiêu:** Đánh giá runtime hai mặt và nhiều món.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-211 |
| 2. Task name | Đánh giá runtime hai mặt và nhiều món |
| 3. Description | Chạy rubric paired với output runtime và đúng outfit snapshot; kiểm từng garment thực được áp dụng. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P1 / reliable / M4-R |
| 6. Dependencies | [C-207](../tracks/C.md#c-207), [C-208](../tracks/C.md#c-208), [C-209](../tracks/C.md#c-209), [C-210](../tracks/C.md#c-210) |
| 7. Input / Output contracts | GO_DUAL và C-204 evidence; CT-06 appliedItemIds và per-view result. |
| 8. Deliverables | Runtime pair report input/output revision/cost/latency và bugs. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Chỉ PASS dual nếu cả cặp đạt rubric; một mặt đạt chỉ FRONT, không đánh task dual DONE thành success. |
| 11. Testing | Live pair review + mock failure/unsupported slots; evidence private có quyền. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Chất lượng runtime khác POC; detector tự động không đủ tin cậy. |
| 14. Fallback / Simplification | Giữ front-only đã kiểm, park dual và ghi blocker/tác động; không điền ảnh back sinh tùy ý. |
| 15. Definition of Done | Runtime dual có report PASS theo rubric và lifecycle/privacy checks; nếu FAIL thì REVIEW để sửa, nếu thiếu điều kiện thì BLOCKED, chưa DONE. |

1. Chạy 3 cặp approved cases qua server/client; 2 reviewers chấm như feasibility gate.
2. So cùng garment ID màu/hoa văn/logo và body/pose; không so face identity ở back bằng biometric.
3. Test multi-item nếu capability supported; subset phải ghi rõ, không label full outfit.

**Điều kiện nhận việc:** GO_DUAL bắt buộc; GO_FRONT_ONLY không phải dependency thành công của task dual.

<a id="c-212"></a>

## C-212 — Tích hợp và nghiệm thu VTO theo capability

**Mục tiêu:** Tích hợp và nghiệm thu VTO theo capability.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-212 |
| 2. Task name | Tích hợp và nghiệm thu VTO theo capability |
| 3. Description | Dùng fixture outfit/profile có consent để kiểm end-to-end VTO; chưa cần recommendation thật. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P1 / reliable / M4-R |
| 6. Dependencies | [C-207](../tracks/C.md#c-207), [C-208](../tracks/C.md#c-208), [C-209](../tracks/C.md#c-209), [C-210](../tracks/C.md#c-210), [INT-001](../tracks/A.md#int-001) |
| 7. Input / Output contracts | CT-06; gate record GO_FRONT_ONLY/GO_DUAL; real provider và local image refs. |
| 8. Deliverables | M4-R FRONT hoặc M4-R DUAL evidence với jobIds/output source/cost; readiness flag cho D. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Front mode có ≥3 cases measured chất lượng đạt rubric; dual cần paired report C-211; job lifecycle/consent pass. |
| 11. Testing | Integration device/server, mode-specific quality review; no fake/live mixing. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Provider/cache URL expire; shared route configs; feature enabled trước privacy proof. |
| 14. Fallback / Simplification | Giữ M4 blocked và route disabled; POC report vẫn tồn tại nhưng không phải runtime complete. |
| 15. Definition of Done | M4 mode cụ thể được ghi, no claim dual nếu chỉ front; bugs về VTO do C sửa. |

1. Mount handler qua B, wire client qua D; dùng outfit snapshot fixture rõ provenance.
2. Chạy front quality/restart/partial/error và privacy checks; nếu dual phải kèm C-211 PASS.
3. Công bố supported item categories/views/count, limitations và enabled flag; D nhận flag không tự đoán.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

## Hàng đợi P2

<a id="c-213"></a>

## C-213 — Tối ưu nhiều món và ổn định góc nhìn

**Mục tiêu:** Tối ưu nhiều món và ổn định góc nhìn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-213 |
| 2. Task name | Tối ưu nhiều món và ổn định góc nhìn |
| 3. Description | Đánh giá native multi-garment hoặc sequential passes với chi phí/identity drift, không hứa 3D fitting. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P2 / future / M4-R |
| 6. Dependencies | [C-212](../tracks/C.md#c-212) |
| 7. Input / Output contracts | Approved dataset, provider capability, per-job budgets. |
| 8. Deliverables | Experiment A/B pipeline và recommendation go/no-go; versioned evaluator. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không giảm garment fidelity hoặc identity để lấy ảnh đẹp; có report cả failure. |
| 11. Testing | Paired evaluator với holdout cases; budget-controlled repeated runs. |
| 12. Estimated effort | 120–240 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Compute/cost cao, data không đủ đại diện. |
| 14. Fallback / Simplification | Giới hạn một-piece/ít món và front-only rõ; không mở batch VTO cho5 bộ mặc định. |
| 15. Definition of Done | Có quyết định sản phẩm dựa trên report; không mở mode mới trước nghiệm thu. |

1. So native multi-item với sequential giữ cùng inputs; kiểm món đầu có bị đổi ở pass sau.
2. Thử seed/reference control nếu tài liệu hỗ trợ, không coi seed là guarantee consistency.
3. Báo quality/cost/latency theo outfit/view count và chọn giới hạn hỗ trợ.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.
