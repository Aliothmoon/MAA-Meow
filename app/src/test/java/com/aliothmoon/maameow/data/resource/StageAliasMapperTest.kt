package com.aliothmoon.maameow.data.resource

import org.junit.Assert.assertEquals
import org.junit.Test

class StageAliasMapperTest {

    /**
     * 中文输入法敲「1-7」送上来的是全角连字符，不归一化就会存进一个 MaaCore 认不出的关卡码
     */
    @Test
    fun fullWidthStageCode_isNormalized() {
        assertEquals("1-7", StageAliasMapper.mapToStageCode("1－7"))
    }

    @Test
    fun fullWidthAlias_isStillMapped() {
        assertEquals("CE-6", StageAliasMapper.mapToStageCode("ＣＥ"))
    }

    @Test
    fun fullWidthStageCode_matchesAvailableStages() {
        assertEquals("CE-6", StageAliasMapper.mapToStageCode("ＣＥ－６", listOf("CE-6")))
    }

    @Test
    fun chineseAlias_isStillMapped() {
        assertEquals("CE-6", StageAliasMapper.mapToStageCode("龙门币"))
        assertEquals("LS-6", StageAliasMapper.mapToStageCode("狗粮"))
    }

    @Test
    fun unknownStage_isOnlyUppercased() {
        assertEquals("XX-9", StageAliasMapper.mapToStageCode("xx-9"))
    }

    @Test
    fun blank_isReturnedAsIs() {
        assertEquals("", StageAliasMapper.mapToStageCode(""))
    }
}
