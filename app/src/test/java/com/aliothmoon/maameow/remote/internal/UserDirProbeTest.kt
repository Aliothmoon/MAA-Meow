package com.aliothmoon.maameow.remote.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/** AsstSetUserDir 前的探测：不可访问只能返回原因，绝不能把进程带崩 */
class UserDirProbeTest {

    @Test
    fun probe_passesOnWritableDirectory_andLeavesNoProbeFile() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            assertNull(UserDirProbe.probe(dir))
            assertTrue(File(dir, "debug").isDirectory)
            assertFalse(File(dir, "debug/.probe").exists())
            // 独立目录首次 setup 时 resource 还没解压，缺了必须放行
            assertFalse(File(dir, "resource").exists())
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
            assertFalse(File(dir, "resource").exists())
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

    @Test
    fun probe_passesWhenNestedResourceIsReadable_andLeavesNoProbeFile() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val model = File(dir, "resource/onnx/model.onnx")
            model.parentFile!!.mkdirs()
            val bytes = byteArrayOf(0x08, 0x01)
            model.writeBytes(bytes)
            assertNull(UserDirProbe.probe(dir))
            assertTrue(File(dir, "debug").isDirectory)
            assertFalse(File(dir, "debug/.probe").exists())
            assertFalse(File(dir, "resource/.probe").exists())
            assertTrue(model.readBytes().contentEquals(bytes))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_rejectsUnlistableResourceDirectory() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        val resource = File(dir, "resource")
        try {
            val onnx = File(resource, "onnx")
            onnx.mkdirs()
            File(onnx, "model.onnx").writeBytes(byteArrayOf(1))
            denyAccess(resource)
            // 目录本身 stat 得到，子项因为进不去而 exists()==false，不能当成没有资源
            assertTrue(resource.isDirectory)
            assertFalse(File(resource, "onnx").exists())
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("cannot list"))
            assertTrue(failure, failure.contains(resource.path))
        } finally {
            allowOwner(resource)
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_rejectsUnlistableOnnxDirectory() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        val onnx = File(dir, "resource/onnx")
        try {
            onnx.mkdirs()
            File(onnx, "model.onnx").writeBytes(byteArrayOf(1))
            denyAccess(onnx)
            assertTrue(File(dir, "resource").canRead())
            assertFalse(File(onnx, "model.onnx").exists())
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("cannot list"))
            assertTrue(failure, failure.contains(onnx.path))
        } finally {
            allowOwner(onnx)
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_rejectsModelThatCannotBeOpened() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        val model = File(dir, "resource/onnx/model.onnx")
        try {
            model.parentFile!!.mkdirs()
            model.writeBytes(byteArrayOf(9, 9))
            denyAccess(model)
            assertFalse(model.canRead())
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("open failed"))
            assertTrue(failure, failure.contains(model.path))
        } finally {
            allowOwner(model)
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_failsWhenListingReturnsNullEvenIfReadable() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val hidden = object : File(dir.absolutePath) {
                override fun canRead(): Boolean = true
                override fun list(): Array<String>? = null
                override fun listFiles(): Array<File>? = null
            }
            assertTrue(hidden.canRead())
            val failure = UserDirProbe.probe(hidden)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("cannot list"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_returnsFailureWhenListingThrowsSecurityException() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val denied = object : File(dir.absolutePath) {
                override fun list(): Array<String> = throw SecurityException("no list")
                override fun listFiles(): Array<File> = throw SecurityException("no list")
            }
            val failure = UserDirProbe.probe(denied)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("list denied"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_rejectsListedResourceWhenExistsIsFalse() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val listed = object : File(dir.absolutePath) {
                override fun list(): Array<String>? {
                    val real = super.list() ?: return null
                    return if ("resource" in real) real else real + "resource"
                }
            }
            val resource = File(dir, "resource")
            assertFalse(resource.exists())
            assertTrue(listed.list()!!.contains("resource"))
            val failure = UserDirProbe.probe(listed)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("open failed"))
            assertTrue(failure, failure.contains(resource.path))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(timeout = 5_000)
    fun probe_rejectsLinkThatEscapesResourceTree() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        val outside = Files.createTempDirectory("user-dir-outside").toFile()
        try {
            val resource = File(dir, "resource")
            resource.mkdirs()
            File(resource, "onnx").mkdirs()
            File(resource, "onnx/model.onnx").writeBytes(byteArrayOf(1))
            val secret = File(outside, "secret.onnx")
            secret.writeBytes(byteArrayOf(2))
            Files.createSymbolicLink(File(resource, "escape").toPath(), secret.toPath())
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("link escapes"))
            assertTrue(failure, failure.contains("escape"))
            assertFalse(failure.contains(secret.name))
        } finally {
            outside.deleteRecursively()
            dir.deleteRecursively()
        }
    }

    @Test(timeout = 5_000)
    fun probe_doesNotLoopOnSymlinkCycle() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val onnx = File(dir, "resource/onnx")
            onnx.mkdirs()
            val model = File(onnx, "model.onnx")
            model.writeBytes(byteArrayOf(3))
            Files.createSymbolicLink(File(onnx, "loop").toPath(), onnx.toPath())
            assertNull(UserDirProbe.probe(dir))
            assertFalse(File(dir, "debug/.probe").exists())
            assertTrue(model.readBytes().contentEquals(byteArrayOf(3)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(timeout = 5_000)
    fun probe_rejectsSelfReferentialLink() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val onnx = File(dir, "resource/onnx")
            onnx.mkdirs()
            val self = File(onnx, "self").toPath()
            Files.createSymbolicLink(self, self.fileName)
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("link loop"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(timeout = 5_000)
    fun probe_rejectsMutuallyCyclicLinks() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val resource = File(dir, "resource")
            resource.mkdirs()
            Files.createSymbolicLink(File(resource, "a").toPath(), File("b").toPath())
            Files.createSymbolicLink(File(resource, "b").toPath(), File("a").toPath())
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("link loop"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(timeout = 5_000)
    fun probe_rejectsLinkThroughIntermediateLinkOutsideResource() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        val outside = Files.createTempDirectory("user-dir-outside").toFile()
        try {
            val resource = File(dir, "resource")
            resource.mkdirs()
            File(outside, "secret.onnx").writeBytes(byteArrayOf(2))
            // "aaa" 先于 "zzz" 被列出时，词法上在 resource 内的目标实际要穿过 zzz 走到外面
            Files.createSymbolicLink(File(resource, "zzz").toPath(), outside.toPath())
            Files.createSymbolicLink(File(resource, "aaa").toPath(), File("zzz/secret.onnx").toPath())
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("link escapes"))
            assertFalse(failure.contains("secret.onnx"))
        } finally {
            outside.deleteRecursively()
            dir.deleteRecursively()
        }
    }

    @Test
    fun probe_rejectsResourceThatIsARegularFile() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        try {
            val resource = File(dir, "resource")
            resource.writeBytes(byteArrayOf(1))
            val failure = UserDirProbe.probe(dir)
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("not a directory"))
            assertTrue(failure, failure.contains(resource.path))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun resourceFailure_checksOtherResourceTrees() {
        val dir = Files.createTempDirectory("user-dir-probe").toFile()
        val model = File(dir, "cache/resource/onnx/model.onnx")
        try {
            // 没有 resource 的树与加载侧一样直接跳过
            val overrides = File(dir, "overrides")
            overrides.mkdirs()
            assertNull(UserDirProbe.resourceFailure(overrides))
            model.parentFile!!.mkdirs()
            model.writeBytes(byteArrayOf(4))
            assertNull(UserDirProbe.resourceFailure(File(dir, "cache")))
            denyAccess(model)
            val failure = UserDirProbe.resourceFailure(File(dir, "cache"))
            assertNotNull(failure)
            assertTrue(failure, failure!!.contains("open failed"))
            assertTrue(failure, failure.contains(model.path))
        } finally {
            allowOwner(model)
            dir.deleteRecursively()
        }
    }

    /** root 或不支持 POSIX 权限的文件系统上拒绝不生效，这类用例跳过 */
    private fun denyAccess(file: File) {
        val applied = runCatching { Files.setPosixFilePermissions(file.toPath(), emptySet()) }
        assumeFalse("POSIX permissions unsupported", applied.exceptionOrNull() is UnsupportedOperationException)
        applied.getOrThrow()
        assumeFalse("permission bits not enforced for this process", file.canRead())
    }

    private fun allowOwner(file: File) {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        }
    }
}
