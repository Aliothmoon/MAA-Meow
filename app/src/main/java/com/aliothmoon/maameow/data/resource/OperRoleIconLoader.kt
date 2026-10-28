package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig

/**
 * 职业图标，复用 core 干员识别的职业旗标模板，对应 WPF OperAvatarHelper.GetRoleIcon
 *
 * template/OperBox/OperBoxFlagRole{n}.png，30×30
 */
class OperRoleIconLoader(
    pathConfig: MaaPathConfig
) : TemplateImageLoader(pathConfig, "template/OperBox", cacheSize = 16) {

    companion object {
        /** 顺序与 core OperBoxImageAnalyzer 的职业旗标一致 */
        private val TEMPLATE_INDEX = mapOf(
            "CASTER" to 1,
            "MEDIC" to 2,
            "PIONEER" to 3,
            "SNIPER" to 4,
            "SPECIAL" to 5,
            "SUPPORT" to 6,
            "TANK" to 7,
            "WARRIOR" to 8,
        )

        /** [profession] 取自 battle_data，认不出的职业返回 null */
        fun templateId(profession: String?): String? =
            TEMPLATE_INDEX[profession?.uppercase()]?.let { "OperBoxFlagRole$it" }
    }
}
