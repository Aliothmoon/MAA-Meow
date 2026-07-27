package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.domain.models.DropTarget
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 指定掉落目标库存的运行时重算。
 *
 * 任务开始（TaskChainStart）时用最新库存重新计算缺口，
 * 通过 AsstSetTaskParams 改写正在排队的 Fight 任务参数。
 *
 * 迁移自 WPF FightSettingsUserControlModel.RefreshFightTaskDrops。
 *
 * [setTaskParams] 作为函数参数传入，避免本类持有 [MaaCompositionService]
 * 造成构造循环；调用方 [com.aliothmoon.maameow.maa.callback.TaskChainHandler]
 * 在回调线程同步调用，必须在 core 进入关卡前把参数改完。
 */
class FightDropsRefresher(
    private val depotRepository: DepotRepository,
    private val itemHelper: ItemHelper,
) {
    private val registry = ConcurrentHashMap<Int, DropTarget>()

    /** 由 MaaCompositionService 在 AppendTask 拿到真实 taskId 后调用 */
    fun register(taskId: Int, target: DropTarget) {
        if (taskId > 0) registry[taskId] = target
    }

    fun clear() = registry.clear()

    /**
     * 在 MaaCore 回调线程上同步调用。
     *
     * @param setTaskParams 改写任务参数的 IPC 调用；返回 false 表示下发失败
     * @return 重算结果，由调用方负责写会话日志（本类无 Android Context，便于单测）
     */
    fun onTaskStarted(
        taskId: Int,
        setTaskParams: (Int, String) -> Boolean,
    ): RefreshOutcome {
        val t = registry[taskId] ?: return RefreshOutcome.Skipped
        if (t.dropId.isBlank() || t.dropCount <= 0) return RefreshOutcome.Skipped

        val current = depotRepository.countOf(t.dropId)
        val need = t.dropCount - current
        val dropName = itemHelper.getItemInfo(t.dropId)?.name ?: t.dropId

        // SetTaskParams 是整表重放：必须把 append 时的字段一并带上，否则会冲成 core 默认值
        val json = buildJsonObject {
            put("stage", t.stage)
            put("times", if (need <= 0) 0 else Int.MAX_VALUE)
            put("medicine", t.medicine)
            put("stone", t.stone)
            put("series", t.series)
            t.medicineExpireDays?.let { put("medicine_expire_days", it) }
            if (t.drGrandet) put("DrGrandet", true)
            // 已充足时 drops 仍带 {id:1}：core 需要非空 drops 结构，times=0 才是真正的止损
            put("drops", buildJsonObject {
                put(t.dropId, if (need <= 0) 1 else need)
            })
        }

        val ok = setTaskParams(taskId, json.toString())
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
