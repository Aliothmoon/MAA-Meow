package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.api.MaaApiService
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.activity.ActivityStage
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import com.aliothmoon.maameow.data.model.activity.StageActivityRoot
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * MAA 资源管理器
 * 负责加载和缓存 item_index.json 以及活动关卡数据
 * see StageManager
 */
class ActivityManager(
    private val maaApiService: MaaApiService,
    private val pathConfig: MaaPathConfig,
    private val resourceLoader: MaaResourceLoader,
    private val taskConfigState: TaskConfigState,
    private val itemHelper: ItemHelper
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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

    /**
     * 预加载资源（必须在 UI 访问数据之前调用）
     * 本地数据同步加载，活动关卡异步加载
     */
    suspend fun preload() {
        withContext(Dispatchers.IO) {
            itemHelper.load()
        }
        loadActivityStages()
    }


    // ==================== 活动关卡相关 ====================

    /**
     * 获取活动关卡列表
     * @param onlyOpen 是否只返回正在开放的活动关卡
     */
    fun getActivityStages(onlyOpen: Boolean = true): List<ActivityStage> {
        val stages = _activityStages.value
        return if (onlyOpen) {
            stages.filter { it.isAvailable }
        } else {
            stages
        }
    }

    /**
     * 获取小游戏列表
     * @param onlyOpen 是否只返回正在开放的小游戏
     */
    fun getMiniGames(onlyOpen: Boolean = true): List<MiniGame> {
        val games = _miniGames.value
        return if (onlyOpen) {
            games.filter { it.isOpen }
        } else {
            games
        }
    }

    /**
     * 获取资源收集活动信息
     * @return 如果资源收集活动正在进行，返回活动信息；否则返回 null
     */
    fun getResourceCollection(): StageActivityInfo? {
        return _resourceCollection.value?.takeIf { it.isOpen }
    }

    /**
     * 刷新活动关卡数据（从网络重新加载）
     */
    suspend fun refreshActivityStages() {
        loadActivityStages()
    }

    /**
     * 从网络加载活动关卡数据
     * 迁移自 WPF LoadWebStages
     * 并行下载 StageActivityV2.json 和 tasks.json
     * 非 Official 客户端额外下载全球服 tasks.json
     */
    private suspend fun loadActivityStages() {
        withContext(Dispatchers.IO) {
            val stageDeferred = async { maaApiService.getStageActivity() }
            val tasksDeferred = async { downloadAndDeployTasksConfig() }

            val effectiveClient = getEffectiveClientType()
            val globalTasksDeferred = if (effectiveClient != "Official") {
                async { downloadAndDeployGlobalTasksConfig(effectiveClient) }
            } else {
                null
            }

            tasksDeferred.await()
            globalTasksDeferred?.await()

            val jsonContent = stageDeferred.await()
            if (jsonContent == null) {
                Timber.w("无法获取活动关卡数据")
                _activityStages.value = emptyList()
                _miniGames.value = emptyList()
                return@withContext
            }

            try {
                parseActivityStages(jsonContent)
                buildMergedStagesMap()
            } catch (e: Exception) {
                Timber.e(e, "解析活动关卡数据失败")
                _activityStages.value = emptyList()
                _miniGames.value = emptyList()
            }
        }
    }

    /**
     * 解析活动关卡 JSON
     * from WPF ParseActivityStages
     */
    private fun parseActivityStages(jsonContent: String) {
        val root = json.decodeFromString<StageActivityRoot>(jsonContent)
        val official = root.official

        if (official == null) {
            Timber.w("活动关卡数据中没有 Official 字段")
            _activityStages.value = emptyList()
            _miniGames.value = emptyList()
            return
        }

        val activityStages = mutableListOf<ActivityStage>()

        // 解析支线活动
        // TODO: 需要支持（YoStarJP/YoStarKR/YoStarEN），当前只解析 Official
        official.sideStoryStage?.forEach { (key, entry) ->
            val groupMinReq = entry.minimumRequired
            val groupActivityInfo = entry.activity?.let { info ->
                StageActivityInfo.fromActivityInfo(key, info)
            }

            entry.stages?.forEach { stageRaw ->
                // Per-stage MinimumRequired，fallback 到分组级别 (WPF: stageObj["MinimumRequired"] ?? groupMinReqStr)
                val minReq = stageRaw.minimumRequired ?: groupMinReq
                if (!MaaCoreVersion.meetsMinimumRequired(minReq)) {
                    Timber.d("跳过关卡 ${stageRaw.value}: 需要版本 $minReq")
                    return@forEach
                }

                // Per-stage Activity override (WPF: stageObj["Activity"] ?? activityToken)
                val stageActivityInfo = stageRaw.activity?.let { info ->
                    StageActivityInfo.fromActivityInfo(key, info)
                } ?: groupActivityInfo

                activityStages.add(
                    ActivityStage.fromRaw(stageRaw, stageActivityInfo, key)
                )
            }
        }

        // 解析小游戏 (对标 WPF ParseMiniGameEntries)
        val parsedMiniGames = official.miniGame
            ?.map { MiniGame.fromEntry(it) }
            ?.filter { it.isOpen }  // WPF: entry.BeingOpen
            ?: emptyList()

        // 合并默认小游戏 (对标 WPF InitializeDefaultMiniGameEntries + InsertRange)
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
        val miniGames = parsedMiniGames + defaultMiniGames  // API 在前，默认在后

        // 解析资源收集活动
        val resourceCollection = official.resourceCollection?.let { info ->
            StageActivityInfo.fromResourceCollection(info)
        }

        _activityStages.value = activityStages
        _miniGames.value = miniGames
        _resourceCollection.value = resourceCollection

        Timber.d("加载了 ${activityStages.size} 个活动关卡, ${miniGames.size} 个小游戏")
        if (resourceCollection?.isOpen == true) {
            Timber.d("资源收集活动进行中: ${resourceCollection.tip}")
        }
    }

    // ==================== 合并关卡列表 ====================

    /**
     * 关卡分组数据
     * 用于 UI 显示分组标题
     */
    data class StageGroup(
        val title: String,           // 分组标题
        val stages: List<StageItem>, // 关卡列表
        val daysLeftText: String? = null  // 剩余天数文本（活动关卡分组）
    )

    /**
     * 统一的关卡项（活动关卡和常驻关卡的统一表示）
     */
    data class StageItem(
        val code: String,            // 关卡代码（如 "ME-8", "CE-6"）
        val displayName: String,     // 显示名称
        val isActivityStage: Boolean = false,  // 是否为活动关卡
        val isOpenToday: Boolean = true,       // 今天是否开放
        val drop: String? = null,    // 掉落物品 ID（活动关卡）
        val dropGroups: List<List<String>> = emptyList()  // 芯片本掉落组 (迁移自 WPF)
    )

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

    // ==================== tasks.json 热更新 ====================

    /**
     * 下载 tasks.json 并部署到缓存资源目录
     * DiskCache 自动写入 {cacheDir}/resource/tasks.json，与 MaaCore LoadResource(cacheDir) 对齐
     */
    private suspend fun downloadAndDeployTasksConfig() {
        try {
            maaApiService.getTasksConfig()
        } catch (e: Exception) {
            Timber.w(e, "下载/部署 tasks.json 失败")
        }
    }


    /**
     * 下载全球服 tasks.json 并部署到缓存资源目录
     * 路径: cache/resource/global/{clientType}/resource/tasks.json
     */
    private suspend fun downloadAndDeployGlobalTasksConfig(clientType: String) {
        try {
            maaApiService.getGlobalTasksConfig(clientType)
        } catch (e: Exception) {
            Timber.w(e, "下载/部署全球服 tasks.json 失败: $clientType")
        }
    }

    /**
     * 获取有效的客户端类型
     * 官服和B服使用同样的资源 (对标 WPF GetClientType)
     */
    private fun getEffectiveClientType(): String {
        val clientType = taskConfigState.wakeUpConfig.value.clientType
        return if (clientType == "Bilibili" || clientType.isBlank()) "Official" else clientType
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
