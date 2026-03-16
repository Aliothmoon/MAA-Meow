cmake_minimum_required(VERSION 3.22.1)

project("bridge")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(-Os -flto -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden)
add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)

add_library(bridge SHARED bridge.cpp bridge.h misc.cpp)
set_source_files_properties(bridge.cpp PROPERTIES COMPILE_OPTIONS "-O2")
add_executable(launcher launcher.c)
set_target_properties(launcher PROPERTIES
        OUTPUT_NAME "liblauncher"
        SUFFIX ".so"
        LINKER_LANGUAGE C)

target_link_libraries(bridge log android jnigraphics)
target_link_libraries(launcher log dl)
