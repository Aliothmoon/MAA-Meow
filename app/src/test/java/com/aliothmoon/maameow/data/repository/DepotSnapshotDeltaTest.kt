package com.aliothmoon.maameow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepotSnapshotDeltaTest {

    @Test
    fun noBaseline_returnsNull() {
        val snap = DepotSnapshot(scanItems = mapOf("30011" to 10), syncTimeMillis = 2L)

        assertNull(snap.delta())
    }

    @Test
    fun delta_coversUnionOfBothScans() {
        val snap = DepotSnapshot(
            syncTimeMillis = 2L,
            scanItems = mapOf("30011" to 10, "30013" to 5),
            prevScanItems = mapOf("30011" to 4, "30012" to 3),
            prevSyncTimeMillis = 1L,
        )

        val delta = snap.delta()!!

        assertEquals(mapOf("30011" to 6, "30012" to -3, "30013" to 5), delta.changes)
        assertEquals(1L, delta.fromMillis)
    }

    @Test
    fun delta_dropsUnchangedItems() {
        val snap = DepotSnapshot(
            syncTimeMillis = 2L,
            scanItems = mapOf("30011" to 10, "30012" to 7),
            prevScanItems = mapOf("30011" to 10, "30012" to 3),
            prevSyncTimeMillis = 1L,
        )

        assertEquals(mapOf("30012" to 4), snap.delta()!!.changes)
    }

    @Test
    fun delta_ignoresItemsMergedAfterScan() {
        val snap = DepotSnapshot(
            items = mapOf("30011" to 50),
            syncTimeMillis = 2L,
            scanItems = mapOf("30011" to 10),
            prevScanItems = mapOf("30011" to 10),
            prevSyncTimeMillis = 1L,
        )

        assertEquals(emptyMap<String, Int>(), snap.delta()!!.changes)
    }

    @Test
    fun formatDepotDelta_prefixesPlusOnIncrease() {
        assertEquals("+500", formatDepotDelta(500))
        assertEquals("-100", formatDepotDelta(-100))
    }
}
