package com.aliothmoon.maameow.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 仓库自身的语义：全量覆盖、掉落累加、排除集、缺口查询、分片隔离。
 *
 * 分片装载、写回队列等共享机制在 [ProfileShardStoreTest] 里测。
 */
class DepotRepositoryTest {

    private val activeProfileId = MutableStateFlow(PROFILE_A)
    private val profileDeleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private lateinit var store: FakePreferencesDataStore
    private lateinit var repository: DepotRepository

    @Before
    fun setUp() {
        store = FakePreferencesDataStore()
        repository = DepotRepository(store, fakeChainState())
    }

    private fun fakeChainState(): TaskChainState = mockk {
        every { this@mockk.profileId } returns this@DepotRepositoryTest.activeProfileId
        every { isLoaded } returns MutableStateFlow(true)
        every { this@mockk.profileDeleted } returns this@DepotRepositoryTest.profileDeleted
    }

    private suspend fun rawShardOf(profileId: String): String? =
        store.data.first()[stringPreferencesKey("depot_$profileId")]

    private suspend fun awaitDiskShard(profileId: String = PROFILE_A): DepotSnapshot =
        withTimeout(AWAIT_TIMEOUT_MS) {
            while (true) {
                val raw = rawShardOf(profileId)
                if (raw != null) {
                    return@withTimeout JsonUtils.common.decodeFromString(raw)
                }
                yield()
            }
            error("unreachable")
        }

    private suspend fun awaitSnapshot(predicate: (DepotSnapshot) -> Boolean): DepotSnapshot =
        withTimeout(AWAIT_TIMEOUT_MS) { repository.snapshot.first(predicate) }

    @Test
    fun initialSnapshot_isEmpty() = runBlocking {
        assertEquals(DepotSnapshot(), awaitSnapshot { true })
    }

    @Test
    fun set_updatesMemoryAndStampsSyncTime() {
        repository.set(listOf(DepotItem("30011", 200), DepotItem("30012", 5)))

        val snap = repository.snapshot.value
        assertEquals(mapOf("30011" to 200, "30012" to 5), snap.items)
        assertTrue(snap.syncTimeMillis > 0)
    }

    @Test
    fun set_overwritesInsteadOfMerging() {
        repository.set(listOf(DepotItem("30011", 200)))
        repository.set(listOf(DepotItem("30012", 5)))

        assertEquals(mapOf("30012" to 5), repository.snapshot.value.items)
    }

    @Test
    fun set_keepsExcludedIds() {
        // 识别结果即仓库事实：家具/碳/经验若真在仓库里就该记录
        repository.set(listOf(DepotItem("3401", 12), DepotItem("5001", 999)))

        assertEquals(mapOf("3401" to 12, "5001" to 999), repository.snapshot.value.items)
    }

    @Test
    fun merge_addsToExistingItem() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.merge(listOf("30011" to 20))

        assertEquals(120, repository.countOf("30011"))
    }

    @Test
    fun merge_insertsUnknownItem() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.merge(listOf("30012" to 5))

        assertEquals(mapOf("30011" to 100, "30012" to 5), repository.snapshot.value.items)
    }

    @Test
    fun merge_accumulatesOnlyEligibleEntries() {
        repository.merge(
            listOf(
                "3401" to 10,
                "3112" to 10,
                "3113" to 10,
                "3114" to 10,
                "5001" to 10,
                "furni" to 3,
                "" to 3,
                "30011abc" to 3,
                "30011" to 0,
                "30012" to -5,
                "30013" to 7,
            )
        )

        assertEquals(mapOf("30013" to 7), repository.snapshot.value.items)
    }

    @Test
    fun merge_withNoEligibleEntries_writesNothing() {
        repository.merge(listOf("3401" to 10, "furni" to 3, "30011" to 0))

        assertEquals(emptyMap<String, Int>(), repository.snapshot.value.items)
        assertEquals(0L, repository.snapshot.value.syncTimeMillis)
    }

    @Test
    fun merge_doesNotTouchSyncTime() {
        repository.set(listOf(DepotItem("30011", 100)))
        val syncTime = repository.snapshot.value.syncTimeMillis

        repository.merge(listOf("30011" to 20))

        assertEquals(120, repository.countOf("30011"))
        assertEquals(
            "syncTime 表示上次完整识别时间，掉落累加不应改变它",
            syncTime,
            repository.snapshot.value.syncTimeMillis,
        )
    }

    @Test
    fun merge_accumulatesAcrossRepeatedCalls() {
        repeat(5) { repository.merge(listOf("30011" to 1)) }

        assertEquals(5, repository.countOf("30011"))
    }

    /**
     * 护栏：磁盘只写不读，多写不丢累加。
     */
    @Test
    fun merge_neverLosesUpdates_acrossManyWrites() {
        repeat(50) { repository.merge(listOf("30011" to 1)) }

        assertEquals(50, repository.countOf("30011"))
    }

    @Test
    fun diskWriteAfterHydrate_doesNotOverrideMemory() = runBlocking {
        repository.merge(listOf("30011" to 7))
        awaitSnapshot { it.items["30011"] == 7 }

        store.edit { prefs ->
            prefs[stringPreferencesKey("depot_$PROFILE_A")] =
                JsonUtils.common.encodeToString(DepotSnapshot(items = mapOf("30011" to 2)))
        }
        withTimeout(AWAIT_TIMEOUT_MS) { repeat(50) { yield() } }

        assertEquals("已加载的分片不得被磁盘值覆盖", 7, repository.countOf("30011"))
    }

    @Test
    fun concurrentMerge_doNotLoseUpdates() = runBlocking {
        (1..20).map { async { repository.merge(listOf("30011" to 1)) } }.awaitAll()

        assertEquals(20, repository.countOf("30011"))
    }

    @Test
    fun countOf_returnsZeroWhenAbsent() = runBlocking {
        awaitSnapshot { true }

        assertEquals(0, repository.countOf("30011"))
    }

    @Test
    fun syncTime_stampedAfterSet_includingEmptyWarehouse() {
        assertEquals(0L, repository.snapshot.value.syncTimeMillis)

        repository.set(emptyList())
        assertTrue(
            "已识别空仓应 stamp syncTime，按 count=0 算缺口",
            repository.snapshot.value.syncTimeMillis > 0L,
        )
        assertTrue(repository.snapshot.value.items.isEmpty())

        repository.set(listOf(DepotItem("30011", 1)))
        assertTrue(repository.snapshot.value.syncTimeMillis > 0L)
        assertEquals(1, repository.countOf("30011"))
    }

    @Test
    fun countOf_visibleImmediatelyAfterSet() {
        repository.set(listOf(DepotItem("30011", 90)))
        assertEquals(
            "TaskChainStart 重算必须立刻读到识别结果，不能等 DataStore",
            90,
            repository.countOf("30011"),
        )
    }

    @Test
    fun merge_visibleToCountOfImmediately() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.merge(listOf("30011" to 7))
        assertEquals(107, repository.countOf("30011"))
    }

    @Test
    fun start_removesShardWhenProfileDeleted() = runBlocking {
        repository.set(listOf(DepotItem("30011", 100)))
        awaitDiskShard()
        repository.start()
        withTimeout(AWAIT_TIMEOUT_MS) {
            while (profileDeleted.subscriptionCount.value == 0) yield()
        }

        profileDeleted.emit(PROFILE_A)

        withTimeout(AWAIT_TIMEOUT_MS) {
            while (rawShardOf(PROFILE_A) != null) yield()
        }
    }

    @Test
    fun profilesAreStoredInSeparateShards() = runBlocking {
        repository.set(listOf(DepotItem("30011", 100)))
        awaitDiskShard(PROFILE_A)

        activeProfileId.value = PROFILE_B
        repository.set(listOf(DepotItem("30012", 7)))
        awaitDiskShard(PROFILE_B)

        assertEquals(mapOf("30011" to 100), awaitDiskShard(PROFILE_A).items)
        assertEquals(mapOf("30012" to 7), awaitDiskShard(PROFILE_B).items)
    }

    @Test
    fun switchingProfile_swapsSnapshot() = runBlocking {
        repository.set(listOf(DepotItem("30011", 100)))
        awaitSnapshot { it.items.containsKey("30011") }

        activeProfileId.value = PROFILE_B
        assertEquals(emptyMap<String, Int>(), awaitSnapshot { it.items.isEmpty() }.items)

        activeProfileId.value = PROFILE_A
        assertEquals(100, awaitSnapshot { it.items.containsKey("30011") }.items["30011"])
    }

    @Test
    fun corruptedShard_fallsBackToEmptySnapshotOnPreload() = runBlocking {
        val freshStore = FakePreferencesDataStore()
        freshStore.edit { it[stringPreferencesKey("depot_$PROFILE_A")] = "{ this is not json" }

        val freshRepository = DepotRepository(freshStore, fakeChainState())

        withTimeout(AWAIT_TIMEOUT_MS) { freshRepository.isLoaded.first { it } }
        assertEquals(emptyMap<String, Int>(), freshRepository.snapshot.value.items)
        assertEquals(0, freshRepository.countOf("30011"))
    }

    @Test
    fun emptyProfileId_skipsWrite() {
        activeProfileId.value = ""

        repository.set(listOf(DepotItem("30011", 100)))

        assertEquals(emptyMap<String, Int>(), repository.snapshot.value.items)
    }

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
