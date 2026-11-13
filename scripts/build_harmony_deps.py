#!/usr/bin/env python3
"""Build native recognition dependencies with the HarmonyOS SDK (no Android binaries)."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import shutil
import tarfile
import urllib.request


SOURCE_LOCK = Path(__file__).resolve().parents[1] / 'experimental/harmony-core/native-sources.json'
SOURCES = json.loads(SOURCE_LOCK.read_text())
OPTIONS = {
    'zlib': ['-DZLIB_BUILD_EXAMPLES=OFF'],
    'opencv': ['-DBUILD_LIST=core,imgproc,imgcodecs,features2d,calib3d,videoio', '-DBUILD_SHARED_LIBS=OFF',
               '-DBUILD_TESTS=OFF', '-DBUILD_PERF_TESTS=OFF', '-DBUILD_EXAMPLES=OFF',
               '-DBUILD_opencv_apps=OFF', '-DBUILD_JAVA=OFF', '-DBUILD_opencv_python3=OFF',
               '-DWITH_IPP=OFF', '-DWITH_OPENCL=OFF', '-DWITH_FFMPEG=OFF',
               '-DWITH_GSTREAMER=OFF', '-DWITH_GTK=OFF', '-DWITH_V4L=OFF',
               '-DWITH_ITT=OFF', '-DWITH_OPENEXR=OFF', '-DWITH_WEBP=OFF',
               '-DWITH_TIFF=OFF', '-DWITH_JASPER=OFF', '-DWITH_JPEG=ON',
               '-DBUILD_JPEG=ON', '-DWITH_PNG=ON', '-DBUILD_PNG=ON',
               '-DBUILD_ZLIB=ON', '-DWITH_PROTOBUF=OFF', '-DBUILD_PROTOBUF=OFF', '-DWITH_ADE=OFF',
               '-DOPENCV_ENABLE_ALLOCATOR_STATS=OFF'],
    'ncnn': ['-DNCNN_SHARED_LIB=OFF', '-DNCNN_VULKAN=OFF', '-DNCNN_OPENMP=OFF',
             '-DNCNN_BUILD_TOOLS=OFF', '-DNCNN_BUILD_EXAMPLES=OFF',
             '-DNCNN_BUILD_BENCHMARK=OFF', '-DNCNN_BUILD_TESTS=OFF',
             '-DNCNN_DISABLE_RTTI=OFF', '-DNCNN_DISABLE_EXCEPTION=OFF'],
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--harmony-home', type=Path, default=Path.home() / 'Maindome/harmony')
    parser.add_argument('--only', nargs='+', choices=list(SOURCES), default=list(SOURCES))
    parser.add_argument('--jobs', type=int, default=8)
    args = parser.parse_args()
    home = args.harmony_home.resolve()
    sdk = home / 'command-line-tools/sdk/default/openharmony/native'
    cmake = sdk / 'build-tools/cmake/bin/cmake'
    prefix = home / 'build/deps-ohos-arm64/install'
    manifest_path = home / 'build/deps-ohos-arm64/sources.json'
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    for name in args.only:
        version, url = SOURCES[name]['version'], SOURCES[name]['url']
        archive = home / f'cache/native-sources/{name}-{version}.tar.gz'
        archive.parent.mkdir(parents=True, exist_ok=True)
        if not archive.exists():
            print(f'Download {name} {version}', flush=True)
            temporary = archive.with_suffix('.partial')
            with urllib.request.urlopen(url, timeout=60) as response, temporary.open('wb') as output:
                shutil.copyfileobj(response, output)
            temporary.replace(archive)
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        if digest != SOURCES[name]['sha256']:
            raise RuntimeError(f'Cached archive changed: {archive}')
        manifest[name] = {'version': version, 'url': url, 'sha256': digest}
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        source = home / f'research/native-deps/{name}-{version}'
        if not source.exists():
            source.parent.mkdir(parents=True, exist_ok=True)
            with tarfile.open(archive) as package:
                # All upstream release archives have one top-level directory.
                roots = {m.name.split('/')[0] for m in package.getmembers()}
                if len(roots) != 1:
                    raise RuntimeError(f'Unexpected archive layout: {archive}')
                root = next(iter(roots))
                package.extractall(source.parent, filter='data')
                extracted = source.parent / root
                if extracted != source:
                    extracted.rename(source)
        build = home / f'build/deps-ohos-arm64/{name}'
        command = [str(cmake), '-S', str(source), '-B', str(build), '-G', 'Ninja',
                   f'-DCMAKE_TOOLCHAIN_FILE={sdk}/build/cmake/ohos.toolchain.cmake',
                   '-DOHOS_ARCH=arm64-v8a', '-DOHOS_PLATFORM=OHOS',
                   '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_POSITION_INDEPENDENT_CODE=ON',
                   '-DCMAKE_C_FLAGS=-Wno-unused-command-line-argument',
                   '-DCMAKE_CXX_FLAGS=-Wno-unused-command-line-argument',
                   f'-DCMAKE_INSTALL_PREFIX={prefix}', *OPTIONS[name]]
        print(f'Configure/build/install {name}', flush=True)
        subprocess.run(command, check=True)
        subprocess.run([str(cmake), '--build', str(build), '-j', str(args.jobs)], check=True)
        subprocess.run([str(cmake), '--install', str(build)], check=True)
    print(f'Native dependency prefix: {prefix}', flush=True)


if __name__ == '__main__':
    main()
