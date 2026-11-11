package com.aliothmoon.maameow

import android.os.IBinder
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.HotUpdateSync
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.models.ClientResourceProfile
import com.aliothmoon.maameow.domain.service.CoreDataPusher
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.remote.SetupResult
import com.aliothmoon.maameow.utils.JsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class MaaResourceLoaderTest {

    @After
    fun tearDown() {
        // `Packages.appliedMapping` 是进程级可变状态：用例里写进去的值会漏到下一个类
        // （PackagesTest 依赖「没被污染」的顺序断言）
        Packages.appliedMapping = emptyMap()
        unmockkObject(RemoteServiceManager)
    }

    @Test
    fun ensureLoaded_reloads_afterHotUpdateCommitted() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            env.loadedDirs.clear()
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertTrue(env.loadedDirs.isEmpty())

            env.hotUpdateRevision.value++
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun depsInfoTimeout_doesNotLeaveLoaderInLoading() = runBlocking {
        withEnv { env ->
            // `withTimeout` 抛的是 CancellationException 子类：若被当成「上层取消」原样抛出，
            // `_state` 会永久停在 Loading，`ensureLoaded` 的等待者与启动互斥锁一起挂死
            env.loader.depsInfoTimeoutMs = 50
            coEvery { env.resourceDataManager.load(any(), any(), any()) } coAnswers {
                delay(5_000)
            }

            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)
        }
    }

    @Test
    fun permanentFailure_retriesOnlyAfterFilesChange() = runBlocking {
        withEnv { env ->
            env.resourceReady.set(false)
            assertTrue(env.loader.load("Official").isFailure)
            assertTrue((env.loader.state.value as MaaResourceLoader.State.Failed).permanent)

            // 文件没动过，不白重试
            env.resourceReady.set(true)
            assertTrue(env.loader.ensureLoaded("Official").isFailure)
            assertTrue(env.loadedDirs.isEmpty())

            // 重新初始化或资源更新后再试
            env.resourceRevision.value++
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
        }
    }

    @Test
    fun ensureLoaded_reloads_afterResourceUpdated() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            env.loadedDirs.clear()

            env.resourceRevision.value++
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
        }
    }

    // 日服写入 MaaCore 的键在进程内不可回滚，换资源档必须换提权进程

    // 提权进程读写不了根目录（#227）：不能再往下 LoadResource，且要标成需换存储位置的永久失败

    @Test
    fun load_marksStorageInaccessible_whenRemoteSetupRejectsUserDir() = runBlocking {
        withEnv(setupCode = SetupResult.ERR_USER_DIR_INACCESSIBLE) { env ->
            val result = env.loader.load("Official")

            assertTrue(result.isFailure)
            val state = env.loader.state.value
            assertTrue(state is MaaResourceLoader.State.Failed)
            state as MaaResourceLoader.State.Failed
            assertTrue(state.permanent)
            assertEquals(MaaResourceLoader.State.FailReason.STORAGE_INACCESSIBLE, state.reason)
            assertTrue(env.loadedDirs.isEmpty())
        }
    }

    @Test
    fun load_usesAppDir_byDefault() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            assertTrue(env.loadedDirs.none { it.startsWith("/data/local/tmp") })
        }
    }

    @Test
    fun load_passesLocalTmpPaths_whenCoreSeparated() = runBlocking {
        withEnv(coreSeparated = true) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            assertTrue(env.loadedDirs.all { it.startsWith("/data/local/tmp/maameow") })
            coVerify(exactly = 1) { env.coreDataPusher.prepare(any()) }
        }
    }

    @Test
    fun load_failsRetryable_whenCoreDataPrepareFails() = runBlocking {
        withEnv(coreSeparated = true) { env ->
            coEvery { env.coreDataPusher.prepare(any()) } returns false
            assertTrue(env.loader.load("Official").isFailure)
            val state = env.loader.state.value as MaaResourceLoader.State.Failed
            assertFalse(state.permanent)
            assertTrue(env.loadedDirs.isEmpty())
        }
    }

    @Test
    fun load_keepsGenericSetupFailureRetryable() = runBlocking {
        withEnv(setupCode = SetupResult.ERR_CORE_NOT_LOADED) { env ->
            assertTrue(env.loader.load("Official").isFailure)
            val state = env.loader.state.value as MaaResourceLoader.State.Failed
            assertFalse(state.permanent)
            assertEquals(MaaResourceLoader.State.FailReason.GENERIC, state.reason)
        }
    }

    @Test
    fun load_usesSelectedDisplayLanguage_forClientResources() = runBlocking {
        withEnv(appLanguage = AppSettingsManager.AppLanguage.EN) { env ->
            // revision 必须与 GameMetadataLoader 用同一套记账：传默认 0 会让
            // ResourceDataManager 的 cachedBase 校验永远失配，每次加载都重解析一遍
            env.resourceRevision.value = 7
            assertTrue(env.loader.load("YoStarEN").isSuccess)
            coVerify(exactly = 1) { env.resourceDataManager.load("YoStarEN", any(), 7) }
            verify(exactly = 1) { env.itemHelper.load("YoStarEN") }
        }
    }

    @Test
    fun load_addsGamePackageLayerRightAfterMain_whenCustomized() = runBlocking {        withEnv(gamePackages = mapOf("YoStarJP" to "com.example.jp")) { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            // 必须紧跟主资源，后面的层才能照常覆盖 options
            assertEquals("game_package", File(env.loadedDirs[1]).name)
            assertTrue(File(env.loadedDirs[1], "resource/config.json").isFile)
        }
    }

    @Test
    fun load_writesGamePackageLayerWithDefaults_whenNotCustomized() = runBlocking {
        withEnv { env ->
            // 之前自定义过、后来清空：这一层必须写回默认映射。
            // 只删目录、跳过这层的话，core 进程里旧的 packageName 还在，任务会连不上
            val stale = File(env.rootDir, "game_package/resource/config.json")
            stale.parentFile!!.mkdirs()
            stale.writeText("""{"packageName":{"Official":"com.example.old"}}""")

            assertTrue(env.loader.load("Official").isSuccess)
            assertEquals("game_package", File(env.loadedDirs[1]).name)
            val written = File(env.loadedDirs[1], "resource/config.json").readText()
            val packages = runCatching {
                JsonUtils.common.parseToJsonElement(written).jsonObject.getValue("packageName")
                    .jsonObject.mapValues { it.value.jsonPrimitive.content }
            }.getOrThrow()
            // 基底里没有 txwy，它能出现说明默认映射被补齐（而不是照抄基底）
            assertEquals("tw.txwy.and.arknights", packages["txwy"])
            assertFalse(packages.containsValue("com.example.old"))
            // connection 保持基底原样，别把连接模板清空
            assertTrue(
                "game package layer should keep base connection templates",
                written.contains("General"),
            )
        }
    }

    @Test
    fun load_doesNotLeaveAppliedMarker_whenPrepareFails() = runBlocking {
        withEnv(coreSeparated = true, gamePackages = mapOf("Official" to "com.example.cn")) { env ->
            // 写层成功 ≠ core 应用成功：prepare 在 LoadResource 之前，失败时 core 从未见过这一层。
            // 提前写标记会给下一次「清空覆盖 + 基底缺失」留下假证据（永久硬失败）
            coEvery { env.coreDataPusher.prepare(any()) } returns false
            assertTrue(env.loader.load("Official").isFailure)
            assertFalse(File(env.rootDir, "game_package/.applied").exists())
        }
    }

    @Test
    fun resetIfStale_forgetsAppliedMapping_whenReplacedWhileFailed() = runBlocking {
        withEnv(gamePackages = mapOf("Official" to "com.example.cn")) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertEquals("com.example.cn", Packages.appliedMapping["Official"])

            // 用户恢复默认包名 → 该重载，但新进程加载失败（服务仍 Connected）。
            // 旧映射必须归零，否则 App 会拿旧自定义包名判「已安装」/拉错包
            env.gamePackages.value = emptyMap()
            coEvery { env.coreDataPusher.prepare(any()) } returns false
            assertTrue(env.loader.load("Official").isFailure)

            val fresh = env.replaceService()
            assertTrue(env.loader.resetIfStale(fresh))
            assertTrue(Packages.appliedMapping.isEmpty())
            assertEquals(Packages.defaults["Official"], Packages["Official"])
        }
    }

    @Test
    fun load_gamePackageLayer_keepsBasePackageNameFromResource() = runBlocking {
        // 基包名可能被热更更新过：写层时必须沿用基底自己的值，不能被编译期常量盖回默认
        val baseConfig = """
            {
              "version": "1.0",
              "options": { "taskDelay": 500, "controlDelayRange": [0, 0] },
              "packageName": { "Official": "com.hypergryph.custom" },
              "connection": [ { "configName": "General" } ]
            }
        """.trimIndent()
        withEnv(baseConfig = baseConfig, gamePackages = mapOf("YoStarEN" to "com.example.en")) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            val written = File(env.loadedDirs[1], "resource/config.json").readText()
            val packages = JsonUtils.common.parseToJsonElement(written).jsonObject
                .getValue("packageName").jsonObject.mapValues { it.value.jsonPrimitive.content }
            assertEquals("com.hypergryph.custom", packages["Official"])
            assertEquals("com.example.en", packages["YoStarEN"])
        }
    }

    @Test
    fun load_failsWhenClearingOverrides_cannotRestoreBaseMapping() = runBlocking {
        // 先带自定义包名成功加载（这层真的推进 core 了），再让基底读不到并清空覆盖：
        // 这时必须失败，不能静默 Ready（core 会一直等旧包名）。
        // 用「本进程是否应用过这层」判断，而不是磁盘上有没有旧目录——后者在写失败/被外部清掉时会误判。
        withEnv(gamePackages = mapOf("Official" to "com.example.cn")) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            File(env.rootDir, "resource/config.json").delete()
            env.gamePackages.value = emptyMap()
            env.loader.invalidate()

            assertTrue(env.loader.load("Official").isFailure)
            // 失败后重试仍必须失败：不能第二次退化成「不需要这一层」而直接 Ready
            env.loader.invalidate()
            assertTrue(env.loader.load("Official").isFailure)
        }
    }

    @Test
    fun load_failsWhenClearingOverridesAfterProcessRestart() = runBlocking {
        // 跨进程证据：App 重启后新实例的 `appliedGamePackageLayer` 是干净的，
        // 只剩磁盘上的 `.applied` 标记。core 进程能活过 App 重启，这条路径是唯一拦得住
        // 「清空覆盖 + 本次写失败 → 静默 Ready、core 仍等旧包名」的证据，之前零覆盖。
        withEnv(gamePackages = mapOf("Official" to "com.example.cn")) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            Packages.appliedMapping = emptyMap()
            val restarted = env.rebuildLoader()
            // 基底读不到 → 无法把默认映射写回 core，必须硬失败而不是「不需要这一层」
            File(env.rootDir, "resource/config.json").delete()
            env.gamePackages.value = emptyMap()
            restarted.invalidate()
            assertTrue(restarted.load("Official").isFailure)
            // 重试仍失败：标记还在，不能第二次退化成 Ready
            restarted.invalidate()
            assertTrue(restarted.load("Official").isFailure)
        }
    }

    @Test
    fun load_skipsGamePackageLayer_whenNoOverridesAndBaseMissingOnFirstLoad() = runBlocking {
        // 首次加载、没有任何自定义包名、基底又读不到：这层本来就没有内容要写进 core，
        // 不该把它当失败（否则首页红字 + 每次启动无退避重试）
        withEnv { env ->
            File(env.rootDir, "resource/config.json").delete()

            assertTrue(env.loader.load("Official").isSuccess)
        }
    }

    @Test
    fun load_mapsGamePackageLayer_whenCoreSeparated() = runBlocking {
        withEnv(coreSeparated = true, gamePackages = mapOf("Official" to "com.example.cn")) { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertEquals("/data/local/tmp/maameow/game_package", env.loadedDirs[1].replace(File.separatorChar, '/'))
        }
    }

    @Test
    fun ensureLoaded_reloads_afterGamePackagesChanged() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            env.loadedDirs.clear()

            // 不经 invalidate，Ready 状态下改包名也要重载
            env.gamePackages.value = mapOf("Official" to "com.example.cn")
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertEquals("game_package", File(env.loadedDirs[1]).name)
            verify(exactly = 0) { RemoteServiceManager.unbind() }

            // 清空覆盖后必须重载：这一层改写成默认映射，把 core 里旧的包名映射顶回去
            env.loadedDirs.clear()
            env.gamePackages.value = emptyMap()
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertEquals("game_package", File(env.loadedDirs[1]).name)
            assertTrue(
                File(env.loadedDirs[1], "resource/config.json").readText()
                    .contains("com.hypergryph.arknights")
            )
        }
    }

    @Test
    fun gamePackageConfig_keepsRequiredFields_andOnlyOverridesPackageName() {
        val config = JsonUtils.common.parseToJsonElement(
            MaaResourceLoader.gamePackageConfig(BASE_CONFIG, mapOf("YoStarKR" to "com.example.kr"))
        ).jsonObject

        assertEquals(setOf("version", "options", "packageName", "connection"), config.keys)
        assertEquals(JsonUtils.common.parseToJsonElement(BASE_CONFIG).jsonObject["options"], config["options"])
        // 这一层的唯一职责就是替换 packageName（合并逻辑在 writeGamePackageLayer）
        assertEquals(
            mapOf("YoStarKR" to "com.example.kr"),
            config.getValue("packageName").jsonObject.mapValues { it.value.jsonPrimitive.content },
        )
        // connection 保持基底原样：清空会让后加载覆盖掉用户的 ADB/模拟器模板
        assertEquals(
            JsonUtils.common.parseToJsonElement(BASE_CONFIG).jsonObject["connection"],
            config["connection"],
        )
    }

    @Test
    fun resourceProfile_groupsBaseOnlyClientsTogether() {
        assertEquals("", ClientResourceProfile.of(""))
        assertEquals("", ClientResourceProfile.of("Official"))
        assertEquals("", ClientResourceProfile.of("Bilibili"))
        assertEquals("YoStarJP", ClientResourceProfile.of("YoStarJP"))
        assertEquals("YoStarEN", ClientResourceProfile.of("YoStarEN"))
        assertEquals("txwy", ClientResourceProfile.of("txwy"))
    }

    @Test
    fun requiresServiceRestart_onlyWhenResourceProfileChanges() {
        // 全新进程没什么可污染的
        assertFalse(MaaResourceLoader.requiresServiceRestart(null, "Official"))
        assertFalse(MaaResourceLoader.requiresServiceRestart(null, "YoStarJP"))
        // 官服/B服共用 base
        assertFalse(MaaResourceLoader.requiresServiceRestart("Official", "Bilibili"))
        assertFalse(MaaResourceLoader.requiresServiceRestart("Bilibili", "Official"))
        assertFalse(MaaResourceLoader.requiresServiceRestart("YoStarJP", "YoStarJP"))
        // 复现路径
        assertTrue(MaaResourceLoader.requiresServiceRestart("YoStarJP", "Official"))
        assertTrue(MaaResourceLoader.requiresServiceRestart("YoStarJP", "Bilibili"))
        // 反向也脏，公招 tag 的 static 缓存双向失效
        assertTrue(MaaResourceLoader.requiresServiceRestart("Official", "YoStarJP"))
        // global 之间也互相残留
        assertTrue(MaaResourceLoader.requiresServiceRestart("YoStarJP", "YoStarEN"))
    }

    @Test
    fun load_restartsElevatedService_whenSwitchingFromGlobalClientToOfficial() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }

            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun load_keepsElevatedService_whenSwitchingBetweenBaseOnlyClients() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loader.load("Bilibili").isSuccess)
            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun load_dropsGlobalOverride_afterSwitchingBackToOfficial() = runBlocking {
        withEnv { env ->
            env.loader.load("YoStarJP")
            assertTrue(env.loadedDirs.any { it.contains("YoStarJP") })

            env.loadedDirs.clear()
            env.loader.load("Official")
            // 切回国服不能再带上日服覆盖
            assertTrue(env.loadedDirs.none { it.contains("YoStarJP") })

            env.loadedDirs.clear()
            env.loader.load("YoStarJP")
            assertTrue(env.loadedDirs.any { it.contains("YoStarJP") })
            verify(exactly = 2) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun ensureLoaded_reloads_whenRequestedClientDiffersFromLoadedOne() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)

            // Ready 但客户端对不上，不能直接放行
            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }

            // 同一资源档不该重复加载
            env.loadedDirs.clear()
            assertTrue(env.loader.ensureLoaded("Bilibili").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }
            assertTrue(env.loadedDirs.isEmpty())
        }
    }

    @Test
    fun reset_forgetsLoadedClient_soNextLoadDoesNotRestart() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            // 服务死亡后新进程是干净的，不该再多重启一次
            env.loader.reset()
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)

            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun load_dropsReadyState_whenServiceDoesNotComeBackAfterProfileSwitch() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)

            // 解绑后服务没起来，不能停在 Ready
            env.rebindFails.set(true)
            assertTrue(env.loader.load("Official").isFailure)
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)

            // 服务恢复后重新完整加载，不该再多重启一次
            env.rebindFails.set(false)
            env.loadedDirs.clear()
            assertTrue(env.loader.load("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            verify(exactly = 1) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun invalidate_keepsLoadedClient_soProfileSwitchStillRestarts() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)

            // 覆盖开关之类只是让资源失效，进程还活着且带着日服残留
            env.loader.invalidate()
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)

            // 所以切回国服依然必须换进程
            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 1) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun invalidate_doesNotRestart_whenClientUnchanged() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            env.loader.invalidate()

            env.loadedDirs.clear()
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            // 同一档位不该白白换进程
            verify(exactly = 0) { RemoteServiceManager.unbind() }
            assertTrue(env.loadedDirs.any { it.contains("YoStarJP") })
        }
    }

    // Shizuku 重启后直接 Connected -> Connecting -> Connected，新进程没加载过资源
    @Test
    fun ensureLoaded_reloads_whenServiceReplacedWithoutDisconnect() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            env.replaceService()
            env.loadedDirs.clear()

            assertTrue(env.loader.ensureLoaded("Official").isSuccess)
            assertTrue(env.loadedDirs.isNotEmpty())
            // 新进程是干净的，不该再换一次
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    @Test
    fun resetIfStale_keepsReady_forSameService() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("Official").isSuccess)
            assertFalse(env.loader.resetIfStale(RemoteServiceManager.getInstanceOrNull()!!))
            assertTrue(env.loader.state.value is MaaResourceLoader.State.Ready)
        }
    }

    @Test
    fun resetIfStale_dropsReady_whenServiceReplaced() = runBlocking {
        withEnv { env ->
            assertTrue(env.loader.load("YoStarJP").isSuccess)
            val fresh = env.replaceService()

            assertTrue(env.loader.resetIfStale(fresh))
            assertTrue(env.loader.state.value is MaaResourceLoader.State.NotLoaded)
            // 旧进程的资源档也一并作废，换客户端不该多重启
            assertTrue(env.loader.load("Official").isSuccess)
            verify(exactly = 0) { RemoteServiceManager.unbind() }
        }
    }

    private class Env(
        val rootDir: File,
        val loader: MaaResourceLoader,
        val gamePackages: MutableStateFlow<Map<String, String>>,
        val loadedDirs: MutableList<String>,
        val hotUpdateRevision: MutableStateFlow<Int>,
        val resourceRevision: MutableStateFlow<Int>,
        val resourceReady: AtomicBoolean,
        /** 置 true 模拟换进程后服务没能回来 */
        val rebindFails: AtomicBoolean,
        val coreDataPusher: CoreDataPusher,
        /** 资源依赖数据：与 core 资源同步加载的那两个 load 调用 */
        val itemHelper: ItemHelper,
        val resourceDataManager: ResourceDataManager,
        /** 换一个新提权进程并直接置 Connected，不经过 Disconnected */
        val replaceService: () -> RemoteService,
        /** 用同一个 rootDir 重建 loader：模拟 App 重启后内存 flag 不可见、只剩磁盘证据 */
        val rebuildLoader: () -> MaaResourceLoader,
    )

    private suspend fun withEnv(
        appLanguage: AppSettingsManager.AppLanguage = AppSettingsManager.AppLanguage.ZH,
        setupCode: Int = SetupResult.OK,
        coreSeparated: Boolean = false,
        gamePackages: Map<String, String> = emptyMap(),
        baseConfig: String = BASE_CONFIG,
        block: suspend (Env) -> Unit,
    ) {
        val rootDir = Files.createTempDirectory("maa-resource-loader-test").toFile()
        try {
            File(rootDir, "resource").mkdirs()
            File(rootDir, "resource/config.json").writeText(baseConfig)
            File(rootDir, "cache/resource").mkdirs()
            listOf("YoStarJP", "YoStarEN").forEach {
                File(rootDir, "resource/global/$it/resource").mkdirs()
                File(rootDir, "cache/resource/global/$it/resource").mkdirs()
            }

            val resourceReady = AtomicBoolean(true)
            val gamePackagesFlow = MutableStateFlow(gamePackages)
            val pathConfig = mockk<MaaPathConfig> {
                every { this@mockk.rootDir } returns rootDir.absolutePath
                every { resourceDir } returns File(rootDir, "resource").absolutePath
                every { isResourceReady } answers { resourceReady.get() }
                every { cacheDir } returns File(rootDir, "cache").absolutePath
                every { cacheResourceDir } returns File(rootDir, "cache/resource").absolutePath
                every { overridesDir } returns File(rootDir, "overrides").absolutePath
                every { globalResourceDir(any()) } answers {
                    File(rootDir, "resource/global/${firstArg<String>()}/resource")
                }
                every { globalCacheResourceDir(any()) } answers {
                    File(rootDir, "cache/resource/global/${firstArg<String>()}/resource")
                }
            }
            val appSettings = mockk<AppSettingsManager> {
                every { debugMode } returns MutableStateFlow(false)
                every { language } returns MutableStateFlow(appLanguage)
                every { displayLanguage } returns ResourceDataManager.displayLanguageCode(appLanguage)
                every { forceFullscreenOnVirtualDisplay } returns MutableStateFlow(false)
                every { tasksOverrideEnabled } returns MutableStateFlow(false)
                every { this@mockk.gamePackages } returns gamePackagesFlow
            }
            val chainState = mockk<TaskChainState> {
                every { clientType } returns "Official"
            }
            val hotUpdateRevision = MutableStateFlow(0)
            val hotUpdateSync = mockk<HotUpdateSync> { every { revision } returns hotUpdateRevision }
            val resourceRevision = MutableStateFlow(0)
            val resourceInitService = mockk<ResourceInitService> { every { revision } returns resourceRevision }
            every { pathConfig.isCoreSeparated } returns coreSeparated
            every { pathConfig.coreRootDir } answers {
                if (coreSeparated) "/data/local/tmp/maameow" else rootDir.absolutePath
            }
            every { pathConfig.toCorePath(any()) } answers {
                if (coreSeparated) {
                    MaaPathConfig.toCorePath(firstArg(), rootDir.absolutePath, "/data/local/tmp/maameow")
                } else firstArg()
            }
            val coreDataPusher = mockk<CoreDataPusher> {
                coEvery { prepare(any()) } returns true
            }
            // 资源依赖数据（活动关卡/物品索引/干员表）与 core 资源无关，这里只需不抛异常
            val itemHelper = mockk<ItemHelper>(relaxed = true)
            val resourceDataManager = mockk<ResourceDataManager>(relaxed = true)
            val activityManager = mockk<ActivityManager>(relaxed = true)
            val loadedDirs = mutableListOf<String>()

            fun newService(): RemoteService {
                val maaCore = mockk<MaaCoreService>()
                every { maaCore.LoadResource(any()) } answers {
                    loadedDirs += firstArg<String>()
                    true
                }
                return mockk<RemoteService> {
                    every { asBinder() } returns mockk<IBinder>()
                    every { setup(any(), any()) } returns setupCode
                    justRun { setForceFullscreenOnVirtualDisplay(any()) }
                    every { maaCoreService } returns maaCore
                }
            }
            var service = newService()

            // 解绑置 Disconnected，取到服务即视为 Connected
            val serviceState = MutableStateFlow<RemoteServiceManager.ServiceState>(
                RemoteServiceManager.ServiceState.Disconnected
            )
            val rebindFails = AtomicBoolean(false)

            mockkObject(RemoteServiceManager)
            every { RemoteServiceManager.state } returns serviceState
            every { RemoteServiceManager.unbind() } answers {
                serviceState.value = RemoteServiceManager.ServiceState.Disconnected
            }
            every { RemoteServiceManager.getInstanceOrNull() } answers {
                (serviceState.value as? RemoteServiceManager.ServiceState.Connected)?.service
            }
            coEvery { RemoteServiceManager.useRemoteService<Result<Unit>>(any(), any()) } coAnswers {
                if (rebindFails.get()) error("elevated service gone")
                serviceState.value = RemoteServiceManager.ServiceState.Connected(service)
                secondArg<suspend (RemoteService) -> Result<Unit>>().invoke(service)
            }

            block(
                Env(
                    rootDir = rootDir,
                    gamePackages = gamePackagesFlow,
                    loader = MaaResourceLoader(
                        pathConfig = pathConfig,
                        appSettings = appSettings,
                        chainState = chainState,
                        hotUpdateSync = hotUpdateSync,
                        resourceInitService = resourceInitService,
                        itemHelper = itemHelper,
                        resourceDataManager = resourceDataManager,
                        activityManager = activityManager,
                        coreDataPusher = coreDataPusher,
                    ),
                    loadedDirs = loadedDirs,
                    hotUpdateRevision = hotUpdateRevision,
                    resourceRevision = resourceRevision,
                    resourceReady = resourceReady,
                    rebindFails = rebindFails,
                    coreDataPusher = coreDataPusher,
                    itemHelper = itemHelper,
                    resourceDataManager = resourceDataManager,
                    replaceService = {
                        service = newService()
                        serviceState.value = RemoteServiceManager.ServiceState.Connected(service)
                        service
                    },
                    rebuildLoader = {
                        MaaResourceLoader(
                            pathConfig = pathConfig,
                            appSettings = appSettings,
                            chainState = chainState,
                            hotUpdateSync = hotUpdateSync,
                            resourceInitService = resourceInitService,
                            itemHelper = itemHelper,
                            resourceDataManager = resourceDataManager,
                            activityManager = activityManager,
                            coreDataPusher = coreDataPusher,
                        )
                    },
                )
            )
        } finally {
            rootDir.deleteRecursively()
        }
    }

    private companion object {
        val BASE_CONFIG = """
            {
              "version": "1.0",
              "options": { "taskDelay": 500, "controlDelayRange": [0, 0] },
              "packageName": { "Official": "com.hypergryph.arknights", "YoStarKR": "com.YoStarKR.Arknights" },
              "connection": [ { "configName": "General" } ]
            }
        """.trimIndent()
    }
}
