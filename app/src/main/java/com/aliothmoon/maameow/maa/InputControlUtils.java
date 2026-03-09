package com.aliothmoon.maameow.maa;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.aliothmoon.maameow.third.Ln;
import com.aliothmoon.maameow.third.wrappers.InputManager;
import com.aliothmoon.maameow.third.wrappers.ServiceManager;


public final class InputControlUtils {

    private static InputManager manager;

    private static InputManager getManager() {
        if (manager == null) {
            manager = ServiceManager.getInputManager();
        }
        return manager;
    }

    private static final int DEFAULT_DEVICE_ID = 0;
    private static final int DEFAULT_SOURCE = InputDevice.SOURCE_TOUCHSCREEN;

    private static final MotionEvent.PointerProperties[] POINTER_PROPERTIES = new MotionEvent.PointerProperties[1];
    private static final MotionEvent.PointerCoords[] POINTER_COORDS = new MotionEvent.PointerCoords[1];

    static {
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_FINGER;
        POINTER_PROPERTIES[0] = props;

        POINTER_COORDS[0] = new MotionEvent.PointerCoords();
    }

    private static long currentDownTime = 0;
    private static boolean touchSessionActive = false;

    private static float lastX = 0;
    private static float lastY = 0;
    private static int lastDisplayId = 0;

    private InputControlUtils() {
    }

    private static void setPointerCoords(float x, float y, float pressure) {
        MotionEvent.PointerCoords coords = POINTER_COORDS[0];
        coords.x = x;
        coords.y = y;
        coords.pressure = pressure;
        coords.size = 1.0f;
    }

    private static MotionEvent obtainTouchEvent(long downTime, long eventTime, int action,
                                                 float x, float y, float pressure) {
        setPointerCoords(x, y, pressure);
        return MotionEvent.obtain(
                downTime, eventTime, action,
                1, POINTER_PROPERTIES, POINTER_COORDS,
                0, 0,
                1.0f, 1.0f,
                DEFAULT_DEVICE_ID, 0, DEFAULT_SOURCE, 0
        );
    }

    private static boolean injectAndRecycle(MotionEvent event, int displayId, int mode) {
        try {
            if (!setDisplayId(event, displayId)) {
                return false;
            }
            return getManager().injectInputEvent(event, mode);
        } finally {
            event.recycle();
        }
    }

    private static void sendUpEventInternal(float x, float y, int displayId) {
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent motionEvent = obtainTouchEvent(currentDownTime, eventTime,
                MotionEvent.ACTION_UP, x, y, 0.0f);
        // 补偿性 UP 用同步模式，确保在后续 DOWN 之前完成
        injectAndRecycle(motionEvent, displayId, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean setDisplayId(InputEvent event, int displayId) {
        return displayId == 0 || InputManager.setDisplayId(event, displayId);
    }

    public static synchronized boolean down(int x, int y, int displayId) {
        // 上一次 DOWN 没有 UP，需要结束上一会话
        if (touchSessionActive) {
            Ln.w("TouchDown: 检测到未结束的触摸会话，自动发送 UP 事件");
            sendUpEventInternal(lastX, lastY, lastDisplayId);
        }

        currentDownTime = SystemClock.uptimeMillis();
        touchSessionActive = true;

        lastX = x;
        lastY = y;
        lastDisplayId = displayId;

        MotionEvent motionEvent = obtainTouchEvent(currentDownTime, currentDownTime,
                MotionEvent.ACTION_DOWN, x, y, 1.0f);
        return injectAndRecycle(motionEvent, displayId, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public static synchronized boolean move(int x, int y, int displayId) {
        if (!touchSessionActive) {
            Ln.w("TouchMove: 没有活跃的触摸会话，忽略移动事件");
            return false;
        }

        lastX = x;
        lastY = y;

        long eventTime = SystemClock.uptimeMillis();

        MotionEvent motionEvent = obtainTouchEvent(currentDownTime, eventTime,
                MotionEvent.ACTION_MOVE, x, y, 1.0f);
        return injectAndRecycle(motionEvent, displayId, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public static synchronized boolean up(int x, int y, int displayId) {
        if (!touchSessionActive) {
            Ln.w("TouchUp: no active session, ignore this event");
            return false;
        }

        long eventTime = SystemClock.uptimeMillis();
        touchSessionActive = false;

        MotionEvent motionEvent = obtainTouchEvent(currentDownTime, eventTime,
                MotionEvent.ACTION_UP, x, y, 0.0f);
        return injectAndRecycle(motionEvent, displayId, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public static boolean keyDown(int keyCode, int displayId) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent keyEvent = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);

        if (!setDisplayId(keyEvent, displayId)) {
            return false;
        }

        return getManager().injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    public static boolean keyUp(int keyCode, int displayId) {
        long upTime = SystemClock.uptimeMillis();
        KeyEvent keyEvent = new KeyEvent(upTime, upTime, KeyEvent.ACTION_UP, keyCode, 0);

        if (!setDisplayId(keyEvent, displayId)) {
            return false;
        }

        return getManager().injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }
}
