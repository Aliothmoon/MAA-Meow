package com.aliothmoon.maameow.third.wrappers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Build;
import android.os.IInterface;

import com.aliothmoon.maameow.constant.AndroidVersions;
import com.aliothmoon.maameow.third.FakeContext;
import com.aliothmoon.maameow.third.Ln;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/** 包一层 SystemApi 的 android.companion.virtual.VirtualDeviceManager */
@SuppressLint("PrivateApi,DiscouragedPrivateApi")
@androidx.annotation.RequiresApi(AndroidVersions.API_34_ANDROID_14)
public final class VirtualDeviceManager {

    private static final int LOCK_STATE_ALWAYS_UNLOCKED = 1;
    private static final int POLICY_TYPE_CLIPBOARD = 4;
    private static final int DEVICE_POLICY_CUSTOM = 1;
    private static final int DEVICE_ID_INVALID = -1;

    private final Object manager; // android.companion.virtual.VirtualDeviceManager
    private final Constructor<?> paramsBuilderCtor;
    private final Method setLockStateMethod;
    private final Method setDevicePolicyMethod;
    private final Method buildParamsMethod;
    private final Method createVirtualDeviceMethod;
    private final Method getDevicePolicyMethod;
    private final Method createVirtualDisplayMethod;
    private final Method getDeviceIdMethod;
    private final Method closeMethod;

    private VirtualDeviceManager(Object manager) throws ReflectiveOperationException {
        this.manager = manager;
        Class<?> paramsClass = Class.forName("android.companion.virtual.VirtualDeviceParams");
        Class<?> builderClass = Class.forName("android.companion.virtual.VirtualDeviceParams$Builder");
        Class<?> deviceClass = Class.forName("android.companion.virtual.VirtualDeviceManager$VirtualDevice");
        paramsBuilderCtor = builderClass.getConstructor();
        setLockStateMethod = builderClass.getMethod("setLockState", int.class);
        setDevicePolicyMethod = findMethod(builderClass, "setDevicePolicy", int.class, int.class);
        buildParamsMethod = builderClass.getMethod("build");
        createVirtualDeviceMethod = manager.getClass().getMethod("createVirtualDevice", int.class, paramsClass);
        getDevicePolicyMethod = findMethod(manager.getClass(), "getDevicePolicy", int.class, int.class);
        createVirtualDisplayMethod = deviceClass.getMethod("createVirtualDisplay",
                VirtualDisplayConfig.class, Executor.class, VirtualDisplay.Callback.class);
        getDeviceIdMethod = deviceClass.getMethod("getDeviceId");
        closeMethod = deviceClass.getMethod("close");
    }

    static VirtualDeviceManager create() {
        try {
            IInterface service = ServiceManager.getService(Context.VIRTUAL_DEVICE_SERVICE,
                    "android.companion.virtual.IVirtualDeviceManager");
            if (service == null) {
                throw new IllegalStateException("virtualdevice service unavailable");
            }
            Class<?> aidlClass = Class.forName("android.companion.virtual.IVirtualDeviceManager");
            Constructor<?> ctor = Class.forName("android.companion.virtual.VirtualDeviceManager")
                    .getDeclaredConstructor(aidlClass, Context.class);
            ctor.setAccessible(true);
            return new VirtualDeviceManager(ctor.newInstance(service, FakeContext.get()));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 系统拒绝时原样抛 SecurityException 供降档；15 QPR2+ 共享剪贴板要求 trusted 权限与显示 */
    public VirtualDevice createVirtualDevice(int associationId, boolean alwaysUnlocked, boolean shareClipboard)
            throws ReflectiveOperationException {
        boolean requestSharedClipboard = shareClipboard
                && Build.VERSION.SDK_INT >= AndroidVersions.API_35_ANDROID_15
                && setDevicePolicyMethod != null;
        if (!requestSharedClipboard) {
            return createVirtualDeviceInternal(associationId, alwaysUnlocked, false);
        }
        try {
            return createVirtualDeviceInternal(associationId, alwaysUnlocked, true);
        } catch (SecurityException | IllegalArgumentException e) {
            // 默认策略也失败则非剪贴板所致，直接抛给上层降档
            VirtualDevice device = createVirtualDeviceInternal(associationId, alwaysUnlocked, false);
            Ln.w("VirtualDeviceManager: custom clipboard policy rejected: " + e.getMessage());
            return device;
        }
    }

    private VirtualDevice createVirtualDeviceInternal(
            int associationId, boolean alwaysUnlocked, boolean shareClipboard
    ) throws ReflectiveOperationException {
        try {
            Object builder = paramsBuilderCtor.newInstance();
            if (alwaysUnlocked) {
                setLockStateMethod.invoke(builder, LOCK_STATE_ALWAYS_UNLOCKED);
            }
            if (shareClipboard && setDevicePolicyMethod != null) {
                setDevicePolicyMethod.invoke(builder, POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM);
            }
            Object params = buildParamsMethod.invoke(builder);
            Object device = createVirtualDeviceMethod.invoke(manager, associationId, params);
            if (device == null) {
                throw new IllegalStateException("VDM returned no virtual device");
            }
            return new VirtualDevice(device);
        } catch (InvocationTargetException e) {
            throw ServiceManager.unwrap(e);
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public final class VirtualDevice {
        private final Object device; // android.companion.virtual.VirtualDeviceManager$VirtualDevice

        private VirtualDevice(Object device) {
            this.device = device;
        }

        public VirtualDisplay createVirtualDisplay(VirtualDisplayConfig config) throws ReflectiveOperationException {
            try {
                VirtualDisplay display = (VirtualDisplay) createVirtualDisplayMethod.invoke(device, config, null, null);
                if (display == null) {
                    throw new IllegalStateException("VDM returned no display");
                }
                return display;
            } catch (InvocationTargetException e) {
                throw ServiceManager.unwrap(e);
            }
        }

        public int getDeviceId() throws ReflectiveOperationException {
            try {
                Object id = getDeviceIdMethod.invoke(device);
                return id instanceof Integer ? (Integer) id : DEVICE_ID_INVALID;
            } catch (InvocationTargetException e) {
                throw ServiceManager.unwrap(e);
            }
        }

        public boolean sharesDefaultClipboard(int deviceId) {
            if (deviceId <= Context.DEVICE_ID_DEFAULT
                    || Build.VERSION.SDK_INT < AndroidVersions.API_35_ANDROID_15
                    || getDevicePolicyMethod == null) {
                return false;
            }
            try {
                Object policy = getDevicePolicyMethod.invoke(manager, deviceId, POLICY_TYPE_CLIPBOARD);
                return Integer.valueOf(DEVICE_POLICY_CUSTOM).equals(policy);
            } catch (Exception e) {
                Ln.w("VirtualDeviceManager: getDevicePolicy(CLIPBOARD) failed", e);
                return false;
            }
        }

        public void close() throws ReflectiveOperationException {
            try {
                closeMethod.invoke(device);
            } catch (InvocationTargetException e) {
                throw ServiceManager.unwrap(e);
            }
        }
    }
}
