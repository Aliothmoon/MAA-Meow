package com.aliothmoon.maameow.maa.callback

import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.toolbox.DepotItem
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.data.model.toolbox.RecruitCalcResult
import com.aliothmoon.maameow.data.model.toolbox.RecruitOperator
import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.repository.OperBoxRepository
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 工具类任务结果：SubTaskHandler 回调转发。 */
class ToolboxResultCollector(
    private val resourceDataManager: ResourceDataManager,
    private val achievementRepository: AchievementRepository,
    private val depotRepository: DepotRepository,
    private val operBoxRepository: OperBoxRepository,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val achievement = DoubleSyncAchievement()

    fun onSessionStart() = achievement.clear()

    private val _recruitTags = MutableStateFlow<List<String>>(emptyList())
    val recruitTags: StateFlow<List<String>> = _recruitTags.asStateFlow()

    private val _recruitResults = MutableStateFlow<List<RecruitCalcResult>>(emptyList())
    val recruitResults: StateFlow<List<RecruitCalcResult>> = _recruitResults.asStateFlow()

    fun onRecruitTagsDetected(details: JSONObject?) {
        val tags = details?.getJSONArray("tags")
            ?.mapNotNull { it?.toString() }
            ?: return
        _recruitTags.value = tags
    }

    fun onRecruitResult(details: JSONObject?) {
        details ?: return
        val result = details.getJSONArray("result") ?: return
        val parsed = result.mapNotNull { entry ->
            val obj = entry as? JSONObject ?: return@mapNotNull null
            val level = obj.getIntValue("level", 0)
            val tags = obj.getJSONArray("tags")?.mapNotNull { it?.toString() } ?: emptyList()
            val opers = obj.getJSONArray("opers")?.mapNotNull { operEntry ->
                val oper = operEntry as? JSONObject ?: return@mapNotNull null
                RecruitOperator(
                    name = oper.getString("name") ?: "",
                    level = oper.getIntValue("level", 0),
                )
            } ?: emptyList()
            RecruitCalcResult(tags = tags, level = level, operators = opers)
        }
        _recruitResults.value = parsed
    }

    fun clearRecruit() {
        _recruitTags.value = emptyList()
        _recruitResults.value = emptyList()
    }

    fun onDepotResult(details: JSONObject?) {
        details ?: return
        if (!details.getBooleanValue("done")) return

        val dataStr = details.getString("data") ?: return
        val dataObj = com.alibaba.fastjson2.JSON.parseObject(dataStr) ?: return
        val items = dataObj.entries.mapNotNull { (id, value) ->
            val count = (value as? Number)?.toInt() ?: return@mapNotNull null
            if (count > 0) DepotItem(id, count) else null
        }
        depotRepository.set(items)
        achievement.onDepotSuccess()
        ioScope.launch {
            achievementRepository.report {
                event = AchievementEvents.TOOLBOX_RESULT
                "tool" to "Depot"
                "maxCount" to (items.maxOfOrNull { it.count } ?: 0)
            }
        }
    }

    fun onOperBoxResult(details: JSONObject?) {
        details ?: return
        if (!details.getBooleanValue("done")) return

        val ownOpers = details.getJSONArray("own_opers")?.mapNotNull { entry ->
            val obj = entry as? JSONObject ?: return@mapNotNull null
            OperBoxOperator(
                id = obj.getString("id") ?: return@mapNotNull null,
                name = obj.getString("name") ?: "",
                rarity = obj.getIntValue("rarity", 0),
                elite = obj.getIntValue("elite", 0),
                level = obj.getIntValue("level", 0),
                potential = obj.getIntValue("potential", 0),
                own = true,
            )
        } ?: return

        val ownedIds = ownOpers.map { it.id }.toSet()

        val notOwned = resourceDataManager.operators.value
            .filter { (id, _) -> id !in ownedIds }
            .map { (id, info) ->
                OperBoxOperator(
                    id = id,
                    name = info.name,
                    rarity = info.rarity,
                    elite = 0,
                    level = 0,
                    potential = 0,
                    own = false,
                )
            }

        val ownedSorted = ownOpers.sortedWith(
            compareByDescending<OperBoxOperator> { it.rarity }
                .thenByDescending { it.elite }
                .thenByDescending { it.level }
                .thenByDescending { it.potential },
        )
        val notOwnedSorted = notOwned.sortedByDescending { it.rarity }

        operBoxRepository.set(ownedSorted, notOwnedSorted)
        achievement.onOperSuccess()
        ioScope.launch {
            achievementRepository.report {
                event = AchievementEvents.TOOLBOX_RESULT
                "tool" to "OperBox"
                "hasPallas" to ownOpers.any {
                    it.name == "帕拉斯" || it.name.equals("Pallas", ignoreCase = true)
                }
            }
        }
    }

    /** 本会话 Oper+Depot 成功回调后上报 DepotOperBox。 */
    private inner class DoubleSyncAchievement {
        @Volatile
        private var operDone = false

        @Volatile
        private var depotDone = false

        fun clear() {
            operDone = false
            depotDone = false
        }

        fun onOperSuccess() {
            operDone = true
            tryReport()
        }

        fun onDepotSuccess() {
            depotDone = true
            tryReport()
        }

        private fun tryReport() {
            if (!operDone || !depotDone) return
            operDone = false
            depotDone = false
            ioScope.launch {
                achievementRepository.report {
                    event = AchievementEvents.TOOLBOX_RESULT
                    "tool" to "DepotOperBox"
                }
            }
        }
    }
}
