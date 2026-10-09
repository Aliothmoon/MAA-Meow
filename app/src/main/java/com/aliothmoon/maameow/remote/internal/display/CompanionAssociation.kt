package com.aliothmoon.maameow.remote.internal.display

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.third.FakeContext
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
    private const val USER_ID = 0

    private const val COMMAND_TIMEOUT_SECONDS = 5L
    private const val ASSOCIATION_TIMEOUT_MS = 3_000L
    private const val ASSOCIATION_POLL_MS = 100L

    private const val COMPANION_INTERFACE = "android.companion.ICompanionDeviceManager"

    fun address(): String {
        val hash = BuildConfig.APPLICATION_ID.hashCode()
        return String.format(
            Locale.ROOT,
            "02:4D:41:%02X:%02X:%02X",
            hash ushr 16 and 0xff,
            hash ushr 8 and 0xff,
            hash and 0xff,
        )
    }

    fun ensure(address: String): AssociationInfo {
        find(address)?.let { return it }

        // Android 15+ 会在关联存续期间向 com.android.shell 异步授予 app streaming
        // 角色；命令返回不代表关联已经可见，因此后续轮询结构化状态。
        runCommand(
            "cmd",
            "companiondevice",
            "associate",
            USER_ID.toString(),
            SHELL_PACKAGE,
            address,
            STREAMING_PROFILE,
            "false",
        )
        return await(address)
            ?: throw IllegalStateException("Companion association was not created")
    }

    fun cleanupStale() {
        val address = address()
        if (find(address) == null) return

        remove(address)
        check(awaitRemoval(address)) {
            "Stale companion association was not removed"
        }
        Ln.i("Removed stale VDM companion association")
    }

    fun remove(address: String) {
        if (find(address) == null) return
        runCommand(
            "cmd",
            "companiondevice",
            "disassociate",
            USER_ID.toString(),
            SHELL_PACKAGE,
            address,
        )
    }

    private fun find(address: String): AssociationInfo? =
        companionDeviceManager().myAssociations.firstOrNull { association ->
            address.equals(
                association.deviceMacAddress?.toString(),
                ignoreCase = true,
            )
        }

    private fun await(address: String): AssociationInfo? {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            val association = find(address)
            if (association != null) return association
            SystemClock.sleep(ASSOCIATION_POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return find(address)
    }

    private fun awaitRemoval(address: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            if (find(address) == null) return true
            SystemClock.sleep(ASSOCIATION_POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return find(address) == null
    }

    private fun companionDeviceManager(): CompanionDeviceManager {
        val aidlClass = Class.forName(COMPANION_INTERFACE)
        val aidl = getBinderInterface(Context.COMPANION_DEVICE_SERVICE, aidlClass)
        val constructor = CompanionDeviceManager::class.java
            .getDeclaredConstructor(aidlClass, Context::class.java)
            .apply { isAccessible = true }
        return constructor.newInstance(aidl, FakeContext.get())
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
            throw IOException("${command.joinToString(" ")} timed out")
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
}

private fun java.lang.reflect.Method.invokeUnwrapped(
    receiver: Any?,
    vararg arguments: Any?,
): Any? = try {
    invoke(receiver, *arguments)
} catch (failure: InvocationTargetException) {
    throw failure.targetException
}
