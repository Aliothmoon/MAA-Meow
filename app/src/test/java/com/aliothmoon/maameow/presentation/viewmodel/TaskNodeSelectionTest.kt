package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskNodeSelectionTest {

    private fun node(id: String) = TaskChainNode(
        id = id,
        name = id,
        enabled = true,
        config = WakeUpConfig(),
    )

    @Test
    fun `null selection falls back to first node`() {
        val nodes = listOf(node("a"), node("b"))
        assertEquals(
            "a",
            resolveTaskPanelSelectedNodeId(
                nodes = nodes,
                selectedNodeId = null,
                isAddingTask = false,
                isProfileMode = false,
            ),
        )
    }

    @Test
    fun `invalid selection falls back to first node`() {
        val nodes = listOf(node("a"), node("b"))
        assertEquals(
            "a",
            resolveTaskPanelSelectedNodeId(
                nodes = nodes,
                selectedNodeId = "gone",
                isAddingTask = false,
                isProfileMode = false,
            ),
        )
    }

    @Test
    fun `valid selection is kept`() {
        val nodes = listOf(node("a"), node("b"))
        assertEquals(
            "b",
            resolveTaskPanelSelectedNodeId(
                nodes = nodes,
                selectedNodeId = "b",
                isAddingTask = false,
                isProfileMode = false,
            ),
        )
    }

    @Test
    fun `adding or profile mode keeps null`() {
        val nodes = listOf(node("a"))
        assertNull(
            resolveTaskPanelSelectedNodeId(
                nodes = nodes,
                selectedNodeId = null,
                isAddingTask = true,
                isProfileMode = false,
            ),
        )
        assertNull(
            resolveTaskPanelSelectedNodeId(
                nodes = nodes,
                selectedNodeId = null,
                isAddingTask = false,
                isProfileMode = true,
            ),
        )
    }

    @Test
    fun `empty chain yields null`() {
        assertNull(
            resolveTaskPanelSelectedNodeId(
                nodes = emptyList(),
                selectedNodeId = "a",
                isAddingTask = false,
                isProfileMode = false,
            ),
        )
    }
}
