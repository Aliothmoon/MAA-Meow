package com.aliothmoon.maameow.data.achievement

fun buildAchievementStates(
    records: Map<String, AchievementRecord>,
    definitions: Collection<AchievementDefinition>,
): List<AchievementState> = definitions
    .filter { definition ->
        definition.allTriggers().isNotEmpty() || records.containsKey(definition.id)
    }
    .map { definition ->
        val record = records[definition.id]
        AchievementState(
            definition = definition,
            unlocked = record?.unlocked == true,
            unlockedAtMillis = record?.unlockedAtMillis,
            progress = record?.progress ?: 0,
        )
    }
    .sortedWith(
        compareByDescending<AchievementState> { it.unlocked }
            .thenBy { it.definition.category.ordinal }
            .thenBy { it.definition.group }
            .thenBy { it.definition.groupIndex }
            .thenBy { it.definition.id }
    )

/** 一条成就的全部触发器(单 trigger + triggers 列表合并)。 */
fun AchievementDefinition.allTriggers(): List<AchievementTrigger> = buildList {
    trigger?.let(::add)
    addAll(triggers)
}
