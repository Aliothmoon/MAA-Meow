package com.aliothmoon.maameow

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBuildLocalOptContractTest {

    @Test
    fun localAbiAndLtoSwitchesAreWired() {
        val gradle = resolve("build.gradle.kts").readText()
        assertTrue(gradle.contains("maa.abi"))
        assertTrue(gradle.contains("maa.nativeLto"))
        assertTrue(gradle.contains("MAA_NATIVE_LTO"))
        assertTrue(gradle.contains("arm64-v8a"))
        assertTrue(gradle.contains("x86_64"))

        val cmake = resolve("src/main/native/CMakeLists.txt").readText()
        assertTrue(cmake.contains("option(MAA_NATIVE_LTO"))
        assertTrue(cmake.contains("add_compile_options(-flto)"))
        assertTrue(cmake.contains("add_link_options(-flto)"))
    }

    private fun resolve(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
        checkNotNull(file) { "File not found for test: $relativePath" }
        return file
    }
}
