#ifndef BRIDGE_PREVIEW_H
#define BRIDGE_PREVIEW_H

#include "bridge_internal.h"

#include <media/NdkImage.h>

void SetPreviewSurface(JNIEnv *env, jobject jSurface);

void ShutdownPreview(JNIEnv *env);

bool IsPreviewEnabled();

bool DispatchPreview(AImage *image);

void DrainPreviewQueue();

#endif // BRIDGE_PREVIEW_H
