#ifndef BRIDGE_CAPTURE_H
#define BRIDGE_CAPTURE_H

#include "bridge_internal.h"

#include <string>

jobject SetupNativeCapturer(JNIEnv *env, int width, int height);

void ReleaseNativeCapturer();

std::string DescribeReleaseStage();

#endif // BRIDGE_CAPTURE_H
