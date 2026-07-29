package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 用内存 [FakePreferencesDataStore] 而非 `PreferenceDataStoreFactory`：
 * 后者在 Windows JVM 上第二次写入必然失败（临时文件无法 rename 覆盖已存在的目标），
 * 会让「多次写入」类用例变成平台缺陷的牺牲品。内存实现保留 updateData 的串行语义，
 * 测的是本类逻辑而非 DataStore 的文件 IO。
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
        every { this@mockk.activeProfileId } returns this@DepotRepositoryTest.activeProfileId
        every { isLoaded } returns MutableStateFlow(true)
        every { this@mockk.profileDeleted } returns this@DepotRepositoryTest.profileDeleted
    }

    /** 直接读存储解码，用于断言「写入已发生」这类确定性事实。 */
    private suspend fun storedSnapshot(profileId: String = PROFILE_A): DepotSnapshot =
        rawShardOf(profileId)?.let { JsonUtils.common.decodeFromString<DepotSnapshot>(it) }
            ?: DepotSnapshot()

    private suspend fun rawShardOf(profileId: String): String? =
        store.data.first()[stringPreferencesKey("depot_$profileId")]

    /**
     * 等待 StateFlow 传播，仅用于断言「快照跟随变化」。谓词必须描述终态 ——
     * StateFlow 是 conflated 的，中间态可能被跳过。
     */
    private suspend fun awaitSnapshot(predicate: (DepotSnapshot) -> Boolean): DepotSnapshot =
        withTimeout(AWAIT_TIMEOUT_MS) { repository.snapshot.first(predicate) }

    @Test
    fun initialSnapshot_isEmpty() = runBlocking {
        assertEquals(DepotSnapshot(), awaitSnapshot { true })
    }

    @Test
    fun replaceAll_storesItemsAndStampsSyncTime() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 200), DepotItem("30012", 5)))

        val stored = storedSnapshot()
        assertEquals(mapOf("30011" to 200, "30012" to 5), stored.items)
        assertTrue("replaceAll 必须刷新识别时间", stored.syncTimeMillis > 0)
    }

    @Test
    fun replaceAll_overwritesInsteadOfMerging() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 200)))

        repository.replaceAll(listOf(DepotItem("30012", 5)))

        assertEquals(mapOf("30012" to 5), storedSnapshot().items)
    }

    @Test
    fun replaceAll_keepsExcludedIds() = runBlocking {
        // 识别结果即仓库事实：家具/碳/经验若真在仓库里就该记录
        repository.replaceAll(listOf(DepotItem("3401", 12), DepotItem("5001", 999)))

        assertEquals(mapOf("3401" to 12, "5001" to 999), storedSnapshot().items)
    }

    @Test
    fun applyDrops_addsToExistingItem() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))

        repository.applyDrops(listOf("30011" to 20))

        assertEquals(120, storedSnapshot().items["30011"])
    }

    @Test
    fun applyDrops_insertsUnknownItem() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))

        repository.applyDrops(listOf("30012" to 5))

        assertEquals(mapOf("30011" to 100, "30012" to 5), storedSnapshot().items)
    }

    @Test
    fun applyDrops_accumulatesOnlyEligibleEntries() = runBlocking {
        repository.applyDrops(
            listOf(
                "3401" to 10,      // 家具
                "3112" to 10,      // 碳
                "3113" to 10,
                "3114" to 10,
                "5001" to 10,      // 经验
                "furni" to 3,      // 非数字 id
                "" to 3,           // 空 id
                "30011abc" to 3,   // 混合 id
                "30011" to 0,      // 非正数
                "30012" to -5,
                "30013" to 7,      // 唯一合法项
            )
        )

        assertEquals(mapOf("30013" to 7), storedSnapshot().items)
    }

    @Test
    fun applyDrops_withNoEligibleEntries_writesNothing() = runBlocking {
        repository.applyDrops(listOf("3401" to 10, "furni" to 3, "30011" to 0))

        assertNull("全部被过滤时不应创建分片", rawShardOf(PROFILE_A))
    }

    @Test
    fun applyDrops_doesNotTouchSyncTime() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))
        val syncTime = storedSnapshot().syncTimeMillis

        repository.applyDrops(listOf("30011" to 20))

        val after = storedSnapshot()
        assertEquals(120, after.items["30011"])
        assertEquals(
            "syncTime 表示上次完整识别时间，掉落累加不应改变它",
            syncTime,
            after.syncTimeMillis,
        )
    }

    @Test
    fun applyDrops_accumulatesAcrossRepeatedCalls() = runBlocking {
        repeat(5) { repository.applyDrops(listOf("30011" to 1)) }

        assertEquals(5, storedSnapshot().items["30011"])
    }

    @Test
    fun concurrentApplyDrops_doNotLoseUpdates() = runBlocking {
        // 事务内读-改-写的核心保证：并发累加不丢更新
        (1..20).map { async { repository.applyDrops(listOf("30011" to 1)) } }.awaitAll()

        assertEquals(20, storedSnapshot().items["30011"])
    }

    @Test
    fun countOf_returnsZeroWhenAbsent() = runBlocking {
        awaitSnapshot { true }

        assertEquals(0, repository.countOf("30011"))
    }

    @Test
    fun countOf_reflectsPersistedItems() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 42)))
        awaitSnapshot { it.items.containsKey("30011") }

        assertEquals(42, repository.countOf("30011"))
    }

    @Test
    fun countOf_visibleImmediatelyAfterReplaceAllSync_withoutAwaitingStore() {
        // 模拟 MaaCore 回调线程：只 Sync 写内存，不等落盘
        repository.replaceAllSync(listOf(DepotItem("30011", 90)))
        assertEquals(
            "TaskChainStart 重算必须立刻读到识别结果，不能等 DataStore",
            90,
            repository.countOf("30011"),
        )
    }

    @Test
    fun applyDropsSync_visibleToCountOfBeforePersist() {
        repository.replaceAllSync(listOf(DepotItem("30011", 100)))
        repository.applyDropsSync(listOf("30011" to 7))
        assertEquals(107, repository.countOf("30011"))
    }

    @Test
    fun dropProfile_removesShard() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))

        repository.dropProfile(PROFILE_A)

        assertNull(rawShardOf(PROFILE_A))
        assertEquals(emptyMap<String, Int>(), awaitSnapshot { it.items.isEmpty() }.items)
    }

    @Test
    fun start_dropsShardWhenProfileDeleted() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))
        repository.start()
        // 订阅在 IO 调度器上异步建立，emit 早于订阅会丢事件（replay=0）
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
        repository.replaceAll(listOf(DepotItem("30011", 100)))

        activeProfileId.value = PROFILE_B
        repository.replaceAll(listOf(DepotItem("30012", 7)))

        assertEquals(mapOf("30011" to 100), storedSnapshot(PROFILE_A).items)
        assertEquals(mapOf("30012" to 7), storedSnapshot(PROFILE_B).items)
    }

    @Test
    fun switchingProfile_swapsSnapshot() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))
        awaitSnapshot { it.items.containsKey("30011") }

        activeProfileId.value = PROFILE_B
        assertEquals(emptyMap<String, Int>(), awaitSnapshot { it.items.isEmpty() }.items)

        activeProfileId.value = PROFILE_A
        assertEquals(100, awaitSnapshot { it.items.containsKey("30011") }.items["30011"])
    }

    @Test
    fun corruptedShard_fallsBackToEmptySnapshot() = runBlocking {
        repository.replaceAll(listOf(DepotItem("30011", 100)))
        awaitSnapshot { it.items.containsKey("30011") }

        store.edit { it[stringPreferencesKey("depot_$PROFILE_A")] = "{ this is not json" }

        // 由非空变为空，证明 decode 的失败回退分支确实被执行
        assertEquals(emptyMap<String, Int>(), awaitSnapshot { it.items.isEmpty() }.items)
    }

    @Test
    fun emptyProfileId_skipsWrite() = runBlocking {
        activeProfileId.value = ""

        repository.replaceAll(listOf(DepotItem("30011", 100)))

        assertNull(rawShardOf(""))
    }

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}

/** 内存 Preferences DataStore，`updateData` 与真实实现一样串行执行。 */
private class FakePreferencesDataStore : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())
    private val writeLock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = writeLock.withLock {
        transform(state.value).also { state.value = it }
    }
}
