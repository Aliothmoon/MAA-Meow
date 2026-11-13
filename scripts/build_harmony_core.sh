#!/usr/bin/env bash
set -euo pipefail
source "$HOME/Maindome/harmony/env.sh"
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
core_dir="$MAA_HARMONY_HOME/research/core"
sdk_native="$DEVECO_SDK_HOME/default/openharmony/native"
for component in maacore maautils; do
    target_dir="$core_dir"
    if [[ "$component" == maautils ]]; then target_dir="$core_dir/src/MaaUtils"; fi
    patch_file="$repo_dir/experimental/harmony-core/patches/$component-ohos.patch"
    if git -C "$target_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
        : # Already applied exactly.
    else
        git -C "$target_dir" apply --check "$patch_file"
        git -C "$target_dir" apply "$patch_file"
    fi
done
cp "$repo_dir/experimental/harmony-core/"{HarmonyHost.h,HarmonyController.h,HarmonyController.cpp} "$core_dir/src/MaaCore/Controller/"
"$sdk_native/build-tools/cmake/bin/cmake" -S "$core_dir" \
    -B "$MAA_HARMONY_HOME/build/maacore-ohos-arm64" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$sdk_native/build/cmake/ohos.toolchain.cmake" \
    -DOHOS_ARCH=arm64-v8a -DCMAKE_BUILD_TYPE=Debug \
    -DCMAKE_C_FLAGS=-Wno-unused-command-line-argument \
    '-DCMAKE_CXX_FLAGS=-Wno-unused-command-line-argument -D_LIBCPP_ENABLE_EXPERIMENTAL' \
    -DMAA_OHOS_DEPS="$MAA_HARMONY_HOME/build/deps-ohos-arm64/install" \
    -DMAA_OHOS_PORT_DIR="$repo_dir/experimental/harmony-core" \
    -DMAA_OHOS_VCPKG="$MAA_HARMONY_HOME/build/vcpkg-ohos/installed/maa-arm64-ohos" \
    -DMAADEPS_TRIPLET=maa-arm64-ohos -DWITH_RPATH_LIBRARY=OFF \
    -DZLIB_LIBRARY="$MAA_HARMONY_HOME/build/deps-ohos-arm64/install/lib/libz.a" \
    -DZLIB_LIBRARY_RELEASE="$MAA_HARMONY_HOME/build/deps-ohos-arm64/install/lib/libz.a" \
    -DBUILD_WPF_GUI=OFF -DWITH_EXTRA_CALLERS=OFF -DWITH_EMULATOR_EXTRAS=OFF \
    -DWITH_MAC_SCK=OFF
"$sdk_native/build-tools/cmake/bin/cmake" --build \
    "$MAA_HARMONY_HOME/build/maacore-ohos-arm64" -j "${MAA_BUILD_JOBS:-6}"
