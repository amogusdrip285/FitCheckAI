# Polyvore Outfits — đánh giá cho FitCheckAI (11/11/2026)

Nguồn người dùng đề xuất: <https://huggingface.co/datasets/mvasil/polyvore-outfits>.
Revision công khai tại lúc kiểm tra: `22cc0c748c293a598e1df561a76abe96273cb76a`.
Kho code của tác giả dẫn trực tiếp tới trang này: <https://github.com/mvasil/fashion-compatibility>.

## Kết luận

Phù hợp về mặt bài toán cho **Track B: compatibility, recommendation, retrieval, fill-in-the-blank**.
Track A có thể dùng một tập ảnh được cấp quyền để thử import, grid và thuộc tính category.
Không dùng nó làm bằng chứng hoàn thành Track A: metadata công khai không xác nhận các cặp front/back cùng garment,
không có đánh giá camera/URI/process death, và không thay thế bộ ảnh thử có manifest quyền sử dụng rõ ràng.

Dataset card mô tả 68.306 outfit, 261.058 fashion item, ảnh sản phẩm, category, description và nhóm outfit.
Đó là đồ mặc đời thường/thương mại, nhưng bộ nghiên cứu công bố năm 2018; không gọi là dữ liệu xu hướng 2026.
Trang hiện báo khoảng 4,3 GB. API công khai báo `gated: auto`; nội dung file cần tài khoản chấp nhận điều kiện.
Đã xem card, API metadata/file inventory, repo và abstract; **chưa tải parquet hay kiểm tra ảnh từng hàng**.

## Điều kiện cần làm rõ

Chính dataset card có mâu thuẫn: phần Access Requirements giới hạn học thuật/phi lợi nhuận và nói tác giả không sở hữu
bản quyền ảnh/metadata; phần License & Terms lại ghi CC BY 4.0, cho phép thương mại. Không suy ra quyền phân phối ảnh
chỉ từ nhãn license. Cần chủ dataset làm rõ phạm vi được phép trước khi đóng gói ảnh vào APK/repo hoặc dùng thương mại.
Không dùng mirror để vượt yêu cầu truy cập. Chưa gửi yêu cầu access, chưa chia sẻ thông tin tài khoản người dùng.

## Nếu sau này được cấp quyền phù hợp

1. Giữ revision gốc, license/access confirmation, image hash, original item ID và outfit IDs trong manifest riêng.
2. Dùng split **disjoint** để tránh cùng item xuất hiện ở train và test. Không trộn với nondisjoint.
3. Map category sang contract FitCheckAI; không tự suy diễn màu/chất liệu/mùa thành ground truth.
4. Đánh giá compatibility AUC/FITB ở Track B; đo import/render/error ở Track A riêng biệt.
5. Bổ sung cặp front/back được xác minh và ảnh tự chụp cho kiểm thử camera; không ghép hai item cùng loại thành một cặp.

Bộ CC0 hiện tại tiếp tục là nguồn độc lập cho Track A: <https://github.com/alexeygrigorev/clothing-dataset>.
Paper Polyvore: <https://arxiv.org/abs/1803.09196>.
