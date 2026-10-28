package com.aliothmoon.maameow.data.preferences

import android.content.Context
import android.util.AtomicFile
import androidx.core.util.readText
import androidx.core.util.writeText
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.service.UnlockGestureReader
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * 录制的解锁手势
 *
 * 单独落文件而不进 DataStore：轨迹有几 KB，塞进 AppSettings 会让每次设置变更都跟着解析一遍；
 * 落在私有目录也顺带保证它不会被配置导出带走——轨迹等价于锁屏凭证
 */
class UnlockGestureStore(private val context: Context) : UnlockGestureReader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val gestureFile: File
        get() = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    private val _gesture = MutableStateFlow<UnlockGesture?>(null)

    /** 已录制的手势；未录制为 null */
    val gesture: StateFlow<UnlockGesture?> = _gesture.asStateFlow()

    /** 本进程里录的；写盘失败时内存里的手势比文件新，修改时间也得跟内存走 */
    @Volatile
    private var savedAtMs: Long? = null

    init {
        scope.launch { _gesture.value = read() }
    }

    suspend fun save(gesture: UnlockGesture) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = gestureFile
                    file.parentFile?.mkdirs()
                    // 写一半被杀会留下截断的 JSON，下次读出来就是「未录制」
                    AtomicFile(file).writeText(
                        JsonUtils.common.encodeToString(UnlockGesture.serializer(), gesture),
                    )
                }.onFailure { Timber.e(it, "save unlock gesture failed") }
            }
        }
        _gesture.value = gesture
        savedAtMs = System.currentTimeMillis()
    }

    suspend fun clear() {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { AtomicFile(gestureFile).delete() }
                    .onFailure { Timber.w(it, "clear unlock gesture failed") }
            }
        }
        _gesture.value = null
        savedAtMs = null
    }

    override suspend fun readJson(): String = withContext(Dispatchers.IO) {
        val gesture = _gesture.value ?: read() ?: return@withContext ""
        JsonUtils.common.encodeToString(UnlockGesture.serializer(), gesture)
    }

    /** 轨迹里不存录制时间：本进程录的用内存记的时刻，否则取文件修改时间 */
    override suspend fun recordedAtMs(): Long? = savedAtMs ?: withContext(Dispatchers.IO) {
        // 与 read() 一致，只剩 .bak 时取它的时间；AtomicFile.getLastModifiedTime 要 API 31
        val backup = File(gestureFile.path + ".bak")
        (if (backup.isFile) backup else gestureFile).lastModified().takeIf { it > 0 }
    }

    private suspend fun read(): UnlockGesture? = withContext(Dispatchers.IO) {
        val file = gestureFile
        // 改名前被杀时只剩 .bak，AtomicFile 读的时候会自己回滚
        if (!file.isFile && !File(file.path + ".bak").isFile) return@withContext null
        val text = runCatching { AtomicFile(file).readText() }.getOrElse {
            Timber.w(it, "unlock gesture unreadable")
            return@withContext null
        }
        UnlockGesture.parseOrNull(text) { Timber.w(it) }
    }

    private companion object {
        const val DIR_NAME = "unlock"
        const val FILE_NAME = "gesture.json"
    }
}
