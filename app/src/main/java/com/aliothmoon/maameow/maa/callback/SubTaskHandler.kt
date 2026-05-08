package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.data.model.FightConfig
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaNotificationCenter
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.maa.AsstMsg
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.alibaba.fastjson2.JSONArray
import timber.log.Timber
import java.util.Locale

/**
 * SubTask 级别回调处理器
 * 处理 SubTaskError(20000)、SubTaskStart(20001)、SubTaskCompleted(20002)、SubTaskExtraInfo(20003)
 */
class SubTaskHandler(
    applicationContext: Context,
    private val sessionLogger: MaaSessionLogger,
    private val copilotRuntimeStateStore: CopilotRuntimeStateStore,
    private val resourceDataManager: ResourceDataManager,
    private val toolboxResultCollector: ToolboxResultCollector,
    private val notificationCenter: MaaNotificationCenter,
    private val chainState: TaskChainState,
    private val activityManager: ActivityManager,
) {
    private val resources = applicationContext.resources
    private val packageName = applicationContext.packageName

    // 战斗进度临时暂存（FightTimes/SanityBeforeStage 先于 StartButton2/AnnihilationConfirm 到达）
    private data class PendingFightState(
        val timesFinished: Int? = null,
        val series: Int? = null,
        val sanityCost: Int? = null,
        val sanity: Int? = null,
        val sanityMax: Int? = null,
    )

    private var pendingFight = PendingFightState()

    // 本次会话累计用药数（跨战斗累计，session 开始时重置）
    private var medicineUsedTotal = 0

    // 会话级理智快照（最近一次 SanityBeforeStage），供 AllTasksCompleted 消费
    data class SanitySnapshot(
        val current: Int,
        val max: Int,
        val reportTimeMillis: Long = System.currentTimeMillis(),
    )

    /** 最近一次 SanityBeforeStage 的快照，跨战斗保留，session 结束时清空 */
    var lastSanitySnapshot: SanitySnapshot? = null
        private set

    /** 每次新 session 开始时调用，重置跨任务状态 */
    fun resetSessionState() {
        pendingFight = PendingFightState()
        medicineUsedTotal = 0
        lastSanitySnapshot = null
    }

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
                val innerDetails = details.getJSONObject("details")
                val opers = innerDetails?.getJSONObject("opers")
                if (opers.isNullOrEmpty()) {
                    append(str("MissingOperators"), LogLevel.ERROR)
                } else {
                    val sb = StringBuilder(str("MissingOperators")).append("\n")
                    opers.forEach { (groupName, value) ->
                        val arr = value as? JSONArray
                        if (arr == null || arr.size <= 1) {
                            sb.append("$groupName\n")
                        } else {
                            val names = arr.mapNotNull { (it as? JSONObject)?.getString("name") }
                            sb.append("$groupName=> ${names.joinToString(" / ")}\n")
                        }
                    }
                    append(sb.trimEnd().toString(), LogLevel.ERROR)
                }
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
                sessionLogger.append(what, LogLevel.MESSAGE)
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
                val sb = StringBuilder(str("MissionStart"))
                val pf = pendingFight
                if (pf.timesFinished != null) {
                    val series = pf.series ?: 1
                    val times = if (series > 1)
                        "${pf.timesFinished + 1}~${pf.timesFinished + series}"
                    else
                        "${pf.timesFinished + 1}"
                    val cost = pf.sanityCost?.toString() ?: "???"
                    sb.append(" $times ($cost)")
                }
                if (pf.sanity != null && pf.sanityMax != null)
                    sb.append("  ${str("Sanity")}: ${pf.sanity}/${pf.sanityMax}")
                append(sb.toString(), LogLevel.INFO)
                pendingFight = PendingFightState()
            }

            "StoneConfirm" -> {
                val times = innerDetails?.getIntValue("exec_times") ?: 0
                append("${str("StoneUsed")} $times ${str("UnitTime")}", LogLevel.INFO)
            }

            "AbandonAction" -> {
                append(str("ActingCommandError"), LogLevel.ERROR)
            }

            "FightMissionFailedAndStop" -> {
                val message = str("FightMissionFailedAndStop")
                append(message, LogLevel.ERROR)
                notificationCenter.notifySubTaskFailure(message)
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
                copilotRuntimeStateStore.markTaskSuccess()
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

            when (taskchain) {
                "Infrast" if task == "UnlockClues" -> {
                    append(str("ClueExchangeUnlocked"), LogLevel.TRACE)
                }

                "Infrast" if task == "SendClues" -> {
                    append(str("CluesSent"), LogLevel.TRACE)
                }

                "Roguelike" if task == "StartExplore" -> {
                    val times = innerDetails.getIntValue("exec_times", 0)
                    append("${str("BegunToExplore")} $times ${str("UnitTime")}", LogLevel.INFO)
                }

                // 上游 dev-v2 22f3175b4: Core 移除 EndOfActionThenStop 任务,
                // OF-1 信用战完成现在会触发 Copilot@StageDrops-Stars-3
                "Mall" if task == "StageDrops-Stars-3" -> {
                    append("${str("CompleteTask")}${str("CreditFight")}", LogLevel.TRACE)
                }

                "Mall" if (task == "VisitLimited" || task == "VisitNextBlack") -> {
                    append("${str("CompleteTask")}${str("Visiting")}", LogLevel.TRACE)
                }
            }
        }
    }

    // ==================== SubTaskExtraInfo (20003) ====================

    private fun handleExtraInfo(details: JSONObject) {
        // Depot / OperBox 通过 taskchain 路由（与 WPF 一致）
        val taskchain = details.getString("taskchain")
        val subDetails = details.getJSONObject("details")
        when (taskchain) {
            "Depot" -> {
                toolboxResultCollector.onDepotResult(subDetails)
                return
            }

            "OperBox" -> {
                toolboxResultCollector.onOperBoxResult(subDetails)
                return
            }
        }

        val what = details.getString("what") ?: return

        when (what) {
            "FightTimes" -> {
                pendingFight = pendingFight.copy(
                    timesFinished = subDetails?.getIntValue("times_finished"),
                    series = subDetails?.getIntValue("series"),
                    sanityCost = subDetails?.getIntValue("sanity_cost"),
                )
            }

            "SanityBeforeStage" -> {
                val cur = subDetails?.getIntValue("current_sanity")
                val max = subDetails?.getIntValue("max_sanity")
                pendingFight = pendingFight.copy(sanity = cur, sanityMax = max)
                if (cur != null && max != null) {
                    lastSanitySnapshot = SanitySnapshot(cur, max)
                }
            }

            "StageDrops" -> handleStageDrops(subDetails)
            "AccountSwitch" -> {
                val accountName = subDetails?.getString("account_name") ?: ""
                append("${str("AccountSwitch")} -->> $accountName", LogLevel.INFO)
            }

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
                    "${str("ThisFacility")}${str(facility)} ${
                        String.format(
                            Locale.US,
                            "%02d",
                            index
                        )
                    }",
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
                toolboxResultCollector.onRecruitTagsDetected(subDetails)
            }

            "RecruitSpecialTag" -> {
                val tag = subDetails?.getString("tag") ?: ""
                append("${str("RecruitingTips")}\n$tag", LogLevel.RARE)
                notificationCenter.notifyRecruitSpecialTag(tag)
            }

            "RecruitRobotTag" -> {
                val tag = subDetails?.getString("tag") ?: ""
                append("${str("RecruitingTips")}\n$tag", LogLevel.RECRUIT_ROBOT)
                notificationCenter.notifyRecruitRobotTag(tag)
            }

            "RecruitResult" -> {
                val level = subDetails?.getIntValue("level") ?: 0
                val annotatedTooltip = buildRecruitResultTooltip(subDetails)
                sessionLogger.append(
                    LogItem(
                        content = "$level ★ Tags",
                        level = if (level >= 5) LogLevel.RARE else LogLevel.INFO,
                        annotatedTooltip = annotatedTooltip
                    )
                )
                toolboxResultCollector.onRecruitResult(subDetails)
                if (level >= 5) {
                    notificationCenter.notifyRecruitHighRarity(level)
                }
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
                val reason = when (reqType) {
                    "elite" -> "精英化不足"
                    "level" -> "等级不足"
                    "skill_level" -> "技能等级不足"
                    "module" -> "所需模组未解锁"
                    else -> reqType
                }
                append(str("BattleFormationOperUnavailable", name, reason), LogLevel.ERROR)
                copilotRuntimeStateStore.markRequirementIgnored()
            }

            "CopilotAction" -> handleCopilotAction(subDetails)
            "CopilotListLoadTaskFileSuccess" -> {
                val fileName = subDetails?.getString("file_name") ?: ""
                val stageName = subDetails?.getString("stage_name") ?: ""
                append("Parse $fileName[$stageName] Success", LogLevel.INFO)
                copilotRuntimeStateStore.resetRequirementIgnored()
            }

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

        if (curTimes > 0) sb.append("\n${str("CurTimes")} : $curTimes")

        // 剿灭周进度 (v6.9.0+): annihilation_weekly_process = [当前, 上限]
        val annihilationProcess = subDetails?.getJSONArray("annihilation_weekly_process")
        if (annihilationProcess != null && annihilationProcess.size == 2) {
            val cur = annihilationProcess.getIntValue(0)
            val total = annihilationProcess.getIntValue(1)
            if (cur >= 0 && total > 0) {
                sb.append("\n${str("AnnihilationMode")} : $cur / $total")
            }
        }

        sessionLogger.append(sb.toString(), LogLevel.TRACE)
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
        if (count > 0) medicineUsedTotal += count

        when {
            count == -1 -> append("${str("MedicineUsed")} Unknown times", LogLevel.ERROR)
            isExpiring -> {
                // 上游 dev-v2 925ff331a: 回调不带 expire_days, 反查当前 active fight config 计算小时数
                val hours = computeExpireHoursFromActiveConfig()
                val prefix = if (hours > 0) {
                    str("ExpiringMedicineUsedHours", hours)
                } else {
                    str("ExpiringMedicineUsed")
                }
                append("$prefix (+$count, ${str("Total")}: $medicineUsedTotal)", LogLevel.INFO)
            }

            else -> append(
                "${str("MedicineUsed")} (+$count, ${str("Total")}: $medicineUsedTotal)",
                LogLevel.INFO
            )
        }
    }

    /**
     * 反查当前任务链中第一个启用过期药的 FightConfig, 计算最终过期小时数
     * 算法对齐上游 WPF: max(用户配置天数, 活动结束前两天时距本周末天数) * 24
     * 同一时刻通常只有一个 fight 在执行, 此简化是安全的
     */
    private fun computeExpireHoursFromActiveConfig(): Int {
        val fight = chainState.chain.value
            .mapNotNull { it.config as? FightConfig }
            .firstOrNull { it.useExpiringMedicine }
            ?: return 0

        val userDays = fight.medicineExpireDays.coerceIn(1, 7)
        val activityDays = if (fight.useExpireMedicineForActivity) {
            activityManager.getActivityAwareExpireDays()
        } else {
            0
        }
        return maxOf(userDays, activityDays) * 24
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

    // ==================== 公招干员信息解析 ====================

    /**
     * 解析 RecruitResult 回调中的 tag 组合与匹配干员信息，生成带颜色标注的富文本。
     * 参考 WPF ToolboxViewModel.UpdateRecruitResult 逻辑。
     *
     * JSON 结构：details.result = [{ level, tags[], opers[{ id, name, level }] }, ...]
     */
    private fun buildRecruitResultTooltip(details: JSONObject?): AnnotatedString? {
        val resultArray = details?.getJSONArray("result") ?: return null
        if (resultArray.isEmpty()) return null

        val defaultColor = LogLevel.MESSAGE.color

        return buildAnnotatedString {
            for (i in 0 until resultArray.size) {
                val comb = resultArray.getJSONObject(i) ?: continue
                val tagLevel = comb.getIntValue("level")
                val tags = comb.getJSONArray("tags")?.joinToString("  ") ?: ""

                // tag 组合标题行：按组合星级着色
                withStyle(
                    SpanStyle(
                        color = LogLevel.forRecruitStar(tagLevel).color,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("$tagLevel★ Tags:  $tags")
                }

                // 干员列表：星标着色，干员名黑色，每个干员独占一行
                val opers = comb.getJSONArray("opers")
                if (opers != null && opers.isNotEmpty()) {
                    val operList = (0 until opers.size).mapNotNull { j ->
                        val oper = opers.getJSONObject(j) ?: return@mapNotNull null
                        val operName = oper.getString("name") ?: return@mapNotNull null
                        val operLevel = oper.getIntValue("level")
                        val localizedName =
                            resourceDataManager.getLocalizedCharacterName(operName) ?: operName
                        operLevel to localizedName
                    }.sortedByDescending { it.first }

                    for ((star, name) in operList) {
                        append("\n  ")
                        withStyle(SpanStyle(color = LogLevel.forRecruitStar(star).color)) {
                            append("★".repeat(star))
                        }
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(" $name")
                        }
                    }
                }

                if (i < resultArray.size - 1) append("\n\n")
            }
        }.takeIf { it.isNotEmpty() }
    }

    // ==================== 字符串资源辅助方法 ====================

    private fun str(key: String): String = MaaStringRes.getString(resources, packageName, key)

    private fun str(key: String, vararg args: Any): String =
        MaaStringRes.getString(resources, packageName, key, *args)

    private fun append(content: String, level: LogLevel) {
        sessionLogger.append(content, level)
    }
}
