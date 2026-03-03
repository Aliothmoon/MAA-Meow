package com.aliothmoon.maameow.maa.callback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Copilot 运行时状态共享存储：
 * 1) 是否出现过练度要求被忽略
 * 2) 作战成功事件计数（用于列表模式自动勾除/自动评价）
 */
class CopilotRuntimeStateStore {
    private val _hasRequirementIgnored = MutableStateFlow(false)
    val hasRequirementIgnored: StateFlow<Boolean> = _hasRequirementIgnored.asStateFlow()

    private val _taskSuccessToken = MutableStateFlow(0L)
    val taskSuccessToken: StateFlow<Long> = _taskSuccessToken.asStateFlow()

    fun markRequirementIgnored() {
        _hasRequirementIgnored.value = true
    }

    fun resetRequirementIgnored() {
        _hasRequirementIgnored.value = false
    }

    fun markTaskSuccess() {
        _taskSuccessToken.update { it + 1L }
    }
}
