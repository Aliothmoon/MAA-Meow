package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.data.resource.ServerTimezone
import java.time.LocalDate

/**
 * 代理倍率临时锁定。
 *
 * 2026-08-01（鹰历）游戏更新后代理倍率上限由 6 提升至 10，
 * 但 MaaCore 的 FightTimesTaskPlugin 仍硬校验 series ∈ [1, 6]
 * （见 src/MaaCore/Task/Fight/FightTimesTaskPlugin.cpp:73/93/103），
 * 下发 7~10 会导致识别失败、任务中断。
 *
 * 对齐上游 FightSettingsUserControlModel 的
 * 「#region 代理倍率临时锁定（适配后删除）」。
 *
 * TODO: MaaCore 适配 7~10 倍率后，删除本文件及其全部调用点：
 *   - FightConfig.toTaskParams 里的 clientType 钳制分支
 *   - AnalyzeTaskChainUseCase 里的锁定 Warning 日志
 *   - FightConfigPanel.SeriesSection 的 locked 分支与提示文案
 *   - 字符串 panel_fight_series_locked_tip / runlog_series_locked
 */
object SeriesLock {

    private val LOCK_DATE: LocalDate = LocalDate.of(2026, 8, 1)

    /** 仅官服/B服需要锁定；YoStar/txwy 更新节奏不同，倍率仍为 1~6 */
    private val LOCKED_CLIENTS = setOf("Official", "Bilibili")

    fun isLocked(
        clientType: String,
        yjDate: LocalDate = ServerTimezone.getYjDate(clientType),
    ): Boolean = clientType in LOCKED_CLIENTS && !yjDate.isBefore(LOCK_DATE)
}
