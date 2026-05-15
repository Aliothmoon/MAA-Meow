package com.aliothmoon.maameow.third.wrappers;

import android.os.Build;
import android.os.IInterface;
import android.os.SystemClock;

import com.aliothmoon.maameow.constant.AndroidVersions;
import com.aliothmoon.maameow.third.Ln;

import java.lang.reflect.Method;

public final class PowerManager {
    private final IInterface manager;
    private Method isScreenOnMethod;
    private Method userActivityMethod;

    private static final int USER_ACTIVITY_EVENT_OTHER = 0;

    static PowerManager create() {
        IInterface manager = ServiceManager.getService("power", "android.os.IPowerManager");
        return new PowerManager(manager);
    }

    private PowerManager(IInterface manager) {
        this.manager = manager;
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

}
