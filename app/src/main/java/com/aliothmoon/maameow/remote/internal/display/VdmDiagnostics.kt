package com.aliothmoon.maameow.remote.internal.display

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.view.Display
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager

/** VDM 能力探测和可搜索的单行诊断输出。 */
internal object VdmDiagnostics {
    private const val GROUP_READY_TIMEOUT_MS = 1_000L
    private const val DISPLAY_READY_TIMEOUT_MS = 1_000L
    private const val POLL_INTERVAL_MS = 20L

    fun <T> stage(stage: String, display: Display? = null, action: () -> T): T = try {
        action()
    } catch (failure: VdmProbeException) {
        throw failure
    } catch (failure: Throwable) {
        throw probeFailure(stage, display, failure)
    }

    /** display group 注册可能异步完成，因此在有界时间内轮询确认。 */
    fun awaitIndependentGroup(display: Display): Int {
        val deadline = SystemClock.elapsedRealtime() + GROUP_READY_TIMEOUT_MS
        var groupId: Int? = null
        var lastFailure: Throwable? = null
        do {
            try {
                groupId = ServiceManager.getDisplayManager().getDisplayGroupId(display.displayId)
                if (groupId > 0) return groupId
            } catch (failure: Throwable) {
                lastFailure = failure
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        throw VdmProbeException(
            stage = "display_group",
            displayId = display.displayId,
            groupId = groupId,
            displayState = display.state,
            cause = lastFailure,
        )
    }

    fun awaitDisplayOn(display: Display) {
        val deadline = SystemClock.elapsedRealtime() + DISPLAY_READY_TIMEOUT_MS
        do {
            if (display.state == Display.STATE_ON) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        throw VdmProbeException(
            stage = "display_state",
            displayId = display.displayId,
            groupId = readGroupId(display),
            displayState = display.state,
        )
    }

    fun reportRemoteSuccess(displayId: Int, legacyId: Int, imePolicy: String) {
        val groupId = runCatching {
            ServiceManager.getDisplayManager().getDisplayGroupId(displayId)
        }.getOrNull()
        Ln.i(
            commonPrefix("selected") +
                " owner=shell-sidecar stage=complete legacyId=$legacyId displayId=$displayId" +
                " groupId=${groupId ?: "unknown"} imePolicy=$imePolicy"
        )
    }

    fun reportFallback(failure: Throwable, display: Display?, legacyId: Int) {
        val probe = failure as? VdmProbeException
        val cause = probe?.cause ?: failure
        Ln.e(
            commonPrefix("fallback") +
                " stage=${probe?.stage ?: "unknown"} legacyId=$legacyId" +
                " displayId=${probe?.displayId ?: display?.displayId ?: "none"}" +
                " groupId=${probe?.groupId ?: display?.let(::readGroupId) ?: "unknown"}" +
                " state=${probe?.displayState ?: display?.state ?: "unknown"}" +
                " error=${cause.javaClass.simpleName}:${singleLine(cause.message)}"
        )
    }

    private fun probeFailure(stage: String, display: Display?, cause: Throwable) =
        VdmProbeException(
            stage = stage,
            displayId = display?.displayId,
            groupId = display?.let(::readGroupId),
            displayState = display?.state,
            cause = cause,
        )

    private fun readGroupId(display: Display): Int? = runCatching {
        ServiceManager.getDisplayManager().getDisplayGroupId(display.displayId)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun commonPrefix(result: String): String {
        val identity = when (Process.myUid()) {
            Process.ROOT_UID -> "root-controller"
            Process.SHELL_UID -> "shell-process"
            else -> "unsupported-uid-${Process.myUid()}"
        }
        val effectiveUid = runCatching { Os.geteuid() }.getOrDefault(-1)
        return "VDM_DIAG result=$result identity=$identity euid=$effectiveUid" +
            " sdk=${Build.VERSION.SDK_INT} manufacturer=${singleLine(Build.MANUFACTURER)}" +
            " model=${singleLine(Build.MODEL)}"
    }

    private fun singleLine(value: String?): String = value.orEmpty()
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(' ', '_')
        .ifEmpty { "none" }
}

internal class VdmProbeException(
    val stage: String,
    val displayId: Int? = null,
    val groupId: Int? = null,
    val displayState: Int? = null,
    cause: Throwable? = null,
) : Exception(
    "VDM probe failed at $stage" +
        " (displayId=${displayId ?: "none"}, groupId=${groupId ?: "unknown"}, " +
        "state=${displayState ?: "unknown"})",
    cause,
)
