#ifndef BRIDGE_CAPTURE_H
#define BRIDGE_CAPTURE_H

#include "bridge_internal.h"

jobject SetupNativeCapturer(JNIEnv *env, int width, int height);

void ReleaseNativeCapturer();

#endif // BRIDGE_CAPTURE_H
