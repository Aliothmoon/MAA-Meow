cmake_minimum_required(VERSION 3.22.1)

project("bridge")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(MAA_NATIVE_LTO "Enable link-time optimization" OFF)

add_compile_options(-Os -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden)
add_link_options(-Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)
if (MAA_NATIVE_LTO)
    add_compile_options(-flto)
    add_link_options(-flto)
endif ()

add_library(bridge SHARED
        bridge.cpp
        bridge.h
        bridge_internal.h
        bridge_frame_buffer.h
        bridge_frame_buffer.cpp
        bridge_preview.h
        bridge_preview.cpp
        bridge_capture.h
        bridge_capture.cpp
        bridge_input.h
        bridge_input.cpp
        misc.cpp)
set_source_files_properties(
        bridge.cpp
        bridge_frame_buffer.cpp
        bridge_preview.cpp
        bridge_capture.cpp
        bridge_input.cpp
        PROPERTIES COMPILE_OPTIONS "-O2")
option(ENABLE_FRAME_TIMING "Enable per-frame timing logs" OFF)
if (ENABLE_FRAME_TIMING)
    target_compile_definitions(bridge PRIVATE ENABLE_FRAME_TIMING)
endif ()
add_executable(launcher launcher.c)
set_target_properties(launcher PROPERTIES
        OUTPUT_NAME "liblauncher"
        SUFFIX ".so"
        LINKER_LANGUAGE C)

target_link_libraries(bridge log android jnigraphics mediandk EGL GLESv2)
target_link_libraries(launcher log)
