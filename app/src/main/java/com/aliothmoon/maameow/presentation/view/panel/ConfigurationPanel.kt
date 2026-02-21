package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.presentation.components.EmptyConfigHint
import com.aliothmoon.maameow.presentation.view.panel.fight.FightConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.mall.MallConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.roguelike.RoguelikeConfigPanel
import kotlinx.coroutines.launch

@Composable
fun ConfigurationPanel(
    modifier: Modifier = Modifier,
    state: FloatingPanelState,
    taskConfig: TaskConfigState,
) {
    val scope = rememberCoroutineScope()

    // Collect each config from StateFlow
    val wakeUpConfig by taskConfig.wakeUpConfig.collectAsStateWithLifecycle()
    val recruitConfig by taskConfig.recruitConfig.collectAsStateWithLifecycle()
    val infrastConfig by taskConfig.infrastConfig.collectAsStateWithLifecycle()
    val fightConfig by taskConfig.fightConfig.collectAsStateWithLifecycle()
    val mallConfig by taskConfig.mallConfig.collectAsStateWithLifecycle()
    val awardConfig by taskConfig.awardConfig.collectAsStateWithLifecycle()
    val roguelikeConfig by taskConfig.roguelikeConfig.collectAsStateWithLifecycle()
    val reclamationConfig by taskConfig.reclamationConfig.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        when (state.currentTaskType) {
            TaskType.WAKE_UP -> WakeUpConfigPanel(
                config = wakeUpConfig,
                onConfigChange = { scope.launch { taskConfig.setWakeUpConfig(it) } }
            )

            TaskType.RECRUITING -> RecruitConfigPanel(
                config = recruitConfig,
                onConfigChange = { scope.launch { taskConfig.setRecruitConfig(it) } }
            )

            TaskType.BASE -> InfrastConfigPanel(
                config = infrastConfig,
                onConfigChange = { scope.launch { taskConfig.setInfrastConfig(it) } }
            )

            TaskType.COMBAT -> FightConfigPanel(
                config = fightConfig,
                onConfigChange = { scope.launch { taskConfig.setFightConfig(it) } }
            )

            TaskType.MALL -> MallConfigPanel(
                config = mallConfig,
                onConfigChange = { scope.launch { taskConfig.setMallConfig(it) } }
            )

            TaskType.MISSION -> AwardConfigPanel(
                config = awardConfig,
                onConfigChange = { scope.launch { taskConfig.setAwardConfig(it) } }
            )

            TaskType.AUTO_ROGUELIKE -> RoguelikeConfigPanel(
                config = roguelikeConfig,
                onConfigChange = { scope.launch { taskConfig.setRoguelikeConfig(it) } },
            )

            TaskType.RECLAMATION -> ReclamationConfigPanel(
                config = reclamationConfig,
                onConfigChange = { scope.launch { taskConfig.setReclamationConfig(it) } }
            )

            null -> EmptyConfigHint()
        }
    }
}
