package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.model.copilot.CopilotGroup
import com.aliothmoon.maameow.data.model.copilot.CopilotOperator
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.PromotedOperIds
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 按 ID 对位，干员名随显示语言变 */
class CopilotOperatorMatching(
    private val appSettings: AppSettingsManager,
    operBoxRepository: OperBoxRepository,
    private val resourceDataManager: ResourceDataManager,
) {
    data class Settings(
        val enabled: Boolean = true,
        val modes: Set<OperatorMatchMode> = DEFAULT_MODES,
    )

    class Matcher(
        private val owned: Map<String, OperBoxOperator>,
        private val resolveId: (CopilotOperator) -> String?,
    ) {
        /** Core 识别结果，没有技能与模组 */
        val partial: Boolean = owned.values.none { it.hasTrainingData }

        fun match(opers: List<CopilotOperator>, groups: List<CopilotGroup>): OperatorMatch =
            CopilotOperatorMatcher.match(opers, groups) { oper ->
                val id = resolveId(oper) ?: return@match null
                // Core 识别只回升变干员的当前形态，一图流则给全部形态
                owned[id] ?: if (partial) PromotedOperIds.formsOf(id).firstNotNullOfOrNull { owned[it] } else null
            }
    }

    val settings: Flow<Settings> = combine(
        appSettings.copilotOperMatchEnabled,
        appSettings.copilotOperMatchModes,
    ) { enabled, modes -> Settings(enabled, parseModes(modes)) }

    val matcher: Flow<Matcher?> = combine(
        operBoxRepository.snapshot,
        resourceDataManager.nameIndex,
    ) { snapshot, _ ->
        snapshot.owned.filter { it.own }.associateBy { it.id }
            .takeIf { it.isNotEmpty() }
            ?.let { Matcher(it, ::resolveId) }
    }

    suspend fun setEnabled(enabled: Boolean) = appSettings.setCopilotOperMatchEnabled(enabled)

    suspend fun setModes(modes: Set<OperatorMatchMode>) =
        appSettings.setCopilotOperMatchModes(OperatorMatchMode.entries.filter { it in modes }.joinToString(","))

    /** 同名升变干员按作业里的职业区分 */
    private fun resolveId(oper: CopilotOperator): String? {
        val info = resourceDataManager.getCharacterByNameOrAlias(oper.name) ?: return null
        if (oper.role.isBlank() || info.profession.equals(oper.role, ignoreCase = true)) return info.id
        return PromotedOperIds.formsOf(info.id).firstOrNull { id ->
            resourceDataManager.getCharacterById(id)?.profession.equals(oper.role, ignoreCase = true)
        } ?: info.id
    }

    companion object {
        val DEFAULT_MODES = setOf(OperatorMatchMode.READY, OperatorMatchMode.BORROW, OperatorMatchMode.TRAIN)

        internal fun parseModes(raw: String): Set<OperatorMatchMode> =
            raw.split(',').mapNotNullTo(HashSet()) { name ->
                OperatorMatchMode.entries.firstOrNull { it.name == name.trim() }
            }.ifEmpty { DEFAULT_MODES }
    }
}
