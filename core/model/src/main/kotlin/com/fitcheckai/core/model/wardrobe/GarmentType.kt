package com.fitcheckai.core.model.wardrobe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GarmentType(val id: String, val category: Category, val label: String) {
    companion object {
        val entries: List<GarmentType> by lazy {
            val source = requireNotNull(GarmentType::class.java.getResourceAsStream("garment-types.json"))
            val catalog = source.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Json.parseToJsonElement(catalog).jsonArray.map { value ->
                val row = value.jsonObject
                GarmentType(
                    id = row.getValue("id").jsonPrimitive.content,
                    category = Category.valueOf(row.getValue("category").jsonPrimitive.content),
                    label = row.getValue("label").jsonPrimitive.content,
                )
            }
        }

        private val byId by lazy { entries.associateBy(GarmentType::id) }

        fun find(id: String?): GarmentType? = byId[id]
    }
}
