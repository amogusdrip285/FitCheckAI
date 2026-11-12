package com.fitcheckai.core.model.ai.fakes

import com.fitcheckai.core.model.recommendation.ItemSummary
import com.fitcheckai.core.model.recommendation.Occasion
import com.fitcheckai.core.model.recommendation.OutfitRequest
import com.fitcheckai.core.model.recommendation.OutfitSource
import com.fitcheckai.core.model.recommendation.ShortfallReason
import com.fitcheckai.core.model.wardrobe.AttributeField
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ClothingAttributes
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.GarmentColor
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.Pattern
import com.fitcheckai.core.model.wardrobe.ResultSource
import java.io.File
import java.lang.reflect.Modifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-005: enum/field của Kotlin (core/model) và proxy (proxy/src) phải trùng tên — client B-003/B-007 map bằng `name`.
 * Đọc thẳng source TS/fixture trong repo, nên đổi một phía mà quên phía kia sẽ làm test này fail.
 */
class ProxyCompatibilityTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile && File(it, "proxy").isDirectory }
    private fun read(path: String) = File(root, path).readText()

    private val domain = read("proxy/src/shared/domain.ts")
    private fun tsArray(source: String, name: String): Set<String> {
        val body = Regex("""export const $name = \[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(source)?.groupValues?.get(1)
            ?: error("Không thấy $name trong proxy")
        return Regex("\"([A-Za-z_]+)\"").findAll(body).map { it.groupValues[1] }.toSet()
    }
    private fun names(entries: List<Enum<*>>) = entries.map { it.name }.toSet()
    private fun fields(type: Class<*>) = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()

    @Test fun `shared enums match proxy domain`() {
        assertEquals(names(Category.entries), tsArray(domain, "CATEGORIES"))
        assertEquals(names(GarmentColor.entries), tsArray(domain, "COLORS"))
        assertEquals(names(Pattern.entries), tsArray(domain, "PATTERNS"))
        assertEquals(names(Occasion.entries), tsArray(domain, "OCCASIONS"))
        assertEquals(names(ImageView.entries), tsArray(domain, "VIEWS"))
        assertEquals(setOf("SPRING", "SUMMER", "AUTUMN", "WINTER"), tsArray(domain, "SEASONS"))
        // Wire dùng camelCase (category, colors, …, seasonTags); client map 1-1 sang AttributeField.
        assertEquals(AttributeField.entries.size, tsArray(domain, "ATTRIBUTE_FIELDS").size)
    }

    @Test fun `every server error code exists in Kotlin ErrorCode`() {
        val union = Regex("""export type ErrorCode =(.*?);""", RegexOption.DOT_MATCHES_ALL).find(read("proxy/src/shared/errors.ts"))!!.groupValues[1]
        val server = Regex("\"([A-Z_]+)\"").findAll(union).map { it.groupValues[1] }.toSet()
        assertTrue(server.isNotEmpty())
        assertTrue("Thiếu ở Kotlin: ${server - names(ErrorCode.entries)}", names(ErrorCode.entries).containsAll(server))
    }

    @Test fun `shortfall reasons and sources match the outfits route`() {
        val schema = read("proxy/src/outfits/schema.ts")
        val modelReasons = Regex("""shortfallReason: z\.enum\(\[(.*?)]""").find(schema)!!.groupValues[1]
            .let { Regex("\"([A-Z_]+)\"").findAll(it).map { m -> m.groupValues[1] }.toSet() }
        assertTrue(read("proxy/src/outfits/route.ts").contains("\"INVALID_OUTFITS_REMOVED\""))
        assertEquals(names(ShortfallReason.entries), modelReasons + "INVALID_OUTFITS_REMOVED")
        val sources = Regex("""source: "([A-Z_]+)" \| "([A-Z_]+)"""").find(schema)!!.groupValues.drop(1).toSet()
        assertTrue(names(OutfitSource.entries).containsAll(sources) && names(ResultSource.entries).containsAll(sources))
    }

    @Test fun `CT-02 example request keys equal Kotlin OutfitRequest fields`() {
        val example = Json.parseToJsonElement(read("proxy/test/fixtures/v2/outfits-request-ct02-example.json")).jsonObject
        assertEquals(fields(OutfitRequest::class.java), example.keys)
        val item = example["items"]!!.jsonArray.first().jsonObject
        assertEquals(fields(ItemSummary::class.java), item.keys)
        val wardrobe = Json.parseToJsonElement(read("proxy/test/fixtures/v2/wardrobe-items.json")).jsonArray
        val attributeKeys = wardrobe.flatMap { (it.jsonObject["attributes"] as JsonObject).keys }.toSet()
        assertTrue("Field wire lạ: ${attributeKeys - fields(ClothingAttributes::class.java)}",
            fields(ClothingAttributes::class.java).containsAll(attributeKeys))
    }
}
