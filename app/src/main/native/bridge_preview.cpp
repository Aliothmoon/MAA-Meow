#include "bridge_preview.h"

#include "bridge_frame_buffer.h"

#include <android/hardware_buffer.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <media/NdkImageReader.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <queue>
#include <thread>

static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC eglGetNativeClientBufferANDROID = nullptr;
static PFNEGLCREATEIMAGEKHRPROC eglCreateImageKHR = nullptr;
static PFNEGLDESTROYIMAGEKHRPROC eglDestroyImageKHR = nullptr;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC glEGLImageTargetTexture2DOES = nullptr;

/** 只有 surface 跟窗口有关，其余几项建一次用到进程结束 */
struct EGLState {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLConfig config = nullptr;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    GLuint program = 0;
    GLuint textureId = 0;
};

static const char *VERTEX_SHADER = R"(
attribute vec4 vPosition;
attribute vec2 vTexCoord;
varying vec2 fTexCoord;
void main() {
    gl_Position = vPosition;
    fTexCoord = vTexCoord;
}
)";

static const char *FRAGMENT_SHADER = R"(
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES sTexture;
varying vec2 fTexCoord;
void main() {
    gl_FragColor = texture2D(sTexture, fTexCoord);
}
)";

static jobject g_previewSurfaceObj = nullptr;
static std::mutex g_previewMutex;
static std::atomic<bool> g_hasPreview{false};

static EGLState g_eglState;
static std::thread g_renderThread;
static std::queue<AImage *> g_renderQueue;
static std::mutex g_renderMutex;
static std::condition_variable g_renderCv;
static std::atomic<bool> g_renderThreadRunning{false};
static ANativeWindow *g_pendingWindow = nullptr;
static bool g_pendingDetach = false;
static bool g_pendingBlank = false;

static GLuint LoadShader(GLenum type, const char *source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    return shader;
}

static void DrainPreviewQueueLocked() {
    while (!g_renderQueue.empty()) {
        AImage_delete(g_renderQueue.front());
        g_renderQueue.pop();
    }
}

/** display 是进程级单例，eglTerminate 会把它整个作废，下一次 eglInitialize 得把驱动重新拉起来 */
static bool EnsureEglDisplay() {
    if (g_eglState.display != EGL_NO_DISPLAY) {
        return true;
    }

    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY || eglInitialize(display, nullptr, nullptr) == EGL_FALSE) {
        LOGE("EnsureEglDisplay: eglInitialize failed");
        return false;
    }

    const EGLint configAttribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_NONE
    };
    EGLConfig config;
    EGLint numConfigs = 0;
    if (eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) == EGL_FALSE ||
        numConfigs <= 0) {
        LOGE("EnsureEglDisplay: eglChooseConfig failed");
        return false;
    }

    g_eglState.display = display;
    g_eglState.config = config;
    return true;
}

/** 须在 eglMakeCurrent 之后调；这些挂在 context 上，context 不重建就不用重来 */
static void EnsureGlObjects() {
    if (g_eglState.program) {
        return;
    }

    eglGetNativeClientBufferANDROID = reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
            eglGetProcAddress("eglGetNativeClientBufferANDROID"));
    eglCreateImageKHR = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(
            eglGetProcAddress("eglCreateImageKHR"));
    eglDestroyImageKHR = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(
            eglGetProcAddress("eglDestroyImageKHR"));
    glEGLImageTargetTexture2DOES = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
            eglGetProcAddress("glEGLImageTargetTexture2DOES"));

    GLuint vShader = LoadShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fShader = LoadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    GLuint program = glCreateProgram();
    glAttachShader(program, vShader);
    glAttachShader(program, fShader);
    glLinkProgram(program);
    glDeleteShader(vShader);
    glDeleteShader(fShader);
    glUseProgram(program);

    GLuint textureId = 0;
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId);
    glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    g_eglState.program = program;
    g_eglState.textureId = textureId;
}

/** EGLSurface 必须先于 ANativeWindow_release 销毁 */
static void DetachWindow() {
    if (g_eglState.surface == EGL_NO_SURFACE) {
        return;
    }
    eglMakeCurrent(g_eglState.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(g_eglState.display, g_eglState.surface);
    g_eglState.surface = EGL_NO_SURFACE;
}

static bool AttachWindow(ANativeWindow *window) {
    if (!EnsureEglDisplay()) {
        return false;
    }

    EGLSurface surface = eglCreateWindowSurface(g_eglState.display, g_eglState.config, window,
                                                nullptr);
    if (surface == EGL_NO_SURFACE) {
        LOGE("AttachWindow: eglCreateWindowSurface failed, error=0x%x", eglGetError());
        return false;
    }

    if (g_eglState.context == EGL_NO_CONTEXT) {
        const EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        g_eglState.context = eglCreateContext(g_eglState.display, g_eglState.config, EGL_NO_CONTEXT,
                                              contextAttribs);
        if (g_eglState.context == EGL_NO_CONTEXT) {
            LOGE("AttachWindow: eglCreateContext failed");
            eglDestroySurface(g_eglState.display, surface);
            return false;
        }
    }

    if (eglMakeCurrent(g_eglState.display, surface, surface, g_eglState.context) == EGL_FALSE) {
        LOGE("AttachWindow: eglMakeCurrent failed, error=0x%x", eglGetError());
        eglDestroySurface(g_eglState.display, surface);
        return false;
    }

    // 先切到新 surface 再销毁旧的：销毁 current 的 surface 是未定义行为
    EGLSurface previous = g_eglState.surface;
    g_eglState.surface = surface;
    if (previous != EGL_NO_SURFACE) {
        eglDestroySurface(g_eglState.display, previous);
    }

    EnsureGlObjects();

    // viewport 只在 context 首次 makeCurrent 时按当时的 surface 定下来，换 surface 不会跟着走
    EGLint width = 0;
    EGLint height = 0;
    eglQuerySurface(g_eglState.display, surface, EGL_WIDTH, &width);
    eglQuerySurface(g_eglState.display, surface, EGL_HEIGHT, &height);
    glViewport(0, 0, width, height);

    eglMakeCurrent(g_eglState.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    return true;
}

/** 只在进程退出时走：eglTerminate 仅此一处 */
static void DestroyEgl() {
    if (g_eglState.display == EGL_NO_DISPLAY) {
        return;
    }
    eglMakeCurrent(g_eglState.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (g_eglState.surface != EGL_NO_SURFACE) {
        eglDestroySurface(g_eglState.display, g_eglState.surface);
    }
    // program 与 texture 随 context 一起回收
    if (g_eglState.context != EGL_NO_CONTEXT) {
        eglDestroyContext(g_eglState.display, g_eglState.context);
    }
    eglTerminate(g_eglState.display);
    g_eglState = EGLState{};
}

static bool MakeSurfaceCurrent(const char *caller) {
    if (eglMakeCurrent(g_eglState.display, g_eglState.surface, g_eglState.surface,
                       g_eglState.context) == EGL_FALSE) {
        LOGE("%s: eglMakeCurrent failed, error=0x%x", caller, eglGetError());
        return false;
    }
    return true;
}

static void ReleaseCurrent() {
    eglMakeCurrent(g_eglState.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

static void RenderPreview(AHardwareBuffer *hb) {
    if (g_eglState.surface == EGL_NO_SURFACE || !g_eglState.program || !hb) {
        return;
    }
    if (!eglGetNativeClientBufferANDROID || !eglCreateImageKHR ||
        !eglDestroyImageKHR || !glEGLImageTargetTexture2DOES) {
        return;
    }

    if (!MakeSurfaceCurrent("RenderPreview")) {
        return;
    }

    EGLClientBuffer clientBuffer = eglGetNativeClientBufferANDROID(hb);
    EGLint attrs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    EGLImageKHR image = eglCreateImageKHR(g_eglState.display, EGL_NO_CONTEXT,
                                          EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attrs);
    if (image == EGL_NO_IMAGE_KHR) {
        LOGE("RenderPreview: eglCreateImageKHR failed");
        ReleaseCurrent();
        return;
    }

    glBindTexture(GL_TEXTURE_EXTERNAL_OES, g_eglState.textureId);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_EXTERNAL_OES, image);

    glClear(GL_COLOR_BUFFER_BIT);
    GLfloat vertices[] = {-1, 1, -1, -1, 1, 1, 1, -1};
    GLfloat texCoords[] = {0, 0, 0, 1, 1, 0, 1, 1};

    GLint posLoc = glGetAttribLocation(g_eglState.program, "vPosition");
    GLint texLoc = glGetAttribLocation(g_eglState.program, "vTexCoord");

    glEnableVertexAttribArray(posLoc);
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(texLoc);
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 0, texCoords);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    eglSwapBuffers(g_eglState.display, g_eglState.surface);

    eglDestroyImageKHR(g_eglState.display, image);
    ReleaseCurrent();
}

static void RenderBlank() {
    if (g_eglState.surface == EGL_NO_SURFACE || !MakeSurfaceCurrent("RenderBlank")) {
        return;
    }

    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(g_eglState.display, g_eglState.surface);
    ReleaseCurrent();
}

static void RenderLoop() {
    ANativeWindow *window = nullptr;

    while (g_renderThreadRunning.load(std::memory_order_acquire)) {
        AImage *image = nullptr;
        ANativeWindow *nextWindow = nullptr;
        bool detach = false;
        bool blank = false;
        {
            std::unique_lock<std::mutex> lock(g_renderMutex);
            g_renderCv.wait(lock, [] {
                return !g_renderThreadRunning.load(std::memory_order_acquire) ||
                       !g_renderQueue.empty() || g_pendingWindow != nullptr || g_pendingDetach ||
                       g_pendingBlank;
            });

            if (!g_renderThreadRunning.load(std::memory_order_acquire)) {
                break;
            }

            detach = g_pendingDetach;
            g_pendingDetach = false;
            nextWindow = g_pendingWindow;
            g_pendingWindow = nullptr;
            blank = g_pendingBlank;
            g_pendingBlank = false;

            if (!g_renderQueue.empty()) {
                image = g_renderQueue.front();
                g_renderQueue.pop();
            }
        }

        // EGL 只在这条线程上动
        if (detach || nextWindow) {
            DetachWindow();
            if (window) {
                ANativeWindow_release(window);
                window = nullptr;
            }
        }
        if (nextWindow) {
            window = nextWindow;
            if (!AttachWindow(window)) {
                ANativeWindow_release(window);
                window = nullptr;
            }
        }

        // 同一拍里还取到了图，那是清屏请求之后才入队的新帧，画在黑屏之后
        if (blank) {
            RenderBlank();
        }
        if (image) {
            AHardwareBuffer *hb = nullptr;
            if (AImage_getHardwareBuffer(image, &hb) == AMEDIA_OK && hb) {
                RenderPreview(hb);
            }
            AImage_delete(image);
        }
    }

    // 收摊：不画黑的话 SurfaceView 上会一直留着最后一帧，看着像还在跑
    RenderBlank();
    DetachWindow();
    if (window) {
        ANativeWindow_release(window);
    }
    DestroyEgl();
}

/** 换窗口不停渲染线程：join 会卡住 binder 线程。收摊走 [ShutdownPreview] */
void SetPreviewSurface(JNIEnv *env, jobject jSurface) {
    std::lock_guard<std::mutex> lock(g_previewMutex);

    if (g_previewSurfaceObj && env && env->IsSameObject(jSurface, g_previewSurfaceObj)) {
        return;
    }

    if (g_previewSurfaceObj && env) {
        env->DeleteGlobalRef(g_previewSurfaceObj);
        g_previewSurfaceObj = nullptr;
    }

    ANativeWindow *window = nullptr;
    if (jSurface && env) {
        window = ANativeWindow_fromSurface(env, jSurface);
        if (window) {
            g_previewSurfaceObj = env->NewGlobalRef(jSurface);
        } else {
            LOGE("SetPreviewSurface: ANativeWindow_fromSurface failed");
        }
    }

    g_hasPreview.store(window != nullptr, std::memory_order_release);

    {
        std::lock_guard<std::mutex> queueLock(g_renderMutex);
        DrainPreviewQueueLocked();
        if (g_pendingWindow) {
            ANativeWindow_release(g_pendingWindow);
        }
        g_pendingWindow = window;
        g_pendingDetach = true;
    }

    if (window && !g_renderThreadRunning.load(std::memory_order_acquire)) {
        if (g_renderThread.joinable()) {
            g_renderThread.join();
        }
        g_renderThreadRunning.store(true, std::memory_order_release);
        g_renderThread = std::thread(RenderLoop);
    }
    g_renderCv.notify_all();
}

void ShutdownPreview(JNIEnv *env) {
    // 不走 SetPreviewSurface(nullptr)：它会让渲染线程先拆窗口，收摊那一帧黑屏就没处画了
    std::lock_guard<std::mutex> lock(g_previewMutex);
    g_hasPreview.store(false, std::memory_order_release);
    if (g_previewSurfaceObj && env) {
        env->DeleteGlobalRef(g_previewSurfaceObj);
        g_previewSurfaceObj = nullptr;
    }
    bool wasRunning;
    {
        // 停止标志在锁里改：渲染线程在同一把锁下判条件，锁外改会卡进它「判完还没睡下」的缝里丢通知
        std::lock_guard<std::mutex> queueLock(g_renderMutex);
        DrainPreviewQueueLocked();
        if (g_pendingWindow) {
            ANativeWindow_release(g_pendingWindow);
            g_pendingWindow = nullptr;
        }
        wasRunning = g_renderThreadRunning.exchange(false, std::memory_order_acq_rel);
    }

    if (wasRunning) {
        g_renderCv.notify_all();
        if (g_renderThread.joinable()) {
            g_renderThread.join();
        }
    }
}

bool IsPreviewEnabled() {
    return g_hasPreview.load(std::memory_order_acquire);
}

bool DispatchPreview(AImage *image) {
    if (!image || !g_hasPreview.load(std::memory_order_acquire)) {
        return false;
    }

    static auto lastDispatchTime = std::chrono::steady_clock::now();
    auto now = std::chrono::steady_clock::now();
    if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastDispatchTime).count() <
        16) {
        return false;
    }
    lastDispatchTime = now;

    AImage *imageToDelete = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_renderMutex);
        if (!g_hasPreview.load(std::memory_order_acquire)) {
            return false;
        }
        if (!g_renderQueue.empty()) {
            imageToDelete = g_renderQueue.front();
            g_renderQueue.pop();
        }
        g_renderQueue.push(image);
    }
    if (imageToDelete) {
        AImage_delete(imageToDelete);
    }
    g_renderCv.notify_one();
    return true;
}

void DrainPreviewQueue() {
    std::lock_guard<std::mutex> lock(g_renderMutex);
    DrainPreviewQueueLocked();
}

void BlankPreview(int64_t expectedFrameCount) {
    if (!g_hasPreview.load(std::memory_order_acquire)) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(g_renderMutex);
        // 采集线程先写帧缓冲（帧计数 +1）再往这里入队：计数变了就是来过真帧，
        // 它要么已在队里要么正等这把锁，再清队列画黑屏会把它盖掉
        if (GetFrameCount() != expectedFrameCount) {
            return;
        }
        // 还排着的图画的是同一份残影，留着会在黑屏之后又画回来
        DrainPreviewQueueLocked();
        g_pendingBlank = true;
    }
    g_renderCv.notify_one();
}
