# Dữ liệu Track A

Catalog dùng cho phát triển và kiểm thử trên PC là **Fashion Product Images (Small), phiên bản 1**. Bộ fixtures ở `fixtures/modern-clothing/` phục vụ các ca kiểm thử Android cần ảnh trước/sau. [Nguồn catalog và giấy phép MIT do Kaggle công bố](https://www.kaggle.com/datasets/paramaggarwal/fashion-product-images-small).

## Catalog Kaggle

Từ thư mục gốc repo, với Python 3.10 trở lên:

```powershell
python -m pip install -r tools/data/requirements.txt
python tools/data/prepare_kaggle_fashion.py --download --sample-per-type 10
```

Lệnh tải bản v1 vào `data/raw/kaggle/fashion-product-images-small-v1.zip` (khoảng 565 MiB), kiểm SHA-256 của `styles.csv`, rồi nối mỗi sản phẩm với JPEG theo `id`. Để dùng ZIP v1 có sẵn, truyền `--archive "đường-dẫn-tới-file.zip"`. Bỏ `--sample-per-type 10` nếu chỉ cần tạo chỉ mục đầy đủ.

| Đầu ra trong `data/processed/kaggle-fashion-small/` | Cách dùng |
|---|---|
| `catalog.csv` | Đủ 44.446 dòng và 10 cột gốc, kèm `wardrobeCategory`, `garmentType`, `garmentTypeBasis` để lọc trong Excel. Ba cột bổ sung để trống với dòng chưa có ảnh hợp lệ trong chỉ mục. [Bản CSV trong Git](reports/kaggle-fashion-small-catalog.csv). |
| `items.jsonl` | 36.997 dòng ảnh hợp lệ. `archiveMember` trỏ tới ảnh trong ZIP. `sourceId` nối lại với catalog. |
| `excluded.json` | ID và lý do loại từng dòng. `duplicateOf` trỏ đến ID được giữ khi ảnh và nhãn tham chiếu trùng. |
| `audit.json` | Thống kê lọc, số ảnh từng tag, kích thước, nguồn và SHA-256. [Báo cáo đã chạy trên bản v1](reports/kaggle-fashion-small-audit.json). |
| `metadata-top10.json` | Tối đa 10 giá trị phổ biến cho từng cột nhãn. [Bảng xếp hạng đã tạo](reports/kaggle-fashion-small-top10.json). |
| `sample-10-per-type/` | 480 ảnh đối chiếu, tối đa 10 ảnh/tag. Manifest ghi đường dẫn, SHA-256, đủ nhãn gốc, tag quy đổi và số mẫu từng tag. |

Để thử trên Android, đưa một JPEG mẫu vào thư viện máy hoặc emulator. Vào **Tủ đồ → Thêm đồ → Chọn ảnh** trong ô **Mặt trước**. Dùng `category`, `garmentType` và `colors` trong manifest để đối chiếu sau khi chọn hoặc nhận diện. JPEG catalog, ZIP và chỉ mục JSONL nằm trong thư mục local. Để cập nhật CSV và hai báo cáo trong Git sau khi chạy audit, thêm `--publish-metadata` vào lệnh chuẩn bị.

### Quy tắc nhãn và phạm vi

- `sourceLabels` giữ nguyên `gender`, `masterCategory`, `subCategory`, `articleType`, `baseColour`, `season`, `year`, `usage`, `productDisplayName`. `sourceId` giữ cột `id`.
- Bảng top 10 tính trên toàn bộ 44.446 dòng catalog, xếp theo số lần xuất hiện giảm dần rồi theo tên khi bằng nhau. Mẫu số của phần trăm gồm cả dòng thiếu giá trị. Giá trị rỗng và `NA` được đếm riêng. Cột có ít hơn 10 giá trị có danh sách ngắn hơn. `id` và tên sản phẩm được giữ theo từng dòng để tra cứu.
- `category` là sáu nhóm của app. [Từ điển 51 tag](sources/garment-types.json) giữ mã, tên tiếng Việt, nhóm và dấu hiệu nhận diện. Bộ ba `masterCategory/subCategory/articleType` cho biết loại gốc. `garmentTypeBasis` ghi khi script dùng thêm tên hoặc `usage` để tách polo, quần âu, giày da và các kiểu khác. Tên hãng có chữ Polo đứng riêng không đủ để gán tag polo. Giày tây chỉ được gán giày da khi tên catalog có ghi leather.
- `colors` quy đổi một màu chính sang bảng màu của app, ví dụ `Navy Blue` thành `BLUE`. `Multi`, `NA` và giá trị chưa map có `colors=[]`. `seasonTags` giữ mùa catalog sau quy đổi, ví dụ `Fall` thành `AUTUMN`. Mùa, năm, đối tượng bán, dịp sử dụng và tên catalog thuộc metadata nguồn.
- `referenceFields` ghi nhóm, loại chi tiết và màu chính khi có. `pattern=UNKNOWN`, `material=null`, `formality=null`, `styleTags=[]`, `view=UNKNOWN` và `back=null` giữ các thuộc tính chưa xác định. `groundTruthFields=[]` và `independentReviewStatus=PENDING` ghi trạng thái review.

| Nhóm app | Số ảnh |
|---|---:|
| Áo (`TOP`) | 14.301 |
| Quần và chân váy (`BOTTOM`) | 2.382 |
| Đầm và đồ liền quần (`DRESS`) | 454 |
| Áo khoác (`OUTERWEAR`) | 302 |
| Giày và dép (`SHOES`) | 9.213 |
| Phụ kiện (`ACCESSORY`) | 10.345 |
| **Tổng** | **36.997** |

Audit có 44.446 dòng catalog và 44.441 ảnh ở `images/`. Thư mục `myntradataset/` trong ZIP chứa bản sao. Script nối lại tên ở 22 dòng có dấu phẩy thừa và giải mã từng JPEG trong phạm vi đã chọn. Có 6.639 dòng ngoài phạm vi, 5 dòng thiếu ảnh, 724 bản trùng byte cùng nhãn loại/màu và 81 dòng trùng byte có xung đột nhãn. Bản trùng cùng nhãn giữ ID nhỏ nhất. Mỹ phẩm, đồ lót, đồ ngủ, bộ nhiều món và các loại chưa quy đổi vẫn có metadata trong CSV.

Phần lớn ảnh là 60 × 80 px và một số món được chụp trên người mẫu. Bộ mẫu có đủ 10 ảnh cho 46 tag, bao gồm cả 12 tag được yêu cầu. Năm tag ít ảnh hơn là cardigan (2), blazer (8), áo khoác dài (0), mũ (3), băng đô (7). Nhãn catalog phù hợp để tra cứu và kiểm thử. Đo chất lượng AI cần review ảnh độc lập trên một tập riêng. Kiểm thử ảnh hai mặt dùng fixtures bên dưới.

Hai kiểm tra dữ liệu chạy được sau khi clone, dùng fixtures trong repo và ZIP giả lập nhỏ cho các ca CSV lệch cột, ảnh thiếu/hỏng và ảnh trùng:

```powershell
python tools/data/validate_fixtures.py
python -m unittest discover -s tools/data -p test_prepare_kaggle_fashion.py
```

## Fixtures Android

Bộ [manifest](fixtures/modern-clothing/manifest.json) gồm **21 món, 24 ảnh thật, 3 cặp trước/sau**, đủ 6 nhóm, kèm [ca lỗi](fixtures/modern-clothing/cases.json). Nhãn loại chi tiết, màu và họa tiết dùng để đối chiếu trên Android. `fieldProvenance` ghi nguồn từng trường, `referenceFields` liệt kê nhãn tham chiếu và `groundTruthFields` để rỗng trong khi chờ review độc lập.

Ngày 12/11/2026, người dùng xác nhận đầm `a06aab12` màu đen. Track A đối chiếu tám trường còn lại từ review của B với ảnh gốc và sửa bảy trường. Quần `4d0ffc98` giữ `BLUE,WHITE` do thấy sắc navy ở vùng sáng, đồng thời cập nhật họa tiết `STRIPED`. Manifest ghi lý do theo từng trường. [Review nhãn của B](../eval/ai/recognition/label-review.track-a.json).

Script tạo nhãn eval ưu tiên các trường đã chốt trong manifest. Các số liệu từ lần chạy ngày 11/11/2026 vẫn dùng bộ nhãn của lần chạy đó. Một phép đo accuracy độc lập cần bộ ảnh mới được gắn nhãn trước khi chạy model.

- **18 ảnh đơn:** [Alexey Grigorev clothing-dataset](https://github.com/alexeygrigorev/clothing-dataset), revision `7ed86a9cc56763b6acb35e41cb35184e43a5d218`, [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). Credit: các contributor của clothing-dataset và Tagias.
- **DDC International T-shirt, front/back:** Jonathan Schilling, [front](https://commons.wikimedia.org/wiki/File:DDC_International_T-shirt_front.jpg), [back](https://commons.wikimedia.org/wiki/File:DDC_International_T-shirt_back.jpg), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- **Wiki Loves Monuments female shirt, front/back:** [Béria L. Rodríguez @ Wikimedia Commons](https://commons.wikimedia.org/wiki/User:Beria), [front](https://commons.wikimedia.org/wiki/File:WLM_shirt_-_Female_(front).jpg), [back](https://commons.wikimedia.org/wiki/File:WLM_shirt_-_Female_(back).jpg), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
- **10 years of Polish Wikipedia event shirt, front/back:** Juandev, [front](https://commons.wikimedia.org/wiki/File:10_years_of_polish_Wikipedia,_event_T-shirt_front_side.JPG), [back](https://commons.wikimedia.org/wiki/File:10_years_of_polish_Wikipedia,_event_T-shirt_back_side.JPG), [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Logo có thể có quyền nhãn hiệu riêng.

Ảnh fixtures giữ giấy phép ảnh nguồn, được xoay theo EXIF, thu nhỏ tối đa 1.536 px và encode JPEG với metadata đã bỏ. Manifest ghi URL, giấy phép, attribution, hash trước/sau và các thay đổi. `variants/blurry.jpg` là ảnh CC0 được làm mờ để kiểm thử lỗi. Ba cặp trước/sau dựa trên mô tả nguồn và đối chiếu ảnh.

Trong APK debug, vào **Tủ đồ → Ảnh kiểm thử → Thêm bộ ảnh** để import chủ động vào tủ hiện tại. Fixtures được đóng gói trong debug. ZIP Kaggle và mẫu đã xuất phục vụ kiểm thử trên máy phát triển.

Tái tạo fixtures khi cần cập nhật nguồn hoặc ảnh:

```powershell
python tools/data/prepare_modern_clothing.py --download
python tools/data/fetch_pairs.py
python tools/data/curate_fixtures.py --commons-dir data/raw/commons
python tools/data/validate_fixtures.py
```

Script CC0 dùng bộ nguồn cũ để tái tạo 18 ảnh đơn. [Báo cáo audit CC0](reports/modern-clothing-audit.json) ghi 5.096 ảnh có nhãn sau lọc. Downloader Wikimedia dừng khi gặp 429 và báo Retry-After. Pillow/JPEG codec khác phiên bản có thể thay đổi hash ảnh dẫn xuất, nên kiểm lại manifest khi tái tạo. [Đánh giá nguồn Polyvore](POLYVORE_ASSESSMENT.md) được lưu riêng.
