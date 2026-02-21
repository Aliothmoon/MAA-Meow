package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.maa.AsstMsg
import timber.log.Timber

/**
 * SubTask 级别回调处理器
 * 处理 SubTaskError(20000)、SubTaskStart(20001)、SubTaskCompleted(20002)、SubTaskExtraInfo(20003)
 */
class SubTaskHandler(
    applicationContext: Context,
    private val runtimeLogCenter: RuntimeLogCenter
) {
    private val resources = applicationContext.resources
    private val packageName = applicationContext.packageName

    /**
     * 主分发方法
     */
    fun handle(msg: AsstMsg, details: JSONObject) {
        when (msg) {
            AsstMsg.SubTaskError -> handleError(details)
            AsstMsg.SubTaskStart -> handleStart(details)
            AsstMsg.SubTaskCompleted -> handleCompleted(details)
            AsstMsg.SubTaskExtraInfo -> handleExtraInfo(details)
            else -> Timber.w("SubTaskHandler received unexpected msg: $msg")
        }
    }

    // ==================== SubTaskError (20000) ====================

    private fun handleError(details: JSONObject) {
        val subtask = details.getString("subtask") ?: return

        when (subtask) {
            "StartGameTask" -> {
                append(str("FailedToOpenClient"), LogLevel.ERROR)
            }

            "StopGameTask" -> {
                append(str("CloseArknightsFailed"), LogLevel.ERROR)
            }

            "AutoRecruitTask" -> {
                val why = details.getString("why") ?: str("ErrorOccurred")
                append("$why, ${str("HasReturned")}", LogLevel.ERROR)
            }

            "RecognizeDrops" -> {
                append(str("DropRecognitionError"), LogLevel.ERROR)
            }

            "ReportToPenguinStats" -> {
                val why = details.getString("why") ?: ""
                append("$why, ${str("GiveUpUploadingPenguins")}", LogLevel.WARNING)
            }

            "CheckStageValid" -> {
                append(str("TheEx"), LogLevel.ERROR)
            }

            "BattleFormationTask" -> {
                append(str("MissingOperators"), LogLevel.ERROR)
            }

            "CopilotTask" -> {
                val innerDetails = details.getJSONObject("details")
                val what = innerDetails?.getString("what")
                if (what == "UserAdditionalOperInvalid") {
                    val name = innerDetails.getString("name") ?: ""
                    append(str("CopilotUserAdditionalNameInvalid", name), LogLevel.ERROR)
                }
            }

            else -> {
                Timber.d("SubTaskError unhandled subtask=$subtask")
            }
        }
    }

    // ==================== SubTaskStart (20001) ====================

    private fun handleStart(details: JSONObject) {
        val subtask = details.getString("subtask") ?: return

        when (subtask) {
            "ProcessTask" -> {
                val innerDetails = details.getJSONObject("details")
                val task = innerDetails?.getString("task") ?: return
                handleProcessTaskStart(task, innerDetails, details)
            }

            "CombatRecordRecognitionTask" -> {
                val what = details.getString("what") ?: return
                runtimeLogCenter.append(what, LogLevel.MESSAGE)
            }

            else -> {
                // 其他 subtask 不处理
            }
        }
    }

    private fun handleProcessTaskStart(
        task: String,
        innerDetails: JSONObject?,
        details: JSONObject
    ) {
        when (task) {
            "StartButton2", "AnnihilationConfirm" -> {
                append(str("MissionStart"), LogLevel.INFO)
            }

            "StoneConfirm" -> {
                val times = innerDetails?.getIntValue("exec_times") ?: 0
                append("${str("StoneUsed")} $times ${str("UnitTime")}", LogLevel.INFO)
            }

            "AbandonAction" -> {
                append(str("ActingCommandError"), LogLevel.ERROR)
            }

            "FightMissionFailedAndStop" -> {
                append(str("FightMissionFailedAndStop"), LogLevel.ERROR)
            }

            "RecruitRefreshConfirm" -> {
                append(str("LabelsRefreshed"), LogLevel.INFO)
            }

            "RecruitConfirm" -> {
                append(str("RecruitConfirm"), LogLevel.INFO)
            }

            "InfrastDormDoubleConfirmButton" -> {
                append(str("InfrastDormDoubleConfirmed"), LogLevel.ERROR)
            }

            "ExitThenAbandon" -> {
                append(str("ExplorationAbandoned"), LogLevel.ROGUELIKE_ABANDON)
            }

            "MissionCompletedFlag" -> {
                append(str("FightCompleted"), LogLevel.ROGUELIKE_SUCCESS)
            }

            "MissionFailedFlag" -> {
                append(str("FightFailed"), LogLevel.ERROR)
            }

            "StageTrader" -> {
                append(str("Trader"), LogLevel.INFO)
            }

            "StageSafeHouse" -> {
                append(str("SafeHouse"), LogLevel.INFO)
            }

            "StageFilterTruth" -> {
                append(str("FilterTruth"), LogLevel.INFO)
            }

            "StageCombatOps" -> {
                append(str("CombatOps"), LogLevel.ROGUELIKE_COMBAT)
            }

            "StageEmergencyOps" -> {
                append(str("EmergencyOps"), LogLevel.ROGUELIKE_EMERGENCY)
            }

            "StageDreadfulFoe", "StageDreadfulFoe-5" -> {
                append(str("DreadfulFoe"), LogLevel.ROGUELIKE_BOSS)
            }

            "StageTraderInvestSystemFull" -> {
                append(str("UpperLimit"), LogLevel.INFO)
            }

            "OfflineConfirm" -> {
                append(str("GameDrop"), LogLevel.WARNING)
            }

            "GamePass" -> {
                append(str("RoguelikeGamePass"), LogLevel.RARE)
            }

            "StageTraderSpecialShoppingAfterRefresh" -> {
                append(str("RoguelikeSpecialItemBought"), LogLevel.RARE)
            }

            "DeepExplorationNotUnlockedComplain" -> {
                append(str("DeepExplorationNotUnlockedComplain"), LogLevel.WARNING)
            }

            "PNS-Resume" -> {
                append(str("ReclamationPnsModeError"), LogLevel.ERROR)
            }

            "PIS-Commence" -> {
                append(str("ReclamationPisModeError"), LogLevel.ERROR)
            }

            "BattleStartAll" -> {
                append(str("MissionStart"), LogLevel.INFO)
            }

            "StageDrops-Stars-3", "StageDrops-Stars-Adverse" -> {
                append(str("CompleteCombat"), LogLevel.INFO)
            }

            else -> {
                // 大量 ProcessTask task 不需要日志
            }
        }
    }

    // ==================== SubTaskCompleted (20002) ====================

    private fun handleCompleted(details: JSONObject) {
        val subtask = details.getString("subtask") ?: return

        if (subtask == "ProcessTask") {
            val taskchain = details.getString("taskchain")
            val innerDetails = details.getJSONObject("details")
            val task = innerDetails?.getString("task")

            when {
                taskchain == "Infrast" && task == "UnlockClues" -> {
                    append(str("ClueExchangeUnlocked"), LogLevel.TRACE)
                }

                taskchain == "Roguelike" && task == "StartExplore" -> {
                    val times = innerDetails.getIntValue("exec_times", 0)
                    append("${str("BegunToExplore")} $times ${str("UnitTime")}", LogLevel.INFO)
                }

                taskchain == "Mall" && task == "EndOfActionThenStop" -> {
                    append("${str("CompleteTask")}${str("CreditFight")}", LogLevel.TRACE)
                }

                taskchain == "Mall" && (task == "VisitLimited" || task == "VisitNextBlack") -> {
                    append("${str("CompleteTask")}${str("Visiting")}", LogLevel.TRACE)
                }
            }
        }
    }

    // ==================== SubTaskExtraInfo (20003) ====================

    private fun handleExtraInfo(details: JSONObject) {
        val what = details.getString("what") ?: return
        val subDetails = details.getJSONObject("details")

        when (what) {
            "StageDrops" -> handleStageDrops(subDetails)
            "StageInfoError" -> append(str("StageInfoError"), LogLevel.ERROR)
            "StageQueueUnableToAgent" -> {
                val code = subDetails?.getString("stage_code") ?: ""
                append("${str("StageQueue")} $code ${str("UnableToAgent")}", LogLevel.INFO)
            }

            "StageQueueMissionCompleted" -> {
                val code = subDetails?.getString("stage_code") ?: ""
                val stars = subDetails?.getIntValue("stars") ?: 0
                append("${str("StageQueue")} $code - $stars ★", LogLevel.INFO)
            }

            "EnterFacility" -> {
                val facility = subDetails?.getString("facility") ?: ""
                val index = (subDetails?.getIntValue("index") ?: 0) + 1
                append(
                    "${str("ThisFacility")}${str(facility)} ${String.format("%02d", index)}",
                    LogLevel.TRACE
                )
            }

            "ProductIncorrect" -> append(str("ProductIncorrect"), LogLevel.ERROR)
            "ProductUnknown" -> append(str("ProductUnknown"), LogLevel.ERROR)
            "ProductChanged" -> append(str("ProductChanged"), LogLevel.INFO)
            "CustomInfrastRoomGroupsMatch" -> {
                val group = subDetails?.getString("group") ?: ""
                append("${str("RoomGroupsMatch")}$group", LogLevel.TRACE)
            }

            "CustomInfrastRoomGroupsMatchFailed" -> {
                val groups = subDetails?.getJSONArray("groups")?.joinToString(", ") ?: ""
                append("${str("RoomGroupsMatchFailed")}$groups", LogLevel.TRACE)
            }

            "CustomInfrastRoomOperators" -> {
                val names = subDetails?.getJSONArray("names")?.joinToString(", ") ?: ""
                append("${str("RoomOperators")}$names", LogLevel.TRACE)
            }

            "InfrastTrainingIdle" -> append(str("TrainingIdle"), LogLevel.TRACE)
            "InfrastTrainingCompleted" -> handleInfrastTrainingCompleted(subDetails)
            "InfrastTrainingTimeLeft" -> handleInfrastTrainingTimeLeft(subDetails)
            "RecruitTagsDetected" -> {
                val tags = subDetails?.getJSONArray("tags")?.joinToString("\n") ?: ""
                append("${str("RecruitingResults")}\n$tags", LogLevel.TRACE)
            }

            "RecruitResult" -> {
                val level = subDetails?.getIntValue("level") ?: 0
                append("$level ★", if (level >= 5) LogLevel.RARE else LogLevel.INFO)
            }

            "RecruitSupportOperator" -> {
                val name = subDetails?.getString("name") ?: ""
                append(str("RecruitSupportOperator", name), LogLevel.INFO)
            }

            "RecruitTagsSelected" -> {
                val tags = subDetails?.getJSONArray("tags")?.joinToString("\n") ?: str("NoDrop")
                append("${str("Choose")} Tags：\n$tags", LogLevel.TRACE)
            }

            "RecruitTagsRefreshed" -> {
                val count = subDetails?.getIntValue("count") ?: 0
                append("${str("Refreshed")}$count${str("UnitTime")}", LogLevel.TRACE)
            }

            "RecruitNoPermit" -> {
                val cont = subDetails?.getBooleanValue("continue") ?: false
                append(str(if (cont) "ContinueRefresh" else "NoRecruitmentPermit"), LogLevel.TRACE)
            }

            "NotEnoughStaff" -> append(str("NotEnoughStaff"), LogLevel.ERROR)
            "CreditFullOnlyBuyDiscount" -> {
                val credit = subDetails?.getString("credit") ?: ""
                append("${str("CreditFullOnlyBuyDiscount")}$credit", LogLevel.MESSAGE)
            }

            "StageInfo" -> {
                val name = subDetails?.getString("name") ?: ""
                append("${str("StartCombat")}$name", LogLevel.TRACE)
            }

            "UseMedicine" -> handleUseMedicine(subDetails)
            "ReclamationReport" -> handleReclamationReport(subDetails)
            "ReclamationProcedureStart" -> {
                val times = subDetails?.getIntValue("times") ?: 0
                append("${str("MissionStart")} $times ${str("UnitTime")}", LogLevel.INFO)
            }

            "ReclamationSmeltGold" -> {
                val times = subDetails?.getIntValue("times") ?: 0
                append("${str("AlgorithmDoneSmeltGold")} $times ${str("UnitTime")}", LogLevel.TRACE)
            }

            "BattleFormation" -> {
                val formation = subDetails?.getJSONArray("formation")?.joinToString(", ") ?: ""
                append("${str("BattleFormation")}\n[$formation]", LogLevel.TRACE)
            }

            "BattleFormationParseFailed" -> append(
                str("BattleFormationParseFailed"),
                LogLevel.TRACE
            )

            "BattleFormationSelected" -> {
                val selected = subDetails?.getString("selected") ?: ""
                append("${str("BattleFormationSelected")}$selected", LogLevel.TRACE)
            }

            "BattleFormationOperUnavailable" -> {
                val name = subDetails?.getString("oper_name") ?: ""
                val reqType = subDetails?.getString("requirement_type") ?: ""
                append(str("BattleFormationOperUnavailable", name, reqType), LogLevel.ERROR)
            }

            "CopilotAction" -> handleCopilotAction(subDetails)
            "SSSStage" -> {
                val stage = subDetails?.getString("stage") ?: ""
                append(str("CurrentStage", stage), LogLevel.INFO)
            }

            "SSSSettlement" -> {
                val why = details.getString("why") ?: ""
                append(why, LogLevel.INFO)
            }

            "SSSGamePass" -> append(str("SSSGamePass"), LogLevel.RARE)
            "UnsupportedLevel" -> {
                val level = subDetails?.getString("level") ?: ""
                append("${str("UnsupportedLevel")}$level", LogLevel.ERROR)
            }

            else -> {
                Timber.d("SubTaskExtraInfo unhandled what=$what")
            }
        }
    }

    // ==================== 辅助格式化方法 ====================

    private fun handleStageDrops(subDetails: JSONObject?) {
        val stageCode = subDetails?.getJSONObject("stage")?.getString("stageCode") ?: ""
        val stats = subDetails?.getJSONArray("stats")
        val curTimes = subDetails?.getIntValue("cur_times") ?: -1
        val sb = StringBuilder("$stageCode ${str("TotalDrop")}\n")

        if (stats == null || stats.isEmpty()) {
            sb.append(str("NoDrop"))
        } else {
            for (i in 0 until stats.size) {
                val item = stats.getJSONObject(i)
                val itemName = item.getString("itemName") ?: ""
                val displayName = if (itemName == "furni") str("FurnitureDrop") else itemName
                val quantity = item.getIntValue("quantity")
                val addQuantity = item.getIntValue("addQuantity")
                sb.append("$displayName : $quantity")
                if (addQuantity > 0) sb.append(" (+$addQuantity)")
                if (i < stats.size - 1) sb.append("\n")
            }
        }

        if (curTimes >= 0) sb.append("\n${str("CurTimes")} : $curTimes")
        runtimeLogCenter.append(sb.toString(), LogLevel.TRACE)
    }

    private fun handleInfrastTrainingCompleted(subDetails: JSONObject?) {
        val operator = subDetails?.getString("operator") ?: ""
        val skill = subDetails?.getString("skill") ?: ""
        val level = subDetails?.getIntValue("level") ?: 0
        append(
            "[$operator] $skill\n${str("TrainingLevel")}: $level ${str("TrainingCompleted")}",
            LogLevel.INFO
        )
    }

    private fun handleInfrastTrainingTimeLeft(subDetails: JSONObject?) {
        val operator = subDetails?.getString("operator") ?: ""
        val skill = subDetails?.getString("skill") ?: ""
        val level = subDetails?.getIntValue("level") ?: 0
        val time = subDetails?.getString("time") ?: ""
        append(
            "[$operator] $skill\n${str("TrainingLevel")}: $level\n${str("TrainingTimeLeft")}: $time",
            LogLevel.INFO
        )
    }

    private fun handleUseMedicine(subDetails: JSONObject?) {
        val count = subDetails?.getIntValue("count") ?: 0
        val isExpiring = subDetails?.getBooleanValue("is_expiring") ?: false

        when {
            count == -1 -> append("${str("MedicineUsed")} Unknown times", LogLevel.ERROR)
            isExpiring -> append("${str("ExpiringMedicineUsed")} (+$count)", LogLevel.INFO)
            else -> append("${str("MedicineUsed")} (+$count)", LogLevel.INFO)
        }
    }

    private fun handleReclamationReport(subDetails: JSONObject?) {
        val totalBadges = subDetails?.getIntValue("total_badges") ?: 0
        val badges = subDetails?.getIntValue("badges") ?: 0
        val totalCp = subDetails?.getIntValue("total_construction_points") ?: 0
        val cp = subDetails?.getIntValue("construction_points") ?: 0
        append(
            "${str("AlgorithmFinish")}\n${str("AlgorithmBadge")}: $totalBadges(+$badges)\n${str("AlgorithmConstructionPoint")}: $totalCp(+$cp)",
            LogLevel.TRACE
        )
    }

    private fun handleCopilotAction(subDetails: JSONObject?) {
        val doc = subDetails?.getString("doc")
        if (doc != null && doc.isNotEmpty()) {
            append(doc, LogLevel.MESSAGE)
        } else {
            val action = subDetails?.getString("action") ?: ""
            val target = subDetails?.getString("target") ?: ""
            append(str("CurrentSteps", str(action), target), LogLevel.TRACE)
        }

        val elapsedTime = subDetails?.getIntValue("elapsed_time") ?: -1
        if (elapsedTime >= 0) {
            append(str("ElapsedTime", elapsedTime), LogLevel.MESSAGE)
        }
    }

    // ==================== 字符串资源辅助方法 ====================

    private fun str(key: String): String = MaaStringRes.getString(resources, packageName, key)

    private fun str(key: String, vararg args: Any): String =
        MaaStringRes.getString(resources, packageName, key, *args)

    private fun append(content: String, level: LogLevel) {
        runtimeLogCenter.append(content, level)
    }
}
