package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.model.activity.ActivityStage
import com.aliothmoon.maameow.data.model.activity.ClientStageActivity
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import com.aliothmoon.maameow.data.model.activity.StageActivityRoot
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.collections.plus

/**
 * MAA 资源管理器
 * 负责加载和缓存 item_index.json 以及活动关卡数据
 * see StageManager
 */
class ActivityManager(
    private val maaApiService: MaaApiService,
    private val taskConfigState: TaskConfigState,
    private val itemHelper: ItemHelper,
    private val resourceDataManager: ResourceDataManager
) {

    private val _activityStages = MutableStateFlow<List<ActivityStage>>(emptyList())
    private val _miniGames = MutableStateFlow<List<MiniGame>>(emptyList())
    private val _resourceCollection = MutableStateFlow<StageActivityInfo?>(null)
    private val _stages = MutableStateFlow<Map<String, MergedStageInfo>>(emptyMap())

    /** 活动关卡列表 */
    val activityStages: StateFlow<List<ActivityStage>> = _activityStages.asStateFlow()

    /** 小游戏列表 */
    val miniGames: StateFlow<List<MiniGame>> = _miniGames.asStateFlow()

    /** 资源收集活动信息 */
    val resourceCollection: StateFlow<StageActivityInfo?> = _resourceCollection.asStateFlow()

    /** 过期活动关卡正则，如 "UR-5"、"ME-8" */
    private val expiredActivityRegex = Regex("^[A-Za-z]{2}-\\d{1,2}$")


    /** 鹰历时区（UTC+8） */
    private val serverZone = ZoneId.of("Asia/Shanghai")

    suspend fun load(clientType: String) {
        doLoadActivityStages(clientType)
    }


    /**
     * 从网络加载活动关卡数据
     * 迁移自 WPF LoadWebStages
     * 并行下载 StageActivityV2.json 和 tasks.json，然后解析并构建合并字典
     *
     * @param clientType 有效客户端类型 (Official,Bilibili, YoStarEN, YoStarJP, YoStarKR, txwy)
     */
    private suspend fun doLoadActivityStages(clientType: String) {
        val type = if (clientType == "Bilibili") "Official" else clientType
        val job = withContext(Dispatchers.IO) {
            async {
                setupHotUpdate(type)
            }
        }
        try {
            doLoadWebStages(type)
            buildMergedStagesMap()
        } catch (e: Exception) {
            Timber.e(e, "解析活动关卡数据失败")
        }
        job.await()
    }

    private suspend fun doLoadWebStages(clientType: String) {
        val jsonVal = maaApiService.getStageActivity()
        if (jsonVal == null) {
            Timber.w("无法获取活动关卡数据")
            return
        }
        val activity = StageActivityRoot.parse(jsonVal, clientType)

        if (activity == null) {
            Timber.w("活动关卡数据中没有 $clientType 字段")
            return
        }

        _activityStages.value = doParseStageInfo(activity)

        _miniGames.value = doParseMiniGame(activity)

        // 解析资源收集活动
        val resourceCollection = activity.resourceCollection?.let { info ->
            StageActivityInfo.fromResourceCollection(info)
        }


        _resourceCollection.value = resourceCollection

        Timber.d("加载了 ${_activityStages.value.size} 个活动关卡, ${_miniGames.value.size} 个小游戏")
        if (resourceCollection?.isOpen == true) {
            Timber.d("资源收集活动进行中: ${resourceCollection.tip}")
        }
    }

    private fun doParseMiniGame(activity: ClientStageActivity): List<MiniGame> {
        // 解析小游戏 (see WPF ParseMiniGameEntries)
        val parsedMiniGames = activity.miniGame
            ?.map { MiniGame.fromEntry(it) }
            ?.filter { it.isOpen }  // WPF: entry.BeingOpen
            ?: emptyList()

        // 合并默认小游戏 (see WPF InitializeDefaultMiniGameEntries + InsertRange)
        val parsedValues = parsedMiniGames.map { it.value }.toSet()
        val defaultMiniGames = DefaultMiniGames.ENTRIES
            .filter { it.value !in parsedValues }  // 按 value 去重
            .map { entry ->
                MiniGame(
                    display = entry.display,
                    value = entry.value,
                    utcStartTime = 0L,
                    utcExpireTime = Long.MAX_VALUE,
                    tipKey = entry.tipKey
                )
            }
        return parsedMiniGames + defaultMiniGames  // API 在前，默认在后
    }

    private fun doParseStageInfo(activity: ClientStageActivity): List<ActivityStage> {
        // 解析支线活动
        return activity.sideStoryStage?.flatMap { (key, entry) ->
            val groupMinReq = entry.minimumRequired
            val result = entry.stages?.mapNotNull { stageRaw ->
                // Per-stage MinimumRequired，fallback 到分组级别 (WPF: stageObj["MinimumRequired"] ?? groupMinReqStr)
                val minReq = stageRaw.minimumRequired ?: groupMinReq
                if (!MaaCoreVersion.meetsMinimumRequired(minReq)) {
                    Timber.d("跳过关卡 ${stageRaw.value}: 需要版本 $minReq")
                    return@mapNotNull null
                }

                // Per-stage Activity override (WPF: stageObj["Activity"] ?? activityToken)
                val stageActivityInfo = stageRaw.activity?.let { info ->
                    StageActivityInfo.fromActivityInfo(key, info)
                } ?: entry.activity?.let { info ->
                    StageActivityInfo.fromActivityInfo(key, info)
                }

                ActivityStage.fromRaw(stageRaw, stageActivityInfo, key)
            }
            result ?: emptyList()
        } ?: emptyList()

    }


    private suspend fun setupHotUpdate(clientType: String) {
        val job = withContext(Dispatchers.IO) {
            async {
                maaApiService.getTasksInfo()
            }
        }
        if (clientType != "Official") {
            maaApiService.getGlobalTasksInfo(clientType)
        }
        job.await()
    }


    /**
     * 获取合并后的关卡列表（按分组）
     * 迁移自 WPF MergePermanentAndActivityStages
     *
     * @param filterByToday 是否只返回今天开放的关卡
     * @return 分组后的关卡列表（活动关卡在前，常驻关卡在后）
     */
    fun getMergedStageGroups(filterByToday: Boolean = false): List<StageGroup> {
        val groups = mutableListOf<StageGroup>()
        val today = LocalDate.now().dayOfWeek
        val currentStages = _stages.value

        // 1. 活动关卡分组
        // ParseActivityStages()         活动关卡（限时开放）
        val openActivityStages = _activityStages.value.filter { it.isAvailable }

        if (openActivityStages.isNotEmpty()) {
            val activityGroups = openActivityStages.groupBy { it.activityKey }
            activityGroups.forEach { (activityKey, stages) ->
                val activityInfo = stages.firstOrNull()?.activity
                val activityTip = activityInfo?.tip ?: activityKey
                val daysLeftText = activityInfo?.getDaysLeftText()
                val stageItems = stages.map { stage ->
                    StageItem(
                        code = stage.value,
                        displayName = stage.display,
                        isActivityStage = true,
                        isOpenToday = true,
                        drop = stage.drop
                    )
                }
                if (stageItems.isNotEmpty()) {
                    groups.add(
                        StageGroup(
                            title = activityTip,
                            stages = stageItems,
                            daysLeftText = daysLeftText
                        )
                    )
                }
            }
        }

        // 2. 常驻关卡分组
        // InitializeDefaultStages()     固定关卡（剿灭等）
        val defaultStageItem = StageItem(
            code = "",
            displayName = "当前/上次",
            isActivityStage = false,
            isOpenToday = true
        )

        // AddPermanentStages()          常驻关卡（主线/资源本等）
        val permanentStages = listOf(defaultStageItem) + PermanentStages.STAGES.map { stage ->
            val mergedInfo = currentStages[stage.code]
            val isOpen = mergedInfo?.isStageOpen(today) ?: stage.isOpenOn(today)
            StageItem(
                code = stage.code,
                displayName = stage.displayName,
                isActivityStage = false,
                isOpenToday = isOpen,
                dropGroups = stage.dropGroups
            )
        }

        val filteredPermanent = if (filterByToday) {
            permanentStages.filter { it.isOpenToday }
        } else {
            permanentStages
        }

        if (filteredPermanent.isNotEmpty()) {
            groups.add(StageGroup(title = "常驻关卡", stages = filteredPermanent))
        }

        return groups
    }

    /**
     * 获取合并后的扁平关卡列表（不含分组信息）
     *
     * @param filterByToday 是否只返回今天开放的关卡
     * @return 关卡列表（活动关卡在前，常驻关卡在后）
     */
    fun getMergedStageList(filterByToday: Boolean = false): List<StageItem> {
        return getMergedStageGroups(filterByToday).flatMap { it.stages }
    }

    /**
     * 判断是否为资源本（受资源收集活动影响）
     */
    private fun isResourceStage(code: String): Boolean {
        return code.startsWith("CE-") ||
                code.startsWith("LS-") ||
                code.startsWith("CA-") ||
                code.startsWith("AP-") ||
                code.startsWith("SK-") ||
                code.startsWith("PR-")
    }

    /**
     * 资源收集活动是否开放
     */
    fun isResourceCollectionOpen(): Boolean {
        return _resourceCollection.value?.isOpen == true
    }


    /**
     * 构建合并关卡字典
     * 迁移自 WPF StageManager._stages 合并逻辑
     * 将常驻关卡 + 活动关卡合并到 mergedStagesMap
     */
    private fun buildMergedStagesMap() {
        val result = mutableMapOf<String, MergedStageInfo>()
        // 1. 添加活动关卡
        _activityStages.value.forEach { stage ->
            result[stage.value] = MergedStageInfo(
                code = stage.value,
                displayName = stage.display,
                activity = stage.activity,
                drop = stage.drop
            )
        }

        // 2. 添加常驻关卡
        val resourceCollection = _resourceCollection.value
        PermanentStages.STAGES.forEach { stage ->
            if (!result.containsKey(stage.code)) {
                val activity = if (isResourceStage(stage.code)) resourceCollection else null
                result[stage.code] = MergedStageInfo(
                    code = stage.code,
                    displayName = stage.displayName,
                    openDays = stage.openDays,
                    activity = activity,
                    tip = stage.tip
                )
            }
        }

        // 3. 移除过期活动关卡
        result.entries.removeAll { (code, info) ->
            info.activity != null && info.activity.isExpired && !info.activity.isResourceCollection && expiredActivityRegex.matches(
                code
            )
        }

        _stages.value = result
        Timber.d("合并关卡字典已构建，共 ${result.size} 个关卡")
    }

    /**
     * 计算到下一个更新时间点的延迟（毫秒）
     * 鹰角历日期切换点: UTC+8 的 04:00 和 16:00，加 0~10 分钟随机延迟
     */
    private fun calcNextUpdateDelayMs(): Long {
        val now = ZonedDateTime.now(serverZone)
        val today = now.toLocalDate()

        // 今天的两个切换点
        val switchPoints = listOf(
            today.atTime(4, 0).atZone(serverZone),
            today.atTime(16, 0).atZone(serverZone)
        )

        // 找到下一个切换点
        val nextSwitch = switchPoints.firstOrNull { it.isAfter(now) }
            ?: today.plusDays(1).atTime(4, 0).atZone(serverZone)

        val baseDelay = ChronoUnit.MILLIS.between(now, nextSwitch)
        // 0~10 分钟随机延迟
        val randomDelay = (Math.random() * 10 * 60 * 1000).toLong()
        return baseDelay + randomDelay
    }

    // ==================== 关卡查询与提示 ====================

    /**
     * 获取关卡信息（带 fallback）
     * 迁移自 WPF StageManager.GetStageInfo
     */
    fun getStageInfo(stage: String): MergedStageInfo {
        _stages.value[stage]?.let { return it }

        // 匹配 "XX-N" 格式的过期活动关卡
        if (stage.isNotEmpty() && expiredActivityRegex.matches(stage)) {
            val expiredActivity =
                StageActivityInfo(name = stage, tip = "", utcStartTime = 0L, utcExpireTime = 0L)
            return MergedStageInfo(
                code = stage,
                displayName = stage,
                activity = expiredActivity
            )
        }

        // Fallback: 当作常驻关卡
        return MergedStageInfo(code = stage, displayName = stage)
    }

    /**
     * 关卡是否在列表中
     * 迁移自 WPF StageManager.IsStageInStageList
     */
    fun isStageInStageList(stage: String): Boolean {
        return _stages.value.containsKey(stage)
    }

    /**
     * 添加未开放关卡
     * 迁移自 WPF StageManager.AddUnOpenStage
     */
    fun addUnOpenStage(stage: String) {
        val unopenActivity = StageActivityInfo(
            name = stage,
            tip = "",
            utcStartTime = 0L,
            utcExpireTime = 0L
        )
        _stages.value = _stages.value + (stage to MergedStageInfo(
            code = stage,
            displayName = stage,
            activity = unopenActivity
        ))
    }

    /**
     * 获取今日关卡提示文本
     * 迁移自 WPF StageManager.GetStageTips
     *
     * @param dayOfWeek 星期几
     * @return 提示文本行列表
     */
    fun getStageTips(
        dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek
    ): List<String> {
        val lines = mutableListOf<String>()
        val shownSideStories = mutableSetOf<String>()
        var resourceTipShown = false

        for ((_, stageInfo) in _stages.value) {
            if (!stageInfo.isStageOpen(dayOfWeek)) continue

            val activity = stageInfo.activity

            // 1. 资源收集活动提示 (只显示一次)
            if (!resourceTipShown && activity != null && activity.isResourceCollection && activity.isOpen) {
                lines.add(0, "｢${activity.tip}｣ 剩余开放${activity.getDaysLeftText()}")
                resourceTipShown = true
            }

            // 2. 支线活动提示 (按活动名去重)
            if (activity != null && activity.name.isNotEmpty() && !activity.isResourceCollection) {
                if (shownSideStories.add(activity.name)) {
                    lines.add("｢${activity.name}｣ 剩余开放${activity.getDaysLeftText()}")
                }
            }

            // 3. 活动关卡掉落物品
            if (!stageInfo.drop.isNullOrEmpty()) {
                val text = itemHelper.getItemInfo(stageInfo.drop)?.name ?: stageInfo.drop
                lines.add("${stageInfo.code}: $text")
            }

            // 4. 常规关卡提示
            if (stageInfo.tip.isNotEmpty()) {
                lines.add(stageInfo.tip)
            }
        }

        return lines
    }
}
