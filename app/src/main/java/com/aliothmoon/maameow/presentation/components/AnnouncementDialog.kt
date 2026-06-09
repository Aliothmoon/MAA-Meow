package com.aliothmoon.maameow.presentation.components

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.snapshotFlow
import com.aliothmoon.maameow.R
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay

/** 勾选"不再显示"前需停留的秒数 */
private const val STAY_SECONDS_REQUIRED = 5

@Composable
fun AnnouncementDialog(
    imageAssetPath: String?,
    markdown: String,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
) {
    // 是否已滚动至底部
    var scrolledToBottom by remember { mutableStateOf(false) }
    // 已停留秒数
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    // "不再显示"勾选状态
    var dontShowAgain by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

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

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .dialogWidth(max = 600.dp, fraction = 0.95f)
                    .heightIn(max = screenHeight * 0.85f)
                    .padding(horizontal = maxHorizontalInset + 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val inLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                // 标题栏
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.announcement_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (inLandscape) {
                        Row(
                            modifier = Modifier
                                .toggleable(
                                    value = dontShowAgain,
                                    enabled = canCheck,
                                    role = Role.Checkbox,
                                    onValueChange = { dontShowAgain = it },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = null,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.announcement_dont_show_again),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canCheck) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                )
                                if (!canCheck) {
                                    var showHint by remember { mutableStateOf(false) }
                                    val hintText = if (!scrolledToBottom) {
                                        stringResource(R.string.announcement_scroll_to_bottom_hint)
                                    } else {
                                        val remaining = maxOf(0, STAY_SECONDS_REQUIRED - elapsedSeconds)
                                        stringResource(R.string.announcement_dont_show_again_hint, remaining)
                                    }
                                    Box {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = hintText,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { showHint = true },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        DropdownMenu(
                                            expanded = showHint,
                                            onDismissRequest = { showHint = false },
                                        ) {
                                            Text(
                                                text = hintText,
                                                modifier = Modifier.padding(12.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { onDismiss(dontShowAgain) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.announcement_confirm),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 公告内容（可滚动）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.55f)
                        .verticalScroll(scrollState),
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    MarkdownText(
                        markdown = markdown,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!inLandscape) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // "不再显示"勾选框
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = dontShowAgain,
                                enabled = canCheck,
                                role = Role.Checkbox,
                                onValueChange = { dontShowAgain = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = null,
                        )
                        Text(
                            text = stringResource(R.string.announcement_dont_show_again),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (canCheck) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }

                    // 未满足条件时显示提示
                    if (!canCheck) {
                        if (!scrolledToBottom) {
                            // 尚未滚动到底部（无论 elapsedSeconds 是多少）
                            Text(
                                text = stringResource(R.string.announcement_scroll_to_bottom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        } else {
                            // 已滚动到底部，但时间未到
                            val remaining = maxOf(0, STAY_SECONDS_REQUIRED - elapsedSeconds)
                            Text(
                                text = stringResource(
                                    R.string.announcement_dont_show_again_hint,
                                    remaining,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onDismiss(dontShowAgain) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.announcement_confirm))
                    }
                    }
                }
            }
        }
    }
}
