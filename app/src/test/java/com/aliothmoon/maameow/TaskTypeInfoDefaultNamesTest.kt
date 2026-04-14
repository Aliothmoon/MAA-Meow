package com.aliothmoon.maameow

import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTypeInfoDefaultNamesTest {

    @Test
    fun defaultName_matchesWpfChineseLabels() {
        assertEquals("开始唤醒", TaskTypeInfo.WAKE_UP.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("自动公招", TaskTypeInfo.RECRUITING.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("基建换班", TaskTypeInfo.BASE.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("理智作战", TaskTypeInfo.COMBAT.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("信用收支", TaskTypeInfo.MALL.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("领取奖励", TaskTypeInfo.MISSION.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("自动肉鸽", TaskTypeInfo.AUTO_ROGUELIKE.defaultName(AppSettingsManager.AppLanguage.ZH))
        assertEquals("生息演算", TaskTypeInfo.RECLAMATION.defaultName(AppSettingsManager.AppLanguage.ZH))
    }

    @Test
    fun defaultName_matchesWpfEnglishLabels() {
        assertEquals("Login", TaskTypeInfo.WAKE_UP.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Auto Recruit", TaskTypeInfo.RECRUITING.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Base", TaskTypeInfo.BASE.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Combat", TaskTypeInfo.COMBAT.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Credit Store", TaskTypeInfo.MALL.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Collect Rewards", TaskTypeInfo.MISSION.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Auto I.S.", TaskTypeInfo.AUTO_ROGUELIKE.defaultName(AppSettingsManager.AppLanguage.EN))
        assertEquals("Reclamation Algorithm", TaskTypeInfo.RECLAMATION.defaultName(AppSettingsManager.AppLanguage.EN))
    }
}
