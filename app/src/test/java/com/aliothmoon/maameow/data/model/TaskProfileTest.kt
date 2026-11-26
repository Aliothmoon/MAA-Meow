package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.utils.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskProfileTest {

    private val json = JsonUtils.common

    @Test
    fun legacyJsonWithoutRunDurationLimit_followsGlobal() {
        val profile = json.decodeFromString<TaskProfile>("""{"id":"a","name":"a","chain":[]}""")

        assertNull(profile.runDurationLimitMinutes)
    }

    @Test
    fun runDurationLimit_roundTrips() {
        val profile = TaskProfile(id = "a", name = "a", chain = emptyList(), runDurationLimitMinutes = 45)

        assertEquals(profile, json.decodeFromString<TaskProfile>(json.encodeToString(profile)))
    }
}
