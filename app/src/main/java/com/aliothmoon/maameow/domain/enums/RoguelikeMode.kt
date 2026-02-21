package com.aliothmoon.maameow.domain.enums

/**
 * 肉鸽策略模式 - 对齐 WPF RoguelikeMode 枚举
 */
enum class RoguelikeMode(val value: Int) {
    /** 刷等级，尽可能稳定地打更多层数 */
    Exp(0),

    /** 刷源石锭，投资完成后自动退出 */
    Investment(1),

    /** 刷开局，刷取热水壶或精二干员开局 */
    Collectible(4),

    /** 刷坍缩范式 */
    CLP_PDS(5),

    /** 月度小队 */
    Squad(6),

    /** 深入调查 */
    Exploration(7),

    /** 刷常乐节点 */
    FindPlaytime(20001);
}
