package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.maa.task.TaskSlot
import com.aliothmoon.maameow.manager.RemoteServiceManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 目标库存运行时重算：stage(slot) → bind(taskId) → onTaskStarted SetTaskParams。
 * 直接走 [RemoteServiceManager] 改参，避免 Composition 构造环；回调线程同步执行。
 */
class FightDropsRefresher(
    private val depotRepository: DepotRepository,
    private val itemHelper: ItemHelper,
) {
    private val targets = ConcurrentHashMap<TaskSlot, DropTarget>()
    private val registry = ConcurrentHashMap<Int, TaskSlot>()

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
    }

    /** MaaCore 回调线程：重算缺口并 SetTaskParams；runlog 由 TaskChainHandler 写。 */
    fun onTaskStarted(taskId: Int): RefreshOutcome {
        val slot = registry[taskId] ?: return RefreshOutcome.Skipped
        val t = targets[slot] ?: return RefreshOutcome.Skipped
        if (t.dropId.isBlank() || t.dropCount <= 0) return RefreshOutcome.Skipped

        val current = depotRepository.countOf(t.dropId)
        val need = t.dropCount - current
        val dropName = itemHelper.getItemInfo(t.dropId)?.name ?: t.dropId
        val paramsJson = t.toFightParamsJson(need)

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
    }
}
