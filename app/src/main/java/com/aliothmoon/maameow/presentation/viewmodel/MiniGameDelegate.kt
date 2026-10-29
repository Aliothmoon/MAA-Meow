package com.aliothmoon.maameow.presentation.viewmodel

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.achievement.AchievementEvents
import com.aliothmoon.maameow.data.achievement.AchievementRepository
import com.aliothmoon.maameow.data.model.EventShopBlacklist
import com.aliothmoon.maameow.data.model.EventShopPreset
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.ReclamationConfig
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber

private const val SECRET_FRONT_VALUE = "MiniGame@SecretFront"

/** 热更的 StageActivityV2 下发的是带 @Begin 的写法，两种都认，对齐上游 IsPixelPaint */
private val PIXEL_PAINT_VALUES = setOf("MiniGame@PixelPaint", "MiniGame@PixelPaint@Begin")

/** 实际下发的任务名固定，与选中项的 Value 无关，对齐上游 AsstPixelPaint */
private const val PIXEL_PAINT_TASK = "MiniGame@PixelPaint@Begin"
/** 活动商店，黑名单非空时带 params.event_shop.blacklist */
private const val EVENT_SHOP_VALUE = "SS@Store@Begin"
private const val DEFAULT_TASK_NAME = EVENT_SHOP_VALUE

/** 自动提升潜能，勾选后带 params.auto_raise_potential.use_normal_token */
private const val AUTO_RAISE_POTENTIAL_VALUE = "MiniGame@AutoRaisePotential@Begin"

/** 生息演算不走 CustomTask，直接以 Reclamation 任务类型下发，对齐上游 AsstReclamation */
private const val RECLAMATION_VALUE = "Reclamation"

/**
 * 界园·电表倒转。列表里登记的 value 只是「通用闸门」节点名，
 * 真正下发的是带序号的入口 + 轮次字母：
 *
 *   `JieGarden@Roguelike@MeterRewind@HEnter<N>`  投「衡如常」
 *   `JieGarden@Roguelike@MeterRewind@LEnter<N>`  投「厉如锋」
 *
 * `H` / `L` 是两条完全独立的资源链（只有投钱那一步不同）。之所以要在入口上分叉，
 * 是因为资源层没有跨任务状态：每个 Custom 任务只投一次钱，`maxTimes` 计数每轮归零，
 * 光靠 `maxTimes: 5` 永远凑不满 5 次衡。
 */
private const val METER_REWIND_VALUE = "JieGarden@Roguelike@MeterRewind"
private const val METER_REWIND_ENTER_PREFIX = "JieGarden@Roguelike@MeterRewind@"

/** 投衡如常的链路前缀 */
private const val VARIANT_HENG = "H"

/** 投厉如锋的链路前缀（奖励 = 要酒一壶，6 烛火） */
private const val VARIANT_LI = "L"

/** 投厉如锋 + 奖励选收藏品的链路前缀（要兵器一对，6 件随机收藏品） */
private const val VARIANT_RELIC = "R"

/** 一个完整周期 = 5 次衡如常 + 1 次厉如锋，第 6 次投厉 */
private const val METER_REWIND_LI_EVERY = 6

/**
 * 第 [round] 次投钱（从 1 开始）该投厉如锋吗？
 *
 * 5 衡 1 厉 → 第 6、12、18… 次投厉，其余投衡。炸火时的「补投一次厉」由资源层
 * `@HCompLi` 处理（只在投衡轮的分支里），不占用这里的名额。
 */
private fun meterRewindIsLiRound(round: Int): Boolean =
    round > 0 && round % METER_REWIND_LI_EVERY == 0

data class MiniGameUiState(
    val selectedTaskName: String = DEFAULT_TASK_NAME,
    val selectedEnding: String = "A",
    val selectedEvent: String = "",
    val useNormalToken: Boolean = false,
    val statusMessage: UiText = UiText.Empty,
)

class MiniGameDelegate(
    private val appContext: Context,
    activityManager: ActivityManager,
    private val compositionService: MaaCompositionService,
    private val scope: CoroutineScope,
    private val achievementRepository: AchievementRepository,
    private val pixelArt: PixelArtDelegate,
    private val sessionLogger: MaaSessionLogger,
    private val appSettingsManager: AppSettingsManager,
    private val clientType: () -> String,
) {

    private val _state = MutableStateFlow(MiniGameUiState())
    val state: StateFlow<MiniGameUiState> = _state.asStateFlow()

    val miniGames: StateFlow<List<MiniGame>> = activityManager.miniGames

    val eventShopPresets: StateFlow<Set<EventShopPreset>> = appSettingsManager.eventShopBlacklistPresets
    val eventShopCustom: StateFlow<String> = appSettingsManager.eventShopBlacklistCustom

    val reclamationConfig: StateFlow<ReclamationConfig> = appSettingsManager.reclamationConfig

    /** 电表倒转：进第几个「常乐」节点（1~4），持久化在设置里，重启不丢 */
    val changleIndex: StateFlow<Int> = appSettingsManager.meterRewindChangleIndex

    /** 电表倒转：一次启动连跑的轮数（5 衡 1 厉，奖励拿烛火/源石锭） */
    val meterRewindRounds: StateFlow<Int> = appSettingsManager.meterRewindRounds

    /** 电表倒转：换藏品次数（全投厉如锋，奖励选『要兵器一对』拿收藏品；0 = 不跑） */
    val meterRewindRelicRounds: StateFlow<Int> = appSettingsManager.meterRewindRelicRounds

    /**
     * 是否「界园·电表倒转」。
     *
     * 不用全等比较：列表里同一个小游戏的 value 可能来自热更活动数据（带 `@Begin` 等后缀、
     * 大小写差异，甚至被上游条目顶替），一旦对不上，配置项就不会显示、序号也不会下发。
     * 这里放宽成「含 MeterRewind」或「JieGarden 开头」，两种情况都能命中。
     */
    fun isMeterRewind(selectedTaskName: String): Boolean =
        selectedTaskName.contains("MeterRewind", ignoreCase = true) ||
            selectedTaskName.startsWith("JieGarden", ignoreCase = true)

    fun onChangleIndexChanged(index: Int) {
        scope.launch { appSettingsManager.setMeterRewindChangleIndex(index) }
    }

    fun onMeterRewindRoundsChanged(rounds: Int) {
        scope.launch { appSettingsManager.setMeterRewindRounds(rounds) }
    }

    fun onMeterRewindRelicRoundsChanged(rounds: Int) {
        scope.launch { appSettingsManager.setMeterRewindRelicRounds(rounds) }
    }

    fun isSecretFront(selectedTaskName: String): Boolean =
        selectedTaskName == SECRET_FRONT_VALUE

    fun isPixelPaint(selectedTaskName: String): Boolean =
        selectedTaskName in PIXEL_PAINT_VALUES

    fun isAutoRaisePotential(selectedTaskName: String): Boolean =
        selectedTaskName == AUTO_RAISE_POTENTIAL_VALUE

    fun isEventShop(selectedTaskName: String): Boolean =
        selectedTaskName == EVENT_SHOP_VALUE

    fun isReclamation(selectedTaskName: String): Boolean =
        selectedTaskName == RECLAMATION_VALUE

    fun onTaskSelected(value: String) {
        _state.update { it.copy(selectedTaskName = value) }
        logCurrentSelection("onTaskSelected")
    }

    fun onEndingSelected(ending: String) {
        _state.update { it.copy(selectedEnding = ending) }
    }

    fun onEventSelected(event: String) {
        _state.update { it.copy(selectedEvent = event) }
    }

    fun onUseNormalTokenChanged(useNormalToken: Boolean) {
        _state.update { it.copy(useNormalToken = useNormalToken) }
    }

    fun onEventShopPresetChanged(preset: EventShopPreset, checked: Boolean) {
        scope.launch { appSettingsManager.setEventShopBlacklistPreset(preset, checked) }
    }

    fun onEventShopCustomChanged(custom: String) {
        scope.launch { appSettingsManager.setEventShopBlacklistCustom(custom) }
    }

    fun onReclamationConfigChanged(transform: (ReclamationConfig) -> ReclamationConfig) {
        scope.launch { appSettingsManager.updateReclamationConfig(transform) }
    }

    fun findGame(selectedTaskName: String): MiniGame? =
        miniGames.value.find { it.value == selectedTaskName }

    private fun buildTaskName(): String {
        val snapshot = _state.value
        if (isPixelPaint(snapshot.selectedTaskName)) {
            return PIXEL_PAINT_TASK
        }
        if (snapshot.selectedTaskName == SECRET_FRONT_VALUE) {
            val base = "${snapshot.selectedTaskName}@Begin@Ending${snapshot.selectedEnding}"
            return if (snapshot.selectedEvent.isNotBlank()) "$base@${snapshot.selectedEvent}" else base
        }
        return snapshot.selectedTaskName
    }

    internal fun buildTaskParams(): MaaTaskParams {
        if (isReclamation(_state.value.selectedTaskName)) {
            return reclamationConfig.value.toTaskParams(clientType())
        }
        val taskName = buildTaskName()
        val params = buildJsonObject {
            putJsonArray("task_names") { add(JsonPrimitive(taskName)) }
            buildExtraParams()?.let { put("params", it) }
        }.toString()
        return MaaTaskParams(MaaTaskType.CUSTOM, params)
    }

    /**
     * 一次启动要下发的任务列表。
     *
     * 电表倒转每投完一次都会回到残识地图，要重新进一次「常乐」节点，所以这里按轮数
     * 下发等量的独立任务。**「投衡还是投厉」「奖励拿烛火还是拿藏品」都由这里决定**，
     * 不能交给资源层的 maxTimes：每个任务只投一次钱，`maxTimes` 的计数在每轮开始时天然归零，
     * 永远到不了 5（v19 的 5 次衡之后继续投衡就是这个原因）。资源层因此提供了三条独立入口链：
     * `@HEnter<n>`（投衡如常 → 收下源石锭）、`@LEnter<n>`（投厉如锋 → 要酒一壶 6 烛火）、
     * `@REnter<n>`（投厉如锋 → 要兵器一对 6 件藏品），本函数按轮次挑选。
     *
     * 两种玩法可以同时排：先跑完「刷钱」的 N 轮，再跑「换藏品」的 M 轮，共用一次启动。
     */
    private fun buildTaskParamsList(): List<MaaTaskParams> {
        val snapshot = _state.value
        if (isMeterRewind(snapshot.selectedTaskName)) {
            val index = changleIndex.value.coerceIn(1, 4)
            val moneyRounds = meterRewindRounds.value.coerceIn(1, 999)
            val relicRounds = meterRewindRelicRounds.value.coerceIn(0, 999)
            // 刷钱：5 衡 1 厉（第 6 次投厉如锋 → 要酒一壶 6 烛火）
            // 换藏品：5 次拿藏品 + 1 次拿烛火（第 6 次走 L 链）。投厉要消耗烛火，只出不进会把自己耗干；
            // 炸火时多花一次投厉才拿到同一份奖励，藏品实际到手的次数自然少一次，无需额外计数。
            val variants = buildList {
                repeat(moneyRounds) { i ->
                    add(if (meterRewindIsLiRound(i + 1)) VARIANT_LI else VARIANT_HENG)
                }
                repeat(relicRounds) { i ->
                    add(if (meterRewindIsLiRound(i + 1)) VARIANT_LI else VARIANT_RELIC)
                }
            }
            return variants.map { variant ->
                val params = buildJsonObject {
                    putJsonArray("task_names") {
                        add(JsonPrimitive("$METER_REWIND_ENTER_PREFIX$variant" + "Enter$index"))
                    }
                }.toString()
                MaaTaskParams(MaaTaskType.CUSTOM, params)
            }
        }
        return listOf(buildTaskParams())
    }

    /** 各玩法的附加参数，同一时间只有一种；null 表示整个 params 不下发 */
    private fun buildExtraParams(): JsonObject? {
        val snapshot = _state.value
        val selected = snapshot.selectedTaskName
        return when {
            // 黑名单为空时 Core 走原流程全买
            isEventShop(selected) ->
                EventShopBlacklist.keywords(eventShopPresets.value, eventShopCustom.value, clientType())
                    .takeIf { it.isNotEmpty() }
                    ?.let { blacklist ->
                        buildJsonObject {
                            putJsonObject("event_shop") {
                                putJsonArray("blacklist") { blacklist.forEach { add(JsonPrimitive(it)) } }
                            }
                        }
                    }

            // 不勾选时 Core 默认点 × 放弃本次提升
            isAutoRaisePotential(selected) && snapshot.useNormalToken -> buildJsonObject {
                putJsonObject("auto_raise_potential") {
                    put("use_normal_token", true)
                }
            }

            // 由 Core 的 PixelPaintTaskPlugin 消费
            isPixelPaint(selected) -> {
                val pixelArtState = pixelArt.state.value
                pixelArtState.plan?.let { plan ->
                    buildJsonObject {
                        putJsonObject("pixel_paint") {
                            put("swipe", pixelArtState.swipeEnabled)
                            put("grid_delay", pixelArtState.gridDelayMs)
                            putJsonArray("groups") {
                                plan.groups.forEach { group ->
                                    addJsonObject {
                                        put("color", group.color)
                                        putJsonArray("points") {
                                            group.points.forEach { point ->
                                                addJsonArray {
                                                    add(point[0])
                                                    add(point[1])
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> null
        }
    }

    fun onStart() {
        val selectedTaskName = _state.value.selectedTaskName
        val pixelPaint = isPixelPaint(selectedTaskName)
        if (pixelPaint) {
            val plan = pixelArt.state.value.plan
            if (plan == null) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_need_image)) }
                return
            }
            if (plan.groups.isEmpty()) {
                _state.update { it.copy(statusMessage = uiTextOf(R.string.pixel_art_nothing_to_paint)) }
                return
            }
        }
        if (findGame(selectedTaskName)?.isUnsupported == true) {
            _state.update {
                it.copy(statusMessage = uiTextOf(R.string.panel_mini_game_not_supported))
            }
            return
        }

        scope.launch {
            val tasks = buildTaskParamsList()
            // 下发的节点名/轮数写进运行日志：入队失败时能一眼看出是索引越界还是资源缺节点
            if (isMeterRewind(selectedTaskName)) {
                val index = changleIndex.value.coerceIn(1, 4)
                val money = meterRewindRounds.value.coerceIn(1, 999)
                val relic = meterRewindRelicRounds.value.coerceIn(0, 999)
                val liRounds = (1..money).count { meterRewindIsLiRound(it) }
                // 换藏品轮次里第 6 的倍数走 L 链（拿烛火回血），其余走 R 链（拿藏品）
                val relicCandle = (1..relic).count { meterRewindIsLiRound(it) }
                sessionLogger.append(
                    appContext.getString(
                        R.string.meter_rewind_status_started,
                        index,
                        money,
                        money - liRounds,
                        liRounds,
                        relic - relicCandle,
                        relicCandle,
                        tasks.size,
                    ),
                    LogLevel.INFO,
                )
            }
            _state.update { it.copy(statusMessage = uiTextOf(R.string.toolbox_status_starting)) }
            val result = compositionService.startCopilot(tasks)
            if (result is MaaCompositionService.StartResult.Success) {
                achievementRepository.report {
                    event = AchievementEvents.MINI_GAME_STARTED
                    "task" to selectedTaskName
                }
                // 面板可能被收起，格数/色数只有客户端知道，写进运行日志留痕
                if (pixelPaint) {
                    pixelArt.state.value.plan?.let { plan ->
                        sessionLogger.append(
                            appContext.getString(
                                R.string.pixel_art_status_started,
                                plan.paintedCellCount,
                                plan.groups.size,
                            ),
                            LogLevel.INFO,
                        )
                    }
                }
            }
            _state.update {
                it.copy(
                    statusMessage = appContext.formatStartResult(
                        result,
                        uiTextOf(R.string.panel_mini_game_started),
                    ),
                )
            }
        }
    }

    fun onStop() {
        scope.launch {
            _state.update { it.copy(statusMessage = uiTextOf(R.string.toolbox_status_stopping)) }
            compositionService.stop()
            _state.update { it.copy(statusMessage = uiTextOf(R.string.toolbox_status_stopped)) }
        }
    }

    private fun logCurrentSelection(source: String) {
        val selectedTaskName = _state.value.selectedTaskName
        val game = miniGames.value.find { it.value == selectedTaskName }
        Timber.d(
            "MiniGame[%s]: selectedTaskName=%s, matchedDisplay=%s, matchedValue=%s, tipKey=%s, tip=%s, listSize=%d",
            source,
            selectedTaskName,
            game?.display?.resolve(appContext),
            game?.value,
            game?.tipKey,
            game?.tip?.resolve(appContext)?.replace("\n", "\\n"),
            miniGames.value.size
        )
    }

    companion object {
        val ENDINGS = listOf("A", "B", "C", "D", "E")

        // value 是拼入任务名的流水线片段，必须保持简中；display 走资源本地化
        val EVENTS: List<Pair<String, UiText>> = listOf(
            "" to uiTextOf(R.string.mini_game_sf_event_none),
            "支援作战平台" to uiTextOf(R.string.mini_game_sf_event_support_platform),
            "游侠" to uiTextOf(R.string.mini_game_sf_event_knight_errant),
            "诡影迷踪" to uiTextOf(R.string.mini_game_sf_event_sly_shadows),
        )
    }
}
