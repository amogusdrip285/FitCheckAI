package com.fitcheckai.core.model.wardrobe

import com.fitcheckai.core.model.wardrobe.fakes.*
import org.junit.Assert.*
import org.junit.Test

class SyncMappingTest {
    @Test fun `mapping roundtrip preserves entity media UUID hash revision and tombstone`() {
        val data = GarmentTransfer(sixFixtures(), listOf(GarmentTombstone(fixtureId("deleted"), 4, 1000)))
        val output = GarmentSyncMapping.decode(GarmentSyncMapping.encode(data))
        assertEquals(data, output)
        output.items.flatMap { it.images.refs() }.forEach { assertEquals("garment/${it.id}", GarmentSyncMapping.opaqueMediaId(it)) }
    }
    @Test fun `older active export cannot resurrect local or incoming deletion`() {
        val item = sixFixtures().first()
        val active = GarmentTransfer(listOf(item.copy(revision = 10)), emptyList())
        val deleted = GarmentTransfer(emptyList(), listOf(GarmentTombstone(item.id, 2, 300)))
        assertTrue(GarmentSyncMapping.merge(active, deleted).items.isEmpty())
        assertTrue(GarmentSyncMapping.merge(deleted, active).items.isEmpty())
    }
    @Test fun `person photos and conflicting active versions are rejected`() {
        val item = sixFixtures().first()
        val private = item.copy(images = ImagePair(item.images.front!!.copy(scope = MediaScope.PERSON)))
        assertThrows(IllegalArgumentException::class.java) { GarmentSyncMapping.encode(GarmentTransfer(listOf(private), emptyList())) }
        assertThrows(WardrobeException::class.java) {
            GarmentSyncMapping.merge(GarmentTransfer(listOf(item), emptyList()), GarmentTransfer(listOf(item.copy(name = "conflict")), emptyList()))
        }
        val repeated = GarmentTransfer(listOf(item), emptyList())
        assertEquals(repeated, GarmentSyncMapping.merge(repeated, repeated))
    }
}
