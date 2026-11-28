package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.domain.state.ResourceInitState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ResourceInitServiceTest {

    // 解压中途取的快照结束后必须失效，否则那次永久失败再也不会重试
    @Test
    fun revisionChangesAfterMidExtractionSnapshot_onSuccess() = runBlocking {
        val env = Env(Result.success(1))
        env.service.checkAndInit()
        assertTrue(env.service.state.value is ResourceInitState.Ready)
        assertTrue(env.service.revision.value > env.midRevision!!)
        // 早于发布 Ready：观察者一看到 Ready 就比对版本
        assertEquals(env.service.revision.value, env.revisionAtMark)
    }

    @Test
    fun appVersionMarkerClearedBeforeExtracting() = runBlocking {
        val env = Env(Result.success(1))
        env.service.checkAndInit()
        assertEquals(true, env.versionClearedAtExtract)
    }

    @Test
    fun cancellationWhileBuildingFailureReport_resetsStateToNotChecked() = runBlocking {
        val telemetry = mockk<RunTelemetry>(relaxed = true)
        val env = Env(Result.success(1), telemetry = telemetry) {
            currentCoroutineContext().job.cancel()
            throw IOException("boom")
        }
        val coreGate = object : CoreFilesGate {
            override suspend fun <T> whenIdle(block: suspend () -> T): T = block()
        }
        val service = ResourceInitService(
            env.context, env.extractor, env.pathConfig, lazy { coreGate }, telemetry = telemetry,
        )

        launch { service.reInitialize() }.join()
        assertEquals(ResourceInitState.NotChecked, service.state.value)
        verify(exactly = 0) { telemetry.onResourceInitFailed(any()) }
    }

    @Test
    fun revisionChangesAfterMidExtractionSnapshot_onFailure() = runBlocking {
        val telemetry = mockk<RunTelemetry>(relaxed = true)
        val env = Env(Result.failure(IOException("boom")), telemetry = telemetry)
        env.service.checkAndInit()
        assertTrue(env.service.state.value is ResourceInitState.Failed)
        assertTrue(env.service.revision.value > env.midRevision!!)
        verify(exactly = 1) {
            telemetry.onResourceInitFailed(match {
                it.trigger == "checkAndInit" &&
                    it.errorType == "IOException" &&
                    it.cause.contains("boom")
            })
        }
    }

    @Test
    fun cancellationDuringExtraction_resetsStateToNotCheckedAndSkipsFailureTelemetry() = runBlocking {
        val root = Files.createTempDirectory("res-init-cancel").toFile()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val telemetry = mockk<RunTelemetry>(relaxed = true)
        val context = mockk<Context> {
            every { getString(any()) } returns ""
            every { assets } throws IOException("no assets in unit test")
        }
        val pathConfig = mockk<MaaPathConfig> {
            every { isResourceReady } returns false
            every { ensureDirectories() } returns true
            every { rootDir } returns root.absolutePath
            every { resourceDir } returns File(root, "resource").absolutePath
            every { debugDir } returns File(root, "debug").absolutePath
            every { overrideTasksFile } returns File(root, "overrides/tasks.json")
            every { markAppVersion() } returns Unit
            every { clearAppVersion() } returns Unit
        }
        var shouldSuspend = true
        val extractor = mockk<AssetExtractor> {
            coEvery { extract(any(), any(), any()) } coAnswers {
                if (shouldSuspend) {
                    entered.complete(Unit)
                    gate.await()
                }
                Result.success(1)
            }
        }
        val coreGate = object : CoreFilesGate {
            override suspend fun <T> whenIdle(block: suspend () -> T): T = block()
        }
        val service = ResourceInitService(context, extractor, pathConfig, lazy { coreGate }, telemetry = telemetry)

        val job = launch { service.reInitialize() }
        entered.await()
        assertTrue(service.state.value is ResourceInitState.Extracting)

        job.cancelAndJoin()
        assertEquals(ResourceInitState.NotChecked, service.state.value)
        verify(exactly = 0) { telemetry.onResourceInitFailed(any()) }

        shouldSuspend = false
        service.checkAndInit()
        assertEquals(ResourceInitState.Ready, service.state.value)
    }

    private class Env(
        extractResult: Result<Int>,
        telemetry: RunTelemetry? = null,
        extractBlock: (suspend () -> Result<Int>)? = null,
    ) {
        var midRevision: Int? = null
        var revisionAtMark: Int? = null
        var versionCleared = false
        var versionClearedAtExtract: Boolean? = null
        val context: Context
        val pathConfig: MaaPathConfig
        val extractor: AssetExtractor
        val service: ResourceInitService

        init {
            val root = Files.createTempDirectory("res-init").toFile()
            context = mockk<Context> {
                every { getString(any()) } returns ""
                every { assets } throws IOException("no assets in unit test")
            }
            pathConfig = mockk<MaaPathConfig> {
                every { isResourceReady } returns false
                every { ensureDirectories() } returns true
                every { resourceDir } returns File(root, "resource").absolutePath
                every { overrideTasksFile } returns File(root, "overrides/tasks.json")
                every { rootDir } returns root.absolutePath
                every { debugDir } returns File(root, "debug").absolutePath
                every { markAppVersion() } answers { revisionAtMark = service.revision.value }
                every { clearAppVersion() } answers { versionCleared = true }
            }
            extractor = mockk<AssetExtractor> {
                coEvery { extract(any(), any(), any()) } coAnswers {
                    midRevision = service.revision.value
                    versionClearedAtExtract = versionCleared
                    extractBlock?.invoke() ?: extractResult
                }
            }
            service = ResourceInitService(context, extractor, pathConfig, lazy { mockk<CoreFilesGate>() }, telemetry = telemetry)
        }
    }
}
