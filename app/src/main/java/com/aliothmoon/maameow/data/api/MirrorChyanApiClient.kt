package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.data.api.model.MirrorChyanData
import com.aliothmoon.maameow.data.api.model.MirrorChyanResponse
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.utils.JsonUtils
import timber.log.Timber

/**
 * MirrorChyan API 通用客户端
 * 封装 HTTP 请求、响应解析和业务错误映射
 */
class MirrorChyanApiClient(private val httpClient: HttpClientHelper) {

    private val json = JsonUtils.common

    /**
     * 调用 MirrorChyan latest API
     * @throws MirrorChyanBizException 业务错误（CDK 无效、资源不存在等）
     */
    suspend fun getLatest(apiUrl: String, query: Map<String, String>): Result<MirrorChyanData> {
        return runCatching {
            val response = httpClient.get(apiUrl, query = query)

            if (response.code == 500) {
                throw MirrorChyanBizException(500, "更新服务不可用")
            }

            val body = kotlin.runCatching {
                json.decodeFromString<MirrorChyanResponse>(response.body.string())
            }.getOrDefault(MirrorChyanResponse.UNKNOWN_ERR)

            if (body.code != 0) {
                throw MirrorChyanBizException(body.code, body.msg)
            }

            body.data ?: throw MirrorChyanBizException(-1, "数据为空")
        }.onFailure { e ->
            Timber.e(e, "MirrorChyan API 请求失败: $apiUrl")
        }
    }
}

/** MirrorChyan 业务异常，携带业务错误码 */
class MirrorChyanBizException(val bizCode: Int, override val message: String?) : Exception(message) {
    fun toUpdateError(): UpdateError = UpdateError.fromCode(bizCode, message)
}

/** CDK 未配置异常 */
class CdkRequiredException : Exception("请输入正确的 CDK")
