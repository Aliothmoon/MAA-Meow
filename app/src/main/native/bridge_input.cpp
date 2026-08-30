#include <unistd.h>
#include "bridge_input.h"

static JavaVM *g_jvm = nullptr;
static jclass g_driver_clz = nullptr;
static jmethodID g_touch_down_method = nullptr;
static jmethodID g_touch_move_method = nullptr;
static jmethodID g_touch_up_method = nullptr;
static jmethodID g_key_down_method = nullptr;
static jmethodID g_key_up_method = nullptr;
static jmethodID g_start_app_method = nullptr;

/* upcall 落到 DriverClass -> InputControlUtils/ActivityUtils，那边全是对隐藏 API 的反射，
 * 各家 ROM 上抛异常是常态。异常挂在 JNIEnv 上不清掉，下一次 JNI 调用就是未定义行为
 * （表现为下一轮 upcall 开头的 NewStringUTF 里 SIGSEGV），每次 upcall 后必须清 */
static int FinishUpcall(JNIEnv *env, jboolean result, const char *context) {
    if (CheckJNIException(env, context)) {
        return -1;
    }
    return result ? 0 : -1;
}

static int UpcallTouch(JNIEnv *env, MethodType method, const TouchArgs &touch, int displayId) {
    jmethodID mid = method == TOUCH_DOWN ? g_touch_down_method
                                         : method == TOUCH_MOVE ? g_touch_move_method
                                                                : g_touch_up_method;
    if (!env || !g_driver_clz || !mid) {
        return -1;
    }
    jboolean result = env->CallStaticBooleanMethod(g_driver_clz, mid, touch.p.x, touch.p.y,
                                                   touch.contact, displayId);
    return FinishUpcall(env, result, "DriverClass.touch");
}

static int UpcallKey(JNIEnv *env, MethodType method, int keyCode, int displayId) {
    jmethodID mid = method == KEY_DOWN ? g_key_down_method : g_key_up_method;
    if (!env || !g_driver_clz || !mid) {
        return -1;
    }
    jboolean result = env->CallStaticBooleanMethod(g_driver_clz, mid, keyCode, displayId);
    return FinishUpcall(env, result, "DriverClass.key");
}

static int UpcallStartApp(JNIEnv *env, const char *packageName, int displayId, bool forceStop) {
    if (!env || !packageName || !g_driver_clz || !g_start_app_method) {
        return -1;
    }

    jstring jPackageName = env->NewStringUTF(packageName);
    jboolean result = env->CallStaticBooleanMethod(g_driver_clz, g_start_app_method, jPackageName,
                                                   displayId, static_cast<jboolean>(forceStop));
    env->DeleteLocalRef(jPackageName);
    return FinishUpcall(env, result, "DriverClass.startApp");
}

bool InitInputBridge(JavaVM *vm, JNIEnv *env, const char *driverClassName) {
    g_jvm = vm;
    if (!env || !driverClassName) {
        return false;
    }

    jclass driverClass = env->FindClass(driverClassName);
    if (!driverClass || CheckJNIException(env, "FindClass(driverClassName)")) {
        return false;
    }

    g_driver_clz = static_cast<jclass>(env->NewGlobalRef(driverClass));
    env->DeleteLocalRef(driverClass);
    if (!g_driver_clz) {
        return false;
    }

    g_touch_down_method = env->GetStaticMethodID(g_driver_clz, "touchDown", "(IIII)Z");
    g_touch_move_method = env->GetStaticMethodID(g_driver_clz, "touchMove", "(IIII)Z");
    g_touch_up_method = env->GetStaticMethodID(g_driver_clz, "touchUp", "(IIII)Z");
    g_key_down_method = env->GetStaticMethodID(g_driver_clz, "keyDown", "(II)Z");
    g_key_up_method = env->GetStaticMethodID(g_driver_clz, "keyUp", "(II)Z");
    g_start_app_method = env->GetStaticMethodID(g_driver_clz, "startApp",
                                                "(Ljava/lang/String;IZ)Z");

    if (CheckJNIException(env, "GetStaticMethodID(DriverClass)") ||
        !g_touch_down_method || !g_touch_move_method || !g_touch_up_method ||
        !g_key_down_method || !g_key_up_method || !g_start_app_method) {
        ReleaseInputBridge(env);
        return false;
    }

    return true;
}

void ReleaseInputBridge(JNIEnv *env) {
    g_touch_down_method = nullptr;
    g_touch_move_method = nullptr;
    g_touch_up_method = nullptr;
    g_key_down_method = nullptr;
    g_key_up_method = nullptr;
    g_start_app_method = nullptr;

    if (g_driver_clz && env) {
        env->DeleteGlobalRef(g_driver_clz);
    }
    g_driver_clz = nullptr;
    g_jvm = nullptr;
}

struct JniThreadAttacher {
    JNIEnv *env = nullptr;
    bool needs_detach = false;

    JniThreadAttacher() {
        if (!g_jvm) return;
        if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
            if (g_jvm->AttachCurrentThreadAsDaemon(&env, nullptr) == JNI_OK) {
                needs_detach = true;
                LOGI("JniThreadAttacher: attached thread %d", gettid());
            } else {
                LOGE("JniThreadAttacher: attach failed for thread %d", gettid());
            }
        }
    }

    ~JniThreadAttacher() {
        if (needs_detach && g_jvm) {
            LOGI("JniThreadAttacher: detaching thread %d", gettid());
            g_jvm->DetachCurrentThread();
        }
    }
};

static JNIEnv *GetJNIEnv() {
    thread_local JniThreadAttacher attacher;
    return attacher.env;
}

BRIDGE_API int DispatchInputMessage(MethodParam param) {
    LOGD("DispatchInputMessage: method=%d display_id=%d", param.method, param.display_id);

    auto *env = GetJNIEnv();
    if (!env) {
        return -1;
    }

    switch (param.method) {
        case TOUCH_DOWN:
        case TOUCH_MOVE:
        case TOUCH_UP:
            return UpcallTouch(env, param.method, param.args.touch, param.display_id);
        case KEY_DOWN:
        case KEY_UP:
            return UpcallKey(env, param.method, param.args.key.key_code, param.display_id);
        case START_GAME:
            return UpcallStartApp(env, param.args.start_game.package_name, param.display_id,
                                  param.args.start_game.force_stop != 0);
        default:
            return 0;
    }
}
