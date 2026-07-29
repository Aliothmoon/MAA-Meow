package com.aliothmoon.maameow.maa.callback

import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [MaaStringRes] 位于 MaaCore 回调热路径上，本测试锁住两条性能契约：
 * 未命中也必须缓存，且缓存必须并发安全。
 */
class MaaStringResTest {

    private val resources: Resources = mockk()
    private val pkg = "com.aliothmoon.maameow"

    @Before
    fun setUp() {
        MaaStringRes.clearCacheForTest()
    }

    // ==================== 命名转换 ====================

    @Test
    fun camelToSnake_convertsMaaCoreKeys() {
        assertEquals("stage_drops", MaaStringRes.camelToSnake("StageDrops"))
        assertEquals("cur_times", MaaStringRes.camelToSnake("CurTimes"))
        // 点号是 MaaCore 任务名的层级分隔符，按下划线处理
        assertEquals("copilot_stage_drops", MaaStringRes.camelToSnake("Copilot.StageDrops"))
    }

    @Test
    fun camelToSnake_keepsAcronymsTogether() {
        // 连续大写不逐字母拆分，只在「大写后跟小写」处断开
        assertEquals("prts_task", MaaStringRes.camelToSnake("PRTSTask"))
    }

    @Test
    fun camelToSnake_doesNotSplitBeforeTrailingDigit() {
        // 数字不触发断开；只有「数字后跟大写」才断（StartButton2 vs Stage2Foo）
        assertEquals("start_button2", MaaStringRes.camelToSnake("StartButton2"))
        assertEquals("stage2_foo", MaaStringRes.camelToSnake("Stage2Foo"))
        assertEquals("stage_dreadful_foe_5", MaaStringRes.camelToSnake("StageDreadfulFoe_5"))
    }

    // ==================== 正向缓存 ====================

    @Test
    fun getResId_looksUpByMaaPrefixedSnakeName() {
        every { resources.getIdentifier("maa_stage_drops", "string", pkg) } returns 1234

        assertEquals(1234, MaaStringRes.getResId(resources, pkg, "StageDrops"))
    }

    @Test
    fun getResId_hitIsCached_identifierResolvedOnce() {
        every { resources.getIdentifier(any(), any(), any()) } returns 1234

        repeat(5) { MaaStringRes.getResId(resources, pkg, "StageDrops") }

        verify(exactly = 1) { resources.getIdentifier("maa_stage_drops", "string", pkg) }
    }

    // ==================== 负向缓存（本次修复的核心） ====================

    /**
     * MaaCore 有大量 key 没有对应字符串资源。若只缓存命中，
     * 这些 key 每次出现都要重跑 getIdentifier —— 而它就在回调热路径上。
     */
    @Test
    fun getResId_missIsAlsoCached_identifierResolvedOnce() {
        every { resources.getIdentifier(any(), any(), any()) } returns 0

        repeat(10) { MaaStringRes.getResId(resources, pkg, "SomeUnmappedCoreKey") }

        verify(exactly = 1) { resources.getIdentifier("maa_some_unmapped_core_key", "string", pkg) }
    }

    @Test
    fun getString_missFallsBackToKeyItself_evenWhenCached() {
        every { resources.getIdentifier(any(), any(), any()) } returns 0

        assertEquals("UnknownKey", MaaStringRes.getString(resources, pkg, "UnknownKey"))
        // 第二次走缓存，仍须回退为 key 本身而不是空串或崩溃
        assertEquals("UnknownKey", MaaStringRes.getString(resources, pkg, "UnknownKey"))
        verify(exactly = 1) { resources.getIdentifier(any(), any(), any()) }
    }

    @Test
    fun getString_missNeverCallsGetString() {
        every { resources.getIdentifier(any(), any(), any()) } returns 0

        MaaStringRes.getString(resources, pkg, "UnknownKey")

        // resId=0 传给 Resources.getString 会抛 NotFoundException，必须短路
        verify(exactly = 0) { resources.getString(any<Int>()) }
    }

    @Test
    fun getString_hitResolvesThroughResources() {
        every { resources.getIdentifier("maa_no_drop", "string", pkg) } returns 42
        every { resources.getString(42) } returns "无掉落"

        assertEquals("无掉落", MaaStringRes.getString(resources, pkg, "NoDrop"))
    }

    @Test
    fun getString_withFormatArgs_passesThemThrough() {
        every { resources.getIdentifier("maa_cur_times", "string", pkg) } returns 7
        every { resources.getString(7, *anyVararg()) } returns "已完成 3 次"

        assertEquals("已完成 3 次", MaaStringRes.getString(resources, pkg, "CurTimes", 3))

        verify { resources.getString(7, 3) }
    }

    @Test
    fun getString_withFormatArgs_missFallsBackToKey() {
        every { resources.getIdentifier(any(), any(), any()) } returns 0

        assertEquals("Unknown", MaaStringRes.getString(resources, pkg, "Unknown", 1, "x"))
    }

    // ==================== 并发安全 ====================

    /**
     * 回调经 oneway binder 送达：对同一 binder node 串行，但执行线程取自线程池、
     * 身份会变。普通 HashMap 的写入之间没有 happens-before，可能读到半初始化的表。
     */
    @Test
    fun getResId_isSafeUnderConcurrentAccess() {
        val keys = (0 until 64).map { "ConcurrentKey$it" }
        val calls = AtomicInteger(0)
        every { resources.getIdentifier(any(), any(), any()) } answers {
            calls.incrementAndGet()
            val name = firstArg<String>()
            // 一半命中一半未命中，两条缓存路径都压到
            if (name.last().code % 2 == 0) 100 else 0
        }

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val results = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Int>>())

        repeat(threads) {
            pool.execute {
                start.await()
                repeat(50) {
                    keys.forEach { key ->
                        results += key to MaaStringRes.getResId(resources, pkg, key)
                    }
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue("并发访问未在 10s 内完成，疑似 HashMap 死循环", done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        // 同一 key 在所有线程上的结果必须一致
        val byKey = results.groupBy({ it.first }, { it.second })
        byKey.forEach { (key, values) ->
            assertEquals("key=$key 在并发下返回了不一致的 resId", 1, values.distinct().size)
        }
        // 每个 key 只解析一次：负缓存在并发下同样生效
        assertEquals(keys.size, calls.get())
    }
}
