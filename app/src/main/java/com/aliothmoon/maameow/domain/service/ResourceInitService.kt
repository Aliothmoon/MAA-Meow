package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaFiles.ASSET_DIR_NAME
import com.aliothmoon.maameow.constant.MaaFiles.OVERRIDES_ASSET_TASKS
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.datasource.AssetExtractor
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextDynamicOr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class ResourceInitService(
    private val context: Context,
    private val assetExtractor: AssetExtractor,
    private val pathConfig: MaaPathConfig,
    private val coreFiles: Lazy<CoreFilesGate>,
) {
    private companion object {
        const val OVERRIDES_ASSET_TEMPLATE_DIR = "overrides/resource/template"

        /** 内置模板图（TemplateMatch 用），必须随 APK 一起下发到磁盘 */
        val OVERRIDES_TEMPLATE_ASSETS = listOf(
            "MeterRewindChangle.png",
            "MeterRewindCandle.png",
            "MeterRewindGo.png",
            "MeterRewindRewardCandle.png",
            "MeterRewindRewardRelic.png",
            "MeterRewindNext.png",
            "MeterRewindPopupDone.png",
        )
        const val OVERRIDES_TEMPLATE_MARKER = ".template_version"

        /**
         * 内置 overrides tasks.json 的内容指纹（SHA-256 前 16 位）。
         *
         * 早先用手写版本号 +1 来判断要不要覆盖磁盘文件，代价是「改了 tasks.json 忘了加号」时
         * 编译完全正常、磁盘却保留旧文件：Core 里就没有新节点，App 侧只看到
         * `任务加入队列失败：Custom`（v13→v17 连续踩过）。改成内容指纹后，
         * 只要 APK 里的文件变了就一定覆盖一次，手改磁盘文件（指纹不变）仍然保留。
         */
        fun assetTasksDigest(context: Context): String? = runCatching {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            context.assets.open(OVERRIDES_ASSET_TASKS).use { src ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val read = src.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }.take(16)
        }.onFailure { Timber.w(it, "计算 overrides 任务指纹失败") }.getOrNull()
    }

    private val _state = MutableStateFlow<ResourceInitState>(ResourceInitState.NotChecked)
    val state: StateFlow<ResourceInitState> = _state.asStateFlow()

    // App 侧资源目录内容版本，重解或资源更新后自增
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** 资源更新包已解到 App 侧目录 */
    fun onResourceUpdated() {
        _revision.update { it + 1 }
    }

    suspend fun checkAndInit() {
        val cur = _state.value
        if (cur !is ResourceInitState.NotChecked && cur !is ResourceInitState.Failed) {
            return
        }
        if (!_state.compareAndSet(cur, ResourceInitState.Checking)) {
            return
        }

        try {
            if (withContext(Dispatchers.IO) { pathConfig.isResourceReady }) {
                // 资源已就绪时也刷新一次内置 overrides 模板：overrides 由 App 管理（重解时本就会被覆盖），
                // 每次启动从 APK 同步可保证同版本覆盖安装后自定义任务随 APK 即时生效，无需重解 266MB 资源。
                withContext(Dispatchers.IO) { doForceSyncOverridesTemplate() }
                _state.value = ResourceInitState.Ready
                return
            }
            doExtractFromAssets()
        } finally {
            // 调用方被取消时还停在 Checking，后续调用都会早退，放回去让下次重查
            _state.compareAndSet(ResourceInitState.Checking, ResourceInitState.NotChecked)
        }
    }

    /** 会删掉整个资源目录 */
    suspend fun reInitialize() {
        coreFiles.value.whenIdle { doExtractFromAssets() }
    }

    private suspend fun doExtractFromAssets() {
        _state.value =
            ResourceInitState.Extracting(0, 0, context.getString(R.string.resource_init_preparing))

        try {
            val result = try {
                withContext(Dispatchers.IO) {
                    pathConfig.ensureDirectories()
                    val resourceDir = File(pathConfig.resourceDir)
                    if (resourceDir.exists() && !resourceDir.deleteRecursively()) {
                        Timber.w("清理旧资源目录失败: ${resourceDir.absolutePath}")
                    }
                    resourceDir.mkdirs()
                }

                assetExtractor.extract(
                    assetDir = ASSET_DIR_NAME,
                    destDir = File(pathConfig.resourceDir),
                    onProgress = { progress ->
                        _state.value = ResourceInitState.Extracting(
                            extractedCount = progress.extractedCount,
                            totalCount = progress.totalCount,
                            currentFile = progress.currentFile
                        )
                    }
                )
            } finally {
                // 早于发布 Ready/Failed；checkAndInit 不走门禁，解压中的加载靠这次递增重试
                _revision.update { it + 1 }
            }

            result.fold(
                onSuccess = {
                    pathConfig.markAppVersion()
                    doForceSyncOverridesTemplate()
                    Timber.i("资源初始化完成")
                    _state.value = ResourceInitState.Ready
                },
                onFailure = { e ->
                    _state.value = ResourceInitState.Failed(
                        (e as? LocalizedException)?.uiText
                            ?: uiTextDynamicOr(e.message, R.string.resource_init_error_copy_failed)
                    )
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "资源初始化失败")
            _state.value = ResourceInitState.Failed(
                (e as? LocalizedException)?.uiText
                    ?: uiTextDynamicOr(e.message, R.string.resource_init_error_unknown)
            )
        }
    }

    private fun doForceSyncOverridesTemplate() {
        doSyncOverrideTasks()
        // 模板图每次启动都覆盖（体积只有几十 KB）。它们必须落地到磁盘，
        // 否则 TemplateMatch 的节点会识别失败，表现为「点开始之后什么都不做」。
        doSyncTemplates()
    }

    private fun doSyncOverrideTasks() {
        val dest = pathConfig.overrideTasksFile
        val digest = assetTasksDigest(context) ?: return
        runCatching {
            val marker = File(dest.parentFile, OVERRIDES_TEMPLATE_MARKER)
            val synced = marker.takeIf { it.exists() }?.readText()?.trim()
            if (dest.exists() && synced == digest) {
                // 内容未变：保留磁盘现状，允许手改的任务定义在重启后继续生效
                Timber.d("overrides 模板内容未变(%s)，保留现状: %s", digest, dest.absolutePath)
                return
            }
            dest.parentFile?.mkdirs()
            context.assets.open(OVERRIDES_ASSET_TASKS).use { src ->
                dest.outputStream().use { src.copyTo(it) }
            }
            marker.writeText(digest)
            Timber.i(
                "overrides 任务已同步(%s, %d bytes): %s",
                digest,
                dest.length(),
                dest.absolutePath,
            )
        }.onFailure {
            Timber.w(it, "overrides 模板同步失败，跳过")
        }
    }

    /**
     * 把内置模板图同步到磁盘。
     *
     * 同时写两份：
     * 1. overrides/resource/template/ —— 覆盖层资源自己的模板目录；
     * 2. resource/template/          —— 主资源目录。覆盖层的模板能否被索引到取决于
     *    核心实现的资源路径扫描顺序，主资源目录是确定能命中的，双份最稳。
     */
    private fun doSyncTemplates() {
        val overrideTplDir = File(pathConfig.overridesDir, "resource/template")
        val mainTplDir = File(pathConfig.resourceDir, "template")
        runCatching {
            overrideTplDir.mkdirs()
            mainTplDir.mkdirs()
            for (name in OVERRIDES_TEMPLATE_ASSETS) {
                val bytes = context.assets
                    .open("$OVERRIDES_ASSET_TEMPLATE_DIR/$name")
                    .use { it.readBytes() }
                File(overrideTplDir, name).writeBytes(bytes)
                File(mainTplDir, name).writeBytes(bytes)
            }
            Timber.i("模板图已同步: ${OVERRIDES_TEMPLATE_ASSETS.joinToString()} ")
        }.onFailure {
            Timber.w(it, "模板图同步失败，跳过")
        }
    }
}
