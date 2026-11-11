# Track D — App Foundation & Experience

[Workflow](../../../TEAM_WORKFLOW.md) · [Board](../TASK_BOARD.md) · [Contracts](../CONTRACTS.md) · [Milestones](../DEPENDENCIES_AND_INTEGRATION.md)

Task card ở file này là nguồn scope/acceptance. Trạng thái chỉ ở Board; mọi task còn TODO. ID cũ giữ ổn định dù prefix khác owner: **owner trong bảng là người chịu trách nhiệm hiện tại**. Chọn task có dependency đạt theo priority, không theo ngày. Mọi đường dẫn source là deliverable tương lai, chưa có implementation.

## Hàng đợi P0

<a id="pre-006"></a>

## PRE-006 — Wireflow Profile → Wardrobe → Today → Try-On

**Mục tiêu:** Wireflow Profile → Wardrobe → Today → Try-On.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-006 |
| 2. Task name | Wireflow Profile → Wardrobe → Today → Try-On |
| 3. Description | Chốt journey có profile tối giản, nhập đồ hai mặt, kết quả outfit trực quan và VTO cho outfit được chọn. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | Định hướng mới của nhóm; CT-01…07, scope modes trong README. |
| 8. Deliverables | Wireflow và copy consent/loading/error/partial/not-supported; không cần design system lớn. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không buộc có cân nặng/ảnh để phối đồ; thiếu back không giả output back; VTO không auto-run 5 bộ. |
| 11. Testing | Paper walkthrough với A/B/C cho đủ/thiếu ảnh, unknown gate, error. |
| 12. Estimated effort | 30–45 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Thiết kế quá nhiều màn hoặc yêu cầu animation. |
| 14. Fallback / Simplification | Giữ Material và một card visual dùng lại; cắt animation chứ không bỏ disclosure/consent. |
| 15. Definition of Done | Entry point từng feature và các error path được owner nhận, không ràng buộc ngày. |

1. Vẽ 4 route với đường skip profile fields/ảnh và đường sửa dữ liệu.
2. Today có occasion, style/free text, count 1–5, focus, chọn outfit; VTO chỉ chạy khi nhấn.
3. Phân biệt collage, mô phỏng một mặt, mô phỏng hai mặt và ảnh không được kiểm chứng; không claim đúng size/fit.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="pre-007"></a>

## PRE-007 — Chốt contract v2 và compatibility fixtures

**Mục tiêu:** Chốt contract v2 và compatibility fixtures.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-007 |
| 2. Task name | Chốt contract v2 và compatibility fixtures |
| 3. Description | Chốt đặc tả trong CONTRACTS để từng owner có thể làm với fake. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | [PRE-006](../tracks/D.md#pre-006) |
| 7. Input / Output contracts | CONTRACTS v2: CT-01…07; 7 domain models; A/B/C review. |
| 8. Deliverables | Spec/version và mẫu success/error/partial, capability negotiation VTO, checklist consumer. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | A/B/C/D xác nhận JSON và interface; có test case deleted ID, missing back, duplicate job, privacy deny. |
| 11. Testing | Tabletop contract tests các fixture CT-07; không cần service live. |
| 12. Estimated effort | 40–60 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Tranh luận schema dài hạn làm mất thời gian. |
| 14. Fallback / Simplification | Giữ model tối thiểu; chưa implement cloud/auth hay 3D; capability unknown không coi là supported. |
| 15. Definition of Done | V2 có owner và fixtures đủ cho triển khai độc lập; changes tiếp theo phải có consumer review. |

1. Chốt ID/revision/front-back refs/nullability; profile optional và payload privacy.
2. Chốt 1–5 outfits, focus/exclude, structured validation; async TryOnRequest/Result per view.
3. Chốt fakes theo owner, dependency không vòng, schema/route registration và cách change contract.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-008"></a>

## BACK-008 — Onboarding và form hồ sơ tối giản

**Mục tiêu:** Onboarding và form hồ sơ tối giản.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-008 |
| 2. Task name | Onboarding và form hồ sơ tối giản |
| 3. Description | Form hồ sơ tối giản: chiều cao, style/category preferences; cân nặng tùy chọn, skip/edit/reset rõ ràng, không behavioral ranking. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [D-006](../tracks/D.md#d-006) |
| 7. Input / Output contracts | CT-04 UserProfile/ProfileStorage fake; UI cho height/styles/preferences, weight optional. |
| 8. Deliverables | profile/ProfileScreen/ViewModel, skip/edit/reset flows. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Skip profile/weight/photos vẫn tới Today; save/reopen giữ values khi storage thật; không nhập thì null, không 0 giả. |
| 11. Testing | UI fake invalid/empty/save error; device storage ở INT-001. |
| 12. Estimated effort | 60–90 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Form đòi thông tin thừa hoặc tư vấn size sai. |
| 14. Fallback / Simplification | Chỉ height/styles, hoãn field khác; không yêu cầu ảnh trước khi phối đồ. |
| 15. Definition of Done | Profile UI độc lập fake pass; D-004 làm storage, D-005 nhận ảnh riêng. |

1. Form height cm optional, style/category preferences optional; weight hoàn toàn tùy chọn, không bắt giới tính/tuổi.
2. Validate format/range để người dùng sửa, không suy luận sức khỏe/body shape; skip vẫn dùng app.
3. Save qua interface; onboarding completed flag không phụ thuộc ảnh; delete/reset có xác nhận.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="s-001"></a>

## S-001 — Dựng project tối thiểu và cài APK đầu

**Mục tiêu:** Dựng project tối thiểu và cài APK đầu.

| Trường | Nội dung |
|---|---|
| 1. Task ID | S-001 |
| 2. Task name | Dựng project tối thiểu và cài APK đầu |
| 3. Description | Tạo app Android tối thiểu và route registration cho các feature theo contract. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / foundation / M1 |
| 6. Dependencies | [PRE-001](../tracks/B.md#pre-001), [PRE-002](../tracks/A.md#pre-002), [PRE-008](../tracks/B.md#pre-008) |
| 7. Input / Output contracts | PRE-002 toolchain; CT ownership; Profile/Wardrobe/Today/TryOn routes. |
| 8. Deliverables | :app Compose, navigation, Material theme, Gradle/Manifest, APK shell. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | 4 route placeholder mở/back được trên máy thật; build clean; không crash cold start. |
| 11. Testing | Build + install + force-stop/reopen trên máy chính. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Gradle/JDK download; dependency mismatch. |
| 14. Fallback / Simplification | Một module, manual DI, theme Material; không tạo 15 Gradle modules/Hilt nếu chưa cần. |
| 15. Definition of Done | Có APK đầu và log build/device; root files có owner D. |

1. Khởi tạo :app, dependency Room/serialization/network theo toolchain đã kiểm.
2. Tạo route placeholders; feature tự export screen, D sở hữu NavHost/root.
3. Build/cài máy thật; chưa tích hợp mọi feature và không claim AI.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="s-002"></a>

## S-002 — Khai báo contract và fake wiring

**Mục tiêu:** Khai báo contract và fake wiring.

| Trường | Nội dung |
|---|---|
| 1. Task ID | S-002 |
| 2. Task name | Khai báo contract và fake wiring |
| 3. Description | Khai báo models/interfaces v2 và composition root manual DI; mỗi track tự làm fake của service mình qua A-005/B-005/C-206/D-006. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / foundation / M1 |
| 6. Dependencies | [S-001](../tracks/D.md#s-001), [PRE-007](../tracks/D.md#pre-007) |
| 7. Input / Output contracts | PRE-007 CT-01…07; service registry và route signatures. |
| 8. Deliverables | contracts/** models/interfaces; bootstrap/AppContainer nhận adapters theo owner. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Tất cả model/service compile, không imports feature chéo; fake từng track có chỗ cắm rõ. |
| 11. Testing | Compile và review imports/signatures với các consumer. |
| 12. Estimated effort | 30–45 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Interface khác tài liệu; callback circular. |
| 14. Fallback / Simplification | Không thêm generic framework hoặc DI annotations; D sửa trực tiếp root trong patch nhỏ. |
| 15. Definition of Done | Contracts compile và mỗi owner nhận boundary; M1 chỉ pass sau đủ fake tasks. |

1. Tạo models/result và service signatures v2; types không phụ thuộc impl của A/B/C/D.
2. Khai báo điểm wiring/route; mỗi track tự làm fake trong package mình qua A-005/B-005/C-206/D-006.
3. Compile interface và công bố revision; không chờ provider/model.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="int-004"></a>

## INT-004 — Đóng gói APK và kiểm tra bản nộp

**Mục tiêu:** Đóng gói APK và kiểm tra bản nộp.

| Trường | Nội dung |
|---|---|
| 1. Task ID | INT-004 |
| 2. Task name | Đóng gói APK và kiểm tra bản nộp |
| 3. Description | Tạo artifact cố định từ bản đã kiểm tra, xác minh chính APK này cài được. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M6 |
| 6. Dependencies | [INT-003](../tracks/A.md#int-003) |
| 7. Input / Output contracts | Regression candidate theo mode, event submission rules và toolchain chuẩn. |
| 8. Deliverables | APK cuối, SHA-256, build identifier, README cài và disclosure; location không chứa secrets. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | APK có checksum, cài và mở trên máy thật, call live thành công; token hợp lệ trong khung demo; không provider key. |
| 11. Testing | Artifact install smoke bắt buộc; D+A xác nhận bản cuối. |
| 12. Estimated effort | 25–40 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Nhầm APK cũ; debug signing đổi làm không update được; token hết hạn. |
| 14. Fallback / Simplification | Giữ debug APK đã test nếu được thể lệ cho phép; không chuyển release/signing stack sát giờ. |
| 15. Definition of Done | D ký artifact/build/mode, A xác nhận thiết bị; yêu cầu nộp tuân lịch BTC tách khỏi acceptance. |

1. Build APK đúng yêu cầu BTC, lưu checksum và source/disclosure mapping.
2. Cài chính artifact đó; A kiểm restart, B live AI; C kiểm VTO nếu enabled.
3. Ghi capability/mode và limitations, backup APK; không sửa signing stack sát deadline.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="d-003"></a>

## D-003 — Diễn tập và bàn giao nộp bài

**Mục tiêu:** Diễn tập và bàn giao nộp bài.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-003 |
| 2. Task name | Diễn tập và bàn giao nộp bài |
| 3. Description | Chạy pitch gắn đúng artifact cuối, kiểm tra submission và disclosure. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M6 |
| 6. Dependencies | [INT-004](../tracks/D.md#int-004), [C-205](../tracks/C.md#c-205) |
| 7. Input / Output contracts | Artifact, gate report, disclosure; outline tối giản nếu D-002 P1 chưa làm. |
| 8. Deliverables | Kịch bản demo cuối, người cầm máy/thuyết trình, receipt nộp do nhóm thực hiện. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Pitch không vượt thời gian; đúng artifact; receipt hoặc trạng thái nộp rõ; các claim khớp evidence. |
| 11. Testing | Dry run trên máy chính; A cầm máy, B theo dõi kết nối khi demo, C hỗ trợ điều hướng. |
| 12. Estimated effort | 20–35 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Nộp trễ; chỉ có video không có app; nói quá kết quả. |
| 14. Fallback / Simplification | Bỏ slide phụ, giữ app live và disclosure; video chỉ tài liệu phụ không thay AI live. |
| 15. Definition of Done | Nhóm nhận package bàn giao và trạng thái nộp; chưa có receipt thì không đánh dấu đã nộp. |

1. Diễn tập app thật: profile/wardrobe/AI list; selected try-on chỉ mode đã pass.
2. Nếu VTO unavailable/partial nêu đúng; cảnh báo mô phỏng không phải độ vừa vặn, không dùng pre-render làm live.
3. Nhóm nộp theo lịch BTC, lưu receipt; báo rõ pre-existing vs new substantial work.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-001"></a>

## C-001 — Today: yêu cầu và danh sách outfit trực quan

**Mục tiêu:** Today: yêu cầu và danh sách outfit trực quan.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-001 |
| 2. Task name | Today: yêu cầu và danh sách outfit trực quan |
| 3. Description | Tạo UI nhập dịp/style/yêu cầu, trạng thái loading và danh sách 1–5 outfit có ảnh, giải thích và chọn đúng một outfit. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [D-006](../tracks/D.md#d-006), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-02/03/04; fake recommendation 1/5/fewer; A-006 visual slot placeholder có API chốt. |
| 8. Deliverables | today/TodayScreen/ViewModel input occasion/style/text/count, list outfit và selection. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | 1 và 5 outfit render hình+giải thích riêng; selection đúng ID; count giới hạn; tủ thiếu có hướng dẫn; không gọi 5 VTO jobs. |
| 11. Testing | Compose/UI fixtures 0/1/5/missing image/loading/error; source label. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Chờ A vì muốn ảnh thật quá sớm; mở rộng carousel. |
| 14. Fallback / Simplification | List cards thay carousel/animation; vẫn trực quan bằng ảnh món đồ. |
| 15. Definition of Done | Today chạy fake không chờ Room/AI/VTO; screenshot chỉ là UI evidence, không AI live. |

1. Input dịp, style override, text ≤500 ký tự, count 1–5; dùng profile defaults không ép profile đầy đủ.
2. Render list với ItemCard/OutfitVisual composable A cung cấp; preview placeholder cho phát triển độc lập.
3. Select một outfit; CTA try-on chuyển outcome/ID sang C screen, không auto-generate; UI flags reroll/focus khi capability sẵn.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-003"></a>

## C-003 — Nối service AI và kiểm soát request

**Mục tiêu:** Nối service AI và kiểm soát request.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-003 |
| 2. Task name | Nối service AI và kiểm soát request |
| 3. Description | Điều phối snapshot tủ → generator → validate/render hoặc fallback. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [C-001](../tracks/D.md#c-001), [D-006](../tracks/D.md#d-006), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-01/02/03/04; snapshots và cancellable recommendation; fallback optional B. |
| 8. Deliverables | today coordinator context assembly + selected outfit revision checks. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Double tap/rotation không duplicate call; deleted/edited item làm result stale; no body photo hoặc weight gửi recommendation mặc định. |
| 11. Testing | Coroutine fake timeout/stale/revision change/empty profile; no actual API required. |
| 12. Estimated effort | 60–90 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Retry ngầm làm call nhiều lần; UI tự gọi khi compose. |
| 14. Fallback / Simplification | Chỉ gọi khi user nhấn, không progressive AI thay kết quả, không auto refresh. |
| 15. Definition of Done | Context/state tests pass; errors không cần fallback P1 để hết loading. |

1. Đọc wardrobe/profile snapshots, lấy weather nullable không block; build DTO tối thiểu.
2. Một request đang chạy; input change/cancel không để response cũ override; giữ selection theo stable ID.
3. Validate current item revisions trước try-on; không tự retry khi rotation; reroll/focus dùng schema đã có chỉ bật khi B capability ready.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="c-004"></a>

## C-004 — Kiểm tra offline và vòng đời UI

**Mục tiêu:** Kiểm tra offline và vòng đời UI.

| Trường | Nội dung |
|---|---|
| 1. Task ID | C-004 |
| 2. Task name | Kiểm tra offline và vòng đời UI |
| 3. Description | Chạy failure matrix và sửa UI bị kẹt, cùng app đã wiring thật nếu sẵn. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M6 |
| 6. Dependencies | [C-003](../tracks/D.md#c-003) |
| 7. Input / Output contracts | CT-03/07; Fake recommendation error và APK live nếu có. |
| 8. Deliverables | Kết quả timeout/offline/rotation/empty kèm bug hoặc PASS. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không loading vô hạn hoặc call duplicate do rotation; lỗi typed khi offline; RULES chỉ khi C-002 đã tích hợp và đúng nhãn; empty có CTA hữu ích. |
| 11. Testing | Unit fake clock và kiểm thử thiết bị; real outage bằng tắt mạng, không phụ thuộc provider lỗi ngẫu nhiên. |
| 12. Estimated effort | 30–50 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Cancel không được propagate; device test muộn. |
| 14. Fallback / Simplification | Bỏ animation/state lưu kết quả dài hạn, giữ state machine tối thiểu. |
| 15. Definition of Done | D xác nhận Today lifecycle; VTO state-machine tests ở C-208/209 không dồn vào đây. |

1. Airplane mode request rồi online retry; RULES nếu enabled, nếu không error đúng.
2. Rotation/back/force-stop không tạo new call tự động; khôi phục profile/tủ từ storage.
3. Kiểm selected outfit invalidation và source labels, ghi owner bug thuộc B/A/D.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="d-004"></a>

## D-004 — ProfileStorage local và xóa ảnh cá nhân

**Mục tiêu:** ProfileStorage local và xóa ảnh cá nhân.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-004 |
| 2. Task name | ProfileStorage local và xóa ảnh cá nhân |
| 3. Description | Lưu profile nhỏ bằng typed atomic file cùng serializer hiện có; ảnh qua MediaStore interface A, test bằng fake. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-04 UserProfile/ProfileStorage; CT-01 media refs; no provider networking. |
| 8. Deliverables | profile/data LocalProfileStorage, persistence/delete hooks, backup rules patch. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Reopen giữ null/values/refs; clear ảnh không hồi sinh qua profile cache; save không tạo network call; không require weight. |
| 11. Testing | Unit roundtrip/failed write/delete refs; device restart và kiểm backup configuration theo máy. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Backup mặc định; atomic file/media mismatch; scope deletion giữa D/C. |
| 14. Fallback / Simplification | Giữ profile chỉ metadata nếu chưa có privacy readiness; ảnh vẫn optional. |
| 15. Definition of Done | ProfileStorage tests pass, A/C nhận deletion/invalidation contract, no hidden cloud sync. |

1. UUID profile ổn định/revision, nullable fields, front/back refs; metadata atomic, ảnh không BLOB.
2. Clear field/photo/profile có confirmation ở UI, callback invalidation tới C; không tự upload khi save.
3. Exclude private profile/body/result data khỏi cloud backup và device-transfer theo SDK; verify config, không tự bật cloud sync.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="d-005"></a>

## D-005 — Ảnh toàn thân trước/sau và consent UI

**Mục tiêu:** Ảnh toàn thân trước/sau và consent UI.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-005 |
| 2. Task name | Ảnh toàn thân trước/sau và consent UI |
| 3. Description | UI import/replace/delete body front/back qua MediaStore fake/real, tách consent upload theo job. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [BACK-008](../tracks/D.md#back-008), [A-005](../tracks/A.md#a-005) |
| 7. Input / Output contracts | CT-01/04/06; UI privacy copy C-210 sẽ review, chưa upload ở đây. |
| 8. Deliverables | profile/BodyPhotosSection với view labels và lựa chọn skip/delete. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không có ảnh vẫn phối đồ; nhập 1 view không tạo view kia; delete trigger invalidation, không upload khi import. |
| 11. Testing | UI fake picker cancel/replace/delete và input scope; real storage tại INT-001. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Ảnh private bị dùng fixture/log; consent checkbox quá rộng. |
| 14. Fallback / Simplification | Chỉ front optional nhưng ghi back missing; không vô hiệu hóa privacy controls. |
| 15. Definition of Done | UI local fake pass và D/C thống nhất consent boundary. |

1. Chọn ảnh FRONT/BACK độc lập, hướng dẫn góc chụp ngắn; không đo cơ thể hay gán body type bằng AI.
2. Preview local, replace/delete và thông báo local storage; missing back không tự clone front.
3. CTA Try-On hiển thị provider/data mục đích và hỏi consent trước submit bên C; consent demo không mặc định.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="d-006"></a>

## D-006 — Fake ProfileStorage và preview app experience

**Mục tiêu:** Fake ProfileStorage và preview app experience.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-006 |
| 2. Task name | Fake ProfileStorage và preview app experience |
| 3. Description | Fake profile nullable/body refs và consent status, preview 4 route states. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / foundation / M1 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-03/04; route contract, no provider. |
| 8. Deliverables | profile/fakes và bootstrap dev bindings cho D screens. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | D screens chạy standalone fake; không imports real feature impl trong contracts. |
| 11. Testing | Unit fake contract và route smoke. |
| 12. Estimated effort | 25–40 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Fake luôn complete profile nên bỏ sót null. |
| 14. Fallback / Simplification | Dùng 2 profiles minimal/full và error switches. |
| 15. Definition of Done | M1 experience fake sẵn, không cần AI/VTO account. |

1. Fake observe/save/clear và missing front/back; stable UUID/revision.
2. Preview skip onboarding, errors, selected outfit route dùng C fake.
3. No real upload/secret, banner DEV rõ; A/B/C fake được mount khi có không cần đợi real.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="d-007"></a>

## D-007 — Tích hợp journey và chọn release mode

**Mục tiêu:** Tích hợp journey và chọn release mode.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-007 |
| 2. Task name | Tích hợp journey và chọn release mode |
| 3. Description | Integration coordinator chọn CORE/FRONT/DUAL theo bằng chứng, không chịu sửa lỗi tất cả modules. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P0 / core / M5 |
| 6. Dependencies | [INT-002](../tracks/B.md#int-002), [D-005](../tracks/D.md#d-005), [C-205](../tracks/C.md#c-205), [C-210](../tracks/C.md#c-210) |
| 7. Input / Output contracts | CT-01…07; M2/M3, gate record, optional C-212/C-211 runtime pass. |
| 8. Deliverables | M5-CORE hoặc M5-FRONT/DUAL record; root bindings/route selection/feature flags. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | M5-CORE có M3 live và VTO disabled trung thực; M5-FRONT/DUAL chỉ khi runtime tương ứng pass. Core không được đổi tên thành full VTO hoàn tất. |
| 11. Testing | E2E device và fake delayed job/privacy invalidation; integration support có ledger riêng. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | D trở thành người sửa hộ mọi bug; bật feature từ POC chưa có runtime. |
| 14. Fallback / Simplification | Ship CORE có limitation rõ khi VTO gate fail, ghi M4/M5-full chưa đạt; không thay requirement âm thầm. |
| 15. Definition of Done | Root wiring và mode record đúng evidence; mọi blocker có owner, không thiếu acceptance của mode đã bật. |

1. Nối profile→wardrobe→Today→selected outfit; stable IDs/revisions đi xuyên suốt, không để selection thay trong job.
2. Mở VTO chỉ khi gate và C-212 pass; DUAL còn cần C-211, thiếu thì UI unavailable hoặc mode front đã được duyệt.
3. Chạy real-store delete/revoke với late-result fixtures C; giao bug A media/B AI/C tryon/D navigation.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.
