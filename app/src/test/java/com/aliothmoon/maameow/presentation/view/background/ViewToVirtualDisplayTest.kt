package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewToVirtualDisplayTest {

    private val display = DefaultDisplayConfig.RES_720P

    @Test
    fun centeredCardMatchesFullScreenIdentity() {
        val fullscreen = PreviewCardBounds(0f, 0f, display.width.toFloat(), display.height.toFloat())
        val center = Offset(640f, 360f)
        val full = viewToVirtualDisplay(
            center, fullscreen, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertEquals(IntOffset(640, 360), full.offset)
        assertTrue(full.inside)
        assertTrue(full.deliver)

        // 容器 1800×720，16:9 卡片水平居中：左 260，与全屏同一相对点落到同一虚拟坐标
        val centered = PreviewCardBounds(260f, 0f, 1280f, 720f)
        val onCard = viewToVirtualDisplay(
            Offset(900f, 360f), centered, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertEquals(full.offset, onCard.offset)
        // 左缘是画面 x=0，而不是把容器内的 260 原样送进虚拟屏
        val leftEdge = viewToVirtualDisplay(
            Offset(260f, 360f), centered, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertEquals(IntOffset(0, 360), leftEdge.offset)
        assertTrue(leftEdge.deliver)

        val hd = DefaultDisplayConfig.RES_1080P
        val hdFull = PreviewCardBounds(0f, 0f, hd.width.toFloat(), hd.height.toFloat())
        val hdPoint = viewToVirtualDisplay(
            Offset(100f, 200f), hdFull, hd, PreviewPointerAction.Down, gestureActive = false,
        )
        assertEquals(IntOffset(100, 200), hdPoint.offset)
    }

    @Test
    fun insetsAndHostAspectKeepRightEdgeOnDisplay() {
        // 更宽的宿主：卡片受高度限制，并被挖孔/状态栏顶开
        val wideHost = PreviewCardBounds(left = 80f, top = 24f, width = 1600f, height = 900f)
        val wideEdge = viewToVirtualDisplay(
            Offset(wideHost.right, wideHost.top),
            wideHost,
            display,
            PreviewPointerAction.Down,
            gestureActive = false,
        )
        assertTrue(wideEdge.inside)
        assertTrue(wideEdge.deliver)
        assertEquals(display.width - 1, wideEdge.offset.x)
        assertEquals(0, wideEdge.offset.y)

        // 更高的宿主：卡片受宽度限制，左/上 inset 都不是 0
        val tallHost = PreviewCardBounds(left = 48f, top = 96f, width = 960f, height = 540f)
        val tallEdge = viewToVirtualDisplay(
            Offset(tallHost.right, tallHost.top + tallHost.height / 2f),
            tallHost,
            display,
            PreviewPointerAction.Down,
            gestureActive = false,
        )
        assertTrue(tallEdge.deliver)
        assertEquals(display.width - 1, tallEdge.offset.x)
        assertEquals(display.height / 2, tallEdge.offset.y)

        // 容器比 16:9 宽时等比留白，而不是把横向拉满
        val letterboxed = PreviewCardBounds(0f, 0f, 800f, 360f)
        val contentCenter = viewToVirtualDisplay(
            Offset(400f, 180f), letterboxed, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertEquals(IntOffset(display.width / 2, display.height / 2), contentCenter.offset)
        val sideBar = viewToVirtualDisplay(
            Offset(10f, 180f), letterboxed, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertFalse(sideBar.inside)
        assertFalse(sideBar.deliver)
        val contentRight = viewToVirtualDisplay(
            Offset(720f, 180f), letterboxed, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertTrue(contentRight.deliver)
        assertEquals(display.width - 1, contentRight.offset.x)
    }

    @Test
    fun downOutsideRejectedAndActiveGestureClamped() {
        val card = PreviewCardBounds(100f, 80f, 640f, 360f)
        val outside = Offset(90f, 40f)
        val down = viewToVirtualDisplay(
            outside, card, display, PreviewPointerAction.Down, gestureActive = false,
        )
        assertFalse(down.inside)
        assertFalse(down.deliver)
        assertEquals(IntOffset(0, 0), down.offset)

        val move = viewToVirtualDisplay(
            outside, card, display, PreviewPointerAction.Move, gestureActive = true,
        )
        assertTrue(move.deliver)
        assertEquals(IntOffset(0, 0), move.offset)

        val stray = viewToVirtualDisplay(
            outside, card, display, PreviewPointerAction.Move, gestureActive = false,
        )
        assertFalse(stray.deliver)

        val up = viewToVirtualDisplay(
            Offset(card.right + 30f, card.bottom + 20f),
            card,
            display,
            PreviewPointerAction.Up,
            gestureActive = true,
        )
        assertFalse(up.inside)
        assertTrue(up.deliver)
        assertEquals(IntOffset(display.width - 1, display.height - 1), up.offset)
    }

    @Test
    fun newestBoundsOverrideStaleGeometry() {
        val view = Offset(740f, 260f)
        val stale = PreviewCardBounds(0f, 0f, display.width.toFloat(), display.height.toFloat())
        val fresh = PreviewCardBounds(100f, 80f, 640f, 360f)
        val fromStale = viewToVirtualDisplay(
            view, stale, display, PreviewPointerAction.Move, gestureActive = true,
        )
        val fromFresh = viewToVirtualDisplay(
            view, fresh, display, PreviewPointerAction.Move, gestureActive = true,
        )
        assertEquals(IntOffset(740, 260), fromStale.offset)
        assertEquals(IntOffset(display.width - 1, 360), fromFresh.offset)
        assertNotEquals(fromStale.offset, fromFresh.offset)
    }
}
