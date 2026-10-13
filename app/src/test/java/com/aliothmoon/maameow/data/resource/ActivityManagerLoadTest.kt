package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.api.MaaApiService.CachedBody
import com.aliothmoon.maameow.data.preferences.TaskChainState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 界面元数据与 Core 加载前后脚都会拉活动关卡，同一客户端只拉一次
 */
class ActivityManagerLoadTest {

    private val fresh = CachedBody("{}", fresh = true)

    private val api = mockk<MaaApiService> {
        coEvery { getStageActivity() } returns fresh
        coEvery { getTasksInfo() } returns fresh
        coEvery { getGlobalTasksInfo(any()) } returns fresh
    }

    private val manager = ActivityManager(
        context = mockk(relaxed = true),
        chainState = mockk<TaskChainState> { every { clientType } returns "Official" },
        maaApiService = api,
        itemHelper = mockk(relaxed = true),
    )

    @Test
    fun sameClientIsReusedRightAfterAFreshLoad() = runTest {
        manager.load("Official")
        manager.load("Official")
        // B 服与官服同一档
        manager.load("Bilibili")

        coVerify(exactly = 1) { api.getStageActivity() }
        coVerify(exactly = 1) { api.getTasksInfo() }
    }

    @Test
    fun otherClientLoadsAgain() = runTest {
        manager.load("Official")
        manager.load("YoStarEN")

        coVerify(exactly = 2) { api.getStageActivity() }
        coVerify(exactly = 1) { api.getGlobalTasksInfo("YoStarEN") }
    }

    @Test
    fun offlineFallbackIsNotReused() = runTest {
        coEvery { api.getTasksInfo() } returns CachedBody("{}", fresh = false)

        manager.load("Official")
        manager.load("Official")

        coVerify(exactly = 2) { api.getTasksInfo() }
    }

    @Test
    fun concurrentCallersShareOneFetch() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { api.getStageActivity() } coAnswers {
            gate.await()
            fresh
        }

        launch { manager.load("Official") }
        launch { manager.load("Official") }
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { api.getStageActivity() }
    }
}
