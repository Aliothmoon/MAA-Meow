package com.aliothmoon.maameow.remote.internal

import android.os.Build
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.remote.canReuseActiveDisplay
import com.aliothmoon.maameow.remote.startBackgroundDisplay
import com.aliothmoon.maameow.third.Ln
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VdmDisplayControllerTest {

    private companion object {
        const val LEGACY_DISPLAY_ID = 12
        const val VDM_DISPLAY_ID = 13
        const val MAIN_USER_ID = 0
        const val WORK_PROFILE_USER_ID = 10
        const val SHELL_PACKAGE = "com.android.shell"
        const val ASSOCIATION_ADDRESS = "02:00:00:00:00:01"
        const val STREAMING_PROFILE = "android.app.role.COMPANION_DEVICE_APP_STREAMING"
        const val OTHER_PROFILE = "android.app.role.COMPANION_DEVICE_WATCH"
    }

    @Test
    fun reuseRequiresActiveDisplayAndSameBackend() {
        assertFalse("没有活动显示时不可复用", canReuse(activeDisplayId = DefaultDisplayConfig.DISPLAY_NONE))
        assertFalse("Legacy 活动显示不能被 VDM 复用", canReuse(activeUsesVdm = false, requestedUsesVdm = true))
        assertFalse("VDM 活动显示不能被 Legacy 复用", canReuse(activeUsesVdm = true, requestedUsesVdm = false))
    }

    @Test
    fun legacyIsReusedByAnyUserButVdmOnlyByTheSameUser() {
        assertTrue("Legacy 后端不区分用户", canReuse(activeUsesVdm = false, requestedUserId = WORK_PROFILE_USER_ID))
        assertTrue("VDM 同用户可复用", canReuse(activeUsesVdm = true, requestedUserId = MAIN_USER_ID))
        assertFalse("VDM 跨用户不可复用", canReuse(activeUsesVdm = true, requestedUserId = WORK_PROFILE_USER_ID))
    }

    @Test
    fun associateCommandDependsOnAndroidVersion() {
        assertEquals(
            listOf("cmd", "companiondevice", "associate", "10", SHELL_PACKAGE, ASSOCIATION_ADDRESS),
            associateCommand(userId = WORK_PROFILE_USER_ID, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
        val withStreamingProfile = listOf(
            "cmd", "companiondevice", "associate", "0", SHELL_PACKAGE, ASSOCIATION_ADDRESS,
            STREAMING_PROFILE, "false",
        )
        assertEquals(withStreamingProfile, associateCommand(sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertEquals(withStreamingProfile, associateCommand(sdkInt = Build.VERSION_CODES.BAKLAVA))
    }

    @Test
    fun profileMigrationOnlyTargetsStreamingProfileOnAndroid15Plus() {
        assertFalse(VdmDisplayController.requiresProfileMigration(profile = null, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertTrue(VdmDisplayController.requiresProfileMigration(profile = null, sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertTrue(VdmDisplayController.requiresProfileMigration(profile = OTHER_PROFILE, sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertFalse(VdmDisplayController.requiresProfileMigration(profile = STREAMING_PROFILE, sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertTrue(VdmDisplayController.requiresProfileMigration(profile = null, sdkInt = Build.VERSION_CODES.BAKLAVA))
        assertFalse(VdmDisplayController.requiresProfileMigration(profile = STREAMING_PROFILE, sdkInt = Build.VERSION_CODES.BAKLAVA))
    }

    @Test
    fun cleanupReleasesDisplayThenDeviceThenAssociation() {
        val calls = mutableListOf<String>()

        val failure = VdmDisplayController.cleanupInReverseOrder(
            releaseDisplay = { calls += "releaseDisplay" },
            closeVirtualDevice = { calls += "closeVirtualDevice" },
            removeAssociation = { calls += "removeAssociation" },
        )

        assertNull(failure)
        assertEquals(listOf("releaseDisplay", "closeVirtualDevice", "removeAssociation"), calls)
    }

    @Test
    fun cleanupKeepsFirstFailureAndStillRunsLaterStages() = withStubbedLogging {
        val releaseFailure = IllegalStateException("display already released")
        val closeFailure = IllegalStateException("device close failed")
        val calls = mutableListOf<String>()

        val failure = VdmDisplayController.cleanupInReverseOrder(
            releaseDisplay = {
                calls += "releaseDisplay"
                throw releaseFailure
            },
            closeVirtualDevice = {
                calls += "closeVirtualDevice"
                throw closeFailure
            },
            removeAssociation = { calls += "removeAssociation" },
        )

        assertEquals(listOf("releaseDisplay", "closeVirtualDevice", "removeAssociation"), calls)
        assertSame("首个失败作为清理结果返回", releaseFailure, failure)
        assertSame("后续失败作为 suppressed 附加", closeFailure, releaseFailure.suppressed.single())

        val associationFailure = IllegalStateException("association was not removed")
        val lastFailure = VdmDisplayController.cleanupInReverseOrder(
            releaseDisplay = {},
            closeVirtualDevice = {},
            removeAssociation = { throw associationFailure },
        )
        assertSame("仅最后一步失败时也返回该异常", associationFailure, lastFailure)
    }

    @Test
    fun legacyIsUsedWhenVdmIsNotRequestedOrUnsupported() {
        var legacyStarts = 0
        val startLegacy: () -> Int = {
            legacyStarts++
            LEGACY_DISPLAY_ID
        }

        assertEquals(
            LEGACY_DISPLAY_ID,
            startBackgroundDisplay(
                useVdm = false,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                startVdm = { error("未请求 VDM 时不得启动 VDM") },
                startLegacy = startLegacy,
                onVdmFailure = { error("未请求 VDM 时不得上报 VDM 失败") },
            ),
        )
        assertEquals("未请求 VDM 时应启动一次 Legacy", 1, legacyStarts)
        assertEquals(
            LEGACY_DISPLAY_ID,
            startBackgroundDisplay(
                useVdm = true,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                startVdm = { error("Android 13 不得尝试 VDM") },
                startLegacy = startLegacy,
                onVdmFailure = { error("Android 13 不得上报 VDM 失败") },
            ),
        )
        assertEquals("Android 13 也应启动一次 Legacy", 2, legacyStarts)
    }

    @Test
    fun vdmBackendIsUsedOnAndroid14() {
        assertEquals(
            VDM_DISPLAY_ID,
            startBackgroundDisplay(
                useVdm = true,
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                startVdm = { VDM_DISPLAY_ID },
                startLegacy = { error("VDM 成功时不得启动 Legacy") },
                onVdmFailure = { error("VDM 成功时不得上报失败") },
            ),
        )
    }

    @Test
    fun vdmFailureDoesNotFallBackToLegacy() {
        val cause = IllegalStateException("association was not created")
        var legacyStarts = 0
        var reportedFailure: Throwable? = null

        val displayId = startBackgroundDisplay(
            useVdm = true,
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            startVdm = { throw cause },
            startLegacy = {
                legacyStarts++
                LEGACY_DISPLAY_ID
            },
            onVdmFailure = { reportedFailure = it },
        )

        assertEquals(DefaultDisplayConfig.DISPLAY_NONE, displayId)
        assertSame("原始异常应回传给调用方", cause, reportedFailure)
        assertEquals("VDM 失败时不得回退到 Legacy 显示", 0, legacyStarts)
    }

    private fun canReuse(
        activeUsesVdm: Boolean = false,
        activeUserId: Int = MAIN_USER_ID,
        activeDisplayId: Int = if (activeUsesVdm) VDM_DISPLAY_ID else LEGACY_DISPLAY_ID,
        requestedUsesVdm: Boolean = activeUsesVdm,
        requestedUserId: Int = activeUserId,
    ): Boolean = canReuseActiveDisplay(
        activeDisplayId = activeDisplayId,
        activeUsesVdm = activeUsesVdm,
        activeVdmUserId = activeUserId,
        requestedUsesVdm = requestedUsesVdm,
        requestedUserId = requestedUserId,
    )

    private fun associateCommand(
        sdkInt: Int,
        userId: Int = MAIN_USER_ID,
    ): List<String> = VdmDisplayController.buildAssociateCommand(
        address = ASSOCIATION_ADDRESS,
        userId = userId,
        sdkInt = sdkInt,
    )

    /**
     * 清理失败路径会经 [Ln.w] 打阶段日志；JVM 单测里的 `android.util.Log` 是桩，
     * 只在覆盖失败路径时挂日志 Mock，并在结束时卸载以避免跨测试污染。
     */
    private fun withStubbedLogging(block: () -> Unit) {
        mockkStatic(Ln::class)
        try {
            every { Ln.w(any<String>()) } just runs
            block()
        } finally {
            unmockkStatic(Ln::class)
        }
    }
}
