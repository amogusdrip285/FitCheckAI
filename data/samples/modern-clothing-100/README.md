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

## Các nhãn cần kiểm lại

Rà contact sheet bằng trợ lý ghi nhận các trường hợp bên dưới; đây chưa phải review độc lập hay danh sách lỗi đầy đủ.
Giữ nguyên `sourceLabel` trong manifest để truy nguồn, không tự sửa thành ground truth.

| Ảnh | Nhãn nguồn | Quan sát cần đối chiếu |
|---|---|---|
| [2ac96388](images/BOTTOM/2ac96388-4353-44b6-a174-de2f2523afd1.jpg) | Skirt | Trông giống quần short kẻ; cần kiểm lại nhãn chi tiết. Category BOTTOM vẫn phù hợp. |
| [637a56e4](images/BOTTOM/637a56e4-7fa6-4f75-8859-0f8902461554.jpg) | Skirt | Trông giống quần short đen; cần kiểm lại nhãn chi tiết. Category BOTTOM vẫn phù hợp. |
| [c6bc821e](images/DRESS/c6bc821e-631a-4221-9d92-7c7685edebd0.jpg) | Dress | Ảnh đồ có phụ kiện che một phần; chưa xác nhận được chiều dài và category. |
| [521c2c74](images/DRESS/521c2c74-7131-427a-af36-0c48aa438119.jpg) | Dress | Cần phân biệt váy quây với chân váy. |
| [5e982004](images/DRESS/5e982004-dc73-41b7-a784-ba316570fc1e.jpg) | Dress | Cần phân biệt đầm ngắn với áo không tay. |

Loại các trường hợp chưa thống nhất khỏi bộ chấm accuracy cho tới khi có reviewer xác nhận. Mẫu này vẫn dùng được để test nhập/lưu ảnh.

## Kiểm tra và tái tạo

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
