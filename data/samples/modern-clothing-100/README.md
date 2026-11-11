# 100 ảnh quần áo thật để phát triển

Theo yêu cầu giới hạn dung lượng repo, chỉ đưa **100 ảnh bulk** lên GitHub. Bộ này độc lập với 24 ảnh fixtures đã có.
Ảnh là đồ mặc đời thường; không khẳng định ảnh mới chụp hay hợp xu hướng năm 2026.

| Thư mục ảnh | Loại | Số ảnh |
|---|---|---:|
| [TOP](images/TOP) | Áo | 25 |
| [BOTTOM](images/BOTTOM) | Quần, quần short, chân váy | 25 |
| [DRESS](images/DRESS) | Đầm | 15 |
| [OUTERWEAR](images/OUTERWEAR) | Áo khoác | 15 |
| [SHOES](images/SHOES) | Giày | 10 |
| [ACCESSORY](images/ACCESSORY) | Mũ | 10 |

[manifest.json](manifest.json) ghi file, nhãn gốc `sourceLabel`, category được mapping, URL nguồn tại revision cố định,
kích thước, SHA-256 và nhóm contributor đã băm. Ảnh giữ nguyên byte nguồn, giấy phép **CC0 1.0** trong [LICENSE](LICENSE).
Nguồn: [Alexey Grigorev clothing-dataset](https://github.com/alexeygrigorev/clothing-dataset/tree/7ed86a9cc56763b6acb35e41cb35184e43a5d218);
credit các contributor và Tagias. Không có nhãn màu, chất liệu, phong cách hoặc cặp trước/sau được suy đoán thêm.

Lựa chọn có thể tái tạo: chỉ lấy split `train` của bulk, loại các ảnh đã dùng làm fixture,
chọn theo hạn ngạch category và luân phiên nhãn gốc; thứ tự trong mỗi nhãn dùng SHA-256 với seed cố định.
Đây là mẫu phục vụ phát triển/import ảnh, không đại diện phân bố toàn bộ dataset, không phải holdout đo accuracy.
Nhãn được giữ từ nguồn, **chưa được kiểm độc lập**: `referenceFields=["category"]`, `groundTruthFields=[]`,
`independentReviewStatus=PENDING`. Không đổi ảnh đơn thành cặp front/back; `view=UNKNOWN`, `back=null`.

Từ thư mục gốc repo:

```powershell
python -m pip install -r tools/data/requirements.txt
python tools/data/package_modern_sample.py
```

Lệnh trên kiểm đủ 100 ảnh, nhãn/category, hash, kích thước, metadata và giấy phép; chạy được ngay sau clone, không tải bulk.
Muốn tái tạo từ nguồn thì chạy:

```powershell
python tools/data/prepare_modern_clothing.py --download
python tools/data/package_modern_sample.py --build
```

Bulk đầy đủ và chỉ mục vẫn ở `data/raw/`, `data/processed/` trên máy tải, được gitignore.
100 ảnh này không được đóng gói trong APK hay tự import vào tủ đồ. Bộ kiểm thử có sẵn trong debug vẫn là 21 món/24 ảnh,
hướng dẫn tại [data/README.md](../../README.md).
