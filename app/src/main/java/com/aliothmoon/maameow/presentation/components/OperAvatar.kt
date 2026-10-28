package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.resource.OperAvatarLoader
import com.aliothmoon.maameow.data.resource.OperRoleIconLoader
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import org.koin.compose.koinInject

// 未拥有干员的色彩保留比例，对齐 WPF OperAvatarHelper.DesaturatedColorKeep
private val DesaturatedFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f) })

/** 按干员名显示头像，别名与各语言名都认，对应 WPF OperAvatarHelper.GetOperAvatarByName */
@Composable
fun OperAvatarByName(
    name: String,
    modifier: Modifier = Modifier,
    resourceDataManager: ResourceDataManager = koinInject(),
) {
    // 干员表异步加载，换客户端或语言会重建，跟着重查
    val nameIndex by resourceDataManager.nameIndex.collectAsStateWithLifecycle()
    val operId = remember(name, nameIndex) {
        resourceDataManager.getCharacterByNameOrAlias(name)?.id.orEmpty()
    }
    OperAvatar(operId = operId, modifier = modifier)
}

@Composable
fun OperAvatar(
    operId: String,
    modifier: Modifier = Modifier,
    desaturated: Boolean = false,
    loader: OperAvatarLoader = koinInject(),
) {
    TemplateImage(
        id = operId,
        loader = loader,
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
        colorFilter = if (desaturated) DesaturatedFilter else null,
    )
}

/** 应用图标充当头像，对齐上游干员识别里帕拉斯显示 MAA 图标 */
@Composable
fun AppIconAvatar(modifier: Modifier = Modifier, desaturated: Boolean = false) {
    val context = LocalContext.current
    // 系统按启动器形状裁好的自适应图标
    val icon = remember(context) {
        context.packageManager.getApplicationIcon(context.applicationInfo)
            .toBitmap(APP_ICON_PX, APP_ICON_PX).asImageBitmap()
    }
    Image(
        bitmap = icon,
        contentDescription = null,
        modifier = modifier,
        colorFilter = if (desaturated) DesaturatedFilter else null,
    )
}

private const val APP_ICON_PX = 96

/** 职业图标，[profession] 取自 battle_data；认不出的职业留空位 */
@Composable
fun OperRoleIcon(
    profession: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loader: OperRoleIconLoader = koinInject(),
) {
    TemplateImage(
        id = OperRoleIconLoader.templateId(profession).orEmpty(),
        loader = loader,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}
