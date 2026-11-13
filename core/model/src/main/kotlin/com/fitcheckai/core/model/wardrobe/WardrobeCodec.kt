package com.fitcheckai.core.model.wardrobe

import kotlinx.serialization.json.*

/** Versioned local serialization. Domain paths never belong in an AI request. */
object WardrobeCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encodeMedia(ref: MediaRef): String = media(ref).toString()
    fun decodeMedia(text: String): MediaRef = media(json.parseToJsonElement(text).jsonObject)
    fun encodeItem(item: ClothingItem): String = buildJsonObject {
        put("schemaVersion", 2); put("id", item.id); put("name", item.name)
        put("revision", item.revision); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
        item.deletedAt?.let { put("deletedAt", it) }
        put("recognitionState", item.recognitionState.name)
        put("images", buildJsonObject {
            item.images.front?.let { put("front", media(it)) }; item.images.back?.let { put("back", media(it)) }
        })
        put("attributes", attributes(item.attributes))
    }.toString()
    fun decodeItem(text: String): ClothingItem {
        val o = json.parseToJsonElement(text).jsonObject
        require(o["schemaVersion"]?.jsonPrimitive?.int == 2)
        val images = o.getValue("images").jsonObject
        return ClothingItem(o.string("id"), o.string("name"),
            ImagePair(images["front"]?.jsonObject?.let(::media), images["back"]?.jsonObject?.let(::media)),
            attributes(o.getValue("attributes").jsonObject), o.long("revision"), o.long("createdAt"),
            o.long("updatedAt"), o["deletedAt"]?.jsonPrimitive?.longOrNull,
            RecognitionState.valueOf(o.string("recognitionState")))
    }
    fun media(ref: MediaRef): JsonObject = buildJsonObject {
        put("id", ref.id); put("ownerEntityId", ref.ownerEntityId); put("scope", ref.scope.name); put("view", ref.view.name)
        put("localRelativePath", ref.localRelativePath); put("mimeType", ref.mimeType)
        put("width", ref.width); put("height", ref.height); put("byteCount", ref.byteCount)
        put("contentHash", ref.contentHash); put("revision", ref.revision)
        ref.derivedFromMediaId?.let { put("derivedFromMediaId", it) }
    }
    fun media(o: JsonObject): MediaRef = MediaRef(o.string("id"), o.string("ownerEntityId"),
        MediaScope.valueOf(o.string("scope")), ImageView.valueOf(o.string("view")), o.string("localRelativePath"),
        o.string("mimeType"), o.getValue("width").jsonPrimitive.int, o.getValue("height").jsonPrimitive.int,
        o.long("byteCount"), o.string("contentHash"), o.long("revision"), o["derivedFromMediaId"]?.jsonPrimitive?.contentOrNull)
    fun attributes(a: ClothingAttributes): JsonObject = buildJsonObject {
        put("category", a.category.name); put("colors", strings(a.colors.map { it.name })); put("pattern", a.pattern.name)
        a.garmentType?.let { put("garmentType", it) }
        a.material?.let { put("material", it) }; a.formality?.let { put("formality", it) }
        put("seasonTags", strings(a.seasonTags)); put("styleTags", strings(a.styleTags))
        put("attributeSources", buildJsonObject { a.attributeSources.forEach { (key, value) -> put(key.name, value.name) } })
        put("needsReviewFields", strings(a.needsReviewFields.map { it.name }))
    }
    fun attributes(o: JsonObject): ClothingAttributes = ClothingAttributes(
        Category.valueOf(o.string("category")), o.list("colors").map(GarmentColor::valueOf), Pattern.valueOf(o.string("pattern")),
        o["material"]?.jsonPrimitive?.contentOrNull, o["formality"]?.jsonPrimitive?.intOrNull,
        o.list("seasonTags"), o.list("styleTags"),
        o["attributeSources"]?.jsonObject?.map { (key, value) -> AttributeField.valueOf(key) to AttributeSource.valueOf(value.jsonPrimitive.content) }?.toMap().orEmpty(),
        o.list("needsReviewFields").map(AttributeField::valueOf).toSet(),
        o["garmentType"]?.jsonPrimitive?.contentOrNull)
    fun strings(values: Iterable<String>) = JsonArray(values.map(::JsonPrimitive))
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long
    private fun JsonObject.list(key: String) = get(key)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
}
