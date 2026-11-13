/*
 * Copyright (c) 2024 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef EXTENSION_C_API_H
#define EXTENSION_C_API_H

#include <cstdint>
#include <cstdio>

#ifdef __cplusplus
namespace OHOS::uitest {
extern "C" {
#endif

typedef int32_t RetCode;
#define RETCODE_SUCCESS 0
#define RETCODE_FAIL (-1)

struct Text {
    const char *data;
    size_t size;
};

struct ReceiveBuffer {
    uint8_t *data;
    size_t capacity;
    size_t *size;
};

// enum LogLevel : int32_t { DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6 };

typedef void (*DataCallback)(Text bytes);

struct LowLevelFunctions {
    RetCode (*callThroughMessage)(Text in, ReceiveBuffer out, bool *fatalError);
    RetCode (*setCallbackMessageHandler)(DataCallback handler);
    RetCode (*atomicTouch)(int32_t stage, int32_t px, int32_t py);
    RetCode (*startCapture)(Text name, DataCallback callback, Text optJson);
    RetCode (*stopCapture)(Text name);
    RetCode (*atomicMouseAction)(int32_t stage, int32_t px, int32_t py, int32_t btn);
    RetCode (*atomicMouseActionInDisplay)(int32_t stage, int32_t px, int32_t py, int32_t btn, int32_t displayId);
    RetCode (*atomicTouchInDisplay)(int32_t stage, int32_t px, int32_t py, int32_t displayId);
};

struct UiTestPort {
    RetCode (*getUiTestVersion)(ReceiveBuffer out);
    RetCode (*printLog)(int32_t level, Text tag, Text format, va_list ap);
    RetCode (*getAndClearLastError)(int32_t *codeOut, ReceiveBuffer msgOut);
    RetCode (*initLowLevelFunctions)(LowLevelFunctions *out);
};

// hook function names of UiTestExtension library
#define UITEST_EXTENSION_CALLBACK_ONINIT "UiTestExtension_OnInit"
#define UITEST_EXTENSION_CALLBACK_ONRUN "UiTestExtension_OnRun"
// proto of UiTestExtensionOnInitCallback: void (UiTestPort port, size_t argc, char **argv)
typedef RetCode (*UiTestExtensionOnInitCallback)(UiTestPort port, size_t argc, char **argv);
// proto of UiTestExtensionOnRun
typedef RetCode (*UiTestExtensionOnRunCallback)(void);

#ifdef __cplusplus
}
}
#endif

#endif