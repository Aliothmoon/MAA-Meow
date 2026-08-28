#ifndef NATIVE_LIB_H
#define NATIVE_LIB_H

#include <jni.h>

#include <cstddef>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

#define BRIDGE_API __attribute__((visibility("default")))

struct FrameInfo {
    uint32_t width;
    uint32_t height;
    uint32_t stride;
    uint32_t length;
    void *data;
    void *frame_ref;
};

enum MethodType {
    START_GAME = 1,
    STOP_GAME = 2,
    INPUT = 4,
    TOUCH_DOWN = 6,
    TOUCH_MOVE = 7,
    TOUCH_UP = 8,
    KEY_DOWN = 9,
    KEY_UP = 10
};

struct Position {
    int x;
    int y;
};

struct StartGameArgs {
    const char *package_name;
    int force_stop;
};

struct StopGameArgs {
    const char *client_type;
};

struct InputArgs {
    const char *text;
};

struct TouchArgs {
    Position p;
    // 手指 id 0..15，对齐 MaaFramework >= 5.13.0-beta.3 的 AndroidExternalLib.h；
    // 旧版 control unit 不写此字段，但 fw 侧 MethodParam 一直是 { } 初始化，
    // 此处与 StartGameArgs.force_stop(=0) 同偏移，读到的恒为 0，无需版本闸
    int contact;
};

struct KeyArgs {
    int key_code;
};

union ArgUnion {
    StartGameArgs start_game;
    StopGameArgs stop_game;
    InputArgs input;
    TouchArgs touch;
    KeyArgs key;
};

struct MethodParam {
    int display_id;
    MethodType method;
    ArgUnion args;
};

BRIDGE_API FrameInfo GetLockedPixels(void);
BRIDGE_API int UnlockPixels(FrameInfo info);
BRIDGE_API int DispatchInputMessage(MethodParam param);

#ifdef __cplusplus
}

bool CheckJNIException(JNIEnv *env, const char *context);

#endif

#endif // NATIVE_LIB_H
