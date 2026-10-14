package com.aliothmoon.maameow.remote.internal.display

import android.companion.AssociationInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.third.Ln
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** VDM 使用的临时 Companion Device 关联及其角色授权。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object CompanionAssociation {
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val STREAMING_PROFILE = "android.app.role.COMPANION_DEVICE_APP_STREAMING"
    private const val COMMAND_TIMEOUT_SECONDS = 5L
    // 带 profile 的关联要等 RoleController 异步回调后才会入库；AOSP 的远端请求
    // 超时是 15 秒，轮询预算必须覆盖它，避免超时回落后关联又迟到写入。
    private const val ASSOCIATION_TIMEOUT_MS = 17_000L
    // cmd 本地超时时，system_server 的 RoleController 请求仍可能在 15 秒窗口内完成。
    // 失败清理必须覆盖同一完整预算，不能只观察几秒便留下迟到关联。
    private const val FAILED_ENSURE_CLEANUP_GRACE_MS = ASSOCIATION_TIMEOUT_MS
    private const val ASSOCIATION_POLL_MS = 100L

    private const val COMPANION_INTERFACE = "android.companion.ICompanionDeviceManager"

    fun address(userId: Int): String {
        val hash = 31 * BuildConfig.APPLICATION_ID.hashCode() + userId
        return String.format(
            Locale.ROOT,
            "02:4D:41:%02X:%02X:%02X",
            hash ushr 16 and 0xff,
            hash ushr 8 and 0xff,
            hash and 0xff,
        )
    }

    fun ensure(address: String, userId: Int): AssociationInfo {
        find(address, userId)?.let { association ->
            if (!requiresStreamingProfileMigration(association.deviceProfile, Build.VERSION.SDK_INT)) {
                return association
            }
            try {
                remove(address, userId)
                check(awaitRemoval(address, userId)) {
                    "Legacy companion association was not removed before migration"
                }
            } catch (failure: Throwable) {
                Ln.w("Failed to remove legacy companion association before migration", failure)
                throw failure
            }
        }

        val command = buildAssociateCommand(address, userId, Build.VERSION.SDK_INT)
        var requestMayCompleteAsynchronously = false
        try {
            // 命令进程即使超时，system_server 也可能已经受理了异步创建请求。
            runCommand(*command.toTypedArray())
            requestMayCompleteAsynchronously = true
            val association = await(address, userId)
                ?: throw IllegalStateException("Companion association was not created")
            return association
        } catch (failure: Throwable) {
            // 已成功提交或本地命令超时时，RoleController 仍可能在 15 秒内迟到写入；
            // 明确的非零退出/进程启动失败则没有异步请求，无需空轮询 17 秒。
            if (requestMayCompleteAsynchronously || failure is CommandTimedOutException) {
                runCatching { cleanupFailedEnsure(address, userId) }
                    .onFailure(failure::addSuppressed)
            }
            throw failure
        }
    }

    internal fun buildAssociateCommand(address: String, userId: Int, sdkInt: Int): List<String> {
        val command = mutableListOf(
            "cmd",
            "companiondevice",
            "associate",
            userId.toString(),
            SHELL_PACKAGE,
            address,
        )
        // Android 14 的 shell 命令只消费前三个参数；15+ 才正式支持 profile 和
        // self-managed 参数。按平台能力组装命令，避免依赖“忽略多余参数”的实现细节。
        if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            command += STREAMING_PROFILE
            command += "false"
        }
        return command
    }

    internal fun requiresStreamingProfileMigration(deviceProfile: String?, sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && deviceProfile != STREAMING_PROFILE

    fun cleanupStale(userId: Int) {
        val address = address(userId)
        if (find(address, userId) == null) return

        remove(address, userId)
        check(awaitRemoval(address, userId)) {
            "Stale companion association was not removed"
        }
        Ln.i("Removed stale VDM companion association")
    }

    fun remove(address: String, userId: Int) {
        if (find(address, userId) == null) return
        runCommand(
            "cmd",
            "companiondevice",
            "disassociate",
            userId.toString(),
            SHELL_PACKAGE,
            address,
        )
    }

    private fun find(address: String, userId: Int): AssociationInfo? =
        associationsForUser(userId).firstOrNull { association ->
            address.equals(
                association.deviceMacAddress?.toString(),
                ignoreCase = true,
            )
        }

    private fun await(address: String, userId: Int): AssociationInfo? {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            val association = find(address, userId)
            if (association != null) return association
            SystemClock.sleep(ASSOCIATION_POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return find(address, userId)
    }

    private fun awaitRemoval(address: String, userId: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            if (find(address, userId) == null) return true
            SystemClock.sleep(ASSOCIATION_POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return find(address, userId) == null
    }

    private fun cleanupFailedEnsure(address: String, userId: Int) {
        val deadline = SystemClock.elapsedRealtime() + FAILED_ENSURE_CLEANUP_GRACE_MS
        do {
            if (find(address, userId) != null) {
                remove(address, userId)
                check(awaitRemoval(address, userId)) {
                    "Failed companion association was not removed"
                }
                return
            }
            SystemClock.sleep(ASSOCIATION_POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        // 封住轮询截止与最终检查之间的边界。
        if (find(address, userId) != null) {
            remove(address, userId)
            check(awaitRemoval(address, userId)) {
                "Late companion association was not removed"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun associationsForUser(userId: Int): List<AssociationInfo> {
        val aidlClass = Class.forName(COMPANION_INTERFACE)
        val aidl = getBinderInterface("companiondevice", aidlClass)
        return aidl.javaClass
            .getMethod("getAssociations", String::class.java, Int::class.javaPrimitiveType)
            .invokeUnwrapped(aidl, SHELL_PACKAGE, userId) as List<AssociationInfo>
    }

    private fun getBinderInterface(serviceName: String, aidlClass: Class<*>): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invokeUnwrapped(null, serviceName) as? IBinder
            ?: throw IllegalStateException("$serviceName service is unavailable")
        val stubClass = Class.forName("${aidlClass.name}\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java)
            .invokeUnwrapped(null, binder)
            ?: throw IllegalStateException("Could not bind $serviceName service")
    }

    private fun runCommand(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw CommandTimedOutException("${command.joinToString(" ")} timed out")
        }

        val bytes = ByteArrayOutputStream()
        process.inputStream.use { input -> input.copyTo(bytes) }
        val output = bytes.toString(Charsets.UTF_8.name())
        if (process.exitValue() != 0) {
            throw IOException(
                "${command.joinToString(" ")} failed (${process.exitValue()}): " +
                    output.trim()
            )
        }
        return output
    }

    private class CommandTimedOutException(message: String) : IOException(message)
}

private fun java.lang.reflect.Method.invokeUnwrapped(
    receiver: Any?,
    vararg arguments: Any?,
): Any? = try {
    invoke(receiver, *arguments)
} catch (failure: InvocationTargetException) {
    throw failure.targetException
}
