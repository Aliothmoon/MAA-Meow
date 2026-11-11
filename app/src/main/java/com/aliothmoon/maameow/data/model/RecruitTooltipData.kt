package com.aliothmoon.maameow.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecruitCombination(
    val starLevel: Int,
    val tags: List<String>,
    /** 已按星级倒序 */
    val opers: List<RecruitOper>
)

@Immutable
data class RecruitOper(
    val starLevel: Int,
    val name: String
)

@Immutable
data class RecruitTagsLog(
    val label: String,
    val tags: List<String>,
    val kind: RecruitTagsKind,
)

enum class RecruitTagsKind { DETECTED, SELECTED, SPECIAL, ROBOT }
