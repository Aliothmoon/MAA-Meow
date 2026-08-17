package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.data.resource.StageApCostHelper
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.manager.RemoteServiceManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * 目标库存运行时重算：stage(slot) → bind(taskId) → onTaskStarted SetTaskParams。
 * 直接走 [RemoteServiceManager] 改参，避免 Composition 构造环；回调线程同步执行。
 */
class FightDropsRefresher(
    private val depotRepository: DepotRepository,
    private val itemHelper: ItemHelper,
    private val stageApCostHelper: StageApCostHelper,
    private val subTaskHandler: SubTaskHandler,
) {
    private val targets = ConcurrentHashMap<TaskSlot, DropTarget>()
    private val registry = ConcurrentHashMap<Int, TaskSlot>()

    /**
     * 已被证明「窗口内临期药已用完」的天数上限，本次会话内只增不减
     *
     * 目标库存任务下发的是 times=MAX，正常结束却没达标只可能是理智耗尽，
     * 且结束前已经把窗口内的临期药用光了；后续任务据此判断能否直接跳过
     */
    @Volatile
    private var provenExhaustedMedicineDays = 0

    fun stage(slot: TaskSlot, target: DropTarget) {
        targets[slot] = target
    }

    /** 未 stage 的 slot（普通 FIGHT）直接 no-op。 */
    fun bind(slot: TaskSlot, taskId: Int) {
        if (taskId <= 0) return
        if (!targets.containsKey(slot)) return
        registry[taskId] = slot
    }

    fun clear() {
        targets.clear()
        registry.clear()
        provenExhaustedMedicineDays = 0
    }

    /** 目标库存任务正常结束却没达标 → 记下它的临期药窗口已耗尽 */
    fun onTaskCompleted(taskId: Int) {
        val slot = registry[taskId] ?: return
        val t = targets[slot] ?: return
        val expireDays = t.medicineExpireDays ?: 0
        if (expireDays <= 0 || t.dropId.isBlank() || t.dropCount <= 0) return
        // 已达标说明结束原因与理智无关，不构成耗尽证明
        if (depotRepository.countOf(t.dropId) >= t.dropCount) return
        if (expireDays > provenExhaustedMedicineDays) {
            provenExhaustedMedicineDays = expireDays
            Timber.i("临期药窗口已证明耗尽：%d 天（来自 %s）", expireDays, t.logLabel)
        }
    }

    /**
     * 理智不足且没有药剂/源石预算、临期药窗口也已证明耗尽时，本任务不必再进图
     *
     * 自然回复按 6 分钟 1 点向上取整估算并封顶，估高不估低，长队列后也不会误跳
     */
    private fun estimateSkipForSanity(t: DropTarget): SanityShortfall? {
        if (t.medicine > 0 || t.stone > 0) return null
        if ((t.medicineExpireDays ?: 0) > provenExhaustedMedicineDays) return null
        val snapshot = subTaskHandler.lastSanitySnapshot ?: return null
        val apCost = stageApCostHelper.getApCost(t.stage) ?: run {
            Timber.i("关卡 %s 无理智消耗数据，跳过理智判定", t.stage)
            return null
        }
        val regen = if (snapshot.current < snapshot.max) {
            val elapsedMinutes = (System.currentTimeMillis() - snapshot.reportTimeMillis) / 60_000.0
            maxOf(0, ceil(elapsedMinutes / SANITY_REGEN_MINUTES).toInt())
        } else {
            0
        }
        val estimated = minOf(snapshot.current + regen, snapshot.max)
        return if (estimated < apCost) SanityShortfall(estimated, apCost) else null
    }

    private data class SanityShortfall(val estimatedSanity: Int, val apCost: Int)

    /** MaaCore 回调线程：重算缺口并 SetTaskParams；runlog 由 TaskChainHandler 写。 */
    fun onTaskStarted(taskId: Int): RefreshOutcome {
        val slot = registry[taskId] ?: return RefreshOutcome.Skipped
        val t = targets[slot] ?: return RefreshOutcome.Skipped
        if (t.dropId.isBlank() || t.dropCount <= 0) return RefreshOutcome.Skipped

        val current = depotRepository.countOf(t.dropId)
        val need = t.dropCount - current
        val dropName = itemHelper.getItemInfo(t.dropId)?.name ?: t.dropId
        val shortfall = if (need > 0) estimateSkipForSanity(t) else null
        val paramsJson = t.toFightParamsJson(need, forceSkip = shortfall != null)

        val maa = RemoteServiceManager.getInstanceOrNull()?.maaCoreService
        val ok = if (maa == null) {
            Timber.w("SetTaskParams 时 MaaCore 服务不可用，taskId=%d", taskId)
            false
        } else {
            runCatching { maa.SetTaskParams(taskId, paramsJson) }
                .onFailure { Timber.e(it, "SetTaskParams 失败 taskId=%d", taskId) }
                .getOrDefault(false)
        }
        if (!ok) {
            Timber.w("SetTaskParams 返回 false，taskId=%d，任务将按原参数执行", taskId)
        }

        return if (need <= 0) {
            RefreshOutcome.Sufficient(
                logLabel = t.logLabel,
                dropName = dropName,
                current = current,
                target = t.dropCount,
                applied = ok,
            )
        } else if (shortfall != null) {
            Timber.i(
                "FightTask %d (%s) 理智不足跳过: 预估 %d < 关卡消耗 %d",
                taskId, t.logLabel, shortfall.estimatedSanity, shortfall.apCost,
            )
            RefreshOutcome.SanityInsufficient(
                logLabel = t.logLabel,
                estimatedSanity = shortfall.estimatedSanity,
                apCost = shortfall.apCost,
                applied = ok,
            )
        } else {
            Timber.i(
                "FightTask %d (%s) 重算缺口: %s 需要 %d（当前 %d / 目标 %d），下发%s",
                taskId, t.logLabel, dropName, need, current, t.dropCount,
                if (ok) "成功" else "失败",
            )
            RefreshOutcome.Updated(
                logLabel = t.logLabel,
                dropName = dropName,
                need = need,
                current = current,
                target = t.dropCount,
                applied = ok,
            )
        }
    }

    sealed interface RefreshOutcome {
        data object Skipped : RefreshOutcome

        data class Sufficient(
            val logLabel: String,
            val dropName: String,
            val current: Int,
            val target: Int,
            val applied: Boolean,
        ) : RefreshOutcome

        data class Updated(
            val logLabel: String,
            val dropName: String,
            val need: Int,
            val current: Int,
            val target: Int,
            val applied: Boolean,
        ) : RefreshOutcome

        /** 缺口还在，但理智跑不动，本任务整体跳过 */
        data class SanityInsufficient(
            val logLabel: String,
            val estimatedSanity: Int,
            val apCost: Int,
            val applied: Boolean,
        ) : RefreshOutcome
    }

    private companion object {
        /** 理智自然回复速度：6 分钟 1 点 */
        const val SANITY_REGEN_MINUTES = 6.0
    }
}
