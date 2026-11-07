package com.aliothmoon.maameow.data.resource

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

/**
 * 企鹅物流期望掉率：按「关卡 code × 材料 id」查 quantity / times。
 *
 * 数据来自两个公开接口：
 * - `/PenguinStats/api/v2/stages`：code ↔ stageId（如 `PR-C-1` ↔ `pro_c_1`），matrix 只有 stageId
 * - `/PenguinStats/api/v2/result/matrix?server=X`：`(stageId,itemId) → quantity/times`
 *
 * 两者合并后按 server 缓存到磁盘（默认 24h），无网 / 解析失败 / 组合无样本一律返回 null，
 * 调用方据此回退逐场刷。[prepare] 是 suspend，必须在展开任务链之前完成（toTaskParams 同步，不能联网）。
 */
class PenguinDropRateProvider(
    appContext: Context,
    private val httpClient: OkHttpClient,
) {
    private val cacheDir = appContext.filesDir

    private val stateLock = Any()
    private val fetchMutex = Mutex()

    /** key = "code|itemId"，value = 单次进图的期望掉落个数 */
    private var rates: Map<String, Double>? = null
    private var loadedServer: String? = null
    private var fetchedAt: Long = 0L

    /**
     * 确保指定服务器的掉率已加载。过期或缺失时联网刷新；
     * 刷新失败但磁盘有旧缓存则继续用旧数据。全程不抛异常。
     */
    suspend fun prepare(server: String) {
        if (isFresh(server)) return
        fetchMutex.withLock {
            if (isFresh(server)) return
            loadFromDisk(server)
            if (isFresh(server)) return
            refresh(server)
        }
    }

    /** 单次进图该材料的期望掉落个数；无数据返回 null（调用方回退逐场刷）。 */
    fun getRate(stageCode: String, itemId: String): Double? {
        if (stageCode.isBlank() || itemId.isBlank()) return null
        val map = synchronized(stateLock) { rates } ?: return null
        return map[dropRateKey(stageCode, itemId)]?.takeIf { it > 0.0 }
    }

    private fun isFresh(server: String): Boolean {
        val (map, loaded, at) = synchronized(stateLock) { Triple(rates, loadedServer, fetchedAt) }
        return map != null && loaded == server && System.currentTimeMillis() - at < CACHE_TTL_MS
    }

    private fun loadFromDisk(server: String) {
        if (synchronized(stateLock) { rates != null && loadedServer == server }) return
        val file = cacheFile(server)
        if (!file.exists()) return
        val parsed = runCatching { parseCache(file.readText()) }
            .onFailure { Timber.e(it, "解析掉率缓存失败: %s", file.name) }
            .getOrNull() ?: return
        synchronized(stateLock) {
            rates = parsed.first
            fetchedAt = parsed.second
            loadedServer = server
        }
        Timber.i("已从磁盘加载 %d 条掉率（%s）", parsed.first.size, server)
    }

    private suspend fun refresh(server: String) {
        val stagesBody = get(STAGES_PATH) ?: return
        val matrixBody = get("$MATRIX_PATH?server=$server") ?: return

        val parsed = runCatching { parse(stagesBody, matrixBody) }
            .onFailure { Timber.e(it, "解析掉率数据失败") }
            .getOrNull() ?: return
        if (parsed.isEmpty()) {
            Timber.w("掉率数据为空（%s），回退逐场刷", server)
            return
        }

        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            rates = parsed
            fetchedAt = now
            loadedServer = server
        }
        runCatching { cacheFile(server).writeText(buildCacheJson(parsed, now)) }
            .onFailure { Timber.w(it, "掉率缓存写入失败") }
        Timber.i("已刷新 %d 条掉率（%s）", parsed.size, server)
    }

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        for (base in DOMAINS) {
            val body = runCatching {
                httpClient.newCall(
                    Request.Builder()
                        .url("$base$path")
                        .header("Accept", "application/json")
                        .header("User-Agent", "MaaMeow/${BuildConfig.VERSION_NAME}")
                        .build(),
                ).execute().use { resp -> if (resp.isSuccessful) resp.body.string() else null }
            }.onFailure { Timber.w(it, "拉取掉率失败: %s%s", base, path) }.getOrNull()
            if (body != null) return@withContext body
        }
        Timber.w("掉率拉取全部失败: %s", path)
        null
    }

    /** 合并 stages 的 code↔stageId 与 matrix 的统计，得到按 code 索引的掉率表 */
    private fun parse(stagesBody: String, matrixBody: String): Map<String, Double> =
        mergeDropRates(stagesBody, matrixBody)

    private fun buildCacheJson(map: Map<String, Double>, at: Long): String {
        val ratesObj = JSONObject(map.size)
        map.forEach { (k, v) -> ratesObj.put(k, v) }
        return JSONObject().apply {
            put("fetchedAt", at)
            put("rates", ratesObj)
        }.toString()
    }

    /** @return Pair<掉率表, 抓取时刻> */
    private fun parseCache(body: String): Pair<Map<String, Double>, Long> {
        val root = JSON.parseObject(body) ?: return emptyMap<String, Double>() to 0L
        val at = root.getLongValue("fetchedAt")
        val obj = root.getJSONObject("rates") ?: return emptyMap<String, Double>() to 0L
        val result = HashMap<String, Double>(obj.size)
        obj.forEach { (k, v) ->
            val rate = (v as? Number)?.toDouble() ?: return@forEach
            if (rate > 0.0) result[k] = rate
        }
        return result to at
    }

    private fun cacheFile(server: String) = File(cacheDir, "penguin_drop_rates_$server.json")

    private companion object {
        const val STAGES_PATH = "/PenguinStats/api/v2/stages"
        const val MATRIX_PATH = "/PenguinStats/api/v2/result/matrix"
        val DOMAINS = listOf("https://penguin-stats.io", "https://penguin-stats.cn")

        /** 掉率变化慢，一天一刷足够；也避免每次启动都打外网 */
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }
}

/** 掉率表的 key 形态：关卡 code + 材料 id */
internal fun dropRateKey(stageCode: String, itemId: String): String = "$stageCode|$itemId"

/**
 * 合并 stages 的 `code ↔ stageId` 与 matrix 的 `quantity / times`，产物按关卡 code 索引
 * （matrix 只有内部 stageId，而计划里存的是用户输入的 code）。
 *
 * 跳过：stages 里没有 code 的条目、matrix 里 times/quantity 非正的样本、映射不到的 stageId。
 * 解析失败按「没有数据」处理（调用方另有 runCatching 兜底）。独立成顶层函数是为了能直接喂假 JSON 做单测。
 */
internal fun mergeDropRates(stagesBody: String, matrixBody: String): Map<String, Double> {
    val codeByStageId = HashMap<String, String>()
    val stagesArray = runCatching { JSON.parseArray(stagesBody) }.getOrNull() ?: return emptyMap()
    stagesArray.forEach { item ->
        val obj = item as? JSONObject ?: return@forEach
        val stageId = obj.getString("stageId") ?: return@forEach
        val code = obj.getString("code") ?: return@forEach
        if (stageId.isNotBlank() && code.isNotBlank()) codeByStageId[stageId] = code
    }
    if (codeByStageId.isEmpty()) return emptyMap()

    val matrix = runCatching { JSON.parseObject(matrixBody)?.getJSONArray("matrix") }.getOrNull()
        ?: return emptyMap()
    val result = HashMap<String, Double>(matrix.size)
    matrix.forEach { item ->
        val obj = item as? JSONObject ?: return@forEach
        val stageId = obj.getString("stageId") ?: return@forEach
        val itemId = obj.getString("itemId") ?: return@forEach
        val code = codeByStageId[stageId] ?: return@forEach
        val times = obj.getLongValue("times")
        val quantity = obj.getLongValue("quantity")
        if (times > 0 && quantity > 0) {
            result[dropRateKey(code, itemId)] = quantity.toDouble() / times.toDouble()
        }
    }
    return result
}
