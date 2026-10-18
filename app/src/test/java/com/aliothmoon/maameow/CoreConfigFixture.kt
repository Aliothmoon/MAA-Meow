package com.aliothmoon.maameow

import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.serialization.json.*
import org.junit.Assert.*
import java.io.File

/** Required-field contract from Core v6.19.0 Config/GeneralConfig.cpp (independent file parsing). */
internal object CoreConfigFixture {
    fun text(names: String = "{}") = """{
        "version":"6.19.0",
        "options":{
            "taskDelay":0,
            "SSSFightScreencapInterval":100,
            "RoguelikeFightScreencapInterval":100,
            "CopilotFightScreencapInterval":100,
            "controlDelayRange":[0,0]
        },
        "packageName":$names,
        "connection":[]
    }""".trimIndent()

    fun assertLoadable(file: File) {
        val config = JsonUtils.common.parseToJsonElement(file.readText()).jsonObject
        assertTrue("${file.path}: version", config["version"]?.jsonPrimitive?.isString == true)
        val options = config["options"] as? JsonObject ?: fail("${file.path}: options missing").let { error("options") }
        for (key in listOf("taskDelay", "SSSFightScreencapInterval", "RoguelikeFightScreencapInterval", "CopilotFightScreencapInterval")) {
            assertNotNull("${file.path}: options.$key", options[key]?.jsonPrimitive?.intOrNull)
        }
        val delays = options["controlDelayRange"] as? JsonArray
        assertEquals(2, delays?.size)
        assertTrue(delays!!.all { it.jsonPrimitive.intOrNull != null })
        assertTrue("${file.path}: packageName", config["packageName"] is JsonObject)
        assertTrue(config["packageName"]!!.jsonObject.values.all { it is JsonPrimitive && it.isString })
        assertTrue("${file.path}: connection", config["connection"] is JsonArray)
    }
}
