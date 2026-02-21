package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.TaskType
import com.aliothmoon.maameow.data.preferences.TaskConfigState
import com.aliothmoon.maameow.maa.task.MaaTaskParams

class BuildTaskParamsUseCase(private val config: TaskConfigState) {
    operator fun invoke(): List<MaaTaskParams> {
        val tasks = config.taskList.value
            .filter { it.isEnabled }
            .sortedBy { it.order }

        return tasks.flatMap {
            when (it.toTaskType()) {
                TaskType.COMBAT -> {
                    val fight = config.fightConfig.value
                    listOfNotNull(fight.toTaskParams(), fight.toRemainingSanityParams())
                }

                TaskType.WAKE_UP -> listOf(config.wakeUpConfig.value.toTaskParams())
                TaskType.RECRUITING -> listOf(config.recruitConfig.value.toTaskParams())
                TaskType.BASE -> listOf(config.infrastConfig.value.toTaskParams())
                TaskType.MALL -> listOf(config.mallConfig.value.toTaskParams())
                TaskType.MISSION -> listOf(config.awardConfig.value.toTaskParams())
                TaskType.AUTO_ROGUELIKE -> listOf(config.roguelikeConfig.value.toTaskParams())
                TaskType.RECLAMATION -> listOf(config.reclamationConfig.value.toTaskParams())
                null -> emptyList()
            }
        }
    }
}