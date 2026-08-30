package com.aliothmoon.maameow.presentation.components

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.announcement.AnnouncementSectionParser
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** 勾选"不再显示"前需停留的秒数 */
private const val STAY_SECONDS_REQUIRED = 5

/** 未读完狂点确认：超过该次数放行并解锁成就 */
private const val STUBBORN_CLICKS_TO_UNLOCK = 20

@Composable
fun AnnouncementDialog(
    imageAssetPath: String?,
    markdown: String,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    onStubbornUnlock: () -> Unit,
) {
    // 是否已滚动至底部
    var scrolledToBottom by remember { mutableStateOf(false) }
    // 已停留秒数
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    // "不再显示"勾选状态
    var dontShowAgain by remember { mutableStateOf(false) }
    // 未读完时点确认的累计次数
    var stubbornClicks by remember { mutableIntStateOf(0) }

    val scrollState = rememberScrollState()

    // 分节：`## ` 切分，0 = 全部；切节内容回顶
    val sections = remember(markdown) { AnnouncementSectionParser.parse(markdown) }
    val fullContent = remember(markdown) { AnnouncementSectionParser.fullContent(markdown) }
    var selectedSection by remember(markdown) { mutableIntStateOf(0) }
    val shownMarkdown = if (selectedSection in 1..sections.size) {
        sections[selectedSection - 1].content
    } else {
        fullContent
    }
    LaunchedEffect(selectedSection) { scrollState.scrollTo(0) }

    // 检测是否滚动到底部（内容不足一屏时 maxValue==0 视为已到底）
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value >= scrollState.maxValue }
            .distinctUntilChanged()
            .collect { atBottom ->
                if (atBottom) {
                    scrolledToBottom = true
                }
            }
    }

    // 停留计时器：滚动到底部后才开始计时，使倒计时提示得以显示
    LaunchedEffect(scrolledToBottom) {
        if (!scrolledToBottom) return@LaunchedEffect
        stubbornClicks = 0
        elapsedSeconds = 0
        repeat(STAY_SECONDS_REQUIRED) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // 勾选框是否可启用
    val canCheck by remember {
        derivedStateOf { scrolledToBottom && elapsedSeconds >= STAY_SECONDS_REQUIRED }
    }

    // 未读完点确认不关闭：文案逐次升级，累计 20+ 次解锁成就并放行（对齐 WPF）
    val confirmLabel = when {
        stubbornClicks <= 0 -> stringResource(R.string.announcement_confirm)
        stubbornClicks == 1 -> stringResource(R.string.announcement_not_finished_confirm_1)
        stubbornClicks == 2 -> stringResource(R.string.announcement_not_finished_confirm_2)
        else -> stringResource(R.string.announcement_not_finished_confirm_3) +
                "?".repeat(stubbornClicks - 3)
    }
    val onConfirmClick = {
        if (scrolledToBottom || dontShowAgain) {
            onDismiss(dontShowAgain)
        } else {
            stubbornClicks++
            if (stubbornClicks > STUBBORN_CLICKS_TO_UNLOCK) {
                onStubbornUnlock()
                onDismiss(false)
            }
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val context = LocalContext.current

    val imageBitmap = remember(imageAssetPath) {
        if (imageAssetPath == null) return@remember null
        runCatching {
            context.assets.open(imageAssetPath).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
    ) {
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val maxHorizontalInset = max(
            safeInsets.calculateLeftPadding(layoutDirection),
            safeInsets.calculateRightPadding(layoutDirection)
        )
        // 垂直安全区：避免居中弹窗底部按钮被状态栏/导航栏（手势条）遮挡
        val maxVerticalInset = max(
            safeInsets.calculateTopPadding(),
            safeInsets.calculateBottomPadding()
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .dialogWidth(max = 600.dp, fraction = 0.95f)
                    .heightIn(max = screenHeight * 0.85f)
                    .padding(
                        horizontal = maxHorizontalInset + 16.dp,
                        vertical = maxVerticalInset,
                    ),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                val inLandscape =
                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val stayHint = when {
                    canCheck -> null
                    !scrolledToBottom -> stringResource(R.string.announcement_scroll_to_bottom_hint)
                    else -> stringResource(
                        R.string.announcement_dont_show_again_hint,
                        maxOf(0, STAY_SECONDS_REQUIRED - elapsedSeconds),
                    )
                }

                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = if (inLandscape) 12.dp else 20.dp,
                    ),
                ) {
                    // 标题栏：横屏不再塞勾选和确认，避免挤爆
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        DialogIconBadge(icon = Icons.Rounded.Campaign)
                        Text(
                            text = stringResource(R.string.announcement_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 分节导航：≥2 节才显示，NEW 节带红点
                    if (sections.size >= 2) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SectionChip(
                                label = stringResource(R.string.announcement_section_all),
                                selected = selectedSection == 0,
                                isNew = false,
                                onClick = { selectedSection = 0 },
                            )
                            sections.forEachIndexed { index, section ->
                                SectionChip(
                                    label = section.title,
                                    selected = selectedSection == index + 1,
                                    isNew = section.isNew,
                                    onClick = { selectedSection = index + 1 },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 公告内容（可滚动）——用 weight 占据中间剩余空间（而非固定 0.55×屏高），
                    // 保证底部勾选框与确认按钮在任何屏幕高度/字体缩放下都不会被挤出弹窗裁掉
                    val atBottomNow by remember {
                        derivedStateOf { scrollState.value >= scrollState.maxValue }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                        ) {
                            MarkdownText(
                                markdown = shownMarkdown,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            // 图片作为内容收尾：首屏留给正文，读完滚到底自然看到
                            if (imageBitmap != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                        }
                        // 未滚到底时底部渐隐，提示下方还有内容
                        if (!atBottomNow) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            1f to MaterialTheme.colorScheme.surfaceColorAtElevation(
                                                6.dp
                                            ),
                                        )
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (inLandscape) 12.dp else 16.dp))

                    if (inLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DontShowAgainToggle(
                                checked = dontShowAgain,
                                enabled = canCheck,
                                hint = stayHint,
                                compact = true,
                                modifier = Modifier.weight(1f),
                                onCheckedChange = { dontShowAgain = it },
                            )
                            Button(
                                onClick = onConfirmClick,
                                modifier = Modifier
                                    .widthIn(min = 88.dp, max = 200.dp)
                                    .fillMaxHeight(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = confirmLabel,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        DontShowAgainToggle(
                            checked = dontShowAgain,
                            enabled = canCheck,
                            hint = stayHint,
                            compact = false,
                            modifier = Modifier.fillMaxWidth(),
                            onCheckedChange = { dontShowAgain = it },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onConfirmClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DontShowAgainToggle(
    checked: Boolean,
    enabled: Boolean,
    hint: String?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(R.string.announcement_dont_show_again),
                style = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = if (compact) {
                    Modifier
                        .height(20.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                } else {
                    Modifier.heightIn(min = 20.dp)
                },
            )
        }
        if (compact || hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint ?: "\u00A0",
                style = MaterialTheme.typography.bodySmall,
                color = if (hint != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Transparent
                },
                maxLines = if (compact) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    selected: Boolean,
    isNew: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = label, maxLines = 1)
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
        },
    )
}
