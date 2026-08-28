package com.aliothmoon.maameow.maa;

import android.os.RemoteException;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.aliothmoon.maameow.ITouchEventCallback;
import com.aliothmoon.maameow.third.Ln;
import com.aliothmoon.maameow.third.wrappers.InputManager;
import com.aliothmoon.maameow.third.wrappers.ServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/** 多指触控注入：{@link TouchPointerSequence} 规划 MotionEvent，这里注入并维护槽位 */
public final class InputControlUtils {

    private static final String TAG = "InputControlUtils";

    private static InputManager manager;
    private static volatile ITouchEventCallback touchCallback;

    private static InputManager getManager() {
        if (manager == null) {
            manager = ServiceManager.getInputManager();
        }
        return manager;
    }

    private static final int DEFAULT_DEVICE_ID = 0;
    private static final int DEFAULT_SOURCE = InputDevice.SOURCE_TOUCHSCREEN;

    public static final int SINGLE_CONTACT = 0;

    private static final MotionEvent.PointerProperties[] POINTER_PROPERTIES =
            new MotionEvent.PointerProperties[TouchPointerSequence.MAX_CONTACTS];
    private static final MotionEvent.PointerCoords[] POINTER_COORDS =
            new MotionEvent.PointerCoords[TouchPointerSequence.MAX_CONTACTS];

    static {
        for (int i = 0; i < TouchPointerSequence.MAX_CONTACTS; i++) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;
            POINTER_PROPERTIES[i] = props;
            POINTER_COORDS[i] = new MotionEvent.PointerCoords();
        }
    }

    /** 在场手指；只整体换引用，注入失败时保持与系统侧一致 */
    private static List<TouchPointerSequence.Pointer> slots = Collections.emptyList();
    private static long gestureDownTime = 0;

    private InputControlUtils() {
    }

    /** liftingIndex 为正在抬起的手指（压力置 0），无则传 -1；CANCEL 全部置 0 */
    private static MotionEvent obtainEvent(List<TouchPointerSequence.Pointer> pointers, long eventTime,
                                           int action, int liftingIndex) {
        boolean cancel = (action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_CANCEL;
        int n = pointers.size();
        for (int i = 0; i < n; i++) {
            TouchPointerSequence.Pointer p = pointers.get(i);
            POINTER_PROPERTIES[i].id = p.getContact();
            MotionEvent.PointerCoords coord = POINTER_COORDS[i];
            coord.x = Math.max(0, p.getX());
            coord.y = Math.max(0, p.getY());
            coord.pressure = (cancel || i == liftingIndex) ? 0.0f : 1.0f;
            coord.size = 1.0f;
        }
        return MotionEvent.obtain(
                gestureDownTime, eventTime, action,
                n, POINTER_PROPERTIES, POINTER_COORDS,
                0, 0,
                1.0f, 1.0f,
                DEFAULT_DEVICE_ID, 0, DEFAULT_SOURCE, 0
        );
    }

    /** reportIndex 为本次事件发生变化的手指，触控预览只上报这一根 */
    private static boolean inject(MotionEvent event, int displayId, int mode, int reportIndex) {
        try {
            if (!setDisplayId(event, displayId)) {
                return false;
            }
            notifyTouchCallback(event, reportIndex);
            return getManager().injectInputEvent(event, mode);
        } finally {
            event.recycle();
        }
    }

    public static void setTouchCallback(ITouchEventCallback callback) {
        touchCallback = callback;
    }

    private static void notifyTouchCallback(MotionEvent event, int index) {
        ITouchEventCallback callback = touchCallback;
        if (callback == null) {
            return;
        }
        try {
            callback.onCallback(Math.round(event.getX(index)), Math.round(event.getY(index)),
                    event.getActionMasked(), event.getPointerId(index));
        } catch (RemoteException | RuntimeException e) {
            touchCallback = null;
            Ln.w(TAG + ": touch callback failed, clearing registration", e);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean setDisplayId(InputEvent event, int displayId) {
        return displayId == 0 || InputManager.setDisplayId(event, displayId);
    }

    private static int encodeAction(int masked, int index) {
        if (masked == TouchPointerSequence.ACTION_POINTER_DOWN
                || masked == TouchPointerSequence.ACTION_POINTER_UP) {
            return masked | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        }
        return masked;
    }

    private static List<TouchPointerSequence.Pointer> without(List<TouchPointerSequence.Pointer> pointers,
                                                              int index) {
        List<TouchPointerSequence.Pointer> next = new ArrayList<>(pointers);
        next.remove(index);
        return next;
    }

    /** 清空系统侧触控槽位 */
    private static void cancelGesture(int displayId) {
        if (!slots.isEmpty()) {
            MotionEvent cancel = obtainEvent(slots, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, -1);
            inject(cancel, displayId, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC, 0);
        }
        slots = Collections.emptyList();
    }

    private static boolean injectStep(TouchPointerSequence.Step step, int displayId) {
        if (!step.getOk()) {
            return false;
        }
        if (step.getCancelFirst()) {
            cancelGesture(displayId);
        }
        long now = SystemClock.uptimeMillis();
        if (slots.isEmpty()) {
            gestureDownTime = now;
        }
        int masked = step.getActionMasked();
        int index = step.getChangingIndex();
        List<TouchPointerSequence.Pointer> pointers = step.getPointers();
        boolean isDown = masked == TouchPointerSequence.ACTION_DOWN
                || masked == TouchPointerSequence.ACTION_POINTER_DOWN;
        boolean isUp = masked == TouchPointerSequence.ACTION_UP
                || masked == TouchPointerSequence.ACTION_POINTER_UP;
        List<TouchPointerSequence.Pointer> next = masked == TouchPointerSequence.ACTION_UP
                ? Collections.<TouchPointerSequence.Pointer>emptyList()
                : masked == TouchPointerSequence.ACTION_POINTER_UP ? without(pointers, index) : pointers;

        // DOWN 必须 WAIT_FOR_FINISH，确保起始状态被系统接收
        boolean ok = inject(
                obtainEvent(pointers, now, encodeAction(masked, index), isUp ? index : -1),
                displayId,
                isDown ? InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
                        : InputManager.INJECT_INPUT_EVENT_MODE_ASYNC,
                index);
        // 未送达则槽位不动；之后同 contact 再按下会先整体 CANCEL 自愈
        if (ok) {
            slots = next;
        }
        return ok;
    }

    private static synchronized boolean apply(TouchPointerSequence.Kind kind, int x, int y, int contact,
                                              int displayId) {
        return injectStep(TouchPointerSequence.INSTANCE.plan(kind, slots, contact, x, y), displayId);
    }

    public static boolean down(int x, int y, int contact, int displayId) {
        return apply(TouchPointerSequence.Kind.Down, x, y, contact, displayId);
    }

    public static boolean move(int x, int y, int contact, int displayId) {
        return apply(TouchPointerSequence.Kind.Move, x, y, contact, displayId);
    }

    public static boolean up(int x, int y, int contact, int displayId) {
        return apply(TouchPointerSequence.Kind.Up, x, y, contact, displayId);
    }

    /** 整体取消当前手势，仍按着的手指全部释放 */
    public static synchronized void cancel(int displayId) {
        cancelGesture(displayId);
    }

    public static boolean keyDown(int keyCode, int displayId) {
        long downTime = SystemClock.uptimeMillis();
        KeyEvent keyEvent = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);

        if (!setDisplayId(keyEvent, displayId)) {
            return false;
        }
        return getManager().injectInputEvent(keyEvent, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
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
