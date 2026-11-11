# Track A — Wardrobe & Imaging

[Workflow](../../../TEAM_WORKFLOW.md) · [Board](../TASK_BOARD.md) · [Contracts](../CONTRACTS.md) · [Milestones](../DEPENDENCIES_AND_INTEGRATION.md)

Task card ở file này là nguồn scope/acceptance. Trạng thái chỉ ở Board; mọi task còn TODO. ID cũ giữ ổn định dù prefix khác owner: **owner trong bảng là người chịu trách nhiệm hiện tại**. Chọn task có dependency đạt theo priority, không theo ngày. Mọi đường dẫn source là deliverable tương lai, chưa có implementation.

## Hàng đợi P0

<a id="pre-002"></a>

## PRE-002 — Kiểm tra toolchain và máy Android

**Mục tiêu:** Kiểm tra toolchain và máy Android.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-002 |
| 2. Task name | Kiểm tra toolchain và máy Android |
| 3. Description | Xác minh toolchain/Room compatibility và máy thật bằng sample công khai được phép; không viết core sản phẩm trong bước research. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | Android Studio/JDK/SDK của nhóm; một máy chính và nếu có một máy dự phòng; DEC-02/06. |
| 8. Deliverables | Bảng phiên bản tương thích, model/API thiết bị, lệnh build/cài và kết quả trong ghi chú readiness. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Ít nhất 1 máy thật mở được sample; A và D tái lập được build, không chỉ IDE sync thành công. |
| 11. Testing | Build smoke và kiểm thử máy thật; ghi Android API và lỗi nếu có. |
| 12. Estimated effort | 40–70 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Tải SDK chậm; driver; JDK mismatch; chưa ai quen Kotlin. |
| 14. Fallback / Simplification | Một build laptop chuẩn và một máy chính; thiếu Android readiness là blocker thật, không thay bằng browser. |
| 15. Definition of Done | Có ma trận readiness và bằng chứng sample chạy; không đánh dấu app FitCheckAI đã chạy. |

1. Ghi JDK/AGP/Gradle/Kotlin/Compose từ template đã chọn; tải dependency được phép trước.
2. Build sample công khai, bật USB debugging, cài và mở trên máy chính.
3. Kiểm tra cáp, dung lượng, quyền cài APK, adb, sạc và hotspot; ghi máy dự phòng.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="pre-004"></a>

## PRE-004 — Chuẩn bị ảnh và fixture có quyền dùng

**Mục tiêu:** Chuẩn bị ảnh và fixture có quyền dùng.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-004 |
| 2. Task name | Chuẩn bị ảnh và fixture có quyền dùng |
| 3. Description | Tạo bộ ảnh nhỏ đủ kiểm thử nhập ảnh và outfit, không seed dữ liệu giả thành dữ liệu người dùng. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / readiness / M0 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | CT-01/05; ảnh quần áo có quyền dùng, hai mặt cùng một món. |
| 8. Deliverables | Fixture 6 món, ít nhất 3 cặp front/back, nhãn chuẩn và manifest nguồn không chứa ảnh cá nhân. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Có ít nhất 6 items, trong đó 3 cặp front/back đúng món, thêm case thiếu/mismatch; nguồn rõ và file mở được. |
| 11. Testing | A kiểm ảnh/URI trên máy; B review ground truth; không suy diễn quyền ảnh người. |
| 12. Estimated effort | 35–60 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Ảnh cloud chưa tải; quyền dùng không rõ. |
| 14. Fallback / Simplification | Dùng đồ tự chụp và ghi nguồn; bộ 2 ảnh chỉ đủ spike, chưa đạt acceptance 6 items/3 cặp thì task chưa DONE. |
| 15. Definition of Done | Có manifest và fixture chuyển giao cho C/B, không tuyên bố ảnh đã được import vào app. |

1. Chuẩn bị TOP/BOTTOM/DRESS và ảnh trước/sau có chi tiết khác nhau; cùng ID item.
2. Thêm case thiếu mặt sau, ảnh nhòe, nhầm hai món khác nhau; ghi nhãn kỳ vọng.
3. Ghi licence/consent, copy gallery, không dùng ảnh người của C làm sample public.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-005"></a>

## BACK-005 — Nối recognition vào form và sửa thuộc tính

**Mục tiêu:** Nối recognition vào form và sửa thuộc tính.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-005 |
| 2. Task name | Nối recognition vào form và sửa thuộc tính |
| 3. Description | Nối suggestion recognition vào form, cho sửa và giữ provenance/revision; chất lượng model được đo riêng ở B-006. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [A-003](../tracks/A.md#a-003), [B-005](../tracks/B.md#b-005) |
| 7. Input / Output contracts | CT-05 ClothingRecognition interface, fake results có revision/conflict; không cần endpoint thật. |
| 8. Deliverables | Wardrobe auto-fill/correction state và integration seam. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Manual edit không bị response muộn ghi đè; tag fail vẫn lưu; back conflict có cảnh báo; source per field rõ. |
| 11. Testing | UI fake delayed/error/conflict; real client smoke ở INT-002. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Response cũ áp dụng nhầm draft; A/B lệch enums. |
| 14. Fallback / Simplification | Giảm field về category; ghi rõ phạm vi, không hạ tiêu chuẩn bằng đổi nhãn sau test. |
| 15. Definition of Done | UI fake tests pass, ready real binding; quality report do B-006 chịu trách nhiệm. |

1. Gọi interface với item draft/revision/front/back sau consent garment upload.
2. Merge suggestions chỉ vào field chưa sửa; conflict/unknown hiện để xác nhận, lỗi vẫn manual.
3. Save attributes đã xác nhận; response stale không ghi đè draft hoặc ảnh mới.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-012"></a>

## BACK-012 — Sửa/xóa món và lọc tủ đồ

**Mục tiêu:** Sửa/xóa món và lọc tủ đồ.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-012 |
| 2. Task name | Sửa/xóa món và lọc tủ đồ |
| 3. Description | CRUD thuộc core: sửa thuộc tính, thay ảnh, xóa có xác nhận, lọc category/color; invalidate outfit tham chiếu item cũ. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [A-001](../tracks/A.md#a-001), [A-003](../tracks/A.md#a-003), [A-002](../tracks/A.md#a-002) |
| 7. Input / Output contracts | CT-01 repository upsert/delete/revision và media ownership; outfit stale check. |
| 8. Deliverables | Item detail/edit UI, repository update/delete/filter và tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Edit/reopen giữ thuộc tính; cancel delete không đổi dữ liệu; delete không để Today hiển thị item mất; filter trả đúng kết quả. |
| 11. Testing | Unit/integration update/delete/file-failure; device edit/delete/reopen và stale outfit. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Xóa ảnh đang được job dùng; chưa có cloud tombstone policy. |
| 14. Fallback / Simplification | Giữ delete local với invalidation đúng; filter nâng cao có thể hoãn. |
| 15. Definition of Done | CRUD/durability pass, C/D nhận invalidation rules; không tạo wear log. |

1. Edit giữ ID, tăng revision; replace view qua MediaStore không đè file đang dùng.
2. Delete đánh tombstone và hide item, cleanup refs sau transaction; stale outfit không được Try-On.
3. Filter category/color; thiếu file hiển thị lỗi có thể sửa, không wipe DB.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="d-001"></a>

## D-001 — Checklist nghiệm thu và hồ sơ evidence

**Mục tiêu:** Checklist nghiệm thu và hồ sơ evidence.

| Trường | Nội dung |
|---|---|
| 1. Task ID | D-001 |
| 2. Task name | Checklist nghiệm thu và hồ sơ evidence |
| 3. Description | Chuẩn bị dữ liệu và mẫu ghi kết quả để integration không phải nghĩ cách test từ đầu. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M6 |
| 6. Dependencies | [PRE-007](../tracks/D.md#pre-007), [PRE-004](../tracks/A.md#pre-004) |
| 7. Input / Output contracts | CT-01…07; matrix Core/VTO-front/VTO-dual trong integration plan. |
| 8. Deliverables | Case index và evidence template cho từng owner A/B/C/D. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Tất cả P0 acceptance có case kiểm tra; không ghi PASS trước khi chạy. |
| 11. Testing | Review checklist cùng A/C; test procedure trên APK placeholder chỉ cho phần shell. |
| 12. Estimated effort | 30–45 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Quên gắn kết quả với build; thiếu thiết bị. |
| 14. Fallback / Simplification | Một bảng Markdown và một máy thật đủ; bỏ video đẹp nếu tốn giờ. |
| 15. Definition of Done | Matrix có owner từng case và acceptance mapping; không cần chờ toàn bộ skeleton mới chuẩn bị. |

1. Lập tests Room/profile/recognition/list outfits/selected try-on/consent/delete.
2. Ghi expected source và gate per mode; case chưa chạy NOT RUN.
3. Mỗi owner nhận test module; A điều phối device execution, D nhận chỉ blocker/wiring.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="int-001"></a>

## INT-001 — Tích hợp Room, media và Profile

**Mục tiêu:** Tích hợp Room, media và Profile.

| Trường | Nội dung |
|---|---|
| 1. Task ID | INT-001 |
| 2. Task name | Tích hợp Room, media và Profile |
| 3. Description | Thay fake repository bằng repository thật tại composition root, giữ AI fake có nhãn DEV nếu B chưa sẵn. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [A-001](../tracks/A.md#a-001), [A-002](../tracks/A.md#a-002), [A-003](../tracks/A.md#a-003), [D-004](../tracks/D.md#d-004), [BACK-008](../tracks/D.md#back-008), [D-005](../tracks/D.md#d-005), [BACK-012](../tracks/A.md#back-012) |
| 7. Input / Output contracts | CT-01/04, real Wardrobe/ProfileStorage/MediaStore; root patch do D áp dụng. |
| 8. Deliverables | M2 evidence: profile + garment/front-back refs bền, navigation hoạt động. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Ảnh người và garment tách scope; save/reopen giữ IDs/refs; skip photos vẫn dùng wardrobe; chỉ fake AI còn lại. |
| 11. Testing | Integration machine + real device; A owner test, D hỗ trợ binding riêng ledger. |
| 12. Estimated effort | 30–50 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Đọc hai instance khác state; root conflict. |
| 14. Fallback / Simplification | Giảm trang trí, giữ luồng Profile/Wardrobe và dữ liệu dùng cho Today; không hardcode danh sách để che lỗi. |
| 15. Definition of Done | M2 PASS có build/device, fake AI còn lại ghi rõ. |

1. A cung cấp adapter/media binding, D thay root fake; mỗi repo chỉ một app instance.
2. Nhập profile optional/body photos optional và 2 garment items có front/back; mở lại kiểm refs.
3. A sửa media/Room, D sửa profile; không cho A sửa tất cả profile logic hoặc D sửa Room hộ.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="int-003"></a>

## INT-003 — Regression và quyết định đóng băng

**Mục tiêu:** Regression và quyết định đóng băng.

| Trường | Nội dung |
|---|---|
| 1. Task ID | INT-003 |
| 2. Task name | Regression và quyết định đóng băng |
| 3. Description | Kiểm thử các lỗi làm mất điểm nặng, quyết định GO/NO-GO và danh sách sửa cuối. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M6 |
| 6. Dependencies | [D-007](../tracks/D.md#d-007), [A-004](../tracks/A.md#a-004), [C-004](../tracks/D.md#c-004), [D-001](../tracks/A.md#d-001) |
| 7. Input / Output contracts | M5 evidence theo mode, core tests và VTO tests tương ứng; APK candidate. |
| 8. Deliverables | Bảng regression với mỗi case có outcome, owner và bug severity. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không crash/data loss/key leak/invalid IDs/upload không consent; feature VTO bật chỉ khi acceptance mode đó pass. |
| 11. Testing | Regression device có checklist theo mode; mocks fault injection, live bằng requestId thật. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Máy thứ hai không có; lỗi sát freeze. |
| 14. Fallback / Simplification | Chạy toàn bộ case trên máy chính và ghi thiếu coverage thiết bị; revert stretch gây lỗi, không bỏ test đường chính. |
| 15. Definition of Done | A tổng hợp regression PASS, D xác nhận release scope; unresolved blockers không bị hạ ưu tiên để nộp. |

1. Chạy device matrix và force-stop; privacy delete/revoke/upload opt-out là release blocker.
2. Mỗi owner sửa lỗi module mình, A retest steps; D chỉ xử lý root/navigation.
3. Ghi mode CORE/FRONT/DUAL và gates chưa đạt; không pass M4 chỉ vì fallback collage.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-001"></a>

## A-001 — Room schema và Clothing Repository

**Mục tiêu:** Room schema và Clothing Repository.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-001 |
| 2. Task name | Room schema và Clothing Repository |
| 3. Description | Lưu metadata tủ đồ, observe list và phục hồi sau process death. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-01 ClothingItem/ClothingAttributes/MediaRef; Room version đã kiểm. |
| 8. Deliverables | wardrobe/data Room entities/DAO/database/repository, schema export và tests. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Save/reopen giữ IDs và front/back; write lỗi rollback; deleted item bị loại khỏi snapshot; migration schema có test khi thay đổi. |
| 11. Testing | Room instrumented CRUD/transactions, schema validation; force-stop ở A-004. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | KSP/Room compatibility; file và DB không cùng transaction. |
| 14. Fallback / Simplification | Giảm attributes nhưng vẫn Room; metadata/ảnh cleanup có recovery, không quay lại JSON mặc định. |
| 15. Definition of Done | Room tests pass và repository CT-01 được D/B consumer review. |

1. Tạo item/media refs với UUID, revision, created/updated/deletedAt; front required/back nullable; index ID.
2. Triển khai observe/getSnapshot/upsert/delete bằng transaction, revision check; ảnh là file riêng không BLOB.
3. Export schema, test process reopen/rollback/delete; không tạo WearLog/Insights table.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-002"></a>

## A-002 — MediaStore và nhập ảnh front/back

**Mục tiêu:** MediaStore và nhập ảnh front/back.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-002 |
| 2. Task name | MediaStore và nhập ảnh front/back |
| 3. Description | Chọn một ảnh, sao chép vào internal storage và hiển thị preview. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-01 MediaStore/ImagePair; fake file refs; PRE-004 fixtures. |
| 8. Deliverables | media/LocalMediaStore do A sở hữu; wardrobe picker slot FRONT/BACK; filesDir/noBackupFilesDir theo loại. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Front/back không bị tráo; cancel/back missing không mất front; ảnh còn sau restart; không lưu PII ở cache tạm không kiểm soát. |
| 11. Testing | Máy thật large/rotation/cloud URI/cancel, unit ref ownership và cleanup failure. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Cloud URI cần mạng; ảnh codec lạ; bitmap memory. |
| 14. Fallback / Simplification | Chỉ nhận JPEG/PNG có kiểm tra, hướng dẫn chọn ảnh đã tải; không dùng cacheDir làm storage. |
| 15. Definition of Done | A bàn giao MediaStore fake/real cùng contract cho D/C, tests pass; ghi quyền file và quy trình xóa. |

1. Import mỗi view riêng, gán mediaId ownerEntityId/view; cancel không thay view khác.
2. Decode sampling/orientation, loại EXIF, giữ original đủ chất lượng và derivative riêng; validate MIME/byte limits.
3. Trả local ref ổn định; replace/delete có cleanup journal; scope PERSON/GARMENT/TRYON_RESULT, không public gallery tự động.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-003"></a>

## A-003 — Form loại/màu và danh sách tủ đồ

**Mục tiêu:** Form loại/màu và danh sách tủ đồ.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-003 |
| 2. Task name | Form loại/màu và danh sách tủ đồ |
| 3. Description | Nối picker với form metadata thủ công, save và grid/list. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M2 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002), [A-005](../tracks/A.md#a-005) |
| 7. Input / Output contracts | CT-01/05; FakeWardrobeRepository/FakeRecognition từ fixtures, form draft. |
| 8. Deliverables | WardrobeScreen/ViewModel grid, front/back preview, edit form và save. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | UI fake nhập/sửa/hủy đúng hai view, không duplicate; manual correction có thể lưu khi recognition lỗi. |
| 11. Testing | UI fake success/late response/error; integration real tại INT-001. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Compose state lẫn draft/saved; async image loading. |
| 14. Fallback / Simplification | Grid/list đơn giản và ít field; không bỏ khả năng sửa tag. |
| 15. Definition of Done | Wardrobe UI test độc lập pass; không đợi AI endpoint hoặc Room thật để xây UI. |

1. Hiển thị slots front/back, form attributes, source MANUAL/AI và unknown.
2. Save qua repository interface; disable double tap, giữ draft nếu lỗi; không overwrite manual edits bởi response cũ.
3. Grid từ observeItems; thumbnails placeholder, empty/error states; actual binding ở INT-001.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-004"></a>

## A-004 — Kiểm tra persistence và recovery trên máy

**Mục tiêu:** Kiểm tra persistence và recovery trên máy.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-004 |
| 2. Task name | Kiểm tra persistence và recovery trên máy |
| 3. Description | Chứng minh dữ liệu và ảnh không phụ thuộc process hoặc quyền URI tạm. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M6 |
| 6. Dependencies | [INT-001](../tracks/A.md#int-001), [BACK-012](../tracks/A.md#back-012) |
| 7. Input / Output contracts | APK đã wire Room/media/profile; fixtures front/back. |
| 8. Deliverables | Device persistence report và bug owner. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | 3 lần force-stop/reopen giữ nguyên ảnh và dữ liệu; cancel không mất món đã lưu. |
| 11. Testing | Máy thật bắt buộc; kết quả persistence test A-001 được đính kèm. |
| 12. Estimated effort | 30–50 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Chỉ test trên emulator hoặc nhầm restart activity với process death. |
| 14. Fallback / Simplification | Thu nhỏ data test về 2 món nhưng vẫn phải qua cold start; không dùng seed để che lỗi lưu. |
| 15. Definition of Done | A ghi PASS/FAIL, D thấy bằng chứng và không còn lỗi mất dữ liệu chặn demo. |

1. Nhập/sửa/xóa 3 item có back; force-stop 3 lần, đối chiếu ID/revision/ảnh.
2. Simulate missing file hoặc failed import; không reset DB; kiểm xóa không hồi sinh item.
3. Kiểm ảnh profile giữ đúng refs và không lộ qua log; D sửa lỗi profile, A sửa Room/media.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-005"></a>

## A-005 — Fake wardrobe và media fixtures

**Mục tiêu:** Fake wardrobe và media fixtures.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-005 |
| 2. Task name | Fake wardrobe và media fixtures |
| 3. Description | Tạo fake WardrobeRepository/MediaStore với ảnh hai view và thao tác lỗi có thể điều khiển. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / foundation / M1 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002) |
| 7. Input / Output contracts | CT-01/07; fixture PRE-004 hoặc placeholder có quyền dùng. |
| 8. Deliverables | wardrobe/fakes và media/fakes, success/missing-back/deleted/IO-fail fixtures. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | A/D/C render/save/query qua fake; deleted ID không có trong snapshot, back nullable rõ. |
| 11. Testing | Unit contract tests fake; compile consumer preview. |
| 12. Estimated effort | 25–40 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Fake không phản ánh lỗi thật; fixture ID không ổn định. |
| 14. Fallback / Simplification | Dùng 3 item với placeholder nếu chưa có ảnh licensed; đủ success/error. |
| 15. Definition of Done | Fake contract tests pass và consumer nhận fixtures; M1 không đợi Room thật. |

1. Tạo 6 item UUID ổn định/revision/front-back, tủ rỗng và tủ thiếu slot.
2. Fake observe/upsert/delete và media missing/error để consumer test.
3. Đặt fake trong dev/test binding, banner DEV; không làm release fallback giả.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

<a id="a-006"></a>

## A-006 — Component outfit trực quan dùng lại

**Mục tiêu:** Component outfit trực quan dùng lại.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-006 |
| 2. Task name | Component outfit trực quan dùng lại |
| 3. Description | Xây OutfitVisual/ItemThumbnail nhận DTO/presentation data và callbacks, không biết provider hay NavHost. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P0 / core / M3 |
| 6. Dependencies | [S-002](../tracks/D.md#s-002), [A-005](../tracks/A.md#a-005) |
| 7. Input / Output contracts | CT-01/02/03; list item refs, selected outfit; fake data. |
| 8. Deliverables | wardrobe/presentation/OutfitVisual.kt và preview states; component API do A sở hữu. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Outfit card có đúng ảnh theo IDs, 1–5 cards không nhầm selection; ảnh lỗi không crash. |
| 11. Testing | Compose preview/tests 1/5/missing image/DRESS; D review component signature. |
| 12. Estimated effort | 45–75 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Component API đổi làm D rework. |
| 14. Fallback / Simplification | List ảnh theo hàng đơn giản, không tạo graphic canvas phức tạp. |
| 15. Definition of Done | Component tests pass, D review/nhận API ổn định; real Today integration ở INT-002, A sửa lỗi component. |

1. Ghép ảnh top/bottom hoặc dress, thumbnail front/back nếu có; dùng ảnh gốc khi chưa segmentation.
2. Hiển thị placeholder/missing và caption; collage chỉ minh họa đồ, không giả người mặc.
3. Expose select/focus callbacks; D quyết định navigation; kiểm accessibility labels.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.

## Hàng đợi P1

<a id="pre-005"></a>

## PRE-005 — Nghiên cứu người dùng ngắn và trung thực

**Mục tiêu:** Nghiên cứu người dùng ngắn và trung thực.

| Trường | Nội dung |
|---|---|
| 1. Task ID | PRE-005 |
| 2. Task name | Nghiên cứu người dùng ngắn và trung thực |
| 3. Description | Kiểm tra nỗi đau, quy trình chọn đồ và willingness-to-try trước khi thiết kế pitch. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P1 / quality / M6 |
| 6. Dependencies | Không có — có thể bắt đầu độc lập. |
| 7. Input / Output contracts | Giả thuyết phân khúc review; người tham gia tự nguyện, không tự liên hệ thay nhóm trong audit. |
| 8. Deliverables | Ghi chú ẩn danh 3–5 phỏng vấn mục tiêu, câu hỏi và tổng hợp; số thực có thể thấp hơn mục tiêu. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Không bịa số liệu; mỗi claim có ghi chú nguồn; nếu n=0 phải ghi chưa có evidence và không claim validation. |
| 11. Testing | Review câu hỏi trước khi hỏi; C đọc tổng hợp tìm kết luận vượt dữ liệu. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Mẫu nhỏ hoặc không có người trả lời; không lấy việc tuyển người làm blocker coding. |
| 14. Fallback / Simplification | Thực hiện 1–2 cuộc ngắn và khai báo mẫu nhỏ; không tạo mục tiêu 50 câu trả lời làm blocker coding. |
| 15. Definition of Done | Có hồ sơ kết quả/giới hạn thực tế và dữ liệu dùng cho D-002; đây không phải beta before/after. |

1. Hỏi vấn đề phối đồ gần nhất, nhu cầu xem trực quan và mức thoải mái với ảnh toàn thân.
2. Xin phép ghi và trích dẫn; không thu chiều cao/cân nặng/ảnh nếu không cần nghiên cứu.
3. Báo n thực, phản chứng, ngại privacy và kênh thử nghiệm; không claim đo giảm thời gian nếu chưa quan sát.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-003"></a>

## BACK-003 — Tách nền có kiểm tra model readiness

**Mục tiêu:** Tách nền có kiểm tra model readiness.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-003 |
| 2. Task name | Tách nền có kiểm tra model readiness |
| 3. Description | Thêm ML Kit segmentation như bước tùy chọn, giữ ảnh gốc nếu model/quality lỗi. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P1 / quality / M3 |
| 6. Dependencies | [A-002](../tracks/A.md#a-002) |
| 7. Input / Output contracts | Ảnh fixtures, minSdk/Play services đã xác minh; capability contract readiness. |
| 8. Deliverables | ImageSegmenter interface, implementation và UI trạng thái tải model/skip. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Chưa tải model/offline vẫn nhập ảnh gốc; ready thì xử lý được; không claim thành công nếu no-subject. |
| 11. Testing | Máy thật cold model/offline/ready; bộ 6 ảnh nền đơn giản và phức tạp. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Beta API; model không sẵn trên máy; quality kém. |
| 14. Fallback / Simplification | Tắt segmentation, dùng original image; không làm fake ảnh đã tách. |
| 15. Definition of Done | Có quality/error report và user có đường skip không mất dữ liệu. |

1. Kiểm SDK/model readiness; warm-up có ready/downloading/unavailable, không chặn nhập.
2. Xử lý front/back độc lập; giữ original và derivative refs riêng, không upload mask thay original khi provider không hỗ trợ.
3. So chất lượng và cleanup; khi no-subject thì giữ gốc, không fake transparent image.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-101"></a>

## A-101 — Chụp bằng ứng dụng camera hệ thống

**Mục tiêu:** Chụp bằng ứng dụng camera hệ thống.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-101 |
| 2. Task name | Chụp bằng ứng dụng camera hệ thống |
| 3. Description | Thêm một đường lấy ảnh camera nhẹ hơn CameraX nếu gate stretch cho phép. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P1 / quality / M2 |
| 6. Dependencies | [A-002](../tracks/A.md#a-002) |
| 7. Input / Output contracts | MediaStore.import CT-01; FileProvider do D owner manifest; máy có camera activity. |
| 8. Deliverables | wardrobe camera launcher; root manifest/provider patch qua D. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Chụp/sau cancel không crash; ảnh giữ qua restart; picker vẫn dùng được. |
| 11. Testing | Thiết bị thật: chụp,cancel,rotation,reopen; review URI grant. |
| 12. Estimated effort | 20–40 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Camera activity/vendor; thay Manifest sát freeze. |
| 14. Fallback / Simplification | Cắt nguyên task và dùng picker, không làm nửa CameraX. |
| 15. Definition of Done | Camera matrix pass, picker còn hoạt động; chọn một camera route, không bắt cả intent và CameraX. |

1. D cấp FileProvider URI và quyền tạm đúng phạm vi.
2. Launch TakePicture với output URI; success nhập cùng pipeline A-002, cancel dọn file.
3. Xử lý không có camera app và security exception; không xin permission thừa.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

## Hàng đợi P2

<a id="back-002"></a>

## BACK-002 — CameraX capture và quyền camera

**Mục tiêu:** CameraX capture và quyền camera.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-002 |
| 2. Task name | CameraX capture và quyền camera |
| 3. Description | Tạo preview/capture in-app sau khi picker ổn định, không phải blocker hackathon. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M2 |
| 6. Dependencies | [A-002](../tracks/A.md#a-002) |
| 7. Input / Output contracts | MediaStore.import CT-01; SDK CameraX đã kiểm compatibility; root D. |
| 8. Deliverables | wardrobe camera screen, permission flow, capture pipeline. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Chụp 5 ảnh liên tiếp; deny không crash; ảnh lưu bền sau restart; không giữ camera khi background. |
| 11. Testing | Máy thật ít nhất 2 thiết bị nếu có; permission revoke/rotation/background. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Vendor camera và lifecycle; module mới. |
| 14. Fallback / Simplification | Giữ system camera intent hoặc picker nếu thiết bị không ổn. |
| 15. Definition of Done | Capture và permission matrix pass; không làm giảm độ bền A-004. |

1. Thêm dependency qua D, bind preview lifecycle và capture output.
2. Yêu cầu CAMERA đúng lúc; denial/permanent denial đưa về picker/settings rõ.
3. Capture off main, normalize orientation, giải phóng camera khi rời màn.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-009"></a>

## BACK-009 — Pilot beta nhỏ có kế hoạch đo

**Mục tiêu:** Pilot beta nhỏ có kế hoạch đo.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-009 |
| 2. Task name | Pilot beta nhỏ có kế hoạch đo |
| 3. Description | Tuyển nhóm thử nghiệm nhỏ, đo usability trước khi nhắm 15–30 người. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M6 |
| 6. Dependencies | [INT-004](../tracks/D.md#int-004), [BACK-011](../tracks/B.md#back-011) |
| 7. Input / Output contracts | APK, consent/privacy và BACK-011; mode VTO phải được kiểm chứng trước khi tuyển. |
| 8. Deliverables | Protocol 5 người mục tiêu, consent, install guide, event dictionary; số tuyển thực. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Biết ai đã consent ở kho riêng, số cài/số dùng thực, cách opt-out; không đưa PII vào repo. |
| 11. Testing | Dry run protocol nội bộ rồi pilot; kết quả thật mới đánh DONE phần triển khai pilot. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Không ai tham gia; API bill tăng; phân phối phức tạp. |
| 14. Fallback / Simplification | 1–2 buổi usability có giám sát, báo n thật; không claim retention. |
| 15. Definition of Done | Pilot đã diễn ra, có kết quả và giới hạn thực tế; thiếu tuyển người thì BLOCKED, không DONE hoặc gọi beta thành công theo số lời mời. |

1. Tuyển số người tự nguyện thực, ghi consent riêng tư; không gửi ảnh cá nhân vào repo.
2. Quan sát tạo tủ/profile/chọn outfit và optional try-on; ghi task success/lỗi thay wear metrics.
3. Bàn giao bản có version và cách xóa dữ liệu; không mặc định thu analytics nền.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="back-010"></a>

## BACK-010 — Phân tích kết quả và quyết định roadmap

**Mục tiêu:** Phân tích kết quả và quyết định roadmap.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-010 |
| 2. Task name | Phân tích kết quả và quyết định roadmap |
| 3. Description | Phân tích usability, chất lượng recommendation/VTO và privacy friction từ pilot; chỉ theo dõi dài hạn khi opt-in. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M6 |
| 6. Dependencies | [BACK-009](../tracks/A.md#back-009) |
| 7. Input / Output contracts | Pilot observations consent; source/mode thật, không seed usage. |
| 8. Deliverables | Báo cáo n/denominator/missing data/before-after nếu có; quyết định giữ/cắt giả thuyết. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Mọi chart truy được về dữ liệu ẩn danh; không gộp seed thành usage; không extrapolate thị trường từ n nhỏ. |
| 11. Testing | Review công thức và 2 record bằng tay; peer review B. |
| 12. Estimated effort | 60–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Missing observations; cherry-picking kết quả tốt. |
| 14. Fallback / Simplification | Báo cáo định tính với giới hạn, hoãn kết luận thống kê. |
| 15. Definition of Done | Có kết luận hành động và hạn chế, không bị deadline hackathon ép tạo dữ liệu. |

1. Báo task success, thời gian quan sát và quality complaints theo mode.
2. Nêu mẫu nhỏ/missing data/no-control; không suy ra fit accuracy hoặc causal impact.
3. Chọn cải tiến có bằng chứng, không tự xây hành vi tracking ngoài scope.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="later-003"></a>

## LATER-003 — Chia sẻ ảnh outfit có lựa chọn

**Mục tiêu:** Chia sẻ ảnh outfit có lựa chọn.

| Trường | Nội dung |
|---|---|
| 1. Task ID | LATER-003 |
| 2. Task name | Chia sẻ ảnh outfit có lựa chọn |
| 3. Description | Xuất collage từ ảnh local với Android share sheet theo thao tác user. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M6 |
| 6. Dependencies | [INT-004](../tracks/D.md#int-004) |
| 7. Input / Output contracts | Outfit visual/media refs; chỉ user-triggered share, try-on label phải giữ nếu share ảnh mô phỏng. |
| 8. Deliverables | Collage export/share URI và cleanup temp files. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Ảnh share mở được ở ứng dụng nhận; cancel không mất ảnh gốc; không tự gửi. |
| 11. Testing | Máy thật qua 2 ứng dụng nhận và URI access test. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Quyền media/ảnh người dùng; bitmap lớn. |
| 14. Fallback / Simplification | Export một ảnh nhỏ thay collage phức tạp. |
| 15. Definition of Done | User kiểm soát nội dung và thao tác gửi, không có upload nền. |

1. Render ảnh vừa kích thước, watermark có thể tắt; không chứa metadata riêng.
2. Share FileProvider URI chỉ đọc với lifetime hợp lý.
3. Handle cancel/missing image và dọn file tạm.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="later-004"></a>

## LATER-004 — Thử bản địa hóa một luồng và một thị trường

**Mục tiêu:** Thử bản địa hóa một luồng và một thị trường.

| Trường | Nội dung |
|---|---|
| 1. Task ID | LATER-004 |
| 2. Task name | Thử bản địa hóa một luồng và một thị trường |
| 3. Description | Kiểm chứng hướng mở rộng bằng luồng tiếng Anh và nghiên cứu thị trường hẹp. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M6 |
| 6. Dependencies | [BACK-010](../tracks/A.md#back-010) |
| 7. Input / Output contracts | Pilot insights, strings baseline và nguồn thị trường có thể xác minh lúc làm. |
| 8. Deliverables | Bản dịch luồng chính được review và memo thử VN→một thị trường kế tiếp. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Luồng không trộn ngôn ngữ ngoài ý muốn; luận điểm thị trường có nguồn và giới hạn. |
| 11. Testing | UI locale walkthrough + peer language review, không bịa user quốc tế. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Đa dạng văn hóa/khí hậu và dữ liệu yếu. |
| 14. Fallback / Simplification | Giữ tiếng Việt, chỉ research memo nếu không đủ reviewer. |
| 15. Definition of Done | Có quyết định thử tiếp hay hoãn, không claim global readiness. |

1. Tách strings cần dịch, thử date/unit/occasion; nhờ người đọc kiểm ngữ cảnh.
2. Phỏng vấn/desk research một phân khúc, ghi đối thủ và chi phí localization/support.
3. Đặt giả thuyết kênh acquisition/activation, không coi LLM dịch là đủ product-market fit.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="later-005"></a>

## LATER-005 — Khảo sát monetization có cost model

**Mục tiêu:** Khảo sát monetization có cost model.

| Trường | Nội dung |
|---|---|
| 1. Task ID | LATER-005 |
| 2. Task name | Khảo sát monetization có cost model |
| 3. Description | Đánh giá willingness-to-pay/freemium trước khi tích hợp affiliate hoặc commerce. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M6 |
| 6. Dependencies | [BACK-010](../tracks/A.md#back-010), [BACK-011](../tracks/B.md#back-011) |
| 7. Input / Output contracts | Chi phí request thực, pilot evidence; research consent. |
| 8. Deliverables | Bảng unit economics giả định và 3–5 phản hồi mục tiêu (số thực). |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Mỗi giả định có nguồn hoặc nhãn chưa kiểm; không revenue projection như thực tế. |
| 11. Testing | Review số học và sensitivity 2–3 mức usage. |
| 12. Estimated effort | 60–100 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Người dùng không sẵn trả tiền; sample thiên lệch. |
| 14. Fallback / Simplification | Giữ sản phẩm thử nghiệm và hard spend ceiling, chưa thương mại hóa. |
| 15. Definition of Done | Có go/no-go business experiment; không tạo dịch vụ trả phí hay giao dịch từ task nghiên cứu. |

1. Ước cost/user từ usage thật và khoảng lỗi/latency, nêu uncertainty.
2. Hỏi nhu cầu trả phí theo tác vụ, không dẫn dắt mua đồ ngoài tủ.
3. So freemium/paid cap; xác định xung đột affiliate với tận dụng đồ sẵn có.

**Điều kiện nhận việc:** Theo dependency và điều kiện cấp phép trong TEAM_WORKFLOW; không có deadline theo ngày.

<a id="a-201"></a>

## A-201 — Thiết kế sync boundary và export mapping

**Mục tiêu:** Thiết kế sync boundary và export mapping.

| Trường | Nội dung |
|---|---|
| 1. Task ID | A-201 |
| 2. Task name | Thiết kế sync boundary và export mapping |
| 3. Description | Chứng minh UUID/revision/tombstone và MediaRef mapping có thể xuất/nhập không đổi ID. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P2 / future / M6 |
| 6. Dependencies | [A-001](../tracks/A.md#a-001), [D-004](../tracks/D.md#d-004) |
| 7. Input / Output contracts | CT-01/04; local DB, media IDs; không cloud account/resource. |
| 8. Deliverables | Sync design note + export/import fixture test, conflict policy draft. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | IDs giữ qua roundtrip, deleted không resurrect, ảnh người không export mặc định. |
| 11. Testing | Unit fixture mapping và review privacy; no remote calls. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Clock skew và conflict policy chưa xác nhận. |
| 14. Fallback / Simplification | Chỉ design/tests, không thêm infrastructure trước nhu cầu. |
| 15. Definition of Done | Boundary có evidence, cloud implementation là công việc riêng chưa được cam kết. |

1. Map local refs sang remote opaque media IDs, không dùng absolute path làm identity.
2. Chốt conflict/revision/deletion semantics với D/C cho dữ liệu nhạy cảm.
3. Roundtrip fixture cùng ID; không triển khai auth/sync engine hay provider cloud.

**Điều kiện nhận việc:** Theo dependency; tuân thủ thể lệ và ngân sách được nhóm chấp thuận.
