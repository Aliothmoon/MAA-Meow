package com.aliothmoon.maameow.domain.service

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 作业介绍里的 B 站视频号提取，与 WPF BVRegex 对齐：
 * av 号纯数字、BV 号固定 10 位字母数字、可带 /?p=N 分 P
 */
class CopilotVideoUrlTest {

    private val manager = CopilotManager(apiService = mockk(), repository = mockk())

    @Test
    fun bvId_isExtractedInFull() {
        // 旧正则 [aAbB][vV]\d+ 在这里只能截到 BV1
        assertEquals(
            "https://www.bilibili.com/video/BV1GJ411x7h7",
            manager.extractVideoUrl("视频讲解见 BV1GJ411x7h7，注意抄干员练度")
        )
    }

    @Test
    fun bvId_withPartSuffix_keepsSuffix() {
        assertEquals(
            "https://www.bilibili.com/video/BV1GJ411x7h7/?p=2",
            manager.extractVideoUrl("https://www.bilibili.com/video/BV1GJ411x7h7/?p=2")
        )
    }

    @Test
    fun avId_andCaseInsensitive() {
        assertEquals("https://www.bilibili.com/video/av170001", manager.extractVideoUrl("av170001"))
        assertEquals("https://www.bilibili.com/video/AV170001", manager.extractVideoUrl("看 AV170001"))
        assertEquals("https://www.bilibili.com/video/bv1GJ411x7h7", manager.extractVideoUrl("bv1GJ411x7h7"))
    }

    @Test
    fun noMatch_returnsEmpty() {
        assertEquals("", manager.extractVideoUrl(""))
        assertEquals("", manager.extractVideoUrl("无视频"))
        assertEquals("BV 号不足 10 位不算", "", manager.extractVideoUrl("BV1GJ4"))
        assertEquals("av 嵌在单词里不算", "", manager.extractVideoUrl("nav12"))
    }
}
