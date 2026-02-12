cmake_minimum_required(VERSION 3.22.1)

project("bridge")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(-Os -flto -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden)
add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)

add_library(bridge SHARED bridge.cpp bridge.h misc.cpp)

target_link_libraries(bridge log android jnigraphics)