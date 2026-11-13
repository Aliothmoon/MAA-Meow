cmake_minimum_required(VERSION 3.16)
project(maa_harmony_helper C)
add_executable(maa-harmony-helper helper.c)
target_compile_features(maa-harmony-helper PRIVATE c_std_11)
target_compile_options(maa-harmony-helper PRIVATE -Wall -Wextra -Werror)
if(CMAKE_C_COMPILER_ID MATCHES "Clang")
    # SDK 6.1 toolchain passes --gcc-toolchain even though its clang does not use it.
    target_compile_options(maa-harmony-helper PRIVATE -Wno-unused-command-line-argument)
endif()
