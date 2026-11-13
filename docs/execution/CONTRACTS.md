# Contracts v2 — local-first, nhiều outfit và Virtual Try-On

[Workflow](../../TEAM_WORKFLOW.md) · [Board](TASK_BOARD.md) · [Gate VTO](VTO_FEASIBILITY.md)

Spec để triển khai, **chưa phải application code**. PRE-007 chốt version; S-002 khai báo types/wiring. Quyết định sản phẩm trong yêu cầu mới có ưu tiên hơn draft cũ. Những giới hạn kỹ thuật dưới đây là mặc định đề xuất để nhóm review, không khẳng định provider đã hỗ trợ. Không dùng contracts v1 song song trong active task.

## CT-00 — ownership và kiến trúc

Một Android `:app`, Kotlin/Compose, manual DI và interfaces; chỉ tách Gradle modules khi có lợi ích đo được. Room từ đầu cho wardrobe. Profile nhỏ dùng typed atomic file qua ProfileStorage để D không đợi schema A; cùng serialization stack, không thêm framework storage chỉ cho một object. Ảnh là files trong private app storage. Không account cloud/sync engine/analytics nền trong core.

| File/package dự kiến | Owner | Boundary |
|---|---|---|
| `wardrobe/**`, `media/**`, Room schema/database | A | Garment CRUD, import/normalize/media lifecycle dùng chung; không sở hữu profile metadata |
| `wardrobe/presentation/OutfitVisual`, ItemThumbnail | A | Component visual cho D, callbacks thuần; không NavHost/provider |
| `ai/**`, `proxy/recognition/**`, `proxy/outfits/**`, `proxy/shared/**`, `eval/ai/**` | B | Recognition/recommendation/weather; API key/quota và proxy bootstrap |
| `tryon/**`, `proxy/tryon/**`, `eval/tryon/**` | C | Job client/server, privacy coordinator, Try-On screen/result và job metadata |
| `profile/**`, `today/**`, `bootstrap/**`, `ui/theme/**`, NavHost | D | Onboarding/profile/Today, route/wiring, app lifecycle |
| `contracts/**`, Gradle/version catalog, Manifest, backup rules, root resources | D | Thay qua một owner; A/B/C gửi patch đề xuất và review consumer |
| Proxy entry/config/routes/auth envelope | B | C export handler, B mount; C không sửa root proxy song song |
| `docs/execution/` | D điều phối | Card owner đề xuất nội dung; status board một nơi; không để D viết/sửa mọi báo cáo |

D có quyền điều phối, không có nghĩa phải sửa bug của A/B/C. Tests theo package thuộc owner package. File root thay qua request ngắn: task ID, reason, schema before/after, consumer và test.

## Tương thích với Sprint 0

Mã nguồn commit `314e237` có `ClothingItem.imagePath`, `StyleProfile`, `OutfitSource.AI`, service v1 và proxy `/v1/*`; đây **không** phải cách triển khai v2. D chốt shared type/interface migration; A xử lý wardrobe/media; B xử lý reco/recognition/proxy; C xử lý TryOn. Không tạo contract v2 riêng trong feature module. Xem [mapping thực tế và ranh giới ownership](SPRINT0_TO_V2_ALIGNMENT.md).

## CT-01 — ID, media, ClothingItem và WardrobeRepository

Tất cả domain entity dùng UUID string do client sinh; không dùng Room auto-increment hoặc absolute path làm identity. Có `revision: Long >=1`, `createdAt/updatedAt: epochMillis UTC`, `deletedAt: epochMillis?`. Revision tăng khi nội dung/ảnh đổi, check optimistic revision khi save. Timestamp không tự giải quyết conflict cloud; cloud sync là A-201, chưa triển khai. Tombstone tối thiểu giữ ID/time khi xóa; nội dung/ảnh cá nhân phải xóa theo privacy, không giữ PII chỉ để chờ sync.

**MediaRef**: `id`, `ownerEntityId`, `scope=GARMENT|PERSON|TRYON_RESULT`, `view=FRONT|BACK`, `localRelativePath`, `mimeType`, `width`, `height`, `byteCount`, `contentHash`, `revision`, `derivedFromMediaId?`. Hash/local path không phải remote URL hoặc auth token. Không wire domain `localRelativePath` tới provider. Front/back là ảnh thật khác nhau, không clone hoặc suy ra ảnh còn thiếu. **ImagePair**: `front: MediaRef?`, `back: MediaRef?`; required rules theo context, không default ảnh giả.

**ClothingAttributes**: `category: TOP|BOTTOM|DRESS|OUTERWEAR|SHOES|ACCESSORY|UNKNOWN`, `colors: List<BLACK|WHITE|GRAY|BEIGE|BROWN|BLUE|GREEN|RED|YELLOW|PINK|PURPLE|OTHER>` tối đa 3, `pattern: SOLID|STRIPED|CHECKED|PRINTED|OTHER|UNKNOWN`, `styleTags: List<String>` tối đa 5×32 ký tự. `material: String?`, `formality: Int?` 1–5, `seasonTags: List<String>` optional quality fields; thiếu bằng chứng để null/empty, không ép model đoán. Tách `attributeSources: Map<field,MANUAL|AI>` và `needsReviewFields` để sửa không mất provenance.

`garmentType: String?` là mã loại chi tiết thuộc `category`, mặc định `null` khi đọc dữ liệu cũ. Tag có provenance và review theo field `GARMENT_TYPE`. Các mã và nhóm dùng chung nằm trong [từ điển loại đồ](../../data/sources/garment-types.json). Slot phối đồ tiếp tục dựa vào `category`.

**ClothingItem**: các identity/revision/timestamps trên + `images: ImagePair`, `attributes: ClothingAttributes`, `recognitionState: NOT_REQUESTED|PENDING|REVIEW|CONFIRMED|FAILED`. Front bắt buộc khi save item active; back tùy chọn. Category UNKNOWN có thể lưu nháp nhưng không được recommendation dùng như TOP. Không wearCount/lastWornAt trong contract active.

**WardrobeRepository (A)**:

| Operation | Input → Output |
|---|---|
| `observeItems()` | Flow<LoadState<List<ClothingItem>>>; empty khác lỗi đọc |
| `getSnapshot()` | Result<WardrobeSnapshot>; snapshotId và item IDs/revisions, không item deleted |
| `get(id)` | Result<ClothingItem?> |
| `upsert(item, expectedRevision?)` | Result<ClothingItem>; Conflict khi revision cũ, transaction metadata |
| `delete(id, expectedRevision)` | Result<DeletionReceipt>; hide/tombstone rồi cleanup refs, event invalidation |

**MediaStore (A)**: `import(uri, scope, ownerId, view) → Result<MediaRef>`; `read(ref) → Result<bytes/stream>`; `remove(ref) → Result<DeletionReceipt>`; `derive(ref, preset) → Result<MediaRef>`. Không xóa ảnh mới trước DB commit. Sau DB commit dọn ảnh cũ qua cleanup journal/retry; rollback thì dọn file mới. Readers phát hiện missing file rõ, không wipe DB. JPEG/PNG, decode sampling/off main, normalize orientation, strip EXIF; original ≤2048px cạnh dài, ≤4MiB sau xử lý mặc định. Upload derivative theo CT-07; segmentation derivative tách khỏi original. Person/result files không nằm public gallery, excluded backups qua D.

A-005 fake repository/media cung cấp success/IO/conflict/missing-back/deleted fixtures; A-001/002 real đổi qua interface, không bắt D/C chờ implementation.

## CT-04 — UserProfile và ProfileStorage

**UserProfile**: `id/revision/createdAt/updatedAt`, `heightCm: Double?`, `weightKg: Double?`, `stylePreferences: List<String>` tối đa 5×32 ký tự, `preferredCategories: Set<Category>`, `cityKey: String?`, `bodyImages: ImagePair`, `onboardingCompleted: Boolean`. Mọi thông tin người dùng có thể skip; cân nặng không bắt buộc, không suy ra từ ảnh. Numeric field phải finite/positive khi có và có đơn vị rõ; không chẩn đoán vóc dáng/sức khỏe hoặc xác định size từ số đo này. Không yêu cầu tên thật, email, ngày sinh, giới tính hoặc body measurements khác trong core.

**ProfileStorage (D)**: `observeProfile() → Flow<LoadState<UserProfile?>>`; `save(profile, expectedRevision) → Result<UserProfile>`; `replaceBodyPhoto(view, ref?, expectedRevision) → Result<UserProfile>`; `clearProfile() → Result<DeletionReceipt>`. Xóa ảnh phải cập nhật refs/revision, emit invalidation cho TryOnService, rồi dọn originals/derivatives/results gắn input đã xóa. Save profile/ảnh không tạo network call. Consent upload không lưu một boolean vĩnh viễn trong profile: tạo riêng ConsentGrant theo provider/purpose/request, có version và revokedAt. D-006 fake có minimal/full/missing-back/save-fail.

Default projection cho recommendation chỉ `heightCm?`, styles, preferredCategories; không gửi cân nặng, body images, profile path/UUID người dùng cho LLM nếu chưa có mục đích được đồng ý riêng. City dùng weather resolver, không phải precise GPS bắt buộc.

## CT-05 — Clothing Recognition

**RecognitionRequest**: `schemaVersion=2`, `requestId`, `draftId/itemId`, `inputRevision`, `front: GarmentImageInput`, `back?: GarmentImageInput`, `consentGrant` cho upload garment. ImageInput trên wire chỉ bytes/ephemeral reference được server cho phép, mime/view/mediaId/hash; không nhận arbitrary public URL từ client. Payload limits CT-07. Provider không nhận hai ảnh: capability báo FRONT_ONLY, không silent ignore back.

**RecognitionResult**: `requestId`, `itemId`, `inputRevision`, `suggestedAttributes`, `needsReviewFields`, `viewEvidence: Map<field,List<FRONT|BACK>>`, `warnings`, `source: AI_LIVE|FAKE`. Unknown giữ null/UNKNOWN; confidence optional không phải xác suất đã calibrated. Front/back mâu thuẫn trả REVIEW thay vì ưu tiên tùy tiện. Không tự save từ service.

**ClothingRecognition (B)**: `recognize(request) → Result<RecognitionResult>` suspend cancellable. B-005 fake, B-007 Android client, BACK-004 proxy `POST /v2/recognition`. A BACK-005 giữ draft revisions, chỉ áp suggestion lên fields user chưa sửa. Response stale bị bỏ hoặc yêu cầu review, không overwrite ảnh mới.

`POST /v2/recognition` nhận `garmentTypeVersion: 1` khi `/health` quảng bá cùng version. Response chi tiết thêm `suggestedAttributes.garmentType` và có thể thêm `garmentType` trong review/evidence. Client cũ nhận định dạng nhóm như trước. Khi mở rộng từ điển cần kiểm tra mã cũ và chính sách tương thích client. [Chi tiết wire](../contracts/proxy-api.md).

## CT-02 — OutfitRequest, Outfit và Recommendation

**OutfitRequest**: `schemaVersion=2`, `requestId`, `wardrobeSnapshotId`, `items: List<ItemSummary>` 1–50, `profile: ProfileProjection?`, `occasion: CASUAL|SCHOOL|WORK|DATE|PARTY|SPORT|FORMAL_EVENT`, `styleOverrides: List<String>`, `freeText: String` 0–500 ký tự, `weather: WeatherSnapshot?`, `requestedCount: Int` 1–5, `focusItemId: UUID?`, `excludeOutfitSignatures: Set<String>` tối đa 5, `regenerationOfRequestId: UUID?`.

ItemSummary chỉ id/revision/attributes đủ dùng; không ảnh/local paths. WeatherSnapshot có city label/tempC/condition/validAt/fetchedAt/source/isStale; missing/stale phải ghi đúng, không coi là current. Thời tiết tự động C-101 P1; core biết nhận context và chạy với null. Free text là dữ liệu sở thích, không được override allowlist, source, output schema hay privacy constraints.

**Outfit**: `id: UUID`, `requestId`, `wardrobeSnapshotId`, `items: List<{itemId,revision}>`, `explanation` 1–500 ký tự tiếng Việt, `source: AI_LIVE|RULES|FAKE`, `createdAt`, `signature` canonical sorted IDs. Provider có thể trả IDs; B wrapper gán stable outfit ID một lần khi nhận response, không sinh mới mỗi render. Một outfit hợp lệ có TOP+BOTTOM hoặc DRESS; thêm tối đa một OUTERWEAR/SHOES/ACCESSORY mỗi loại khi có. Không bắt buộc giày. Không DRESS+TOP/BOTTOM trong baseline validation.

**RecommendationResult**: `requestId`, `outfits: List<Outfit>` 1…requestedCount, `shortfallReason?` khi trả ít hơn. Không đủ một bộ thì typed `INSUFFICIENT_WARDROBE`, không success list rỗng. Các bộ khác nhau theo item set, không chỉ khác explanation. IDs/revisions thuộc input snapshot; focus nếu có phải trong mọi outfit; không trả excluded set. Trước hiển thị/chọn Try-On, D so với repository hiện tại: item sửa/xóa => stale, yêu cầu chọn/generate lại. Tủ đồ không đủ 5 bộ là trạng thái hợp lệ có giải thích.

**OutfitRecommendation (B)**: `recommend(request) → Result<RecommendationResult>`; B-005 fake, B-003 client, B-002 `POST /v2/outfits`. C-102 và LATER-006 hoàn thiện reroll/focus; schema có từ đầu, capability `supportsReroll/supportsFocus` false thì UI không bật CTA. Server phải reject unsupported constraint thay vì bỏ qua. A expose focus callback trên item; D nối route, B sở hữu semantics.

Ví dụ wire fixture (ID ngắn minh họa; triển khai dùng UUID):

```json
{"schemaVersion":2,"requestId":"r1","wardrobeSnapshotId":"s1","items":[{"id":"top1","revision":1,"attributes":{"category":"TOP","colors":["WHITE"]}},{"id":"bottom1","revision":1,"attributes":{"category":"BOTTOM","colors":["BLACK"]}}],"profile":{"heightCm":null,"stylePreferences":["minimal"],"preferredCategories":[]},"occasion":"WORK","styleOverrides":[],"freeText":"Tôi thích đơn giản","weather":null,"requestedCount":5,"focusItemId":null,"excludeOutfitSignatures":[],"regenerationOfRequestId":null}
```

Fixture hợp lệ có thể trả **1** outfit top1+bottom1, `shortfallReason=NOT_ENOUGH_DISTINCT_COMBINATIONS`. Không được nhân cùng bộ thành 5 để đủ số.

## CT-06 — TryOnRequest, TryOnResult và VirtualTryOn

Capability result: `decision=UNKNOWN|NO_GO|GO_FRONT_ONLY|GO_DUAL`, `supportedViews`, `supportedCategories`, `maxGarmentsPerJob`, `supportsNativePairedViews`, `runtimeVerifiedViews`, `providerConfigVersion`. C-205 ghi feasibility; C-212 mới ghi runtime verified. Gate report BLOCKED ánh xạ capability UNKNOWN (chưa đủ evidence), không phải GO. CORE chưa có runtimeVerifiedViews; FRONT chỉ FRONT; DUAL cần FRONT+BACK và C-211 PASS. Hai requests độc lập không tự nâng `supportsNativePairedViews` thành true.

**TryOnRequest**: `schemaVersion=2`, `requestId`, `idempotencyKey`, `outfitSnapshot: Outfit + ItemSummary[]`, `profileRevision`, `personImages: ImagePair`, `garmentImages: Map<itemId,ImagePair>`, `requestedViews: Set<FRONT|BACK>`, `selectedItemIds`, `consentGrantId`, `inputGeneration`, `providerConfigVersion`.

Chỉ request cho **một outfit đã chọn**. selectedItemIds là subset explicit của outfit khi provider chỉ hỗ trợ một garment; UI phải ghi “mô phỏng món …”, không gọi là cả outfit. Default yêu cầu toàn bộ clothing slots mà capability đã hỗ trợ; shoes/accessory không supported phải hiện excluded coverage. Requested FRONT cần personFront + garmentFront của các món selected; BACK cần matching personBack + garmentBack. Không dùng front làm back hoặc sinh backside mà gọi là reference thật. Missing input => NEEDS_INPUT trước upload/billing. Outfit stale hoặc media deleted => STALE_INPUT.

Local request dùng refs; C adapter dựng wire payload bytes cho đúng views sau consent. Không gửi toàn bộ wardrobe/profile. Server verifies consent/session, caps và schema; không tin absolute paths, arbitrary download URL hoặc client tự xưng owner. Job stores phải durable theo cơ chế đã được quyền dùng; không mặc định có DB/cloud resource miễn phí.

**TryOnResult**: `jobId`, `requestId`, `outfitId`, `inputGeneration`, `inputRevisions`, `status`, `views: Map<View,ViewResult>`, `appliedItemIds`, `unappliedItemIds`, `providerConfigVersion`, `createdAt`, `expiresAt?`, `source: AI_LIVE|FAKE`, `warnings`, `remoteDeletionState`.

ViewResult: `state=NOT_REQUESTED|QUEUED|RUNNING|SUCCEEDED|FAILED|EXPIRED|CANCELLED`, `outputRef?`, `error?`, `qualityReview=UNREVIEWED|ACCEPTED|REJECTED`. Job status `QUEUED|RUNNING|PARTIAL|SUCCEEDED|FAILED|TIMED_OUT|UNKNOWN_SUBMISSION|CANCELLED|DELETED`. Transport SUCCEEDED nghĩa ảnh nhận được, **không tự là quality ACCEPTED**. Partial front success/back fail phải giữ hai trạng thái, không suy thành dual success. Quality gate đánh giá bộ case, không hứa có automated detector đủ tin cậy cho mọi output.

**VirtualTryOn (C)**:

| Operation | Input → Output |
|---|---|
| `capabilities()` | Result<Capability> |
| `submit(request)` | Result<JobHandle> hoặc NEEDS_INPUT/CONSENT_REQUIRED/UNSUPPORTED_CAPABILITY/STALE_INPUT; không sinh ảnh sync trong UI |
| `observe(jobId)` | Flow<JobState>; poll bounded/backoff qua client |
| `cancel(jobId)` | Result<CancellationReceipt>; provider best-effort, không hứa refund |
| `delete(jobId)` | Result<DeletionReceipt>; xóa local và mô tả riêng provider state |

Proxy C routes: `POST /v2/tryon/jobs` trả 202 `{jobId,requestId,status}`; `GET /v2/tryon/jobs/{id}`; `POST .../{id}/cancel`; `DELETE .../{id}`. Auth session owner mọi route, UUID khó đoán không thay auth. Idempotency key scope theo session + inputGeneration; retry submit cùng key cùng body trả cùng job, khác body trả 409. Persist reservation trước upstream. Nếu upstream có thể đã nhận nhưng response mất, giữ UNKNOWN_SUBMISSION/reconcile; không tự POST mới nếu không bảo đảm dedupe provider.

C-206 fake mô phỏng all states, C-207 server, C-208 client, C-209 UI. Job metadata lưu local do C sở hữu; image bytes đọc/xóa qua A MediaStore. D ProfileStorage chỉ emit invalidation, không import C implementation. Root composition nối observer/callback để không dependency vòng.

## CT-03 — UI/lifecycle và consent

Routes: Profile, Wardrobe, Today, TryOn(outfitId + snapshot reference). Screen function nhận interfaces/callbacks, không đọc global provider key/container. D owns NavHost; C owns whole TryOnScreen; A owns wardrobe + OutfitVisual. Fake/real selected ở composition root, không trong business logic.

Mọi màn có Idle/Loading/Success/Empty hoặc Error riêng; partial là state của VTO. ViewModel giữ request identity qua rotation, process restart phục hồi profile/wardrobe/job metadata; recommendation không tự gọi lại mất tiền, VTO resume GET status. Double tap không tạo nhiều calls/jobs. Chọn outfit khác không âm thầm đổi input job đang chạy.

ConsentGrant gồm purpose RECOGNITION|TRYON, provider/config version, selected media IDs/views, issuedAt/revokedAt, request generation và user confirmation. Demo/share có consent khác. Từ chối không làm mất core wardrobe/recommendation. Xóa ảnh hoặc rút consent invalidate generation, cancel best-effort, remove refs/derivatives/results local; late completion không được ghi lại/hiển thị. Remote deletion trạng thái honest, không hứa xóa ngay nếu provider không hỗ trợ.

## CT-07 — errors, deadlines, privacy và fixtures

Common typed errors: INVALID_REQUEST, CONSENT_REQUIRED, UNAUTHORIZED, FORBIDDEN, PAYLOAD_TOO_LARGE, RATE_LIMITED, BUDGET_EXCEEDED, STALE_INPUT, CONFLICT, NEEDS_INPUT, UNSUPPORTED_CAPABILITY, INSUFFICIENT_WARDROBE, INVALID_AI_OUTPUT, NETWORK, UPSTREAM_TIMEOUT, UPSTREAM_ERROR, IO, CANCELLED, EXPIRED. `{error:{code,message,requestId,retryable}}`; không raw upstream body/key/PII. HTTP 400/401/403/409/413/429/502/503/504 dùng theo lỗi; Android local errors không fake HTTP status.

Mặc định đề xuất để test: recognition/outfit client deadline 20s, proxy ≤18s, provider ≤15s; không auto retry. Weather 3s độc lập. VTO submit HTTP timeout 15s, poll 2→4→8s với tổng chờ ≤120s cho front, ≤180s cho pair; đây là technical timeout, **khác quality/demo latency target trong feasibility gate**. Timeout không có nghĩa provider đã dừng/billing không xảy ra.

Wire caps: outfits UTF-8 ≤64KiB/50 items; recognition tối đa 2 garment derivatives ≤1MiB/ảnh decoded, tổng encoded JSON ≤4MiB; VTO person ≤2MiB/view, garment ≤1MiB/view, tổng decoded ≤8MiB và wire ≤12MiB. Nếu provider/host giới hạn thấp hơn, capability/config chọn ngưỡng thấp hơn và chặn trước upload. Output max 5 outfits, 1 sample/view cho VTO mặc định. Một job VTO/session đang chạy. Không nhận signed URL từ miền bất kỳ để proxy fetch; provider output URL phải allowlist/expiry/auth, hoặc server trả bytes theo cơ chế được duyệt. Không bucket/public URL người dùng làm shortcut.

Mỗi service có kill switch và budget cap được nhóm đồng ý; rate-limit không tự là hard monetary cap. Không có approved budget/account/privacy => live service blocked, mocks vẫn dùng để phát triển. Logs chỉ requestId/jobId kỹ thuật, status, latency, service; không ảnh/base64/body text/numeric profile/signed URLs. Provider key không nằm APK/git/log. Chưa có cloud sync không có nghĩa ảnh không rời máy: upload AI phải nói rõ.

Fixtures tối thiểu shared schema: 6 wardrobe items (3 cặp front/back), profile minimal/full/no-photo, recognition conflict, 1/5/fewer outfits, unknown/deleted ID, VTO missing-back/partial/back-failed/stale/consent-revoked/expired/double-submit. Ảnh người fixtures phải synthetic/licensed rõ, hoặc private consented và ngoài repo. Fake implementation có source FAKE; release không có fallback fake-as-live.

## Thay đổi contract

PRE-007 chốt v2, chưa có code v1 nên không cần migration endpoint v1 trong lượt này. Sau khi code tồn tại: owner đề xuất diff, D điều phối, B review recognition/recommendation, C review VTO, A review media/schema. Field nullable/default là lựa chọn tương thích, không giấy phép bỏ qua semantic changes. Breaking change tăng version hoặc migration có tests. Không đổi contracts trong task ngoài scope rồi bắt consumer sửa theo; mỗi coding agent báo file chạm và compatibility test trước bàn giao.

Ở CORE, route TryOn chỉ giải thích capability chưa khả dụng, không render kết quả FAKE như sản phẩm thật. C-208/C-209 có thể phát triển bằng fake trước GO; C-207 và mọi upload live cần gate/account/budget, runtime quality cần C-210 privacy checks. Khi C-210 xây bằng fake, phép thử cleanup với storage thật được thực hiện ở D-007/C-212 trước mở upload.


## Protocol đánh giá recommendation / recognition

B-004 cố định 10 fixture cases: tủ rỗng; chỉ TOP; một bộ TOP+BOTTOM; DRESS; đủ 5 tổ hợp; ít hơn requestedCount có reason; ID/revision lạ; duplicate item sets; freeText cố override allowlist; profile null/weather stale. Response invalid phải bị chặn, không vào UI như success. Ít nhất 3 case đủ đồ được gọi live, hai reviewer B/A chấm riêng tính phù hợp context và giải thích: 1 sai/mâu thuẫn input, 2 chung chung thiếu căn cứ, 3 dùng đúng thuộc tính và ít nhất một context thực, 4 giải thích lựa chọn cụ thể và nhất quán, 5 rõ cả trade-off/giới hạn. Mỗi case đạt ≥3/5 ở cả hai tiêu chí/cả hai reviewer; invariant ID/slot/count vẫn phải đạt 100%, không lấy điểm văn phong bù sai đồ. Report ghi denominator/failed cases/model/prompt version/cost; mẫu nhỏ không chứng minh tỷ lệ production.

B-006 dùng labels của 6 garment items/3 cặp để báo category/color theo field/view. Ba live cases mục tiêu: front-only, matched pair, conflicting pair. Nếu provider chỉ FRONT_ONLY thì thử pair phải trả UNSUPPORTED_CAPABILITY trước billing, dùng mock để kiểm conflict UI; ghi rõ không đạt dual recognition, không im lặng bỏ ảnh back. M3 chỉ công bố capability đã có measured evidence. Không suy material/fit accuracy từ category đúng. Budget thiếu thì ghi phần chưa chạy, không chuyển mock thành live evidence.
