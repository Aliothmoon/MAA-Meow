package com.aliothmoon.maameow.data.model


import com.aliothmoon.maameow.domain.enums.InfrastMode
import com.aliothmoon.maameow.domain.enums.InfrastRoomType
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.maa.task.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 基建换班配置
 *
 * 完整迁移自 WPF InfrastTask.cs 和 InfrastSettingsUserControlModel.cs
 * 支持常规模式（Normal）、自定义模式（Custom）和队列轮换模式（Rotation）
 *
 * WPF 源文件:
 * - Model: InfrastTask.cs
 * - ViewModel: InfrastSettingsUserControlModel.cs
 * - View: InfrastSettingsUserControl.xaml
 */
@Serializable
data class InfrastConfig(
    // ============ 基建模式 ============

    /**
     * 基建切换模式
     * 对应 WPF: InfrastMode (enum)
     * - "Normal": 常规模式
     * - "Custom": 自定义基建配置
     * - "Rotation": 队列轮换模式
     */
    val mode: InfrastMode = InfrastMode.Normal,

    // ============ 自定义基建配置（Custom 模式） ============

    /**
     * 当前选择的内置预设 key
     * 对应 WPF: DefaultInfrast (string)
     *
     * - "user_defined": 手动选择文件
     * - 其他值: 对应 custom_infrast 目录下的预设文件名（不含 .json）
     *
     * 选择预设时会自动设置 customInfrastFile
     */
    val defaultInfrast: String = "user_defined",

    /**
     * 自定义基建配置文件的绝对路径
     * 对应 WPF: CustomInfrastFile -> InfrastTask.Filename
     *
     * 预设模式: {resourceDir}/custom_infrast/{preset}.json
     * 手动选择: 用户通过文件选择器指定的路径
     */
    val customInfrastFile: String = "",

    /**
     * 自定义基建计划选择
     * 对应 WPF: CustomInfrastPlanSelect
     *
     * - -1: 根据时间段自动轮换（time rotation）
     * - 0+: 指定计划索引
     */
    val customInfrastPlanSelect: Int = -1,

    // ============ 设施列表（有序） ============

    /**
     * 基建设施优先级列表（有序，含启用状态）
     * 对应 WPF: InfrastItemViewModels (ObservableCollection<DragItemViewModel>)
     *
     * Pair<InfrastRoomType, Boolean>:
     * - first: 设施类型
     * - second: 是否启用
     *
     * 列表顺序代表换班优先级，支持拖拽调整
     */
    val facilities: List<Pair<InfrastRoomType, Boolean>> =
        InfrastRoomType.values.map { it to true },

    // ============ 无人机用途 ============

    /**
     * 无人机使用方式
     * 对应 WPF: UsesOfDrones (string)
     *
     * 选项:
     * - "_NotUse": 不使用无人机
     * - "Money": 龙门币（制造站）
     * - "SyntheticJade": 合成玉（制造站）
     * - "CombatRecord": 作战记录（制造站）
     * - "PureGold": 赤金（贸易站）
     * - "OriginStone": 源石碎片（贸易站）
     * - "Chip": 芯片（贸易站）
     */
    val usesOfDrones: String = "Money",

    // ============ 心情阈值 ============

    /**
     * 宿舍心情阈值（百分比）
     * 对应 WPF: DormThreshold (int, 0-100%)
     *
     * 干员心情低于此值时将被替换下班休息
     * 范围: 0-100（百分比值）
     *
     * 注意:
     * - WPF中DormThreshold是百分比值(0-100)
     * - 传递给MAA Core时需要除以100转换为0.0-1.0浮点数
     * - Rotation模式下此参数不显示和使用
     */
    val dormThreshold: Int = 30,

    // ============ 高级设置 ============

    /**
     * 宿舍空位补信赖
     * 对应 WPF: DormTrustEnabled (bool)
     *
     * 启用后，宿舍有空位时会优先安排信赖未满的干员进入宿舍
     * 注意: Rotation模式下不显示此选项
     */
    val dormTrustEnabled: Boolean = false,

    /**
     * 宿舍是否使用未进驻筛选标签
     * 对应 WPF: DormFilterNotStationedEnabled (bool)
     *
     * 启用后，已在其他设施工作的干员不会被安排进宿舍休息
     * 注意: Rotation模式下不显示此选项
     */
    val dormFilterNotStationedEnabled: Boolean = true,

    /**
     * 制造站搓玉自动补货
     * 对应 WPF: OriginiumShardAutoReplenishment (bool)
     *
     * 启用后，制造站合成玉生产线会自动补充原料（源石碎片）
     */
    val originiumShardAutoReplenishment: Boolean = true,

    /**
     * 会客室留言板领取信用
     * 对应 WPF: receptionMessageBoard (bool)
     *
     * 启用后，会在会客室领取留言板的信用点
     */
    val receptionMessageBoard: Boolean = true,

    /**
     * 会客室线索交流
     * 对应 WPF: ReceptionClueExchange (bool)
     *
     * 启用后，会自动进行线索交流
     */
    val receptionClueExchange: Boolean = true,

    /**
     * 会客室赠送线索
     * 对应 WPF: ReceptionSendClue (bool)
     *
     * 启用后，会自动向好友赠送线索
     */
    val receptionSendClue: Boolean = true,

    /**
     * 继续专精
     * 对应 WPF: ContinueTraining (bool)
     *
     * 启用后，技能专精完成后会继续进行下一个专精任务
     */
    val continueTraining: Boolean = false,

    /**
     * 自定义基建计划的时间段数据（不参与序列化）
     *
     * 由 UI 层解析配置文件后设置，用于 toTaskParams() 时间轮换解析
     * 结构: plans[planIndex] -> periods -> [startTime, endTime]
     */
    @Transient
    val customPlanPeriods: List<List<List<String>>> = emptyList()
) : TaskParamProvider {

    /**
     * 将心情阈值转换为MAA Core需要的浮点数格式(0.0-1.0)
     */
    fun getDormThresholdAsFloat(): Double = dormThreshold / 100.0
    override fun toTaskParams(): MaaTaskParams {
        val threshold = getDormThresholdAsFloat()
        val paramsJson = buildJsonObject {
            put("facility", buildJsonArray {
                facilities.filter { it.second }
                    .map { it.first.name }
                    .forEach {
                        add(JsonPrimitive(it))
                    }
            })
            put("drones", usesOfDrones)
            put("continue_training", continueTraining)
            put("threshold", threshold)
            put("dorm_notstationed_enabled", dormFilterNotStationedEnabled)
            put("dorm_trust_enabled", dormTrustEnabled)
            put("replenish", originiumShardAutoReplenishment)
            put("reception_message_board", receptionMessageBoard)
            put("reception_clue_exchange", receptionClueExchange)
            put("reception_send_clue", receptionSendClue)
            put("mode", mode.value)
            if(mode == InfrastMode.Custom){
                put("filename", customInfrastFile)
                put("plan_index", resolveCustomPlanIndex())
            }
        }

        return MaaTaskParams(MaaTaskType.INFRAST, paramsJson.toString())
    }

    /**
     * 解析自定义基建计划索引
     *
     * 对应 WPF: AsstInfrastTask.SerializeTask 中 plan_index 的解析逻辑
     * - customInfrastPlanSelect >= 0: 直接使用指定索引
     * - customInfrastPlanSelect == -1: 时间轮换，匹配当前时间所在的 period
     */
    private fun resolveCustomPlanIndex(): Int {
        if (customInfrastPlanSelect >= 0) return customInfrastPlanSelect
        if (customPlanPeriods.isEmpty()) return 0

        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("H:mm")

        for ((index, periods) in customPlanPeriods.withIndex()) {
            for (period in periods) {
                if (period.size < 2) continue
                val start = runCatching { LocalTime.parse(period[0], formatter) }.getOrNull() ?: continue
                val end = runCatching { LocalTime.parse(period[1], formatter) }.getOrNull() ?: continue
                if (start <= end) {
                    if (now in start..end) return index
                } else {
                    // 跨午夜时间段，如 21:00 - 04:59
                    if (now >= start || now <= end) return index
                }
            }
        }
        return 0
    }
}