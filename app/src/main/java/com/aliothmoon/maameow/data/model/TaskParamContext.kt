package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.ResourceDataManager

/**
 * 任务参数组装所需的外部输入。
 *
 * 收进单一对象而非逐个加形参：新增输入时只需改本类与真正用到它的配置类，
 * 也避免了「无参重载偷偷填默认值」这种与实际行为不符的写法。
 *
 * ## 为什么持有管理器本身，而不是把每个查询拆成值或 lambda
 *
 * 拆成值/lambda 看似能精确收窄能力，实际有两个更大的代价：
 * - **每加一个查询就多一个字段**，且必须同步改动所有构造点（含每个测试）。
 *   本类从 3 个字段涨到 6 个只用了两轮迭代，按值传递会涨得更快。
 * - **必然滋生「说谎的默认」**：给 `depotCount` 之类的字段配默认值，
 *   漏传时就会静默变成「仓库为空 → 满量刷」；不配默认值则每个测试都要写满全部参数。
 *   本类刻意不给任何字段默认值，正是为了让编译器兜住这类疏漏。
 *
 * ## 使用约束
 *
 * **只调只读查询**。`toTaskParams` 不是 suspend，因此全部 suspend 写入（如
 * [DepotRepository.replaceAll]、[ActivityManager.load]）已被编译器挡住；
 * 但少数非 suspend 的状态变更方法（`DepotRepository.start`、`ActivityManager.addUnOpenStage`
 * / `startPeriodicCheck`、`ItemHelper.load`）仍可达 —— **不要调用它们**。
 * 任务参数组装必须是无副作用的纯计算，否则同一份配置反复展开会产生不同结果。
 *
 * 同理，配置类内部**不得**用 `GlobalContext` 之类的服务定位反向抓依赖：
 * 那会让 `toTaskParams` 的纯函数性质变成假的 —— 拿不到依赖时静默降级为错误行为，
 * 且单测因 Koin 未启动而实际测的是降级分支，生产路径反而从未被覆盖。
 * 所有外部输入一律走本类。
 *
 * 本类持有的是有状态的管理器，`equals` / `hashCode` 无实际意义，
 * 不要用于相等性比较或做 Map key。
 *
 * @param clientType 客户端类型（Official / Bilibili / YoStarEN / …）。
 *   填错会静默产生错误参数（旧代码硬编码 "Official" 正是此坑），由编译器强制显式提供。
 * @param chainAllowsCreditFight 整条任务链是否允许信用作战借助战打 OF-1。
 *   仅表示「链级前提成立」，是否真的下发还要与各 MallConfig 自身的开关取与。
 * @param activityManager 关卡开放判定、活动开放状态、活动临期理智药天数。
 * @param depotRepository 仓库材料存量，供库存保持计算缺口。
 * @param itemHelper 材料 ID → 名称，仅用于日志展示。
 * @param resourceDataManager 干员名归一化：MaaCore 的 core_char 仅认简中名
 *   （BattleDataConfig::find_oper 只匹配 name 字段，繁中/英文名会使 get_role 返回 Unknown，
 *   导致开局干员选择失败），故下发前须把本地化名反查回简中名。
 *   对齐 WPF RoguelikeSettingsUserControlModel.cs:1073。
 */
data class TaskParamContext(
    val clientType: String,
    val chainAllowsCreditFight: Boolean,
    val activityManager: ActivityManager,
    val depotRepository: DepotRepository,
    val itemHelper: ItemHelper,
    val resourceDataManager: ResourceDataManager,
)
