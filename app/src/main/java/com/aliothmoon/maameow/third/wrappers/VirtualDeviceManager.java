package com.aliothmoon.maameow.third.wrappers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.IInterface;

import com.aliothmoon.maameow.constant.AndroidVersions;
import com.aliothmoon.maameow.third.FakeContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/** 包一层 SystemApi 的 android.companion.virtual.VirtualDeviceManager */
@SuppressLint("PrivateApi,DiscouragedPrivateApi")
@androidx.annotation.RequiresApi(AndroidVersions.API_34_ANDROID_14)
public final class VirtualDeviceManager {

    // android.companion.virtual.VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED
    private static final int LOCK_STATE_ALWAYS_UNLOCKED = 1;

    private final Object manager; // android.companion.virtual.VirtualDeviceManager
    private final Constructor<?> paramsBuilderCtor;
    private final Method setLockStateMethod;
    private final Method buildParamsMethod;
    private final Method createVirtualDeviceMethod;
    private final Method createVirtualDisplayMethod;
    private final Method closeMethod;

    private VirtualDeviceManager(Object manager) throws ReflectiveOperationException {
        this.manager = manager;
        Class<?> paramsClass = Class.forName("android.companion.virtual.VirtualDeviceParams");
        Class<?> builderClass = Class.forName("android.companion.virtual.VirtualDeviceParams$Builder");
        Class<?> deviceClass = Class.forName("android.companion.virtual.VirtualDeviceManager$VirtualDevice");
        paramsBuilderCtor = builderClass.getConstructor();
        setLockStateMethod = builderClass.getMethod("setLockState", int.class);
        buildParamsMethod = builderClass.getMethod("build");
        createVirtualDeviceMethod = manager.getClass().getMethod("createVirtualDevice", int.class, paramsClass);
        createVirtualDisplayMethod = deviceClass.getMethod("createVirtualDisplay",
                VirtualDisplayConfig.class, Executor.class, VirtualDisplay.Callback.class);
        closeMethod = deviceClass.getMethod("close");
    }

    static VirtualDeviceManager create() {
        try {
            IInterface service = ServiceManager.getService(Context.VIRTUAL_DEVICE_SERVICE,
                    "android.companion.virtual.IVirtualDeviceManager");
            Class<?> aidlClass = Class.forName("android.companion.virtual.IVirtualDeviceManager");
            Constructor<?> ctor = Class.forName("android.companion.virtual.VirtualDeviceManager")
                    .getDeclaredConstructor(aidlClass, Context.class);
            ctor.setAccessible(true);
            return new VirtualDeviceManager(ctor.newInstance(service, FakeContext.get()));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 系统拒绝时原样抛 SecurityException，调用方靠它降档 */
    public VirtualDevice createVirtualDevice(int associationId, boolean alwaysUnlocked) throws ReflectiveOperationException {
        try {
            Object builder = paramsBuilderCtor.newInstance();
            if (alwaysUnlocked) {
                setLockStateMethod.invoke(builder, LOCK_STATE_ALWAYS_UNLOCKED);
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

        public void close() throws ReflectiveOperationException {
            try {
                closeMethod.invoke(device);
            } catch (InvocationTargetException e) {
                throw ServiceManager.unwrap(e);
            }
        }
    }
}
