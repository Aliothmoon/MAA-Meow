package com.aliothmoon.maameow.domain.state

/**
 * 资源初始化状态
 */
sealed class ResourceInitState {
    /**
     * 未检查
     */
    data object NotChecked : ResourceInitState()

    /**
     * 检查中
     */
    data object Checking : ResourceInitState()

    /**
     * 资源已就绪
     */
    data object Ready : ResourceInitState()

    /**
     * 解压中
     */
    data class Extracting(
        val extractedCount: Int,
        val totalCount: Int,
        val currentFile: String = ""
    ) : ResourceInitState() {
        val progress: Int
            get() = if (totalCount > 0) (extractedCount * 100 / totalCount) else 0
    }

    /**
     * 初始化失败
     */
    data class Failed(val message: String) : ResourceInitState()
}