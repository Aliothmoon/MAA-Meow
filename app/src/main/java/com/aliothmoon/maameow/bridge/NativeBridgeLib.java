package com.aliothmoon.maameow.bridge;

import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;

import com.aliothmoon.maameow.third.Ln;


public class NativeBridgeLib {
    public static boolean LOADED;

    static {
        try {
            System.loadLibrary("bridge");
            LOADED = true;
        } catch (Throwable e) {
            LOADED = false;
            Ln.e("NativeBridgeLib static initializer: ", e);
        }
    }

    // for test
    @SuppressWarnings("JavaJniMissingFunction")
    public static native String ping();


    @SuppressWarnings("JavaJniMissingFunction")
    public static native void initFrameBuffers(int width, int height);


    @SuppressWarnings("JavaJniMissingFunction")
    public static native long copyFrameFromHardwareBuffer(HardwareBuffer buffer);


    @SuppressWarnings("JavaJniMissingFunction")
    public static native void releaseFrameBuffers();

    /**
     * 测试用
     */
    @SuppressWarnings("JavaJniMissingFunction")
    public static native Bitmap getFrameBufferBitmap();

}