package com.aliothmoon.maameow.bridge;

import android.graphics.Bitmap;
import android.view.Surface;

import com.aliothmoon.maameow.third.Ln;

import dalvik.annotation.optimization.FastNative;

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
    @FastNative
    public static native String ping();

    public static native Surface setupNativeCapturer(int width, int height);

    public static native void releaseNativeCapturer();

    @FastNative
    public static native void setPreviewSurface(Object surface);

    /**
     * 测试用
     */
    public static native Bitmap getFrameBufferBitmap();

    @FastNative
    public static native long getFrameCount();

}
