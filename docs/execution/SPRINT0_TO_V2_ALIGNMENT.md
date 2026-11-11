# FitCheckAI — Đồng bộ Sprint 0 scaffold với execution contracts v2

**Ngày ghi nhận:** 2026-11-11. **Trạng thái:** tài liệu hướng dẫn chuyển đổi, KHÔNG phải bằng chứng mã nguồn đã được migrate.
**Nguồn chuẩn:** [`CONTRACTS.md`](CONTRACTS.md), [`TEAM_WORKFLOW.md`](../../TEAM_WORKFLOW.md), task cards của A/B/C/D. File này không thay đổi scope, ID, priority, effort hoặc dependency của 68 task hiện có.

## Quyết định

**Không hạ contracts v2 xuống tương thích với scaffold v1.** Sprint 0 (commit `314e237`) là bộ khung ban đầu; các interface Kotlin, Room schema, UI và proxy bên trong vẫn dựa trên phân công cũ. Sửa mã nguồn để tuân thủ v2 theo ownership; giữ scaffold/Gradle/Hilt dùng được thay vì dựng project thứ hai.

Tất cả track có tác động. **D dẫn thay đổi interface dùng chung và composition root**, nhưng không tự gánh migration của A/B/C.

| Hiện có ở Sprint 0 | Đích v2 | Primary owner thực thi |
|---|---|---|
| `core/model/Clothing.kt`: `imagePath` đơn, `wearCount`, không có `MediaRef`/`ImagePair`/`revision` | ClothingItem/MediaRef/ImagePair theo CT-01/CT-03, front/back và revision, không dùng absolute path làm identity | **D** chốt shared model + signatures; **A** cập nhật mapper/Room/media |
| `core/model/Contracts.kt`: WardrobeRepository cũ, ImageSegmenter, ClothingTagger, AiOutfitGenerator, WeatherProvider, OutfitService, WearLogRepository, ProfileRepository | Interfaces CT-03…06; MediaStore/WardrobeRepository/ProfileStorage/recognition/recommendation/VirtualTryOn | **D** quản lý shared interface; **A/B/C** hiện thực service/fake của mình |
| `Outfit.kt`: một `OutfitRequest` với `List<ClothingItem>`, `OutfitSource AI|RULES`, thiếu snapshot/revision | OutfitRequest/Result theo CT-02 (1–5 outfit, snapshot IDs, typed errors, source `AI_LIVE|RULES|FAKE`) | **D** shared types; **B** generator/validation; **D** Today |
| `UserData.kt`: `StyleProfile` và wear feedback, không có paired body photos | `UserProfile`, `ImagePair` body media, `ProfileStorage` theo CT-04; optional photo/weight | **D** |
| `app/FitCheckApp.kt`: Today, Wardrobe, Insights; chưa có TryOn route | Profile/Wardrobe/Today/TryOn; disabled VTO đúng capability; không tự generate | **D** route/DI; **C** TryOnScreen |
| `settings.gradle.kts`/app Gradle: nhiều module/Hilt hiện hữu, chưa có TryOn | Tái sử dụng scaffold; thêm module C khi D chấp thuận và wire module | **D** build/root; **C** đề xuất module + code độc lập |
| `proxy/src/index.ts`: `/v1/tag`, `/v1/outfits` và `x-device-id` stub; chưa có VTO jobs | `/v2/recognition`, `/v2/outfits`, `/v2/tryon/jobs` với validation/consent/auth/budget | **B** proxy entry/auth; **C** VTO handler/adapter sau gate |
| `.github/CODEOWNERS`: ghi C = Stylist/Weather, D = Insights | A Wardrobe/Imaging; B Recognition/Reco/Weather; C VTO; D Foundation/Profile/Today | **D** điều phối thay ownership với A/B/C |

## Nguồn dữ liệu A → C

C **không phải chờ A push implementation** để làm provider research, POC bằng dữ liệu được phép, fake job, client/Screen/Compose Preview hoặc privacy tests. C dùng fixtures tuân `MediaRef`, `ImagePair`, `MediaStore` v2.

Điều kiện khi ghép A thật:
- Mỗi garment `front` có ref hợp lệ; `back` là ảnh thực khác hoặc `null`, không nhân đôi front thành back.
- A giữ original/bản chuẩn hóa và derivative (segmentation riêng); không chỉ giữ ảnh đã tách nền.
- `MediaStore.read(ref)` cung cấp stream/bytes sau kiểm tra revision, owner, scope, view, MIME; C không đọc trực tiếp Room DAO, `imagePath`, đường dẫn tuyệt đối.
- A xử lý import, orientation, strip EXIF, resize, xoá refs; C xử lý upload derivative theo provider sau consent, job/result lifecycle.
- Tác vụ phối đồ nhận `Outfit` với item IDs + revision; C chỉ Try-On **một outfit được chọn**, không render tất cả.
- Person front/back và profile do D quản lý qua ProfileStorage + A MediaStore; C không tạo kho ảnh người thứ hai.

## Representation đã chốt cho Track C

MVP VTO là **AI photorealistic 2D với ảnh người thật**, không phải 3D/avatar xoay 360° và không phải flat-lay/mannequin. Mục tiêu nhận `personFront/personBack` và `garmentFront/garmentBack`, xuất **hai ảnh 2D** trước/sau của cùng outfit. Đây là mục tiêu, **không mặc định model có native paired-view hoặc multi-garment**. Chỉ công bố `FRONT` hoặc `DUAL` khi qua gate trong [`VTO_FEASIBILITY.md`](VTO_FEASIBILITY.md). Không tuyên bố độ vừa vặn/kích cỡ chính xác.

## Trình tự thực thi, không khóa cả nhóm

1. **D (PRE-007/S-002)**: công bố shared types và interface v2 có thể compile, fixture + compatibility tests, xử lý deprecation v1 rõ ràng. Không xây lại Gradle project và không migrate dữ liệu sản xuất chưa tồn tại. D sở hữu và review các shared files.
2. **A**: mapper/Room/media theo v2, fake A-005; đưa ảnh front/back qua `MediaStore`, kiểm test persistence, chỉ sửa module A.
3. **B**: fakes và clients nhận v2, validation output, proxy `/v2`; B sở hữu auth, budgets, route mount.
4. **C**: nghiên cứu/POC và fake/job/privacy/Screen độc lập; gửi proposed signatures cho D; chỉ live provider sau rights, consent, budget và capability gate.
5. **D**: nối composition root/navigation/Profile/Today; A/B/C kiểm compatibility ở boundary; run CI, smoke APK trên thiết bị thật theo milestone.

Đừng chờ mọi track xong mới tích hợp. Những phần shared làm một patch nhỏ do owner hợp nhất; consumer không tự ghi đè model/Gradle/NavHost. Nếu D chưa có v2 compile, C/A/B có thể tự làm fixtures, module riêng và contract tests, **không tự tạo bộ contract v2 thứ hai trong production code**.

## Kiểm tra trước khi đánh dấu S-002 / M1

- Build sạch các shared interfaces v2 và four-track fakes; không còn consumer bị buộc vào `imagePath`/wear log để compile.
- Sample front-only, full pair, missing back, stale revision, deleted media, invalid outfit IDs, VTO partial/failure/consent revoked.
- Không có import vòng giữa domain/types và feature implementation.
- Repo chạy được bằng Gradle/Hilt hiện hữu hoặc migration nhỏ đã được đồng ý.
- CI/test build thực tế đã chạy; không báo PASS chỉ vì docs đã được sửa.
- Tài liệu `REVIEW_AND_DECISIONS.md` cũ nói chưa có source ở **thời điểm lập tài liệu**; không áp dụng làm trạng thái repo sau commit Sprint 0.

## Kiểm soát thay đổi

- Không đổi ID, P0/P1/P2, effort, dependency hoặc 68-task board chỉ để thêm ghi chú này.
- Không push/commit/merge/PR/deploy tự động. Triển khai làm trên nhánh riêng theo quy ước của nhóm.
- Đánh dấu việc chuyển đổi *DONE* chỉ khi implementation và tests tương ứng đã tồn tại.
