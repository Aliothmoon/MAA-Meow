package com.aliothmoon.maameow.maa;


import com.aliothmoon.maameow.bridge.NativeBridgeLib;
import com.aliothmoon.maameow.remote.internal.ActivityUtils;
import com.aliothmoon.maameow.remote.internal.PrimaryDisplayManager;
import com.aliothmoon.maameow.third.Ln;

import timber.log.Timber;

/**
 * upcall driver
 */
public final class DriverClass {

    private static final String TAG = "DriverClass";
    private static final int FRAME_WAIT_TIMEOUT_MS = 5000;
    private static final int FRAME_WAIT_INTERVAL_MS = 50;

    private DriverClass() {
    }

    public static boolean startApp(String packageName, int displayId, boolean forceStop) {
        if (displayId == PrimaryDisplayManager.DISPLAY_ID) {
            return ActivityUtils.startApp(packageName, displayId, forceStop);
        }
        boolean ret = ActivityUtils.startApp(packageName, displayId, forceStop, true);
        if (ret) {
            awaitFirstFrame();
        }
        return ret;
    }

    private static void awaitFirstFrame() {
        long baseline = NativeBridgeLib.getFrameCount();
        int elapsed = 0;
        while (NativeBridgeLib.getFrameCount() <= baseline && elapsed < FRAME_WAIT_TIMEOUT_MS) {
            try {
                Thread.sleep(FRAME_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            elapsed += FRAME_WAIT_INTERVAL_MS;
        }
        if (elapsed >= FRAME_WAIT_TIMEOUT_MS) {
            Ln.w(TAG + ": awaitFirstFrame timed out after " + FRAME_WAIT_TIMEOUT_MS + "ms");
        }
    }

    public static boolean touchDown(int x, int y, int displayId) {
        Ln.i(TAG + ": touchDown(" + x + ", " + y + ", displayId=" + displayId + ")");
        boolean result = InputControlUtils.down(x, y, displayId);
        Ln.i(TAG + ": touchDown result=" + result);
        return result;
    }

    public static boolean touchMove(int x, int y, int displayId) {
        Ln.i(TAG + ": touchMove(" + x + ", " + y + ", displayId=" + displayId + ")");
        boolean result = InputControlUtils.move(x, y, displayId);
        Ln.i(TAG + ": touchMove result=" + result);
        return result;
    }

    public static boolean touchUp(int x, int y, int displayId) {
        Ln.i(TAG + ": touchUp(" + x + ", " + y + ", displayId=" + displayId + ")");
        boolean result = InputControlUtils.up(x, y, displayId);
        Ln.i(TAG + ": touchUp result=" + result);
        return result;
    }

    public static boolean keyDown(int keyCode, int displayId) {
        Ln.i(TAG + ": keyDown(keyCode=" + keyCode + ", displayId=" + displayId + ")");
        boolean result = InputControlUtils.keyDown(keyCode, displayId);
        Ln.i(TAG + ": keyDown result=" + result);
        return result;
    }

    public static boolean keyUp(int keyCode, int displayId) {
        Ln.i(TAG + ": keyUp(keyCode=" + keyCode + ", displayId=" + displayId + ")");
        boolean result = InputControlUtils.keyUp(keyCode, displayId);
        Ln.i(TAG + ": keyUp result=" + result);
        return result;
    }
}
