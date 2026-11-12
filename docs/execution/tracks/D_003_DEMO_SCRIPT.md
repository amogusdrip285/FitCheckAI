# D-003 — Kịch bản demo và bàn giao nộp bài

[Board](../TASK_BOARD.md) · [Card](D.md#d-003) · [Pitch C (D-002)](C_D002_PITCH_SUPPORT.md) · [Gói nộp](D_INT004_SUBMISSION.md)

**Owner:** Track D · **Chế độ demo:** CORE (theo [D-007](D_007_RELEASE_MODE.md)). Chưa diễn tập; điền bảng §3 sau dry run.

## 1. Vai trò

| Vai | Người | Việc |
|---|---|---|
| Thuyết trình | D | Dẫn chuyện, giữ giờ |
| Cầm máy | A | Thao tác app trên máy chính |
| Theo dõi kết nối | B | Kiểm proxy/phiên demo trước giờ, chuyển hotspot nếu cần |
| Hỗ trợ điều hướng | C | Trả lời câu hỏi VTO, đọc giới hạn |

## 2. Kịch bản app (~3 phút, đúng artifact trong `dist/`)

| # | Thao tác | Câu nói chính | Dự phòng |
|---|---|---|---|
| 1 | Mở app → Hồ sơ đã có sẵn (style, ảnh tùy chọn) | "Mọi trường hồ sơ đều tùy chọn; cân nặng và ảnh người không dùng để phối." | — |
| 2 | Tủ đồ → Thêm món, chụp/ chọn ảnh mặt trước, AI điền loại/màu | "AI chỉ gợi ý thuộc tính, người dùng sửa được." | Nếu AI chậm: nhập tay, nói rõ |
| 3 | Today → chọn dịp *Đi làm* → 1–5 bộ có ảnh + giải thích | "Mỗi bộ dùng món thật trong tủ, có lý do." | Offline: bộ *Phối cơ bản*, nói đúng nhãn |
| 4 | Tùy chỉnh: style *Minimal*, "trời mưa", 3 bộ → Gợi ý theo yêu cầu | "Chỉ gọi AI khi bấm." | — |
| 5 | Sửa một món trong tủ → quay lại Today: bộ cũ biến mất | "Không bao giờ gợi ý dựa trên dữ liệu cũ." | — |
| 6 | Thử đồ với bộ này → màn Try-On báo chưa bật | "Thử đồ ảo đang ở cổng kiểm định; bản này không giả kết quả." | — |

## 3. Kiểm tra trước giờ

- [ ] Đúng file `dist/FitCheckAI-…-CORE.apk`, SHA-256 khớp, đã cài trên máy chính.
- [ ] Phiên demo/proxy còn hạn; gọi thử Today 1 lần thành công.
- [ ] Tủ đồ demo ≥ 6 món (fixtures A), hồ sơ demo không chứa dữ liệu cá nhân thật.
- [ ] Pin > 60%, tắt thông báo, độ sáng cao.
- [ ] Dry run tính giờ: ___ phút (giới hạn BTC: ___).

## 4. Không được nói

- Không gọi CORE là "thử đồ ảo hoàn chỉnh"; không dùng ảnh pre-render như kết quả live.
- Không claim đúng size/độ vừa; không claim độ chính xác ngoài số trong [kết quả eval](../../../eval/ai/results/2026-11-12-luna-summary.md).

## 5. Bàn giao

- [ ] Nộp theo lịch BTC, lưu receipt (ảnh chụp/email). **Chưa có receipt thì không đánh dấu đã nộp.**
- [ ] Ghi link artifact, SHA-256, commit, mode vào board.
