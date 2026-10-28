package com.aliothmoon.maameow.maa.callback

import android.content.Context
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.OperProgressResult
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ResourceDataManager
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber

class OperProgressHandler(
    private val context: Context,
    private val logger: MaaSessionLogger,
    private val tracker: TaskChainStatusTracker,
    private val chainState: TaskChainState,
    private val resourceDataManager: ResourceDataManager,
    private val appSettings: AppSettingsManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private data class Update(val nodeId: String, val results: List<OperProgressResult>, val removeFinished: Boolean)
    private val updates = Channel<Update>(Channel.UNLIMITED)

    init {
        // 串行写回，汇总不能越过尚未处理的明细
        scope.launch {
            for (update in updates) {
                try {
                    chainState.recordOperProgress(update.nodeId, update.results, update.removeFinished)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.e(error, "Failed to save operator progress")
                }
            }
        }
    }

    fun onExtraInfo(message: JSONObject) {
        val details = message.getJSONObject("details") ?: return
        val taskId = message.getIntValue("taskid", 0)
        // 在 tracker 清理前取得归属，异步写回不再查询运行状态
        val nodeId = tracker.getNodeId(taskId)
        when (message.getString("what")) {
            "OperProgressDetail" -> {
                val result = parseResult(details) ?: return
                val status = details.getString("result") ?: return
                val reason = details.getString("result_detail").orEmpty()
                val training = status == "Success" && result.skillMastery != null &&
                    reason in setOf("Completed", "PrerequisiteTraining")
                if (status == "Success" && nodeId != null) {
                    updates.trySend(Update(nodeId, listOf(result.copy(trainingStarted = training)), false))
                }
                val statusRes = when {
                    training -> R.string.oper_progress_result_training
                    reason == "AlreadySatisfied" -> R.string.oper_progress_result_satisfied
                    reason == "ResourceInsufficient" -> R.string.oper_progress_result_resources
                    reason == "OperatorNotFound" -> R.string.oper_progress_result_not_found
                    reason == "PrerequisiteNotMet" -> R.string.oper_progress_result_prerequisite
                    reason == "ChipNotCraftable" -> R.string.oper_progress_result_chip
                    reason == "FormulaLocked" -> R.string.oper_progress_result_formula
                    reason == "TrainingRoomBusy" -> R.string.oper_progress_result_busy
                    reason == "Interrupt" -> R.string.oper_progress_result_interrupted
                    status == "Success" -> R.string.oper_progress_result_completed
                    status == "Skipped" -> R.string.oper_progress_result_skipped
                    else -> R.string.oper_progress_result_recognition
                }
                val targets = buildList {
                    result.elite?.let { add(context.getString(R.string.oper_progress_elite_target, it)) }
                    result.skillLevel?.let { add(context.getString(R.string.oper_progress_skill_target, it)) }
                    result.skillMastery?.forEachIndexed { index, level ->
                        if (level > 0) add(context.getString(R.string.oper_progress_mastery_target, index + 1, level))
                    }
                }
                val name = resourceDataManager.getLocalizedCharacterName(result.name, appSettings.displayLanguage)
                    ?: result.name
                logger.append(
                    context.getString(R.string.oper_progress_log_detail, name, context.getString(statusRes),
                        if (targets.isEmpty()) "" else "\n" + targets.joinToString(" · ")),
                    when (status) {
                        "Success" -> if (training) LogLevel.INFO else LogLevel.SUCCESS
                        "Skipped" -> LogLevel.WARNING
                        else -> LogLevel.ERROR
                    },
                )
            }
            "OperProgressSummary" -> {
                val success = integer(details["success"])?.takeIf { it >= 0 } ?: return
                val failed = integer(details["failed"])?.takeIf { it >= 0 } ?: return
                val skipped = integer(details["skipped"])?.takeIf { it >= 0 } ?: return
                val finished = details.getJSONArray("finished")?.mapNotNull { item ->
                    (item as? JSONObject)?.let(::parseResult)?.copy(skillMastery = null)
                }.orEmpty()
                // finished 无法区分专精已启动和已完成，专精只消费明细
                if (nodeId != null) updates.trySend(Update(nodeId, finished, true))
                logger.append(context.getString(R.string.oper_progress_log_summary, success, failed, skipped),
                    if (failed > 0) LogLevel.WARNING else LogLevel.INFO)
            }
        }
    }

    private fun parseResult(obj: JSONObject): OperProgressResult? {
        val name = (obj["name"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val role = (obj["role"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val mastery = (obj["skill_mastery"] as? JSONArray)?.takeIf { it.size == 3 }?.let { array ->
            array.map { integer(it)?.takeIf { level -> level in 0..3 } ?: return null }
        }
        return OperProgressResult(
            name, role,
            elite = integer(obj["elite"])?.takeIf { it in 1..2 },
            skillLevel = integer(obj["skill_level"])?.takeIf { it in 2..7 },
            skillMastery = mastery,
        )
    }

    private fun integer(value: Any?): Int? = (value as? Number)?.let {
        it.toInt().takeIf { number -> number.toDouble() == it.toDouble() }
    }
}
