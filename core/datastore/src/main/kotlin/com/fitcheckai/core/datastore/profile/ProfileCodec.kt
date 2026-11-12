package com.fitcheckai.core.datastore.profile

import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ImagePair
import com.fitcheckai.core.model.wardrobe.WardrobeCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Versioned local format for the single profile file. Media refs reuse Track A's codec. */
internal object ProfileCodec {
    private const val SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(profile: UserProfile): String = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", profile.id)
        put("revision", profile.revision)
        put("createdAt", profile.createdAt)
        put("updatedAt", profile.updatedAt)
        profile.heightCm?.let { put("heightCm", it) }
        profile.weightKg?.let { put("weightKg", it) }
        put("stylePreferences", JsonArray(profile.stylePreferences.map(::JsonPrimitive)))
        put("preferredCategories", JsonArray(profile.preferredCategories.sorted().map { JsonPrimitive(it.name) }))
        profile.cityKey?.let { put("cityKey", it) }
        put("bodyImages", buildJsonObject {
            profile.bodyImages.front?.let { put("front", json.parseToJsonElement(WardrobeCodec.encodeMedia(it))) }
            profile.bodyImages.back?.let { put("back", json.parseToJsonElement(WardrobeCodec.encodeMedia(it))) }
        })
        put("onboardingCompleted", profile.onboardingCompleted)
    }.toString()

    fun decode(text: String): UserProfile {
        val root = json.parseToJsonElement(text).jsonObject
        require(root.long("schemaVersion") == SCHEMA_VERSION.toLong()) { "Unsupported profile schema" }
        val images = root["bodyImages"]?.jsonObject ?: JsonObject(emptyMap())
        return UserProfile(
            id = root.string("id"),
            revision = root.long("revision"),
            createdAt = root.long("createdAt"),
            updatedAt = root.long("updatedAt"),
            heightCm = root["heightCm"]?.jsonPrimitive?.doubleOrNull,
            weightKg = root["weightKg"]?.jsonPrimitive?.doubleOrNull,
            stylePreferences = root.strings("stylePreferences"),
            preferredCategories = root.strings("preferredCategories").map(Category::valueOf).toSet(),
            cityKey = root["cityKey"]?.jsonPrimitive?.content,
            bodyImages = ImagePair(
                front = images["front"]?.let { WardrobeCodec.decodeMedia(it.toString()) },
                back = images["back"]?.let { WardrobeCodec.decodeMedia(it.toString()) },
            ),
            onboardingCompleted = root["onboardingCompleted"]?.jsonPrimitive?.booleanOrNull ?: false,
        ).also(UserProfile::validate)
    }

    private fun JsonObject.string(key: String) = requireNotNull(this[key]?.jsonPrimitive?.content) { "Missing $key" }
    private fun JsonObject.long(key: String) = requireNotNull(this[key]?.jsonPrimitive) { "Missing $key" }.long
    private fun JsonObject.strings(key: String) = this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
}
