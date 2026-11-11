# Dữ liệu ảnh Track A

Chỉ có ảnh quần áo đời thường: áo, quần/váy, đầm, áo khoác, giày và mũ. Không đưa ảnh trang phục lịch sử/bảo tàng vào dataset hay APK.

## Các tập có mục đích khác nhau

| Tập | Đã có | Dùng để làm gì |
|---|---|---|
| Bulk CC0 | 5.756 file ảnh; 5.403 dòng nhãn; 5.096 ảnh có nhãn sử dụng được | Import/IO, phân loại coarse category và nghiên cứu thêm |
| [Mẫu bulk đã commit](samples/modern-clothing-100/README.md) | 100 ảnh, đủ 6 category, lấy từ split train và không trùng các fixture | Chia sẻ cho teammate, test import/ảnh trên PC; không đóng gói APK |
| Fixtures đã chọn | 21 món, 24 ảnh thật; 3 cặp front/back; 6 category | Test chức năng Track A có thể lặp lại trên PC/Android |

Không gọi 5.756 file là 5.756 ảnh có nhãn: có 358 file không có dòng nhãn tương ứng, 5 dòng thiếu ảnh và 302 dòng nhãn mơ hồ bị loại.
Audit không phát hiện file hỏng trong các dòng có file, hoặc bản trùng byte giữa các ảnh có nhãn được giữ. Chưa chứng minh không có ảnh gần trùng.
Chi tiết: [báo cáo audit](reports/modern-clothing-audit.json), [manifest](fixtures/modern-clothing/manifest.json), [case lỗi](fixtures/modern-clothing/cases.json).

Bulk giữ nhãn loại từ nguồn, chưa được kiểm thủ công toàn bộ. Tập fixtures được trợ lý rà soát trực quan, chưa được người độc lập adjudicate.
Chỉ mục bulk và mẫu 100 ảnh ghi category là nhãn tham chiếu từ nguồn, `groundTruthFields=[]`, review độc lập `PENDING`.
Manifest ghi `fieldProvenance` cho từng trường: category của 18 ảnh CC0 giữ cả `sourceLabel` và bước mapping/rà ảnh;
category của 3 áo Wikimedia, màu/họa tiết và tên hiển thị do trợ lý rà/đặt. `pairIdentity` ghi nguồn mô tả từng mặt và bước đối chiếu ảnh.
`referenceFields` liệt kê nhãn tham chiếu; **`groundTruthFields` để rỗng**, `independentReviewStatus=PENDING` cho cả 21 món.
Consumer B cần adjudicate và tách holdout trước khi coi chúng là benchmark. Trường `groundTruthFields` cũ được giữ tên để tương thích,
nhưng không còn liệt kê annotation chưa được kiểm độc lập. Validator chặn việc vô tình công bố chúng là ground truth.
Màu/họa tiết là annotation tham chiếu, không phải kết quả đo accuracy của AI. Chất liệu, độ trang trọng, mùa và phong cách không được tự suy diễn.
Độ phân giải nhiều ảnh bulk thấp; không dùng để kết luận chất lượng VTO, độ vừa vặn, hoặc khả năng xử lý ảnh camera độ phân giải cao.
Năm chụp các ảnh có thể cũ; “hiện đại” ở đây chỉ đồ mặc đời thường, không có claim xu hướng 2026.

## Nguồn và quyền sử dụng

- **18 ảnh đơn fixtures và 100 ảnh mẫu bulk:** [Alexey Grigorev clothing-dataset](https://github.com/alexeygrigorev/clothing-dataset), revision `7ed86a9cc56763b6acb35e41cb35184e43a5d218`, [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). Credit: các contributor của clothing-dataset và Tagias.
- **DDC International T-shirt, front/back:** Jonathan Schilling, [front](https://commons.wikimedia.org/wiki/File:DDC_International_T-shirt_front.jpg), [back](https://commons.wikimedia.org/wiki/File:DDC_International_T-shirt_back.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **Wiki Loves Monuments female shirt, front/back:** [Béria L. Rodríguez @ Wikimedia Commons](https://commons.wikimedia.org/wiki/User:Beria), [front](https://commons.wikimedia.org/wiki/File:WLM_shirt_-_Female_(front).jpg), [back](https://commons.wikimedia.org/wiki/File:WLM_shirt_-_Female_(back).jpg), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
- **10 years of Polish Wikipedia event shirt, front/back:** Juandev, [front](https://commons.wikimedia.org/wiki/File:10_years_of_polish_Wikipedia,_event_T-shirt_front_side.JPG), [back](https://commons.wikimedia.org/wiki/File:10_years_of_polish_Wikipedia,_event_T-shirt_back_side.JPG), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Logo có thể có quyền nhãn hiệu riêng; không ngụ ý được Wikimedia bảo trợ.

Ảnh dẫn xuất giữ cùng giấy phép ảnh nguồn; giấy phép ảnh không được thay bằng giấy phép code repo.
Ảnh fixtures được xoay theo EXIF, thu nhỏ tối đa 1.536 px, encode JPEG và xóa metadata. Manifest giữ URL nguồn/giấy phép, attribution, hash trước/sau và mô tả thay đổi.
100 ảnh mẫu bulk giữ nguyên byte nguồn, có hash/kích thước/nhãn trong manifest riêng và bản giấy phép đi kèm.
`variants/blurry.jpg` là biến thể làm mờ có chủ ý của ảnh CC0 để thử lỗi, không tính là ảnh thật mới hay ground truth nhận diện.
Ba cặp được xác nhận bằng mô tả nguồn và đối chiếu ảnh. Hai ảnh cùng loại áo không được ghép thành một cặp.

## Chạy và tái tạo

Python 3.10+; cài `Pillow` và `requests` (`pip install -r tools/data/requirements.txt`).

```powershell
python tools/data/validate_fixtures.py
python tools/data/package_modern_sample.py
python tools/data/prepare_modern_clothing.py --download
python tools/data/package_modern_sample.py --build
python tools/data/fetch_pairs.py
python tools/data/curate_fixtures.py --commons-dir data/raw/commons
python tools/data/validate_fixtures.py
```

Ảnh fixtures và đúng 100 ảnh mẫu bulk được commit để clone là có dữ liệu. Bulk source (~162 MB zip) và outputs đầy đủ ở `data/raw/`, `data/processed/`, được gitignore.
Yêu cầu mới nhất là chỉ push khoảng 100 ảnh bulk; không đưa toàn bộ 5.756 file hay ZIP lên GitHub.
Kiểm tra dữ liệu sau clone chỉ cần hai lệnh validator đầu; các lệnh còn lại dùng khi muốn tải/tái tạo dữ liệu.
Các minh họa legacy còn cần cho regression database nằm riêng ở `core/database/src/test/assets/seed`, không phải dataset và không đóng gói trong APK.
Downloader Wikimedia dừng khi gặp 429 và báo Retry-After; không dùng mirror để vượt hạn chế. Hash thay đổi phải được review lại.
Pillow/JPEG codec khác phiên bản có thể cho hash dẫn xuất khác; kiểm tra manifest và ảnh trước khi commit bản tái tạo.

Split bulk dựa trên nhóm người đóng góp, có liên kết nhóm nếu trùng byte; train 3.693, validation 605, test 798.
Không chia lại ngẫu nhiên từng ảnh rồi báo accuracy trên cùng item. Chưa có nhãn garment identity cho toàn bộ bulk nên không bảo đảm hết rò rỉ near-duplicate.
Các ảnh đã dùng để chọn fixture/thiết kế UI không phải một holdout mù cho nghiên cứu ML sau này.

Trong APK **debug**: Tủ đồ → Ảnh kiểm thử/Bộ ảnh thật để kiểm thử → Thêm bộ ảnh. Import chủ động, không thay tủ hiện có, không tự seed trong bản release.
Thông tin fixture được gắn “Cần kiểm tra nhãn”; không gọi là AI nhận diện. Polyvore được đánh giá riêng tại [POLYVORE_ASSESSMENT.md](POLYVORE_ASSESSMENT.md), chưa tải hoặc đóng gói.
