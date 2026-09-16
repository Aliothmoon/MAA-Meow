package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * 一图流 OpenAPI 干员练度数据，对齐 WPF YituliuApiService
 *
 * 鉴权是请求头 Authorization 直接带 Token，没有 Bearer 前缀；非 2xx 按网络错误，其余看响应体的 code
 */
class YituliuApiService(
    private val httpClientHelper: HttpClientHelper,
) {
    @Serializable
    data class Operator(
        val id: String = "",
        val level: Int = 0,
        val evolvePhase: Int = 0,
        val mainSkillLevel: Int = 0,
        val potentialRank: Int = 0,
        val skills: List<Skill>? = null,
        val equips: List<Equip>? = null,
    )

    @Serializable
    data class Skill(val id: String = "", val level: Int = 0)

    /** [type] 为模组分支，X / Y */
    @Serializable
    data class Equip(val id: String = "", val type: String = "", val level: Int = 0)

    sealed interface Result {
        /** 账号没有练度数据时是空列表 */
        data class Success(val operators: List<Operator>) : Result

        /** 各自对应一条不同的用户指引，不能合并 */
        sealed interface Failure : Result {
            /** Token 只有写权限，读不到数据 */
            data object WriteOnly : Failure

            /** Token 无效或已失效；响应解析不出来也归这里 */
            data object Invalid : Failure

            data object NetworkError : Failure
        }
    }

    suspend fun fetchOperators(token: String): Result = withContext(Dispatchers.IO) {
        try {
            val raw = httpClientHelper.get(
                MaaApi.YITULIU_OPERATOR_INFO,
                headers = mapOf(HEADER_AUTHORIZATION to token),
            ).use { response ->
                if (!response.isSuccessful) {
                    Timber.w("%s: HTTP %d", TAG, response.code)
                    return@withContext Result.Failure.NetworkError
                }
                response.body.string()
            }
            val payload = JsonUtils.common.decodeFromString<Payload>(raw)
            when (payload.code) {
                CODE_SUCCESS -> Result.Success(payload.data.orEmpty())
                CODE_WRITE_ONLY -> Result.Failure.WriteOnly
                else -> {
                    Timber.w("%s: rejected, code=%d", TAG, payload.code)
                    Result.Failure.Invalid
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            Timber.w("%s: bad response: %s", TAG, e.javaClass.simpleName)
            Result.Failure.Invalid
        } catch (e: Exception) {
            // 异常消息可能回显 Token（OkHttp 校验请求头值时会带上原值），只记类名
            Timber.w("%s: request failed: %s", TAG, e.javaClass.simpleName)
            Result.Failure.NetworkError
        }
    }

    @Serializable
    private data class Payload(
        val code: Int = 0,
        val data: List<Operator>? = null,
    )

    companion object {
        private const val TAG = "YituliuApi"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val CODE_SUCCESS = 200
        private const val CODE_WRITE_ONLY = 20010
    }
}

/** 失败原因对应的提示文案，设置页与干员识别共用 */
fun YituliuApiService.Result.Failure.message(): UiText = when (this) {
    YituliuApiService.Result.Failure.WriteOnly -> uiTextOf(R.string.oper_box_yituliu_write_only)
    YituliuApiService.Result.Failure.Invalid -> uiTextOf(R.string.oper_box_yituliu_invalid)
    YituliuApiService.Result.Failure.NetworkError -> uiTextOf(R.string.oper_box_yituliu_network_error)
}
