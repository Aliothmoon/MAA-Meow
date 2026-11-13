#pragma once

#include <cstddef>
#include <cstdint>

// The host owns capture authorization and the local touch service. Callbacks
// run on MaaCore worker threads; they must never call ArkTS synchronously.
struct MaaHarmonyHost
{
    uint32_t abi_version;
    bool (*frame_size)(int* width, int* height);
    bool (*read_bgr)(uint8_t* output, size_t capacity, int width, int height);
    bool (*click)(int x, int y, int width, int height);
    bool (*swipe)(int x1, int y1, int x2, int y2, int duration, int width, int height);
};

extern "C" __attribute__((visibility("default"))) bool AsstSetHarmonyHost(const MaaHarmonyHost* host);
