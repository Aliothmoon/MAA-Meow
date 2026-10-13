package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.MaaFiles
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.File
import java.io.IOException

class CopilotOperBoxAssist(
    private val appSettings: AppSettingsManager,
    private val operBoxRepository: OperBoxRepository,
    private val pathConfig: MaaPathConfig,
) {
    data class State(
        val yituliuEnabled: Boolean = false,
        val dataUsable: Boolean = false,
        val syncTimeMillis: Long = 0L,
        /** 用户开关；数据不可用时保留，恢复后自动生效 */
        val enabled: Boolean = false,
    ) {
        val available: Boolean get() = yituliuEnabled && dataUsable
        val active: Boolean get() = enabled && available
    }

    val state: Flow<State> = combine(
        appSettings.operBoxUseYituliuApi,
        appSettings.operBoxAssistEnabled,
        operBoxRepository.snapshot,
    ) { yituliu, enabled, snap ->
        State(yituliu, snap.canAssistFormation, snap.syncTimeMillis, enabled)
    }

    suspend fun setEnabled(enabled: Boolean) = appSettings.setOperBoxAssistEnabled(enabled)

    suspend fun writeCoreData(): String? = withContext(Dispatchers.IO) {
        val file = File(pathConfig.rootDir, "${MaaFiles.OPER_BOX_DATA_DIR}/${MaaFiles.OPER_BOX_DATA_FILE}")
        try {
            file.parentFile?.mkdirs()
            file.writeText(encodeCoreData(operBoxRepository.snapshot.value.owned), Charsets.UTF_8)
            pathConfig.toCorePath(file.absolutePath)
        } catch (e: IOException) {
            Timber.e(e, "write operbox data failed")
            null
        }
    }

    @Serializable
    private data class CoreData(@SerialName("own_opers") val ownOpers: List<OperBoxOperator>)

    companion object {
        internal fun encodeCoreData(owned: List<OperBoxOperator>): String =
            JsonUtils.common.encodeToString(CoreData.serializer(), CoreData(owned))
    }
}
