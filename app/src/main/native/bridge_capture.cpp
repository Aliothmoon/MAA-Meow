#include "bridge_capture.h"

#include "bridge_frame_buffer.h"
#include "bridge_preview.h"

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <thread>

struct NativeCapturer {
    AImageReader *reader = nullptr;
    ANativeWindow *window = nullptr;
    AImageReader_ImageListener listener{};
    int width = 0;
    int height = 0;
};

static NativeCapturer *g_capturer = nullptr;

// 顺序同 kReleaseStageNames
enum ReleaseStage : int {
    RELEASE_IDLE = 0,
    RELEASE_DETACH_LISTENER,
    RELEASE_WAIT_CALLBACK,
    RELEASE_DRAIN_PREVIEW,
    RELEASE_DELETE_READER,
    RELEASE_BLANK_PREVIEW,
    RELEASE_FRAME_BUFFERS,
};

static constexpr const char *kReleaseStageNames[] = {
        "idle", "detach_listener", "wait_callback", "drain_preview", "delete_reader", "blank_preview",
        "frame_buffers",
};

static std::atomic<int> g_release_stage{RELEASE_IDLE};
static std::atomic<int64_t> g_release_stage_since_ms{0};

// AImageReader_delete 持 reader 锁等回调线程退出，回调里 acquire 也要这把锁
// 关闭前须让进行中的回调放完，之后到来的回调不再碰 reader
static std::atomic<bool> g_closing{false};
static std::atomic<int> g_callbacks_in_flight{0};

static int64_t NowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static void EnterReleaseStage(ReleaseStage stage) {
    g_release_stage_since_ms.store(NowMs(), std::memory_order_relaxed);
    g_release_stage.store(stage, std::memory_order_release);
}

static void ConsumeLatestImage(AImageReader *reader) {
    AImage *image = nullptr;
    if (AImageReader_acquireLatestImage(reader, &image) != AMEDIA_OK || !image) {
        return;
    }

    AHardwareBuffer *hb = nullptr;
    if (AImage_getHardwareBuffer(image, &hb) == AMEDIA_OK && hb) {
        WriteHardwareBufferToFrame(hb);
    }

    bool handedOver = false;
    if (IsPreviewEnabled()) {
        handedOver = DispatchPreview(image);
    }

    if (!handedOver) {
        AImage_delete(image);
    }
}

static void onImageAvailable(void *context, AImageReader *reader) {
    (void) context;
    // 先登记再看标志，与 Release 的先置标志再等归零配对
    g_callbacks_in_flight.fetch_add(1);
    if (!g_closing.load()) {
        ConsumeLatestImage(reader);
    }
    g_callbacks_in_flight.fetch_sub(1);
}

jobject SetupNativeCapturer(JNIEnv *env, int width, int height) {
    ReleaseNativeCapturer();
    InitFrameBuffers(width, height);

    g_capturer = new NativeCapturer();
    g_capturer->width = width;
    g_capturer->height = height;

    media_status_t status = AImageReader_newWithUsage(
            width, height, AIMAGE_FORMAT_RGBA_8888,
            AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE, 5,
            &g_capturer->reader);
    if (status != AMEDIA_OK) {
        LOGE("AImageReader_newWithUsage failed: %d", status);
        delete g_capturer;
        g_capturer = nullptr;
        ReleaseFrameBuffers();
        return nullptr;
    }

    g_closing.store(false);
    g_capturer->listener.context = g_capturer;
    g_capturer->listener.onImageAvailable = onImageAvailable;
    status = AImageReader_setImageListener(g_capturer->reader, &g_capturer->listener);
    if (status != AMEDIA_OK) {
        LOGE("SetupNativeCapturer: AImageReader_setImageListener failed: %d", status);
        AImageReader_delete(g_capturer->reader);
        delete g_capturer;
        g_capturer = nullptr;
        ReleaseFrameBuffers();
        return nullptr;
    }

    status = AImageReader_getWindow(g_capturer->reader, &g_capturer->window);
    if (status != AMEDIA_OK || !g_capturer->window) {
        LOGE("SetupNativeCapturer: AImageReader_getWindow failed: status=%d, window=%p",
             status, g_capturer->window);
        AImageReader_setImageListener(g_capturer->reader, nullptr);
        AImageReader_delete(g_capturer->reader);
        delete g_capturer;
        g_capturer = nullptr;
        ReleaseFrameBuffers();
        return nullptr;
    }

    return ANativeWindow_toSurface(env, g_capturer->window);
}

void ReleaseNativeCapturer() {
    if (g_capturer && g_capturer->reader) {
        g_closing.store(true);
        EnterReleaseStage(RELEASE_DETACH_LISTENER);
        AImageReader_setImageListener(g_capturer->reader, nullptr);
        EnterReleaseStage(RELEASE_WAIT_CALLBACK);
        while (g_callbacks_in_flight.load() != 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }

    // 回调已放完，不会再往队里塞图
    EnterReleaseStage(RELEASE_DRAIN_PREVIEW);
    DrainPreviewQueue();

    if (g_capturer) {
        if (g_capturer->reader) {
            EnterReleaseStage(RELEASE_DELETE_READER);
            AImageReader_delete(g_capturer->reader);
        }

        delete g_capturer;
        g_capturer = nullptr;
        LOGI("NativeCapturer released");
    }

    // 那块屏没了，预览上留着的是它的最后一帧；listener 已摘，帧计数不会再变
    EnterReleaseStage(RELEASE_BLANK_PREVIEW);
    BlankPreview(GetFrameCount());
    EnterReleaseStage(RELEASE_FRAME_BUFFERS);
    ReleaseFrameBuffers();
    EnterReleaseStage(RELEASE_IDLE);
}

std::string DescribeReleaseStage() {
    const int stage = g_release_stage.load(std::memory_order_acquire);
    // InitFrameBuffers 也会单独调 ReleaseFrameBuffers，不经收尾阶段
    const std::string buffers = DescribeFrameBufferRelease();
    if (stage == RELEASE_IDLE) {
        return buffers.empty() ? std::string() : "frame_buffers " + buffers;
    }
    char buf[96];
    snprintf(buf, sizeof(buf), "%s for %lldms", kReleaseStageNames[stage],
             static_cast<long long>(NowMs() - g_release_stage_since_ms.load(std::memory_order_relaxed)));
    std::string out(buf);
    if (!buffers.empty()) {
        out += " ";
        out += buffers;
    }
    return out;
}
