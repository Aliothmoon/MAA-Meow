package com.aliothmoon.maameow.presentation.view.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.presentation.components.CollapsibleSecretField
import com.aliothmoon.maameow.presentation.components.ListItemDivider
import com.aliothmoon.maameow.presentation.components.SectionHeader
import com.aliothmoon.maameow.presentation.components.SettingRow
import com.aliothmoon.maameow.presentation.components.SettingsGroupCard
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.search.ProvideSettingSearch
import com.aliothmoon.maameow.presentation.search.SettingSearchTarget
import com.aliothmoon.maameow.presentation.viewmodel.YituliuSettingsViewModel
import com.aliothmoon.maameow.theme.MaaDesignTokens
import com.aliothmoon.maameow.utils.Misc
import com.aliothmoon.maameow.utils.i18n.asString
import org.koin.androidx.compose.koinViewModel

@Composable
fun YituliuSettingsView(
    navController: NavController,
    viewModel: YituliuSettingsViewModel = koinViewModel(),
) {
    val reportToYituliu by viewModel.reportToYituliu.collectAsStateWithLifecycle()
    val operBoxUseYituliuApi by viewModel.operBoxUseYituliuApi.collectAsStateWithLifecycle()
    val operBoxAssist by viewModel.operBoxAssistState.collectAsStateWithLifecycle()
    val perProfile by viewModel.perProfile.collectAsStateWithLifecycle()
    val sharedToken by viewModel.sharedToken.collectAsStateWithLifecycle()
    val profileTokens by viewModel.profileTokens.collectAsStateWithLifecycle()
    val verifyStates by viewModel.verifyStates.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_yituliu_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() },
            )
        },
    ) { paddingValues ->
        val contentColor = MaterialTheme.colorScheme.onSurface

        ProvideSettingSearch {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                contentPadding = PaddingValues(
                    horizontal = MaaDesignTokens.Spacing.listHorizontal,
                    vertical = MaaDesignTokens.Spacing.sm,
                ),
            ) {
                // 屏外 item 不组合，拆开搜索锚点会落空
                item {
                    SectionHeader(stringResource(R.string.settings_yituliu_section_oper_box))
                    SettingsGroupCard {
                        SettingSearchTarget(R.string.settings_oper_box_yituliu_title) {
                            SettingSwitchItem(
                                title = stringResource(R.string.settings_oper_box_yituliu_title),
                                info = stringResource(R.string.settings_oper_box_yituliu_desc),
                                contentColor = contentColor,
                                checked = operBoxUseYituliuApi,
                                icon = Icons.Rounded.Inventory2,
                                onCheckedChange = { viewModel.setOperBoxUseYituliuApi(it) },
                            )
                        }
                        ListItemDivider()
                        SettingSearchTarget(R.string.panel_autobattle_operbox_assist) {
                            SettingSwitchItem(
                                title = stringResource(R.string.panel_autobattle_operbox_assist),
                                info = stringResource(R.string.settings_oper_box_assist_desc),
                                contentColor = contentColor,
                                checked = operBoxAssist.active,
                                enabled = operBoxAssist.available,
                                icon = Icons.Rounded.Groups,
                                onCheckedChange = { viewModel.setOperBoxAssistEnabled(it) },
                            )
                        }
                    }

                    Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                    SettingSearchTarget(R.string.settings_yituliu_open_api_token) {
                        SectionHeader(stringResource(R.string.settings_yituliu_open_api_token))
                    }
                    SettingsGroupCard {
                        SettingSearchTarget(R.string.settings_yituliu_token_per_profile) {
                            SettingSwitchItem(
                                title = stringResource(R.string.settings_yituliu_token_per_profile),
                                contentColor = contentColor,
                                checked = perProfile,
                                icon = Icons.Rounded.ManageAccounts,
                                onCheckedChange = { viewModel.setPerProfile(it) },
                            )
                        }
                        ListItemDivider()
                        if (perProfile) {
                            profileTokens.forEach { profile ->
                                key(profile.profileId) {
                                    TokenField(
                                        label = if (profile.active) {
                                            stringResource(R.string.settings_yituliu_token_profile_active, profile.name)
                                        } else {
                                            profile.name
                                        },
                                        token = profile.token,
                                        verify = verifyStates[profile.profileId],
                                        onTokenChange = { viewModel.setProfileToken(profile.profileId, it) },
                                        onVerify = { viewModel.verify(profile.profileId) },
                                    )
                                    ListItemDivider()
                                }
                            }
                        } else {
                            key(YituliuSettingsViewModel.SHARED) {
                                TokenField(
                                    label = stringResource(R.string.settings_yituliu_open_api_token),
                                    token = sharedToken,
                                    verify = verifyStates[YituliuSettingsViewModel.SHARED],
                                    onTokenChange = { viewModel.setSharedToken(it) },
                                    onVerify = { viewModel.verify(YituliuSettingsViewModel.SHARED) },
                                )
                            }
                            ListItemDivider()
                        }
                        SettingRow(
                            title = stringResource(R.string.settings_yituliu_get_token),
                            description = stringResource(R.string.settings_yituliu_open_api_token_hint),
                            titleColor = contentColor,
                            trailing = { RowTrailingIcon(RowTrailing.EXTERNAL) },
                            onClick = { Misc.openUriSafely(context, MaaApi.YITULIU_ACCOUNT_HOME) },
                        )
                    }

                    Spacer(Modifier.height(MaaDesignTokens.Spacing.sectionGap))
                    SectionHeader(stringResource(R.string.settings_yituliu_section_report))
                    SettingsGroupCard {
                        SettingSearchTarget(R.string.settings_report_yituliu) {
                            SettingSwitchItem(
                                title = stringResource(R.string.settings_report_yituliu),
                                info = stringResource(R.string.settings_report_yituliu_desc),
                                contentColor = contentColor,
                                checked = reportToYituliu,
                                icon = Icons.Rounded.Leaderboard,
                                onCheckedChange = { viewModel.setReportToYituliu(it) },
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(MaaDesignTokens.Spacing.xxl)) }
            }
        }
    }
}

@Composable
private fun TokenField(
    label: String,
    token: String,
    verify: YituliuSettingsViewModel.VerifyState?,
    onTokenChange: (String) -> Unit,
    onVerify: () -> Unit,
) {
    CollapsibleSecretField(
        title = label,
        value = token,
        onValueChange = onTokenChange,
        placeholder = stringResource(R.string.settings_yituliu_token_placeholder),
        titleStyle = MaterialTheme.typography.bodyLarge,
        action = {
            TextButton(
                onClick = onVerify,
                enabled = verify?.verifying != true,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(stringResource(R.string.settings_yituliu_token_verify))
            }
        },
        status = status@{
            val message = verify?.message ?: return@status
            Text(
                text = message.text.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (message.ok) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        },
    )
}
