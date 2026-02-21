package com.aliothmoon.maameow.data.resource

/**
 * 默认小游戏列表
 */
object DefaultMiniGames {
    data class DefaultMiniGameEntry(
        val display: String,
        val value: String,
        val tipKey: String? = null
    )

    val ENTRIES = listOf(
        DefaultMiniGameEntry("赛季商店", "SS@Store@Begin", "MiniGameNameSsStoreTip"),
        DefaultMiniGameEntry("绿票商店", "GreenTicket@Store@Begin", "MiniGameNameGreenTicketStoreTip"),
        DefaultMiniGameEntry("黄票商店", "YellowTicket@Store@Begin", "MiniGameNameYellowTicketStoreTip"),
        DefaultMiniGameEntry("生息演算商店", "RA@Store@Begin", "MiniGameNameRAStoreTip"),
        DefaultMiniGameEntry("隐秘战线", "MiniGame@SecretFront", "MiniGame@SecretFrontTip")
    )
}