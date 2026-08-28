package com.aliothmoon.maameow.presentation.state

data class PreviewTouchMarker(
    val id: Long,
    val x: Int,
    val y: Int,
    val action: Int,
    val contact: Int,
    val createdAtMs: Long,
) {
    companion object {
        // 双指时每根手指的轨迹长度与单指持平
        const val MAX_ACTIVE_MARKERS = 16
        const val TTL_MS = 600L
        const val CLEANUP_INTERVAL_MS = 100L
    }
}
