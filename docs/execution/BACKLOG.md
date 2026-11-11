# Backlog ngoài scope active và lịch sử task

[Trang chủ](README.md) · [Board](TASK_BOARD.md) · [Change summary](REVIEW_AND_DECISIONS.md)

Task P1/P2 còn có giá trị sản phẩm nằm trong backlog riêng của từng [A](tracks/A.md), [B](tracks/B.md), [C](tracks/C.md), [D](tracks/D.md). File này giữ 5 card cũ đã park/retire, không nằm trong tổng active, không dùng để giao coding agent hiện tại. Nội dung archive là lịch sử, không phải contracts đang áp dụng; muốn mở lại phải re-scope và review dependency.

**BACK-001: Gộp / retire — Room được làm ngay trong A-001; không có code JSON hoặc dữ liệu legacy để cần migration JSON→Room. ID không tái sử dụng.**

<a id="back-001"></a>

## BACK-001 — Chuyển metadata sang Room có migration

**Mục tiêu:** Chuyển metadata sang Room có migration.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-001 |
| 2. Task name | Chuyển metadata sang Room có migration |
| 3. Description | Thay persistence backend sau sự kiện khi cần query/edit/log, giữ interface hiện hữu. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P1 / ARCHIVED / Không áp dụng |
| 6. Dependencies | [A-004](tracks/A.md#a-004) |
| 7. Input / Output contracts | CT-01 schemaVersion 1; JSON/ảnh thật; Room version kiểm chứng lúc làm. |
| 8. Deliverables | wardrobe Room entity/DAO/repository, schema export và importer JSON một lần. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Tủ cũ đủ ID/ảnh sau nâng cấp; import hai lần không duplicate; lỗi import không mất JSON gốc. |
| 11. Testing | Instrumented Room migration/transaction + device upgrade JSON→Room. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Codegen mismatch; mất dữ liệu lúc nâng cấp. |
| 14. Fallback / Simplification | Giữ JSON backend đến khi migration pass; không đổi UI cùng lúc. |
| 15. Definition of Done | A chứng minh nâng cấp có rollback, C không cần đổi contract đọc tủ. |

1. Thiết kế schema item giữ ID/path; thêm Room qua root owner.
2. Import JSON trong transaction, marker chỉ ghi sau thành công; giữ backup rollback.
3. Test restart và migration lặp không nhân đôi; không destructive migration production.

**Điều kiện nhận việc:** Không nhận việc; cần quyết định mở lại riêng.

**BACK-006: Park ngoài active board — Wear log và tracking không còn ưu tiên; không tạo bảng/method trong contract active.**

<a id="back-006"></a>

## BACK-006 — Wear log atomic và feedback persistence

**Mục tiêu:** Wear log atomic và feedback persistence.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-006 |
| 2. Task name | Wear log atomic và feedback persistence |
| 3. Description | Thiết kế một operation ghi mặc và cập nhật đếm đúng một lần. |
| 4. Track / Owner | A |
| 5. Priority / Scope / Milestone | P1 / ARCHIVED / Không áp dụng |
| 6. Dependencies | [BACK-001](BACKLOG.md#back-001) |
| 7. Input / Output contracts | Room schema; Outfit snapshot; D/C consumer; feedback contract mới. |
| 8. Deliverables | WearLog/Feedback entities, recordWear(idempotencyKey,snapshot), query API. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Hai lần cùng event chỉ tăng một lần; lỗi transaction không update một nửa; lịch sử vẫn giải thích được khi item bị xóa. |
| 11. Testing | Room transaction/duplicate ID/missing item tests và force-stop persistence. |
| 12. Estimated effort | 75–120 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | A/D cùng sửa schema; wear count double update. |
| 14. Fallback / Simplification | Chỉ log sự kiện, tính count từ log nếu đơn giản hơn; một owner schema. |
| 15. Definition of Done | API atomic được C/D review và không gọi markWorn riêng từ UI. |

1. Chốt snapshot item IDs/occasion/source/time để lịch sử không phụ thuộc outfit memory.
2. Trong transaction insert log và increment counts; unique event ID chống double tap.
3. Cho feedback một giá trị/record; define delete-item history policy.

**Điều kiện nhận việc:** Không nhận việc; cần quyết định mở lại riêng.

**BACK-007: Park ngoài active board — Insights và tần suất mặc bị đưa khỏi active scope.**

<a id="back-007"></a>

## BACK-007 — Mặc bộ này và Insights cơ bản

**Mục tiêu:** Mặc bộ này và Insights cơ bản.

| Trường | Nội dung |
|---|---|
| 1. Task ID | BACK-007 |
| 2. Task name | Mặc bộ này và Insights cơ bản |
| 3. Description | Hiển thị số lần mặc và danh sách ít dùng từ dữ liệu thật, không tạo thống kê seed giả. |
| 4. Track / Owner | D |
| 5. Priority / Scope / Milestone | P1 / ARCHIVED / Không áp dụng |
| 6. Dependencies | [BACK-006](BACKLOG.md#back-006), [C-003](tracks/D.md#c-003) |
| 7. Input / Output contracts | WearLog API, current outfit snapshot; C sở hữu Today button patch. |
| 8. Deliverables | Insights UI, query mapping; phối hợp C thêm CTA recordWear. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Mặc một lần làm counts khớp DB; double tap không tăng đôi; tủ rỗng không chia 0. |
| 11. Testing | Unit aggregates, integration button→DB→Insights; device reopen. |
| 12. Estimated effort | 90–150 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Counts sai do seed/log thiếu; UI scope tăng. |
| 14. Fallback / Simplification | Chỉ bảng count + last worn, hoãn biểu đồ. |
| 15. Definition of Done | Dữ liệu truy ngược về log, không claim thay đổi hành vi từ ngày đầu. |

1. C nối button idempotent; D tạo màn count/items chưa mặc với empty state.
2. Định nghĩa mẫu số % tủ và khoảng 30 ngày rõ; dữ liệu chưa đủ thì nói chưa đủ.
3. Không thêm focus-item route trước contract tương ứng.

**Điều kiện nhận việc:** Không nhận việc; cần quyết định mở lại riêng.

**LATER-001: Park ngoài active board — Learning từ hành vi/feedback dài hạn chưa ưu tiên.**

<a id="later-001"></a>

## LATER-001 — Thử nghiệm ranking từ feedback

**Mục tiêu:** Thử nghiệm ranking từ feedback.

| Trường | Nội dung |
|---|---|
| 1. Task ID | LATER-001 |
| 2. Task name | Thử nghiệm ranking từ feedback |
| 3. Description | Thử một rule reranker dựa trên feedback thật, chưa huấn luyện model lớn. |
| 4. Track / Owner | B |
| 5. Priority / Scope / Milestone | P2 / ARCHIVED / Không áp dụng |
| 6. Dependencies | [BACK-008](tracks/D.md#back-008), [BACK-010](tracks/A.md#back-010) |
| 7. Input / Output contracts | Dataset feedback consent; baseline recommendations và metric pilot. |
| 8. Deliverables | Offline experiment và quyết định có đưa vào app hay không. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Reproducible comparison, không học từ seed hay tự chấm thay user. |
| 11. Testing | Offline eval và unit invariant; chưa A/B live nếu thiếu người. |
| 12. Estimated effort | 120–240 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Feedback quá ít; overfit. |
| 14. Fallback / Simplification | Giữ explicit style preference, hoãn học tự động. |
| 15. Definition of Done | Có report go/no-go và không claim personal model nếu chưa kiểm chứng. |

1. Chốt metric preference và split theo người tránh leakage.
2. So rule reranker với baseline cùng bộ case; kiểm ID/missing slots vẫn giữ.
3. Báo sample size, cold-start và failure; chỉ ship nếu evidence đủ.

**Điều kiện nhận việc:** Không nhận việc; cần quyết định mở lại riêng.

**LATER-002: Park ngoài active board — Weekly planning chưa phục vụ luồng chính; không tạo dependency wear log.**

<a id="later-002"></a>

## LATER-002 — Prototype lịch outfit một tuần

**Mục tiêu:** Prototype lịch outfit một tuần.

| Trường | Nội dung |
|---|---|
| 1. Task ID | LATER-002 |
| 2. Task name | Prototype lịch outfit một tuần |
| 3. Description | Tạo lịch local để gán outfit đã có vào ngày, không sinh AI 7 lần mặc định. |
| 4. Track / Owner | C |
| 5. Priority / Scope / Milestone | P2 / ARCHIVED / Không áp dụng |
| 6. Dependencies | [BACK-006](BACKLOG.md#back-006) |
| 7. Input / Output contracts | Outfit snapshot/log storage; scope UX được D review. |
| 8. Deliverables | Week plan UI và local storage; contract planned khác worn. |
| 9. Implementation checklist | Các bước ngay dưới bảng. |
| 10. Acceptance criteria | Plan không thay wearCount, 7 ngày giữ sau restart, missing item có cảnh báo. |
| 11. Testing | Unit date/timezone và persistence; device edit/reopen. |
| 12. Estimated effort | 120–180 phút công owner; gồm test cục bộ. |
| 13. Risks / Blockers | Calendar/timezone và tăng chi phí AI. |
| 14. Fallback / Simplification | Chỉ 3 ngày local, không auto-generate. |
| 15. Definition of Done | Lịch ổn định và không làm sai Insights. |

1. Chọn ngày và outfit có sẵn; save/remove assignment.
2. Tách planned và actual wear để không tăng counts khi lên lịch.
3. Reopen giữ lịch, cho sửa khi item bị xóa.

**Điều kiện nhận việc:** Không nhận việc; cần quyết định mở lại riêng.
