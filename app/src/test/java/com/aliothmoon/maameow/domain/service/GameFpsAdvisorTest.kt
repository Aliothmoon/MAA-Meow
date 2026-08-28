package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.service.GameFpsAdvisor.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameFpsAdvisorTest {

    // 3 个样本、80% → 需要 3 个都低于阈值
    private val advisor = GameFpsAdvisor(windowSize = 3)

    private fun GameFpsAdvisor.feed(vararg samples: Float): List<Level?> = samples.map { onSample(it)?.level }

    private fun feed(vararg samples: Float) = advisor.feed(*samples)

    @Test
    fun needsFullWindowBeforeAdvising() {
        assertEquals(listOf(null, null), feed(20f, 20f))
    }

    @Test
    fun sustainedLowAdvisesLowOnce() {
        assertEquals(listOf(null, null, Level.LOW, null, null), feed(20f, 25f, 28f, 20f, 20f))
    }

    @Test
    fun sustainedDegradedAdvisesDegradedOnce() {
        assertEquals(listOf(null, null, Level.DEGRADED, null), feed(45f, 40f, 48f, 42f))
    }

    @Test
    fun healthyFrameRateNeverAdvises() {
        assertEquals(listOf(null, null, null, null), feed(60f, 59f, 60f, 58f))
    }

    @Test
    fun adviceCarriesWindowMedianNotCurrentSample() {
        // 触发时的样本是 60，但窗口中位数 20 才是该报的数
        val advisor = GameFpsAdvisor(windowSize = 5)
        val advice = listOf(20f, 20f, 20f, 25f, 60f).map { advisor.onSample(it) }.last()
        assertEquals(Level.LOW, advice?.level)
        assertEquals(20f, advice!!.medianFps, 0f)
    }

    @Test
    fun defaultWindowNeedsTwelveOfFifteenLowSamples() {
        val eleven = GameFpsAdvisor()
        // 11 低 + 4 高：73% 不触发
        assertNull((List(11) { 20f } + List(4) { 60f }).map { eleven.onSample(it) }.last())

        val twelve = GameFpsAdvisor()
        // 12 低 + 3 高：80% 触发
        assertEquals(Level.LOW, (List(12) { 20f } + List(3) { 60f }).map { twelve.onSample(it) }.last()?.level)
    }

    @Test
    fun transientDipDoesNotAdvise() {
        // 10 秒窗口里 3 秒转场掉到个位数：占比 30% 不到 80%，均值法会误报为 DEGRADED
        val advisor = GameFpsAdvisor(windowSize = 10)
        val samples = List(7) { 60f } + List(3) { 5f } + List(5) { 60f }
        samples.forEach { assertNull(advisor.onSample(it)) }
    }

    @Test
    fun mostlyLowWindowAdvisesEvenWithOccasionalGoodSample() {
        // 10 个里 8 个低于 30 → 80% 达标
        val advisor = GameFpsAdvisor(windowSize = 10)
        val samples = List(4) { 20f } + listOf(60f) + List(4) { 20f } + listOf(60f)
        assertEquals(Level.LOW, samples.map { advisor.onSample(it) }.last()?.level)
    }

    @Test
    fun shortIdleIsSkippedWithoutClearingWindow() {
        // 加载黑屏 1~3 秒（帧计数回退源会给 0）不打断累计
        val advisor = GameFpsAdvisor(windowSize = 4, maxIdleStreak = 3)
        assertEquals(
            listOf(null, null, null, null, null, null, Level.LOW),
            advisor.feed(20f, 20f, 0f, 0f, 0f, 20f, 20f),
        )
    }

    @Test
    fun longIdleClearsWindow() {
        // 菜单/加载页停留超过容忍长度后重新累计
        val advisor = GameFpsAdvisor(windowSize = 3, maxIdleStreak = 2)
        assertEquals(listOf(null, null, null, null, null, null), advisor.feed(20f, 20f, 0f, 0f, 0f, 20f))
        assertEquals(listOf(null, Level.LOW), advisor.feed(20f, 20f))
    }

    @Test
    fun unknownSampleCountsAsShortIdle() {
        // -1（远端未监控/读取失败）与 0 同样处理：短暂出现只跳过
        assertEquals(listOf(null, null, null, Level.LOW), feed(20f, -1f, 20f, 20f))
    }

    @Test
    fun recoveringFromLowStillReportsDegradedLater() {
        feed(20f, 20f, 20f)
        // 回升到 30~50 区间并持续满窗口时提示一次 DEGRADED
        assertEquals(1, feed(45f, 45f, 45f, 45f).count { it == Level.DEGRADED })
    }

    @Test
    fun stayingLowNeverEscalatesToDegraded() {
        assertEquals(listOf(null, null, Level.LOW, null, null, null), feed(20f, 20f, 20f, 25f, 28f, 29f))
    }

    @Test
    fun resetAllowsAdvisingAgain() {
        feed(20f, 20f, 20f)
        advisor.reset()
        assertEquals(Level.LOW, feed(20f, 20f, 20f).last())
    }
}
