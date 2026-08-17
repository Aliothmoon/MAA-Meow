package com.aliothmoon.maameow.data.resource

import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.data.config.MaaPathConfig
import timber.log.Timber
import java.io.File

/**
 * 关卡理智消耗查询，数据来自 resource/stages.json
 *
 * 变更检测依据 version.json 的 last_updated：资源合并成功才写它，
 * 文件 mtime 会随解压保留原始时间戳，不能当变更信号
 */
class StageApCostRepository(
    private val pathConfig: MaaPathConfig,
) {
    private val lock = Any()
    private var apCosts: Map<String, Int>? = null
    private var loadedVersion: String? = null

    /** 关卡单次进图理智消耗；关卡不在表里（手输活动关、复刻前缀等）返回 null */
    fun getApCost(stage: String): Int? {
        if (stage.isBlank()) return null
        synchronized(lock) {
            val version = pathConfig.readDiskResourceVersion()
            if (apCosts == null || version != loadedVersion) {
                loadedVersion = version
                apCosts = load(File(pathConfig.resourceDir, STAGES_FILE))
            }
            return apCosts?.get(stage)
        }
    }

    private fun load(file: File): Map<String, Int> {
        if (!file.exists()) {
            Timber.w("stages.json 不存在: %s", file.absolutePath)
            return emptyMap()
        }
        return runCatching {
            val result = HashMap<String, Int>()
            JSON.parseArray(file.readText()).forEach { item ->
                val obj = item as? com.alibaba.fastjson2.JSONObject ?: return@forEach
                val code = obj.getString("code")
                val apCost = obj.getIntValue("apCost", 0)
                if (!code.isNullOrBlank() && apCost > 0) {
                    result[code] = apCost
                }
            }
            Timber.i("已加载 %d 个关卡的理智消耗", result.size)
            result as Map<String, Int>
        }.getOrElse {
            Timber.e(it, "解析 stages.json 失败")
            emptyMap()
        }
    }

    private companion object {
        const val STAGES_FILE = "stages.json"
    }
}
