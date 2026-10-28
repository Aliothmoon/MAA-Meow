package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** AsstSetUserDir 前的探测：不可访问只能返回原因，绝不能把进程带崩 */
class UserDirProbeTest {

    @Test
    fun probe_passesOnWritableDirectory_andLeavesNoProbeFile() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            assertNull(UserDirProbe.probe(dir))
            assertTrue(File(dir, "debug").isDirectory)
            assertFalse(File(dir, "debug/.probe").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_createsMissingUserDir() {
        val parent = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val dir = File(parent, "Maa")
            assertNull(UserDirProbe.probe(dir))
            assertTrue(dir.isDirectory)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun probe_failsWhenPathIsAFile() {
        val file = Files.createTempFile("user-dir-probe", ".txt").toFile()
        try {
            val failure = UserDirProbe.probe(file)
            assertNotNull(failure)
            assertTrue(failure!!.contains("owner of"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun mountOf_picksDeepestCoveringMount() {
        // App 命名空间里 Android/data 是单独 bind 进来的，独立目录落在 /data
        val mountInfo = listOf(
            "2388 28 0:67 / /storage/emulated rw,nosuid master:44 - fuse /dev/fuse rw",
            "8563 8474 0:66 /0/Android/data /storage/emulated/0/Android/data rw master:43 - sdcardfs /data/media rw,gid=1015",
            "30 1 253:5 / /data rw,nosuid - f2fs /dev/block/dm-5 rw",
            "1 0 253:0 / / ro - ext4 /dev/root ro",
        )
        assertEquals(
            "/storage/emulated/0/Android/data sdcardfs",
            UserDirProbe.mountOf("/storage/emulated/0/Android/data/pkg/files/Maa", mountInfo),
        )
        assertEquals("/data f2fs", UserDirProbe.mountOf("/data/local/tmp/maameow", mountInfo))
        // 前缀相同但不是子路径，不能算进去
        assertEquals("/ ext4", UserDirProbe.mountOf("/database", mountInfo))
        assertEquals("?", UserDirProbe.mountOf("/x", emptyList()))
    }
}
