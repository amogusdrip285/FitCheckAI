# Kiểm thử tag loại đồ · 13/11/2026

Bản kiểm thử đồng bộ với `origin/main` tại `ef2a8ba` trước khi áp dụng thay đổi. Tủ đồ và proxy nhận diện dùng chung 51 tag tiếng Việt trong sáu nhóm. `category` phục vụ nhóm phối đồ. `garmentType` phục vụ nhận diện, chỉnh sửa và lọc loại chi tiết. [Từ điển dùng chung](../../data/sources/garment-types.json).

| 12 tag được yêu cầu | Mã | Ảnh hợp lệ Kaggle |
|---|---|---:|
| Áo sơ mi | `SHIRT` | 2.906 |
| Áo polo | `POLO` | 1.130 |
| Áo thun | `T_SHIRT` | 5.719 |
| Áo khoác | `JACKET` | 279 |
| Áo len | `SWEATER` | 275 |
| Quần jeans | `JEANS` | 587 |
| Quần âu | `DRESS_PANTS` | 238 |
| Quần short | `SHORTS` | 497 |
| Chân váy | `SKIRT` | 114 |
| Giày thể thao | `SNEAKERS` | 2.050 |
| Giày da | `LEATHER_SHOES` | 77 |
| Sandal | `SANDALS` | 963 |

Các tag bổ sung gồm đầm, hoodie, blazer, cardigan, quần thể thao, giày bệt, giày cao gót, dép xỏ ngón, mũ, túi, ba lô, thắt lưng, đồng hồ, kính và các phụ kiện khác. Số ảnh theo từng tag dựa trên catalog, không đo tần suất sử dụng ngoài đời. [Audit và số mẫu](../../data/reports/kaggle-fashion-small-audit.json).

## Kiểm dữ liệu và app

| Kiểm tra | Kết quả |
|---|---|
| Kaggle v1 | CSV giữ đủ 44.446 sản phẩm và 10 cột gốc. Có 36.997 ảnh hợp lệ với tag quy đổi. Bảng top 10 được tính trên toàn bộ catalog. Đã xác minh 480 JPEG mẫu với SHA-256, đủ 10 mẫu cho 46 tag. |
| Fixtures Android | 21 món, 24 JPEG, 3 cặp trước/sau. Tất cả tag khớp nhóm. Ảnh và hash nguồn khớp manifest. |
| Python | 4 bài qua, gồm nhãn gốc, tên có dấu phẩy, JPEG trùng/hỏng, xếp hạng và tách tag từ catalog. |
| Proxy | TypeScript qua. 182 bài test qua. Bundle Wrangler dry run thành công. |
| Android | 163 bài test qua trong `core:model` (43), `core:ai` (41), `core:database` (26), `feature:wardrobe` (38), `feature:today` (15). `assembleDebug` thành công. Lint Tủ đồ 0 lỗi và 1 cảnh báo `UseKtx`. |
| Samsung SM-F721B | Nâng cấp gói QA có tủ đồ sẵn. Mở món áo sơ mi trắng, thấy ba lựa chọn **Áo sơ mi**, **Áo polo**, **Áo thun**. Chọn tag, lưu, lọc còn đúng một món, mở chi tiết và kiểm tag còn sau khi khởi động lại app. |

Ảnh chụp Samsung lưu trong `build/outputs/garment-tags/`. APK QA dùng application ID `com.fitcheckai.tracka.physicalqa`. SHA-256 của APK dùng khi chạy luồng trên máy là `2b1459b83a313619502ff4ad3f0438a2720e64b6819795ee25bc4da5dde27c08`.

## Kiểm nhận diện qua FOSTA

Chạy Worker cục bộ với model `gpt-6-luna` qua FOSTA. Ba ảnh catalog chỉ gửi mặt trước. Ca thiếu consent trả HTTP 403 trước khi gọi model.

| ID Kaggle | Nhãn đối chiếu | Kết quả | Thời gian |
|---|---|---|---:|
| 17179 | Áo sơ mi trắng | `TOP / SHIRT`, có WHITE | 13,215 giây |
| 16645 | Áo polo xanh lá | `TOP / POLO`, có GREEN | 8,744 giây |
| 42175 | Áo thun trắng | `TOP / T_SHIRT`, có WHITE | 8,519 giây |

Điểm đối chiếu của lượt này là **3/3 nhóm**, **3/3 tag chi tiết** và **3/3 màu chính**. Ảnh mẫu được chọn để kiểm luồng. Các số này chưa đo accuracy của cả 51 tag. Nhãn nguồn không đủ căn cứ chấm họa tiết, chất liệu hoặc độ trang trọng.

## Chạy lại và triển khai

```powershell
python tools/data/validate_fixtures.py
python -m unittest discover -s tools/data -p test_prepare_kaggle_fashion.py
python tools/data/prepare_kaggle_fashion.py --download --sample-per-type 10 --publish-metadata
npm --prefix proxy run typecheck
npm --prefix proxy test -- --silent
.\gradlew.bat :core:model:test :core:ai:testDebugUnitTest :core:database:testDebugUnitTest :feature:wardrobe:testDebugUnitTest :feature:today:testDebugUnitTest :app:assembleDebug :feature:wardrobe:lintDebug
```

Sau khi cấu hình mã demo của proxy thử nghiệm trong môi trường máy, có thể kiểm guard và lập kế hoạch chấm theo manifest. Thêm `--confirm-spend --max-ai-calls 3` để thực sự gọi AI cho tối đa ba ảnh. Chọn một manifest nhỏ nếu cần đối chiếu ba tag cụ thể.

```powershell
node eval/ai/run-recognition.mjs --base-url http://127.0.0.1:8787 --labels ../../../data/processed/kaggle-fashion-small/sample-10-per-type/manifest.json --all-front --require-garment-type
```

Track B triển khai Worker theo cấu hình provider và trạng thái dịch vụ đã chốt. Kiểm tra `/health` trả `recognitionGarmentTypeVersion: 1`, sau đó chạy ca smoke với ảnh catalog. Tại thời điểm kiểm thử, Worker công khai chưa trả trường này. Android cho phép chọn và lọc tag thủ công ngay. [Runbook triển khai Worker](../track-b/B-001-deploy-runbook.md).
