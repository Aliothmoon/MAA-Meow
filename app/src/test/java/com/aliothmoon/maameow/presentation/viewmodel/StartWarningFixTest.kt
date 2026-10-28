package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.usecase.TaskStartAcknowledgement
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogFixAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 能就地改掉的前置检查要给修复动作，「仍然启动」才会降为文字按钮 */
class StartWarningFixTest {

    @Test
    fun adbInput_rootEnablesDirectly_shizukuOpensDeveloperSettings() {
        assertEquals(
            PanelDialogFixAction.ENABLE_ADB_INPUT_WITH_ROOT,
            startWarningFix(TaskStartAcknowledgement.ADB_INPUT_RESTRICTED, RemoteBackend.ROOT),
        )
        assertEquals(
            PanelDialogFixAction.OPEN_DEVELOPER_SETTINGS,
            startWarningFix(TaskStartAcknowledgement.ADB_INPUT_RESTRICTED, RemoteBackend.SHIZUKU),
        )
    }

    @Test
    fun displayWarnings_openDisplaySettings() {
        for (ack in listOf(
            TaskStartAcknowledgement.SMART_RESOLUTION_ENABLED,
            TaskStartAcknowledgement.EYE_PROTECTION_ENABLED,
        )) {
            assertEquals(PanelDialogFixAction.OPEN_DISPLAY_SETTINGS, startWarningFix(ack, RemoteBackend.SHIZUKU))
        }
    }

    @Test
    fun gameWarnings_haveNoFix() {
        assertNull(startWarningFix(TaskStartAcknowledgement.GAME_NOT_RUNNING_WITHOUT_WAKE_UP, RemoteBackend.ROOT))
        assertNull(startWarningFix(TaskStartAcknowledgement.GAME_NOT_INSTALLED, RemoteBackend.ROOT))
    }
}
