# eval/ — đo chất lượng AI (OWNER: Track B)

Cấu trúc dự kiến:
```
eval/
├─ images/          # 30–50 ảnh quần áo thật của nhóm (đã resize ≤ 768px)
├─ labels.json      # nhãn đúng: { "<file>": { "category": "TOP", "colors": ["#..."], "formality": 2 } }
├─ outfit-cases.json# 10 kịch bản: tủ đồ cố định × thời tiết × dịp
└─ run-eval.mjs     # gọi proxy, xuất độ chính xác theo field → results/<ngày>.md
```
Mục tiêu (xem [Track B](../docs/plans/20-track-b-ai.md)): category ≥ 90%, màu chính ≥ 80%, formality ±1 ≥ 85%.
Mỗi lần đổi prompt ⇒ chạy lại và commit kết quả vào `results/`.
