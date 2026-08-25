package com.aliothmoon.maameow.presentation.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.navigation.BottomNavTab

/**
 * @param tab    该步所在的主 Tab，切到这一步时 pager 先切过去
 * @param target 高亮靶点，null = 不挖洞、卡片居中
 */
data class OnboardingStep(
    val tab: BottomNavTab,
    val target: OnboardingTarget?,
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

object OnboardingSteps {

    val all: List<OnboardingStep> = listOf(
        // 首页
        OnboardingStep(
            tab = BottomNavTab.HOME,
            target = null,
            icon = Icons.Rounded.AutoAwesome,
            titleRes = R.string.onboarding_welcome_title,
            bodyRes = R.string.onboarding_welcome_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.HOME,
            target = OnboardingTarget.SERVICE_STATUS,
            icon = Icons.Rounded.Dashboard,
            titleRes = R.string.onboarding_status_title,
            bodyRes = R.string.onboarding_status_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.HOME,
            target = OnboardingTarget.RUN_MODE,
            icon = Icons.Rounded.Layers,
            titleRes = R.string.onboarding_run_mode_title,
            bodyRes = R.string.onboarding_run_mode_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.HOME,
            target = OnboardingTarget.PERMISSIONS,
            icon = Icons.Rounded.VerifiedUser,
            titleRes = R.string.onboarding_permissions_title,
            bodyRes = R.string.onboarding_permissions_body,
        ),
        // 后台任务页
        OnboardingStep(
            tab = BottomNavTab.BACKGROUND,
            target = OnboardingTarget.BG_PREVIEW,
            icon = Icons.Rounded.Tv,
            titleRes = R.string.onboarding_bg_preview_title,
            bodyRes = R.string.onboarding_bg_preview_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.BACKGROUND,
            target = OnboardingTarget.BG_PANEL_TABS,
            icon = Icons.Rounded.Tab,
            titleRes = R.string.onboarding_bg_panel_tabs_title,
            bodyRes = R.string.onboarding_bg_panel_tabs_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.BACKGROUND,
            target = OnboardingTarget.BG_TASK_LIST,
            icon = Icons.Rounded.Checklist,
            titleRes = R.string.onboarding_bg_task_list_title,
            bodyRes = R.string.onboarding_bg_task_list_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.BACKGROUND,
            target = OnboardingTarget.BG_START,
            icon = Icons.Rounded.PlayArrow,
            titleRes = R.string.onboarding_bg_start_title,
            bodyRes = R.string.onboarding_bg_start_body,
        ),
        // 定时任务页
        OnboardingStep(
            tab = BottomNavTab.SCHEDULE,
            target = OnboardingTarget.SCHEDULE_ADD,
            icon = Icons.Rounded.Add,
            titleRes = R.string.onboarding_schedule_add_title,
            bodyRes = R.string.onboarding_schedule_add_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.SCHEDULE,
            target = OnboardingTarget.SCHEDULE_LIST,
            icon = Icons.AutoMirrored.Rounded.EventNote,
            titleRes = R.string.onboarding_schedule_list_title,
            bodyRes = R.string.onboarding_schedule_list_body,
        ),
        OnboardingStep(
            tab = BottomNavTab.SCHEDULE,
            target = OnboardingTarget.SCHEDULE_TRIGGER_LOG,
            icon = Icons.Rounded.History,
            titleRes = R.string.onboarding_schedule_trigger_log_title,
            bodyRes = R.string.onboarding_schedule_trigger_log_body,
        ),
        // 收尾回首页，指一下设置
        OnboardingStep(
            tab = BottomNavTab.HOME,
            target = OnboardingTarget.TAB_SETTINGS,
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            titleRes = R.string.onboarding_tab_settings_title,
            bodyRes = R.string.onboarding_tab_settings_body,
        ),
    )
}
