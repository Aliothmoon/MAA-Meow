package com.aliothmoon.maameow.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 内存 Preferences DataStore，`updateData` 与真实实现一样串行执行。
 *
 * 用它而非 `PreferenceDataStoreFactory`：后者在 Windows JVM 上第二次写入必然失败
 * （临时文件无法 rename 覆盖已存在的目标），会让「多次写入」类用例变成平台缺陷的牺牲品。
 * 内存实现保留 `updateData` 的串行语义，测的是被测类的逻辑而非 DataStore 的文件 IO。
 *
 * @param writeDelayMs 每次写入的人为延迟，用于制造「写入在途时后续变更堆积」以验证合并写
 */
internal class FakePreferencesDataStore(
    private val writeDelayMs: Long = 0L,
) : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())
    private val writeLock = Mutex()

    /** 置 true 后每次写入抛 [IOException]，用于验证 awaitPersist/flush 失败传播。 */
    @Volatile
    var failWrites: Boolean = false

    /** 实际发生的写入次数，用于断言合并写。 */
    val editCount = AtomicInteger(0)

    /** `data` 被收集的次数，用于断言「只读一次盘」。 */
    val dataCollectCount = AtomicInteger(0)

    override val data: Flow<Preferences> = state.onStart { dataCollectCount.incrementAndGet() }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = writeLock.withLock {
        if (writeDelayMs > 0) delay(writeDelayMs)
        if (failWrites) throw IOException("fake write failure")
        editCount.incrementAndGet()
        transform(state.value).also { state.value = it }
    }
}
