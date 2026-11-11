# FitCheckAI — cách cả nhóm làm việc

[Master Plan](docs/MASTER_PLAN.md) giữ định hướng gốc; file này quy định cách phối hợp; [execution](docs/execution/README.md) chứa công việc cụ thể. Mâu thuẫn với tài liệu cũ được ghi ở [decision log](docs/execution/REVIEW_AND_DECISIONS.md), không sửa ngầm tài liệu gốc.

## 1. Product vision

Giúp người dùng tận dụng quần áo đã sở hữu qua tủ đồ số, gợi ý phối đồ có giải thích và mô phỏng trên ảnh của chính họ. Android trước, dữ liệu local trước, ID ổn định để thêm cloud sync sau. Ảnh AI là mô phỏng, không chứng minh độ vừa vặn, số đo hoặc hình dáng thực tế.

## 2. Core user journey

Hồ sơ tối giản có thể bỏ qua → nhập ảnh trang phục trước/sau → nhận diện và sửa thuộc tính → lưu tủ đồ → nhập hoàn cảnh, sở thích, yêu cầu → xem tối đa 5 outfit trực quan từ đồ đang có → chọn một outfit → Try-On nếu capability đã kiểm chứng. Ảnh toàn thân và cân nặng tùy chọn; không chặn quản lý tủ đồ/phối đồ khi thiếu ảnh người. Mục tiêu Try-On cả mặt trước và mặt sau phải qua gate riêng.

## 3. Scope hiện tại

Core gồm Room wardrobe/CRUD, media local, Profile, recognition, recommendation 1–5 bộ hợp lệ, visual cards, lifecycle và máy thật. P0 VTO là feasibility/prototype, fake và privacy foundation; runtime đáng tin cậy là P1 có gate. Tách nền, camera, weather tự động, reroll/focus và rule fallback là P1; không mô tả đã có chỉ vì schema chứa field. Wear tracking, Insights và behavioral ranking đã park; cloud sync thuộc tương lai. Demo thu hẹp phải công bố các yêu cầu sản phẩm chưa hoàn thành.

## 4. Bốn track xuyên suốt

| Track | Sở hữu | Bàn giao chính |
|---|---|---|
| [A — Wardrobe & Imaging](docs/execution/tracks/A.md) | Room, repository, shared MediaStore, wardrobe UI, sửa tagging, OutfitVisual | Dữ liệu/ảnh và component; tích hợp local, QA/regression |
| [B — AI Recognition & Recommendation](docs/execution/tracks/B.md) | Recognition/recommendation/weather, proxy chung, validation, quota | Client/server AI và fixtures; tích hợp AI live |
| [C — Virtual Try-On](docs/execution/tracks/C.md) | Feasibility, job adapter/client, TryOn UI, chất lượng, privacy coordinator | Capability có evidence; runtime theo gate; hỗ trợ pitch |
| [D — App Foundation & Experience](docs/execution/tracks/D.md) | Root Android/contracts, Profile, Today, navigation, theme, lifecycle | Composition root, điều phối E2E, APK và demo |

Một task có một primary owner. Prefix ID cũ không xác định owner. A sở hữu bytes ảnh dùng chung, D sở hữu profile metadata/body-photo UI, C sở hữu job/result lifecycle; không có ba media stores cạnh tranh.

## 5. Collaboration rules

Nhận task theo priority và dependency đã đạt; tối đa một task DOING/người. Khi bị chặn, ghi blocker, người cần hỗ trợ và điều kiện mở lại ở board rồi chọn việc độc lập. Hỗ trợ/review không tự chuyển ownership; đổi owner phải ghi task, phạm vi file, trách nhiệm test và cập nhật chỉ mục. D điều phối, owner module sửa lỗi module.

## 5a. Đối chiếu scaffold Sprint 0

Sprint 0 (`314e237`) có Kotlin contracts/Hilt/modules theo track cũ. Đích là contracts v2 trong execution, **không đổi ngược spec về v1**. D sở hữu shared interfaces, Gradle và composition root; A/B/C sửa implementation thuộc track mình. C xây VTO **ảnh 2D có người thật**, không phải 3D; có thể dùng fake `MediaStore` trước khi A push. Chi tiết và checklist: [Sprint 0 → v2](docs/execution/SPRINT0_TO_V2_ALIGNMENT.md).

## 6. Contract-first development

[Contracts v2](docs/execution/CONTRACTS.md) là nguồn interface chung. PRE-007 chốt schema/fixtures; S-002 tạo types và điểm wiring. A-005, B-005, C-206, D-006 cung cấp fake theo owner, gồm success và failure. Consumer làm qua interface; implementation thật chỉ bắt buộc ở gate tích hợp tương ứng. Domain types không import feature implementation; composition root nối callbacks để tránh vòng phụ thuộc. Breaking change cần consumer review, version/migration và compatibility test.

## 7. Git, branch và merge conflict

Khi nhóm triển khai, dùng branch ngắn theo task (ví dụ `track-a/A-001-room`), thay đổi có scope rõ và review trước tích hợp. D giữ Gradle/version catalog, Manifest/backup rules, contracts, NavHost và root resources; B giữ proxy entry/config/auth. Người khác gửi patch đề xuất cho owner hotspot. Khi conflict, hai owner thống nhất semantics trước, không chọn toàn bộ “ours/theirs” để vượt lỗi. Giữ bản tích hợp build được và artifact đã kiểm để rollback. Đây là quy trình tương lai; lượt cập nhật tài liệu không thực hiện thao tác Git ghi dữ liệu hoặc deploy.

## 8. Priority và milestone

P0 thiết yếu cho core và feasibility; P1 nâng chất lượng/runtime sau gate; P2 phát triển sau. Priority không phải deadline ngày. M0 contracts; M1 fake độc lập; M2 wardrobe/profile; M3 AI thật; M4 VTO kiểm chứng; M5 E2E theo mode; M6 máy thật/demo. [Gate chi tiết](docs/execution/DEPENDENCIES_AND_INTEGRATION.md) phân biệt M4-F feasibility, M4-R runtime và M5-CORE/FRONT/DUAL. Milestone có thể tiến song song, số thứ tự không bắt buộc tất cả chờ nhau.

## 9. Integration responsibilities

A dẫn INT-001 local integration và INT-003 regression; B dẫn INT-002 AI; C dẫn C-212 VTO; D dẫn D-007 root/E2E, INT-004 artifact và D-003 bàn giao. A xác nhận APK trên thiết bị, B xác nhận AI của đúng artifact, C xác nhận claim VTO. D không nhận trách nhiệm sửa toàn bộ code. Bàn giao cần task ID, contract revision, file chạm, cách chạy, test và giới hạn.

## 10. Definition of Done

Deliverable đúng scope/contract, acceptance và test liên quan đạt với evidence; consumer review boundary; bản tích hợp không regression; board có record. Không đánh DONE chỉ vì hết giờ. Báo cáo feasibility có thể DONE với NO_GO, nhưng tính năng chưa PASS. Phạm vi bị cắt vẫn CUT/TODO/BLOCKED và được công bố, không hạ acceptance để chuyển DONE. Mọi claim FAKE/RULES/AI_LIVE phải đúng nguồn.

## 11. Kiểm thử

Owner kiểm invariants/failure module; fake để phát triển và fault injection, API thật để chứng minh AI, máy thật cho URI/lifecycle/persistence và artifact. Không dùng unit tests thay evidence máy thật. Ghi build/hash, thiết bị/API, case, expected/actual, PASS/FAIL/NOT RUN và bug owner. Không lưu dữ liệu nhạy cảm vào evidence công khai. Tích hợp sau mỗi boundary đủ dùng, không đợi hết backlog.

## 12. AI security, privacy và API cost

Provider key chỉ ở server. Auth/ownership checks, validation, payload limits, budget cap và kill switch trước upload live. Không mặc định có account/credit hoặc tài nguyên miễn phí. Consent theo mục đích/provider/request; chỉ gửi ảnh cần cho outfit đã chọn. Person photos/results ở private storage, loại khỏi backup; xóa/rút consent phải invalidate job và chặn late result. Nêu riêng trạng thái xóa phía provider, không hứa điều API không bảo đảm. Không ghi ảnh/base64/secret/profile vào logs, không sinh VTO cho cả 5 outfit tự động.

## 13. Hackathon constraints

Sự kiện dự kiến 14/11/2026; B qua PRE-001 xác nhận thông báo cuối, quyền POC trước sự kiện và disclosure. Core mobile/AI cần phần phát triển mới đáng kể trong cửa sổ BTC cho phép; không coi chỉ nối API vào sản phẩm dựng sẵn là đủ. Chuẩn bị môi trường, kiến trúc, nghiên cứu và phần được phép; công khai code/templates/spikes có trước. Không giả AI live hoặc số liệu. [Runbook](docs/execution/RUNBOOK.md) quản lý capacity, không giao task theo ngày; toàn P0 vượt khả năng một phiên 5 giờ.

## 14. Nguồn quản lý công việc

[Board](docs/execution/TASK_BOARD.md) là nơi duy nhất ghi trạng thái. Card trong từng track là nguồn scope/dependency/effort; [chuẩn bị](docs/execution/PREPARATION.md) là chỉ mục bắt đầu; [archive](docs/execution/BACKLOG.md) giữ lịch sử; [VTO gate](docs/execution/VTO_FEASIBILITY.md) quy định evidence. Đổi card phải đồng bộ board, graph, totals và decisions khi đổi scope. Không sao chép toàn bộ task sang workflow.
