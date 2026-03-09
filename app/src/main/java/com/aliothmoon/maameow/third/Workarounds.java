package com.aliothmoon.maameow.third;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Looper;

import com.aliothmoon.maameow.constant.AndroidVersions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi,BlockedPrivateApi,SoonBlockedPrivateApi,DiscouragedPrivateApi")
public final class Workarounds {

    private static final Class<?> ACTIVITY_THREAD_CLASS;
    private static final Object ACTIVITY_THREAD;

    static {
        try {
            prepareMainLooper();

            // ActivityThread activityThread = new ActivityThread();
            ACTIVITY_THREAD_CLASS = Class.forName("android.app.ActivityThread");
            Constructor<?> activityThreadConstructor = ACTIVITY_THREAD_CLASS.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            ACTIVITY_THREAD = activityThreadConstructor.newInstance();

            // ActivityThread.sCurrentActivityThread = activityThread;
            Field sCurrentActivityThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, ACTIVITY_THREAD);

            // activityThread.mSystemThread = true;
            Field mSystemThreadField = ACTIVITY_THREAD_CLASS.getDeclaredField("mSystemThread");
            mSystemThreadField.setAccessible(true);
            mSystemThreadField.setBoolean(ACTIVITY_THREAD, true);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Workarounds() {
        // not instantiable
    }

    public static void prepareMainLooper() {
        // Like Looper.prepareMainLooper(), but with quitAllowed set to true
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                @SuppressLint("DiscouragedPrivateApi")
                Field field = Looper.class.getDeclaredField("sMainLooper");
                field.setAccessible(true);
                field.set(null, Looper.myLooper());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }


    public static void apply() {
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
            // On some Samsung devices, DisplayManagerGlobal.getDisplayInfoLocked() calls ActivityThread.currentActivityThread().getConfiguration(),
            // which requires a non-null ConfigurationController.
            // ConfigurationController was introduced in Android 12, so do not attempt to set it on lower versions.
            // <https://github.com/Genymobile/scrcpy/issues/4467>
            // Must be called before fillAppContext() because it is necessary to get a valid system context.
            fillConfigurationController();
        }

        // On ONYX devices, fillAppInfo() breaks video mirroring:
        // <https://github.com/Genymobile/scrcpy/issues/5182>
        boolean mustFillAppInfo = !Build.BRAND.equalsIgnoreCase("ONYX");

        if (mustFillAppInfo) {
            fillAppInfo();
        }

        fillAppContext();
    }

    private static void fillAppInfo() {
        try {
            // ActivityThread.AppBindData appBindData = new ActivityThread.AppBindData();
            Class<?> appBindDataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> appBindDataConstructor = appBindDataClass.getDeclaredConstructor();
            appBindDataConstructor.setAccessible(true);
            Object appBindData = appBindDataConstructor.newInstance();

            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.packageName = FakeContext.PACKAGE_NAME;

            // appBindData.appInfo = applicationInfo;
            Field appInfoField = appBindDataClass.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            appInfoField.set(appBindData, applicationInfo);

            // activityThread.mBoundApplication = appBindData;
            Field mBoundApplicationField = ACTIVITY_THREAD_CLASS.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            mBoundApplicationField.set(ACTIVITY_THREAD, appBindData);
        } catch (Throwable throwable) {
            // this is a workaround, so failing is not an error
            Ln.d("Could not fill app info: " + throwable.getMessage());
        }
    }

    private static void fillAppContext() {
        try {
            Application app = Instrumentation.newApplication(Application.class, FakeContext.get());

            // activityThread.mInitialApplication = app;
            Field mInitialApplicationField = ACTIVITY_THREAD_CLASS.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            mInitialApplicationField.set(ACTIVITY_THREAD, app);
        } catch (Throwable throwable) {
            // this is a workaround, so failing is not an error
            Ln.d("Could not fill app context: " + throwable.getMessage());
        }
    }

    private static void fillConfigurationController() {
        try {
            Class<?> configurationControllerClass = Class.forName("android.app.ConfigurationController");
            Class<?> activityThreadInternalClass = Class.forName("android.app.ActivityThreadInternal");

            // configurationController = new ConfigurationController(ACTIVITY_THREAD);
            Constructor<?> configurationControllerConstructor = configurationControllerClass.getDeclaredConstructor(activityThreadInternalClass);
            configurationControllerConstructor.setAccessible(true);
            Object configurationController = configurationControllerConstructor.newInstance(ACTIVITY_THREAD);

            // ACTIVITY_THREAD.mConfigurationController = configurationController;
            Field configurationControllerField = ACTIVITY_THREAD_CLASS.getDeclaredField("mConfigurationController");
            configurationControllerField.setAccessible(true);
            configurationControllerField.set(ACTIVITY_THREAD, configurationController);
        } catch (Throwable throwable) {
            Ln.d("Could not fill configuration: " + throwable.getMessage());
        }
    }

    static Context getSystemContext() {
        try {
            Method getSystemContextMethod = ACTIVITY_THREAD_CLASS.getDeclaredMethod("getSystemContext");
            return (Context) getSystemContextMethod.invoke(ACTIVITY_THREAD);
        } catch (Throwable throwable) {
            // this is a workaround, so failing is not an error
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            pw.flush();
            Ln.d("Could not get system context: " + sw);
            return null;
        }
    }

}
