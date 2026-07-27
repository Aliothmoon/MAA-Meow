package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.data.model.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 自动公招配置
 *
 * 完整迁移自 WPF RecruitSettingsUserControlModel.cs
 * 包含所有常规和高级设置配置项
 */
@Serializable
data class RecruitConfig(
    // ============ 常规设置 ============

    /**
     * 最大招募次数
     */
    val maxRecruitTimes: Int = 4,


    /**
     * 自动使用加急许可
     * 对应 WPF: UseExpeditedWithNull
     * - true: 总是使用
     * - false: 不使用
     */
    val useExpedited: Boolean = false,

    // ============ 高级设置 - 选择策略 ============

    /**
     * 自动公招选择额外Tag策略
     * 对应 WPF: SelectExtraTags
     * - "0": 不选择额外Tag
     * - "1": 选择额外Tag
     * - "2": 仅选择稀有Tag
     */
    val selectExtraTags: String = "0",

    /**
     * 高优先级Tag列表
     * 对应 WPF: AutoRecruitFirstList (Level3FirstList in JSON)
     * 可选Tag: ["近战位", "远程位", "先锋干员", "近卫干员", "狙击干员",
     *          "重装干员", "医疗干员", "辅助干员", "术师干员", "治疗",
     *          "费用回复", "输出", "生存", "群攻", "防护", "减速"]
     */
    val autoRecruitFirstList: List<String> = emptyList(),

    // ============ 高级设置 - 刷新策略 ============

    /**
     * 刷新三星Tags
     * 对应 WPF: RefreshLevel3
     * true: 当出现非资深/高资三星Tag时自动刷新
     */
    val refreshLevel3: Boolean = true,

    /**
     * 强制刷新
     * 对应 WPF: ForceRefresh
     * true: 招募券用完后强制刷新
     * 依赖: refreshLevel3 必须为 true
     */
    val forceRefresh: Boolean = true,

    // ============ 高级设置 - 稀有度选择 ============

    /**
     * 启用保留词条功能
     * 对应 WPF: PreserveTagEnabled (#16586)
     * 启用后输出 preserve_tags 词条列表，否则输出空数组（对齐 WPF AsstRecruitTask.Serialize）
     * 默认关闭（对齐 WPF 全新用户）；preserveTagList 已预填"支援机械"，勾选即生效
     *
     * 注: 取代旧的 notChooseLevel1（已于对齐 WPF v6.11 时移除，不做老配置迁移，
     *     ignoreUnknownKeys 会静默忽略旧字段，升级后默认关闭）
     */
    val preserveTagEnabled: Boolean = false,

    /**
     * 保留词条列表（公招时不选择列表中的Tag）
     * 对应 WPF: PreserveTagList (#16586)
     * 默认保留"支援机械"，避免影响高星Tag组合识别
     */
    val preserveTagList: List<String> = listOf("支援机械"),

    /**
     * 自动选择三星
     * 对应 WPF: ChooseLevel3
     */
    val chooseLevel3: Boolean = true,

    /**
     * 三星招募时长 - 小时部分
     * 对应 WPF: ChooseLevel3Hour
     * 范围: 1-9
     * 默认: 9小时（540分钟 = 9 * 60）
     */
    val chooseLevel3Hour: Int = 9,

    /**
     * 三星招募时长 - 分钟部分
     * 对应 WPF: ChooseLevel3Min
     * 范围: 0-50，步长10
     * 默认: 0分钟
     */
    val chooseLevel3Min: Int = 0,

    /**
     * 自动选择四星
     * 对应 WPF: ChooseLevel4
     */
    val chooseLevel4: Boolean = true,

    /**
     * 四星招募时长 - 小时部分
     * 对应 WPF: ChooseLevel4Hour
     * 范围: 1-9
     * 默认: 9小时（540分钟 = 9 * 60）
     */
    val chooseLevel4Hour: Int = 9,

    /**
     * 四星招募时长 - 分钟部分
     * 对应 WPF: ChooseLevel4Min
     * 范围: 0-50，步长10
     * 默认: 0分钟
     */
    val chooseLevel4Min: Int = 0,

    /**
     * 自动选择五星
     * 对应 WPF: ChooseLevel5
     * 默认: false（与 WPF 保持一致）
     */
    val chooseLevel5: Boolean = false,

    /**
     * 五星招募时长 - 小时部分
     * 对应 WPF: ChooseLevel5Hour
     * 范围: 1-9
     * 默认: 9小时（540分钟 = 9 * 60）
     */
    val chooseLevel5Hour: Int = 9,

    /**
     * 五星招募时长 - 分钟部分
     * 对应 WPF: ChooseLevel5Min
     * 范围: 0-50，步长10
     * 默认: 0分钟
     */
    val chooseLevel5Min: Int = 0,

    /**
     * 自动选择六星（保留但禁用）
     * 对应 WPF: ChooseLevel6（IsEnabled=False）
     * 注意: 此选项在WPF中被禁用，仅作保留
     */
    val chooseLevel6: Boolean = false
) : TaskParamProvider {
    /**
     * 计算三星总时长（分钟）
     * 对应 WPF: ChooseLevel3Time
     */
    fun getChooseLevel3TotalMinutes(): Int = chooseLevel3Hour * 60 + chooseLevel3Min

    /**
     * 计算四星总时长（分钟）
     * 对应 WPF: ChooseLevel4Time
     */
    fun getChooseLevel4TotalMinutes(): Int = chooseLevel4Hour * 60 + chooseLevel4Min

    /**
     * 计算五星总时长（分钟）
     * 对应 WPF: ChooseLevel5Time
     */
    fun getChooseLevel5TotalMinutes(): Int = chooseLevel5Hour * 60 + chooseLevel5Min

    /**
     * 验证时间范围（对应 WPF 的 ChooseLevelXTime setter 验证逻辑）
     * 范围: 60-540 分钟
     * 返回验证后的时间（如果超出范围则修正）
     */
    private fun validateTime(totalMinutes: Int): Int = when {
        totalMinutes < 60 -> 540   // 小于 1 小时 → 9 小时
        totalMinutes > 540 -> 60   // 大于 9 小时 → 1 小时
        else -> (totalMinutes / 10) * 10  // 向下取整到 10 的倍数
    }

    /**
     * 从总时长（分钟）创建新的 RecruitConfig（三星）
     * 对应 WPF: ChooseLevel3Time setter
     */
    fun withChooseLevel3Time(totalMinutes: Int): RecruitConfig {
        val validated = validateTime(totalMinutes)
        return copy(
            chooseLevel3Hour = validated / 60,
            chooseLevel3Min = validated % 60
        )
    }

    /**
     * 从总时长（分钟）创建新的 RecruitConfig（四星）
     * 对应 WPF: ChooseLevel4Time setter
     */
    fun withChooseLevel4Time(totalMinutes: Int): RecruitConfig {
        val validated = validateTime(totalMinutes)
        return copy(
            chooseLevel4Hour = validated / 60,
            chooseLevel4Min = validated % 60
        )
    }

    /**
     * 从总时长（分钟）创建新的 RecruitConfig（五星）
     * 对应 WPF: ChooseLevel5Time setter
     */
    fun withChooseLevel5Time(totalMinutes: Int): RecruitConfig {
        val validated = validateTime(totalMinutes)
        return copy(
            chooseLevel5Hour = validated / 60,
            chooseLevel5Min = validated % 60
        )
    }

    override fun toTaskParams(ctx: TaskParamContext): TaskParamResult {
        // 构建 select 和 confirm 列表
        val selectList = buildList {
            if (chooseLevel3) add(3)
            if (chooseLevel4) add(4)
            if (chooseLevel5) add(5)
        }
        val confirmList = selectList // 选择即确认

        val paramsJson = buildJsonObject {
            put("refresh", refreshLevel3)
            put("force_refresh", forceRefresh)
            put("select", buildJsonArray {
                selectList.forEach { add(JsonPrimitive(it)) }
            })
            put("confirm", buildJsonArray {
                confirmList.forEach { add(JsonPrimitive(it)) }
            })
            put("times", maxRecruitTimes)
            put("set_time", true)
            put("expedite", useExpedited)
            if (useExpedited) {
                put("expedite_times", maxRecruitTimes)
            }
            // 对齐 WPF AsstRecruitTask.Serialize：总是输出 preserve_tags
            // 启用时输出保留词条列表，否则输出空数组
            put("preserve_tags", buildJsonArray {
                if (preserveTagEnabled) {
                    preserveTagList.forEach { add(JsonPrimitive(it)) }
                }
            })
            put("extra_tags_mode", selectExtraTags.toIntOrNull() ?: 0)
            if (autoRecruitFirstList.isNotEmpty()) {
                put("first_tags", buildJsonArray {
                    autoRecruitFirstList.forEach {
                        add(JsonPrimitive(it))
                    }
                })
            }
            put("recruitment_time", buildJsonObject {
                put("3", getChooseLevel3TotalMinutes())
                put("4", getChooseLevel4TotalMinutes())
                // 对齐上游 v6.13.0-beta.1：5 星时间锁死 9:00，不再读 chooseLevel5Hour/Min
                put("5", 540)
            })
        }

        return TaskParamResult(listOf(MaaTaskParams(MaaTaskType.RECRUIT, paramsJson.toString())))
    }
}