# Dữ liệu Track A

Catalog dùng cho phát triển và kiểm thử trên PC là **Fashion Product Images (Small), phiên bản 1**. Bộ fixtures ở `fixtures/modern-clothing/` phục vụ các ca kiểm thử Android cần ảnh trước/sau. [Nguồn catalog và giấy phép MIT do Kaggle công bố](https://www.kaggle.com/datasets/paramaggarwal/fashion-product-images-small).

## Catalog Kaggle

Từ thư mục gốc repo, với Python 3.10 trở lên:

```powershell
python -m pip install -r tools/data/requirements.txt
python tools/data/prepare_kaggle_fashion.py --download --sample-per-category 2
```

Lệnh tải bản v1 vào `data/raw/kaggle/fashion-product-images-small-v1.zip` (khoảng 565 MiB), kiểm SHA-256 của `styles.csv`, rồi nối mỗi sản phẩm với JPEG theo `id`. Để dùng ZIP v1 đã tải từ trình duyệt, truyền `--archive "đường-dẫn-tới-file.zip"` thay cho `--download`. Bỏ `--sample-per-category 2` nếu chỉ cần tạo chỉ mục đầy đủ.

| Đầu ra trong `data/processed/kaggle-fashion-small/` | Cách dùng |
|---|---|
| `items.jsonl` | 24.856 dòng ảnh hợp lệ. `archiveMember` là tên file ảnh bên trong ZIP. `sourceId` nối lại với catalog. |
| `excluded.json` | ID và lý do loại từng dòng. `duplicateOf` trỏ đến ID được giữ khi ảnh và nhãn tham chiếu trùng. |
| `audit.json` | Thống kê lọc, phân bố nhóm, kích thước, nguồn và SHA-256. [Báo cáo đã chạy trên bản v1](reports/kaggle-fashion-small-audit.json). |
| `sample-2-per-category/` | 12 ảnh đã giải nén để thử chọn ảnh trong app. Mỗi mục trong `manifest.json` có `file` tương đối tới JPEG và nhãn để đối chiếu. |

Để thử trên Android, đưa một ảnh từ thư mục mẫu vào thư viện trên máy hoặc emulator, rồi vào **Tủ đồ → Thêm món → Chọn ảnh**. Dùng `category` và `colors` trong manifest để điền và kiểm lại sau khi lưu. ZIP, ảnh xuất và chỉ mục đầy đủ nằm trong các thư mục local được `.gitignore` quản lý.

### Quy tắc nhãn và phạm vi

- `sourceLabels` giữ 9 cột metadata của Kaggle, gồm `gender`, `masterCategory`, `subCategory`, `articleType`, `baseColour`, `season`, `year`, `usage`, `productDisplayName`. `sourceId` giữ cột `id`. Trường `articleType` chứa loại chi tiết như `Tshirts`, `Skirts` hoặc `Formal Shoes`.
- `category` lấy theo bộ ba `masterCategory`, `subCategory`, `articleType` trong danh sách của script. Danh sách ưu tiên áo, quần/chân váy, đầm, áo khoác, giày/dép và đồ đội đầu. Đồng hồ, túi, mỹ phẩm, trang sức, đồ lót, đồ ngủ, saree và bộ nhiều món nằm ngoài phạm vi catalog Track A này.
- `colors` quy đổi một màu chính sang enum của app, ví dụ `Navy Blue` thành `BLUE`. `Multi`, `NA` và giá trị chưa map có `colors=[]`. `seasonTags` giữ mùa catalog sau quy đổi, ví dụ `Fall` thành `AUTUMN`. Cột `usage` vẫn nằm trong `sourceLabels` để tham chiếu.
- `referenceFields` ghi loại và màu chính có dữ liệu. `pattern=UNKNOWN`, `material=null`, `formality=null`, `styleTags=[]`, `view=UNKNOWN` và `back=null` phản ánh các thuộc tính chưa xác định. `groundTruthFields=[]` và `independentReviewStatus=PENDING` ghi trạng thái review khi Track B dùng nhãn.

| Nhóm app | Số ảnh |
|---|---:|
| Áo (`TOP`) | 12.227 |
| Quần và chân váy (`BOTTOM`) | 2.383 |
| Đầm (`DRESS`) | 438 |
| Áo khoác (`OUTERWEAR`) | 302 |
| Giày và dép (`SHOES`) | 9.214 |
| Mũ và băng đô (`ACCESSORY`) | 292 |
| **Tổng** | **24.856** |

Audit có 44.446 dòng catalog và 44.441 ảnh ở `images/`. Thư mục `myntradataset/` trong ZIP chứa thêm bản sao của bộ ảnh và CSV. Script dùng thư mục gốc, nối lại tên ở 22 dòng có dấu phẩy thừa sau cột metadata, và giải mã JPEG trong phạm vi đã chọn. Có 18.818 dòng ngoài phạm vi, 5 dòng thiếu ảnh, 709 bản trùng byte cùng nhãn nhóm/màu và 58 dòng trùng byte có xung đột nhãn. Với bản trùng cùng nhãn, giữ ID số nhỏ nhất. Trong tập đã lọc, 24.731 ảnh có màu đã map và 24.835 ảnh có mùa catalog.

Phần lớn ảnh là 60 × 80 px và một số món được chụp trên người mẫu. Các sản phẩm được giữ có năm catalog từ 2007 đến 2018. Bộ này phù hợp cho kiểm thử nhập ảnh, tra nhãn và thử pipeline phân loại. Đánh giá recognition cần review nhãn độc lập và tập kiểm thử riêng. Kiểm thử ảnh hai mặt dùng fixtures bên dưới.

Hai kiểm tra dữ liệu chạy được sau khi clone, dùng fixtures trong repo và ZIP giả lập nhỏ cho các ca CSV lệch cột, ảnh thiếu/hỏng và ảnh trùng:

```powershell
python tools/data/validate_fixtures.py
python -m unittest discover -s tools/data -p test_prepare_kaggle_fashion.py
```

## Fixtures Android

Bộ [manifest](fixtures/modern-clothing/manifest.json) gồm **21 món, 24 ảnh thật, 3 cặp trước/sau**, đủ 6 nhóm, kèm [ca lỗi](fixtures/modern-clothing/cases.json). Các nhãn màu/họa tiết và tên hiển thị đã được trợ lý rà từ ảnh. `fieldProvenance` ghi nguồn từng trường, `referenceFields` liệt kê nhãn tham chiếu và `groundTruthFields` để rỗng trong khi chờ review độc lập.

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
