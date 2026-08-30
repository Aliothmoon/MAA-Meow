package com.aliothmoon.maameow.third.wrappers;

import android.os.Build;
import android.os.IInterface;
import android.os.SystemClock;

import com.aliothmoon.maameow.constant.AndroidVersions;
import com.aliothmoon.maameow.third.FakeContext;
import com.aliothmoon.maameow.third.Ln;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class PowerManager {
    private static final int USER_ACTIVITY_EVENT_OTHER = 0;
    private static final int WAKE_REASON_APPLICATION = 2;
    private static final int GO_TO_SLEEP_REASON_APPLICATION = 2;
    private final IInterface manager;
    private Method isScreenOnMethod;
    private Method userActivityMethod;
    private Method wakeUpMethod;
    private int wakeUpMethodVersion = -1;
    private Method goToSleepMethod;
    private int goToSleepMethodVersion = -1;

    // ───────────────── wakeUp ─────────────────
    // 比注入 KEYCODE_WAKEUP 可靠：不经过 PhoneWindowManager 的按键策略

    private PowerManager(IInterface manager) {
        this.manager = manager;
    }

    static PowerManager create() {
        IInterface manager = ServiceManager.getService("power", "android.os.IPowerManager");
        return new PowerManager(manager);
    }

    /**
     * Binder 已发出后读回包时 AppOps 会走 DeviceConfig → Settings → ContentResolver
     * FakeContext 的 acquireProvider 若被 R8 删掉会 AbstractMethodError，不代表系统没执行
     */
    static boolean isClientSideProviderError(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof AbstractMethodError) {
                return true;
            }
        }
        return false;
    }

    private static boolean handlePowerInvokeError(String name, InvocationTargetException e) {
        if (isClientSideProviderError(e.getCause())) {
            Ln.w(name + "() likely applied; client ContentResolver/AppOps failed: " + e.getCause());
            return true;
        }
        Ln.e("Could not invoke " + name, e);
        return false;
    }

    private Method getIsScreenOnMethod() throws NoSuchMethodException {
        if (isScreenOnMethod == null) {
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                isScreenOnMethod = manager.getClass().getMethod("isDisplayInteractive", int.class);
            } else {
                isScreenOnMethod = manager.getClass().getMethod("isInteractive");
            }
        }
        return isScreenOnMethod;
    }

    public boolean isScreenOn(int displayId) {

        try {
            Method method = getIsScreenOnMethod();
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_34_ANDROID_14) {
                return (boolean) method.invoke(manager, displayId);
            }
            return (boolean) method.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return false;
        }
    }

    // ───────────────── goToSleep ─────────────────

    private Method getUserActivityMethod() throws NoSuchMethodException {
        if (userActivityMethod == null) {
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
                // userActivity(int displayId, long time, int event, int flags);
                userActivityMethod = manager.getClass().getMethod("userActivity", int.class, long.class, int.class, int.class);
            } else {
                // userActivity(long time, int event, int flags);
                userActivityMethod = manager.getClass().getMethod("userActivity", long.class, int.class, int.class);
            }
        }
        return userActivityMethod;
    }

    public void userActivity(int displayId) {
        try {
            Method method = getUserActivityMethod();
            long time = SystemClock.uptimeMillis();
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
                method.invoke(manager, displayId, time, USER_ACTIVITY_EVENT_OTHER, 0);
                return;
            }
            method.invoke(manager, time, USER_ACTIVITY_EVENT_OTHER, 0);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
    }

    private Method getWakeUpMethod() throws NoSuchMethodException {
        if (wakeUpMethod == null) {
            Class<?> cls = manager.getClass();
            try {
                // API 29+: wakeUp(long time, int reason, String details, String opPackageName)
                wakeUpMethod = cls.getMethod("wakeUp", long.class, int.class, String.class, String.class);
                wakeUpMethodVersion = 0;
            } catch (NoSuchMethodException e1) {
                try {
                    // API 28: wakeUp(long time, String reason, String opPackageName)
                    wakeUpMethod = cls.getMethod("wakeUp", long.class, String.class, String.class);
                    wakeUpMethodVersion = 1;
                } catch (NoSuchMethodException e2) {
                    // 兜底: wakeUp(long time)
                    wakeUpMethod = cls.getMethod("wakeUp", long.class);
                    wakeUpMethodVersion = 2;
                }
            }
        }
        return wakeUpMethod;
    }

    /**
     * 反射命中的 wakeUp 重载，-1 表示未找到。
     */
    public int resolveWakeUpVariant() {
        try {
            getWakeUpMethod();
        } catch (NoSuchMethodException e) {
            return -1;
        }
        return wakeUpMethodVersion;
    }

    /**
     * @return 反射调用是否发出；客户端 AppOps/Settings 读失败仍视为已发出，需轮询 isScreenOn
     */
    public boolean wakeUp() {
        try {
            Method method = getWakeUpMethod();
            long time = SystemClock.uptimeMillis();
            switch (wakeUpMethodVersion) {
                case 0:
                    method.invoke(manager, time, WAKE_REASON_APPLICATION, "maameow:wake", FakeContext.PACKAGE_NAME);
                    return true;
                case 1:
                    method.invoke(manager, time, "maameow:wake", FakeContext.PACKAGE_NAME);
                    return true;
                default:
                    method.invoke(manager, time);
                    return true;
            }
        } catch (InvocationTargetException e) {
            return handlePowerInvokeError("wakeUp", e);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke wakeUp", e);
            return false;
        }
    }

    private Method getGoToSleepMethod() throws NoSuchMethodException {
        if (goToSleepMethod == null) {
            Class<?> cls = manager.getClass();
            try {
                // goToSleep(long time, int reason, int flags)
                goToSleepMethod = cls.getMethod("goToSleep", long.class, int.class, int.class);
                goToSleepMethodVersion = 0;
            } catch (NoSuchMethodException e1) {
                // goToSleep(long time)
                goToSleepMethod = cls.getMethod("goToSleep", long.class);
                goToSleepMethodVersion = 1;
            }
        }
        return goToSleepMethod;
    }

    /**
     * @return 反射调用是否发出；客户端 AppOps/Settings 读失败仍视为已发出，需轮询 isScreenOn
     */
    public boolean goToSleep() {
        try {
            Method method = getGoToSleepMethod();
            long time = SystemClock.uptimeMillis();
            if (goToSleepMethodVersion == 0) {
                method.invoke(manager, time, GO_TO_SLEEP_REASON_APPLICATION, 0);
            } else {
                method.invoke(manager, time);
            }
            return true;
        } catch (InvocationTargetException e) {
            return handlePowerInvokeError("goToSleep", e);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke goToSleep", e);
            return false;
        }
    }

}
