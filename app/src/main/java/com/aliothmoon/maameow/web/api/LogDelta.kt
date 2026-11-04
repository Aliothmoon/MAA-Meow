package com.aliothmoon.maameow.web.api

import com.aliothmoon.maameow.data.model.LogItem

/**
 * 运行日志整表 → 增量
 * 上次发出的最后一条还在表里就只发它之后的；不在了（被清空或一次超出上限）就整表重发
 */
class LogDelta {

    data class Change(val items: List<LogItem>, val reset: Boolean)

    private var lastSentId: Long? = null

    fun next(logs: List<LogItem>): Change? {
        val lastId = lastSentId
        if (logs.isEmpty()) {
            if (lastId == null) return null
            lastSentId = null
            return Change(emptyList(), reset = true)
        }
        lastSentId = logs.last().id
        if (lastId == null) return Change(logs, reset = true)

        // 刚发过的那条基本在表尾，倒着找
        val index = logs.indexOfLast { it.id == lastId }
        if (index < 0) return Change(logs, reset = true)
        if (index == logs.lastIndex) return null
        return Change(logs.subList(index + 1, logs.size).toList(), reset = false)
    }
}
