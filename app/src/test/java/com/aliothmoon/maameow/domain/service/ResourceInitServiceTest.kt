package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.domain.state.ResourceInitState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
    fun revisionChangesAfterMidExtractionSnapshot_onFailure() = runBlocking {
        val env = Env(Result.failure(IOException("boom")))
        env.service.checkAndInit()
        assertTrue(env.service.state.value is ResourceInitState.Failed)
        assertTrue(env.service.revision.value > env.midRevision!!)
    }

    private class Env(extractResult: Result<Int>) {
        var midRevision: Int? = null
        var revisionAtMark: Int? = null
        val service: ResourceInitService

        init {
            val root = Files.createTempDirectory("res-init").toFile()
            val context = mockk<Context> {
                every { getString(any()) } returns ""
                every { assets } throws IOException("no assets in unit test")
            }
            val pathConfig = mockk<MaaPathConfig> {
                every { isResourceReady } returns false
                every { ensureDirectories() } returns true
                every { resourceDir } returns File(root, "resource").absolutePath
                every { overrideTasksFile } returns File(root, "overrides/tasks.json")
                every { markAppVersion() } answers { revisionAtMark = service.revision.value }
            }
            val extractor = mockk<AssetExtractor> {
                coEvery { extract(any(), any(), any()) } answers {
                    midRevision = service.revision.value
                    extractResult
                }
            }
            service = ResourceInitService(context, extractor, pathConfig, lazy { mockk<CoreFilesGate>() })
        }
    }
}
