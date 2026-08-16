package com.aliothmoon.maameow.domain.models

import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 录制下来的锁屏解锁手势
 * 坐标为录制当时的屏幕像素；回放前需按 [screenWidth] / [screenHeight] / [rotation] 校验
 */
@Serializable
data class UnlockGesture(
    val version: Int = VERSION,
    val screenWidth: Int,
    val screenHeight: Int,
    val rotation: Int,
    val steps: List<UnlockStep>,
) {
    companion object {
        const val VERSION = 1

        /**
         * 解析持久化/跨进程传来的手势
         * 格式对不上就当没录过，好过按旧规则错误回放；两个进程共用这一份闸门
         */
        fun parseOrNull(json: String, onDrop: (String) -> Unit = {}): UnlockGesture? {
            if (json.isBlank()) return null
            val gesture = runCatching {
                JsonUtils.common.decodeFromString(serializer(), json)
            }.getOrElse {
                onDrop("malformed gesture json: ${it.message}")
                return null
            }
            if (gesture.version != VERSION) {
                onDrop("gesture version ${gesture.version} != $VERSION")
                return null
            }
            return gesture
        }
    }
}

@Serializable
data class GesturePoint(
    val x: Int,
    val y: Int,
    /** 相对本步骤起点的毫秒偏移 */
    val tMs: Int,
)

@Serializable
sealed interface UnlockStep {

    /** 与上一步之间的间隔，首步恒为 0 */
    val delayBeforeMs: Int

    @Serializable
    @SerialName("tap")
    data class Tap(
        val x: Int,
        val y: Int,
        override val delayBeforeMs: Int = 0,
    ) : UnlockStep

    @Serializable
    @SerialName("long_press")
    data class LongPress(
        val x: Int,
        val y: Int,
        val holdMs: Int,
        override val delayBeforeMs: Int = 0,
    ) : UnlockStep

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val points: List<GesturePoint>,
        override val delayBeforeMs: Int = 0,
    ) : UnlockStep
}

/** 删掉某一步之后，新首步不该再带间隔 */
fun UnlockStep.withoutDelay(): UnlockStep = when (this) {
    is UnlockStep.Tap -> copy(delayBeforeMs = 0)
    is UnlockStep.LongPress -> copy(delayBeforeMs = 0)
    is UnlockStep.Swipe -> copy(delayBeforeMs = 0)
}
