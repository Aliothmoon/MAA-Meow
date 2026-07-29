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
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 工具类任务的结构化结果收集器
 * 由 SubTaskHandler 在收到对应回调时转发数据
 */
class ToolboxResultCollector(
    private val resourceDataManager: ResourceDataManager,
    private val achievementRepository: AchievementRepository,
    private val itemHelper: ItemHelper,
    private val depotRepository: DepotRepository,
    private val operBoxRepository: OperBoxRepository,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 本轮任务链是否期待 DoubleSync（更新数据双 due 且已启动成功）。
     * 小工具单独识别不会 arm，避免误触。
     */
    @Volatile
    private var doubleSyncArmed = false

    @Volatile
    private var doubleSyncOperDone = false

    @Volatile
    private var doubleSyncDepotDone = false

    /** 任务链启动成功且规划双识别到期时调用。 */
    fun armDoubleSyncSession() {
        doubleSyncArmed = true
        doubleSyncOperDone = false
        doubleSyncDepotDone = false
    }

    /** 任务链结束/停止时清除，避免跨会话误报。 */
    fun clearDoubleSyncSession() {
        doubleSyncArmed = false
        doubleSyncOperDone = false
        doubleSyncDepotDone = false
    }

    private fun noteDoubleSyncOperSuccess() {
        if (!doubleSyncArmed) return
        doubleSyncOperDone = true
        tryReportDoubleSync()
    }

    private fun noteDoubleSyncDepotSuccess() {
        if (!doubleSyncArmed) return
        doubleSyncDepotDone = true
        tryReportDoubleSync()
    }

    private fun tryReportDoubleSync() {
        if (!doubleSyncArmed || !doubleSyncOperDone || !doubleSyncDepotDone) return
        doubleSyncArmed = false
        ioScope.launch {
            achievementRepository.report {
                event = AchievementEvents.TOOLBOX_RESULT
                "tool" to "DepotOperBox"
            }
        }
    }

    // ==================== 公招识别 ====================

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

    // ==================== 仓库识别 ====================

    /**
     * 解析仓库识别结果并写入 [DepotRepository]（小工具 UI 读持久化快照）。
     * MaaCore 回调 taskchain="Depot" 时 details 格式：
     * { "done": true, "data": "{\"30011\":200,...}" }
     */
    fun onDepotResult(details: JSONObject?) {
        details ?: return
        if (!details.getBooleanValue("done")) return

        val dataStr = details.getString("data") ?: return
        val dataObj = com.alibaba.fastjson2.JSON.parseObject(dataStr) ?: return
        val items = dataObj.entries.mapNotNull { (id, value) ->
            val count = (value as? Number)?.toInt() ?: return@mapNotNull null
            if (count > 0) DepotItem(id, count) else null
        }
        // 同步写穿内存，保证随后 TaskChainStart 重算能读到最新库存
        depotRepository.replaceAllSync(items)
        noteDoubleSyncDepotSuccess()
        ioScope.launch {
            achievementRepository.report {
                event = AchievementEvents.TOOLBOX_RESULT
                "tool" to "Depot"
                "maxCount" to (items.maxOfOrNull { it.count } ?: 0)
            }
        }
    }

    // ==================== 干员识别 ====================

    /**
     * 解析干员识别结果并写入 [OperBoxRepository]。
     * MaaCore 回调 taskchain="OperBox" 时 details 格式：
     * { "done": true, "own_opers": [ { id, name, rarity, elite, level, potential, own } ] }
     */
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

        // 识别成功即记 DoubleSync 半边（不等落盘）；写盘仍异步
        noteDoubleSyncOperSuccess()
        ioScope.launch {
            operBoxRepository.replaceAll(ownedSorted, notOwnedSorted)
            achievementRepository.report {
                event = AchievementEvents.TOOLBOX_RESULT
                "tool" to "OperBox"
                "hasPallas" to ownOpers.any {
                    it.name == "帕拉斯" || it.name.equals("Pallas", ignoreCase = true)
                }
            }
        }
    }
}
