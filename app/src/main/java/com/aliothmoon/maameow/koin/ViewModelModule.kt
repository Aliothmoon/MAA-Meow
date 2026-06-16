package com.aliothmoon.maameow.koin

import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.presentation.viewmodel.AchievementViewModel
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ErrorLogViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ExpandedControlPanelViewModel
import com.aliothmoon.maameow.presentation.viewmodel.HomeViewModel
import com.aliothmoon.maameow.presentation.viewmodel.LogHistoryViewModel
import com.aliothmoon.maameow.presentation.viewmodel.TaskOverrideEditorViewModel
import com.aliothmoon.maameow.presentation.viewmodel.ToolboxViewModel
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import com.aliothmoon.maameow.presentation.viewmodel.NotificationSettingsViewModel
import com.aliothmoon.maameow.presentation.viewmodel.UpdateViewModel
import com.aliothmoon.maameow.schedule.ui.ScheduleEditViewModel
import com.aliothmoon.maameow.schedule.ui.ScheduleListViewModel
import com.aliothmoon.maameow.schedule.ui.ScheduleTriggerLogViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::AchievementViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::LogHistoryViewModel)
    viewModelOf(::ErrorLogViewModel)
    viewModelOf(::BackgroundTaskViewModel)
    viewModelOf(::ScheduleListViewModel)
    viewModelOf(::ScheduleEditViewModel)
    viewModelOf(::ScheduleTriggerLogViewModel)
    viewModelOf(::NotificationSettingsViewModel)
    viewModelOf(::TaskOverrideEditorViewModel)
}


val floatingWindowModule = module {
    singleOf(::ExpandedControlPanelViewModel)
    singleOf(::CopilotViewModel)
    singleOf(::ToolboxViewModel)
}
