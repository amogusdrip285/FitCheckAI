# INT-004 — Đóng gói APK và kiểm tra bản nộp

[Board](../TASK_BOARD.md) · [Card](D.md#int-004) · [Release mode](D_007_RELEASE_MODE.md)

**Owner:** Track D · **Phụ thuộc:** INT-003 (chưa đạt). Tài liệu này chuẩn bị sẵn quy trình; chưa có artifact nộp.

## 1. Đóng gói

Trên máy build chính thức (có `fitcheck.proxyUrl` và `fitcheck.accessCode` trong `~/.gradle/gradle.properties`, **không** commit):

```bash
git switch main && git pull --ff-only
python tools/release/package_apk.py --mode CORE
```

Script:
- từ chối khi working tree bẩn (trừ `--allow-dirty`, không dùng cho bản nộp);
- build `:app:assembleDebug` (giữ debug signing đã test, không đổi signing sát giờ);
- **từ chối đóng gói** nếu APK chứa giá trị giống provider key (OpenAI/Google/Anthropic/FASHN/PEM);
- ghi `dist/FitCheckAI-<version>-<commit7>-<MODE>.apk`, `.sha256` và `.json` (commit, mode, thời điểm, kết quả scan, có/không có access code demo — không ghi giá trị).

Kiểm thử script: `python -m unittest discover -s tools/release -p "test_*.py"`.

Chạy thử 12/11/2026 trên APK commit `5e6ddbc`: scan **clean**, `demoAccessCodeCompiledIn = false` → bản build trên máy này **không gọi được AI live**; bản nộp phải build trên máy có access code.

## 2. Smoke cài đặt (bắt buộc, trên chính file trong `dist/`)

```bash
sha256sum -c dist/FitCheckAI-*.sha256
adb install -r dist/FitCheckAI-<...>.apk
```

| Bước | Người | Kết quả |
|---|---|---|
| Cài + mở app, không crash | D | |
| Force-stop, mở lại giữ tủ đồ/hồ sơ | A | |
| Today gọi AI live thành công (nhãn *Gợi ý bởi AI*) | B | |
| Try-On báo "chưa bật" (CORE) | D | |
| Token/phiên demo còn hạn trong khung demo | B | |

## 3. README cài đặt kèm bản nộp

> **FitCheck AI — bản `<version>` (`<commit7>`), chế độ CORE**
> 1. Bật "Cài ứng dụng không rõ nguồn" cho trình quản lý file.
> 2. Cài `FitCheckAI-<...>.apk`. SHA-256: `<sha256>`.
> 3. Android 8.0+ (minSdk 26); đã kiểm trên emulator API 35 và Samsung API 36.
> 4. Cần Internet để nhận gợi ý AI; khi offline app vẫn phối cơ bản (gắn nhãn *Phối cơ bản*).

## 4. Disclosure (dán vào bài nộp)

- Gợi ý outfit và nhận diện món đồ dùng mô hình AI bên thứ ba qua proxy của nhóm; ảnh món đồ chỉ gửi khi người dùng đồng ý. Ảnh toàn thân và cân nặng **không** được gửi cho gợi ý outfit.
- **Thử đồ ảo chưa bật** trong bản này (C-205 BLOCKED). Mọi ảnh thử đồ trong slide/video (nếu có) là bản thử nghiệm, không phải tính năng của APK.
- Gợi ý không đảm bảo vừa size/độ vừa người.
- Dữ liệu (tủ đồ, hồ sơ, ảnh) lưu cục bộ trên máy; xóa trong app sẽ xóa file.
- Ghi rõ phần có sẵn trước sự kiện và phần làm mới theo thể lệ BTC (PRE-001).
