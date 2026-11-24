package com.aliothmoon.maameow.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.TaskProfile
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

    private fun mockProfiles(ids: List<String>): List<TaskProfile> =
        ids.map { pid -> mockk<TaskProfile> { every { this@mockk.id } returns pid } }

    private fun fakeChainState(
        profileIds: List<String>? = null,
        profilesFlow: MutableStateFlow<List<TaskProfile>>? = null,
    ): TaskChainState = mockk {
        every { this@mockk.profileId } returns this@DepotRepositoryTest.activeProfileId
        every { isLoaded } returns MutableStateFlow(true)
        every { this@mockk.profileDeleted } returns this@DepotRepositoryTest.profileDeleted
        when {
            profilesFlow != null -> every { this@mockk.profiles } returns profilesFlow
            profileIds != null -> every { this@mockk.profiles } returns MutableStateFlow(mockProfiles(profileIds))
        }
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
    fun merge_intoNonActiveProfile_staysInTargetShard() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.set(listOf(DepotItem("30011", 80)), PROFILE_B)

        val written = repository.merge(listOf("30011" to 20), PROFILE_B)

        assertTrue(written)
        // 活跃档原样不动，累加落在目标档自己的分片上
        assertEquals(100, repository.countOf("30011"))
        assertEquals(100, repository.countOf("30011", PROFILE_B))
        assertTrue(repository.hasSynced(PROFILE_B))
    }

    @Test
    fun merge_intoProfileWithoutBaseline_isRejected() {
        // 没有识别基线时累加只会得到残缺数据，宁可丢弃（返回 false 让调用方报警）
        assertFalse(repository.merge(listOf("30011" to 20), PROFILE_B))
        assertEquals(0, repository.countOf("30011", PROFILE_B))
    }

    @Test
    fun merge_intoDeletedProfile_isRejected() = runBlocking {
        // 先给 B 建好基线（分片存在且 hasSynced=true），再把 B 从配置档列表里去掉：
        // 只有闸门生效时才会拒绝写入。以前这条用例里 B 连分片都没有，
        // 闸门即使完全失效也会走「无基线不建键」，三条断言照样全过（等于没测）
        repository.set(listOf(DepotItem("30011", 80)), PROFILE_B)
        withTimeout(AWAIT_TIMEOUT_MS) {
            while (rawShardOf(PROFILE_B) == null) yield()
        }

        val profilesFlow = MutableStateFlow(mockProfiles(listOf(PROFILE_A, PROFILE_B)))
        val target = DepotRepository(store, fakeChainState(profilesFlow = profilesFlow))
        withTimeout(AWAIT_TIMEOUT_MS) { target.isLoaded.first { it } }

        // 装载时 B 还在列表里（分片不会被当成孤儿清掉），装载后用户又删了 B
        profilesFlow.value = mockProfiles(listOf(PROFILE_A))

        val written = target.merge(listOf("30011" to 20), PROFILE_B)

        assertFalse(written)
        // 基线还在（80），没有被这次 merge 叠成 100
        assertEquals(80, target.countOf("30011", PROFILE_B))
    }

    @Test
    fun merge_insertsUnknownItem() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.merge(listOf("30012" to 5))

        assertEquals(mapOf("30011" to 100, "30012" to 5), repository.snapshot.value.items)
    }

    @Test
    fun merge_withoutRecognitionBaseline_writesNothing() {
        repository.merge(listOf("30011" to 20))

        assertEquals(emptyMap<String, Int>(), repository.snapshot.value.items)
        assertFalse(repository.snapshot.value.hasSynced)
    }

    @Test
    fun merge_accumulatesOnlyEligibleEntries() {
        repository.set(listOf(DepotItem("30011", 1)))
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

        assertEquals(mapOf("30011" to 1, "30013" to 7), repository.snapshot.value.items)
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
    fun set_firstScan_hasNoBaseline() {
        repository.set(listOf(DepotItem("30011", 100)))

        assertEquals(0L, repository.snapshot.value.prevSyncTimeMillis)
        assertNull(repository.snapshot.value.delta())
    }

    @Test
    fun set_returnsStoredSnapshot() {
        val stored = repository.set(listOf(DepotItem("30011", 100)))

        assertEquals(repository.snapshot.value, stored)
    }

    @Test
    fun set_withoutActiveProfile_returnsNull() {
        activeProfileId.value = ""

        assertNull(repository.set(listOf(DepotItem("30011", 100))))
    }

    @Test
    fun set_shiftsPreviousScanIntoBaseline() {
        repository.set(listOf(DepotItem("30011", 100)))
        val firstSync = repository.snapshot.value.syncTimeMillis
        repository.set(listOf(DepotItem("30012", 5)))

        val snap = repository.snapshot.value
        assertEquals(mapOf("30012" to 5), snap.scanItems)
        assertEquals(mapOf("30011" to 100), snap.prevScanItems)
        assertEquals(firstSync, snap.prevSyncTimeMillis)
    }

    @Test
    fun merge_doesNotTouchScanItems() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.merge(listOf("30011" to 20))

        assertEquals(mapOf("30011" to 100), repository.snapshot.value.scanItems)
    }

    @Test
    fun delta_excludesDropsMergedBetweenScans() {
        repository.set(listOf(DepotItem("30011", 100)))
        repository.merge(listOf("30011" to 20))
        repository.set(listOf(DepotItem("30011", 130)))

        assertEquals(mapOf("30011" to 30), repository.snapshot.value.delta()?.changes)
    }

    @Test
    fun set_afterLegacySnapshot_hasNoBaseline() = runBlocking {
        val legacyStore = FakePreferencesDataStore()
        legacyStore.edit {
            it[stringPreferencesKey("depot_$PROFILE_A")] =
                """{"items":{"30011":120},"syncTimeMillis":1000}"""
        }
        val legacy = DepotRepository(legacyStore, fakeChainState())
        withTimeout(AWAIT_TIMEOUT_MS) { legacy.isLoaded.first { it } }
        assertEquals(120, legacy.countOf("30011"))

        legacy.set(listOf(DepotItem("30011", 150)))

        assertNull("旧数据 items 混了掉落，不能当基线", legacy.snapshot.value.delta())
    }

    @Test
    fun merge_accumulatesAcrossRepeatedCalls() {
        // 掉落累加需要识别基线
        repository.set(listOf(DepotItem("30012", 1)))
        repeat(5) { repository.merge(listOf("30011" to 1)) }

        assertEquals(5, repository.countOf("30011"))
    }

    /**
     * 护栏：磁盘只写不读，多写不丢累加。
     */
    @Test
    fun merge_neverLosesUpdates_acrossManyWrites() {
        // 掉落累加需要识别基线
        repository.set(listOf(DepotItem("30012", 1)))
        repeat(50) { repository.merge(listOf("30011" to 1)) }

        assertEquals(50, repository.countOf("30011"))
    }

    @Test
    fun diskWriteAfterHydrate_doesNotOverrideMemory() = runBlocking {
        // 掉落累加需要识别基线
        repository.set(listOf(DepotItem("30012", 1)))
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
        // 掉落累加需要识别基线
        repository.set(listOf(DepotItem("30012", 1)))
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
    fun merge_shardExistsButNotSynced_isRejected() = runBlocking {
        // 清空某档后分片还在、hasSynced=false：这时 transform 原样返回，
        // 老实现拿「写入结果值」判成功 → merge 返回 true 但一个数都没落（本提交修的就是这条）
        repository.set(listOf(DepotItem("30011", 80)))
        awaitDiskShard(PROFILE_A)
        repository.clear(PROFILE_A)
        awaitSnapshot { !it.hasSynced }
        // 清空是异步落盘的：不等盘上也变空，下面读到的会是 set 那一版的旧时间戳
        withTimeout(AWAIT_TIMEOUT_MS) {
            while (awaitDiskShard(PROFILE_A).hasSynced) yield()
        }

        val written = repository.merge(listOf("30011" to 5))

        assertFalse("分片存在但没有识别基线时不得报成功", written)
        assertEquals(0, repository.countOf("30011"))
        // 断言「没写进去」要看内容：merge 本来就不改 syncTimeMillis，那个断言恒真
        assertTrue("也不该把掉落写进空分片", awaitDiskShard(PROFILE_A).items.isEmpty())
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
