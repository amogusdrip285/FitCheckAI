package com.fitcheckai.core.model

import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Contract giữa các track. Mỗi interface ghi rõ OWNER (implement) — consumer chỉ phụ thuộc interface.
// Quy ước: hàm có thể lỗi do I/O/mạng trả Result, KHÔNG throw ra ngoài.

/** OWNER: Track A (`core:database`). */
interface WardrobeRepository {
    fun observeItems(): Flow<List<ClothingItem>>
    suspend fun getItem(id: String): ClothingItem?
    suspend fun save(imagePath: String, attributes: ClothingAttributes): Result<ClothingItem>
    suspend fun update(item: ClothingItem): Result<Unit>
    /** Xóa cả bản ghi và file ảnh. */
    suspend fun delete(id: String): Result<Unit>
    /** Tăng wearCount, cập nhật lastWornAt. Gọi bởi [WearLogRepository.logWear]. */
    suspend fun markWorn(itemIds: List<String>, at: Instant): Result<Unit>
}

/** OWNER: Track A (`core:imaging`). */
interface ImageSegmenter {
    /** Tải trước model ML Kit (gọi ở onboarding). */
    suspend fun warmUp() {}

    /** Tách nền ảnh [sourceUri] (content:// hoặc file://), trả đường dẫn PNG đã lưu. */
    suspend fun removeBackground(sourceUri: String): Result<String>
}

/** OWNER: Track B (`core:ai`). */
interface ClothingTagger {
    suspend fun tag(imagePath: String): Result<ClothingAttributes>
}

/** OWNER: Track B (`core:ai`). Mọi itemId trả về PHẢI thuộc request.items. */
interface AiOutfitGenerator {
    suspend fun generate(request: OutfitRequest): Result<List<Outfit>>
}

/** OWNER: Track C (`core:weather`). */
interface WeatherProvider {
    suspend fun current(): Result<WeatherSnapshot>
}

/** OWNER: Track C (`core:stylist`). Luôn trả kết quả: AI lỗi → fallback rule-based. */
interface OutfitService {
    suspend fun suggest(occasion: Occasion, focusItemId: String? = null): OutfitSuggestions
}

/** OWNER: Track D (`core:datastore`). */
interface WearLogRepository {
    fun observeLogs(): Flow<List<WearLogEntry>>
    /** Ghi log VÀ cập nhật wearCount qua [WardrobeRepository.markWorn]. */
    suspend fun logWear(outfit: Outfit, at: Instant = Instant.now()): Result<Unit>
    suspend fun recordFeedback(feedback: OutfitFeedback): Result<Unit>
}

/** OWNER: Track D (`core:datastore`). */
interface ProfileRepository {
    /** null = chưa onboarding. */
    fun observe(): Flow<StyleProfile?>
    suspend fun save(profile: StyleProfile): Result<Unit>
}

/** OWNER: Track D (`core:analytics`). Không bao giờ throw. */
interface MetricsTracker {
    fun track(event: MetricEvent)
}
