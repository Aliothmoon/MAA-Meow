package com.aliothmoon.maameow.presentation.components

import java.util.Random

/**
 * 帕拉斯彩蛋样式：界面卡片与导出图共用一份素材。
 *
 * 上游在干员识别里给帕拉斯做了三个特例（MAA 图标当头像、六个随机表情代星级、稀有成就渐变底色），
 * 界面用 Compose、导出用 Canvas，各写一遍容易走偏，所以集中到这里。
 */

/** 对齐上游 LocalizationHelper.GetPallasString(6, 6) */
internal val PALLAS_CHEERS = listOf("💃", "🕺", "🍷", "🍸", "🍺", "🍻", "🥃", "🍶")

/** 六个随机表情拼成的星级替身 */
internal fun pallasCheerText(random: Random = Random()): String =
    List(6) { PALLAS_CHEERS[random.nextInt(PALLAS_CHEERS.size)] }.joinToString("")

/** 上游 AchievementBrush.Rare 的浅色渐变（ARGB），导出图固定用这一套 */
internal val RARE_SUMMON_GRADIENT_LIGHT = listOf(
    0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFFFA726.toInt(), 0xFFAB47BC.toInt(),
    0xFFEF5350.toInt(), 0xFF37474F.toInt(), 0xFFFFEB3B.toInt(), 0xFFEC407A.toInt(),
)

/** 深色主题那一套，界面在暗色背景下用它 */
internal val RARE_SUMMON_GRADIENT_DARK = listOf(
    0xFF90CAF9.toInt(), 0xFFA5D6A7.toInt(), 0xFFFFCC80.toInt(), 0xFFCE93D8.toInt(),
    0xFFEF9A9A.toInt(), 0xFF90A4AE.toInt(), 0xFFFFF176.toInt(), 0xFFF48FB1.toInt(),
)
