package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.data.model.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 获取信用及购物配置
 *
 * 完整迁移自 WPF MallSettingsUserControlModel.cs 和 AsstMallTask.cs
 * 包含访问好友、信用购物、借助战等全部配置项
 *
 * WPF 源文件:
 * - ViewModel: MallSettingsUserControlModel.cs
 * - Model: AsstMallTask.cs
 * - View: MallSettingsUserControl.xaml
 *
 * MaaCore JSON 参数映射:
 * - credit_fight: 借助战
 * - formation_index: 编队索引
 * - visit_friends: 访问好友
 * - shopping: 购物
 * - buy_first: 优先购买列表
 * - blacklist: 黑名单列表
 * - force_shopping_if_credit_full: 溢出时无视黑名单
 * - only_buy_discount: 只买打折物品
 * - reserve_max_credit: 预留300信用
 */
@Serializable
data class MallConfig(
    // ============ 访问好友 ============

    /**
     * 访问好友获取信用点
     * 对应 WPF: CreditVisitFriendsEnabled
     * MaaCore JSON: visit_friends
     */
    val visitFriends: Boolean = true,

    // ============ 借助战 ============

    /**
     * 借助战打 OF-1 获取额外信用点
     * 对应 WPF: CreditFightTaskEnabled
     * MaaCore JSON: credit_fight
     */
    val creditFight: Boolean = false,

    /**
     * 借助战编队选择
     * 对应 WPF: CreditFightSelectFormation
     * MaaCore JSON: formation_index
     *
     * 选项:
     * - 0: 当前编队
     * - 1-4: 编队1-4
     */
    val creditFightFormation: Int = 0,

    // ============ 信用购物 ============

    /**
     * 购买信用商店物品
     * 对应 WPF: CreditShopping
     * MaaCore JSON: shopping
     */
    val shopping: Boolean = true,

    /**
     * 优先购买列表
     * 对应 WPF: CreditFirstList
     * MaaCore JSON: buy_first
     *
     * 默认值参考 WPF LocalizationHelper.GetString("HighPriorityDefault")
     */
    val buyFirst: List<String> = listOf("招聘许可", "龙门币"),

    /**
     * 黑名单列表（不购买的物品）
     * 对应 WPF: CreditBlackList
     * MaaCore JSON: blacklist
     *
     * 默认值参see LocalizationHelper.GetString("BlacklistDefault")
     * 注意: 为与 WPF 对齐，序列化时会按客户端并入固定黑名单角色
     */
    val blacklist: List<String> = listOf("碳", "家具", "加急许可"),

    /**
     * 溢出时无视黑名单
     * 对应 WPF: CreditForceShoppingIfCreditFull
     * MaaCore JSON: force_shopping_if_credit_full
     *
     * 启用后，信用点即将溢出时会购买黑名单物品
     */
    val forceShoppingIfCreditFull: Boolean = false,

    /**
     * 只买打折物品
     * 对应 WPF: CreditOnlyBuyDiscount
     * MaaCore JSON: only_buy_discount
     *
     * 注意: 未打折的优先购买列表物品仍会购买
     */
    val onlyBuyDiscount: Boolean = false,

    /**
     * 预留300信用点
     * 对应 WPF: CreditReserveMaxCredit
     * MaaCore JSON: reserve_max_credit
     *
     * 启用后，信用点低于300时停止购买
     */
    val reserveMaxCredit: Boolean = false
) : TaskParamProvider {
    companion object {
        /**
         * 编队选项列表
         * 对应 WPF: FormationSelectList
         */
        val FORMATION_OPTIONS = listOf(
            0 to "当前",
            1 to "1",
            2 to "2",
            3 to "3",
            4 to "4"
        )

        private val FIXED_BLACKLIST_BY_CLIENT = mapOf(
            "Official" to listOf("讯使", "嘉维尔", "坚雷"),
            "Bilibili" to listOf("讯使", "嘉维尔", "坚雷"),
            "YoStarEN" to listOf("Courier", "Gavial", "Dur-nar"),
            "YoStarJP" to listOf("クーリエ", "ガヴィル", "ジュナー"),
            "YoStarKR" to listOf("쿠리어", "가비알", "듀나"),
            "txwy" to listOf("訊使", "嘉維爾", "堅雷")
        )
    }

    override fun toTaskParams(ctx: TaskParamContext): List<MaaTaskParams> {
        val mergedBlacklist = mergeFixedBlacklist(ctx.clientType)
        // 本任务自身开启，且整条链的前提也成立时才借助战
        val creditFightEnabled = creditFight && ctx.chainAllowsCreditFight
        val paramsJson = buildJsonObject {
            // 访问好友
            put("visit_friends", visitFriends)

            // 借助战
            put("credit_fight", creditFightEnabled)
            if (creditFightEnabled) {
                put("formation_index", creditFightFormation)
            }

            // 购物
            put("shopping", shopping)
            if (buyFirst.isNotEmpty()) {
                put("buy_first", JsonArray(buyFirst.map { JsonPrimitive(it) }))
            }
            if (mergedBlacklist.isNotEmpty()) {
                put("blacklist", JsonArray(mergedBlacklist.map { JsonPrimitive(it) }))
            }

            // 高级选项
            put("force_shopping_if_credit_full", forceShoppingIfCreditFull)
            put("only_buy_discount", onlyBuyDiscount)
            put("reserve_max_credit", reserveMaxCredit)
        }
        return listOf(MaaTaskParams(MaaTaskType.MALL, paramsJson.toString()))
    }

    private fun mergeFixedBlacklist(clientType: String): List<String> {
        val fixedBlacklist = FIXED_BLACKLIST_BY_CLIENT[clientType].orEmpty()
        return (blacklist + fixedBlacklist)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
