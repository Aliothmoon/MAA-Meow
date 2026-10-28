#ifndef BRIDGE_FRAME_BUFFER_H
#define BRIDGE_FRAME_BUFFER_H

#include "bridge_internal.h"

#include <android/hardware_buffer.h>

#include <string>

typedef enum {
    FRAME_STATE_FREE = 0,
    FRAME_STATE_WRITING = 2
} FrameBufferState;

#define FRAME_BUFFER_COUNT 3

typedef struct {
    uint8_t *bgr_data;
    size_t bgr_size;
    int64_t frame_count;
    int width;
    int height;
    int index;
} FrameBuffer;

void InitFrameBuffers(int width, int height);

void ReleaseFrameBuffers();

std::string DescribeFrameBufferRelease();

bool WriteHardwareBufferToFrame(AHardwareBuffer *buffer);

jobject CreateFrameBufferBitmap(JNIEnv *env);

int64_t GetFrameCount();

// 把当前帧换成黑帧，换了才返回 true。截图照常成功，尺寸不变
// expectedFrameCount 为调用方判定时读到的帧计数，对不上就不换
bool BlankFrame(int64_t expectedFrameCount);

#endif // BRIDGE_FRAME_BUFFER_H
