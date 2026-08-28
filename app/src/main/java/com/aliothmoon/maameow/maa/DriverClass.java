package com.aliothmoon.maameow.maa;


import com.aliothmoon.maameow.bridge.NativeBridgeLib;
import com.aliothmoon.maameow.remote.internal.ActivityUtils;
import com.aliothmoon.maameow.remote.internal.GameFpsMonitor;
import com.aliothmoon.maameow.remote.internal.PrimaryDisplayManager;
import com.aliothmoon.maameow.third.Ln;

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
            // 部分 ROM（如 One UI）会把游戏从虚拟屏挪回主屏，启动后校验并尝试拉回；
            // 拉不回则快速失败，避免识别对着虚拟屏空转
            ret = ActivityUtils.ensureAppOnDisplay(packageName, displayId);
            if (!ret) {
                Ln.e(TAG + ": " + packageName + " could not be pinned on display " + displayId);
            }
        }
        if (ret) {
            awaitFirstFrame();
            GameFpsMonitor.start(packageName);
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

    /* 触控是热路径（一次滑动几十次 MOVE），坐标由 MaaCore 记，这里只记失败 */
    public static boolean touchDown(int x, int y, int contact, int displayId) {
        boolean result = InputControlUtils.down(x, y, contact, displayId);
        if (!result) {
            Ln.w(TAG + ": touchDown failed (" + x + ", " + y + ", contact=" + contact + ", displayId=" + displayId + ")");
        }
        return result;
    }

    public static boolean touchMove(int x, int y, int contact, int displayId) {
        boolean result = InputControlUtils.move(x, y, contact, displayId);
        if (!result) {
            Ln.w(TAG + ": touchMove failed (" + x + ", " + y + ", contact=" + contact + ", displayId=" + displayId + ")");
        }
        return result;
    }

    public static boolean touchUp(int x, int y, int contact, int displayId) {
        boolean result = InputControlUtils.up(x, y, contact, displayId);
        if (!result) {
            Ln.w(TAG + ": touchUp failed (" + x + ", " + y + ", contact=" + contact + ", displayId=" + displayId + ")");
        }
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
