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

    public static native void setPreviewSurface(Object surface);

    /** 停掉预览渲染线程并断开 Surface，阻塞到线程退出 */
    public static native void shutdownPreview();

    /**
     * 测试用
     */
    public static native Bitmap getFrameBufferBitmap();

    @FastNative
    public static native long getFrameCount();

    /**
     * 把帧缓冲和预览换成黑帧，换了才返回 true
     * expectedFrameCount 为判定时读到的帧计数，之后来过新帧就不换
     */
    public static native boolean blankFrame(long expectedFrameCount);

}
