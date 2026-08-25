package com.aliothmoon.maameow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** logcat 服务必须复用进程连接器基座，不得自己再写一套拉起 / 死亡处理 */
class LogcatServiceContractTest {

    private val source =
        TestSources.resolve("src/main/java/com/aliothmoon/maameow/manager/LogcatServiceManager.kt").readText()

    @Test
    fun logcatConnectorsShareProcessBase() {
        assertTrue(
            "logcat 两个后端都必须走进程连接器基座，linkToDeath / token 认领由基座统一提供",
            source.contains(": ProcessServiceConnectorBackend("),
        )
        assertFalse(
            "logcat 不得自己再写 linkToDeath，会绕开基座的 token 认领",
            source.contains("linkToDeath"),
        )
    }

    @Test
    fun bindDoesNotEarlyReturnOnDeadBinder() {
        val bindBody = source.substringAfter("fun bind() {").substringBefore("fun unbind()")
        assertTrue(
            "bind() 必须先看 binder 是否还活着，否则死 binder 会让它永远早退",
            bindBody.contains("isBinderAlive"),
        )
    }
}
