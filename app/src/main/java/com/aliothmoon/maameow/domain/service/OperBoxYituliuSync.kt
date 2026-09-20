package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.YituliuApiService
import com.aliothmoon.maameow.data.api.message
import com.aliothmoon.maameow.data.model.toolbox.OperBoxEquip
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.model.toolbox.OperBoxSkill
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.maa.callback.ToolboxResultCollector
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 从一图流 OpenAPI 拉取干员练度并落库，不连提权服务、不下发 Core 任务
 *
 * 只有拿到非空数据才覆盖，失败一律保留本地已有结果，也不回退 Core 识别 ——
 * 开关是用户显式打开的，静默回退会突然要求连接游戏，无人值守时不可预期
 */
class OperBoxYituliuSync(
    private val api: YituliuApiService,
    private val resourceDataManager: ResourceDataManager,
    private val collector: ToolboxResultCollector,
    private val appSettings: AppSettingsManager,
    private val chainState: TaskChainState,
) {
    sealed interface Result {
        /** [skipped] 是本地干员表不认识、被跳过的数量 */
        data class Success(val synced: Int, val skipped: Int) : Result {
            val message: UiText
                get() = if (skipped == 0) {
                    uiTextOf(R.string.oper_box_yituliu_done, synced)
                } else {
                    uiTextOf(R.string.oper_box_yituliu_done_skipped, synced, skipped)
                }
        }

        data class Failed(val message: UiText) : Result
    }

    private val mutex = Mutex()

    suspend fun sync(): Result {
        val token = appSettings.yituliuOpenApiToken.value
        if (token.isEmpty()) return failed(R.string.oper_box_yituliu_token_empty)
        if (!mutex.tryLock()) return failed(R.string.oper_box_yituliu_busy)
        return try {
            withContext(Dispatchers.IO) { doSync(token) }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doSync(token: String): Result {
        if (!ensureLocalOperators()) return failed(R.string.oper_box_yituliu_local_data_missing)

        val operators = when (val result = api.fetchOperators(token)) {
            is YituliuApiService.Result.Success -> result.operators
            is YituliuApiService.Result.Failure -> return Result.Failed(result.message())
        }
        if (operators.isEmpty()) return failed(R.string.oper_box_yituliu_no_data)

        val owned = operators.mapNotNull(::toOperBoxOperator)
        if (owned.isEmpty()) return failed(R.string.oper_box_yituliu_local_table_outdated)

        collector.applyOperBoxResult(owned)
        val skipped = operators.size - owned.size
        Timber.i("%s: synced %d operators, skipped %d", TAG, owned.size, skipped)
        return Result.Success(synced = owned.size, skipped = skipped)
    }

    /** 工具箱走这条路时没连过提权服务，干员表可能还没装载，这里补一次 */
    private suspend fun ensureLocalOperators(): Boolean {
        if (resourceDataManager.operators.value.isNotEmpty()) return true
        runCatching {
            resourceDataManager.load(chainState.clientType, appSettings.displayLanguage)
        }.onFailure { Timber.w(it, "%s: local operator data load failed", TAG) }
        return resourceDataManager.operators.value.isNotEmpty()
    }

    /**
     * 星级和名称来自本地干员表，本地资源中不存在的跳过
     *
     * 对齐上游用未过滤的干员表：阿米娅转职形态在 [ResourceDataManager.operators] 里被当虚拟干员排除，
     * 但账号确实拥有，不应该丢
     */
    private fun toOperBoxOperator(oper: YituliuApiService.Operator): OperBoxOperator? {
        val info = resourceDataManager.getCharacterById(oper.id)?.takeIf { it.isOperator } ?: run {
            Timber.i("%s: operator %s not in local resources", TAG, oper.id)
            return null
        }
        return OperBoxOperator(
            id = oper.id,
            name = resourceDataManager.getLocalizedCharacterName(info, appSettings.displayLanguage)
                ?: info.name,
            rarity = info.rarity,
            elite = oper.evolvePhase,
            level = oper.level,
            potential = oper.potentialRank,
            own = true,
            // 一图流对没上传练度的干员给 0，当作无数据
            mainSkillLevel = oper.mainSkillLevel.takeIf { it > 0 },
            skills = oper.skills.orEmpty().map { OperBoxSkill(it.id, it.level) },
            equips = oper.equips.orEmpty().map { OperBoxEquip(it.id, it.type, it.level) },
        )
    }

    private fun failed(resId: Int) = Result.Failed(uiTextOf(resId))

    companion object {
        private const val TAG = "OperBoxYituliu"
    }
}
