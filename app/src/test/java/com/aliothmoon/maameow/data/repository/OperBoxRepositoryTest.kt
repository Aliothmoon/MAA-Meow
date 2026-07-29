package com.aliothmoon.maameow.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
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
 * 干员箱自身的语义：全量覆盖、识别时间、分片隔离。
 *
 * 分片装载、写回队列等共享机制在 [ProfileShardStoreTest] 里测。
 */
class OperBoxRepositoryTest {

    private val activeProfileId = MutableStateFlow(PROFILE_A)
    private val profileDeleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private lateinit var store: FakePreferencesDataStore
    private lateinit var repository: OperBoxRepository

    @Before
    fun setUp() {
        store = FakePreferencesDataStore()
        repository = OperBoxRepository(store, fakeChainState())
    }

    private fun fakeChainState(): TaskChainState = mockk {
        every { this@mockk.profileId } returns this@OperBoxRepositoryTest.activeProfileId
        every { isLoaded } returns MutableStateFlow(true)
        every { this@mockk.profileDeleted } returns this@OperBoxRepositoryTest.profileDeleted
    }

    private suspend fun rawShardOf(profileId: String): String? =
        store.data.first()[stringPreferencesKey("operbox_$profileId")]

    private suspend fun awaitDiskShard(profileId: String = PROFILE_A): OperBoxSnapshot =
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

    private suspend fun awaitSnapshot(predicate: (OperBoxSnapshot) -> Boolean): OperBoxSnapshot =
        withTimeout(AWAIT_TIMEOUT_MS) { repository.snapshot.first(predicate) }

    private fun sampleOwned() = listOf(
        OperBoxOperator("char_002_amiya", "阿米娅", 5, 2, 80, 1, true),
    )

    private fun sampleNotOwned() = listOf(
        OperBoxOperator("char_1001_amiya2", "阿米娅（近卫）", 6, 0, 0, 0, false),
    )

    @Test
    fun initialSnapshot_isEmpty() = runBlocking {
        assertEquals(OperBoxSnapshot(), awaitSnapshot { true })
        assertFalse(awaitSnapshot { true }.hasSynced)
    }

    @Test
    fun set_updatesMemoryAndStampsSyncTime() {
        repository.set(sampleOwned(), sampleNotOwned())

        val snap = repository.snapshot.value
        assertEquals(sampleOwned(), snap.owned)
        assertEquals(sampleNotOwned(), snap.notOwned)
        assertTrue(snap.hasSynced)
        assertTrue(snap.syncTimeMillis > 0)
    }

    @Test
    fun set_overwritesInsteadOfMerging() {
        repository.set(sampleOwned(), sampleNotOwned())
        val onlyOwned = listOf(
            OperBoxOperator("char_003_kalts", "凯尔希", 6, 2, 90, 5, true),
        )
        repository.set(onlyOwned, emptyList())

        val snap = repository.snapshot.value
        assertEquals(onlyOwned, snap.owned)
        assertTrue(snap.notOwned.isEmpty())
    }

    @Test
    fun start_removesShardWhenProfileDeleted() = runBlocking {
        repository.set(sampleOwned(), emptyList())
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
        repository.set(sampleOwned(), emptyList())
        awaitDiskShard(PROFILE_A)

        activeProfileId.value = PROFILE_B
        repository.set(sampleNotOwned().map { it.copy(own = true) }, emptyList())
        awaitDiskShard(PROFILE_B)

        assertEquals(1, awaitDiskShard(PROFILE_A).owned.size)
        assertEquals("char_002_amiya", awaitDiskShard(PROFILE_A).owned.single().id)
        assertEquals(1, awaitDiskShard(PROFILE_B).owned.size)
        assertEquals("char_1001_amiya2", awaitDiskShard(PROFILE_B).owned.single().id)
    }

    @Test
    fun switchingProfile_swapsSnapshot() = runBlocking {
        repository.set(sampleOwned(), emptyList())
        awaitSnapshot { it.owned.isNotEmpty() }

        activeProfileId.value = PROFILE_B
        assertTrue(awaitSnapshot { it.owned.isEmpty() }.owned.isEmpty())

        activeProfileId.value = PROFILE_A
        assertEquals("阿米娅", awaitSnapshot { it.owned.isNotEmpty() }.owned.single().name)
    }

    /**
     * 回归 F4：冷启动装载完成后，`syncTimeMillis` 必须是盘上的真值。
     */
    @Test
    fun preload_exposesPersistedSyncTimeOnceLoaded() = runBlocking {
        val freshStore = FakePreferencesDataStore()
        freshStore.edit {
            it[stringPreferencesKey("operbox_$PROFILE_A")] = JsonUtils.common.encodeToString(
                OperBoxSnapshot(owned = sampleOwned(), syncTimeMillis = 1_700_000_000_000L)
            )
        }

        val freshRepository = OperBoxRepository(freshStore, fakeChainState())
        withTimeout(AWAIT_TIMEOUT_MS) { freshRepository.isLoaded.first { it } }

        assertTrue("装载完成后不得再读到 syncTime=0", freshRepository.snapshot.value.hasSynced)
        assertEquals(1_700_000_000_000L, freshRepository.snapshot.value.syncTimeMillis)
    }

    @Test
    fun corruptedShard_fallsBackToEmptySnapshotOnPreload() = runBlocking {
        val freshStore = FakePreferencesDataStore()
        freshStore.edit { it[stringPreferencesKey("operbox_$PROFILE_A")] = "{ this is not json" }

        val freshRepository = OperBoxRepository(freshStore, fakeChainState())
        withTimeout(AWAIT_TIMEOUT_MS) { freshRepository.isLoaded.first { it } }

        assertEquals(OperBoxSnapshot(), freshRepository.snapshot.value)
        assertFalse(freshRepository.snapshot.value.hasSynced)
    }

    @Test
    fun emptyProfileId_skipsWrite() {
        activeProfileId.value = ""

        repository.set(sampleOwned(), emptyList())

        assertEquals(OperBoxSnapshot(), repository.snapshot.value)
    }

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
