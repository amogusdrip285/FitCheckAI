package com.fitcheckai.core.database.wardrobe

import com.fitcheckai.core.database.AppDatabase
import com.fitcheckai.core.model.ClothingItem as LegacyItem
import com.fitcheckai.core.model.ClothingAttributes as LegacyAttributes
import com.fitcheckai.core.model.Category as LegacyCategory
import com.fitcheckai.core.model.Season
import com.fitcheckai.core.model.WardrobeRepository as LegacyRepository
import com.fitcheckai.core.model.wardrobe.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter

/** Read compatibility for the existing B/D checkout. All active A writes use CT-01 v2.
 * Unknown categories cannot be represented by v1 and are intentionally excluded.
 * The v1 mandatory formality field uses its old default for display only, never written to v2.
 */
@Singleton
class LegacyWardrobeBridge @Inject constructor(private val repository: WardrobeRepository,
    private val media: MediaStore, private val db: AppDatabase) : LegacyRepository {
    override fun observeItems(): Flow<List<LegacyItem>> = repository.observeItems().filter { it !is LoadState.Loading }.map { load ->
        when (load) {
            is LoadState.Ready -> load.value.mapNotNull { adapt(it) }
            is LoadState.Failed -> throw load.error
            LoadState.Loading -> emptyList()
        }
    }
    override suspend fun getItem(id: String): LegacyItem? =
        repository.get(db.wardrobeDao().migratedId(id) ?: id).getOrThrow()?.let { adapt(it) }

    private suspend fun adapt(item: ClothingItem): LegacyItem? {
        if (item.attributes.category == Category.UNKNOWN) return null
        val a = item.attributes
        val legacy = db.wardrobeDao().legacyId(item.id)?.let { db.clothingItemDao().getById(it) }
        return LegacyItem(item.id, item.images.front?.let(media::displayPath).orEmpty(),
            LegacyAttributes(LegacyCategory.valueOf(a.category.name), item.name, a.colors.map(::hex),
                a.pattern.name.lowercase(), a.material, a.formality ?: 2,
                a.seasonTags.map { Season.valueOf(it) }.toSet(), a.styleTags.toSet()),
            Instant.ofEpochMilli(item.createdAt), legacy?.wearCount ?: 0,
            legacy?.lastWornAtMillis?.let(Instant::ofEpochMilli))
    }
    override suspend fun save(imagePath: String, attributes: LegacyAttributes): Result<LegacyItem> = unsupported()
    override suspend fun update(item: LegacyItem): Result<Unit> = unsupported()
    override suspend fun delete(id: String): Result<Unit> = unsupported()
    override suspend fun markWorn(itemIds: List<String>, at: Instant): Result<Unit> = unsupported()
    private fun <T> unsupported(): Result<T> = Result.failure(WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY,
        "Luồng cũ chỉ đọc. Dùng tủ đồ mới để chỉnh sửa; wear tracking đang ngoài phạm vi."))
    private fun hex(color: GarmentColor) = when (color) {
        GarmentColor.BLACK -> "#222222"; GarmentColor.WHITE -> "#FFFFFF"; GarmentColor.GRAY -> "#888888"
        GarmentColor.BEIGE -> "#D8C3A5"; GarmentColor.BROWN -> "#6B4226"; GarmentColor.BLUE -> "#3B5B8C"
        GarmentColor.GREEN -> "#228B22"; GarmentColor.RED -> "#B23A30"; GarmentColor.YELLOW -> "#E8BF50"
        GarmentColor.PINK -> "#D29DA9"; GarmentColor.PURPLE -> "#84619B"; GarmentColor.OTHER -> "#BAC5C5"
    }
}
