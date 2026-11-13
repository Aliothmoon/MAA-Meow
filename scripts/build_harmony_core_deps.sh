#!/usr/bin/env bash
set -euo pipefail
source "$HOME/Maindome/harmony/env.sh"
export OHOS_SDK_ROOT="$DEVECO_SDK_HOME/default/openharmony"
export VCPKG_DOWNLOADS="$MAA_HARMONY_HOME/cache/vcpkg-downloads"
export VCPKG_MAX_CONCURRENCY="${VCPKG_MAX_CONCURRENCY:-8}"
export VCPKG_BINARY_SOURCES="clear;files,$MAA_HARMONY_HOME/cache/vcpkg-binaries,readwrite"
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
deps_dir="$MAA_HARMONY_HOME/research/maadeps"
"$deps_dir/vcpkg/vcpkg" install \
    boost-system boost-regex boost-asio boost-dll boost-process boost-uuid \
    polyclipping onnxruntime fmt \
    --triplet maa-arm64-ohos \
    --overlay-triplets="$repo_dir/experimental/harmony-core/triplets" \
    --overlay-ports="$repo_dir/experimental/harmony-core/ports" \
    --x-install-root="$MAA_HARMONY_HOME/build/vcpkg-ohos/installed" \
    --x-buildtrees-root="$MAA_HARMONY_HOME/build/vcpkg-ohos/buildtrees" \
    --x-packages-root="$MAA_HARMONY_HOME/build/vcpkg-ohos/packages" --classic
