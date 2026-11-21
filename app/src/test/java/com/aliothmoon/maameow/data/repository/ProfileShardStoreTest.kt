package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * [ProfileShardStore] 的并发与生命周期契约。
 *
 * 仓库特有的语义（排除集、syncTime、缺口查询）在各自的 `*RepositoryTest` 里测，
 * 这里只测共享机制，避免同一份并发逻辑被测两遍。
 */
class ProfileShardStoreTest {

    private val activeProfileId = MutableStateFlow(PROFILE_A)
    private val profileDeleted = MutableSharedFlow<String>(extraBufferCapacity = 8)

    // 桩里引用测试字段必须全限定：mockk{} 块内 `this` 是 mock 本身，
    // 裸写 profileDeleted 会解析成 mock 的同名属性，变成未打桩的递归调用
    private fun fakeChainState(
        profileIdFlow: StateFlow<String> = activeProfileId,
        profileIds: List<String>? = null,
    ): TaskChainState = mockk {
        every { this@mockk.profileId } returns profileIdFlow
        every { isLoaded } returns MutableStateFlow(true)
        every { this@mockk.profileDeleted } returns this@ProfileShardStoreTest.profileDeleted
        if (profileIds != null) {
            val known = profileIds.map { pid ->
                mockk<TaskProfile> { every { this@mockk.id } returns pid }
            }
            every { this@mockk.profiles } returns MutableStateFlow(known)
        }
    }

    private fun fakeChainState(profilesFlow: MutableStateFlow<List<TaskProfile>>): TaskChainState =
        mockk {
            every { this@mockk.profileId } returns activeProfileId
            every { isLoaded } returns MutableStateFlow(true)
            every { this@mockk.profileDeleted } returns this@ProfileShardStoreTest.profileDeleted
            every { this@mockk.profiles } returns profilesFlow
        }

    private fun storeOf(
        store: DataStore<Preferences>,
        chainState: TaskChainState = fakeChainState(),
    ) = ProfileShardStore(
        store = store,
        taskChainState = chainState,
        keyPrefix = PREFIX,
        serializer = TestShard.serializer(),
        empty = ::TestShard,
    )

    private suspend fun rawShardOf(store: DataStore<Preferences>, profileId: String): String? =
        store.data.first()[stringPreferencesKey("$PREFIX$profileId")]

    private suspend fun storedValueOf(store: DataStore<Preferences>, profileId: String): Int? =
        rawShardOf(store, profileId)
            ?.let { JsonUtils.common.decodeFromString<TestShard>(it).value }

    private suspend fun seed(store: DataStore<Preferences>, profileId: String, value: Int) {
        store.edit {
            it[stringPreferencesKey("$PREFIX$profileId")] =
                JsonUtils.common.encodeToString(TestShard(value))
        }
    }

    private suspend fun ProfileShardStore<TestShard>.awaitSnapshot(
        predicate: (TestShard) -> Boolean,
    ): TestShard = withTimeout(TIMEOUT_MS) { snapshot.first(predicate) }

    private suspend fun ProfileShardStore<TestShard>.awaitLoaded() {
        withTimeout(TIMEOUT_MS) { isLoaded.first { it } }
    }

    // ---------------------------------------------------------------- 预热

    /**
     * 核心回归：一次读盘装载全部分片，切档不再需要读盘。
     *
     * 旧实现按档懒加载，切到未加载的档要挂起读盘，期间快照还是上一个档的数据
     * （定时任务冷启动切档后立即启动即命中）。断言读盘次数为 1 直接锁死这一点。
     */
    @Test
    fun preload_loadsAllShardsInOneRead() = runBlocking {
        val store = FakePreferencesDataStore()
        seed(store, PROFILE_A, 1)
        seed(store, PROFILE_B, 2)
        store.dataCollectCount.set(0)

        val shards = storeOf(store)
        shards.awaitLoaded()
        assertEquals(1, shards.awaitSnapshot { it.value == 1 }.value)

        activeProfileId.value = PROFILE_B
        assertEquals(2, shards.awaitSnapshot { it.value == 2 }.value)

        activeProfileId.value = PROFILE_A
        assertEquals(1, shards.awaitSnapshot { it.value == 1 }.value)

        assertEquals("装载全部分片只应读一次盘", 1, store.dataCollectCount.get())
    }

    /**
     * 孤儿分片不能当有效基线：删除事件可能丢（无订阅者）或落盘失败，
     * 重启 load() 若原样灌进内存，`countOf/hasSynced` 会把死档当有效数据。
     */
    @Test
    fun load_dropsOrphanShardNotInProfiles() = runBlocking {
        val store = FakePreferencesDataStore()
        seed(store, PROFILE_A, 1)
        seed(store, PROFILE_B, 2) // B 已从配置档列表里消失

        val shards = storeOf(store, fakeChainState(profileIds = listOf(PROFILE_A)))
        shards.awaitLoaded()
        assertEquals(1, shards.allShards.value[PROFILE_A]?.value)
        assertFalse(
            "已删除的档不能出现在 allShards 里",
            shards.allShards.value.containsKey(PROFILE_B),
        )
        // 磁盘上也要清掉，否则下次重启又读回来
        withTimeout(TIMEOUT_MS) {
            while (rawShardOf(store, PROFILE_B) != null) yield()
        }
    }

    /** 未加载过的档、盘上确实没有的档，一律是空快照而非上一个档的残留。 */
    @Test
    fun unknownProfile_publishesEmptyInsteadOfPreviousProfileData() = runBlocking {
        val store = FakePreferencesDataStore()
        seed(store, PROFILE_A, 42)

        val shards = storeOf(store)
        assertEquals(42, shards.awaitSnapshot { it.value == 42 }.value)

        activeProfileId.value = PROFILE_C
        assertEquals(TestShard(), shards.awaitSnapshot { it == TestShard() })
    }

    /** 读盘失败必须放行 isLoaded，否则一次 IO 故障永久卡死所有等待它的调用方。 */
    @Test
    fun readFailure_stillReleasesGate() = runBlocking {
        val shards = storeOf(FailingReadDataStore())

        shards.awaitLoaded()
        assertEquals(TestShard(), shards.snapshot.value)
    }

    /** 预热挂起期间发生的写入不得被回填覆盖 —— 内存永远优先于磁盘。 */
    @Test
    fun writeDuringPreload_winsOverDiskValue() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val backing = FakePreferencesDataStore()
        seed(backing, PROFILE_A, 5)
        val shards = storeOf(GatedReadDataStore(backing, gate))

        shards.mutate { it.copy(value = 99) }
        gate.complete(Unit)
        shards.awaitLoaded()

        assertEquals("盘上的旧值不得回冲内存", 99, shards.snapshot.value.value)
    }

    // ---------------------------------------------------------------- 核心不变量

    /**
     * 回归 F0：mutate 过程中 active 已切到 B（仅改 value、不 emission），
     * 派生 snapshot 须按 profileId.value 读 map，不得仍展示 A 的写入结果。
     */
    @Test
    fun switchDuringMutate_doesNotPublishStaleProfileData() = runBlocking {
        val profileId = SwitchOnDemandProfileId(PROFILE_A)
        val store = FakePreferencesDataStore()
        val shards = storeOf(store, fakeChainState(profileId))
        shards.awaitLoaded()

        shards.mutate { current ->
            profileId.switchTo(PROFILE_B)      // 捕获之后、发布之前
            current.copy(value = 777)
        }

        // 写入本身照常落到 A 的分片
        shards.awaitPersist()
        assertEquals(777, storedValueOf(store, PROFILE_A))
        assertNull(rawShardOf(store, PROFILE_B))
        // 但活跃档已是 B，快照不得携带 A 的数据
        assertEquals(TestShard(), shards.snapshot.value)
    }

    // ---------------------------------------------------------------- 写回队列

    @Test
    fun awaitPersist_seesAllPriorWrites() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()

        repeat(10) { shards.mutate { s -> s.copy(value = s.value + 1) } }
        shards.awaitPersist()

        assertEquals(10, storedValueOf(store, PROFILE_A))
    }

    @Test
    fun awaitPersist_throwsWhenWriteFails() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()

        store.failWrites = true
        shards.mutate { it.copy(value = 1) }
        try {
            shards.awaitPersist()
            fail("写盘失败时 awaitPersist 必须抛 IOException")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("fake write") == true)
        }
        // 内存仍是新值；磁盘未确认
        assertEquals(1, shards.snapshot.value.value)
        assertNull(rawShardOf(store, PROFILE_A))
    }

    @Test
    fun awaitPersist_succeedsAfterRecoveredWrite() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()

        store.failWrites = true
        shards.mutate { it.copy(value = 1) }
        runCatching { shards.awaitPersist() }

        store.failWrites = false
        shards.mutate { it.copy(value = 2) }
        shards.awaitPersist()

        assertEquals(2, storedValueOf(store, PROFILE_A))
    }

    @Test
    fun concurrentMutations_doNotLoseUpdates() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()

        (1..50).map { async { shards.mutate { s -> s.copy(value = s.value + 1) } } }.awaitAll()
        shards.awaitPersist()

        assertEquals(50, shards.snapshot.value.value)
        assertEquals(50, storedValueOf(store, PROFILE_A))
    }

    /** 一次 mutate 对应一次磁盘写（不做合并）；生产掉落/识别不成突发。 */
    @Test
    fun eachMutationProducesExactlyOneWrite() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()
        store.editCount.set(0)

        repeat(10) { shards.mutate { s -> s.copy(value = s.value + 1) } }
        shards.awaitPersist()

        assertEquals(10, storedValueOf(store, PROFILE_A))
        assertEquals("一次 mutate 一次写", 10, store.editCount.get())
    }

    /** 同一分片的多条 Write 是幂等的（整快照覆盖），最后一条即最终态。 */
    @Test
    fun repeatedWrites_areIdempotentFullSnapshots() = runBlocking {
        val store = FakePreferencesDataStore(writeDelayMs = 3L)
        val shards = storeOf(store)
        shards.awaitLoaded()

        repeat(20) { shards.mutate { s -> s.copy(value = s.value + 1) } }
        shards.awaitPersist()

        assertEquals(20, storedValueOf(store, PROFILE_A))
        assertEquals(20, shards.snapshot.value.value)
    }

    /** 删除排在在途写之后：先写后删，磁盘最终干净。 */
    @Test
    fun deleteAfterPendingWrite_leavesNoOrphanKey() = runBlocking {
        val store = FakePreferencesDataStore(writeDelayMs = 5L)
        val shards = storeOf(store)
        shards.awaitLoaded()

        repeat(5) { shards.mutate { s -> s.copy(value = s.value + 1) } }
        shards.remove(PROFILE_A)          // 不等落盘，直接排队删除
        shards.awaitPersist()

        assertNull("删除必须晚于在途写入生效", rawShardOf(store, PROFILE_A))
    }

    /** 删除排在写之前：后续 Sync 发现分片已不在内存，直接跳过，不复活旧值。 */
    @Test
    fun mutateFor_profileMissingFromState_isIgnored() = runBlocking {
        val store = FakePreferencesDataStore()
        // 只有 PROFILE_A 存在：向不存在的 PROFILE_B 写入必须被丢弃，
        // 否则会在盘上留下一个永远无人认领、还会被当成基线读回来的幽灵分片
        val shards = storeOf(store, fakeChainState(profileIds = listOf(PROFILE_A)))
        shards.awaitLoaded()

        val written = shards.mutateFor(PROFILE_B) { it.copy(value = 9) }

        assertNull(written)
        assertNull(rawShardOf(store, PROFILE_B))
    }

    @Test
    fun mutateFor_profileStillListed_isWritten() = runBlocking {
        val store = FakePreferencesDataStore()
        // B 仍在配置档列表里：闸门只挡「已删除」，不能把正常写入一起挡掉
        val shards = storeOf(store, fakeChainState(profileIds = listOf(PROFILE_A, PROFILE_B)))
        shards.awaitLoaded()

        val written = shards.mutateFor(PROFILE_B) { it.copy(value = 9) }

        assertNotNull(written)
        shards.awaitPersist()
        assertEquals(9, storedValueOf(store, PROFILE_B))
    }

    @Test
    fun mutateFor_unchangedTransform_createsNoShard() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()

        // 未识别过库存的档：transform 原样返回时不该建键、不该落盘
        val written = shards.mutateFor(PROFILE_B) { it }

        assertNull(written)
        assertNull(rawShardOf(store, PROFILE_B))
    }

    @Test
    fun writeAfterDelete_doesNotResurrectShard() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()

        shards.mutate { it.copy(value = 3) }
        shards.awaitPersist()
        assertEquals(3, storedValueOf(store, PROFILE_A))

        shards.remove(PROFILE_A)
        shards.awaitPersist()

        assertNull(rawShardOf(store, PROFILE_A))
        assertEquals(TestShard(), shards.snapshot.value)
    }

    @Test
    fun start_dropsShardWhenProfileDeleted() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()
        shards.mutate { it.copy(value = 8) }
        shards.awaitPersist()

        shards.start()
        // 订阅在 IO 调度器上异步建立，emit 早于订阅会丢事件（replay=0）
        withTimeout(TIMEOUT_MS) {
            while (profileDeleted.subscriptionCount.value == 0) yield()
        }

        profileDeleted.emit(PROFILE_A)

        withTimeout(TIMEOUT_MS) {
            while (rawShardOf(store, PROFILE_A) != null) yield()
        }
    }

    /**
     * 删档事件是异步送达的：送达前用户可能又导入了含同 id 的备份。
     * 这时列表里已经有这个 id，remove 必须跳过，否则会把刚重建的档的分片一起抹掉。
     */
    @Test
    fun remove_skipsWhenProfileReAddedBeforeDeleteRuns() = runBlocking {
        val store = FakePreferencesDataStore()
        val profileA = mockk<TaskProfile> { every { id } returns PROFILE_A }
        val profiles = MutableStateFlow(listOf(profileA))
        val shards = storeOf(store, fakeChainState(profiles))
        shards.awaitLoaded()
        shards.mutate { it.copy(value = 7) }
        shards.awaitPersist()

        shards.remove(PROFILE_A)
        shards.awaitPersist()

        assertEquals("配置档仍在列表里，分片不能被删除", 7, storedValueOf(store, PROFILE_A))
    }

    @Test
    fun emptyProfileId_skipsWrite() = runBlocking {
        val store = FakePreferencesDataStore()
        val shards = storeOf(store)
        shards.awaitLoaded()
        activeProfileId.value = ""

        shards.mutate { it.copy(value = 1) }
        shards.awaitPersist()

        assertNull(rawShardOf(store, ""))
    }

    @Test
    fun mutateFor_nonActiveProfile_usesTargetBaseline() = runBlocking {
        val store = FakePreferencesDataStore()
        seed(store, PROFILE_A, 5)
        val shards = storeOf(store)
        shards.awaitLoaded()

        shards.mutateFor(PROFILE_A) { it.copy(value = it.value + 1) }
        shards.awaitPersist()

        assertEquals(6, storedValueOf(store, PROFILE_A))
    }

    @Test
    fun corruptedShard_fallsBackToEmpty() = runBlocking {
        val store = FakePreferencesDataStore()
        store.edit { it[stringPreferencesKey("$PREFIX$PROFILE_A")] = "{ this is not json" }

        val shards = storeOf(store)
        shards.awaitLoaded()

        // 回退为空快照而不是抛异常掀翻装载协程
        assertEquals(TestShard(), shards.snapshot.value)
    }

    @Test
    fun foreignKeysInSameStore_areIgnored() = runBlocking {
        val store = FakePreferencesDataStore()
        seed(store, PROFILE_A, 4)
        store.edit { it[stringPreferencesKey("other_$PROFILE_A")] = "not ours" }

        val shards = storeOf(store)
        shards.awaitLoaded()

        assertEquals(4, shards.awaitSnapshot { it.value == 4 }.value)
    }

    @Serializable
    private data class TestShard(val value: Int = 0)

    /**
     * 活跃档替身：[switchTo] 只改 [value]，**不产生 emission**。
     *
     * 用它把「读到的活跃档已经变了，但收集器不会再来纠正」这个状态固定住 ——
     * 普通 StateFlow 做不到，因为 emission 必然唤醒收集器。
     */
    private class SwitchOnDemandProfileId(initial: String) : StateFlow<String> {
        private val delegate = MutableStateFlow(initial)

        @Volatile
        private var current: String = initial

        fun switchTo(profileId: String) {
            current = profileId
        }

        override val value: String get() = current
        override val replayCache: List<String> get() = delegate.replayCache
        override suspend fun collect(collector: FlowCollector<String>): Nothing =
            delegate.collect(collector)
    }

    /** `data` 永远抛 IOException，用于验证读盘失败仍放行闸门。 */
    private class FailingReadDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("boom") }
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException("boom")
    }

    /** `data` 的首次读取挂起到 [gate] 放行，用于制造预热窗口。 */
    private class GatedReadDataStore(
        private val delegate: DataStore<Preferences>,
        private val gate: CompletableDeferred<Unit>,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            gate.await()
            emitAll(delegate.data)
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = delegate.updateData(transform)
    }

    private companion object {
        const val PREFIX = "shard_"
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
        const val PROFILE_C = "profile-c"
        const val TIMEOUT_MS = 5_000L
    }
}
