#include "bridge.h"
#include <string>
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <android/bitmap.h>
#include <cinttypes>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <thread>
#include <chrono>
#include <atomic>
#include <cstdint>
#include <mutex>

#define LOG_TAG "LibBridge"

#ifdef NDEBUG
#define LOGD(...) ((void)0)
#else
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static jstring ping(JNIEnv *env, jclass clazz);

static void nativeInitFrameBuffers(JNIEnv *env, jclass clazz, jint width, jint height);

static jlong nativeCopyFrameFromHardwareBuffer(JNIEnv *env, jclass clazz, jobject hardwareBuffer,
                                               jlong timestampNs);

static void nativeReleaseFrameBuffers(JNIEnv *env, jclass clazz);

static jobject nativeGetFrameBufferBitmap(JNIEnv *env, jclass clazz);

static void nativeSetPreviewSurface(JNIEnv *env, jclass clazz, jobject jSurface);

#include <android/native_window.h>
#include <android/native_window_jni.h>

#if defined(__ARM_NEON)

#include <arm_neon.h>

#endif

static void
ProcessFrameData(const uint8_t *src, uint8_t *dstRGBA, uint8_t *dstBGR, int width, int height,
                 int srcStride) {
    for (int y = 0; y < height; ++y) {
        const uint8_t *srcLine = src + y * srcStride;
        uint8_t *dstBGRLine = dstBGR + y * width * 3;
        uint8_t *dstRGBALine = dstRGBA ? (dstRGBA + y * width * 4) : nullptr;

        if (y + 1 < height) __builtin_prefetch(src + (y + 1) * srcStride, 0, 3);

        int x = 0;
#if defined(__ARM_NEON)
        for (; x <= width - 16; x += 16) {
            uint8x16x4_t rgba = vld4q_u8(srcLine + x * 4);

            // 如果需要预览，直接写回 RGBA (此时数据已经在寄存器中，不需要重新加载)
            if (dstRGBALine) {
                vst4q_u8(dstRGBALine + x * 4, rgba);
            }

            // 交换 R 和 B 产出 BGR
            uint8x16x3_t bgr;
            bgr.val[0] = rgba.val[2]; // B
            bgr.val[1] = rgba.val[1]; // G
            bgr.val[2] = rgba.val[0]; // R
            vst3q_u8(dstBGRLine + x * 3, bgr);
        }
#endif
        for (; x < width; ++x) {
            uint8_t r = srcLine[x * 4 + 0];
            uint8_t g = srcLine[x * 4 + 1];
            uint8_t b = srcLine[x * 4 + 2];
            uint8_t a = srcLine[x * 4 + 3];

            if (dstRGBALine) {
                dstRGBALine[x * 4 + 0] = r;
                dstRGBALine[x * 4 + 1] = g;
                dstRGBALine[x * 4 + 2] = b;
                dstRGBALine[x * 4 + 3] = a;
            }

            dstBGRLine[x * 3 + 0] = b;
            dstBGRLine[x * 3 + 1] = g;
            dstBGRLine[x * 3 + 2] = r;
        }
    }
}

static ANativeWindow *g_previewWindow = nullptr;
static jobject g_previewSurfaceObj = nullptr;
static std::mutex g_previewMutex;
static std::atomic<int> g_targetWidth{0};
static std::atomic<int> g_targetHeight{0};

static void nativeSetPreviewSurface(JNIEnv *env, jclass clazz, jobject jSurface) {
    std::lock_guard<std::mutex> lock(g_previewMutex);
    if (g_previewSurfaceObj && env->IsSameObject(jSurface, g_previewSurfaceObj)) return;
    if (g_previewWindow) {
        ANativeWindow_release(g_previewWindow);
        g_previewWindow = nullptr;
    }
    if (g_previewSurfaceObj) {
        env->DeleteGlobalRef(g_previewSurfaceObj);
        g_previewSurfaceObj = nullptr;
    }
    if (jSurface) {
        g_previewSurfaceObj = env->NewGlobalRef(jSurface);
        g_previewWindow = ANativeWindow_fromSurface(env, jSurface);
        if (g_previewWindow) {
            int w = g_targetWidth.load();
            int h = g_targetHeight.load();
            if (w > 0 && h > 0)
                ANativeWindow_setBuffersGeometry(g_previewWindow, w, h,
                                                 AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM);
            LOGI("Preview connected: %p", g_previewWindow);
        }
    }
}

static void DispatchPreview(const FrameBuffer *target) {
    if (!target || !target->data) return;
    std::lock_guard<std::mutex> lock(g_previewMutex);
    if (!g_previewWindow) return;
    static auto lastPreviewTime = std::chrono::steady_clock::now();
    auto now = std::chrono::steady_clock::now();
    if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastPreviewTime).count() <
        16)
        return;
    ANativeWindow_Buffer outBuffer;
    if (ANativeWindow_lock(g_previewWindow, &outBuffer, nullptr) == 0) {
        if (outBuffer.width == target->width && outBuffer.height == target->height) {
            int dstStride = outBuffer.stride * 4;
            int srcStride = target->stride;
            if (dstStride == srcStride) memcpy(outBuffer.bits, target->data, target->size);
            else {
                int rowBytes = target->width * 4;
                for (int y = 0; y < target->height; ++y)
                    memcpy((uint8_t *) outBuffer.bits + y * dstStride, target->data + y * srcStride,
                           rowBytes);
            }
            lastPreviewTime = now;
        }
        ANativeWindow_unlockAndPost(g_previewWindow);
    }
}

static JNINativeMethod gMethods[] = {
        {"ping",                        "()Ljava/lang/String;",                  (void *) ping},
        {"initFrameBuffers",            "(II)V",                                 (void *) nativeInitFrameBuffers},
        {"copyFrameFromHardwareBuffer", "(Landroid/hardware/HardwareBuffer;J)J", (void *) nativeCopyFrameFromHardwareBuffer},
        {"setPreviewSurface",           "(Ljava/lang/Object;)V",                 (void *) nativeSetPreviewSurface},
        {"releaseFrameBuffers",         "()V",                                   (void *) nativeReleaseFrameBuffers},
        {"getFrameBufferBitmap",        "()Landroid/graphics/Bitmap;",           (void *) nativeGetFrameBufferBitmap},
};

static JavaVM *g_jvm = nullptr;
static jclass g_driver_class = nullptr;
static jmethodID g_touch_down_method = nullptr;
static jmethodID g_touch_move_method = nullptr;
static jmethodID g_touch_up_method = nullptr;
static jmethodID g_key_down_method = nullptr;
static jmethodID g_key_up_method = nullptr;
static jmethodID g_start_app_method = nullptr;

static FrameBuffer g_buffers[FRAME_BUFFER_COUNT] = {
        {nullptr, nullptr, 0, 0, 0, 0, 0, 0, 0},
        {nullptr, nullptr, 0, 0, 0, 0, 0, 0, 0},
        {nullptr, nullptr, 0, 0, 0, 0, 0, 0, 0}
};
static std::atomic<int> g_bufferStates[FRAME_BUFFER_COUNT] = {FRAME_STATE_FREE, FRAME_STATE_FREE,
                                                              FRAME_STATE_FREE};
static std::atomic<int> g_readerCounts[FRAME_BUFFER_COUNT] = {0, 0, 0};
static std::atomic<FrameBuffer *> g_readBuffer{nullptr};
static std::atomic<int64_t> g_frameCount{0};
static bool g_frameBuffersInitialized = false;

static constexpr auto DRIVE_CLAZZ = "com/aliothmoon/maameow/maa/DriverClass";
static constexpr auto NATIVE_BRIDGE_CLAZZ = "com/aliothmoon/maameow/bridge/NativeBridgeLib";

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass nativeLibClass = env->FindClass(NATIVE_BRIDGE_CLAZZ);
    if (nativeLibClass == nullptr ||
        env->RegisterNatives(nativeLibClass, gMethods, std::size(gMethods)) < 0)
        return JNI_ERR;
    jclass driverClass = env->FindClass(DRIVE_CLAZZ);
    if (driverClass) {
        g_driver_class = (jclass) env->NewGlobalRef(driverClass);
        g_touch_down_method = env->GetStaticMethodID(g_driver_class, "touchDown", "(III)Z");
        g_touch_move_method = env->GetStaticMethodID(g_driver_class, "touchMove", "(III)Z");
        g_touch_up_method = env->GetStaticMethodID(g_driver_class, "touchUp", "(III)Z");
        g_key_down_method = env->GetStaticMethodID(g_driver_class, "keyDown", "(II)Z");
        g_key_up_method = env->GetStaticMethodID(g_driver_class, "keyUp", "(II)Z");
        g_start_app_method = env->GetStaticMethodID(g_driver_class, "startApp",
                                                    "(Ljava/lang/String;IZ)Z");
        env->DeleteLocalRef(driverClass);
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    std::lock_guard<std::mutex> lock(g_previewMutex);
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) {
        if (g_driver_class) env->DeleteGlobalRef(g_driver_class);
        if (g_previewSurfaceObj) env->DeleteGlobalRef(g_previewSurfaceObj);
    }
    if (g_previewWindow) ANativeWindow_release(g_previewWindow);
}

static int
UpcallInputControl(JNIEnv *env, MethodType method, int x, int y, int keyCode, int displayId) {
    if (!env) return -1;
    jmethodID targetMethod = nullptr;
    switch (method) {
        case TOUCH_DOWN:
            targetMethod = g_touch_down_method;
            break;
        case TOUCH_MOVE:
            targetMethod = g_touch_move_method;
            break;
        case TOUCH_UP:
            targetMethod = g_touch_up_method;
            break;
        case KEY_DOWN:
            targetMethod = g_key_down_method;
            break;
        case KEY_UP:
            targetMethod = g_key_up_method;
            break;
        default:
            return -1;
    }
    if (method == KEY_DOWN || method == KEY_UP)
        return env->CallStaticBooleanMethod(g_driver_class, targetMethod, keyCode, displayId) ? 0
                                                                                              : -1;
    return env->CallStaticBooleanMethod(g_driver_class, targetMethod, x, y, displayId) ? 0 : -1;
}

static int UpcallStartApp(JNIEnv *env, const char *packageName, int displayId, bool forceStop) {
    if (!env || !packageName) return -1;
    jstring jPackageName = env->NewStringUTF(packageName);
    jboolean result = env->CallStaticBooleanMethod(g_driver_class, g_start_app_method, jPackageName,
                                                   displayId, (jboolean) forceStop);
    env->DeleteLocalRef(jPackageName);
    return result ? 0 : -1;
}

BRIDGE_API int DispatchInputMessage(MethodParam param) {
    auto *env = (JNIEnv *) AttachThread();
    if (!env) return -1;
    switch (param.method) {
        case TOUCH_DOWN:
            return UpcallInputControl(env, TOUCH_DOWN, param.args.touch.p.x, param.args.touch.p.y,
                                      0, param.display_id);
        case TOUCH_MOVE:
            return UpcallInputControl(env, TOUCH_MOVE, param.args.touch.p.x, param.args.touch.p.y,
                                      0, param.display_id);
        case TOUCH_UP:
            return UpcallInputControl(env, TOUCH_UP, param.args.touch.p.x, param.args.touch.p.y, 0,
                                      param.display_id);
        case KEY_DOWN:
            return UpcallInputControl(env, KEY_DOWN, 0, 0, param.args.key.key_code,
                                      param.display_id);
        case KEY_UP:
            return UpcallInputControl(env, KEY_UP, 0, 0, param.args.key.key_code, param.display_id);
        case START_GAME:
            return UpcallStartApp(env, param.args.start_game.package_name, param.display_id,
                                  param.args.start_game.force_stop != 0);
        default:
            return 0;
    }
}

BRIDGE_API void *AttachThread() {
    if (!g_jvm) return nullptr;
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) return (void *) env;
    return (g_jvm->AttachCurrentThreadAsDaemon(&env, nullptr) == JNI_OK) ? (void *) env : nullptr;
}

BRIDGE_API int DetachThread(void *env) { return g_jvm ? g_jvm->DetachCurrentThread() : -1; }

void InitFrameBuffers(int width, int height) {
    if (g_frameBuffersInitialized) ReleaseFrameBuffers();
    size_t rgba_size = (size_t) width * height * 4;
    size_t bgr_size = (size_t) width * height * 3;
    int stride = width * 4;
    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) {
        FrameBuffer &buf = g_buffers[i];
        if (posix_memalign((void **) &buf.data, 64, rgba_size) != 0 ||
            posix_memalign((void **) &buf.bgr_data, 64, bgr_size) != 0)
            return;
        buf.width = width;
        buf.height = height;
        buf.stride = stride;
        buf.size = rgba_size;
        buf.bgr_size = bgr_size;
        g_bufferStates[i].store(FRAME_STATE_FREE);
        g_readerCounts[i].store(0);
    }
    g_targetWidth.store(width);
    g_targetHeight.store(height);
    g_readBuffer.store(nullptr);
    g_frameCount.store(0);
    g_frameBuffersInitialized = true;
    LOGI("InitFrameBuffers: Success %dx%d", width, height);
}

void ReleaseFrameBuffers(void) {
    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) {
        while (g_bufferStates[i].load() == FRAME_STATE_WRITING || g_readerCounts[i].load() > 0)
            std::this_thread::yield();
        if (g_buffers[i].data) free(g_buffers[i].data);
        if (g_buffers[i].bgr_data) free(g_buffers[i].bgr_data);
        g_buffers[i].data = nullptr;
        g_buffers[i].bgr_data = nullptr;
    }
    g_frameBuffersInitialized = false;
}

static int GetBufferIndex(FrameBuffer *buf) {
    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) if (&g_buffers[i] == buf) return i;
    return -1;
}

static void CommitWriteBuffer(FrameBuffer *buf) {
    int idx = GetBufferIndex(buf);
    if (idx >= 0) {
        g_readBuffer.store(buf, std::memory_order_release);
        g_bufferStates[idx].store(FRAME_STATE_FREE, std::memory_order_release);
    }
}

static FrameBuffer *AcquireWriteBuffer() {
    FrameBuffer *currentReadBuf = g_readBuffer.load(std::memory_order_acquire);
    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) {
        FrameBuffer *candidate = &g_buffers[i];
        if (candidate == currentReadBuf ||
            g_readerCounts[i].load(std::memory_order_acquire) > 0)
            continue;
        int expected = FRAME_STATE_FREE;
        if (g_bufferStates[i].compare_exchange_strong(expected, FRAME_STATE_WRITING,
                                                      std::memory_order_acq_rel)) {
            if (g_readerCounts[i].load(std::memory_order_acquire) > 0 ||
                g_readBuffer.load(std::memory_order_acquire) == candidate) {
                g_bufferStates[i].store(FRAME_STATE_FREE, std::memory_order_release);
                continue;
            }
            return candidate;
        }
    }
    return nullptr;
}

int64_t CopyFrameFromHardwareBuffer(void *env_ptr, void *hardwareBufferObj, int64_t timestampNs) {
    auto *env = (JNIEnv *) env_ptr;
    if (!env || !hardwareBufferObj || !g_frameBuffersInitialized) return -1;
    FrameBuffer *target = AcquireWriteBuffer();
    if (!target) return -1;
    AHardwareBuffer *buffer = AHardwareBuffer_fromHardwareBuffer(env, (jobject) hardwareBufferObj);
    if (!buffer) {
        g_bufferStates[GetBufferIndex(target)].store(FRAME_STATE_FREE);
        return -1;
    }
    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(buffer, &desc);
    void *srcAddr = nullptr;
    if (AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr, &srcAddr) !=
        0) {
        g_bufferStates[GetBufferIndex(target)].store(FRAME_STATE_FREE);
        return -1;
    }

    bool needsPreview = false;
    {
        std::lock_guard<std::mutex> lock(g_previewMutex);
        needsPreview = (g_previewWindow != nullptr);
    }

    ProcessFrameData((uint8_t *) srcAddr,
                     needsPreview ? target->data : nullptr,
                     target->bgr_data,
                     target->width, target->height,
                     desc.stride * 4
    );

    AHardwareBuffer_unlock(buffer, nullptr);
    target->timestamp = timestampNs;
    target->frameCount = g_frameCount.fetch_add(1) + 1;
    CommitWriteBuffer(target);
    if (needsPreview) DispatchPreview(target);
    return target->frameCount;
}

static const FrameBuffer *LockCurrentFrame() {
    for (int attempt = 0; attempt < 3; attempt++) {
        FrameBuffer *frame = g_readBuffer.load(std::memory_order_acquire);
        if (!frame || frame->frameCount == 0) return nullptr;
        int idx = GetBufferIndex(frame);
        if (idx < 0) return nullptr;
        g_readerCounts[idx].fetch_add(1, std::memory_order_acquire);
        if (g_readBuffer.load(std::memory_order_acquire) != frame) {
            g_readerCounts[idx].fetch_sub(1, std::memory_order_release);
            continue;
        }
        if (g_bufferStates[idx].load(std::memory_order_acquire) == FRAME_STATE_WRITING) {
            bool ready = false;
            for (int spin = 0; spin < 500; spin++)
                if (g_bufferStates[idx].load(std::memory_order_acquire) != FRAME_STATE_WRITING) {
                    ready = true;
                    break;
                }
            if (!ready) {
                g_readerCounts[idx].fetch_sub(1, std::memory_order_release);
                return nullptr;
            }
        }
        return frame;
    }
    return nullptr;
}

static void UnlockFrame(const FrameBuffer *frame) {
    if (frame) {
        int idx = GetBufferIndex(const_cast<FrameBuffer *>(frame));
        if (idx >= 0) g_readerCounts[idx].fetch_sub(1, std::memory_order_release);
    }
}

const FrameBuffer *GetCurrentFrame() { return LockCurrentFrame(); }

BRIDGE_API FrameInfo GetLockedPixels() {
    FrameInfo result = {0};
    const FrameBuffer *frame = GetCurrentFrame();
    if (frame && frame->bgr_data) {
        result.width = frame->width;
        result.height = frame->height;
        result.stride = frame->width * 3;
        result.length = frame->bgr_size;
        result.data = frame->bgr_data;
        result.frame_ref = const_cast<FrameBuffer *>(frame);
    }
    return result;
}

BRIDGE_API int UnlockPixels(FrameInfo info) {
    if (info.frame_ref) UnlockFrame(reinterpret_cast<const FrameBuffer *>(info.frame_ref));
    return 0;
}

static jstring ping(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF("LibBridge");
}

static void nativeInitFrameBuffers(JNIEnv *env, jclass clazz, jint width, jint height) {
    InitFrameBuffers(width, height);
}

static jlong nativeCopyFrameFromHardwareBuffer(JNIEnv *env, jclass clazz, jobject hardwareBuffer,
                                               jlong timestampNs) {
    return hardwareBuffer ? CopyFrameFromHardwareBuffer(env, hardwareBuffer, timestampNs) : -1;
}

static void nativeReleaseFrameBuffers(JNIEnv *env, jclass clazz) { ReleaseFrameBuffers(); }

static jobject nativeGetFrameBufferBitmap(JNIEnv *env, jclass clazz) {
    const FrameBuffer *frame = LockCurrentFrame();
    if (!frame || !frame->data) return nullptr;
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Field = env->GetStaticFieldID(configClass, "ARGB_8888",
                                                   "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Config = env->GetStaticObjectField(configClass, argb8888Field);
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapClass, "createBitmap",
                                                          "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, frame->width,
                                                 frame->height, argb8888Config);
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, bitmap, &info);
        if (info.stride == (uint32_t) frame->stride) memcpy(pixels, frame->data, frame->size);
        else
            for (int y = 0; y < frame->height; ++y)
                memcpy((uint8_t *) pixels + y * info.stride, frame->data + y * frame->stride,
                       frame->width * 4);
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    UnlockFrame(frame);
    return bitmap;
}
