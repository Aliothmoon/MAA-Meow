package com.aliothmoon.maameow

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 直连绑定器的行为约定，防止实现回退 */
class ShizukuUserServiceBinderContractTest {

    @Test
    fun watchdogExpiresAfterServerStartTimeout() {
        val source = binderSource()
        val serverMs = extractMillis(source, "SERVER_START_TIMEOUT_MS = ")
        val watchMs = extractMillis(source, "DEFAULT_BIND_TIMEOUT_MS = ")
        assertTrue("server 启动超时应为 30s", serverMs == 30_000L)
        assertTrue(
            "看门狗必须晚于 server 30s 启动超时，否则 server 还没放弃 App 就先报超时",
            watchMs > serverMs,
        )
    }

    @Test
    fun callbacksStayOnBinderThread() {
        val source = binderSource()
        assertTrue(
            "必须直接实现 IShizukuServiceConnection.Stub",
            source.contains("IShizukuServiceConnection.Stub"),
        )
        assertFalse(
            "回调禁止 post 主线程，主线程卡顿会拖慢送达",
            source.contains("getMainLooper") || source.contains("MAIN_HANDLER"),
        )
    }

    @Test
    fun timeoutPathPeeksForLateBinder() {
        val source = binderSource()
        assertTrue(
            "超时前必须重试一次送达（NO_CREATE 触发广播），oneway 丢失的迟到 binder 靠它救回",
            source.contains("USER_SERVICE_ARG_NO_CREATE"),
        )
    }

    @Test
    fun managerFallbackTimeoutExpiresAfterConnectorWatchdog() {
        val source = resolve("src/main/java/com/aliothmoon/maameow/manager/RemoteServiceManager.kt").readText()
        val fallback = extractMillis(source, "CONNECT_TIMEOUT_MS = ")
        val watchMs = extractMillis(binderSource(), "DEFAULT_BIND_TIMEOUT_MS = ")
        assertTrue(
            "RemoteServiceManager 兜底超时必须晚于连接器看门狗（当前 40s > 35s）",
            fallback > watchMs,
        )
    }

    @Test
    fun connectedBinderIsLinkedToDeath() {
        val source = binderSource()
        assertTrue(
            "connected 后必须 linkToDeath，否则服务进程死亡永远收不到 onDisconnected",
            source.contains("linkToDeath"),
        )
    }

    @Test
    fun remoteServiceConstructorStaysLightweight() {
        val source = resolve(
            "src/main/java/com/aliothmoon/maameow/remote/RemoteServiceImpl.kt"
        ).readText()
        val ctorSection = source.substringAfter("init {").substringBefore("override fun destroy")
        assertFalse(
            "ctor 禁止触发 MaaCoreManager（JNA/libMaaCore 同步加载，慢设备超时主因）",
            ctorSection.contains("MaaCoreManager"),
        )
        assertFalse(
            "ctor 禁止 XmsfFirewall 清理（spawn iptables 有 IO 开销，已后置 setup）",
            ctorSection.contains("XmsfFirewall"),
        )
        val setupSection = source.substringAfter("override fun setup").substringBefore("override fun test")
        assertTrue(
            "xmsf 残留规则清理必须保留在 setup，且先于业务调用",
            setupSection.contains("XmsfFirewall.ensureRestored"),
        )
    }

    @Test
    fun allShizukuUserServicesUseDirectBinder() {
        val connectors = listOf(
            "src/main/java/com/aliothmoon/maameow/manager/ShizukuRemoteServiceConnector.kt",
            "src/main/java/com/aliothmoon/maameow/manager/LogcatServiceManager.kt",
        )
        for (path in connectors) {
            val source = resolve(path).readText()
            assertTrue(
                "$path 必须经 ShizukuUserServiceBinder 发起绑定",
                source.contains("ShizukuUserServiceBinder.bind"),
            )
            assertFalse(
                "$path 禁止回退 Shizuku.bindUserService 封装",
                source.contains("Shizuku.bindUserService"),
            )
        }
    }

    private fun binderSource(): String =
        resolve("src/main/java/com/aliothmoon/maameow/manager/ShizukuUserServiceBinder.kt").readText()

    private fun extractMillis(source: String, keyPrefix: String): Long {
        val idx = source.indexOf(keyPrefix)
        assertTrue("找不到 $keyPrefix", idx >= 0)
        val rest = source.substring(idx + keyPrefix.length)
        val num = rest.takeWhile { it.isDigit() || it == '_' }
        return num.replace("_", "").toLong()
    }

    private fun resolve(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("source not found: $relativePath (cwd=${File(".").absolutePath})")
    }
}
