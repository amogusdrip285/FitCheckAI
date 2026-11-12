# Track C — Implementation Log & Verification Record

**Branch làm việc:** `lamb`
**Base HEAD:** `7d995a5` (`docs: align Sprint 0 scaffold with execution contracts v2`)
**Ngày cập nhật:** `2026-11-11` (Asia/Bangkok, UTC+7)
**Owner:** Track C — Virtual Try-On (`Photorealistic 2D Virtual Try-On using real user photographs`)

---

## 1. Môi trường & Toolchain đã kiểm chứng

| Hạng mục | Trạng thái thực tế | Bằng chứng / Ghi chú |
|---|---|---|
| Git branch & working tree | `lamb` (`git diff` trên tracked files rỗng tuyệt đối) | `HEAD -> lamb` trùng `origin/main` (`7d995a5`). Không tự commit/push/merge/rebase; không sửa bất kỳ file nào thuộc sở hữu Track A/B/D. |
| OS & Hardware | Ubuntu 24.04 x86_64, 31 GiB RAM, 223 GiB trống, `/dev/kvm` sẵn sàng | GPU: Intel Raptor Lake-P Integrated Graphics (**không có GPU NVIDIA/CUDA** → loại trừ chạy local `CatVTON`/`IDM-VTON`). |
| JDK & Gradle | OpenJDK `21.0.12.1`, Gradle `8.13` (`./gradlew`) | Baseline repo build/tests/lint + `:feature:tryon` unit tests/lint đều **PASS**. |
| Android SDK & Emulator | `/home/lamb/Android/Sdk` (`android-35`, `android-37.0`, `adb 37.0.1`) | Máy ảo `Pixel_6` (Android 15, API 35) chạy trực tiếp trên màn hình Desktop (`DISPLAY=:0`), đã cài sẵn ứng dụng Demo độc lập **`FitCheck VTO Demo (FAKE)`** (`TryOnDemoActivity`) và vượt qua `7/7` bài instrumentation Visual QA. |
| Node.js & npm (cho `proxy/`) | Node `v22.23.2`, npm `10.9.8` | Đường dẫn local runtime: `/home/lamb/Project/FocusLab/.tooling/node/bin`. `npm run typecheck` + `npm test` (`12/12` Vitest tests) **PASS**. |
| Python (cho POC `eval/tryon/`) | Python `3.12.3` | POC harness, local private folder ingestion (`--auto-manifest-from-folder`, `--private-in`) & unit suite (`6/6` tests **PASS**). |
| Live VTO Credentials & Budget | **CHƯA CẤP** (`FASHN_API_KEY` trống, `gcloud` chưa cấu hình) | Không tự chi tiền, không bật billing, không upload ảnh thật khi chưa có consent, budget duyệt và xác nhận quy chế pre-event hackathon (`PRE-001`). |

---

## 2. Đối chiếu Trạng thái Task theo đúng Acceptance Criteria (`DONE` / `PARTIAL` / `BLOCKED`)

> Nguyên tắc nghiệm thu nghiêm ngặt: **Tuyệt đối không đánh dấu `DONE` cho các task yêu cầu ảnh người thật, gọi live API, hoặc ghép nối liên track chỉ vì `FAKE` unit/UI tests đã PASS.**

| Task ID | Tên task | Priority | Phân loại nghiệm thu (`DONE` / `PARTIAL` / `BLOCKED`) | Đã hoàn thành (Code & Fake/Local Test) | Phần còn thiếu theo Acceptance Criteria |
|---|---|---|---|---|---|
| **C-201** | Khảo sát VTO provider, quyền dùng và privacy | P0 | **`PARTIAL`** (Tài liệu & ma trận `DONE`; phê duyệt tài khoản/ngân sách chờ User) | `C_201_PROVIDER_RESEARCH.md` đối chiếu chính thức FASHN AI (`tryon-v1.6` \$0.075/ảnh, `tryon-max` \$0.30/ảnh, `return_base64: true`) vs Vertex AI (`virtual-try-on-001`, ngừng hỗ trợ `2027-03-15`), loại trừ local GPU, đề xuất trần POC `25 credits` (~\$1.88). | Cần nhóm/người dùng chính thức phê duyệt trần ngân sách POC, xác nhận `PRE-001` và cấp `FASHN_API_KEY`. |
| **C-202** | Bộ ảnh người và garment có consent cho POC | P0 | **`BLOCKED`** (Tooling & synthetic fixtures `DONE`; dữ liệu người thật `BLOCKED`) | `C_202_DATASET_READINESS.md`, `technical_fixtures_manifest.json` (3 positive + 6 negative synthetic fixtures), hàm `build_manifest_from_private_folder` & `inspect_local_image_file` trong `poc_runner.py` đọc ảnh thật từ thư mục riêng ngoài Git. | Chưa có ≥ 3 bộ ảnh người thật toàn thân (trước/sau) + ảnh quần áo thực tế có biên bản đồng ý (consent) lưu ngoài Git. |
| **C-203** | POC front-view với một outfit được chọn | P0 | **`BLOCKED`** (Harness `DONE` ở `FAKE_ONLY`; chạy live `BLOCKED`) | `eval/tryon/poc_runner.py` + `test_poc_runner.py` (`6/6` tests **PASS**), kiểm tra schema, byte caps, kill-switch, xuất rubric 2 người chấm và tính p50/p95 latency + chi phí. | Chưa chạy live trên 3 bộ ảnh người thật qua FASHN API và chưa có điểm chấm độc lập của 2 reviewer. |
| **C-204** | POC back-view và consistency của cặp | P0 | **`BLOCKED`** (Harness `DONE` ở `FAKE_ONLY`; chạy live `BLOCKED`) | `poc_runner.py` phân biệt rõ `TWO_INDEPENDENT_REQUESTS` vs `NATIVE_PAIRED`, sequential multi-garment probe và trạng thái `PARTIAL` khi mặt sau lỗi. | Phụ thuộc `C-203` live pass + chạy thử nghiệm cặp trước/sau thực tế trên FASHN API. |
| **C-205** | Feasibility gate và quyết định phạm vi VTO | P0 | **`DONE` (ở trạng thái cổng `DECISION = BLOCKED`)** | `C_205_FEASIBILITY_GATE.md` ghi nhận đúng quy tắc: khi chưa có thực nghiệm live chỉ được ghi `DECISION = BLOCKED` (`TryOnCapability.decision = UNKNOWN`), không tự gắn `GO_*`. | Milestone `M4-F` chỉ chuyển sang `GO_FRONT_ONLY` hoặc `GO_DUAL` sau khi mở lại cổng bằng kết quả live của `C-203`/`C-204`. |
| **C-206** | Fake TryOnService và preview states | P0 | **`DONE`** | `FakeVirtualTryOnService.kt`, `FakeTryOnFixtures.kt`, `FakeVirtualTryOnServiceTest.kt` mô phỏng đủ terminal/partial/error/expired/missing-back, khóa idempotency 1 job/key, gắn nhãn `FAKE` rõ ràng. | Đã hoàn tất đầy đủ acceptance criteria độc lập của Track C. |
| **C-209** | Try-On screen cho outfit đã chọn | P1 | **`DONE` (trong phạm vi `:feature:tryon` + Demo APK độc lập)** | `TryOnScreen.kt`, `TryOnViewModel.kt`, `TryOnDemoActivity.kt`, `TryOnViewModelTest.kt`, `TryOnScreenVisualQaTest.kt` (`7/7` instrumentation tests trên `Pixel_6` API 35 **PASS**). Không tự chạy cho 5 outfit, không có thumbnail mặt sau giả, không báo `DUAL` hoàn tất khi thiếu mặt sau. | Nối route vào `FitCheckApp.kt` thuộc ownership của Track D (`S-002`, đã có bản vá sẵn tại `C_SHARED_CONTRACTS_PROPOSAL.md`). |
| **C-208** | Android VTO client và khôi phục job | P1 | **`DONE`** | `AndroidVirtualTryOnClient.kt`, `TryOnLocalJobStore.kt` (`AtomicFileTryOnLocalJobStore`), `AndroidVirtualTryOnClientTest.kt` — lưu `PENDING_SUBMISSION` trước khi gọi mạng, chặn gửi trùng sau process death, chuyển `UNKNOWN_SUBMISSION` khi đứt mạng lúc gửi, poll backoff `2s → 4s → 8s`. | Đã hoàn tất đầy đủ acceptance criteria của client & local job store. |
| **C-210** | Privacy gate và cleanup ảnh/job | P0 | **`PARTIAL`** (Logic & fake tests `DONE`; tích hợp real stores chờ `D-007`) | `TryOnPrivacyCoordinator.kt`, `SafeTryOnLogger.kt`, `TryOnPrivacyCoordinatorTest.kt` — `confirmedByUser = false` → 0 upload, rút consent/xóa ảnh tăng `inputGeneration` và chặn hiển thị kết quả trễ, lọc sạch đường dẫn/URL/base64 trong log. | Kiểm thử hồi quy với `ProfileStorage` thật của Track D (`D-007`) và `MediaStore` thật của Track A (`INT-001`) trước khi bật live. |
| **C-207** | Server adapter VTO và job idempotency | P1 | **`PARTIAL`** (Code & Vitest `DONE`; mount route & durable DB chờ Track B) | `proxy/src/tryon/{types,store,providers,handler}.ts` + `proxy/test/tryon.test.ts` (`8/8` Vitest tests **PASS**) — khóa idempotency nguyên tử, chặn đọc chéo session (`403`), chặn `AI_LIVE` nếu thiếu durable store (`store.isDurable === false`). | Cần Track B (`B-001`) mount `createTryOnRouter()` vào `proxy/src/index.ts`, cấp durable store (Cloudflare D1/KV) và mở cổng `GO_*`. |
| **D-002** | Chuyển evidence thành pitch có giới hạn | P1 | **`PARTIAL`** (Bản nháp & ma trận giới hạn `DONE`; chờ điền số đo live) | `C_D002_PITCH_SUPPORT.md` — kịch bản 5 phút, <= 5 slide, kịch bản demo 105 giây, bảng `Claim → Evidence → Limitation` không phóng đại. | Bổ sung số khảo sát `PRE-005` của nhóm và số đo latency/cost thực tế từ `C-203`/`C-204` khi có. |
| **C-211** | Đánh giá runtime hai mặt và nhiều món | P1 | **`BLOCKED`** | Chưa thực hiện (đúng quy định điều kiện nhận việc: bắt buộc có `GO_DUAL` + live runtime). | Phụ thuộc `GO_DUAL` từ `C-205` + server/client live + 3 bộ ảnh có consent. |
| **C-212** | Tích hợp và nghiệm thu VTO theo capability | P1 | **`BLOCKED`** | Đã chuẩn bị sẵn tài liệu đề xuất ghép nối `C_SHARED_CONTRACTS_PROPOSAL.md`. | Phụ thuộc `GO_FRONT_ONLY`/`GO_DUAL`, `INT-001` (Track A/D real stores) và live provider. |
| **C-213** | Tối ưu nhiều món và ổn định góc nhìn | P2 | **`BLOCKED`** | Giữ đóng theo kỷ luật P2. | Phụ thuộc `C-212` hoàn tất nghiệm thu runtime. |

---

## 3. Hồ sơ Kiểm thử & Interactive Demo trên Thiết bị ảo (`Pixel_6` API 35)

1. **Android `:feature:tryon` JVM Unit Tests & Lint**:
   - `ANDROID_HOME=/home/lamb/Android/Sdk ./gradlew -I .../tryon_include.init.gradle.kts :feature:tryon:testDebugUnitTest :feature:tryon:lintDebug --console=plain` → `BUILD SUCCESSFUL` (`13/13` unit tests **PASS**, `lintDebug` `0` lỗi).
2. **Android `:feature:tryon` Instrumentation Tests & Standalone Interactive Demo (`TryOnDemoActivity`)**:
   - `7/7` bài instrumentation test trong `TryOnScreenVisualQaTest` (bao gồm `interactiveDemoWorkbenchAndLocalPngFlow`) đạt **PASS** trên `Pixel_6` (Android 15, API 35).
   - Đã cài đặt APK độc lập `tryon-debug-androidTest.apk` (`com.fitcheckai.feature.tryon.test`) với `targetSdk = 35` và mở sẵn `com.fitcheckai.feature.tryon.demo.TryOnDemoActivity` (**"FitCheck VTO Demo (FAKE)"**) trực tiếp trên cửa sổ máy ảo `Pixel_6`.
   - 12 ảnh chụp màn hình kiểm chứng trực quan được lưu ngoài Git tại `/home/lamb/.gemini/antigravity/brain/cb920fe9-1c24-4143-98ab-87a249d30e9b/scratch/visual_qa_screenshots/`.
3. **Proxy Server Typecheck & Vitest Suite**:
   - `cd proxy && npm run typecheck && npm test` → `12/12` Vitest tests **PASS**.
4. **Python POC Harness & Local Private Folder Ingestion Tests**:
   - `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s eval/tryon -p "test_*.py" -v` → `6/6` tests **PASS**.

---

## 4. Danh mục toàn bộ File thuộc sở hữu Track C đã tạo mới (36 files)

- `docs/execution/tracks/C_IMPLEMENTATION_LOG.md`
- `docs/execution/tracks/C_SHARED_CONTRACTS_PROPOSAL.md`
- `docs/execution/tracks/C_201_PROVIDER_RESEARCH.md`
- `docs/execution/tracks/C_202_DATASET_READINESS.md`
- `docs/execution/tracks/C_205_FEASIBILITY_GATE.md`
- `docs/execution/tracks/C_D002_PITCH_SUPPORT.md`
- `eval/tryon/.gitignore`
- `eval/tryon/fixtures/technical_fixtures_manifest.json`
- `eval/tryon/poc_runner.py`
- `eval/tryon/test_poc_runner.py`
- `proxy/src/tryon/types.ts`
- `proxy/src/tryon/store.ts`
- `proxy/src/tryon/providers.ts`
- `proxy/src/tryon/handler.ts`
- `proxy/test/tryon.test.ts`
- `feature/tryon/build.gradle.kts`
- `feature/tryon/src/main/AndroidManifest.xml`
- `feature/tryon/src/androidTest/AndroidManifest.xml`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/contract/TryOnContracts.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/contract/Sprint0ModelInterop.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/privacy/SafeTryOnLogger.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/privacy/TryOnPrivacyCoordinator.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/fake/FakeTryOnFixtures.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/fake/FakeVirtualTryOnService.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/client/TryOnLocalJobStore.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/client/AndroidVirtualTryOnClient.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnViewModel.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnScreen.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnNavigation.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnPreviews.kt`
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/demo/TryOnDemoActivity.kt`
- `feature/tryon/src/test/kotlin/com/fitcheckai/feature/tryon/fake/FakeVirtualTryOnServiceTest.kt`
- `feature/tryon/src/test/kotlin/com/fitcheckai/feature/tryon/privacy/TryOnPrivacyCoordinatorTest.kt`
- `feature/tryon/src/test/kotlin/com/fitcheckai/feature/tryon/client/AndroidVirtualTryOnClientTest.kt`
- `feature/tryon/src/test/kotlin/com/fitcheckai/feature/tryon/ui/TryOnViewModelTest.kt`
- `feature/tryon/src/androidTest/kotlin/com/fitcheckai/feature/tryon/TryOnScreenVisualQaTest.kt`

---

## 5. Cập nhật Giai đoạn 2 (`2026-11-11`): Hoàn thiện Khung Tích hợp OpenAI Virtual Try-On End-to-End (`OFFLINE/MOCK PASS` • `LIVE NOT TESTED`)

### 5.1. Quyết định kỹ thuật & Phạm vi thực hiện
- Chốt hướng tích hợp chính thức: **OpenAI Images Edit API (`POST /v1/images/edits`)** dùng multipart `image[]` (Ảnh 1 = người thật mặt trước, Ảnh 2 = trang phục mặt trước), trả `b64_json`, mặc định `FRONT_ONLY` (`maxGarmentsPerJob = 1`).
- Khóa cứng `TRYON_LIVE_ENABLED=false` mặc định ở mọi tầng (`$0.00` phát sinh; không dùng Codex credits thay cho OpenAI API credits).
- Giữ nguyên giao diện hiện tại của `TryOnScreen` theo đúng chỉ thị không redesign UI; chỉ bổ sung phân biệt trung thực `FAKE / MOCK` vs `AI LIVE (OpenAI)` và hiển thị `ContentScale.Fit` để không cắt đầu/trang phục ở tỷ lệ `1024x1536`.

### 5.2. Các thành phần mới & nâng cấp đã triển khai (8 files mới + cập nhật Track C)
- `proxy/src/tryon/openai_prompt.ts`: Prompt có phiên bản `openai-vto-edit-v1.0`, ràng buộc giữ khuôn mặt/dáng đứng/nền/chữ in/logo và danh mục giới hạn kỹ thuật trung thực.
- `proxy/src/tryon/openai_provider.ts`: `OpenAITryOnProvider` thật (`POST /v1/images/edits`), kiểm chứng catalog model GPT Image (`gpt-image-2`, `gpt-image-1.5`, `gpt-image-1-mini`, các bản snapshot `2.6`/`2.5`), chặn model ngừng hỗ trợ (`gpt-image-1`), xử lý `input_fidelity` theo từng dòng model, ước tính USD theo token `usage`, ẩn khóa `sk-...`, ánh xạ lỗi `INSUFFICIENT_QUOTA`/`UNAUTHORIZED`/`FORBIDDEN`/`RATE_LIMITED`/`INVALID_AI_OUTPUT` và chặn retry mù khi đứt mạng (`UNKNOWN_SUBMISSION`).
- `proxy/src/tryon/openai_mock.ts`: Mock trung thực cho `/v1/images/edits` (kiểm tra `multipart/form-data`, `image[]` >= 2, `Authorization: Bearer`, trả PNG RGB hợp lệ qua `b64_json` + `usage` và 11 kịch bản lỗi).
- `proxy/src/tryon/image_pipeline.ts`: Kiểm tra magic bytes JPEG/PNG/WebP, kích thước pixel và đọc tag TIFF EXIF Orientation `0x0112` thuần `Uint8Array`.
- `proxy/src/tryon/store.ts` & `proxy/scripts/tryon_local_server.ts` & `proxy/scripts/tryon.env.example`: Sổ cái bền vững `JsonLedgerTryOnJobStore` (`reserveLiveSpendAtomically`) + máy chủ HTTP cục bộ lắng nghe độc quyền `127.0.0.1:8788` (máy ảo Android kết nối qua `http://10.0.2.2:8788`), tự hot-reload `~/.fitcheckai_private/tryon.env`.
- `proxy/test/openai_tryon.test.ts`: 9 bộ kiểm thử chuyên sâu cho OpenAI adapter, LIVE gates, cost guard, idempotency và privacy redaction (`21/21` Vitest tests **PASS**).
- `eval/tryon/poc_runner.py` & `eval/tryon/test_poc_runner.py`: Bổ sung `OpenAiImageEditProviderAdapter`, kiểm tra EXIF JPEG và tiêu chí chấm `logoPreservation` / `textFidelity` (`7/7` Python tests **PASS**).
- `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/client/LocalDiskTryOnMediaStore.kt` & `LocalDiskTryOnMediaStoreTest.kt`: Kiểm tra magic bytes, chuẩn hóa xoay ảnh EXIF `0x0112` và thu phóng không méo tỷ lệ sang tệp upload tạm mà không ghi đè ảnh gốc hoặc xóa nền áo; lưu kết quả PNG vào vùng riêng `filesDir/tryon_results/`.
- `AndroidVirtualTryOnClient.kt`, `TryOnDemoActivity.kt`, `src/debug/AndroidManifest.xml`, `src/debug/res/xml/tryon_debug_network_security_config.xml`, `TryOnScreenVisualQaTest.kt`: Cấp hydration tức thời (`0ms` delay) cho provider đồng bộ như OpenAI, mở cleartext chỉ riêng `10.0.2.2`/`127.0.0.1` trên bản `debug`/`androidTest`, và kiểm thử E2E thực tế trên máy ảo `Pixel_6` (`17/17` Android unit tests + `9/9` instrumentation tests trên `Pixel_6` + `lintDebug` **PASS**).
- `docs/execution/tracks/C_OPENAI_LIVE_POC_RUNBOOK.md`: Runbook kiểm tra 10 điểm và kích hoạt 1 lượt thử LIVE có kiểm soát.

---

## 6. Cập nhật Giai đoạn 3 (`2026-11-12`): Tích hợp FOSTA Luna (`gpt-6-luna`) Virtual Try-On v2 Song Song OpenAI (`OFFLINE/MOCK PASS` • `FRONT+TOP CLI POC` • `BACK/BOTTOM/TOP+BOTTOM EXPERIMENTAL`)

### 6.1. Phạm vi & Quyết định Kiến trúc
- Giữ nguyên `OpenAITryOnProvider` (`TRYON_PROVIDER=openai`, `POST /v1/images/edits`) làm nhánh chính thức, toàn bộ kiểm thử OpenAI cũ tiếp tục **PASS**.
- Bổ sung `FostaTryOnProvider` (`TRYON_PROVIDER=fosta`, `providerId = "fosta"`, `POST https://codex.fosta.app/v1/responses`, model `gpt-6-luna`, ép công cụ `image_generation`) theo đúng bộ mã `fitcheck_fosta_vto_v2_integration_kit.zip`.
- Hỗ trợ 2 chế độ trình bày (`TryOnPresentationMode.ORIGINAL_PHOTO`, `TryOnPresentationMode.STUDIO_WHITE`), 3 tổ hợp trang phục (`TOP`, `BOTTOM`, `TOP+BOTTOM` trong cùng 1 ảnh kết quả), và 2 góc nhìn (`FRONT`, `BACK` với yêu cầu ảnh mặt sau người + trang phục thật, chặn nhân bản hash mặt trước).
- Cổng an toàn: `TRYON_LIVE_ENABLED=false` mặc định, yêu cầu thêm `FOSTA_APPROVE_QUOTA=true`, giới hạn cứng `TRYON_LIVE_MAX_REQUESTS=1` qua sổ cái bền vững, consent gắn chặt với `providerId = "fosta"`, không retry khi timeout (`UNKNOWN_SUBMISSION`) và không tự động fallback sang OpenAI.

### 6.2. Các tệp tin mới & cập nhật
- `proxy/src/tryon/fosta_prompt.ts`: Ma trận 12 nhánh prompt (`FITCHECK_VTO_PROMPT_VERSION=v2.0` cho `2 modes × 2 views × 3 garment combos`), quy tắc giữ nguyên món đồ không chọn và ánh xạ danh mục `TOP`/`BOTTOM`.
- `proxy/src/tryon/fosta_provider.ts`: Adapter `FostaTryOnProvider` tuần tự hóa cố định `input_text` -> `PERSON` -> `TOP` -> `BOTTOM`, kiểm tra magic bytes đầu vào/đầu ra, ẩn API key/URL và ánh xạ lỗi chuẩn.
- `proxy/src/tryon/fosta_mock.ts`: Mock `/v1/responses` trung thực tạo ảnh PNG phân biệt rõ `ORIGINAL_PHOTO` vs `STUDIO_WHITE` và `TOP`/`BOTTOM`/`TOP+BOTTOM` cùng 11 kịch bản lỗi.
- `proxy/src/tryon/handler.ts`, `proxy/src/tryon/types.ts`, `proxy/scripts/tryon_local_server.ts`, `proxy/scripts/tryon.env.example`, `proxy/test/fosta_tryon.test.ts`: Điều phối đa provider (`openai` | `fosta`), kiểm tra 12 nhánh prompt riêng biệt và luồng E2E (`41/41` Vitest tests **PASS**).
- `TryOnContracts.kt`, `FakeTryOnFixtures.kt`, `TryOnPrivacyCoordinator.kt`, `AndroidVirtualTryOnClient.kt`, `FakeVirtualTryOnService.kt`, `TryOnViewModel.kt`, `TryOnScreen.kt`, `TryOnDemoActivity.kt`: Bổ sung `TryOnPresentationMode`, `TryOnViewMode.BACK_ONLY`, `TryOnGarmentCombo`, nhãn `THỬ NGHIỆM (EXPERIMENTAL)` cho `BACK`/`BOTTOM`/`TOP+BOTTOM`, nhãn `AI LIVE — FOSTA (Proxy thử nghiệm)`, và chế độ `Backend FOSTA Luna (10.0.2.2:8788)` hỗ trợ 6 khe ảnh (`person_front/back`, `top_front/back`, `bottom_front/back`).
- `TryOnViewModelTest.kt`, `TryOnPrivacyCoordinatorTest.kt`, `AndroidVirtualTryOnClientTest.kt`, `TryOnScreenVisualQaTest.kt`: Kiểm thử đơn vị và kiểm thử E2E trên máy ảo `Pixel_6` cho cả `BACKEND_FOSTA_E2E` và `BACKEND_OPENAI_E2E` (`21/21` Android unit tests + `10/10` instrumentation tests trên `Pixel_6` **PASS**).
- `docs/execution/tracks/C_FOSTA_LIVE_POC_RUNBOOK.md`: Hướng dẫn cấu hình và thao tác chạy FOSTA LIVE trên Android Emulator.


---

## 7. Cập nhật Giai đoạn 4 (`2026-11-12`): Finalization — Đồng bộ UI với Track A, Bố cục Image-First 1→8 & Audit Tương thích Nhóm (`origin/main`)

### 7.1. Đồng bộ ngôn ngữ thiết kế với Track A (`WardrobeDesign.kt`) & Bố cục ưu tiên hình ảnh
- Cập nhật `feature/tryon/src/main/kotlin/com/fitcheckai/feature/tryon/ui/TryOnScreen.kt`:
  - Sử dụng hệ thống panel ấm áp đồng bộ với `WardrobeDesign.kt` của Track A: `Surface` bo góc `RoundedCornerShape(22.dp)`, nền `editorialPanelContainerColor()` (`#FFFEFB` sáng / `#22251F` tối), viền `outlineVariant.copy(alpha = 0.7f)`, padding `18.dp`.
  - Chip lựa chọn dạng viên thuốc (`RoundedCornerShape(50)`), nút hành động chính bo góc `RoundedCornerShape(16.dp)` (`heightIn(min = 54.dp)`), khung ảnh tỷ lệ `3:4` bo góc `RoundedCornerShape(20.dp)` với `ContentScale.Fit`.
  - Sắp xếp luồng nhập liệu theo đúng thứ tự ưu tiên hình ảnh (Image-First 1→8):
    1. **Ảnh người dùng (`PersonPhotosPanel`)**: Hiển thị 2 khung ảnh mẫu `Mặt trước` và `Mặt sau` tỷ lệ `3:4` rõ ràng.
    2. **Ảnh trang phục đã chọn (`GarmentThumbnailCard`)**: Thay chip chữ đen bằng thẻ ảnh thumbnail thực tế của từng món đồ (tự động đổi ảnh mặt trước/mặt sau theo góc nhìn đang chọn), kèm chấm màu `accentHex`, nhãn danh mục và biểu tượng chọn.
    3. **Chọn tổ hợp thay đồ**: `Chỉ thay Áo (TOP)`, `Chỉ thay Quần (BOTTOM)`, `Cả Áo + Quần (TOP+BOTTOM)`.
    4. **Chọn chế độ khung hình**: `Ảnh gốc (ORIGINAL_PHOTO)` vs `Nền trắng Studio (STUDIO_WHITE)`.
    5. **Chọn góc nhìn**: `Chỉ mặt trước` vs `Chỉ mặt sau` (kèm cảnh báo khi thiếu ảnh mặt sau và nhãn `THỬ NGHIỆM (EXPERIMENTAL)`).
    6. **Xác nhận quyền xử lý ảnh**: Tóm tắt ngắn gọn số lượng ảnh gửi đi + điều khoản bảo mật theo provider.
    7. **Nút tạo ảnh**: `Tạo ảnh thử đồ`.
    8. **Khu vực kết quả (`ResultShowcaseSection`)**: Tự động cuộn lên đầu màn hình khi bắt đầu hoặc hoàn tất xử lý; hiển thị ảnh `3:4` lớn (`ContentScale.Fit`), nút `So sánh ảnh gốc`, `Thử lại / Tạo lượt mới`, `Xóa kết quả & ảnh tạm` và nhãn phân biệt rõ `FAKE • OFFLINE MOCK` / `AI LIVE — FOSTA` / `AI LIVE (OpenAI)`.

### 7.2. Audit Xung đột Git & Khả năng Tích hợp với `origin/main` (`3666ef5`) và `origin/track-b/B-003-B-007-android-clients` (`752c315`)
- **Kiểm tra va chạm đường dẫn file (`git cat-file -e`)**: **0 file xung đột** giữa toàn bộ 50 tệp tin mới của Track C với `origin/main` (`3666ef5`, đã gộp Track A `track/a-wardrobe-imaging` và Track D `track-d/S-002-contracts-profile`) cũng như nhánh `origin/track-b/B-003-B-007-android-clients` (`752c315`).
- **Kiểm tra trạng thái tracked files**: `0` file bị sửa đổi ngoài phạm vi Track C (`git diff` rỗng tuyệt đối).
- **Hướng dẫn tích hợp nhanh cho Track D / Track B**:
  1. Thêm `include(":feature:tryon")` vào `settings.gradle.kts` và `implementation(project(":feature:tryon"))` vào `app/build.gradle.kts`.
  2. Đăng ký route `Routes.TRYON_PATTERN` (`"tryon/{outfitId}?snapshotId={snapshotId}"` đã có sẵn trong `origin/main:core/model/src/main/kotlin/com/fitcheckai/core/model/nav/Routes.kt`) trong `FitCheckApp.kt` gọi `TryOnRoute(...)`.
  3. Mount `createTryOnRouter()` từ `proxy/src/tryon/handler.ts` vào `proxy/src/app.ts` / `proxy/src/index.ts` dưới tiền tố `/v2/tryon/*`.
