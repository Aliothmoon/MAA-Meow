package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import androidx.lifecycle.ViewModelStore
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.utils.LauncherApps
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleAppBlacklistViewModelTest {
    // 保持生产代码的 IO 调度不变，在独立主线程收集状态，不依赖 sleep。
    private val mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val store = ViewModelStore()
    private val blacklist = MutableStateFlow<Set<String>>(emptySet())
    private val settings = mockk<AppSettingsManager> {
        every { runMode } returns MutableStateFlow(RunMode.BACKGROUND)
        every { scheduleAppBlacklist } returns blacklist
    }
    private val context = mockk<Context>(relaxed = true)
    private val alpha = LauncherApps.App("Alpha", "app.alpha")
    private val beta = LauncherApps.App("Beta", "app.beta")
    private lateinit var viewModel: ScheduleAppBlacklistViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mockkObject(LauncherApps)
        every { context.packageName } returns "app.meow"
        every { context.resources.displayMetrics } returns mockk<DisplayMetrics>().apply { density = 2f }
        // 模拟应用刚被卸载等图标异常，确认不会使整个列表失败。
        every { context.packageManager.getApplicationIcon(any<String>()) } throws
                IllegalStateException("Icon unavailable")
        every { LauncherApps.load(context) } returns listOf(alpha, beta)
        coEvery { settings.setScheduleAppBlacklisted(any(), any()) } answers {
            val pkg = firstArg<String>()
            blacklist.value = if (secondArg<Boolean>()) blacklist.value + pkg else blacklist.value - pkg
        }
    }

    @After
    fun tearDown() {
        runBlocking(mainDispatcher) { store.clear() }
        Dispatchers.resetMain()
        mainDispatcher.close()
        unmockkObject(LauncherApps)
    }

    @Test
    fun loadsLauncherAppsAndUsesPlaceholderWhenIconsFail() = runBlocking(mainDispatcher) {
        val iconsQueried = CompletableDeferred<Unit>()
        every { context.packageManager.getApplicationIcon(beta.packageName) } answers {
            iconsQueried.complete(Unit)
            throw IllegalStateException("Icon unavailable")
        }
        every { LauncherApps.load(context) } returns listOf(
            alpha, beta, LauncherApps.App("Meow", context.packageName),
        )
        createViewModel()
        withTimeout(5_000) { iconsQueried.await() }
        val loaded = awaitLoaded()

        assertEquals(listOf(alpha.packageName, beta.packageName), loaded.rows.map { it.packageName })
        assertTrue(loaded.rows.all { it.icon == null })
        verify(exactly = 1) { LauncherApps.load(context) }
    }

    @Test
    fun listRemainsEditableWhileIconsLoadAndUpdatesIconsIncrementally() = runBlocking(mainDispatcher) {
        val alphaStarted = CompletableDeferred<Unit>()
        val releaseAlpha = CompletableDeferred<Unit>()
        val betaStarted = CompletableDeferred<Unit>()
        val releaseBeta = CompletableDeferred<Unit>()
        val iconBitmap = mockk<Bitmap> {
            every { width } returns 80
            every { height } returns 80
        }
        val drawable = mockk<BitmapDrawable> {
            every { intrinsicWidth } returns 80
            every { intrinsicHeight } returns 80
            every { bitmap } returns iconBitmap
        }
        val packageManager = context.packageManager
        // 模拟不可挂起的系统查询，只阻塞 IO 线程；用信号控制顺序，不依赖 sleep。
        every { packageManager.getApplicationIcon(alpha.packageName) } answers {
            alphaStarted.complete(Unit)
            runBlocking { releaseAlpha.await() }
            drawable
        }
        every { packageManager.getApplicationIcon(beta.packageName) } answers {
            betaStarted.complete(Unit)
            runBlocking { releaseBeta.await() }
            throw IllegalStateException("Icon unavailable")
        }

        try {
            createViewModel()
            withTimeout(5_000) { alphaStarted.await() }
            val initial = awaitLoaded()
            assertEquals(listOf(alpha.packageName, beta.packageName), initial.rows.map { it.packageName })
            assertTrue(initial.rows.all { it.icon == null })

            viewModel.setBlacklisted(alpha.packageName, true)
            val edited = awaitLoaded { it.checkedCount == 1 }
            assertTrue(edited.rows.first().checked)

            releaseAlpha.complete(Unit)
            withTimeout(5_000) { betaStarted.await() }
            // 第二个图标仍在读取时，第一个已显示，且没有覆盖加载期间的勾选。
            val partial = awaitLoaded { it.rows.first().icon != null }
            assertTrue(partial.rows.first().checked)
            assertEquals(1, partial.checkedCount)
            assertEquals(null, partial.rows.last().icon)
        } finally {
            releaseAlpha.complete(Unit)
            releaseBeta.complete(Unit)
        }
    }

    @Test
    fun icons_fitDisplayDensityWithoutStretchingAndAreReusedWhenToggling() = runBlocking(mainDispatcher) {
        // 40dp × density 2 = 80px；2:1 的图标应缩成 80×40，而不是拉伸成正方形。
        // 提供已匹配目标尺寸的 Bitmap，让 toBitmap 直接复用它，避免依赖设备 Canvas。
        val iconBitmap = mockk<Bitmap> {
            every { width } returns 80
            every { height } returns 40
        }
        val drawable = mockk<BitmapDrawable> {
            every { intrinsicWidth } returns 160
            every { intrinsicHeight } returns 80
            every { bitmap } returns iconBitmap
        }
        val packageManager = context.packageManager
        every { packageManager.getApplicationIcon(alpha.packageName) } returns drawable
        createViewModel()
        val loaded = awaitLoaded { it.rows.first().icon != null }
        val icon = requireNotNull(loaded.rows.first().icon)
        assertEquals(80, icon.width)
        assertEquals(40, icon.height)

        viewModel.setBlacklisted(alpha.packageName, true)
        val edited = awaitLoaded { it.checkedCount == 1 }
        assertSame(icon, edited.rows.first().icon)
        verify(exactly = 1) { packageManager.getApplicationIcon(alpha.packageName) }
    }

    private fun createViewModel() {
        viewModel = ScheduleAppBlacklistViewModel(settings, context)
        store.put("blacklist", viewModel)
    }

    private suspend fun awaitLoaded(
        predicate: (ScheduleAppBlacklistViewModel.UiState.Loaded) -> Boolean = { true },
    ): ScheduleAppBlacklistViewModel.UiState.Loaded = withTimeout(5_000) {
        viewModel.state.filterIsInstance<ScheduleAppBlacklistViewModel.UiState.Loaded>().first(predicate)
    }
}
