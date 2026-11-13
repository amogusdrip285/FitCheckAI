package com.fitcheckai.core.model.wardrobe

import kotlinx.serialization.json.*

/** A-201 design prototype only: portable garment metadata, no cloud service or automatic DB import. */
data class GarmentTombstone(val id: String, val revision: Long, val deletedAt: Long) {
    fun validate() { requireUuid(id); require(revision >= 2 && deletedAt >= 0) }
}
data class GarmentTransfer(val items: List<ClothingItem>, val tombstones: List<GarmentTombstone>) {
    fun validate() {
        items.forEach(ClothingItem::validate); tombstones.forEach(GarmentTombstone::validate)
        require(items.distinctBy { it.id }.size == items.size && tombstones.distinctBy { it.id }.size == tombstones.size)
        require(items.none { item -> tombstones.any { it.id == item.id } }) { "Tombstones cannot coexist with active copies." }
        require(items.flatMap { it.images.refs() }.all { it.scope == MediaScope.GARMENT })
    }
}

object GarmentSyncMapping {
    fun opaqueMediaId(ref: MediaRef): String {
        ref.validate(); require(ref.scope == MediaScope.GARMENT)
        return "garment/${ref.id}"
    }
    private fun portable(item: ClothingItem): ClothingItem {
        fun ref(value: MediaRef?) = value?.let {
            opaqueMediaId(it)
            it.copy(localRelativePath = "media/GARMENT/${it.ownerEntityId}/${it.id}.${if (it.mimeType == "image/png") "png" else "jpg"}")
        }
        return item.copy(images = ImagePair(ref(item.images.front), ref(item.images.back)))
    }
    fun encode(transfer: GarmentTransfer): String {
        transfer.validate()
        return buildJsonObject {
            put("format", "fitcheck-garment-mapping"); put("version", 1)
            put("items", JsonArray(transfer.items.map { Json.parseToJsonElement(WardrobeCodec.encodeItem(portable(it))) }))
            put("tombstones", JsonArray(transfer.tombstones.map { buildJsonObject {
                put("id", it.id); put("revision", it.revision); put("deletedAt", it.deletedAt)
            } }))
        }.toString()
    }
    fun decode(text: String): GarmentTransfer {
        require(text.length <= 4 * 1024 * 1024) { "Metadata mapping too large." }
        val o = Json.parseToJsonElement(text).jsonObject
        require(o["format"]?.jsonPrimitive?.content == "fitcheck-garment-mapping" && o["version"]?.jsonPrimitive?.int == 1)
        return GarmentTransfer(o.getValue("items").jsonArray.map { WardrobeCodec.decodeItem(it.toString()) },
            o.getValue("tombstones").jsonArray.map {
                val t = it.jsonObject
                GarmentTombstone(t.getValue("id").jsonPrimitive.content, t.getValue("revision").jsonPrimitive.long, t.getValue("deletedAt").jsonPrimitive.long)
            }).also(GarmentTransfer::validate)
    }
    /** Conservative draft policy: deletion wins; conflicting active copies require explicit review. */
    fun merge(local: GarmentTransfer, incoming: GarmentTransfer): GarmentTransfer {
        local.validate(); incoming.validate()
        val tombstones = (local.tombstones + incoming.tombstones).groupBy { it.id }.values.map { group -> group.maxBy { it.revision } }
        val deleted = tombstones.map { it.id }.toSet()
        val items = (local.items + incoming.items).filter { it.id !in deleted }.groupBy { it.id }.values.map { group ->
            val copies = group.map(::portable).distinct()
            if (copies.size != 1) throw WardrobeException(ErrorCode.CONFLICT, "Hai bản của cùng món đồ cần được đối chiếu trước khi nhập.")
            copies.single()
        }
        return GarmentTransfer(items.sortedBy { it.id }, tombstones.sortedBy { it.id }).also(GarmentTransfer::validate)
    }
}
