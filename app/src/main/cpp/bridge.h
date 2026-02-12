#ifndef NATIVE_LIB_H
#define NATIVE_LIB_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

#define BRIDGE_API __attribute__((visibility("default")))

// Image format enum
typedef enum {
    IMAGE_FORMAT_UNKNOWN = 0,
    IMAGE_FORMAT_RGBA_8888 = 2
} ImageFormat;

// Frame info structure - for MaaCore to read screen frames
typedef struct {
    uint32_t width;          // Width - 4 bytes
    uint32_t height;         // Height - 4 bytes
    uint32_t stride;         // Row stride - 4 bytes
    uint32_t length;         // Data length - 4 bytes
    void *data;              // Pixel data pointer - 8 bytes
    void *frame_ref;         // FrameBuffer pointer for unlocking - 8 bytes
} FrameInfo;

static_assert(sizeof(FrameInfo) == 32, "FrameInfo size should be 32 bytes");
static_assert(alignof(FrameInfo) == 8, "FrameInfo 8-byte aligned for optimal memory access");

// 帧缓冲状态
typedef enum {
    FRAME_STATE_FREE = 0,     // 空闲，可被写入或读取
    FRAME_STATE_WRITING = 2   // 正在被截图线程写入
} FrameBufferState;

// 缓冲区数量
#define FRAME_BUFFER_COUNT 3

typedef struct {
    uint8_t *data;           // 像素数据
    int width;               // 宽度
    int height;              // 高度
    int stride;              // 行字节数
    size_t size;             // 数据大小
    int64_t timestamp;       // 时间戳(纳秒)
    int64_t frameCount;      // 帧计数
} FrameBuffer;

// Unified method type
typedef enum {
    START_GAME = 1,
    STOP_GAME = 2,
    INPUT = 4,
    TOUCH_DOWN = 6,
    TOUCH_MOVE = 7,
    TOUCH_UP = 8,
    KEY_DOWN = 9,
    KEY_UP = 10
} MethodType;

typedef struct {
    int x;
    int y;
} Position;

typedef struct {
    const char *package_name;   // 应用包名
    int force_stop;             // 是否强制停止 (0=false, 1=true)
} StartGameArgs;

typedef struct {
    const char *client_type;
} StopGameArgs;

typedef struct {
    const char *text;
} InputArgs;

// Touch event parameters
typedef struct {
    Position p;
} TouchArgs;

// Key event parameters
typedef struct {
    int key_code;
} KeyArgs;

typedef union {
    StartGameArgs start_game;
    StopGameArgs stop_game;
    InputArgs input;
    TouchArgs touch;
    KeyArgs key;
} ArgUnion;

typedef struct {
    int display_id;
    MethodType method;
    ArgUnion args;
} MethodParam;


BRIDGE_API void *AttachThread(void);

BRIDGE_API int DetachThread(void *env);

BRIDGE_API FrameInfo GetLockedPixels(void);

BRIDGE_API int UnlockPixels(FrameInfo info);

BRIDGE_API int DispatchInputMessage(MethodParam param);

// 帧缓冲管理
BRIDGE_API void InitFrameBuffers(int width, int height);
BRIDGE_API void ReleaseFrameBuffers(void);

// 从 HardwareBuffer 拷贝帧数据，返回帧计数，失败返回 -1
BRIDGE_API int64_t CopyFrameFromHardwareBuffer(void *env, void *hardwareBufferObj);

// 获取当前帧缓冲（只读）
BRIDGE_API const FrameBuffer *GetCurrentFrame(void);

#ifdef __cplusplus
}

bool CheckJNIException(JNIEnv *env, const char *context);

#endif // __cplusplus

#endif // NATIVE_LIB_H
