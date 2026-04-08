#ifndef BRIDGE_FRAME_BUFFER_H
#define BRIDGE_FRAME_BUFFER_H

#include "bridge_internal.h"

#include <android/hardware_buffer.h>

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
bool WriteHardwareBufferToFrame(AHardwareBuffer *buffer);
jobject CreateFrameBufferBitmap(JNIEnv *env);
int64_t GetFrameCount();

#endif // BRIDGE_FRAME_BUFFER_H
