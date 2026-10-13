package com.aliothmoon.maameow.data.resource

import com.aliothmoon.maameow.data.config.MaaPathConfig

/**
 * 干员头像加载器，对应 WPF OperAvatarHelper.GetOperAvatar
 *
 * template/avatar/{operId}.png，120×120 ARGB 约 58KB 一张
 */
class OperAvatarLoader(
    pathConfig: MaaPathConfig
) : TemplateImageLoader(pathConfig, "template/avatar", cacheSize = 256) {
    override fun fallbackOf(id: String): String? = PromotedOperIds.baseOf(id)
}
