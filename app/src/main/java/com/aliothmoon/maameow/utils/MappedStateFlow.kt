package com.aliothmoon.maameow.utils

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 同步派生的只读 [StateFlow]：value 现算，不用 stateIn 也不占 scope */
fun <S, T> StateFlow<S>.mapState(transform: (S) -> T): StateFlow<T> = MappedStateFlow(this, transform)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class MappedStateFlow<S, T>(
    private val source: StateFlow<S>,
    private val transform: (S) -> T,
) : StateFlow<T> {

    override val value: T
        get() = transform(source.value)

    override val replayCache: List<T>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        source.map(transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
