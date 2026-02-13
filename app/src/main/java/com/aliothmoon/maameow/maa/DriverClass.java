package com.aliothmoon.maameow.maa;


import com.aliothmoon.maameow.remote.internal.ActivityUtils;
import com.aliothmoon.maameow.third.Ln;

/**
 * upcall driver
 */
public final class DriverClass {

    private static final String TAG = "DriverClass";

    private DriverClass() {
    }

    public static boolean startApp(String packageName, int displayId, boolean forceStop) {
        return ActivityUtils.startApp(packageName, displayId, forceStop);
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
