# Review, thay đổi scope và decision log

[Workflow](../../TEAM_WORKFLOW.md) · [Board](TASK_BOARD.md) · [Contracts](CONTRACTS.md) · [Runbook](RUNBOOK.md)

## Quyết định bổ sung 11/11/2026 — tích hợp Sprint 0

Sau khi docs v2 được viết, commit Sprint 0 `314e237` bổ sung scaffold theo thiết kế trước đó. Nhận xét 'chưa có source' phía dưới chỉ đúng tại local HEAD `26049f5` khi viết bản trước. Chốt **giữ hợp đồng v2**, di chuyển code theo ownership D(shared), A(media/Room), B(AI/proxy), C(VTO), không giả định chỉ ảnh hưởng D. Track C chọn đầu ra photorealistic 2D có người thật; hai góc nhìn vẫn phải qua VTO gate. Xem [alignment guide](SPRINT0_TO_V2_ALIGNMENT.md).

## Bằng chứng và phạm vi lượt tiếp tục

Ngày 11/11/2026 đọc lại 12 file execution hiện có; giữ các phần đã cập nhật, hoàn thiện tài liệu còn dùng baseline cũ. Local HEAD vẫn `26049f5`. Repository ngoài execution chỉ có 8 Markdown định hướng được Git theo dõi; chưa có application source/Gradle project/tests/APK trong checkout này. Không suy diễn remote hoặc gọi API có phí. Mọi task active còn TODO; cập nhật tài liệu không phải hoàn thành feature.

| Tài liệu gốc được giữ nguyên | Điểm cần đối chiếu theo hướng mới |
|---|---|
| [Requirements](../../Requirements.MD) | Giữ nhu cầu tận dụng tủ đồ; yêu cầu mới làm rõ profile, nhiều outfit và VTO |
| [Scorings](../../Scorings.MD) | 40/35/25 là rubric nội bộ, chưa mặc định chính thức từ BTC |
| [Master Plan](../MASTER_PLAN.md) | Tầm nhìn rộng; execution tách core, feasibility, runtime có gate và future |
| [Sprint 0](../plans/00-overview-and-sprint0.md) | Giảm bộ khung nhiều module; một :app/interfaces/manual DI, review lại khi có nhu cầu thật |
| [A cũ](../plans/10-track-a-wardrobe.md) | Room/CRUD ngay core; shared media front/back, không cần migration JSON không tồn tại |
| [B cũ](../plans/20-track-b-ai.md) | Recognition và recommendation đều P0, structured output/validation, 1–5 bộ |
| [C cũ](../plans/30-track-c-stylist.md) | Stylist UI sang D; fallback/weather/reroll sang B; C tập trung VTO |
| [D cũ](../plans/40-track-d-insights.md) | Profile/Today/root; wear/Insights park; evidence/QA/pitch hỗ trợ bởi A/C |

Nghiên cứu sự kiện từ lượt audit trước: [trang Luma](https://luma.com/l0ro71hq), ngày sự kiện 14/11/2026, build dự kiến 11–16h; core mobile/AI xây trong sự kiện, template công khai và sản phẩm có trước cần disclosure theo luật áp dụng. Đây là thông tin đã tra trước, **không phải xác minh mới hoặc thông báo cuối của BTC**. PRE-001 cần xác nhận quyền thử POC, lịch/đăng ký/submission và rubric cuối. Không mặc định credit có sẵn. Không nghiên cứu lại provider trong lượt tiếp tục; nguồn nền VTO ở [gate](VTO_FEASIBILITY.md).

## Quyết định thay thế baseline cũ

| Baseline cũ | Thực thi v2 / tác động |
|---|---|
| Hai màn, một outfit, manual tags; vision/profile để sau | Profile → Wardrobe → Today → TryOn route; recognition P0, tối đa 5 outfit; fake không chứng minh live |
| JSON repository rồi BACK-001 migrate Room | Room ngay A-001; stable UUID/revision, media files riêng; BACK-001 retire vì không có dữ liệu legacy |
| C Stylist/Weather, D Insights/evidence | A wardrobe/media, B recognition/reco/weather, C VTO, D foundation/profile/Today; prefix ID giữ lịch sử |
| D tích hợp mọi thứ và sửa lỗi toàn app | A INT-001/003 + visual, B INT-002, C TryOn UI/C-212 + pitch; D D-007/root/artifact |
| Wear count/log, Insights, behavioral personalization trong roadmap gần | Park BACK-006/007 và LATER-001/002, không có dependency active vào chúng |
| Deadline task theo ngày và giờ; upper path 215/245 phút | Pull queue theo priority/dependency; state milestones; bỏ dự báo cũ không còn phù hợp scope |
| Tạo được ảnh là VTO xong | P0 feasibility có rubric, P1 runtime theo capability; front-only khác dual, partial khác success |
| Mọi món/nhiều mẫu mặc nhiên là nhiều góc | Chỉ outfit chọn; explicit garment coverage, input back thật, paired consistency phải đo |
| Focus từ Insights, weather fixed city, rule fallback bắt buộc | Focus từ Wardrobe/Today P1; weather city chọn có timestamp P1; lỗi typed core, RULES chỉ khi triển khai |
| Hồ sơ đầy đủ/ảnh cơ thể mặc định | Hồ sơ tối giản, skip được; weight/photos optional; consent upload tách profile save |

Hybrid tiếp tục là cách làm đã chọn: ownership xuyên suốt + contracts/fakes + tích hợp theo seam. Không chọn phase toàn nhóm vì tạo chờ; không chỉ chia module rồi đợi cuối mới ghép vì rủi ro interfaces. Không lặp lại brainstorming, không đổi stack chỉ vì sở thích. Android Kotlin/Compose, Room, private files và proxy là hướng triển khai; toolchain/provider/account vẫn cần kiểm tra thực tế.

## Đối chiếu đủ 50 ID cũ

45 ID tiếp tục active với scope/priority/owner cập nhật; 5 ID archive không dùng lại. Bảng chỉ ghi disposition, không là bản sao trạng thái. Acceptance/effort chính thức nằm ở cards.

| ID cũ | Owner hiện tại | Xử lý |
|---|---|---|
| [PRE-001](tracks/B.md#pre-001) | B | Giữ readiness; B quản lý thể lệ/disclosure, không gán deadline ngày. |
| [PRE-002](tracks/A.md#pre-002) | A | Giữ toolchain/device; không tự coi có project. |
| [PRE-003](tracks/B.md#pre-003) | B | Giữ account/budget; không khóa model chưa kiểm chứng. |
| [PRE-004](tracks/A.md#pre-004) | A | Mở rộng 6 items/3 cặp front-back và quyền ảnh. |
| [PRE-005](tracks/A.md#pre-005) | A | A nhận user research; P1, báo số thực. |
| [PRE-006](tracks/D.md#pre-006) | D | D nhận wireflow bốn routes theo journey mới. |
| [PRE-007](tracks/D.md#pre-007) | D | D chốt contracts v2 và fixtures với consumer. |
| [PRE-008](tracks/B.md#pre-008) | B | B giữ readiness kỹ năng/roles; không giả định tên người. |
| [S-001](tracks/D.md#s-001) | D | Giữ shell tối thiểu, bốn route placeholders, máy thật. |
| [S-002](tracks/D.md#s-002) | D | Types/wiring v2; fake do mỗi track làm. |
| [A-001](tracks/A.md#a-001) | A | Đổi JSON persistence thành Room core, UUID/revisions. |
| [A-002](tracks/A.md#a-002) | A | Shared MediaStore/normalize front-back, private media. |
| [A-003](tracks/A.md#a-003) | A | Wardrobe form/list độc lập qua fake, manual correction. |
| [A-004](tracks/A.md#a-004) | A | Giữ device persistence/recovery, thêm CRUD/cleanup. |
| [A-101](tracks/A.md#a-101) | A | Camera hệ thống P1; MediaStore dùng chung. |
| [B-001](tracks/B.md#b-001) | B | Shared proxy/auth/cost cho recognition/reco và seam VTO. |
| [B-002](tracks/B.md#b-002) | B | Recommendation 1–5 bộ, profile/context, snapshot validation. |
| [B-003](tracks/B.md#b-003) | B | OutfitRecommendation Android client theo v2. |
| [B-004](tracks/B.md#b-004) | B | Live eval nhiều bộ/ID/shortfall, không chỉ demo một bộ. |
| [C-001](tracks/D.md#c-001) | D | Chuyển D: Today inputs/list trực quan/selection. |
| [C-002](tracks/B.md#c-002) | B | Chuyển B: RULES fallback P1; không chặn P0 UI. |
| [C-003](tracks/D.md#c-003) | D | Chuyển D: context và request lifecycle/stale guard. |
| [C-004](tracks/D.md#c-004) | D | Chuyển D: offline/lifecycle, typed error nếu chưa có RULES. |
| [C-101](tracks/B.md#c-101) | B | Chuyển B: weather city chọn + timestamp, P1. |
| [C-102](tracks/B.md#c-102) | B | Chuyển B: reroll/exclusion P1, không bịa tổ hợp. |
| [D-001](tracks/A.md#d-001) | A | Chuyển A: QA matrix/evidence và regression support. |
| [D-002](tracks/C.md#d-002) | C | Chuyển C: pitch có evidence/giới hạn, P1. |
| [D-003](tracks/D.md#d-003) | D | D giữ diễn tập/nộp bài, mode và disclosure đúng artifact. |
| [INT-001](tracks/A.md#int-001) | A | Chuyển A: Room/media/Profile integration. |
| [INT-002](tracks/B.md#int-002) | B | Chuyển B: recognition + recommendation live integration. |
| [INT-003](tracks/A.md#int-003) | A | Chuyển A: regression/freeze; module owner sửa lỗi. |
| [INT-004](tracks/D.md#int-004) | D | D giữ artifact; A xác nhận device, B xác nhận AI. |
| [BACK-001](BACKLOG.md#back-001) | A (lịch sử) | Retire/gộp vào A-001; không cần migration JSON chưa tồn tại. |
| [BACK-002](tracks/A.md#back-002) | A | CameraX P2, không chặn picker/camera hệ thống. |
| [BACK-003](tracks/A.md#back-003) | A | Segmentation P1; model readiness và derivative không mất original. |
| [BACK-004](tracks/B.md#back-004) | B | Nâng P0: recognition front/back /v2/recognition. |
| [BACK-005](tracks/A.md#back-005) | A | Nâng P0: A tag correction UI; eval model tách B-006. |
| [BACK-006](BACKLOG.md#back-006) | A (lịch sử) | Park wear log/count; ngoài scope ưu tiên mới. |
| [BACK-007](BACKLOG.md#back-007) | D (lịch sử) | Park Insights; không dependency active. |
| [BACK-008](tracks/D.md#back-008) | D | Nâng P0: D onboarding/profile tối giản, skip được. |
| [BACK-009](tracks/A.md#back-009) | A | A pilot P2 sau hardening; không coi tuyển người là đã pilot. |
| [BACK-010](tracks/A.md#back-010) | A | A phân tích pilot P2, không khôi phục behavioral analytics core. |
| [BACK-011](tracks/B.md#back-011) | B | B hardening P1 trước public beta; không thay auth/caps core. |
| [BACK-012](tracks/A.md#back-012) | A | Nâng P0: A CRUD/filter và invalidation. |
| [LATER-001](BACKLOG.md#later-001) | B (lịch sử) | Park behavioral ranking; không thu tracking nền. |
| [LATER-002](BACKLOG.md#later-002) | C (lịch sử) | Park weekly planning; không thuộc VTO core. |
| [LATER-003](tracks/A.md#later-003) | A | A share P2, cần consent riêng cho ảnh người/kết quả. |
| [LATER-004](tracks/A.md#later-004) | A | A localization P2 theo evidence thị trường. |
| [LATER-005](tracks/A.md#later-005) | A | A monetization research P2, không tự triển khai thanh toán. |
| [LATER-006](tracks/B.md#later-006) | B | B focus-item P1 từ Wardrobe/Today, không dependency Insights. |

23 task mới: **A-005/A-006/A-201** (fake, visual, sync boundary); **B-005/B-006/B-007** (fake, recognition eval/client); **C-201…213** (VTO feasibility→privacy/runtime→advanced); **D-004…007** (profile storage/photo/fake/E2E). Tổng 45 giữ active + 23 mới = **68 active**, cộng 5 archive = **73 card**. PREPARATION chỉ giữ redirect anchors, không nhân đôi task card. Không đổi ID khi chuyển owner.

## Cập nhật implementation Track A độc lập — 11/11/2026

Người dùng xác nhận chưa có máy Android thật hoặc code B/D riêng và yêu cầu làm phần độc lập trước,
cập nhật documentation, commit/push lên repo. Đây không phải xác nhận nghiệm thu của owner D/B/C.
Đã fast-forward nhánh A tới `origin/main` `7d995a5`, đọc alignment mới rồi giữ Gradle multi-module/Hilt hiện hữu.
Không dựng skeleton/manual DI khác hoặc thay version toolchain đã chốt.

- Shared v2 candidate đặt một nơi tại `core:model/.../wardrobe`; API/media ownership cần D/B/C review trước S-002/PRE-007 DONE.
- V1 giữ ở compatibility bridge chỉ đọc, không có kho wardrobe thứ hai được ghi đồng thời. Profile/Today v2 còn thuộc D.
- Checkout thực tế có 30 item v1 trên emulator (wearCount tổng 9), nên migration Room 1→2 có ý nghĩa và đã kiểm bảo toàn dữ liệu.
  Nhận định “chưa có dữ liệu” trong audit planning cũ không được dùng để reset database này.
- PRE-004 đã có bộ 21 món/24 ảnh thật, 3 cặp front/back và manifest quyền; nhãn do trợ lý rà soát, chưa có B adjudication.
  Bulk CC0 có 5.096 ảnh có nhãn sau lọc; Polyvore chưa nhập vì gate truy cập và điều khoản cần làm rõ.
- Chọn CameraX đang có làm camera route duy nhất; không làm thêm camera intent A-101. Photo Picker vẫn là đường nhập ảnh chính.
- `UnavailableRecognition` là binding mặc định v2; fake chỉ phục vụ tests. Không gọi fake là AI live hoặc tự gửi ảnh để “đủ demo”.
- Share/sync chỉ tới local API/metadata prototype; chưa bật live cloud, thanh toán, beta, VTO hay luồng share từ Today.

Nguồn trạng thái hiện tại: [board](TASK_BOARD.md), [hướng dẫn A v2](../track-a-v2.md),
[báo cáo QA](../qa/track-a-acceptance.md), [dataset](../../data/README.md).
Phần audit tài liệu bên dưới giữ như lịch sử của lần revise kế hoạch; câu “tất cả TODO/không code” không mô tả bản A hiện tại.

## Decision log kế hoạch (giữ lịch sử, đọc cùng cập nhật trên)

Tiếp tục Track A sau merge `114d3cf`: ưu tiên recovery file/cleanup, preview cancellation và Room native tests không cần B/D.
Không đổi schema hoặc shared contract. Dataset làm rõ per-field provenance, reference chưa adjudicate không còn nằm trong `groundTruthFields`.
[Evidence bổ sung](../qa/track-a-independent-followup.md) ghi riêng model ML Kit UNAVAILABLE trên AVD và gate chất lượng chưa chạy.
Người dùng yêu cầu commit mới bằng `NQKhaixyz` và push main; GitHub authentication đã xác minh đúng account và quyền push.
Người dùng giới hạn phần ảnh bulk đưa lên GitHub còn khoảng 100 ảnh: commit đúng 100 ảnh CC0 cùng nhãn nguồn/giấy phép
ở `data/samples/modern-clothing-100`; giữ nguyên 24 ảnh fixtures đã có. Bulk đầy đủ và ZIP tiếp tục gitignore.
Mẫu 100 ảnh chỉ lấy train, không trùng fixture và không dùng làm holdout đo AI.

Chốt theo dependency/điểm sử dụng, không hạn ngày. “Theo yêu cầu mới” là quyết định sản phẩm đã có; “đề xuất” hoặc “chưa xác nhận” không được giả thành đã triển khai.

| ID | Quyết định / trạng thái | Owner và điểm cần chốt / evidence |
|---|---|---|
| DEC-01 | Hybrid và bốn track mới; scope theo hướng mới | Theo yêu cầu người dùng; D điều phối, board/cards thể hiện ownership |
| DEC-02 | Android Kotlin/Compose, một :app/manual DI, Room wardrobe; typed atomic profile file là thiết kế đề xuất | D+A, PRE-002/007/008 và S-002: kỹ năng/toolchain/interfaces; không dùng Room và JSON wardrobe song song |
| DEC-03 | Recognition/reco provider/model/HTTPS host chưa được account xác nhận | B PRE-003/B-001: quyền dùng/model/config/region/quota trước live |
| DEC-04 | Ngân sách recognition/reco/VTO và state store chưa được duyệt | B+C trước bất kỳ phép gọi có phí: số cap cụ thể, attempts/kill switch; không mua/tạo tài nguyên trong audit |
| DEC-05 | Lịch, rubric, đăng ký/submission và quyền POC cần thông báo BTC cuối | B PRE-001: record nguồn/ngày; 40/35/25 chưa coi là chính thức |
| DEC-06 | Thiết bị thật và năng lực nhóm chưa đo | A PRE-002, B PRE-008: model/API/USB/storage/network và nhận vai, cập nhật effort theo thực tế |
| DEC-07 | Dataset garment/person/research chưa được thu thập/xác nhận quyền | A PRE-004/005, C C-202: nguồn/consent/private manifest, không bịa evidence |
| DEC-08 | Core mobile/AI có phần xây mới đáng kể trong sự kiện, disclosure phần tồn tại trước | B PRE-001 + D D-003: snapshot trước/sau và luật được xác nhận; không lách bằng “prep” |
| DEC-09 | VTO dual là mục tiêu, chưa có GO; front-only phải báo tác động | C C-201…205: measured rubric, A review, B budget, D mode; runtime riêng C-212 |
| DEC-10 | Local-first, ID/revision ổn định; cloud sync chưa làm, ảnh nhạy cảm private/excluded backup | Theo hướng mới; A/D/C xác nhận CT-01/04/06 và cleanup ở INT-001/D-007/C-212 |
| DEC-11 | Full P0 vượt capacity 5 giờ; mặc định không cam kết runtime/P1/P2 trong sự kiện | D cùng A/B/C chốt partial demo hoặc mode thực theo remaining effort/luật, công bố P0 còn thiếu |
| DEC-12 | Wire caps/timeouts/rubric VTO là defaults đề xuất, không provider guarantees | PRE-007 và C-205 version trước thử; budget/retention còn mở thì chặn live, không chặn fake |

## Rủi ro còn mở và tải D

- **Capacity:** 34h55–58h15 P0 owner, chưa support; ngay lower bound đã vượt 20h-người danh nghĩa. Chưa thể cam kết full core/dual trong 5 giờ. Runbook có partial demo, giữ acceptance thiếu ở trạng thái mở.
- **Provider/account/privacy/cost:** chưa xác nhận thực; retry, two views và unknown submission có thể tăng phí. Gate phải có budget cụ thể, không dựa tên model trong docs.
- **VTO fidelity:** ảnh đẹp có thể sai người/đồ/mặt lưng; cần 3 case/2 reviewers và runtime quality riêng, không claim fit accuracy.
- **D/root:** Profile/Today/root vẫn có tải 605–960 phút P0; A nhận component/QA/local integration, B AI integration, C whole TryOn UI. Không dùng D làm người sửa mọi bug; shared hotspot review có support ledger.
- **Dữ liệu và lifecycle:** orphan files, stale outfit/revision, consent revoke/late output cần checks thật; local-first không có nghĩa ảnh không rời máy khi gọi AI.

Có thể bắt đầu độc lập ngay qua PRE-002/004 (A), PRE-001/003/008 (B), C-201/202 (C), PRE-006 (D). Chưa có quyền API hoặc shell vẫn có nghiên cứu/fixtures/contract hợp lệ; không coi điều đó là đã sẵn sàng chạy feature live.

## Kiểm tra tài liệu

Kiểm tra bằng script đọc Markdown và rà nội dung ngày 11/11/2026:

- 14 tài liệu đầu ra: 13 trong execution và TEAM_WORKFLOW ở root; 73 card ID duy nhất, đủ 15 trường/owner/mục tiêu/checklist, gồm 68 active và 5 archive.
- Board khớp 68 active về owner/priority/scope/milestone/dependency/effort; tất cả TODO. 46/46 P0 có acceptance criteria. Tổng theo owner/priority khớp Runbook.
- 119 dependency cứng active không có vòng, không trỏ vào archive; thêm 5 cạnh gate có điều kiện vẫn không có vòng. Graph P0 có đúng 46 node/84 cạnh, khớp cards; không P0 nào có prerequisite cứng P1/P2.
- Đã kiểm 360 liên kết Markdown nội bộ, đích file và anchor đều tồn tại; không duplicate explicit anchor trong cùng file. 6 liên kết nguồn ngoài được giữ lại, không kiểm HTTP/nghiên cứu lại trong lượt này.
- Đối chiếu đủ 50 ID cũ: 45 active, 5 archive; thêm 23 ID mới. Mỗi track có ít nhất một task không phụ thuộc track khác để bắt đầu.
- Rà semantics: M2 chờ CRUD qua INT-001 → BACK-012; C-211 chờ privacy C-210; C-212 chờ real-store INT-001; fake UI không đợi provider; feasibility DONE không tự bật runtime.
- Git không có thay đổi trên 8 tài liệu gốc được theo dõi. Chỉ tài liệu Markdown mới/untracked trong execution và TEAM_WORKFLOW; không application code, build/test ứng dụng, gọi API có phí, commit/push/merge/PR/deploy.

So với snapshot đầu lượt tiếp tục: chỉnh CONTRACTS, DEPENDENCIES_AND_INTEGRATION, README, REVIEW_AND_DECISIONS, RUNBOOK, TASK_BOARD và tracks/A/B/C/D (10 file); tạo TEAM_WORKFLOW và VTO_FEASIBILITY (2 file). BACKLOG và PREPARATION giữ nguyên nội dung vì đã phù hợp. Kiểm tra này xác nhận tính nhất quán tài liệu, không xác nhận feature/provider đã chạy. Việc thực thi và các quyết định còn mở vẫn ở board/decision log.
