# D-007 — Bản ghi release mode

[Board](../TASK_BOARD.md) · [Card](D.md#d-007) · [Milestones](../DEPENDENCIES_AND_INTEGRATION.md) · [Gate C-205](C_205_FEASIBILITY_GATE.md)

**Owner:** Track D · **Ngày:** 12/11/2026 · **Commit wiring:** `5e6ddbc` trở đi

## Quyết định hiện hành: **CORE** (M5-CORE **chưa PASS**)

- C-205 ghi `DECISION = BLOCKED` → không có căn cứ bật FRONT/DUAL. App ship với VTO **unavailable trung thực**.
- Không đổi tên CORE thành "VTO hoàn tất"; M4/M5-FULL chưa đạt.

## Wiring đã có trong app

| Hạng mục | Vị trí | Bằng chứng |
|---|---|---|
| Module Try-On được build | `settings.gradle.kts`, `app/build.gradle.kts` | `:app:assembleDebug` pass |
| Route CT-03 | `FitCheckApp.kt` → `tryOnDestination` | `Routes.TRYON_PATTERN` |
| Chọn đúng 1 outfit, revision còn hiệu lực | `TodayViewModel.prepareTryOn` + `TryOnSelectionHandoff` | `TodayViewModelTest`: handoff đúng outfit, từ chối bộ stale |
| VTO tắt | `app/tryon/CoreModeVirtualTryOn.kt` | capability `UNKNOWN`, 0 view, `maxGarmentsPerJob = 0`; submit/cancel/delete trả lỗi, không I/O |
| Profile → Try-On không lộ dữ liệu thừa | `ProfileStorageTryOnPort` | chỉ id/revision/ảnh toàn thân; không chiều cao/cân nặng/style |
| Đường dẫn ảnh đã kiểm owner | `MediaStore.displayPath` | ref không resolve được bị bỏ, không đoán path |

## Checklist M5-CORE (theo DEPENDENCIES_AND_INTEGRATION)

| Điều kiện | Trạng thái | Owner / còn thiếu |
|---|---|---|
| M2 đạt (Room, media, profile thật) | Đạt trên emulator API 35 + Samsung API 36 (INT-001, A-004) | A |
| M3 live (recognition + recommendation qua proxy HTTPS) | **Chưa** — proxy chưa deploy HTTPS (INT-002 BLOCKED) | B |
| Profile → Wardrobe → Today thật | Đạt ở unit + QA thiết bị của A; **chưa** chạy lại sau wiring Try-On | D (cần thiết bị) |
| VTO unavailable đúng gate | Đạt ở code (`CoreModeVirtualTryOn`) | D |
| Real-store deletion/invalidation với late-result fixtures C-210 | **Chưa** chạy trên thiết bị. Ở CORE không có job nên không có kết quả trễ để chặn; vẫn phải kiểm khi bật live | D + C |

## Đổi sang FRONT/DUAL

1. C-205 ghi `GO_FRONT_ONLY`/`GO_DUAL` với 3 case đạt rubric.
2. C-212 (và C-211 cho DUAL) pass; B mount `/v2/tryon` vào proxy.
3. D thay binding `CoreModeVirtualTryOn()` trong `TryOnDestination.kt` bằng `AndroidVirtualTryOnClient`, nối `ProfileStorage.invalidations` → `TryOnPrivacyCoordinator.onPersonPhotoChangedOrDeleted`, chạy lại checklist trên và cập nhật bản ghi này.
