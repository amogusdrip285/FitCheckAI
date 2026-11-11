# Chuẩn bị và hàng đợi có thể bắt đầu

[Workflow](../../TEAM_WORKFLOW.md) · [Board](TASK_BOARD.md) · [Runbook](RUNBOOK.md)

Không gán task cho ngày cố định. PRE là ID lịch sử, không phải ngày làm. Các card đã chuyển về track owner để mỗi người có một backlog xuyên suốt. Đây chỉ là chỉ mục.

| Track | Việc bắt đầu độc lập | Khi bị blocker |
|---|---|---|
| A | [PRE-002](tracks/A.md#pre-002), [PRE-004](tracks/A.md#pre-004), [PRE-005](tracks/A.md#pre-005) | Sau contract: A-005 fake, A-003 UI; Room/AI lỗi không chặn thiết kế/test fixtures. |
| B | [PRE-001](tracks/B.md#pre-001), [PRE-003](tracks/B.md#pre-003), [PRE-008](tracks/B.md#pre-008) | B-005 fake, B-003/B-007 client với mock; không đợi model live để viết validator. |
| C | [C-201](tracks/C.md#c-201), [C-202](tracks/C.md#c-202) | C-206 fake/state UI, privacy review; thiếu key thì experiment BLOCKED, không fake PASS. |
| D | [PRE-006](tracks/D.md#pre-006) → [PRE-007](tracks/D.md#pre-007) | Wireflow/root contracts, sau shell làm D-006/Profile/Today bằng fake. |

## Bridge: scaffold v1 → contracts v2

Đã có Sprint 0 scaffold cũ trên repo. D ưu tiên PRE-007/S-002 để compile shared v2; A/B/C **không cần chờ toàn bộ migration** để làm POC/fakes/fixtures/UI riêng. Tránh mỗi track tạo định nghĩa `MediaRef`, `Outfit`, `TryOnRequest` bất đồng. Xem [alignment guide](SPRINT0_TO_V2_ALIGNMENT.md).

## Chuyển hướng PRE cũ

<a id="pre-001"></a>

- [PRE-001](tracks/B.md#pre-001) — owner B.

<a id="pre-002"></a>

- [PRE-002](tracks/A.md#pre-002) — owner A.

<a id="pre-003"></a>

- [PRE-003](tracks/B.md#pre-003) — owner B.

<a id="pre-004"></a>

- [PRE-004](tracks/A.md#pre-004) — owner A.

<a id="pre-005"></a>

- [PRE-005](tracks/A.md#pre-005) — owner A.

<a id="pre-006"></a>

- [PRE-006](tracks/D.md#pre-006) — owner D.

<a id="pre-007"></a>

- [PRE-007](tracks/D.md#pre-007) — owner D.

<a id="pre-008"></a>

- [PRE-008](tracks/B.md#pre-008) — owner B.

Chỉ nghiên cứu/kiến trúc/môi trường và phần được BTC cho phép được chuẩn bị trước. POC live là phép thử cần quyền API, consent, budget và xác nhận thể lệ; không mặc định 3 ngày chuẩn bị cho phép xây sẵn toàn bộ core.
