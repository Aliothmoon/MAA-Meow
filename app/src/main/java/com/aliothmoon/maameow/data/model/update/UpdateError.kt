package com.aliothmoon.maameow.data.model.update

import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.InvalidArch
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.InvalidChannel
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.InvalidOs
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.KeyBlocked
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.KeyExpired
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.KeyInvalid
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.KeyMismatched
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.ResourceNotFound
import com.aliothmoon.maameow.data.model.update.UpdateError.MirrorchyanBizError.ResourceQuotaExhausted

/**
 * 更新错误类型
 */
sealed class UpdateError : Message {

    data class NetworkError(override val message: String? = null) : UpdateError()
    data class UnknownError(
        override val message: String,
        val code: Int = -1,
        val throwable: Throwable? = null
    ) : UpdateError()

    /** Mirrorchyan业务错误 */
    sealed class MirrorchyanBizError(
        override val message: String
    ) : UpdateError() {
        // 403 - CDK
        data object KeyExpired :
            MirrorchyanBizError("CDK 已过期")

        data object KeyInvalid :
            MirrorchyanBizError("CDK 不正确")

        data object ResourceQuotaExhausted :
            MirrorchyanBizError("CDK 今日下载次数已达上限")

        data object KeyMismatched :
            MirrorchyanBizError("CDK 类型和待下载的资源不匹配")

        data object KeyBlocked :
            MirrorchyanBizError("CDK 已被封禁")

        // 404
        data object ResourceNotFound :
            MirrorchyanBizError("对应架构和系统下的资源不存在")

        // 400
        data object InvalidOs :
            MirrorchyanBizError("错误的系统参数")

        data object InvalidArch :
            MirrorchyanBizError("错误的架构参数")

        data object InvalidChannel :
            MirrorchyanBizError("错误的更新通道参数")

    }

    data object CdkRequired : UpdateError() {
        override val message: String = "请输入正确的 CDK"
    }

    companion object {
        fun fromCode(bizCode: Int, message: String?, throwable: Throwable? = null): UpdateError =
            when (bizCode) {
                7001 -> KeyExpired
                7002 -> KeyInvalid
                7003 -> ResourceQuotaExhausted
                7004 -> KeyMismatched
                7005 -> KeyBlocked
                8001 -> ResourceNotFound
                8002 -> InvalidOs
                8003 -> InvalidArch
                8004 -> InvalidChannel
                else -> UnknownError("unknown error", bizCode, throwable)
            }
    }
}