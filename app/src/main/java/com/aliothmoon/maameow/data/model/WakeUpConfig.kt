package com.aliothmoon.maameow.data.model
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import com.aliothmoon.maameow.data.model.TaskParamProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 开始唤醒配置
 *
 * MaaCore JSON 参数:
 * - client_type: 客户端类型字符串
 * - start_game_enabled: 是否启动游戏
 */
@Serializable
data class WakeUpConfig(
    /**
     * 客户端类型
     * 对应 WPF: ClientType
     * MaaCore JSON: client_type
     *
     * 选项：
     * - "Official": 官服
     * - "Bilibili": B服
     * - "YoStarEN": 国际服(YoStarEN)
     * - "YoStarJP": 日服(YoStarJP)
     * - "YoStarKR": 韩服(YoStarKR)
     * - "txwy": 繁中服(txwy)
     */
    val clientType: String = "Official",

    /**
     * 是否启用启动游戏
     * 对应 WPF: StartGame
     * MaaCore JSON: start_game_enabled
     */
    val startGameEnabled: Boolean = true,

    /**
     * 账号切换目标
     * 对应 WPF: AccountName
     * MaaCore JSON: account_name
     *
     * 仅 Official / Bilibili 生效，其他服将忽略该字段
     */
    val accountName: String = ""
) : TaskParamProvider {
    companion object {
        /**
         * 客户端类型选项值列表
         */
        val CLIENT_TYPES = listOf(
            "Official",
            "Bilibili",
            "YoStarEN",
            "YoStarJP",
            "YoStarKR",
            "txwy"
        )

        /**
         * 客户端类型到服务器类型的映射
         * 用于资源更新等逻辑
         */
        fun getServerType(clientType: String): String = when (clientType) {
            "Official", "Bilibili", "" -> "CN"
            "YoStarEN" -> "US"
            "YoStarJP" -> "JP"
            "YoStarKR" -> "KR"
            "txwy" -> "ZH_TW"
            else -> "CN"
        }
    }

    /**
     * 获取服务器类型
     */
    fun getServerType(): String = Companion.getServerType(clientType)
    override fun toTaskParams(): MaaTaskParams {
        val normalizedAccountName = accountName.trim()
        val canSwitchAccount = clientType == "Official" || clientType == "Bilibili"
        val paramsJson = buildJsonObject {
            put("client_type", clientType)
            put("start_game_enabled", startGameEnabled)
            if (canSwitchAccount && normalizedAccountName.isNotEmpty()) {
                put("account_name", normalizedAccountName)
            }
        }
        return MaaTaskParams(MaaTaskType.START_UP, paramsJson.toString())
    }
}
