package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 内存 DataStore 单测，与 [DepotRepositoryTest] 对称。
 * 不测真实 PreferenceDataStoreFactory（Windows 二次写入 rename 会失败）。
 */
class OperBoxRepositoryTest {

    private val activeProfileId = MutableStateFlow(PROFILE_A)
    private val profileDeleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private lateinit var store: FakeOperBoxPreferencesDataStore
    private lateinit var repository: OperBoxRepository

    @Before
    fun setUp() {
        store = FakeOperBoxPreferencesDataStore()
        repository = OperBoxRepository(store, fakeChainState())
    }

    private fun fakeChainState(): TaskChainState = mockk {
        every { this@mockk.activeProfileId } returns this@OperBoxRepositoryTest.activeProfileId
        every { isLoaded } returns MutableStateFlow(true)
        every { this@mockk.profileDeleted } returns this@OperBoxRepositoryTest.profileDeleted
    }

    private suspend fun storedSnapshot(profileId: String = PROFILE_A): OperBoxSnapshot =
        rawShardOf(profileId)?.let { JsonUtils.common.decodeFromString<OperBoxSnapshot>(it) }
            ?: OperBoxSnapshot()

    private suspend fun rawShardOf(profileId: String): String? =
        store.data.first()[stringPreferencesKey("operbox_$profileId")]

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
    fun replaceAll_storesListsAndStampsSyncTime() = runBlocking {
        repository.replaceAll(sampleOwned(), sampleNotOwned())

        val stored = storedSnapshot()
        assertEquals(sampleOwned(), stored.owned)
        assertEquals(sampleNotOwned(), stored.notOwned)
        assertTrue("replaceAll 必须刷新识别时间", stored.syncTimeMillis > 0)
        assertTrue(stored.hasSynced)
    }

    @Test
    fun replaceAll_overwritesInsteadOfMerging() = runBlocking {
        repository.replaceAll(sampleOwned(), sampleNotOwned())
        val onlyOwned = listOf(
            OperBoxOperator("char_003_kalts", "凯尔希", 6, 2, 90, 5, true),
        )
        repository.replaceAll(onlyOwned, emptyList())

        val stored = storedSnapshot()
        assertEquals(onlyOwned, stored.owned)
        assertTrue(stored.notOwned.isEmpty())
    }

    @Test
    fun dropProfile_removesShard() = runBlocking {
        repository.replaceAll(sampleOwned(), emptyList())

        repository.dropProfile(PROFILE_A)

        assertNull(rawShardOf(PROFILE_A))
        assertFalse(awaitSnapshot { !it.hasSynced }.hasSynced)
    }

    @Test
    fun start_dropsShardWhenProfileDeleted() = runBlocking {
        repository.replaceAll(sampleOwned(), emptyList())
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
        repository.replaceAll(sampleOwned(), emptyList())

        activeProfileId.value = PROFILE_B
        repository.replaceAll(sampleNotOwned().map { it.copy(own = true) }, emptyList())

        assertEquals(1, storedSnapshot(PROFILE_A).owned.size)
        assertEquals("char_002_amiya", storedSnapshot(PROFILE_A).owned.single().id)
        assertEquals(1, storedSnapshot(PROFILE_B).owned.size)
        assertEquals("char_1001_amiya2", storedSnapshot(PROFILE_B).owned.single().id)
    }

    @Test
    fun switchingProfile_swapsSnapshot() = runBlocking {
        repository.replaceAll(sampleOwned(), emptyList())
        awaitSnapshot { it.owned.isNotEmpty() }

        activeProfileId.value = PROFILE_B
        assertTrue(awaitSnapshot { it.owned.isEmpty() }.owned.isEmpty())

        activeProfileId.value = PROFILE_A
        assertEquals("阿米娅", awaitSnapshot { it.owned.isNotEmpty() }.owned.single().name)
    }

    @Test
    fun corruptedShard_fallsBackToEmptySnapshot() = runBlocking {
        repository.replaceAll(sampleOwned(), emptyList())
        awaitSnapshot { it.owned.isNotEmpty() }

        store.edit { it[stringPreferencesKey("operbox_$PROFILE_A")] = "{ this is not json" }

        assertTrue(awaitSnapshot { it.owned.isEmpty() && !it.hasSynced }.owned.isEmpty())
    }

    @Test
    fun emptyProfileId_skipsWrite() = runBlocking {
        activeProfileId.value = ""

        repository.replaceAll(sampleOwned(), emptyList())

        assertNull(rawShardOf(""))
    }

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}

private class FakeOperBoxPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val writeLock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = writeLock.withLock {
        transform(state.value).also { state.value = it }
    }
}
