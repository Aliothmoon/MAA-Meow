package com.aliothmoon.maameow.presentation.view.panel.operprogress

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.OperProgressConfig
import com.aliothmoon.maameow.data.model.OperProgressImportException
import com.aliothmoon.maameow.data.model.OperProgressPlan
import com.aliothmoon.maameow.data.model.OperProgressPlanParser
import com.aliothmoon.maameow.data.model.coreRole
import com.aliothmoon.maameow.data.model.supportsOperProgress
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.resource.CharacterInfo
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.callback.TaskRunStatus
import com.aliothmoon.maameow.presentation.LocalFloatingWindowContext
import com.aliothmoon.maameow.presentation.LocalInputFocusManager
import com.aliothmoon.maameow.presentation.components.InlineConfirmPanel
import com.aliothmoon.maameow.presentation.components.PageBackHandler
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun OperProgressConfigPanel(
    config: OperProgressConfig,
    nodeId: String,
    clientType: String,
    onConfigChange: (OperProgressConfig) -> Unit,
    modifier: Modifier = Modifier,
    resourceDataManager: ResourceDataManager = koinInject(),
    statusTracker: TaskChainStatusTracker = koinInject(),
    appSettings: AppSettingsManager = koinInject(),
) {
    val operators by resourceDataManager.operators.collectAsStateWithLifecycle()
    val tasks by statusTracker.tasks.collectAsStateWithLifecycle()
    val locked = tasks.any {
        it.slot?.nodeId == nodeId && it.status in setOf(TaskRunStatus.PENDING, TaskRunStatus.IN_PROGRESS)
    }
    val currentConfig by rememberUpdatedState(config)
    val currentOnChange by rememberUpdatedState(onConfigChange)
    val currentLocked by rememberUpdatedState(locked)
    val expanded = remember { mutableStateListOf<String>() }
    var pickerVisible by remember { mutableStateOf(false) }
    var tipExpanded by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var revealPlanId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val currentResources by rememberUpdatedState(LocalResources.current)
    val clipboard = LocalClipboard.current
    val inputFocus = LocalInputFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // 应用语言标签是 "en" 不带地区，干员名要用资源语言码
    val appLanguage by appSettings.language.collectAsStateWithLifecycle()
    val language = remember(appLanguage) { appSettings.displayLanguage }
    val operatorsByPlanKey = remember(operators) {
        buildMap {
            operators.values.forEach { oper -> putIfAbsent(oper.name to oper.coreRole, oper) }
        }
    }
    val choices = remember(operators, clientType, language) {
        operators.values.filter {
            it.supportsOperProgress && resourceDataManager.isCharacterAvailableInClient(it, clientType)
        }.sortedWith(compareByDescending<CharacterInfo> { it.rarity }
            .thenBy { resourceDataManager.getLocalizedCharacterName(it, language) })
    }
    // 拖拽中只改本地顺序，松手再落盘；写回是异步的，逐格落盘会按旧顺序算导致回跳
    // 不能带 key：拖拽回调被 pointerInput 长期持有，重建会写到旧 state
    var orderedPlans by remember { mutableStateOf(config.plans) }
    LaunchedEffect(config.plans) { orderedPlans = config.plans }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        if (!currentLocked) {
            val plans = orderedPlans.toMutableList()
            val fromIndex = plans.indexOfFirst { it.id == from.key }
            val toIndex = plans.indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                plans.add(toIndex, plans.removeAt(fromIndex))
                orderedPlans = plans
            }
        }
    }
    val persistOrder = {
        val order = orderedPlans.map { it.id }
        if (!currentLocked && order != currentConfig.plans.map { it.id }) {
            // 按 id 取最新内容，只换顺序
            val byId = currentConfig.plans.associateBy { it.id }
            currentOnChange(currentConfig.copy(plans = order.mapNotNull { byId[it] }))
        }
    }
    LaunchedEffect(revealPlanId, config.plans, pickerVisible) {
        if (!pickerVisible) {
            val index = config.plans.indexOfFirst { it.id == revealPlanId }
            if (index >= 0) listState.animateScrollToItem(index + 1)
            revealPlanId = null
        }
    }

    if (!LocalFloatingWindowContext.current) {
        PageBackHandler(enabled = pickerVisible) { inputFocus.clear(); pickerVisible = false }
    }

    if (pickerVisible && !locked) {
        OperProgressOperatorPicker(
            operators = choices,
            plans = config.plans,
            displayName = { resourceDataManager.getLocalizedCharacterName(it, language) ?: it.name },
            onClose = { inputFocus.clear(); pickerVisible = false },
            onSelect = { oper ->
                val plan = OperProgressPlan.forOperator(oper)
                if (!currentLocked && currentConfig.plans.none { it.key == plan.key }) {
                    currentOnChange(currentConfig.copy(plans = currentConfig.plans + plan))
                    expanded.add(plan.id)
                    revealPlanId = plan.id
                    inputFocus.clear()
                    pickerVisible = false
                }
            },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "oper_progress_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.oper_progress_plans, config.plans.size),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    ExpandableTipIcon(tipExpanded, { tipExpanded = it })
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { expanded.clear() }, enabled = expanded.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.UnfoldLess, stringResource(R.string.oper_progress_collapse), Modifier.size(18.dp))
                    }
                    IconButton(onClick = { clearConfirm = !clearConfirm }, enabled = !locked && config.plans.isNotEmpty(), modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteSweep, stringResource(R.string.oper_progress_clear), Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ExpandableTipContent(tipExpanded, stringResource(R.string.oper_progress_tip))
                if (tipExpanded) {
                    Text(stringResource(R.string.oper_progress_online_editor), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { Misc.openUriSafely(context, ARKNTOOLS_MATERIAL_URL) }.padding(vertical = 4.dp))
                }
                if (locked) Text(stringResource(R.string.oper_progress_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { clearConfirm = false; pickerVisible = true }, enabled = !locked,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.oper_progress_add), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        enabled = !locked, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (locked) 0.15f else 0.35f)),
                        onClick = {
                            scope.launch {
                                try {
                                    val text = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (!currentLocked) {
                                        val parsed = OperProgressPlanParser.parse(text, operators.values, currentConfig.plans)
                                        currentOnChange(currentConfig.copy(plans = currentConfig.plans + parsed))
                                        expanded.addAll(parsed.map { it.id })
                                        revealPlanId = parsed.first().id
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: OperProgressImportException) {
                                    Toast.makeText(context, currentResources.getString(error.textRes, error.operatorName), Toast.LENGTH_LONG).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, R.string.oper_progress_import_invalid, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Default.ContentPaste, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.oper_progress_import), style = MaterialTheme.typography.bodySmall)
                    }
                }
                InlineConfirmPanel(
                    visible = clearConfirm && !locked && config.plans.isNotEmpty(),
                    message = stringResource(R.string.oper_progress_clear_confirm),
                    onConfirm = {
                        if (!currentLocked) currentOnChange(currentConfig.copy(plans = emptyList()))
                        expanded.clear()
                        clearConfirm = false
                    },
                    onDismiss = { clearConfirm = false },
                )
            }
        }
        if (orderedPlans.isEmpty()) {
            item(key = "oper_progress_empty") {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.School, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(stringResource(R.string.oper_progress_empty_title), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.oper_progress_empty), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        itemsIndexed(orderedPlans, key = { _, plan -> plan.id }) { index, plan ->
            val oper = operatorsByPlanKey[plan.name to plan.role]
            ReorderableItem(reorderable, key = plan.id) {
                OperProgressPlanCard(
                    plan = plan, oper = oper,
                    displayName = oper?.let { resourceDataManager.getLocalizedCharacterName(it, language) } ?: plan.name,
                    index = index + 1, expanded = plan.id in expanded, enabled = !locked,
                    dragModifier = Modifier.draggableHandle(
                        enabled = !locked,
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDragStopped = { persistOrder() },
                    ),
                    onToggle = { if (plan.id in expanded) expanded.remove(plan.id) else expanded.add(plan.id) },
                    onChange = { value ->
                        if (!currentLocked) currentOnChange(currentConfig.copy(plans = currentConfig.plans.map { if (it.id == plan.id) value else it }))
                    },
                    onRemove = {
                        if (!currentLocked) currentOnChange(currentConfig.copy(plans = currentConfig.plans.filterNot { it.id == plan.id }))
                        expanded.remove(plan.id)
                    },
                )
            }
        }
    }
}

private const val ARKNTOOLS_MATERIAL_URL = "https://arkntools.app/#/material"
