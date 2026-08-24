package com.aliothmoon.maameow

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧进程的 binder 死讯可能晚于新连接建立，而上层只按 backend 过滤，
 * root→root 重绑时过滤不掉，会打成 Died 或留下孤儿进程
 * 所以两个 root 连接器的死亡回调都必须按 token 认领
 */
class RootBinderDeathContractTest {

    @Test
    fun rootRemoteServiceConnector_deathRecipientIsTokenScoped() {
        assertTokenScopedDeath(
            "src/main/java/com/aliothmoon/maameow/manager/RootRemoteServiceConnector.kt",
            "activeLaunch?.token != token",
        )
    }

    @Test
    fun logcatServiceManager_rootBinderIsLinkedToDeath() {
        val source = resolve(
            "src/main/java/com/aliothmoon/maameow/manager/LogcatServiceManager.kt"
        ).readText()
        assertTrue(
            "root logcat binder 必须 linkToDeath，否则 _service 一直握着死 binder",
            source.contains("linkToDeath"),
        )
        assertTokenScopedDeath(
            "src/main/java/com/aliothmoon/maameow/manager/LogcatServiceManager.kt",
            "rootActiveLaunch?.token != token",
        )
    }

    @Test
    fun logcatServiceManager_bindDoesNotEarlyReturnOnDeadBinder() {
        val source = resolve(
            "src/main/java/com/aliothmoon/maameow/manager/LogcatServiceManager.kt"
        ).readText()
        val bindBody = source.substringAfter("fun bind() {").substringBefore("fun unbind()")
        assertTrue(
            "bind() 必须先看 binder 是否还活着，否则死 binder 会让它永远早退",
            bindBody.contains("isBinderAlive"),
        )
    }

    private fun assertTokenScopedDeath(relativePath: String, tokenCheck: String) {
        val source = resolve(relativePath).readText()
        val deathBody = source.substringAfter("linkToDeath({").substringBefore("}, 0)")
        assertTrue("$relativePath: 找不到 linkToDeath 回调体", deathBody.isNotBlank())
        assertTrue(
            "$relativePath: 死亡回调必须按 token 认领（$tokenCheck），否则会误伤新连接",
            deathBody.contains(tokenCheck),
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
