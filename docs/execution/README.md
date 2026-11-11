# FitCheckAI — execution plan v2

Cập nhật tiếp ngày **11/11/2026**, Asia/Bangkok. Giữ task cards và thay đổi hợp lệ từ lần bị gián đoạn; đây là kế hoạch, chưa có bằng chứng triển khai/POC. Mọi task active còn TODO. Đường dẫn source trong card là deliverable tương lai.

## Lưu ý mã nguồn Sprint 0

Sau commit Sprint 0 `314e237`, repo đã có scaffold Android/Gradle/Hilt, nhưng Kotlin models/Room/proxy/NavHost vẫn theo interface v1. **CONTRACTS.md v2 là nguồn chuẩn**, cần chuyển implementation theo owner, không viết lại spec v2 cho khớp scaffold cũ. Xem [bảng mapping và thứ tự chuyển đổi](SPRINT0_TO_V2_ALIGNMENT.md). Tài liệu lập trước Sprint 0 nêu 'chưa có code' là ảnh chụp trạng thái lịch sử.

## Cách dùng

1. Đọc [TEAM_WORKFLOW](../../TEAM_WORKFLOW.md) để nắm ownership và phối hợp.
2. Nhận task từ [TASK_BOARD](TASK_BOARD.md), mở card [A](tracks/A.md), [B](tracks/B.md), [C](tracks/C.md), [D](tracks/D.md). [PREPARATION](PREPARATION.md) chỉ ra việc bắt đầu độc lập.
3. Chốt [CONTRACTS v2](CONTRACTS.md); dùng fake của từng owner để làm song song.
4. Kiểm [dependency/milestone](DEPENDENCIES_AND_INTEGRATION.md), [VTO gate](VTO_FEASIBILITY.md) và [capacity/runbook](RUNBOOK.md) trước tích hợp/chọn scope demo.
5. Đối chiếu [review/decision log](REVIEW_AND_DECISIONS.md) và [5 card archive](BACKLOG.md) khi gặp yêu cầu cũ.

## Scope và mức chứng minh

Android local-first: profile tối giản → ảnh garment trước/sau → AI recognition có sửa tay → Room wardrobe → context/profile/weather khi có → tối đa 5 outfit có ảnh và giải thích → chọn một outfit để Try-On. Ảnh người và cân nặng tùy chọn; không cần ảnh người để phối đồ. Mục tiêu hai góc VTO phải có evidence về tính nhất quán; ảnh AI không chứng minh fit/size chính xác.

| Mode | Bằng chứng cần | Giới hạn công bố |
|---|---|---|
| CORE | M2/M3, D-007 và M6; recognition/recommendation live, persistence thật | VTO unavailable; chưa đạt mục tiêu hai góc |
| FRONT | CORE + GO_FRONT_ONLY hoặc GO_DUAL + C-212 front runtime PASS | Chỉ mặt trước và garment coverage đã kiểm |
| DUAL | CORE + GO_DUAL + C-211/C-212 paired runtime PASS | Chỉ categories/coverage đã kiểm; mẫu nhỏ chưa chứng minh production reliability |

CORE vẫn là mốc sản phẩm lớn, **không mặc định vừa 5 giờ hackathon**. Demo thu hẹp hơn CORE là partial demo, không gọi M5-CORE. Không mode nào thay AI live bằng FAKE/RULES rồi đánh milestone AI PASS.

## Quy tắc cập nhật

- Board là nguồn trạng thái: TODO → DOING → REVIEW → DONE; BLOCKED/CUT có lý do. Checkbox chỉ tick khi DONE có evidence. Một DOING/người.
- Card là nguồn scope/dependency/acceptance/effort; prefix ID không quyết định owner. Board xếp priority rồi thứ tự topo trong cùng priority; task thiếu dependency vẫn chưa được nhận.
- **68 active: 46 P0, 14 P1, 8 P2; 5 archive.** P0 là core và feasibility sản phẩm; P1 gồm runtime VTO có gate và quality; P2 là future. Milestone trên card là mốc đóng góp, không khiến mọi P1/P2 mang nhãn M6 thành bắt buộc cho demo core.
- Effort là phút công owner gồm local tests, không phải elapsed time. [Board](TASK_BOARD.md) có tổng theo track; [Runbook](RUNBOOK.md) có support ledger/capacity. Không cộng trùng test owner.
- Feasibility report DONE không đồng nghĩa VTO PASS. NO_GO/BLOCKED có thể là kết luận đúng; implementation chưa đạt vẫn mở.

## Tài liệu gốc giữ nguyên

[Requirements](../../Requirements.MD) · [Scorings](../../Scorings.MD) · [Master Plan](../MASTER_PLAN.md) · [Sprint 0](../plans/00-overview-and-sprint0.md) · [A cũ](../plans/10-track-a-wardrobe.md) · [B cũ](../plans/20-track-b-ai.md) · [C cũ](../plans/30-track-c-stylist.md) · [D cũ](../plans/40-track-d-insights.md).

Yêu cầu sản phẩm mới quyết định scope thực thi; khác biệt được ghi trong decision log. Không sửa application code, commit, push, merge, PR, deploy hoặc tạo tài nguyên trong lượt tài liệu này.
