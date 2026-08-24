package com.aliothmoon.maameow

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 换进程时旧进程收尾会 restoreAll() 解除静音，重连后 autoRestore 还会清掉 marker
 * 所以资源档对齐必须排在 mute 之前，否则跨服那次任务全程不静音且不自愈
 */
class ResourceSwitchBeforeMuteContractTest {

    @Test
    fun startTaskChainUseCase_prepareResourcesBeforeMute() {
        assertOrdering(
            "src/main/java/com/aliothmoon/maameow/domain/launch/StartTaskChainUseCase.kt",
            "composition.prepareResources(",
            "muteCoordinator.mute(",
        )
    }

    @Test
    fun backgroundTaskViewModel_prepareResourcesBeforeMute() {
        assertOrdering(
            "src/main/java/com/aliothmoon/maameow/presentation/viewmodel/BackgroundTaskViewModel.kt",
            "compositionService.prepareResources(",
            "gameMuteCoordinator.mute(",
        )
    }

    @Test
    fun prepareResources_refusesToRecycleServiceWhileBusy() {
        val source = resolve(
            "src/main/java/com/aliothmoon/maameow/domain/service/MaaCompositionService.kt"
        ).readText()
        val body = source.substringAfter("suspend fun prepareResources(")
            .substringBefore("private suspend fun checkPreconditions(")
        assertTrue(
            "prepareResources 只能在 IDLE/ERROR 下换资源，否则会杀掉运行中的虚拟显示器",
            body.contains("MaaExecutionState.IDLE") && body.contains("MaaExecutionState.ERROR"),
        )
        assertTrue(body.contains("resourceLoader.ensureLoaded(clientType)"))
    }

    private fun assertOrdering(relativePath: String, first: String, second: String) {
        val source = resolve(relativePath).readText()
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        assertTrue("$relativePath 里找不到 $first", firstIndex >= 0)
        assertTrue("$relativePath 里找不到 $second", secondIndex >= 0)
        assertTrue(
            "$relativePath: $first 必须排在 $second 之前，换进程会解除游戏静音",
            firstIndex < secondIndex,
        )
    }

    private fun resolve(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
        checkNotNull(file) { "File not found for test: $relativePath" }
        return file
    }
}
