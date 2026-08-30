#include "bridge.h"
#include <android/log.h>

#define LOG_TAG "LibBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool CheckJNIException(JNIEnv *env, const char *context) {
    if (env->ExceptionCheck()) {
        LOGE("JNI exception in %s", context);
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

