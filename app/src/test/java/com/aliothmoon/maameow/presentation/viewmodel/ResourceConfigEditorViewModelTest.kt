package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.CoreConfigFixture
import com.aliothmoon.maameow.data.config.GamePackageSettings
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import androidx.lifecycle.viewModelScope
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import com.aliothmoon.maameow.koin.viewModelModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceConfigEditorViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val state = MutableStateFlow(MaaExecutionState.IDLE)
    private val composition = mockk<MaaCompositionService> {
        every { this@mockk.state } returns this@ResourceConfigEditorViewModelTest.state
        coEvery { updateResourceConfig(any()) } coAnswers {
            if (state.value !in setOf(MaaExecutionState.IDLE, MaaExecutionState.ERROR)) false
            else { firstArg<() -> Unit>().invoke(); true }
        }
    }
    private lateinit var vm: GamePackageEditorViewModel
    @Before fun before() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun after() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }
    private lateinit var packageSettings: GamePackageSettings
    private fun viewModel(): GamePackageEditorViewModel {
        packageSettings = GamePackageSettings(
            mockk<MaaPathConfig> { every { rootDir } returns temporary.root.path },
            mockk<AppSettingsManager> { every { tasksOverrideEnabled } returns MutableStateFlow(false) },
            mockk<TaskChainState> { every { clientType } returns "YoStarKR" },
        )
        packageSettings.setEnabled(true)
        return GamePackageEditorViewModel(packageSettings, composition, StandardTestDispatcher()).also { vm = it }
    }
    private fun config() = File(temporary.root, "resource/config.json").apply {
        parentFile!!.mkdirs()
        writeText(CoreConfigFixture.text("""{"YoStarKR":"original.game"}"""))
    }

    @Test fun gameSaveUsesGuardedUpdateAndDoesNotTouchTaskOverrides() = runTest {
        val file = config()
        val tasks = File(temporary.root, "overrides/resource/tasks/tasks.json").apply {
            parentFile!!.mkdirs(); writeText("""{"Task":{}}""")
        }
        val model = viewModel()
        model.load()
        withTimeout(5000) { model.loadedText.filterNotNull().first() }
        val updated = """{"packageName":{"YoStarKR":"new.game"}}"""
        model.onTextChange(updated)
        model.onSave()
        withTimeout(5000) { model.saveState.first { it is GamePackageEditorViewModel.SaveState.Success } }
        assertTrue(file.readText().contains("original.game"))
        assertTrue(File(temporary.root, "config_custom_package.json").readText().contains("new.game"))
        assertEquals("""{"Task":{}}""", tasks.readText())
        coVerify(exactly = 1) { composition.updateResourceConfig(any()) }
    }

    @Test fun taskStartingWhileEditorIsOpenPreventsSaving() = runTest {
        val file = config()
        val original = file.readText()
        val model = viewModel()
        model.load()
        withTimeout(5000) { model.loadedText.filterNotNull().first() }
        model.onTextChange("""{"packageName":{"YoStarKR":"new.game"}}""")
        state.value = MaaExecutionState.STARTING
        model.onSave()
        withTimeout(5000) { model.saveState.first { it is GamePackageEditorViewModel.SaveState.Error } }
        assertEquals(original, file.readText())
    }

    @Test fun missingGameResourceShowsLoadErrorAndCannotBeSaved() = runTest {
        val model = viewModel()
        model.load()
        withTimeout(5000) { model.saveState.first { it is GamePackageEditorViewModel.SaveState.Error } }
        assertNull(model.loadedText.value)
        model.onTextChange("{}")
        model.onSave()
        coVerify(exactly = 0) { composition.updateResourceConfig(any()) }
        assertFalse(File(temporary.root, "resource/config.json").exists())
    }

    @Test fun disablingWhileEditorIsOpenPreventsWritingCustomValues() = runTest {
        config()
        val model = viewModel()
        model.load()
        withTimeout(5000) { model.loadedText.filterNotNull().first() }
        model.onTextChange("""{"packageName":{"YoStarKR":"new.game"}}""")
        packageSettings.setEnabled(false)
        model.onSave()
        withTimeout(5000) { model.saveState.first { it is GamePackageEditorViewModel.SaveState.Error } }
        assertFalse(File(temporary.root, "config_custom_package.json").exists())
    }

    @Test fun koinResolvesGameAndTaskEditorsIndependently() = runTest {
        val app = koinApplication {
            modules(viewModelModule, module {
                single<MaaPathConfig> { mockk { every { rootDir } returns temporary.root.path } }
                single<MaaCompositionService> { composition }
                single<MaaResourceLoader> { mockk(relaxed = true) }
                single<AppSettingsManager> { mockk { every { tasksOverrideEnabled } returns MutableStateFlow(false) } }
                single<TaskChainState> { mockk { every { clientType } returns "YoStarKR" } }
            })
        }
        try {
            val game = app.koin.get<GamePackageEditorViewModel>()
            val tasks = app.koin.get<TaskOverrideEditorViewModel>()
            assertEquals(GamePackageEditorViewModel::class, game::class)
            assertEquals(TaskOverrideEditorViewModel::class, tasks::class)
            game.viewModelScope.cancel()
            tasks.viewModelScope.cancel()
        } finally {
            app.close()
        }
    }

    @Test fun originalTaskEditorStillSavesOnlyItsTaskFile() = runBlocking {
        val base = config()
        val original = base.readText()
        val tasks = File(temporary.root, "overrides/resource/tasks/tasks.json")
        val loader = mockk<MaaResourceLoader>(relaxed = true)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val model = TaskOverrideEditorViewModel(
            mockk<MaaPathConfig> { every { overrideTasksFile } returns tasks }, loader
        )
        try {
            model.load()
            withTimeout(5000) { model.loadedText.filterNotNull().first() }
            assertEquals("{}", model.loadedText.value)
            model.onTextChange("""{"Task":{"next":["OtherTask"]}}""")
            model.onSave()
            withTimeout(5000) { model.saveState.first { it is TaskOverrideEditorViewModel.SaveState.Success } }
            assertEquals(original, base.readText())
            assertTrue(tasks.readText().contains("OtherTask"))
            verify(exactly = 1) { loader.invalidate() }
        } finally {
            model.viewModelScope.cancel()
        }
    }
}
