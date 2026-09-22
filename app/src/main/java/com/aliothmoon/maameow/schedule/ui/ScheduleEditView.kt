package com.aliothmoon.maameow.schedule.ui

import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.presentation.LocalToaster
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.components.WheelTimeFormatToggle
import com.aliothmoon.maameow.presentation.components.WheelTimePicker
import com.aliothmoon.maameow.presentation.components.rememberWheelTimePickerState
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.schedule.model.ScheduleHealthIssue
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.schedule.service.ExactAlarmSettings
import com.aliothmoon.maameow.schedule.service.OemPowerHints
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.theme.OpaqueTheme
import com.aliothmoon.maameow.utils.i18n.asString
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditView(
    navController: NavController,
    strategyId: String?,
    viewModel: ScheduleEditViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isNew = if (state.isLoading) strategyId == null else state.isNew
    val errorMessage = state.errorMessage.asString()
    val toaster = LocalToaster.current
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<LocalTime?>(null) }
    val context = LocalContext.current

    var use24HourPicker by rememberSaveable { mutableStateOf(DateFormat.is24HourFormat(context)) }

    LaunchedEffect(strategyId) {
        viewModel.loadStrategy(context, strategyId)
    }

    val permissionManager: PermissionManager = koinInject()
    val wizardScope = rememberCoroutineScope()

    // 可见性由三个条件推导，不另存一份可变标志
    var wizardDismissed by remember { mutableStateOf(false) }
    val showWizard = state.saveSuccess && !wizardDismissed && state.wizardPending.isNotEmpty()

    // 全部处理完自动返回，含保存时本就全通过的情况
    LaunchedEffect(state.saveSuccess, state.wizardPending.isEmpty()) {
        if (state.saveSuccess && state.wizardPending.isEmpty()) {
            navController.popBackStack()
        }
    }

    // 从系统设置返回时重检，推进到下一项
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (showWizard) viewModel.refreshPermissionChecks()
    }

    // 与健康卡走同一条路，别再手写一套裸 Intent
    fun openWizardTarget(item: ScheduleHealthIssue) {
        wizardScope.launch {
            when (item) {
                ScheduleHealthIssue.BATTERY -> permissionManager.requestBatteryWhitelist(context)
                ScheduleHealthIssue.EXACT_ALARM -> ExactAlarmSettings.open(context)
                ScheduleHealthIssue.NOTIFICATION -> permissionManager.requestNotification(context)
                ScheduleHealthIssue.OVERLAY -> permissionManager.requestOverlay(context)
                // 进不了向导，由健康卡负责
                ScheduleHealthIssue.BACKEND -> Unit
            }
            viewModel.refreshPermissionChecks()
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) {
            toaster.show(errorMessage, type = ToastType.Error)
            viewModel.onDismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (isNew) {
                    stringResource(R.string.schedule_edit_title_new)
                } else {
                    stringResource(R.string.schedule_edit_title_edit)
                },
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.popBackStack() },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            onClick = { viewModel.onSave(context) },
                            enabled = !state.isLoading,
                        ) {
                            Text(stringResource(R.string.schedule_save))
                        }
                    }
                }
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = MaaDesignTokens.Spacing.listHorizontal,
                vertical = MaaDesignTokens.Spacing.sm
            )
        ) {
            item(key = "basic-header") {
                SectionHeader(stringResource(R.string.schedule_section_basic_info))
            }
            if (!state.isNew && state.strategyId != null) {
                item(key = "strategy-id") {
                    Text(
                        text = "ID: ${state.strategyId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text(stringResource(R.string.schedule_name)) },
                    placeholder = { Text(stringResource(R.string.schedule_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            item(key = "type-header") {
                Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                SectionHeader(stringResource(R.string.schedule_section_type))
            }
            item(key = "type") {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    ScheduleType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.scheduleType == type,
                            onClick = { viewModel.onScheduleTypeChanged(type) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ScheduleType.entries.size,
                                baseShape = RoundedCornerShape(4.dp)
                            )
                        ) {
                            Text(
                                when (type) {
                                    ScheduleType.FIXED_TIME -> stringResource(R.string.schedule_type_fixed_time)
                                    ScheduleType.INTERVAL -> stringResource(R.string.schedule_type_interval)
                                }
                            )
                        }
                    }
                }
            }

            when (state.scheduleType) {
                ScheduleType.FIXED_TIME -> {
                    item(key = "days-header") {
                        Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                        SectionHeader(stringResource(R.string.schedule_section_days))
                    }
                    item(key = "days") {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chipColors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                            val allSelected = DayOfWeek.entries.all { it in state.daysOfWeek }
                            FilterChip(
                                selected = allSelected,
                                onClick = { viewModel.onToggleAllDays() },
                                label = { Text(stringResource(R.string.schedule_every_day)) },
                                colors = chipColors
                            )
                            DayOfWeek.entries.forEach { day ->
                                FilterChip(
                                    selected = day in state.daysOfWeek,
                                    onClick = { viewModel.onToggleDay(day) },
                                    label = { Text(scheduleDayChipLabel(day)) },
                                    colors = chipColors
                                )
                            }
                        }
                    }

                    item(key = "times-header") {
                        Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                        SectionHeader(stringResource(R.string.schedule_section_times))
                    }
                    item(key = "times") {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.executionTimes.forEach { time ->
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        editingTime = time
                                        showTimePicker = true
                                    },
                                    label = { Text(time.format(timeFormatter)) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.onRemoveTime(time) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.common_delete),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            AssistChip(
                                onClick = {
                                    editingTime = null
                                    showTimePicker = true
                                },
                                label = { Text(stringResource(R.string.schedule_add_time)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                ScheduleType.INTERVAL -> {
                    item(key = "start-time-header") {
                        Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                        SectionHeader(stringResource(R.string.schedule_section_start_time))
                    }
                    item(key = "start-time") {
                        var showDatePicker by remember { mutableStateOf(false) }
                        var showStartTimePicker by remember { mutableStateOf(false) }
                        // 暂存选中的日期，等时间也选完后一起写入
                        var pendingDateMs by remember { mutableStateOf<Long?>(null) }

                        val displayText = state.startTimeMs?.let { ms ->
                            val zdt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                            zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        } ?: stringResource(R.string.schedule_tap_to_choose)

                        OutlinedTextField(
                            value = displayText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.schedule_first_execution_time)) },
                            modifier = Modifier
                                .fillMaxWidth(),
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { source ->
                                LaunchedEffect(source) {
                                    source.interactions.collect { interaction ->
                                        if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                            showDatePicker = true
                                        }
                                    }
                                }
                            }
                        )

                        if (showDatePicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = state.startTimeMs
                                    ?: System.currentTimeMillis()
                            )
                            OpaqueTheme {
                                DatePickerDialog(
                                    onDismissRequest = { showDatePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            pendingDateMs = datePickerState.selectedDateMillis
                                            showDatePicker = false
                                            showStartTimePicker = true
                                        }) { Text(stringResource(R.string.schedule_next_step)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePicker = false }) {
                                            Text(
                                                stringResource(R.string.common_cancel)
                                            )
                                        }
                                    }
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                        }

                        if (showStartTimePicker) {
                            val existingTime = state.startTimeMs?.let { ms ->
                                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                                    .toLocalTime()
                            }
                            TimePickerDialog(
                                initialTime = existingTime,
                                is24Hour = use24HourPicker,
                                onFormatChange = { use24HourPicker = it },
                                onDismiss = { showStartTimePicker = false },
                                onConfirm = { time ->
                                    val dateMs = pendingDateMs ?: return@TimePickerDialog
                                    val date = Instant.ofEpochMilli(dateMs)
                                        .atZone(ZoneId.of("UTC"))
                                        .toLocalDate()
                                    val combined = date.atTime(time)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                                    viewModel.onStartTimeChanged(combined)
                                    showStartTimePicker = false
                                }
                            )
                        }
                    }

                    item(key = "interval-header") {
                        Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                        SectionHeader(stringResource(R.string.schedule_section_interval))
                    }
                    item(key = "interval") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = if (state.intervalDays > 0) state.intervalDays.toString() else "",
                                onValueChange = {
                                    viewModel.onIntervalDaysChanged(
                                        it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text(stringResource(R.string.schedule_days_unit)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp)
                            )
                            OutlinedTextField(
                                value = if (state.intervalHours > 0) state.intervalHours.toString() else "",
                                onValueChange = {
                                    viewModel.onIntervalHoursChanged(
                                        it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text(stringResource(R.string.schedule_hours_unit)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp)
                            )
                            val totalMinutes =
                                state.intervalDays * 24 * 60 + state.intervalHours * 60
                            if (totalMinutes > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.schedule_total_hours,
                                        totalMinutes / 60
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item(key = "profile-header") {
                Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                SectionHeader(stringResource(R.string.schedule_section_task_config))
            }
            item(key = "profile") {
                if (state.profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.schedule_no_profiles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.profiles.forEach { profile ->
                            FilterChip(
                                selected = profile.id == state.selectedProfileId,
                                onClick = { viewModel.onSelectProfile(profile.id) },
                                label = { Text(profile.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    // 显示选中 Profile 的已启用任务摘要
                    val selectedProfile = state.profiles.find { it.id == state.selectedProfileId }
                    val enabledTasks = selectedProfile?.chain
                        ?.filter { it.enabled }
                        ?.joinToString("、") { it.name }
                    if (!enabledTasks.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.schedule_enabled_tasks, enabledTasks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MaaDesignTokens.Spacing.sm)
                        )
                    }
                }
            }

            item(key = "force-start") {
                Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                SectionHeader(stringResource(R.string.schedule_section_advanced))
                val (expanded, setExpanded) = remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.schedule_force_start),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        ExpandableTipIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            expanded = expanded,
                            onExpandedChange = { setExpanded(it) })
                    }
                    Switch(
                        checked = state.forceStart,
                        onCheckedChange = { viewModel.onForceStartChanged(it) }
                    )
                }
                ExpandableTipContent(
                    visible = expanded,
                    tipText = stringResource(R.string.schedule_force_start_tip),
                )
            }

            item(key = "screen-saver") {
                val (saverExpanded, setSaverExpanded) = remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.schedule_auto_screen_saver),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        ExpandableTipIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            expanded = saverExpanded,
                            onExpandedChange = { setSaverExpanded(it) })
                    }
                    Switch(
                        checked = state.autoScreenSaver,
                        onCheckedChange = { viewModel.onAutoScreenSaverChanged(it) }
                    )
                }
                ExpandableTipContent(
                    visible = saverExpanded,
                    tipText = stringResource(R.string.schedule_auto_screen_saver_tip),
                )
            }

            item(key = "auto-sleep") {
                val (sleepExpanded, setSleepExpanded) = remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.schedule_auto_sleep_after_task),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        ExpandableTipIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            expanded = sleepExpanded,
                            onExpandedChange = { setSleepExpanded(it) })
                    }
                    Switch(
                        checked = state.autoSleepAfterTask,
                        onCheckedChange = { viewModel.onAutoSleepAfterTaskChanged(it) }
                    )
                }
                ExpandableTipContent(
                    visible = sleepExpanded,
                    tipText = stringResource(R.string.schedule_auto_sleep_tip),
                )
                // 从属于上面的开关，关掉就没有意义，直接隐藏
                if (state.autoSleepAfterTask) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.schedule_skip_auto_sleep_if_awake),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.schedule_skip_auto_sleep_if_awake_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.skipAutoSleepIfAwake,
                            onCheckedChange = { viewModel.onSkipAutoSleepIfAwakeChanged(it) }
                        )
                    }
                }
            }

            item(key = "close-game") {
                // 优先级规则容易踩坑，默认展开
                val (closeExpanded, setCloseExpanded) = remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.schedule_close_game_after_task),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        ExpandableTipIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            expanded = closeExpanded,
                            onExpandedChange = { setCloseExpanded(it) })
                    }
                    Switch(
                        checked = state.closeGameAfterTask,
                        onCheckedChange = { viewModel.onCloseGameAfterTaskChanged(it) }
                    )
                }
                val closeEffect by viewModel.closeGameEffect.collectAsStateWithLifecycle()
                val willClose = closeEffect == CloseGameEffect.GlobalOverride
                        || closeEffect == CloseGameEffect.StrategyActive
                Text(
                    text = stringResource(
                        when (closeEffect) {
                            CloseGameEffect.ForegroundInactive -> R.string.schedule_close_game_effect_foreground
                            CloseGameEffect.GlobalOverride -> R.string.schedule_close_game_effect_global
                            CloseGameEffect.StrategyActive -> R.string.schedule_close_game_effect_strategy
                            CloseGameEffect.Inactive -> R.string.schedule_close_game_effect_inactive
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (willClose) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = MaaDesignTokens.Spacing.sm)
                )
                ExpandableTipContent(
                    visible = closeExpanded,
                    tipText = stringResource(R.string.schedule_close_game_tip),
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialTime = editingTime,
            is24Hour = use24HourPicker,
            onFormatChange = { use24HourPicker = it },
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                val old = editingTime
                if (old != null) {
                    viewModel.onReplaceTime(old, time)
                } else {
                    viewModel.onAddTime(time)
                }
                showTimePicker = false
            }
        )
    }

    if (showWizard) {
        val current = state.wizardPending.first()
        PermissionWizardDialog(
            current = current,
            oemHint = scheduleOemPowerHintText(OemPowerHints.hintFor(Build.MANUFACTURER))
                .takeIf { current == ScheduleHealthIssue.BATTERY },
            onGo = { openWizardTarget(current) },
            onLater = {
                wizardDismissed = true
                navController.popBackStack()
            },
        )
    }
}

/** 每次只展示当前待处理项，全部通过后自动关闭 */
@Composable
private fun PermissionWizardDialog(
    current: ScheduleHealthIssue,
    oemHint: String?,
    onGo: () -> Unit,
    onLater: () -> Unit,
) {
    val (tip, desc) = schedulePermissionActionText(current)
    OpaqueTheme {
        AlertDialog(
            onDismissRequest = onLater,
            title = { Text(stringResource(R.string.schedule_permission_title)) },
            text = {
                Column {
                    Text(tip, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall)
                    if (oemHint != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            oemHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onGo) { Text(stringResource(R.string.schedule_go_to_settings)) }
            },
            dismissButton = {
                TextButton(onClick = onLater) { Text(stringResource(R.string.common_later)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime? = null,
    is24Hour: Boolean,
    onFormatChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val pickerState = rememberWheelTimePickerState(
        initialHour = initialTime?.hour ?: 0,
        initialMinute = initialTime?.minute ?: 0,
        is24Hour = is24Hour
    )
    val configuration = LocalConfiguration.current
    // 横屏等矮屏只留三行，保证对话框放得下
    val rows = if (configuration.screenHeightDp >= 400) 5 else 3

    OpaqueTheme {
        BasicAlertDialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MaaDesignTokens.Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_time_picker_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        WheelTimeFormatToggle(
                            is24Hour = is24Hour,
                            onFormatChange = onFormatChange
                        )
                    }
                    WheelTimePicker(
                        state = pickerState,
                        rows = rows,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(MaaDesignTokens.Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                        TextButton(onClick = {
                            onConfirm(LocalTime.of(pickerState.hour, pickerState.minute))
                        }) { Text(stringResource(R.string.common_confirm)) }
                    }
                }
            }
        }
    }
}
