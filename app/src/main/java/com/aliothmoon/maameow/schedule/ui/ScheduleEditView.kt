package com.aliothmoon.maameow.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import com.aliothmoon.maameow.schedule.model.ScheduleType
import com.aliothmoon.maameow.utils.i18n.asString
import org.koin.androidx.compose.koinViewModel
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
    val errorMessage = state.errorMessage.asString()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<LocalTime?>(null) }

    LaunchedEffect(strategyId) {
        viewModel.loadStrategy(strategyId)
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            if (state.needBatteryOptimization || state.needExactAlarm) {
                showPermissionDialog = true
            } else {
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onDismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (state.isNew) {
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
                        TextButton(onClick = { viewModel.onSave() }) {
                            Text(stringResource(R.string.schedule_save))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            item {
                SectionHeader(stringResource(R.string.schedule_section_basic_info))
            }
            if (!state.isNew && state.strategyId != null) {
                item {
                    Text(
                        text = "ID: ${state.strategyId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text(stringResource(R.string.schedule_name)) },
                    placeholder = { Text(stringResource(R.string.schedule_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                SectionHeader(stringResource(R.string.schedule_section_type))
            }
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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
                    item {
                        SectionHeader(stringResource(R.string.schedule_section_days))
                    }
                    item {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
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

                    item {
                        SectionHeader(stringResource(R.string.schedule_section_times))
                    }
                    item {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
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
                    item {
                        SectionHeader(stringResource(R.string.schedule_section_start_time))
                    }
                    item {
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
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
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
                                    TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel)) }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        if (showStartTimePicker) {
                            val existingTime = state.startTimeMs?.let { ms ->
                                Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
                            }
                            TimePickerDialog(
                                initialTime = existingTime,
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

                    item {
                        SectionHeader(stringResource(R.string.schedule_section_interval))
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = if (state.intervalDays > 0) state.intervalDays.toString() else "",
                                onValueChange = { viewModel.onIntervalDaysChanged(it.toIntOrNull() ?: 0) },
                                label = { Text(stringResource(R.string.schedule_days_unit)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp)
                            )
                            OutlinedTextField(
                                value = if (state.intervalHours > 0) state.intervalHours.toString() else "",
                                onValueChange = { viewModel.onIntervalHoursChanged(it.toIntOrNull() ?: 0) },
                                label = { Text(stringResource(R.string.schedule_hours_unit)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp)
                            )
                            val totalMinutes = state.intervalDays * 24 * 60 + state.intervalHours * 60
                            if (totalMinutes > 0) {
                                Text(
                                    text = stringResource(R.string.schedule_total_hours, totalMinutes / 60),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.schedule_section_task_config))
            }
            item {
                if (state.profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.schedule_no_profiles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        )
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.schedule_section_advanced))
                val (expanded, setExpanded) = remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.schedule_force_start), style = MaterialTheme.typography.bodyLarge)
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
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialTime = editingTime,
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

    if (showPermissionDialog) {
        val context = LocalContext.current
        val tips = buildList {
            if (state.needBatteryOptimization) add(stringResource(R.string.schedule_permission_tip_battery_optimization))
            if (state.needExactAlarm) add(stringResource(R.string.schedule_permission_tip_exact_alarm))
        }
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                navController.popBackStack()
            },
            title = { Text(stringResource(R.string.schedule_permission_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.schedule_permission_message,
                        tips.joinToString(stringResource(R.string.common_enumeration_separator))
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.needBatteryOptimization) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    } else if (state.needExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }
                    showPermissionDialog = false
                    navController.popBackStack()
                }) { Text(stringResource(R.string.schedule_go_to_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    navController.popBackStack()
                }) { Text(stringResource(R.string.schedule_later)) }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime? = null,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: 0,
        initialMinute = initialTime?.minute ?: 0
    )
    val configuration = LocalConfiguration.current
    var showDial by remember { mutableStateOf(configuration.screenHeightDp >= 400) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.schedule_time_picker_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                if (showDial) {
                    TimePicker(state = timePickerState)
                } else {
                    TimeInput(state = timePickerState)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showDial = !showDial }) {
                        Text(
                            if (showDial) {
                                stringResource(R.string.schedule_time_picker_keyboard_input)
                            } else {
                                stringResource(R.string.schedule_time_picker_dial_selection)
                            }
                        )
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                        TextButton(onClick = {
                            onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        }) { Text(stringResource(R.string.common_confirm)) }
                    }
                }
            }
        }
    }
}
