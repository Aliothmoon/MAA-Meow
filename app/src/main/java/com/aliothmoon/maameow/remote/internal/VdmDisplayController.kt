package com.aliothmoon.maameow.remote.internal

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.content.Context
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.view.Display
import android.view.Surface
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.bridge.NativeBridgeLib
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Android 14+ VirtualDevice 显示的完整生命周期；调用进程会保持 shell 身份。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object VdmDisplayController {
    private const val TAG = "VdmDisplay"
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val STREAMING_PROFILE = "android.app.role.COMPANION_DEVICE_APP_STREAMING"
    private const val COMPANION_INTERFACE = "android.companion.ICompanionDeviceManager"
    private const val VIRTUAL_DEVICE_INTERFACE = "android.companion.virtual.IVirtualDeviceManager"
    private const val LOCK_STATE_ALWAYS_UNLOCKED = 1
    private const val COMMAND_TIMEOUT_SECONDS = 5L
    private const val ASSOCIATION_TIMEOUT_MS = 17_000L
    private const val DISPLAY_TIMEOUT_MS = 1_000L
    private const val POLL_MS = 100L

    private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
    private const val FLAG_TRUSTED = 1 shl 10
    private const val FLAG_OWN_FOCUS = 1 shl 14
    private const val FLAG_STEAL_TOP_FOCUS_DISABLED = 1 shl 16

    data class DisplayConfig(
        val width: Int = DefaultDisplayConfig.WIDTH,
        val height: Int = DefaultDisplayConfig.HEIGHT,
        val dpi: Int = DefaultDisplayConfig.DPI,
    )

    private data class Session(
        val address: String,
        val userId: Int,
        val device: Any,
        val display: VirtualDisplay,
    )

    private val config = AtomicReference(DisplayConfig())
    private var session: Session? = null

    @Synchronized
    fun setResolution(width: Int, height: Int, dpi: Int) {
        config.set(DisplayConfig(width, height, dpi))
    }

    @Synchronized
    fun start(userId: Int): Int {
        session?.let { return it.display.display.displayId }
        check(Process.myUid() == Process.SHELL_UID && Os.geteuid() == Process.SHELL_UID) {
            "VDM owner must be permanently shell (uid=${Process.myUid()}, euid=${Os.geteuid()})"
        }

        val current = config.get()
        val address = associationAddress(userId)
        var device: Any? = null
        var display: VirtualDisplay? = null
        var associationCreated = false
        try {
            val association = ensureAssociation(address, userId)
            associationCreated = true
            device = createVirtualDevice(association.id)
            val surface = NativeBridgeLib.setupNativeCapturer(current.width, current.height)
            display = createVirtualDisplay(device, surface, current)
            awaitDisplayReady(display.display)
            session = Session(address, userId, device, display)
            Ln.i(
                "$TAG started: displayId=${display.display.displayId}, " +
                    "groupId=${displayGroupId(display.display)}, " +
                    "${current.width}x${current.height}@${current.dpi}, " +
                    "uid=${Process.myUid()}/euid=${Os.geteuid()}"
            )
            return display.display.displayId
        } catch (failure: Throwable) {
            cleanupFailure(failure, display, device, if (associationCreated) address else null, userId)
            NativeBridgeLib.releaseNativeCapturer()
            Ln.e("$TAG start failed", failure)
            throw failure
        }
    }

    @Synchronized
    fun stop() {
        val active = session ?: return
        session = null
        val displayId = active.display.display.displayId
        val failure = cleanupInReverseOrder(
            releaseDisplay = { releaseDisplay(active.display) },
            closeVirtualDevice = { closeVirtualDevice(active.device) },
            removeAssociation = { removeAssociation(active.address, active.userId) },
        )
        NativeBridgeLib.releaseNativeCapturer()
        if (failure == null) {
            Ln.i("$TAG stopped: displayId=$displayId")
        } else {
            Ln.w("$TAG cleanup failed: displayId=$displayId", failure)
        }
    }

    /**
     * 按 display → device → association 逆序释放。
     * 单步失败不中断后续步骤；返回首个失败，后续失败作为 suppressed 附加。
     */
    internal fun cleanupInReverseOrder(
        releaseDisplay: () -> Unit,
        closeVirtualDevice: () -> Unit,
        removeAssociation: () -> Unit,
    ): Throwable? {
        var failure: Throwable? = null
        failure = captureCleanup(failure, "releaseDisplay", releaseDisplay)
        failure = captureCleanup(failure, "closeVirtualDevice", closeVirtualDevice)
        failure = captureCleanup(failure, "removeAssociation", removeAssociation)
        return failure
    }

    @Synchronized
    fun getDisplayId(): Int = session?.display?.display?.displayId
        ?: DefaultDisplayConfig.DISPLAY_NONE

    private fun cleanupFailure(
        failure: Throwable,
        display: VirtualDisplay?,
        device: Any?,
        address: String?,
        userId: Int,
    ) {
        runCatching { display?.let(::releaseDisplay) }.onFailure(failure::addSuppressed)
        runCatching { device?.let(::closeVirtualDevice) }.onFailure(failure::addSuppressed)
        runCatching { address?.let { removeAssociation(it, userId) } }.onFailure(failure::addSuppressed)
    }

    private fun captureCleanup(previous: Throwable?, stage: String, block: () -> Unit): Throwable? =
        try {
            block()
            previous
        } catch (failure: Throwable) {
            Ln.w("$TAG: $stage failed during stop: ${failure.message}")
            previous?.apply { addSuppressed(failure) } ?: failure
        }

    private fun releaseDisplay(display: VirtualDisplay) {
        try {
            display.setSurface(null)
        } finally {
            display.release()
        }
    }

    private fun associationAddress(userId: Int): String {
        val hash = 31 * BuildConfig.APPLICATION_ID.hashCode() + userId
        return String.format(
            Locale.ROOT,
            "02:4D:41:%02X:%02X:%02X",
            /** 计算一个虚拟 MAC 地址以供 VDM 调用。 */
            hash ushr 16 and 0xff,
            hash ushr 8 and 0xff,
            hash and 0xff,
        )
    }

    private fun ensureAssociation(address: String, userId: Int): AssociationInfo {
        findAssociation(address, userId)?.let { existing ->
            if (!requiresProfileMigration(existing.deviceProfile, Build.VERSION.SDK_INT)) {
                Ln.i("$TAG: association reused id=${existing.id} addr=$address")
                return existing
            }
            Ln.i(
                "$TAG: association profile migration: ${existing.deviceProfile} -> " +
                    "$STREAMING_PROFILE, disassociate id=${existing.id}"
            )
            removeAssociation(address, userId)
        }

        var requestMayFinishLate = false
        try {
            runCommand(*buildAssociateCommand(address, userId, Build.VERSION.SDK_INT).toTypedArray())
            requestMayFinishLate = true
            val created = awaitAssociation(address, userId)
                ?: error("Companion association was not created")
            Ln.i("$TAG: association created id=${created.id} addr=$address")
            return created
        } catch (failure: Throwable) {
            if (requestMayFinishLate || failure is CommandTimedOutException) {
                runCatching { removeLateAssociation(address, userId) }
                    .onFailure(failure::addSuppressed)
            }
            throw failure
        }
    }

    internal fun buildAssociateCommand(address: String, userId: Int, sdkInt: Int): List<String> =
        buildList {
            addAll(listOf("cmd", "companiondevice", "associate", userId.toString(), SHELL_PACKAGE, address))
            if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(STREAMING_PROFILE)
                add("false")
            }
        }

    internal fun requiresProfileMigration(profile: String?, sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && profile != STREAMING_PROFILE

    private fun findAssociation(address: String, userId: Int): AssociationInfo? =
        associationsForUser(userId).firstOrNull {
            address.equals(it.deviceMacAddress?.toString(), ignoreCase = true)
        }

    private fun awaitAssociation(address: String, userId: Int): AssociationInfo? {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            val association = findAssociation(address, userId)
            if (association != null) return association
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return findAssociation(address, userId)
    }

    private fun awaitAssociationRemoval(address: String, userId: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            if (findAssociation(address, userId) == null) return true
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        return findAssociation(address, userId) == null
    }

    private fun removeAssociation(address: String, userId: Int) {
        if (findAssociation(address, userId) == null) return
        runCommand("cmd", "companiondevice", "disassociate", userId.toString(), SHELL_PACKAGE, address)
        check(awaitAssociationRemoval(address, userId)) {
            "Companion association was not removed"
        }
        Ln.i("$TAG: association removed addr=$address")
    }

    private fun removeLateAssociation(address: String, userId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ASSOCIATION_TIMEOUT_MS
        do {
            if (findAssociation(address, userId) != null) {
                removeAssociation(address, userId)
                return
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        if (findAssociation(address, userId) != null) removeAssociation(address, userId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun associationsForUser(userId: Int): List<AssociationInfo> {
        val aidlClass = Class.forName(COMPANION_INTERFACE)
        val aidl = binderInterface("companiondevice", aidlClass)
        return aidl.javaClass
            .getMethod("getAssociations", String::class.java, Int::class.javaPrimitiveType)
            .invokeUnwrapped(aidl, SHELL_PACKAGE, userId) as List<AssociationInfo>
    }

    private fun createVirtualDevice(associationId: Int): Any = try {
        createVirtualDevice(associationId, alwaysUnlocked = true)
    } catch (failure: SecurityException) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) throw failure
        Ln.w("$TAG: always-unlocked VDM rejected; retrying with the platform default", failure)
        try {
            createVirtualDevice(associationId, alwaysUnlocked = false).also {
                Ln.i("$TAG: virtual device created without always-unlocked lock state")
            }
        } catch (retryFailure: Throwable) {
            retryFailure.addSuppressed(failure)
            throw retryFailure
        }
    }

    private fun createVirtualDevice(associationId: Int, alwaysUnlocked: Boolean): Any {
        val paramsClass = Class.forName("android.companion.virtual.VirtualDeviceParams")
        val builderClass = Class.forName("android.companion.virtual.VirtualDeviceParams\$Builder")
        val builder = builderClass.getConstructor().newInstance()
        if (alwaysUnlocked) {
            builderClass.getMethod("setLockState", Int::class.javaPrimitiveType)
                .invokeUnwrapped(builder, LOCK_STATE_ALWAYS_UNLOCKED)
        }
        val params = builderClass.getMethod("build").invokeUnwrapped(builder)
        val aidlClass = Class.forName(VIRTUAL_DEVICE_INTERFACE)
        val managerClass = Class.forName("android.companion.virtual.VirtualDeviceManager")
        val manager = managerClass.getDeclaredConstructor(aidlClass, Context::class.java)
            .apply { isAccessible = true }
            .newInstance(binderInterface(Context.VIRTUAL_DEVICE_SERVICE, aidlClass), FakeContext.get())
        return managerClass.getMethod("createVirtualDevice", Int::class.javaPrimitiveType, paramsClass)
            .invokeUnwrapped(manager, associationId, params) ?: error("VDM returned no virtual device")
    }

    @SuppressLint("WrongConstant")
    private fun createVirtualDisplay(
        device: Any,
        surface: Surface,
        displayConfig: DisplayConfig,
    ): VirtualDisplay {
        val config = VirtualDisplayConfig.Builder(
            DefaultDisplayConfig.VD_NAME,
            displayConfig.width,
            displayConfig.height,
            displayConfig.dpi,
        ).setSurface(surface)
            .setFlags(FLAG_SUPPORTS_TOUCH or FLAG_TRUSTED or FLAG_OWN_FOCUS or FLAG_STEAL_TOP_FOCUS_DISABLED)
            .build()
        return device.javaClass.getMethod(
            "createVirtualDisplay",
            VirtualDisplayConfig::class.java,
            Executor::class.java,
            VirtualDisplay.Callback::class.java,
        ).invokeUnwrapped(device, config, null, null) as? VirtualDisplay
            ?: error("VDM returned no display")
    }

    private fun awaitDisplayReady(display: Display) {
        val deadline = SystemClock.elapsedRealtime() + DISPLAY_TIMEOUT_MS
        do {
            if (displayGroupId(display) > 0 && display.state == Display.STATE_ON) return
            SystemClock.sleep(20L)
        } while (SystemClock.elapsedRealtime() < deadline)
        error(
            "VDM display not ready: id=${display.displayId}, " +
                "group=${runCatching { displayGroupId(display) }.getOrDefault(-1)}, state=${display.state}"
        )
    }

    private fun displayGroupId(display: Display): Int =
        ServiceManager.getDisplayManager().getDisplayGroupId(display.displayId)

    private fun closeVirtualDevice(device: Any) {
        device.javaClass.getMethod("close").invokeUnwrapped(device)
    }

    private fun binderInterface(serviceName: String, aidlClass: Class<*>): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invokeUnwrapped(null, serviceName) as? IBinder
            ?: error("$serviceName service is unavailable")
        val stubClass = Class.forName("${aidlClass.name}\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java)
            .invokeUnwrapped(null, binder) ?: error("Could not bind $serviceName service")
    }

    private fun runCommand(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw CommandTimedOutException("${command.joinToString(" ")} timed out")
        }
        val bytes = ByteArrayOutputStream()
        process.inputStream.use { it.copyTo(bytes) }
        if (process.exitValue() != 0) {
            throw IOException(
                "${command.joinToString(" ")} failed (${process.exitValue()}): " +
                    bytes.toString(Charsets.UTF_8.name()).trim()
            )
        }
    }

    private class CommandTimedOutException(message: String) : IOException(message)
}

private fun java.lang.reflect.Method.invokeUnwrapped(receiver: Any?, vararg args: Any?): Any? = try {
    invoke(receiver, *args)
} catch (failure: InvocationTargetException) {
    throw failure.targetException
}
