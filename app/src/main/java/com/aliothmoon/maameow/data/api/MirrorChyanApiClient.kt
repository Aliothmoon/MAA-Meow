package com.aliothmoon.maameow.data.api

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.model.MirrorChyanData
import com.aliothmoon.maameow.data.api.model.MirrorChyanResponse
import com.aliothmoon.maameow.data.model.update.UpdateError
import com.aliothmoon.maameow.utils.i18n.LocalizedException
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * MirrorChyan API 通用客户端
 * 封装 HTTP 请求、响应解析和业务错误映射
 */
class MirrorChyanApiClient(
    private val httpClient: HttpClientHelper,
    private val cdkStatus: MirrorChyanCdkStatus,
) {

    private val json = JsonUtils.common

    /**
     * 调用 MirrorChyan latest API，带 cdk 时顺带回写 [MirrorChyanCdkStatus]
     * @throws MirrorChyanBizException 业务错误（CDK 无效、资源不存在等）
     */
    suspend fun getLatest(
        api: String,
        query: Map<String, String>,
        fetchVersion: Boolean = false
    ): Result<MirrorChyanData> = withContext(Dispatchers.IO) {
        val cdk = query["cdk"]?.takeIf { cdkStatus.begin(it) }
        try {
            runCatching {
                val body = httpClient.get(api, query = query).use { resp ->
                    if (resp.code == 500) {
                        MirrorChyanResponse(500, "更新服务不可用")
                    } else {
                        kotlin.runCatching {
                            json.decodeFromString<MirrorChyanResponse>(resp.body.string())
                        }.getOrDefault(MirrorChyanResponse.UNKNOWN_ERR)
                    }
                }
                cdk?.let { cdkStatus.onResponse(it, body.code, body.data?.cdkExpiredTime) }

                if (body.code == 500 || (body.code != 0 && !fetchVersion)) {
                    // 消息体留空：UpdateError.fromCode 会把已知错误码固定映射到对应 R.string，
                    // 服务端返回的中文串在这个路径上永远不会展示
                    throw MirrorChyanBizException(body.code, null)
                }

                // 同上：fromCode(-1) 固定映射到 R.string.update_error_empty_data
                body.data ?: throw MirrorChyanBizException(-1, null)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                if (e !is MirrorChyanBizException) cdk?.let { cdkStatus.onNetworkFailure(it) }
                Timber.e(e, "MirrorChyan API 请求失败: $api")
            }
        } finally {
            if (cdk != null) cdkStatus.end()
        }
    }
}

/** MirrorChyan 业务异常，携带业务错误码 */
class MirrorChyanBizException(val bizCode: Int, override val message: String?) :
    Exception(message) {
    fun toUpdateError(): UpdateError = UpdateError.fromCode(bizCode, message)
}

/** CDK 未配置异常 */
class CdkRequiredException :
    LocalizedException(uiTextOf(R.string.update_error_cdk_required))
