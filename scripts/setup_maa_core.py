#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Star Rail Core Download & Deploy Script
从 MaaXYZ/MaaFramework 下载安卓引擎 .so，从 VincenttHo/MaaStarRail 拉取星铁资源包。
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import urllib.error
import zipfile
from pathlib import Path

ENGINE_REPO = "MaaXYZ/MaaFramework"
ENGINE_API_BASE = f"https://api.github.com/repos/{ENGINE_REPO}"

RESOURCE_REPO_URL = "https://github.com/VincenttHo/MaaStarRail.git"
RESOURCE_SUBDIR = "assets/resource/base"
INTERFACE_JSON_PATH = "assets/interface.json"

ABI_MAP = {
    "android-aarch64": "arm64-v8a",
    "android-x86_64": "x86_64",
}

EXCLUDE_SO = {"libc++_shared.so"}

ASSETS_RESOURCE_DIR = "app/src/main/assets/StarRailResource"
JNILIBS_DIR = "app/src/main/jniLibs"
CACHE_DIR = ".starrail-cache"
VERSION_FILE = ".starrailengineversion"


def get_project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def fetch_json(url: str) -> dict:
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/vnd.github.v3+json")
    req.add_header("User-Agent", "StarRailMeow-Setup")
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"token {token}")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def download_file(url: str, dest: Path):
    print(f"  [DOWNLOAD] {dest.name}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/octet-stream")
    req.add_header("User-Agent", "StarRailMeow-Setup")
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"token {token}")
    with urllib.request.urlopen(req, timeout=600) as resp:
        with open(dest, "wb") as f:
            shutil.copyfileobj(resp, f)


def get_release_assets(tag: str = None):
    url = f"{ENGINE_API_BASE}/releases/tags/{tag}" if tag else f"{ENGINE_API_BASE}/releases/latest"
    print(f"[FETCH] {url}")
    try:
        data = fetch_json(url)
    except urllib.error.HTTPError as e:
        print(f"[ERROR] {e.code} {e.reason}")
        sys.exit(1)
    return data.get("tag_name", "unknown"), data.get("assets", [])


def find_android_assets(assets: list) -> dict:
    result = {}
    for asset in assets:
        name = asset["name"]
        if not name.endswith(".zip"):
            continue
        for keyword, abi in ABI_MAP.items():
            if keyword in name:
                result[abi] = {"name": name, "url": asset["browser_download_url"], "size": asset["size"]}
    return result


def deploy_engine(zip_path: Path, abi: str, project_root: Path):
    jnilib_dir = project_root / JNILIBS_DIR / abi
    if jnilib_dir.exists():
        for f in jnilib_dir.iterdir():
            if f.suffix == ".so" and f.name != "libjnidispatch.so":
                f.unlink()
    jnilib_dir.mkdir(parents=True, exist_ok=True)

    count = 0
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            name = Path(info.filename).name
            if not name.endswith(".so") or name in EXCLUDE_SO:
                continue
            with z.open(info) as src, open(jnilib_dir / name, "wb") as dst:
                shutil.copyfileobj(src, dst)
            count += 1
    print(f"    [{abi}] 部署 {count} 个 .so 文件")
    return count


def deploy_resource(project_root: Path, ref: str = None):
    assets_dir = project_root / ASSETS_RESOURCE_DIR
    if assets_dir.exists():
        shutil.rmtree(assets_dir)
    assets_dir.parent.mkdir(parents=True, exist_ok=True)

    tmp_clone = project_root / CACHE_DIR / "MaaStarRail_src"
    if tmp_clone.exists():
        shutil.rmtree(tmp_clone)
    tmp_clone.parent.mkdir(parents=True, exist_ok=True)

    print(f"[CLONE] {RESOURCE_REPO_URL}")
    cmd = ["git", "clone", "--depth", "1", "--recursive"]
    if ref:
        cmd += ["--branch", ref]
    cmd += [RESOURCE_REPO_URL, str(tmp_clone)]
    subprocess.run(cmd, check=True)

    configure_script = tmp_clone / "configure.py"
    if configure_script.exists():
        print("[CONFIGURE] 运行 configure.py 填充 OCR 模型...")
        subprocess.run([sys.executable, str(configure_script)], cwd=tmp_clone, check=True)

    src_resource = tmp_clone / RESOURCE_SUBDIR
    if not src_resource.exists():
        print(f"[ERROR] 没找到 {RESOURCE_SUBDIR}/")
        sys.exit(1)

    shutil.copytree(src_resource, assets_dir)
    file_count = sum(1 for _ in assets_dir.rglob("*") if _.is_file())
    print(f"    资源文件: {file_count} 个")

    iface = tmp_clone / INTERFACE_JSON_PATH
    if iface.exists():
        shutil.copy(iface, assets_dir.parent / "interface.json")


def write_version_file(version: str, project_root: Path):
    (project_root / VERSION_FILE).write_text(version + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", "-t")
    parser.add_argument("--resource-ref")
    parser.add_argument("--skip-engine", action="store_true")
    parser.add_argument("--skip-resource", action="store_true")
    parser.add_argument("--abi", choices=["arm64-v8a", "x86_64", "all"], default="all")
    args = parser.parse_args()

    project_root = get_project_root()
    cache_dir = project_root / CACHE_DIR
    target_abis = list(ABI_MAP.values()) if args.abi == "all" else [args.abi]

    print("=" * 55)
    print("==> Star Rail Engine + Resource Setup")
    print("=" * 55)

    if not args.skip_engine:
        tag_name, assets = get_release_assets(args.tag)
        print(f"  引擎版本: {tag_name}")
        android_assets = find_android_assets(assets)
        if not android_assets:
            print("[ERROR] release 里没找到 android zip")
            sys.exit(1)

        for abi, info in android_assets.items():
            if abi not in target_abis:
                continue
            dest = cache_dir / info["name"]
            if not (dest.exists() and dest.stat().st_size == info["size"]):
                download_file(info["url"], dest)
            deploy_engine(dest, abi, project_root)

        write_version_file(tag_name, project_root)
    else:
        print("[SKIP] 跳过引擎下载")

    if not args.skip_resource:
        deploy_resource(project_root, args.resource_ref)
    else:
        print("[SKIP] 跳过资源下载")

    print("[DONE] 完成")


if __name__ == "__main__":
    main()
