package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.repository.DepotRepository
import com.aliothmoon.maameow.data.resource.ItemHelper
import com.aliothmoon.maameow.domain.models.DropTarget
import com.aliothmoon.maameow.manager.RemoteServiceManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * 指定掉落目标库存的运行时重算。
 *
 * 任务开始（TaskChainStart）时用最新库存重新计算缺口，
 * 通过 AsstSetTaskParams 改写正在排队的 Fight 任务参数。
 * 库存之所以会在 append 之后变化：同一条链里前序 Fight 的掉落会累加，
 * 且「任务开始前更新库存数据」的仓库识别也排在 append 之后。
 *
 * 迁移自 WPF FightSettingsUserControlModel.RefreshFightTaskDrops
 * （上游由 AsstProxy.OnTaskStatusChanged → InProgress 触发，进程内同步）。
 *
 * **时序是 best-effort，不是保证**：MaaCoreCallback 是 oneway，回调发出后 MaaCore 侧
 * 不会等待，本次改写还要经「核心进程 → App 进程」和「App 进程 → 核心进程」两跳 binder。
 * 正常情况下远早于 core 打完第一场（掉落判定在进关卡之后），
 * 万一没赶上，任务只是按 append 期的缺口执行 —— 会多刷，但不会刷错。
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
     * 在 MaaCore 回调线程上调用。
     *
     * @param setTaskParams 改写任务参数的 IPC 调用；返回 false 表示下发失败。
     *   默认走 [sendTaskParams]，单测传入假实现即可，无需 Koin。
     * @return 重算结果，由调用方负责写会话日志（本类无 Android Context，便于单测）
     */
    fun onTaskStarted(
        taskId: Int,
        setTaskParams: (Int, String) -> Boolean = ::sendTaskParams,
    ): RefreshOutcome {
        val t = registry[taskId] ?: return RefreshOutcome.Skipped
        if (t.dropId.isBlank() || t.dropCount <= 0) return RefreshOutcome.Skipped

        val current = depotRepository.countOf(t.dropId)
        val need = t.dropCount - current
        val dropName = itemHelper.getItemInfo(t.dropId)?.name ?: t.dropId

        val ok = setTaskParams(taskId, t.toFightParamsJson(need))
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

    /**
     * 运行中改写已排队任务的参数（MaaCore AsstSetTaskParams）。
     *
     * 直接走 [RemoteServiceManager]，不经 MaaCompositionService —— 后者会与
     * `MaaCompositionService → CallbackDispatcher → TaskChainHandler` 构成构造环，
     * 只能靠服务定位懒解析绕开，而本类需要的其实只是这一次 IPC。
     * 调用方在 MaaCore 回调线程上同步执行，本方法内不得再切线程。
     */
    private fun sendTaskParams(taskId: Int, params: String): Boolean {
        val maa = RemoteServiceManager.getInstanceOrNull()?.maaCoreService ?: run {
            Timber.w("SetTaskParams 时 MaaCore 服务不可用，taskId=%d", taskId)
            return false
        }
        return runCatching { maa.SetTaskParams(taskId, params) }
            .onFailure { Timber.e(it, "SetTaskParams 失败 taskId=%d", taskId) }
            .getOrDefault(false)
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
