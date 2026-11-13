# Track A — Wardrobe & Imaging

> Đây là plan v1 được giữ để tham khảo. Phạm vi hiện hành ở [card v2](../execution/tracks/A.md),
> [hướng dẫn triển khai](../track-a-v2.md) và [nghiệm thu](../qa/track-a-acceptance.md).
> Mục tiêu thời gian/AI bên dưới không phải kết quả đã đo; wear tracking và seed minh họa không thuộc luồng v2 đang dùng.

**Sở hữu:** `core:database`, `core:imaging`, `feature:wardrobe`
**Implement:** `WardrobeRepository`, `ImageSegmenter`
**Dùng của người khác:** `ClothingTagger` (B — stub trước), `MetricsTracker` (D)

## Mục tiêu
Người dùng thêm một món đồ trong **< 20 giây**: chụp → nền được tách → thuộc tính được AI điền sẵn → sửa nhanh → lưu. Dữ liệu **còn nguyên sau khi force-stop và mở lại app**.

## Phạm vi

### A1. `core:database`
- Room `AppDatabase` với entity: `ClothingItemEntity`, `OutfitEntity`, `WearLogEntity`, `FeedbackEntity` (tạo đủ trong Sprint 0; DAO của WearLog/Feedback để D dùng).
- TypeConverter cho list/set/Instant; `exportSchema = true`, lưu schema JSON trong repo.
- `WardrobeRepositoryImpl`: Flow danh sách, lọc theo category/màu/mùa, cập nhật `wearCount/lastWornAt` (D gọi qua method `markWorn(ids, at)`).
- Seed data debug (ảnh mẫu trong `assets/seed/`).

### A2. `core:imaging`
- CameraX capture + Photo Picker (chọn từ thư viện).
- `MlKitSegmenter`: ML Kit **Subject Segmentation** → PNG nền trong suốt; xử lý trường hợp không tìm thấy subject (giữ ảnh gốc, báo người dùng).
- Lưu ảnh vào `filesDir/wardrobe/{id}.png` (KHÔNG dùng cache dir — sẽ mất).
- Resize/nén (≤ 768px cạnh dài, JPEG/WebP) để gửi cho B; tính SHA-256 để B cache.
- Tải model ML Kit sớm (lúc onboarding) để lần đầu demo không phải chờ tải.

### A3. `feature:wardrobe`
- **Màn hình Tủ đồ**: lưới ảnh, chip lọc, empty state hướng dẫn thêm đồ đầu tiên.
- **Luồng Thêm đồ**: Camera/Picker → preview ảnh tách nền (animation "AI đang xử lý") → gọi `ClothingTagger` → form đã điền sẵn → Lưu.
  - Tagger lỗi/timeout ⇒ form trống với gợi ý chọn nhanh, vẫn lưu được.
- **Chi tiết món đồ**: ảnh, thuộc tính, số lần mặc, lần mặc cuối, sửa/xóa.
- Gửi event `item_added` (kèm thời gian thêm), `tag_corrected` (field nào bị sửa → đo chất lượng AI).

## Ngoài phạm vi
Phối đồ (C), thống kê (D), prompt/AI (B).

## Task & thứ tự
1. Entity + DAO + repo + test DAO (in-memory Room)
2. Màn hình lưới đọc seed data
3. CameraX + Picker → lưu ảnh gốc
4. ML Kit segmentation + lưu PNG
5. Form thuộc tính (dùng `StubClothingTagger` của B)
6. Chi tiết / sửa / xóa (xóa cả file ảnh)
7. Khi B xong M1: test với tagger thật, đo thời gian end-to-end

## Definition of Done
- [ ] Thêm 10 món liên tiếp không crash, trên máy thật
- [x] Force-stop → mở lại: đủ 10 món, ảnh hiển thị (đã kiểm tra trên trình giả lập API 35; máy thật chưa nghiệm thu)
- [ ] Tắt mạng: vẫn thêm được (tách nền vẫn chạy, form thủ công)
- [x] Unit test repo/DAO, coverage ≥ 80% cho `core:database` (92,19%)
- [x] Không quyền thừa (chỉ CAMERA là quyền runtime; picker không cần storage permission)

Code A1–A3 đã triển khai. Lưu ảnh gốc khi offline đã chạy thành công trên trình giả lập; tách nền offline sau khi tải model và 10 lần thêm trên máy thật còn cần nghiệm thu. Xem [báo cáo kiểm thử](../track-a-verification.md) để biết kết quả và giới hạn; tagger thật phụ thuộc Track B.

## Rủi ro
- Segmentation kém với ảnh treo móc/nền rối ⇒ hướng dẫn chụp trên nền phẳng; cho phép "giữ ảnh gốc".
- Ảnh lớn gây OOM ⇒ decode có `inSampleSize`, xử lý trên `Dispatchers.Default`.
