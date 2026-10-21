package com.aliothmoon.maameow.data.resource

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.activity.ActivityStage
import com.aliothmoon.maameow.data.model.activity.ClientStageActivity
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.model.activity.StageActivityInfo
import com.aliothmoon.maameow.data.model.activity.StageActivityRoot
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.DayOfWeek

/**
 * MAA 资源管理器
 * 负责加载和缓存 item_index.json 以及活动关卡数据
 * see StageManager
 */
class ActivityManager(
    private val context: Context,
    private val chainState: TaskChainState,
    private val itemHelper: ItemHelper,
) {

    private val _activityStages = MutableStateFlow<List<ActivityStage>>(emptyList())
    private val _miniGames = MutableStateFlow(doBuildDefaultMiniGames())
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

    /**
     * 获取当前鹰角历的星期几（根据客户端类型自动选择时区）
     * 迁移自 WPF DateTimeExtension.ToYjDateTime
     */
    fun getYjDayOfWeek(): DayOfWeek {
        return ServerTimezone.getYjDayOfWeek(chainState.clientType)
    }

    /**
     * 获取当前鹰角历星期几的中文名
     */
    fun getYjDayOfWeekName(): String {
        return ServerTimezone.getYjDayOfWeekName(chainState.clientType)
    }

    /**
     * 迁移自 WPF MergePermanentAndActivityStages
     * 合并字典先于活动列表发布，观察列表的界面读到的总是新字典
     */
    fun load(clientType: String, json: String?) {
        val activity = json?.let { StageActivityRoot.parse(it, clientType) }
        if (activity == null) Timber.w("活动关卡数据中没有 $clientType")
        val stages = activity?.let(::doParseStageInfo).orEmpty()
        val resourceCollection = activity?.resourceCollection?.let(StageActivityInfo::fromResourceCollection)
        _stages.value = buildMergedStagesMap(stages, resourceCollection)
        _resourceCollection.value = resourceCollection
        _miniGames.value = doParseMiniGame(activity)
        _activityStages.value = stages
        Timber.d("加载了 ${stages.size} 个活动关卡, ${_miniGames.value.size} 个小游戏 ($clientType)")
    }

    private fun doParseMiniGame(activity: ClientStageActivity?): List<MiniGame> {
        // 解析小游戏 (see WPF ParseMiniGameEntries)
        val parsed = activity?.miniGame?.map { MiniGame.fromEntry(it) }
            ?.filter { it.isOpen }  // WPF: entry.BeingOpen
            ?: emptyList()

        // 合并默认小游戏
        val parsedValues = parsed.map { it.value }.toSet()
        val defaults = doBuildDefaultMiniGames().filter { it.value !in parsedValues }  // 按 value 去重
        val games = parsed + defaults  // API 在前，默认在后
        return games.also {
            it.forEachIndexed { index, game ->
                Timber.d(
                    "MiniGame[%d]: display=%s, value=%s, tipKey=%s, tip=%s, isOpen=%s, isUnsupported=%s",
                    index,
                    game.display.resolve(context),
                    game.value,
                    game.tipKey,
                    game.tip.resolve(context).replace("\n", "\\n"),
                    game.isOpen,
                    game.isUnsupported
                )
            }
        }
    }

    private fun doParseStageInfo(activity: ClientStageActivity): List<ActivityStage> {
        // 解析支线活动
        return activity.sideStoryStage?.flatMap { (key, entry) ->
            val groupMinReq = entry.minimumRequired
            val groupActivityInfo = entry.activity?.let { info ->
                StageActivityInfo.fromActivityInfo(key, info)
            }
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
                } ?: groupActivityInfo

                ActivityStage.fromRaw(stageRaw, stageActivityInfo, key, groupActivityInfo)
            }
            result ?: emptyList()
        } ?: emptyList()

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
        val today = getYjDayOfWeek()
        val currentStages = _stages.value

        // 1. 活动关卡分组
        // ParseActivityStages()         活动关卡（限时开放）
        val openActivityStages = _activityStages.value.filter { it.isAvailable }

        if (openActivityStages.isNotEmpty()) {
            val activityGroups = openActivityStages.groupBy { it.activityKey }
            activityGroups.forEach { (activityKey, stages) ->
                val activityInfo = stages.firstOrNull()?.activity
                val activityTip = activityInfo?.tip ?: activityKey
                val daysLeftText = activityInfo?.let { daysLeftText(it) }
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
                            title = activityTip, stages = stageItems, daysLeftText = daysLeftText
                        )
                    )
                }
            }
        }

        // 2. 常驻关卡分组
        // InitializeDefaultStages()     固定关卡（剿灭等）
        val defaultStageItem = StageItem(
            code = "",
            displayName = context.getString(R.string.panel_fight_stage_reset_current),
            isActivityStage = false,
            isOpenToday = true
        )

        // AddPermanentStages()          常驻关卡（主线/资源本等）
        val permanentStages = listOf(defaultStageItem) + PermanentStages.STAGES.map { stage ->
            val mergedInfo = currentStages[stage.code]
            val isOpen = mergedInfo?.isStageOpen(today) ?: stage.isOpenOn(today)
            StageItem(
                code = stage.code,
                displayName = stage.displayName(context),
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
            groups.add(
                StageGroup(
                    title = context.getString(R.string.panel_fight_stage_group_permanent),
                    stages = filteredPermanent,
                    isPermanent = true,
                )
            )
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

    /** 关卡代码 → 显示名，供日志等非界面处使用；界面走 stageDisplayName */
    fun getStageDisplayName(code: String): String = when {
        code.isEmpty() -> context.getString(R.string.panel_fight_stage_reset_current)
        else -> _activityStages.value.firstOrNull { it.value == code }?.display
            ?: StageInfo.displayNameRes(code)?.let(context::getString)
            ?: code
    }

    /**
     * 判断是否为资源本（受资源收集活动影响）
     */
    private fun isResourceStage(code: String): Boolean {
        return code.startsWith("CE-") || code.startsWith("LS-") || code.startsWith("CA-") || code.startsWith(
            "AP-"
        ) || code.startsWith("SK-") || code.startsWith("PR-")
    }

    /**
     * 资源收集活动是否开放
     */
    fun isResourceCollectionOpen(): Boolean {
        return _resourceCollection.value?.isOpen == true
    }

    /**
     * 判断当前是否有 SideStory 活动进行中。
     * 迁移自 WPF StageManager.IsActivityOpen。
     *
     * 只看 SideStory 类活动，排除资源收集（后者由 [isResourceCollectionOpen] 单独判定），
     * 否则资源收集期间本方法也会为 true，库存保持的两个跳过开关就分不开了。
     */
    fun isActivityOpen(): Boolean {
        return _activityStages.value.any { stage ->
            val activity = stage.activity
            activity != null && !activity.isResourceCollection && activity.isOpen
        }
    }


    /**
     * 构建合并关卡字典
     * 迁移自 WPF StageManager._stages 合并逻辑
     * 将常驻关卡 + 活动关卡合并到 mergedStagesMap
     */
    private fun buildMergedStagesMap(
        activityStages: List<ActivityStage>,
        resourceCollection: StageActivityInfo?,
    ): Map<String, MergedStageInfo> {
        val result = mutableMapOf<String, MergedStageInfo>()
        // 1. 添加活动关卡
        activityStages.forEach { stage ->
            result[stage.value] = MergedStageInfo(
                code = stage.value,
                displayName = stage.display,
                activity = stage.activity,
                drop = stage.drop
            )
        }

        // 2. 添加常驻关卡
        PermanentStages.STAGES.forEach { stage ->
            if (!result.containsKey(stage.code)) {
                val activity = if (isResourceStage(stage.code)) resourceCollection else null
                result[stage.code] = MergedStageInfo(
                    code = stage.code,
                    displayName = stage.displayName(context),
                    openDays = stage.openDays,
                    activity = activity,
                    tipRes = stage.tipRes,
                    dropGroups = stage.dropGroups
                )
            }
        }

        // 3. 移除过期活动关卡
        result.entries.removeAll { (code, info) ->
            info.activity != null && info.activity.isExpired && !info.activity.isResourceCollection && expiredActivityRegex.matches(
                code
            )
        }

        return result
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
                code = stage, displayName = stage, activity = expiredActivity
            )
        }

        // Fallback: 当作常驻关卡
        return MergedStageInfo(code = stage, displayName = stage)
    }

    /**
     * 判断指定关卡今日是否开放（鹰角历）。
     *
     * 迁移自 WPF StageManager.IsStageOpen：先经 [getStageInfo] 兜底再判定开放状态，
     * 因此不在候选列表中的「数字型主线关卡」（如 16-14）会被当作常规关卡 → 永远开放；
     * 「两字母-数字」的过期活动关卡（如 UR-5）→ 判为未开放；空串（当前/上次）→ 视为开放。
     *
     * 注意：不要用 [getMergedStageList] 的成员资格来判断开放，那只是 UI 候选池、不含主线关卡。
     */
    fun isStageOpen(stage: String, dayOfWeek: DayOfWeek = getYjDayOfWeek()): Boolean {
        return getStageInfo(stage).isStageOpen(dayOfWeek)
    }

    /**
     * 判断指定关卡是否为常驻关卡（无周期限制且非限时活动，每天都开放）。
     *
     * 迁移自 WPF FightSettingsUserControlModel.IsPermanentStage：
     * 资源本（如 LS-6）虽关联资源收集活动，但 isResourceCollection 为 true 且无周期限制，同样视为常驻。
     * 注意：空串（当前/上次）经 [getStageInfo] fallback 后也会返回 true。
     */
    fun isPermanentStage(stage: String): Boolean {
        val info = getStageInfo(stage)
        val noPeriodicLimit = info.openDays.isEmpty()
        val notLimitedActivity = info.activity == null || info.activity.isResourceCollection
        return noPeriodicLimit && notLimitedActivity
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
            name = stage, tip = "", utcStartTime = 0L, utcExpireTime = 0L
        )
        _stages.value += (stage to MergedStageInfo(
            code = stage, displayName = stage, activity = unopenActivity
        ))
    }

    /**
     * 获取今日关卡提示文本
     * 迁移自 WPF StageManager.GetStageTips
     *
     * @param dayOfWeek 星期几
     * @param inventory 仓库缓存 itemId → 数量
     * @param hasSyncedInventory 是否完成过全量仓库识别；识别后缺失项按 0 处理，否则视为未知
     * 有意偏离 WPF：上游缺失项显示 --，一项都没有时整行隐藏；
     * 缺失也可能是识别漏掉（core 丢弃数量识别为 0 的格子），但真没有的情况远更常见，显示 0 更有用
     * @return 提示文本行列表
     */
    fun getStageTips(
        dayOfWeek: DayOfWeek = getYjDayOfWeek(),
        inventory: Map<String, Int> = emptyMap(),
        hasSyncedInventory: Boolean = false
    ): List<String> {
        val lines = mutableListOf<String>()
        val shownSideStories = mutableSetOf<String>()
        var resourceTipShown = false
        val inventoryLabel = context.getString(R.string.panel_fight_stage_tip_inventory)
        fun inventoryCount(itemId: String): Int? =
            inventory[itemId] ?: if (hasSyncedInventory) 0 else null

        for ((_, stageInfo) in _stages.value) {
            if (!stageInfo.isStageOpen(dayOfWeek)) continue

            val activity = stageInfo.activity

            // 1. 资源收集活动提示 (只显示一次)
            if (!resourceTipShown && activity != null && activity.isResourceCollection && activity.isOpen) {
                lines.add(0, daysLeftLine(activity.tip, activity))
                resourceTipShown = true
            }

            // 2. 支线活动提示 (按活动名去重)
            if (activity != null && activity.name.isNotEmpty() && !activity.isResourceCollection) {
                if (shownSideStories.add(activity.name)) {
                    lines.add(daysLeftLine(activity.name, activity))
                    // 同名活动下的小游戏入口
                    _miniGames.value
                        .filter { it.activity == activity.name && it.isOpen }
                        .forEach { game ->
                            lines.add(
                                context.getString(
                                    R.string.panel_fight_stage_tip_affiliated_mini_game,
                                    game.display.resolve(context)
                                )
                            )
                        }
                }
            }

            // 3. 活动关卡掉落物品，识别过的物品带库存
            if (!stageInfo.drop.isNullOrEmpty()) {
                val text = itemHelper.getItemInfo(stageInfo.drop)?.name ?: stageInfo.drop
                val count = inventory[stageInfo.drop]
                lines.add(
                    if (count != null) "${stageInfo.code}: $text ($inventoryLabel $count)"
                    else "${stageInfo.code}: $text"
                )
            }

            // 4. 常规关卡提示
            stageInfo.tipRes?.let { lines.add(context.getString(it)) }

            // 5. 分组库存（技能书、芯片等），格式同 WPF DropGroups，缺失项口径见 hasSyncedInventory
            if (stageInfo.dropGroups.any { group -> group.any { inventoryCount(it) != null } }) {
                val groups = stageInfo.dropGroups.joinToString(" / ") { group ->
                    group.joinToString(" & ") { itemId -> inventoryCount(itemId)?.toString() ?: "--" }
                }
                lines.add(" ($inventoryLabel $groups)")
            }
        }

        return lines
    }

    private fun daysLeftLine(name: String, activity: StageActivityInfo): String =
        "｢$name｣ ${context.getString(R.string.panel_fight_activity_days_left_open)}${
            daysLeftText(
                activity
            )
        }"

    /** 迁移自 WPF StageManager.GetDaysLeftText */
    private fun daysLeftText(activity: StageActivityInfo): String {
        val daysLeft = activity.getDaysLeft()
        return if (daysLeft > 0) daysLeft.toString()
        else context.getString(R.string.panel_fight_activity_less_than_one_day)
    }

    // ============ 活动感知过期药辅助 ============

    data class ActivitySummary(
        val name: String, val daysLeft: Long, val isExpiringSoon: Boolean
    )

    fun isAnyActivityExpiringSoon(): Boolean {
        return _activityStages.value.any { stage ->
            stage.activity?.let { it.isOpen && it.getDaysLeft() < 2 } == true
        }
    }

    fun getActivityAwareExpireDays(): Int {
        if (!isAnyActivityExpiringSoon()) return 0
        val yjDow = getYjDayOfWeek()
        return ((7 - yjDow.value + 7) % 7) + 1
    }

    fun getOpenActivitySummaries(): List<ActivitySummary> {
        return _activityStages.value.mapNotNull { it.activity }.filter { it.isOpen }
            .distinctBy { it.name }.map { info ->
                val days = info.getDaysLeft()
                ActivitySummary(
                    name = info.name, daysLeft = days, isExpiringSoon = days < 2
                )
            }
    }

    companion object {
        private fun doBuildDefaultMiniGames(): List<MiniGame> =
            DefaultMiniGames.ENTRIES.map { entry ->
                MiniGame(
                    display = uiTextOf(entry.displayRes),
                    value = entry.value,
                    utcStartTime = 0L,
                    utcExpireTime = Long.MAX_VALUE,
                    tip = uiTextOf(entry.tipRes),
                    category = uiTextOf(entry.categoryRes)
                )
            }
    }
}
