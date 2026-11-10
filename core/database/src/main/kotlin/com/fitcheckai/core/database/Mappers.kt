package com.fitcheckai.core.database

import com.fitcheckai.core.database.entity.ClothingItemEntity
import com.fitcheckai.core.model.Category
import com.fitcheckai.core.model.ClothingAttributes
import com.fitcheckai.core.model.ClothingItem
import com.fitcheckai.core.model.Season
import java.time.Instant

/** Mã hóa list thành chuỗi bằng ký tự Unit Separator (không xuất hiện trong text người dùng). */
object ListCodec {
    private const val SEPARATOR = '\u001F'

    fun encode(values: Collection<String>): String = values.joinToString(SEPARATOR.toString())

    fun decode(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR)
}

fun ClothingItemEntity.toModel(): ClothingItem = ClothingItem(
    id = id,
    imagePath = imagePath,
    attributes = ClothingAttributes(
        category = Category.valueOf(category),
        subCategory = subCategory,
        colors = ListCodec.decode(colors),
        pattern = pattern,
        material = material,
        formality = formality,
        seasons = ListCodec.decode(seasons).map(Season::valueOf).toSet(),
        styleTags = ListCodec.decode(styleTags).toSet(),
    ),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    wearCount = wearCount,
    lastWornAt = lastWornAtMillis?.let(Instant::ofEpochMilli),
)

fun ClothingItem.toEntity(imageHash: String? = null): ClothingItemEntity = ClothingItemEntity(
    id = id,
    imagePath = imagePath,
    imageHash = imageHash,
    category = attributes.category.name,
    subCategory = attributes.subCategory,
    colors = ListCodec.encode(attributes.colors),
    pattern = attributes.pattern,
    material = attributes.material,
    formality = attributes.formality,
    seasons = ListCodec.encode(attributes.seasons.map(Season::name)),
    styleTags = ListCodec.encode(attributes.styleTags),
    createdAtMillis = createdAt.toEpochMilli(),
    wearCount = wearCount,
    lastWornAtMillis = lastWornAt?.toEpochMilli(),
)
