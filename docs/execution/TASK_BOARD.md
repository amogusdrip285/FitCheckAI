# Task board — trạng thái duy nhất

[Workflow](../../TEAM_WORKFLOW.md) · [Trang chủ](README.md) · [Contracts](CONTRACTS.md) · [Milestones](DEPENDENCIES_AND_INTEGRATION.md)

Cập nhật Track A độc lập ngày 11/11/2026; bằng chứng ở [QA A bản đầu](../qa/track-a-acceptance.md) và [bổ sung sau merge main](../qa/track-a-independent-followup.md). Các track B/C/D giữ nguyên trạng thái, không suy nghiệm thu của họ từ build A. Chọn P0 có dependency/gate đã đạt; P0 feasibility có thể kết luận NO_GO nhưng không làm M4 PASS. C-208/C-209 được làm với fake trước GO; C-207 và upload live phải qua gate/account/budget/privacy tương ứng. P2 chưa được cam kết; 5 card archive không nằm trong bảng/tổng.

Bảng xếp priority rồi thứ tự topo trong mỗi priority; vẫn phải kiểm dependency liên track trước nhận việc. Cards là nguồn dependency cứng. Gate có điều kiện (GO_FRONT_ONLY/GO_DUAL, C-211 khi release DUAL) ở [tài liệu tích hợp](DEPENDENCIES_AND_INTEGRATION.md), không suy là đã đạt từ trạng thái TODO.

**Trạng thái bổ sung:** `REVIEW` = deliverable cục bộ đã có, chờ người phụ trách nhận/review; `PARTIAL` = có implementation/evidence nhưng chưa đủ DoD; `BLOCKED` = gate cần máy thật, consumer hoặc người tham gia; `DEFERRED` = chủ động hoãn theo phạm vi. Chỉ tick DONE khi toàn bộ DoD đạt.

## A — Wardrobe & Imaging

| Done | Task | P | Scope | Milestone | Phút owner | Dependencies | Status | Evidence / blocker |
|---|---|---|---|---|---|---|---|---|
| [ ] | [PRE-002](tracks/A.md#pre-002) — Kiểm tra toolchain và máy Android | P0 | readiness | M0 | 40–70 | — | BLOCKED | JDK 21/SDK 35/AVD chạy được; chưa có máy Android thật và D tái lập. |
| [ ] | [PRE-004](tracks/A.md#pre-004) — Chuẩn bị ảnh và fixture có quyền dùng | P0 | readiness | M0 | 35–60 | — | REVIEW | [Catalog Kaggle v1](../../data/README.md#catalog-kaggle) có 24.856 ảnh sau audit; fixtures Android 21 món, 24 ảnh, 3 cặp có nguồn; chờ B review nhãn tham chiếu. |
| [ ] | [D-001](tracks/A.md#d-001) — Checklist nghiệm thu và hồ sơ evidence | P0 | core | M6 | 30–45 | PRE-007, PRE-004 | REVIEW | [46/46 case P0](../qa/p0-case-index.md) và [mẫu evidence](../qa/evidence-template.md); không gán PASS cho gate chưa chạy. |
| [ ] | [A-001](tracks/A.md#a-001) — Room schema và Clothing Repository | P0 | core | M2 | 90–150 | S-002 | REVIEW | Room native API 35: migration, SQL abort rollback, reopen/delete, 8 writers/CAS; chờ D/B review shared CT-01. |
| [ ] | [A-002](tracks/A.md#a-002) — MediaStore và nhập ảnh front/back | P0 | core | M2 | 75–120 | S-002 | REVIEW | 3 scope/EXIF/hash/caps; thêm orphan recovery, bảo vệ ancestor và symlink, cleanup từng mục; unit + Android tests pass, chờ D/C nhận contract. |
| [ ] | [A-005](tracks/A.md#a-005) — Fake wardrobe và media fixtures | P0 | foundation | M1 | 25–40 | S-002 | REVIEW | 6 fake UUID fixtures/3 cặp, nullable back, lỗi IO/CAS/tombstone có tests; consumer D/C chưa tích hợp. |
| [ ] | [A-003](tracks/A.md#a-003) — Form loại/màu và danh sách tủ đồ | P0 | core | M2 | 60–100 | S-002, A-005 | REVIEW | Form/grid thủ công, UNKNOWN/null, search/filter; tests độc lập và QA emulator tại báo cáo. |
| [ ] | [BACK-012](tracks/A.md#back-012) — Sửa/xóa món và lọc tủ đồ | P0 | core | M2 | 60–100 | A-001, A-003, A-002 | PARTIAL | CRUD và resolver stale có tests; real Today v2 cần D tích hợp invalidation, chưa đạt full DoD. |
| [ ] | [A-006](tracks/A.md#a-006) — Component outfit trực quan dùng lại | P0 | core | M3 | 45–75 | S-002, A-005 | REVIEW | OutfitVisual/List 1–5 cards, đúng ID/selection, missing/stale và hai mặt; chờ D nhận API. |
| [ ] | [BACK-005](tracks/A.md#back-005) — Nối recognition vào form và sửa thuộc tính | P0 | core | M3 | 45–75 | A-003, B-005 | PARTIAL | Consent từng request, manual thắng AI muộn, timeout/cancel có tests; binding B-007 chưa có. |
| [ ] | [INT-001](tracks/A.md#int-001) — Tích hợp Room, media và Profile | P0 | core | M2 | 30–50 | A-001, A-002, A-003, D-004, BACK-008, D-005, BACK-012 | BLOCKED | Room/media A đã chạy; D-004/D-005 ProfileStorage v2 và journey liên track chưa có. |
| [ ] | [A-004](tracks/A.md#a-004) — Kiểm tra persistence và recovery trên máy | P0 | core | M6 | 30–50 | INT-001, BACK-012 | PARTIAL | Bản cuối 3 force-stop giữ 51 món, 1 tombstone, 54 hash và 30 rows legacy; chưa máy thật/D witness/toàn journey. |
| [ ] | [INT-003](tracks/A.md#int-003) — Regression và quyết định đóng băng | P0 | core | M6 | 45–75 | D-007, A-004, C-004, D-001 | BLOCKED | Local regression có báo cáo; chưa D-007, physical gate và quyết định release của D. |
| [ ] | [PRE-005](tracks/A.md#pre-005) — Nghiên cứu người dùng ngắn và trung thực | P1 | quality | M6 | 60–100 | — | PARTIAL | [Protocol + hồ sơ n=0](../research/track-a-study-plan.md); không có validation người dùng. |
| [ ] | [BACK-003](tracks/A.md#back-003) — Tách nền có kiểm tra model readiness | P1 | quality | M3 | 75–120 | A-002 | PARTIAL | 7 lifecycle/cancel/late-output tests pass; native probe UNAVAILABLE do module chưa tải, 0 ảnh được đánh giá; gate model quality/máy thật còn mở. |
| [ ] | [A-101](tracks/A.md#a-101) — Chụp bằng ứng dụng camera hệ thống | P1 | quality | M2 | 20–40 | A-002 | DEFERRED | Chọn một route CameraX đang có (BACK-002), đúng điều khoản không bắt cả intent/CameraX; picker giữ nguyên. |
| [ ] | [BACK-002](tracks/A.md#back-002) — CameraX capture và quyền camera | P2 | future | M2 | 90–150 | A-002 | PARTIAL | Emulator deny/permanent-deny/picker, grant/capture/discard pass; chưa matrix 5 ảnh và background trên máy thật. |
| [ ] | [BACK-009](tracks/A.md#back-009) — Pilot beta nhỏ có kế hoạch đo | P2 | future | M6 | 60–100 | INT-004, BACK-011 | BLOCKED | [Kế hoạch pilot](../research/track-a-study-plan.md), n=0; chưa có người tham gia/INT-004/BACK-011. |
| [ ] | [BACK-010](tracks/A.md#back-010) — Phân tích kết quả và quyết định roadmap | P2 | future | M6 | 60–120 | BACK-009 | BLOCKED | Chưa có dữ liệu pilot; không tạo kết quả hoặc chart usage giả. |
| [ ] | [LATER-003](tracks/A.md#later-003) — Chia sẻ ảnh outfit có lựa chọn | P2 | future | M6 | 75–120 | INT-004 | PARTIAL | OutfitShare + callback + FileProvider đọc collage; Today wiring và 2 ứng dụng nhận trên máy thật chưa chạy. |
| [ ] | [LATER-004](tracks/A.md#later-004) — Thử bản địa hóa một luồng và một thị trường | P2 | future | M6 | 90–150 | BACK-010 | DEFERRED | [Quyết định hoãn](../research/track-a-study-plan.md): chưa có evidence thị trường/BACK-010. |
| [ ] | [LATER-005](tracks/A.md#later-005) — Khảo sát monetization có cost model | P2 | future | M6 | 60–100 | BACK-010, BACK-011 | PARTIAL | [Cost model minh bạch giả định](../research/track-a-study-plan.md); chưa có giá/account/evidence để quyết định business GO. |
| [ ] | [A-201](tracks/A.md#a-201) — Thiết kế sync boundary và export mapping | P2 | future | M6 | 90–150 | A-001, D-004 | REVIEW | [Metadata mapping + tests](../track-a-sync-boundary.md): ID giữ nguyên, delete thắng, exclude PERSON; D/C chưa review, chưa cloud. |

## B — AI Recognition & Recommendation

| Done | Task | P | Scope | Milestone | Phút owner | Dependencies | Status | Evidence / blocker |
|---|---|---|---|---|---|---|---|---|
| [ ] | [PRE-001](tracks/B.md#pre-001) — Chốt thể lệ và disclosure | P0 | readiness | M0 | 20–35 | — | TODO | — |
| [ ] | [PRE-003](tracks/B.md#pre-003) — Xác minh provider và ngân sách | P0 | readiness | M0 | 45–75 | — | TODO | — |
| [ ] | [PRE-008](tracks/B.md#pre-008) — Phân vai theo kỹ năng và rehearsal rủi ro | P0 | readiness | M0 | 20–35 | — | TODO | — |
| [ ] | [B-001](tracks/B.md#b-001) — Proxy HTTPS và hàng rào chi phí | P0 | core | M3 | 60–100 | PRE-001, PRE-003, PRE-007 | TODO | — |
| [ ] | [B-005](tracks/B.md#b-005) — Fake AI và compatibility fixtures | P0 | foundation | M1 | 25–40 | S-002 | TODO | — |
| [ ] | [BACK-004](tracks/B.md#back-004) — Recognition proxy cho ảnh trước/sau | P0 | core | M3 | 75–120 | B-001, B-005 | TODO | — |
| [ ] | [B-002](tracks/B.md#b-002) — Recommendation tối đa 5 outfit có giải thích | P0 | core | M3 | 90–150 | B-001, B-005 | TODO | — |
| [ ] | [B-003](tracks/B.md#b-003) — Android client cancellable và parser | P0 | core | M3 | 45–75 | S-002, B-005 | TODO | — |
| [ ] | [B-004](tracks/B.md#b-004) — Đánh giá recommendation live và tính hợp lệ | P0 | core | M3 | 45–75 | B-002, B-003, PRE-004 | TODO | — |
| [ ] | [B-006](tracks/B.md#b-006) — Evaluation recognition hai góc nhìn | P0 | core | M3 | 45–75 | BACK-004, PRE-004 | TODO | — |
| [ ] | [B-007](tracks/B.md#b-007) — Android recognition client và upload có giới hạn | P0 | core | M3 | 45–75 | S-002, B-005 | TODO | — |
| [ ] | [INT-002](tracks/B.md#int-002) — Tích hợp recognition và recommendation live | P0 | core | M3 | 45–75 | INT-001, B-004, B-006, B-007, BACK-005, C-003, A-006 | TODO | — |
| [ ] | [BACK-011](tracks/B.md#back-011) — Củng cố bảo mật/quota trước public beta | P1 | quality | M6 | 120–240 | B-004 | TODO | — |
| [ ] | [LATER-006](tracks/B.md#later-006) — Phối quanh một món có contract xuyên suốt | P1 | quality | M3 | 45–75 | B-002 | TODO | — |
| [ ] | [C-002](tracks/B.md#c-002) — Fallback tối thiểu và phát hiện thiếu đồ | P1 | quality | M3 | 15–25 | S-002, B-005 | TODO | — |
| [ ] | [C-101](tracks/B.md#c-101) — Thời tiết một thành phố có timestamp | P1 | quality | M3 | 45–75 | B-005 | TODO | — |
| [ ] | [C-102](tracks/B.md#c-102) — Phối lại tránh bộ vừa chọn | P1 | quality | M3 | 40–70 | B-002 | TODO | — |

## C — Virtual Try-On

| Done | Task | P | Scope | Milestone | Phút owner | Dependencies | Status | Evidence / blocker |
|---|---|---|---|---|---|---|---|---|
| [ ] | [C-201](tracks/C.md#c-201) — Khảo sát VTO provider, quyền dùng và privacy | P0 | feasibility | M0 | 45–90 | — | TODO | — |
| [ ] | [C-202](tracks/C.md#c-202) — Bộ ảnh người và garment có consent cho POC | P0 | feasibility | M0 | 30–60 | — | TODO | — |
| [ ] | [C-203](tracks/C.md#c-203) — POC front-view với một outfit được chọn | P0 | feasibility | M4-F | 60–120 | C-201, C-202, PRE-001 | TODO | — |
| [ ] | [C-204](tracks/C.md#c-204) — POC back-view và consistency của cặp | P0 | feasibility | M4-F | 60–120 | C-203 | TODO | — |
| [ ] | [C-205](tracks/C.md#c-205) — Feasibility gate và quyết định phạm vi VTO | P0 | feasibility | M4-F | 30–45 | C-201, C-202 | TODO | — |
| [ ] | [C-206](tracks/C.md#c-206) — Fake TryOnService và preview states | P0 | foundation | M1 | 35–60 | S-002 | TODO | — |
| [ ] | [C-210](tracks/C.md#c-210) — Privacy gate và cleanup ảnh/job | P0 | core | M5 | 60–100 | C-201, S-002 | TODO | — |
| [ ] | [D-002](tracks/C.md#d-002) — Chuyển evidence thành pitch có giới hạn | P1 | quality | M6 | 30–45 | PRE-005, C-205 | TODO | — |
| [ ] | [C-207](tracks/C.md#c-207) — Server adapter VTO và job idempotency | P1 | reliable | M4-R | 90–150 | B-001, C-205 | TODO | — |
| [ ] | [C-208](tracks/C.md#c-208) — Android VTO client và khôi phục job | P1 | reliable | M4-R | 75–120 | S-002, C-206 | TODO | — |
| [ ] | [C-209](tracks/C.md#c-209) — Try-On screen cho outfit đã chọn | P1 | reliable | M4-R | 60–100 | C-206 | TODO | — |
| [ ] | [C-211](tracks/C.md#c-211) — Đánh giá runtime hai mặt và nhiều món | P1 | reliable | M4-R | 60–100 | C-207, C-208, C-209, C-210 | TODO | — |
| [ ] | [C-212](tracks/C.md#c-212) — Tích hợp và nghiệm thu VTO theo capability | P1 | reliable | M4-R | 45–75 | C-207, C-208, C-209, C-210, INT-001 | TODO | — |
| [ ] | [C-213](tracks/C.md#c-213) — Tối ưu nhiều món và ổn định góc nhìn | P2 | future | M4-R | 120–240 | C-212 | TODO | — |

## D — App Foundation & Experience

| Done | Task | P | Scope | Milestone | Phút owner | Dependencies | Status | Evidence / blocker |
|---|---|---|---|---|---|---|---|---|
| [ ] | [PRE-006](tracks/D.md#pre-006) — Wireflow Profile → Wardrobe → Today → Try-On | P0 | readiness | M0 | 30–45 | — | TODO | — |
| [ ] | [PRE-007](tracks/D.md#pre-007) — Chốt contract v2 và compatibility fixtures | P0 | readiness | M0 | 40–60 | PRE-006 | TODO | — |
| [ ] | [S-001](tracks/D.md#s-001) — Dựng project tối thiểu và cài APK đầu | P0 | foundation | M1 | 45–75 | PRE-001, PRE-002, PRE-008 | TODO | — |
| [ ] | [S-002](tracks/D.md#s-002) — Khai báo contract và fake wiring | P0 | foundation | M1 | 30–45 | S-001, PRE-007 | TODO | — |
| [ ] | [D-004](tracks/D.md#d-004) — ProfileStorage local và xóa ảnh cá nhân | P0 | core | M2 | 75–120 | S-002 | TODO | — |
| [ ] | [D-006](tracks/D.md#d-006) — Fake ProfileStorage và preview app experience | P0 | foundation | M1 | 25–40 | S-002 | TODO | — |
| [ ] | [BACK-008](tracks/D.md#back-008) — Onboarding và form hồ sơ tối giản | P0 | core | M2 | 60–90 | D-006 | TODO | — |
| [ ] | [C-001](tracks/D.md#c-001) — Today: yêu cầu và danh sách outfit trực quan | P0 | core | M3 | 75–120 | D-006, B-005 | TODO | — |
| [ ] | [C-003](tracks/D.md#c-003) — Nối service AI và kiểm soát request | P0 | core | M3 | 60–90 | C-001, D-006, B-005 | TODO | — |
| [ ] | [C-004](tracks/D.md#c-004) — Kiểm tra offline và vòng đời UI | P0 | core | M6 | 30–50 | C-003 | TODO | — |
| [ ] | [D-005](tracks/D.md#d-005) — Ảnh toàn thân trước/sau và consent UI | P0 | core | M2 | 45–75 | BACK-008, A-005 | TODO | — |
| [ ] | [D-007](tracks/D.md#d-007) — Tích hợp journey và chọn release mode | P0 | core | M5 | 45–75 | INT-002, D-005, C-205, C-210 | TODO | — |
| [ ] | [INT-004](tracks/D.md#int-004) — Đóng gói APK và kiểm tra bản nộp | P0 | core | M6 | 25–40 | INT-003 | TODO | — |
| [ ] | [D-003](tracks/D.md#d-003) — Diễn tập và bàn giao nộp bài | P0 | core | M6 | 20–35 | INT-004, C-205 | TODO | — |

## Counts và effort

Phút công owner gồm local tests, không phải elapsed time/ngày cam kết. Support ledger riêng ở [Runbook](RUNBOOK.md); P0 lớn hơn năng lực một phiên 5 giờ.

| Track | P0 | P1 | P2 | Tổng task | P0 phút | P1 phút | P2 phút | Tổng phút |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A | 13 | 3 | 7 | 23 | 610–1010 | 155–260 | 525–890 | 1290–2160 |
| B | 12 | 5 | 0 | 17 | 560–930 | 265–485 | 0–0 | 825–1415 |
| C | 7 | 6 | 1 | 14 | 320–595 | 360–590 | 120–240 | 800–1425 |
| D | 14 | 0 | 0 | 14 | 605–960 | 0–0 | 0–0 | 605–960 |
| ALL | 46 | 14 | 8 | 68 | 2095–3495 | 780–1335 | 645–1130 | 3520–5960 |

ID C-001/C-003/C-004 thuộc D; C-002/C-101/C-102 và LATER-006 thuộc B; D-001 thuộc A, D-002 thuộc C. Không suy owner từ prefix. C-201…213 là backlog VTO. Đổi estimate/priority/dependency phải cập nhật card, tổng và graph cùng lượt.
