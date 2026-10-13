package com.aliothmoon.maameow.presentation.view.panel.operbox

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator
import com.aliothmoon.maameow.presentation.components.AppIconAvatar
import com.aliothmoon.maameow.presentation.components.MasteryBadge
import com.aliothmoon.maameow.presentation.components.OperAvatar
import com.aliothmoon.maameow.presentation.components.OperRoleIcon
import com.aliothmoon.maameow.presentation.components.operRarityColor
import com.aliothmoon.maameow.theme.OpaqueTheme

/** 卡片上叠加信息的底色与形状，对齐上游 #AA1F1F1F */
private val BadgeBackground = Color(0xAA1F1F1F)
private val BadgeShape = RoundedCornerShape(3.dp)
private val NotOwnedBackground = Color(0x99808080)
private val CardShape = RoundedCornerShape(6.dp)

/** 头像卡，对齐上游干员识别的头像模式（#18352） */
@Composable
internal fun OperBoxCard(
    oper: OperBoxOperator,
    profession: String?,
    /** null 时不可点，悬浮窗里弹不了 Dialog */
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val detailLabel = stringResource(R.string.panel_operbox_card_detail)
    Column(
        modifier = modifier
            .clip(CardShape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = detailLabel, onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CardShape)
                .background(cardBackground(oper)),
        ) {
            OperCardAvatar(oper, Modifier.align(Alignment.Center).fillMaxSize(0.8f))
            OperRoleIcon(
                profession = profession,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .background(BadgeBackground, BadgeShape)
                    .padding(1.dp)
                    .size(12.dp),
            )
            RarityStars(oper, Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
            if (oper.own) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    BadgeIcon(potentialIcon(oper.potential), stringResource(R.string.panel_operbox_potential_icon, oper.potential))
                    eliteIcon(oper.elite)?.let {
                        BadgeIcon(it, stringResource(R.string.panel_operbox_elite_icon, oper.elite))
                    }
                    BadgeText("Lv.${oper.level}")
                }
                TrainingBadges(oper, Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
        }
        Text(
            text = oper.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (oper.own) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 点卡片弹出的详情，补全卡片放不下的名字与练度 */
@Composable
internal fun OperBoxDetailDialog(
    oper: OperBoxOperator,
    profession: String?,
    onDismiss: () -> Unit,
) {
    // Dialog 是独立窗口，不透出主界面自定义背景图
    OpaqueTheme {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(CardShape).background(cardBackground(oper))) {
                            OperCardAvatar(oper, Modifier.align(Alignment.Center).fillMaxSize(0.85f))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(oper.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (oper.isPallas && oper.own) remember(oper.id) { pallasCheers() } else "★".repeat(oper.rarity),
                                style = MaterialTheme.typography.bodySmall,
                                color = operRarityColor(oper.rarity),
                            )
                            roleNameRes(profession)?.let { roleRes ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OperRoleIcon(
                                        profession,
                                        Modifier.background(BadgeBackground, BadgeShape).padding(1.dp).size(14.dp),
                                    )
                                    Text(
                                        stringResource(roleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow(
                        R.string.operbox_export_header_own,
                        stringResource(if (oper.own) R.string.operbox_export_yes else R.string.operbox_export_no),
                    )
                    if (oper.own) {
                        DetailRow(R.string.operbox_export_header_elite, oper.elite.toString()) {
                            eliteIcon(oper.elite)?.let { BadgeIcon(it, null) }
                        }
                        DetailRow(R.string.operbox_export_header_level, oper.level.toString())
                        DetailRow(R.string.operbox_export_header_potential, oper.potential.toString()) {
                            BadgeIcon(potentialIcon(oper.potential), null)
                        }
                        oper.mainSkillLevel?.let {
                            DetailRow(R.string.operbox_export_header_main_skill_level, it.toString())
                        }
                        if (oper.skills.isNotEmpty()) {
                            DetailRow(R.string.operbox_export_header_skills, oper.skills.joinToString(" / ") { it.level.toString() }) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    oper.skills.forEach { MasteryBadge(it.level) }
                                }
                            }
                        }
                        val mods = modBadges(oper)
                        if (mods.isNotEmpty()) DetailRow(R.string.operbox_export_header_equips, mods.joinToString(" "))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(@StringRes label: Int, value: String, leading: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 帕拉斯换成应用图标，对齐上游显示 MAA 图标 */
@Composable
private fun OperCardAvatar(oper: OperBoxOperator, modifier: Modifier) {
    if (oper.isPallas) {
        AppIconAvatar(modifier, desaturated = !oper.own)
    } else {
        OperAvatar(oper.id, modifier, desaturated = !oper.own)
    }
}

@Composable
private fun RarityStars(oper: OperBoxOperator, modifier: Modifier) {
    val pallas = oper.isPallas && oper.own
    Text(
        text = if (pallas) remember(oper.id) { pallasCheers() } else "★".repeat(oper.rarity),
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = Color.White,
            // 6 个 emoji 比星字宽，缩一号免得撑出卡片
            fontSize = if (pallas) 7.sp else 8.sp,
            shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1f), 3f),
        ),
    )
}

/** 右下：模组竖排，下接 RANK 或专精，只有一图流数据有 */
@Composable
private fun TrainingBadges(oper: OperBoxOperator, modifier: Modifier) {
    val mainSkillLevel = oper.mainSkillLevel ?: return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        modBadges(oper).forEach { BadgeText(it) }
        if (oper.skills.none { it.level >= 1 }) {
            BadgeText("RANK $mainSkillLevel")
        } else {
            Row(
                modifier = Modifier.background(BadgeBackground, BadgeShape).padding(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                oper.skills.forEach { MasteryBadge(it.level, Modifier.size(9.dp)) }
            }
        }
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(
        text = text,
        modifier = Modifier.background(BadgeBackground, BadgeShape).padding(horizontal = 2.dp),
        style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

@Composable
private fun BadgeIcon(@DrawableRes res: Int, contentDescription: String?) {
    Image(
        painter = painterResource(res),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier.background(BadgeBackground, BadgeShape).padding(1.dp).size(11.dp),
    )
}

@Composable
private fun cardBackground(oper: OperBoxOperator): Brush = when {
    !oper.own -> SolidColor(NotOwnedBackground)
    oper.isPallas -> rareAchievementBrush()
    else -> SolidColor(operRarityColor(oper.rarity).copy(alpha = 0.45f))
}

/** 模组分支到显示名，对齐上游 ToolboxViewModel.ModTypeDisplay；0 级是未开启，不显示 */
private fun modBadges(oper: OperBoxOperator): List<String> =
    oper.equips.filter { it.level > 0 }.map { "${MOD_TYPE_DISPLAY[it.type] ?: it.type}${it.level}" }

private val MOD_TYPE_DISPLAY = mapOf("A" to "α", "B" to "β", "X" to "χ", "Y" to "γ", "D" to "Δ")

@DrawableRes
private fun eliteIcon(elite: Int): Int? = when (elite) {
    0 -> R.drawable.ic_oper_elite_0
    1 -> R.drawable.ic_oper_elite_1
    2 -> R.drawable.ic_oper_elite_2
    else -> null
}

/** 超出范围按 1 潜显示，同上游 PotentialIconPath */
@DrawableRes
private fun potentialIcon(potential: Int): Int = when (potential) {
    2 -> R.drawable.ic_oper_potential_2
    3 -> R.drawable.ic_oper_potential_3
    4 -> R.drawable.ic_oper_potential_4
    5 -> R.drawable.ic_oper_potential_5
    6 -> R.drawable.ic_oper_potential_6
    else -> R.drawable.ic_oper_potential_1
}

@StringRes
private fun roleNameRes(profession: String?): Int? = when (profession?.uppercase()) {
    "CASTER" -> R.string.oper_role_caster
    "MEDIC" -> R.string.oper_role_medic
    "PIONEER" -> R.string.oper_role_pioneer
    "SNIPER" -> R.string.oper_role_sniper
    "SPECIAL" -> R.string.oper_role_special
    "SUPPORT" -> R.string.oper_role_support
    "TANK" -> R.string.oper_role_tank
    "WARRIOR" -> R.string.oper_role_warrior
    else -> null
}

/** 对齐上游 LocalizationHelper.GetPallasString(6, 6) */
private val PALLAS_CHARS = listOf("💃", "🕺", "🍷", "🍸", "🍺", "🍻", "🥃", "🍶")

private fun pallasCheers(): String = List(6) { PALLAS_CHARS.random() }.joinToString("")

/** 上游 AchievementBrush.Rare.LinearGradientBrush，深浅主题各一套 */
@Composable
private fun rareAchievementBrush(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return Brush.linearGradient(if (dark) RARE_DARK else RARE_LIGHT)
}

private val RARE_LIGHT = listOf(
    Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFA726), Color(0xFFAB47BC),
    Color(0xFFEF5350), Color(0xFF37474F), Color(0xFFFFEB3B), Color(0xFFEC407A),
)

private val RARE_DARK = listOf(
    Color(0xFF90CAF9), Color(0xFFA5D6A7), Color(0xFFFFCC80), Color(0xFFCE93D8),
    Color(0xFFEF9A9A), Color(0xFF90A4AE), Color(0xFFFFF176), Color(0xFFF48FB1),
)
