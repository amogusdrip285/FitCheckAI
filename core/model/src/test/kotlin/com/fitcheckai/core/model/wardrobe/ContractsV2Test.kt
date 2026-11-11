package com.fitcheckai.core.model.wardrobe

import com.fitcheckai.core.model.wardrobe.fakes.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContractsV2Test {
    @Test fun `fake CAS deletion and IO preserve snapshot invariants`() = runBlocking {
        val repo = FakeWardrobeRepository()
        val before = repo.getSnapshot().getOrThrow()
        assertEquals(6, before.items.size)
        assertEquals(3, before.items.count { it.images.back != null })
        val item = before.items.first()
        repo.failNext = ErrorCode.IO
        assertTrue(repo.upsert(item.copy(name = "changed"), item.revision).isFailure)
        assertEquals(before, repo.getSnapshot().getOrThrow())
        val saved = repo.upsert(item.copy(name = "changed"), item.revision).getOrThrow()
        assertEquals(2L, saved.revision)
        assertEquals(ErrorCode.CONFLICT, (repo.upsert(item, 1).exceptionOrNull() as WardrobeException).code)
        assertFalse(repo.getSnapshot().getOrThrow().isCurrent(before.references))
        repo.delete(saved.id, saved.revision).getOrThrow()
        assertNull(repo.get(saved.id).getOrThrow())
        assertFalse(repo.getSnapshot().getOrThrow().items.any { it.id == saved.id })
        assertTrue(repo.upsert(item).isFailure)
    }
    @Test fun `codec roundtrip keeps UUID paired refs and provenance`() {
        sixFixtures().forEach { assertEquals(it, WardrobeCodec.decodeItem(WardrobeCodec.encodeItem(it))) }
    }
    @Test fun `different owner and duplicate views are rejected`() {
        val a = sixFixtures()[0]; val b = sixFixtures()[1]
        assertThrows(IllegalArgumentException::class.java) { a.copy(images = a.images.copy(back = b.images.back)).validate() }
        assertThrows(IllegalArgumentException::class.java) { a.copy(images = ImagePair(null, a.images.back)).validate() }
        assertThrows(IllegalArgumentException::class.java) { a.copy(images = a.images.copy(back = a.images.front!!.copy(view = ImageView.BACK))).validate() }
    }
    @Test fun `consent cannot cross request provider selected images or revoked grant`() {
        val item = sixFixtures().first(); val cap = RecognitionCapability("provider", "v1", true, true)
        val rid = fixtureId("request")
        val grant = ConsentGrant(requestId = rid, provider = cap.provider, providerConfigVersion = cap.configVersion,
            mediaIds = item.images.refs().map { it.id }.toSet(), mediaViews = item.images.refs().associate { it.id to it.view }, confirmedByUser = true)
        val request = RecognitionRequest(rid, item.id, 1, item.images, grant)
        request.validate(cap)
        listOf(grant.copy(requestId = fixtureId("other")), grant.copy(provider = "other"), grant.copy(providerConfigVersion = "v2"),
            grant.copy(mediaIds = emptySet()), grant.copy(revokedAt = 123), grant.copy(purpose = ConsentPurpose.TRYON),
            grant.copy(mediaViews = emptyMap()), grant.copy(requestGeneration = 2), grant.copy(confirmedByUser = false)).forEach {
            assertEquals(ErrorCode.CONSENT_REQUIRED, assertThrows(WardrobeException::class.java) { request.copy(consentGrant = it).validate(cap) }.code)
        }
        assertThrows(WardrobeException::class.java) { request.validate(cap.copy(supportsBack = false)) }
    }
    @Test fun `shared image pairs support person scope only when explicitly requested`() {
        val item = sixFixtures().first()
        val person = ImagePair(item.images.front!!.copy(scope = MediaScope.PERSON), item.images.back!!.copy(scope = MediaScope.PERSON))
        person.validate(item.id, scope = MediaScope.PERSON)
        assertThrows(IllegalArgumentException::class.java) { person.validate(item.id) }
        assertThrows(IllegalArgumentException::class.java) { requireUuid(item.id.uppercase()) }
    }
    @Test fun `manual and unknown survive with truthful provenance`() {
        val current = ClothingAttributes(Category.DRESS, material = "linen", attributeSources = mapOf(AttributeField.CATEGORY to AttributeSource.MANUAL, AttributeField.MATERIAL to AttributeSource.AI))
        val response = RecognitionResult(fixtureId("r"), fixtureId("i"), 1, ClothingAttributes(Category.TOP), source = ResultSource.AI_LIVE)
        val merged = mergeRecognition(current, setOf(AttributeField.CATEGORY), response)
        assertEquals(Category.DRESS, merged.category); assertNull(merged.material)
        assertFalse(AttributeField.MATERIAL in merged.attributeSources)
        assertEquals(AttributeSource.MANUAL, merged.attributeSources[AttributeField.CATEGORY])
        assertFalse(mergeRecognition(ClothingAttributes(), emptySet(), response.copy(source = ResultSource.FAKE)).attributeSources.containsValue(AttributeSource.AI))
    }
}
