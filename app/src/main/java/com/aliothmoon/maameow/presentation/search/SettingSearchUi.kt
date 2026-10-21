package com.aliothmoon.maameow.presentation.search

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.components.ITextField
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.LocalSettingRowBleed
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.view.panel.common.bringIntoViewOnExpand
import com.aliothmoon.maameow.theme.MaaDesignTokens
import org.koin.compose.koinInject

private val LocalSettingSearchRequest = compositionLocalOf<SettingSearchRequest?> { null }
private val LocalSettingSearchNavigator = staticCompositionLocalOf<SettingSearchNavigator?> { null }

/** 页面级订阅一次下发给锚点，各自订阅会掉帧；页面已收集过请求时直接传入 */
@Composable
fun ProvideSettingSearch(
    request: SettingSearchRequest?,
    navigator: SettingSearchNavigator = koinInject(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSettingSearchNavigator provides navigator,
        LocalSettingSearchRequest provides request,
        content = content,
    )
}

@Composable
fun ProvideSettingSearch(
    navigator: SettingSearchNavigator = koinInject(),
    content: @Composable () -> Unit,
) {
    val request by navigator.pending.collectAsStateWithLifecycle()
    ProvideSettingSearch(request, navigator, content)
}

/** 命中时滚进视野并闪两下；只包常显内容 */
@Composable
fun SettingSearchTarget(
    @StringRes anchorRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val request = LocalSettingSearchRequest.current?.takeIf { it.entry.anchorRes == anchorRes }
    val fresh = request?.isFresh() == true
    val flash = remember { Animatable(0f) }
    if (request != null) {
        val navigator = LocalSettingSearchNavigator.current
        // 闪完再消费，请求不变 effect 就不会被取消
        LaunchedEffect(request) {
            if (fresh) {
                repeat(2) {
                    flash.animateTo(1f, tween(180))
                    flash.animateTo(0f, tween(420))
                }
            }
            navigator?.consume(request)
        }
    }

    val highlight = MaterialTheme.colorScheme.primary
    val bleed = LocalSettingRowBleed.current
    Box(
        modifier = modifier
            .bringIntoViewOnExpand(fresh)
            .drawBehind {
                if (flash.value <= 0f) return@drawBehind
                val bleedPx = bleed.toPx()
                drawRect(
                    color = highlight.copy(alpha = 0.18f * flash.value),
                    topLeft = Offset(-bleedPx, 0f),
                    size = Size(size.width + bleedPx * 2, size.height),
                )
            },
    ) {
        content()
    }
}

@Composable
fun SettingSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ITextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.settings_search_hint),
        singleLine = true,
        trailingIcon = {
            if (query.isEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                    )
                }
            }
        },
    )
}

/** 进入搜索才解析清单文本，换语言时重算 */
@Composable
fun SettingSearchResults(
    query: String,
    onClick: (SettingSearchEntry) -> Unit,
) {
    val locales = LocalConfiguration.current.locales
    val resources = LocalContext.current.resources
    val searchables = remember(locales, resources) {
        SettingSearchIndex.entries.filter { it.isAvailable(Build.VERSION.SDK_INT) }.map { entry ->
            SearchableSetting(
                entry = entry,
                title = resources.getString(entry.titleRes),
                description = entry.descRes?.let(resources::getString).orEmpty(),
                keywords = entry.keywordsRes?.let(resources::getString).orEmpty(),
                path = entry.location.path.joinToString(PATH_SEPARATOR) { resources.getString(it) },
            )
        }
    }
    val results = remember(searchables, query) { SettingSearchMatcher.filter(searchables, query) }

    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.settings_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = MaaDesignTokens.Spacing.lg),
        )
        return
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    SettingsGroupCard {
        results.forEachIndexed { index, item ->
            if (index > 0) ListItemDivider()
            SettingRow(
                title = item.title,
                description = item.path,
                titleColor = contentColor,
                descriptionColor = contentColor.copy(alpha = 0.7f),
                onClick = { onClick(item.entry) },
            )
        }
    }
}

private const val PATH_SEPARATOR = " › "
