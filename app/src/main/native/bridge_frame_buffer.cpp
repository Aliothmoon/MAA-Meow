#include "bridge_frame_buffer.h"

#include <android/bitmap.h>

#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <thread>

#if defined(__ARM_NEON)

#include <arm_neon.h>

#endif

static FrameBuffer g_buffers[FRAME_BUFFER_COUNT] = {};
static std::atomic<int> g_buffer_states[FRAME_BUFFER_COUNT] = {
        FRAME_STATE_FREE, FRAME_STATE_FREE, FRAME_STATE_FREE
};
static std::atomic<int> g_reader_counts[FRAME_BUFFER_COUNT] = {0, 0, 0};
static std::atomic<FrameBuffer *> g_read_buffer{nullptr};
static std::atomic<int64_t> g_frame_count{0};
static std::atomic<bool> g_frame_buffers_initialized{false};
// ReleaseFrameBuffers 正在等的槽位，-1 为没在等
static std::atomic<int> g_release_slot{-1};
// 写者有两个：采集线程与 BlankFrame。CommitWriteBuffer 先放开状态再发布，两步之间
// 另一个写者能把这块刚写好的缓冲抢去改写，所以写者之间串行；读者不拿这把锁
static std::mutex g_write_mutex;

static void ProcessFrameDataV2(
        const uint8_t *__restrict src,
        uint8_t *__restrict dst_bgr,
        int width,
        int height,
        int src_stride) {
    for (int y = 0; y < height; ++y) {
        const uint8_t *s = src + static_cast<size_t>(y) * src_stride;
        uint8_t *d3 = dst_bgr + y * width * 3;
        int x = 0;

#if defined(__ARM_NEON)
        for (; x <= width - 16; x += 16) {
            uint8x16x4_t rgba = vld4q_u8(s);
            s += 64;
            uint8x16x3_t bgr;
            bgr.val[0] = rgba.val[2];
            bgr.val[1] = rgba.val[1];
            bgr.val[2] = rgba.val[0];
            vst3q_u8(d3, bgr);
            d3 += 48;
        }
        for (; x <= width - 8; x += 8) {
            uint8x8x4_t rgba = vld4_u8(s);
            s += 32;
            uint8x8x3_t bgr;
            bgr.val[0] = rgba.val[2];
            bgr.val[1] = rgba.val[1];
            bgr.val[2] = rgba.val[0];
            vst3_u8(d3, bgr);
            d3 += 24;
        }
#endif
        for (; x < width; ++x) {
            d3[0] = s[2];
            d3[1] = s[1];
            d3[2] = s[0];
            s += 4;
            d3 += 3;
        }
    }
}

static int GetBufferIndex(FrameBuffer *buf) {
    return buf->index;
}

static void ReleaseBuffer(FrameBuffer *buf) {
    if (!buf) {
        return;
    }
    if (buf->bgr_data) {
        free(buf->bgr_data);
        buf->bgr_data = nullptr;
    }
    buf->width = 0;
    buf->height = 0;
    buf->bgr_size = 0;
    buf->frame_count = 0;
}

static void MarkBufferFree(FrameBuffer *buf) {
    int idx = GetBufferIndex(buf);
    if (idx >= 0) {
        g_buffer_states[idx].store(FRAME_STATE_FREE, std::memory_order_release);
    }
}

// 缓冲正在拆时不发布，返回 false
static bool CommitWriteBuffer(FrameBuffer *buf) {
    int idx = GetBufferIndex(buf);
    if (idx < 0) {
        return false;
    }
    g_buffer_states[idx].store(FRAME_STATE_FREE, std::memory_order_release);
    if (!g_frame_buffers_initialized.load(std::memory_order_acquire)) {
        return false;
    }
    g_read_buffer.store(buf, std::memory_order_release);
    return true;
}

static FrameBuffer *AcquireWriteBuffer() {
    if (!g_frame_buffers_initialized.load(std::memory_order_acquire)) {
        return nullptr;
    }

    FrameBuffer *currentReadBuffer = g_read_buffer.load(std::memory_order_acquire);
    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) {
        FrameBuffer *candidate = &g_buffers[i];
        if (candidate == currentReadBuffer ||
            g_reader_counts[i].load(std::memory_order_acquire) > 0) {
            continue;
        }

        int expected = FRAME_STATE_FREE;
        if (!g_buffer_states[i].compare_exchange_strong(expected, FRAME_STATE_WRITING,
                                                        std::memory_order_acq_rel)) {
            continue;
        }

        if (g_reader_counts[i].load(std::memory_order_acquire) > 0 ||
            g_read_buffer.load(std::memory_order_acquire) == candidate ||
            !g_frame_buffers_initialized.load(std::memory_order_acquire)) {
            g_buffer_states[i].store(FRAME_STATE_FREE, std::memory_order_release);
            continue;
        }
        return candidate;
    }
    return nullptr;
}

static constexpr int WRITE_BUFFER_ATTEMPTS = 20;
static constexpr auto WRITE_BUFFER_RETRY_INTERVAL = std::chrono::microseconds(500);

// 两块非当前缓冲都被读者占着时等一等：应用退出前的最后一帧丢了就没有下一帧来补
static FrameBuffer *AcquireWriteBufferPatiently() {
    for (int attempt = 0; attempt < WRITE_BUFFER_ATTEMPTS; ++attempt) {
        if (FrameBuffer *buf = AcquireWriteBuffer()) {
            return buf;
        }
        if (!g_frame_buffers_initialized.load(std::memory_order_acquire)) {
            return nullptr;
        }
        std::this_thread::sleep_for(WRITE_BUFFER_RETRY_INTERVAL);
    }
    return nullptr;
}

static const FrameBuffer *LockCurrentFrame() {
    if (!g_frame_buffers_initialized.load(std::memory_order_acquire)) {
        return nullptr;
    }

    for (int attempt = 0; attempt < 3; ++attempt) {
        FrameBuffer *frame = g_read_buffer.load(std::memory_order_acquire);
        if (!frame || frame->frame_count == 0) {
            return nullptr;
        }

        int idx = GetBufferIndex(frame);
        if (idx < 0) {
            return nullptr;
        }

        g_reader_counts[idx].fetch_add(1, std::memory_order_acquire);
        if (g_read_buffer.load(std::memory_order_acquire) != frame ||
            !g_frame_buffers_initialized.load(std::memory_order_acquire)) {
            g_reader_counts[idx].fetch_sub(1, std::memory_order_release);
            continue;
        }

        if (g_buffer_states[idx].load(std::memory_order_acquire) == FRAME_STATE_WRITING) {
            bool ready = false;
            for (int spin = 0; spin < 500; ++spin) {
                if (g_buffer_states[idx].load(std::memory_order_acquire) != FRAME_STATE_WRITING) {
                    ready = true;
                    break;
                }
            }
            if (!ready) {
                g_reader_counts[idx].fetch_sub(1, std::memory_order_release);
                return nullptr;
            }
        }

        return frame;
    }
    return nullptr;
}

static void UnlockFrame(const FrameBuffer *frame) {
    if (!frame) {
        return;
    }

    int idx = GetBufferIndex(const_cast<FrameBuffer *>(frame));
    if (idx >= 0) {
        g_reader_counts[idx].fetch_sub(1, std::memory_order_release);
    }
}

// 0 被 LockCurrentFrame 当成无效帧，黑帧要能被读到就得另取一个值
static constexpr int64_t BLANK_FRAME_COUNT = -1;

void InitFrameBuffers(int width, int height) {
    if (g_frame_buffers_initialized.load(std::memory_order_acquire)) {
        ReleaseFrameBuffers();
    }

    const size_t bgrSize = static_cast<size_t>(width) * height * 3;
    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) {
        FrameBuffer &buf = g_buffers[i];
        ReleaseBuffer(&buf);
        if (posix_memalign(reinterpret_cast<void **>(&buf.bgr_data), 64, bgrSize) != 0) {
            LOGE("InitFrameBuffers: posix_memalign failed at index=%d", i);
            for (int j = 0; j <= i; ++j) {
                ReleaseBuffer(&g_buffers[j]);
                g_buffer_states[j].store(FRAME_STATE_FREE, std::memory_order_release);
                g_reader_counts[j].store(0, std::memory_order_release);
            }
            g_read_buffer.store(nullptr, std::memory_order_release);
            g_frame_count.store(0, std::memory_order_release);
            return;
        }

        buf.index = i;
        buf.width = width;
        buf.height = height;
        buf.bgr_size = bgrSize;
        buf.frame_count = 0;
        g_buffer_states[i].store(FRAME_STATE_FREE, std::memory_order_release);
        g_reader_counts[i].store(0, std::memory_order_release);
    }

    g_read_buffer.store(nullptr, std::memory_order_release);
    g_frame_count.store(0, std::memory_order_release);
    g_frame_buffers_initialized.store(true, std::memory_order_release);
    LOGI("InitFrameBuffers: Success %dx%d", width, height);
}

void ReleaseFrameBuffers() {
    g_frame_buffers_initialized.store(false, std::memory_order_release);
    g_read_buffer.store(nullptr, std::memory_order_release);

    for (int i = 0; i < FRAME_BUFFER_COUNT; ++i) {
        g_release_slot.store(i, std::memory_order_release);
        while (g_buffer_states[i].load(std::memory_order_acquire) == FRAME_STATE_WRITING ||
               g_reader_counts[i].load(std::memory_order_acquire) > 0) {
            std::this_thread::yield();
        }

        ReleaseBuffer(&g_buffers[i]);
        g_buffer_states[i].store(FRAME_STATE_FREE, std::memory_order_release);
        g_reader_counts[i].store(0, std::memory_order_release);
    }
    g_release_slot.store(-1, std::memory_order_release);

    g_read_buffer.store(nullptr, std::memory_order_release);
    g_frame_count.store(0, std::memory_order_release);
}

std::string DescribeFrameBufferRelease() {
    const int slot = g_release_slot.load(std::memory_order_acquire);
    if (slot < 0 || slot >= FRAME_BUFFER_COUNT) {
        return {};
    }
    char buf[96];
    snprintf(buf, sizeof(buf), "slot=%d state=%d readers=%d", slot,
             g_buffer_states[slot].load(std::memory_order_acquire),
             g_reader_counts[slot].load(std::memory_order_acquire));
    return buf;
}

bool WriteHardwareBufferToFrame(AHardwareBuffer *buffer) {
    if (!buffer || !g_frame_buffers_initialized.load(std::memory_order_acquire)) {
        return false;
    }

    std::lock_guard<std::mutex> lock(g_write_mutex);
    FrameBuffer *target = AcquireWriteBufferPatiently();
    if (!target) {
        return false;
    }

    void *srcAddr = nullptr;
    if (AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr,
                             &srcAddr) != 0) {
        MarkBufferFree(target);
        return false;
    }

    AHardwareBuffer_Desc desc;
    AHardwareBuffer_describe(buffer, &desc);
    ProcessFrameDataV2(static_cast<uint8_t *>(srcAddr), target->bgr_data, target->width,
                       target->height, static_cast<int>(desc.stride) * 4);
    AHardwareBuffer_unlock(buffer, nullptr);

    target->frame_count = g_frame_count.fetch_add(1, std::memory_order_acq_rel) + 1;
    return CommitWriteBuffer(target);
}

int64_t GetFrameCount() {
    return g_frame_count.load(std::memory_order_acquire);
}

bool BlankFrame(int64_t expectedFrameCount) {
    if (!g_frame_buffers_initialized.load(std::memory_order_acquire)) {
        return false;
    }

    std::lock_guard<std::mutex> lock(g_write_mutex);
    // 调用方下结论之后来过真帧：那一帧才是屏上现在的样子，盖成黑帧它又不再重绘就一直黑下去
    if (g_frame_count.load(std::memory_order_acquire) != expectedFrameCount) {
        return false;
    }

    // 还没出过帧就没有残影可换
    const FrameBuffer *current = LockCurrentFrame();
    if (!current) {
        return false;
    }
    const bool alreadyBlank = current->frame_count == BLANK_FRAME_COUNT;
    UnlockFrame(current);
    if (alreadyBlank) {
        return false;
    }

    FrameBuffer *blank = AcquireWriteBufferPatiently();
    if (!blank) {
        return false;
    }
    // 不动 g_frame_count：awaitFirstFrame 与帧率估算都拿它当「来过真帧」
    memset(blank->bgr_data, 0, blank->bgr_size);
    blank->frame_count = BLANK_FRAME_COUNT;
    return CommitWriteBuffer(blank);
}

BRIDGE_API FrameInfo GetLockedPixels() {
    FrameInfo result = {0};
    const FrameBuffer *frame = LockCurrentFrame();
    if (!frame) {
        return result;
    }

    if (!frame->bgr_data) {
        UnlockFrame(frame);
        return result;
    }

    result.width = frame->width;
    result.height = frame->height;
    result.stride = frame->width * 3;
    result.length = static_cast<uint32_t>(frame->bgr_size);
    result.data = frame->bgr_data;
    result.frame_ref = const_cast<FrameBuffer *>(frame);
    return result;
}

BRIDGE_API int UnlockPixels(FrameInfo info) {
    if (info.frame_ref) {
        UnlockFrame(reinterpret_cast<const FrameBuffer *>(info.frame_ref));
    }
    return 0;
}

static jobject BuildArgb8888BitmapFromBgr(JNIEnv *env, const uint8_t *bgr, int width, int height) {
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    if (!bitmapClass || !configClass) {
        env->ExceptionClear();
        return nullptr;
    }

    jfieldID argb8888Field = env->GetStaticFieldID(
            configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jmethodID createBitmapMethod = env->GetStaticMethodID(
            bitmapClass, "createBitmap",
            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    if (!argb8888Field || !createBitmapMethod) {
        env->ExceptionClear();
        return nullptr;
    }

    jobject argb8888Config = env->GetStaticObjectField(configClass, argb8888Field);
    if (!argb8888Config) {
        env->ExceptionClear();
        return nullptr;
    }

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, width, height,
                                                 argb8888Config);
    if (env->ExceptionCheck()) {
        // createBitmap 可能因 OOM 抛异常，清除后按失败处理
        env->ExceptionClear();
        return nullptr;
    }
    if (!bitmap) {
        return nullptr;
    }

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        !pixels) {
        return nullptr;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    uint8_t *dst = static_cast<uint8_t *>(pixels);
    const uint8_t *src = bgr;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            dst[x * 4 + 0] = src[x * 3 + 2];
            dst[x * 4 + 1] = src[x * 3 + 1];
            dst[x * 4 + 2] = src[x * 3 + 0];
            dst[x * 4 + 3] = 255;
        }
        dst += info.stride;
        src += static_cast<size_t>(width) * 3;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

jobject CreateFrameBufferBitmap(JNIEnv *env) {
    FrameInfo frame = GetLockedPixels();
    if (!frame.data || frame.width == 0 || frame.height == 0 || frame.length == 0) {
        UnlockPixels(frame);
        return nullptr;
    }

    // 拷出 BGR 数据后立即解锁，把持锁时间压到一次 memcpy，避免和采集 / MAA core 抢缓冲区
    auto *bgrCopy = static_cast<uint8_t *>(malloc(frame.length));
    if (!bgrCopy) {
        UnlockPixels(frame);
        return nullptr;
    }
    memcpy(bgrCopy, frame.data, frame.length);
    const int width = static_cast<int>(frame.width);
    const int height = static_cast<int>(frame.height);
    UnlockPixels(frame);

    // 此时已不持有帧锁，bitmap 分配/转换不再阻塞采集
    jobject bitmap = BuildArgb8888BitmapFromBgr(env, bgrCopy, width, height);
    free(bgrCopy);
    return bitmap;
}
