package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.utils.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class CopilotConfigCompatTest {

    /** 旧版 encodeDefaults 把字符串型 userAdditional 写进了每份配置，解码失败会让整份配置回默认 */
    @Test
    fun legacyStringUserAdditional_isIgnoredWithoutResettingConfig() {
        val config = JsonUtils.common.decodeFromString<CopilotConfig>(
            """{"formation":false,"addTrust":true,"addUserAdditional":false,"userAdditional":""}"""
        )

        assertEquals(false, config.formation)
        assertEquals(true, config.addTrust)
        assertEquals(emptyList<UserAdditionalOper>(), config.userAdditionalOpers)
    }
}
