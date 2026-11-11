package com.fitcheckai.core.datastore

import com.fitcheckai.core.model.OutfitFeedback
import com.fitcheckai.core.model.Outfit
import com.fitcheckai.core.model.WardrobeRepository
import com.fitcheckai.core.model.WearLogEntry
import com.fitcheckai.core.model.WearLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPRINT 0 STUB — in-memory.
 * TODO(Track D): RoomWearLogRepository dùng WearLogDao/FeedbackDao trong core:database.
 */
@Singleton
class StubWearLogRepository @Inject constructor(
    private val wardrobe: WardrobeRepository,
) : WearLogRepository {
    private val logs = MutableStateFlow<List<WearLogEntry>>(emptyList())
    private val feedback = MutableStateFlow<List<OutfitFeedback>>(emptyList())

    override fun observeLogs(): Flow<List<WearLogEntry>> = logs.asStateFlow()

    override suspend fun logWear(outfit: Outfit, at: Instant): Result<Unit> {
        val entry = WearLogEntry(
            id = UUID.randomUUID().toString(),
            outfitId = outfit.id,
            itemIds = outfit.itemIds,
            wornAt = at,
        )
        logs.update { listOf(entry) + it }
        return wardrobe.markWorn(outfit.itemIds, at)
    }

    override suspend fun recordFeedback(feedback: OutfitFeedback): Result<Unit> {
        this.feedback.update { listOf(feedback) + it }
        return Result.success(Unit)
    }
}
