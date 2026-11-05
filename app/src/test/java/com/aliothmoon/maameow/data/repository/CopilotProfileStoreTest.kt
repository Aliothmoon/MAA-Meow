package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aliothmoon.maameow.data.model.CopilotConfig
import com.aliothmoon.maameow.data.model.TaskProfile
import com.aliothmoon.maameow.data.model.UserAdditionalOper
import com.aliothmoon.maameow.data.model.copilot.CopilotListItem
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class CopilotProfileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val active = MutableStateFlow("a")
    private val loaded = MutableStateFlow(true)
    private val profiles = MutableStateFlow(listOf(profile("a"), profile("b")))
    private val deleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var duplicate: suspend (String, String) -> Unit = { _, _ -> }
    private val chain = mockk<TaskChainState> {
        every { profileId } returns active
        every { isLoaded } returns loaded
        every { this@mockk.profiles } returns this@CopilotProfileStoreTest.profiles
        every { profileDeleted } returns deleted
        every { registerProfileDuplicator(any()) } answers { duplicate = firstArg() }
    }

    private fun profile(id: String) = TaskProfile(id, id, emptyList())
    private fun key(id: String) = stringPreferencesKey("${CopilotProfileStore.KEY_PREFIX}$id")
    private fun subject(store: DataStore<Preferences>, dir: java.io.File = temp.root) =
        CopilotProfileStore(store, chain, dir)
    private suspend fun CopilotProfileStore.ready() = withTimeout(5_000) { awaitLoaded() }

    private val config = CopilotConfig(
        formation = false, addTrust = true, ignoreRequirements = true,
        useSanityPotion = true, supportUnitUsage = 3, useSupportUnit = true,
        loopTimes = 5, loop = true, useFormation = true, formationIndex = 4,
        addUserAdditional = true, userAdditionalOpers = listOf(UserAdditionalOper("能天使", 3)),
        useCopilotList = true,
    )
    private fun items() = listOf(
        CopilotListItem("1-7", "/shared/7.json", true, 7, false, "web", "title", "row-1"),
        CopilotListItem("2-1", "/shared/8.json", false, 8, true, "local", "other", "row-2"),
    )

    @Test fun allFieldsAndOrderAreIsolatedAcrossAbaAndReload() = runBlocking {
        val disk = FakePreferencesDataStore()
        val store = subject(disk).also { it.ready() }
        val a = CopilotProfileData(config, items())
        store.update("a") { a }
        active.value = "b"
        assertEquals(CopilotProfileData(), store.snapshot.value)
        val b = CopilotProfileData(CopilotConfig(loopTimes = 2), items().reversed())
        store.update("b") { b }
        active.value = "a"
        assertEquals(a, store.snapshot.value)
        store.awaitPersist()
        val restarted = subject(disk).also { it.ready() }
        assertEquals(a, restarted.snapshot.value)
        active.value = "b"
        assertEquals(b, restarted.snapshot.value)
    }

    @Test fun initializationWaitsForProfilesAndCannotOverwriteSavedData() = runBlocking {
        val disk = FakePreferencesDataStore()
        val saved = CopilotProfileData(config, items())
        disk.edit { it[key("a")] = JsonUtils.common.encodeToString(saved) }
        loaded.value = false
        val store = subject(disk)
        store.update("a") { CopilotProfileData() }
        assertFalse(store.isLoaded.value)
        loaded.value = true
        store.ready()
        assertEquals(saved, store.snapshot.value)
    }

    @Test fun legacyDataIsCopiedToEveryExistingProfileAndMigrationIsIdempotent() = runBlocking {
        val disk = FakePreferencesDataStore()
        temp.newFile("config.json").writeText(JsonUtils.common.encodeToString(config))
        temp.newFile("task_list.json").writeText(JsonUtils.common.encodeToString(items()))
        val store = subject(disk).also { it.ready() }
        val expected = CopilotProfileData(config, items())
        assertEquals(expected, store.forProfile("a"))
        assertEquals(expected, store.forProfile("b"))
        store.update("a") { CopilotProfileData() }
        store.awaitPersist()
        val restarted = subject(disk).also { it.ready() }
        assertEquals(CopilotProfileData(), restarted.forProfile("a"))
        assertEquals(expected, restarted.forProfile("b"))
        assertTrue(disk.data.first()[CopilotProfileStore.MIGRATED] == true)
    }

    @Test fun migrationPreservesValidShardAndReplacesCorruptedShard() = runBlocking {
        val disk = FakePreferencesDataStore()
        val saved = CopilotProfileData(config, items())
        disk.edit {
            it[key("a")] = JsonUtils.common.encodeToString(saved)
            it[key("b")] = "{broken"
        }
        val store = subject(disk).also { it.ready() }
        assertEquals(saved, store.forProfile("a"))
        assertEquals(CopilotProfileData(), store.forProfile("b"))
    }

    @Test fun missingEmptyAndCorruptedLegacyFilesFallBackIndependently() = runBlocking {
        temp.newFile("config.json").writeText("{broken")
        temp.newFile("task_list.json").writeText(JsonUtils.common.encodeToString(items()))
        val validList = subject(FakePreferencesDataStore()).also { it.ready() }
        assertEquals(CopilotProfileData(taskList = items()), validList.snapshot.value)
        temp.root.resolve("config.json").writeText(JsonUtils.common.encodeToString(config))
        temp.root.resolve("task_list.json").writeText("")
        val validConfig = subject(FakePreferencesDataStore()).also { it.ready() }
        assertEquals(CopilotProfileData(config), validConfig.snapshot.value)
        temp.root.resolve("config.json").delete()
        temp.root.resolve("task_list.json").delete()
        val absent = subject(FakePreferencesDataStore()).also { it.ready() }
        assertEquals(CopilotProfileData(), absent.snapshot.value)
    }

    @Test fun failedMigrationPreservesLegacyFilesAndRetriesWithoutSuccessMarker() = runBlocking {
        val disk = FakePreferencesDataStore()
        val configFile = temp.newFile("config.json").also { it.writeText(JsonUtils.common.encodeToString(config)) }
        val listFile = temp.newFile("task_list.json").also { it.writeText(JsonUtils.common.encodeToString(items())) }
        disk.failWrites = true
        subject(disk).ready()
        assertNull(disk.data.first()[CopilotProfileStore.MIGRATED])
        assertTrue(configFile.exists())
        assertTrue(listFile.exists())
        disk.failWrites = false
        val retry = subject(disk).also { it.ready() }
        assertEquals(CopilotProfileData(config, items()), retry.snapshot.value)
        assertTrue(disk.data.first()[CopilotProfileStore.MIGRATED] == true)
    }

    @Test fun failedMigrationStillLoadsExistingShard() = runBlocking {
        val disk = FakePreferencesDataStore()
        val saved = CopilotProfileData(config, items())
        disk.edit { it[key("a")] = JsonUtils.common.encodeToString(saved) }
        disk.failWrites = true
        val store = subject(disk).also { it.ready() }
        assertEquals(saved, store.snapshot.value)
    }

    @Test fun newProfileStartsEmptyAndDuplicateCopiesReferencesIndependently() = runBlocking {
        val disk = FakePreferencesDataStore()
        val store = subject(disk).also { it.ready() }
        store.update("a") { CopilotProfileData(config, items()) }
        profiles.value += profile("new")
        active.value = "new"
        assertEquals(CopilotProfileData(), store.snapshot.value)
        duplicate("a", "copy")
        profiles.value += profile("copy")
        active.value = "copy"
        assertEquals(store.forProfile("a"), store.snapshot.value)
        store.update("copy") { it.copy(taskList = emptyList()) }
        assertEquals(items(), store.forProfile("a").taskList)
        assertTrue(store.snapshot.value.taskList.isEmpty())
    }

    @Test fun deleteAndLateAsyncWriteCannotResurrectProfileOrDeleteSharedJson() = runBlocking {
        val disk = FakePreferencesDataStore()
        val shared = temp.newFile("7.json").also { it.writeText("{}") }
        val item = items().first().copy(filePath = shared.absolutePath)
        val store = subject(disk).also { it.ready() }
        store.update("a") { CopilotProfileData(config, listOf(item)) }
        store.update("b") { CopilotProfileData(taskList = listOf(item)) }
        store.awaitPersist()
        withTimeout(5_000) { while (deleted.subscriptionCount.value == 0) yield() }
        profiles.value = profiles.value.filter { it.id != "a" }
        active.value = "b"
        deleted.emit("a")
        withTimeout(5_000) { while (disk.data.first()[key("a")] != null) yield() }
        store.update("a") { CopilotProfileData(config) }
        store.awaitPersist()
        assertNull(disk.data.first()[key("a")])
        assertTrue(shared.exists())
        assertEquals(listOf(item), store.forProfile("b").taskList)
    }

    @Test fun asyncSaveUsesCapturedProfileAfterSwitch() = runBlocking {
        val store = subject(FakePreferencesDataStore()).also { it.ready() }
        val capturedId = active.value
        active.value = "b"
        store.update(capturedId) { it.copy(config = config) }
        assertEquals(CopilotProfileData(), store.snapshot.value)
        active.value = "a"
        assertEquals(config, store.snapshot.value.config)
    }

    @Test fun duplicateBeforeInitializationWaitsAndCopiesLoadedSource() = runBlocking {
        val disk = FakePreferencesDataStore()
        val saved = CopilotProfileData(config, items())
        disk.edit { it[key("a")] = JsonUtils.common.encodeToString(saved) }
        loaded.value = false
        val store = subject(disk)
        val copy = async { duplicate("a", "copy") }
        yield()
        assertFalse(copy.isCompleted)
        loaded.value = true
        copy.await()
        assertEquals(saved, store.forProfile("copy"))
        assertEquals(saved, JsonUtils.common.decodeFromString<CopilotProfileData>(disk.data.first()[key("copy")]!!))
    }

    @Test fun readFailureCannotOverwriteUnreadShardWithDefaults() = runBlocking {
        val disk = FakePreferencesDataStore()
        val saved = CopilotProfileData(config, items())
        disk.edit { it[key("a")] = JsonUtils.common.encodeToString(saved) }
        val unreadable = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("read unavailable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = disk.updateData(transform)
        }
        val store = subject(unreadable)
        try {
            store.ready()
            fail("未成功读取时不能允许编辑")
        } catch (_: IOException) {
        }
        store.update("a") { CopilotProfileData() }
        store.awaitPersist()
        assertEquals(saved, JsonUtils.common.decodeFromString<CopilotProfileData>(disk.data.first()[key("a")]!!))
        assertNull(disk.data.first()[CopilotProfileStore.MIGRATED])
    }

    @Test fun deletionDuringInitialLoadDoesNotRestoreOrphanedShard() = runBlocking {
        val disk = FakePreferencesDataStore()
        val gate = CompletableDeferred<Unit>()
        disk.edit { it[key("a")] = JsonUtils.common.encodeToString(CopilotProfileData(config)) }
        val gated = object : DataStore<Preferences> {
            override val data = flow { gate.await(); emitAll(disk.data) }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = disk.updateData(transform)
        }
        val store = subject(gated)
        withTimeout(5_000) { while (deleted.subscriptionCount.value == 0) yield() }
        profiles.value = listOf(profile("b"))
        active.value = "b"
        deleted.emit("a")
        gate.complete(Unit)
        store.ready()
        withTimeout(5_000) { while (disk.data.first()[key("a")] != null) yield() }
        assertEquals(CopilotProfileData(), store.forProfile("a"))
    }
}
