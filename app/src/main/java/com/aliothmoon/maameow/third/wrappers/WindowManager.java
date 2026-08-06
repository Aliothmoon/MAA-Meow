package com.aliothmoon.maameow.third.wrappers;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.IInterface;

import com.aliothmoon.maameow.constant.AndroidVersions;
import com.aliothmoon.maameow.third.Ln;

import java.lang.reflect.Method;

public final class WindowManager {

    @SuppressWarnings("checkstyle:LineLength")
    // <https://android.googlesource.com/platform/frameworks/base.git/+/2103ff441c66772c80c8560e322dcd9a45be7dcd/core/java/android/view/WindowManager.java#692>
    public static final int DISPLAY_IME_POLICY_LOCAL = 0;
    public static final int DISPLAY_IME_POLICY_FALLBACK_DISPLAY = 1;
    public static final int DISPLAY_IME_POLICY_HIDE = 2;

    private final IInterface manager;
    private Method getRotationMethod;

    private Method freezeDisplayRotationMethod;
    private int freezeDisplayRotationMethodVersion;

    private Method isDisplayRotationFrozenMethod;
    private int isDisplayRotationFrozenMethodVersion;

    private Method thawDisplayRotationMethod;
    private int thawDisplayRotationMethodVersion;

    private Method getDisplayImePolicyMethod;
    private Method setDisplayImePolicyMethod;

    private Method setForcedDisplaySizeMethod;
    private Method clearForcedDisplaySizeMethod;

    private Method syncInputTransactions;

    static WindowManager create() {
        IInterface manager = ServiceManager.getService("window", "android.view.IWindowManager");
        return new WindowManager(manager);
    }

    private WindowManager(IInterface manager) {
        this.manager = manager;
    }

    private Method getGetRotationMethod() throws NoSuchMethodException {
        if (getRotationMethod == null) {
            Class<?> cls = manager.getClass();
            try {
                // method changed since this commit:
                // https://android.googlesource.com/platform/frameworks/base/+/8ee7285128c3843401d4c4d0412cd66e86ba49e3%5E%21/#F2
                getRotationMethod = cls.getMethod("getDefaultDisplayRotation");
            } catch (NoSuchMethodException e) {
                // old version
                getRotationMethod = cls.getMethod("getRotation");
            }
        }
        return getRotationMethod;
    }

    private Method getFreezeDisplayRotationMethod() throws NoSuchMethodException {
        if (freezeDisplayRotationMethod == null) {
            try {
                // Android 15 preview and 14 QPR3 Beta added a String caller parameter for debugging:
                // <https://android.googlesource.com/platform/frameworks/base/+/670fb7f5c0d23cf51ead25538bcb017e03ed73ac%5E%21/>
                freezeDisplayRotationMethod = manager.getClass().getMethod("freezeDisplayRotation", int.class, int.class, String.class);
                freezeDisplayRotationMethodVersion = 0;
            } catch (NoSuchMethodException e) {
                try {
                    // New method added by this commit:
                    // <https://android.googlesource.com/platform/frameworks/base/+/90c9005e687aa0f63f1ac391adc1e8878ab31759%5E%21/>
                    freezeDisplayRotationMethod = manager.getClass().getMethod("freezeDisplayRotation", int.class, int.class);
                    freezeDisplayRotationMethodVersion = 1;
                } catch (NoSuchMethodException e1) {
                    freezeDisplayRotationMethod = manager.getClass().getMethod("freezeRotation", int.class);
                    freezeDisplayRotationMethodVersion = 2;
                }
            }
        }
        return freezeDisplayRotationMethod;
    }

    private Method getIsDisplayRotationFrozenMethod() throws NoSuchMethodException {
        if (isDisplayRotationFrozenMethod == null) {
            try {
                // New method added by this commit:
                // <https://android.googlesource.com/platform/frameworks/base/+/90c9005e687aa0f63f1ac391adc1e8878ab31759%5E%21/>
                isDisplayRotationFrozenMethod = manager.getClass().getMethod("isDisplayRotationFrozen", int.class);
                isDisplayRotationFrozenMethodVersion = 0;
            } catch (NoSuchMethodException e) {
                isDisplayRotationFrozenMethod = manager.getClass().getMethod("isRotationFrozen");
                isDisplayRotationFrozenMethodVersion = 1;
            }
        }
        return isDisplayRotationFrozenMethod;
    }

    private Method getThawDisplayRotationMethod() throws NoSuchMethodException {
        if (thawDisplayRotationMethod == null) {
            try {
                // Android 15 preview and 14 QPR3 Beta added a String caller parameter for debugging:
                // <https://android.googlesource.com/platform/frameworks/base/+/670fb7f5c0d23cf51ead25538bcb017e03ed73ac%5E%21/>
                thawDisplayRotationMethod = manager.getClass().getMethod("thawDisplayRotation", int.class, String.class);
                thawDisplayRotationMethodVersion = 0;
            } catch (NoSuchMethodException e) {
                try {
                    // New method added by this commit:
                    // <https://android.googlesource.com/platform/frameworks/base/+/90c9005e687aa0f63f1ac391adc1e8878ab31759%5E%21/>
                    thawDisplayRotationMethod = manager.getClass().getMethod("thawDisplayRotation", int.class);
                    thawDisplayRotationMethodVersion = 1;
                } catch (NoSuchMethodException e1) {
                    thawDisplayRotationMethod = manager.getClass().getMethod("thawRotation");
                    thawDisplayRotationMethodVersion = 2;
                }
            }
        }
        return thawDisplayRotationMethod;
    }


    private Method getSetForcedDisplaySizeMethod() throws NoSuchMethodException {
        if (setForcedDisplaySizeMethod == null) {
            setForcedDisplaySizeMethod = manager.getClass().getMethod("setForcedDisplaySize", int.class, int.class, int.class);
        }
        return setForcedDisplaySizeMethod;
    }

    private Method getClearForcedDisplaySizeMethod() throws NoSuchMethodException {
        if (clearForcedDisplaySizeMethod == null) {
            clearForcedDisplaySizeMethod = manager.getClass().getMethod("clearForcedDisplaySize", int.class);
        }
        return clearForcedDisplaySizeMethod;
    }


    public boolean setForcedDisplaySize(int displayId, int width, int height) {
        try {
            Method method = getSetForcedDisplaySizeMethod();
            method.invoke(manager, displayId, width, height);
            return true;
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
        return false;
    }

    public boolean clearForcedDisplaySize(int displayId) {
        try {
            Method method = getClearForcedDisplaySizeMethod();
            method.invoke(manager, displayId);
            return true;
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
        return false;
    }


    public int getRotation() {
        try {
            Method method = getGetRotationMethod();
            return (int) method.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return 0;
        }
    }

    public void freezeRotation(int displayId, int rotation) {
        try {
            Method method = getFreezeDisplayRotationMethod();
            switch (freezeDisplayRotationMethodVersion) {
                case 0:
                    method.invoke(manager, displayId, rotation, "scrcpy#freezeRotation");
                    break;
                case 1:
                    method.invoke(manager, displayId, rotation);
                    break;
                default:
                    if (displayId != 0) {
                        Ln.e("Secondary display rotation not supported on this device");
                        return;
                    }
                    method.invoke(manager, rotation);
                    break;
            }
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
    }

    public boolean isRotationFrozen(int displayId) {
        try {
            Method method = getIsDisplayRotationFrozenMethod();
            switch (isDisplayRotationFrozenMethodVersion) {
                case 0:
                    return (boolean) method.invoke(manager, displayId);
                default:
                    if (displayId != 0) {
                        Ln.e("Secondary display rotation not supported on this device");
                        return false;
                    }
                    return (boolean) method.invoke(manager);
            }
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return false;
        }
    }

    public void thawRotation(int displayId) {
        try {
            Method method = getThawDisplayRotationMethod();
            switch (thawDisplayRotationMethodVersion) {
                case 0:
                    method.invoke(manager, displayId, "scrcpy#thawRotation");
                    break;
                case 1:
                    method.invoke(manager, displayId);
                    break;
                default:
                    if (displayId != 0) {
                        Ln.e("Secondary display rotation not supported on this device");
                        return;
                    }
                    method.invoke(manager);
                    break;
            }
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
    }

//    @TargetApi(AndroidVersions.API_30_ANDROID_11)
//    public int[] registerDisplayWindowListener(IDisplayWindowListener listener) {
//        try {
//            return (int[]) manager.getClass().getMethod("registerDisplayWindowListener", IDisplayWindowListener.class).invoke(manager, listener);
//        } catch (Exception e) {
//            Ln.e("Could not register display window listener", e);
//        }
//        return null;
//    }
//
//    @TargetApi(AndroidVersions.API_30_ANDROID_11)
//    public void unregisterDisplayWindowListener(IDisplayWindowListener listener) {
//        try {
//            manager.getClass().getMethod("unregisterDisplayWindowListener", IDisplayWindowListener.class).invoke(manager, listener);
//        } catch (Exception e) {
//            Ln.e("Could not unregister display window listener", e);
//        }
//    }

    @TargetApi(AndroidVersions.API_29_ANDROID_10)
    private Method getGetDisplayImePolicyMethod() throws NoSuchMethodException {
        if (getDisplayImePolicyMethod == null) {
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
                getDisplayImePolicyMethod = manager.getClass().getMethod("getDisplayImePolicy", int.class);
            } else {
                getDisplayImePolicyMethod = manager.getClass().getMethod("shouldShowIme", int.class);
            }
        }
        return getDisplayImePolicyMethod;
    }

    @TargetApi(AndroidVersions.API_29_ANDROID_10)
    public int getDisplayImePolicy(int displayId) {
        try {
            Method method = getGetDisplayImePolicyMethod();
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
                return (int) method.invoke(manager, displayId);
            }
            boolean shouldShowIme = (boolean) method.invoke(manager, displayId);
            return shouldShowIme ? DISPLAY_IME_POLICY_LOCAL : DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return -1;
        }
    }

    @TargetApi(AndroidVersions.API_29_ANDROID_10)
    private Method getSetDisplayImePolicyMethod() throws NoSuchMethodException {
        if (setDisplayImePolicyMethod == null) {
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
                setDisplayImePolicyMethod = manager.getClass().getMethod("setDisplayImePolicy", int.class, int.class);
            } else {
                setDisplayImePolicyMethod = manager.getClass().getMethod("setShouldShowIme", int.class, boolean.class);
            }
        }
        return setDisplayImePolicyMethod;
    }

    @TargetApi(AndroidVersions.API_29_ANDROID_10)
    public void setDisplayImePolicy(int displayId, int displayImePolicy) {
        try {
            Method method = getSetDisplayImePolicyMethod();
            if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
                method.invoke(manager, displayId, displayImePolicy);
            } else if (displayImePolicy != DISPLAY_IME_POLICY_HIDE) {
                method.invoke(manager, displayId, displayImePolicy == DISPLAY_IME_POLICY_LOCAL);
            } else {
                Ln.w("DISPLAY_IME_POLICY_HIDE is not supported before Android 12");
            }
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
    }

    // ============ Android 14+ 截图支持 ============

    private static Class<?> captureArgsClass;
    private static Class<?> screenCaptureListenerClass;
    private Method captureDisplayMethod;

    /**
     * Android 14+ 截图
     * 调用 IWindowManager.captureDisplay(displayId, captureArgs, listener)
     *
     * @param displayId   显示器 ID (通常为 0)
     * @param captureArgs 截图参数 (可为 null)
     * @param listener    ScreenCaptureListener 实例
     */
    @TargetApi(AndroidVersions.API_34_ANDROID_14)
    public void captureDisplay(int displayId, Object captureArgs, Object listener) {
        try {
            if (captureArgsClass == null) {
                captureArgsClass = Class.forName("android.window.ScreenCapture$CaptureArgs");
            }
            if (screenCaptureListenerClass == null) {
                screenCaptureListenerClass = Class.forName("android.window.ScreenCapture$ScreenCaptureListener");
            }
            if (captureDisplayMethod == null) {
                captureDisplayMethod = manager.getClass().getMethod("captureDisplay",
                        int.class, captureArgsClass, screenCaptureListenerClass);
                captureDisplayMethod.setAccessible(true);
            }
            captureDisplayMethod.invoke(manager, displayId, captureArgs, listener);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke captureDisplay", e);
            throw new RuntimeException("captureDisplay failed", e);
        }
    }

    // ───────────────── Keyguard ─────────────────

    private Method isKeyguardLockedMethod;
    private Method isKeyguardSecureMethod;
    private int isKeyguardSecureMethodVersion;
    private Method dismissKeyguardMethod;

    /** 当前是否处于锁屏（含无密码的滑动锁屏）。反射不可用时返回 null。 */
    public Boolean isKeyguardLocked() {
        try {
            if (isKeyguardLockedMethod == null) {
                isKeyguardLockedMethod = manager.getClass().getMethod("isKeyguardLocked");
            }
            return (boolean) isKeyguardLockedMethod.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke isKeyguardLocked", e);
            return null;
        }
    }

    /** 锁屏是否设了凭证（PIN/密码/图案）。反射不可用时返回 null。 */
    public Boolean isKeyguardSecure(int userId) {
        try {
            if (isKeyguardSecureMethod == null) {
                try {
                    // API 30+ 带 userId
                    isKeyguardSecureMethod = manager.getClass().getMethod("isKeyguardSecure", int.class);
                    isKeyguardSecureMethodVersion = 0;
                } catch (NoSuchMethodException e) {
                    isKeyguardSecureMethod = manager.getClass().getMethod("isKeyguardSecure");
                    isKeyguardSecureMethodVersion = 1;
                }
            }
            if (isKeyguardSecureMethodVersion == 0) {
                return (boolean) isKeyguardSecureMethod.invoke(manager, userId);
            }
            return (boolean) isKeyguardSecureMethod.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke isKeyguardSecure", e);
            return null;
        }
    }

    /**
     * 请求解除锁屏（API 26+）。无凭证锁屏直接解除，有凭证时系统弹出 bouncer。
     *
     * @return 反射调用是否成功；不代表已解锁，需另行轮询 isKeyguardLocked
     */
    @TargetApi(AndroidVersions.API_26_ANDROID_8_0)
    public boolean dismissKeyguard() {
        try {
            if (dismissKeyguardMethod == null) {
                Class<?> callbackClass = Class.forName("com.android.internal.policy.IKeyguardDismissCallback");
                dismissKeyguardMethod = manager.getClass().getMethod("dismissKeyguard", callbackClass, CharSequence.class);
            }
            dismissKeyguardMethod.invoke(manager, null, null);
            return true;
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke dismissKeyguard", e);
            return false;
        }
    }

    private Method lockNowMethod;
    private int lockNowMethodVersion = -1;

    private Method getLockNowMethod() throws NoSuchMethodException {
        if (lockNowMethod == null) {
            Class<?> cls = manager.getClass();
            try {
                // lockNow(Bundle options)
                lockNowMethod = cls.getMethod("lockNow", android.os.Bundle.class);
                lockNowMethodVersion = 0;
            } catch (NoSuchMethodException e) {
                lockNowMethod = cls.getMethod("lockNow");
                lockNowMethodVersion = 1;
            }
        }
        return lockNowMethod;
    }

    /** 立即上锁（弹出 keyguard）。@return 反射调用是否成功 */
    public boolean lockNow() {
        try {
            Method method = getLockNowMethod();
            if (lockNowMethodVersion == 0) {
                method.invoke(manager, new android.os.Bundle());
            } else {
                method.invoke(manager);
            }
            return true;
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke lockNow", e);
            return false;
        }
    }
}
