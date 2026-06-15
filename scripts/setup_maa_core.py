#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MAA Core Download & Deploy Script
Download prebuilt MAA Core artifacts from GitHub Release,
deploy resources to assets and shared libraries to jniLibs.

usage:
    python scripts/setup_maa_core.py                    # download latest release and deploy
    python scripts/setup_maa_core.py --tag v6.3.0       # download specific tag
    python scripts/setup_maa_core.py --skip-download     # deploy from cache only

Note: target directories (assets/MaaSync/MaaResource and jniLibs/<abi>/<MAA *.so>)
are always cleaned before deploy to avoid stale files from previous versions.
"""

import argparse
import io
import json
import os
import re
import shutil
import sys
import tarfile
import urllib.request
import urllib.error
from pathlib import Path

import convert_ocr_ncnn

# Fix Windows console encoding
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# ── Config ──────────────────────────────────────────────
DEFAULT_GITHUB_REPO = "MaaAssistantArknights/MaaAssistantArknights"
API_BASE = f"https://api.github.com/repos/{DEFAULT_GITHUB_REPO}"

# ABI mapping: release asset keyword -> jniLibs subdirectory
ABI_MAP = {
    "android-arm64": "arm64-v8a",
    "android-x64": "x86_64",
}

# Excluded from jniLibs copy
EXCLUDE_SO = {"libc++_shared.so"}

# Ignored file extensions
IGNORE_EXTENSIONS = {".h"}

# Target paths (relative to project root)
ASSETS_RESOURCE_DIR = "app/src/main/assets/MaaSync/MaaResource"
JNILIBS_DIR = "app/src/main/jniLibs"
CACHE_DIR = ".maa-cache"
VERSION_FILE = ".maaversion"

# Extract version from tarball name, e.g. MAAComponent-v6.12.0-beta.2-android-arm64.tar.gz
TARBALL_VERSION_RE = re.compile(r"-(v\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?)-android-")


def get_project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def fetch_json(url: str) -> dict:
    def _do_request(with_auth: bool) -> dict:
        req = urllib.request.Request(url)
        req.add_header("Accept", "application/vnd.github.v3+json")
        req.add_header("User-Agent", "MaaMeow-Setup")
        if with_auth:
            req.add_header("Authorization", f"token {token}")
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))

    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        return _do_request(with_auth=False)
    try:
        return _do_request(with_auth=True)
    except urllib.error.HTTPError as e:
        # GITHUB_TOKEN is scoped to the current repo and may be rejected (401)
        # when accessing a different public repository. Retry without auth.
        if e.code == 401:
            print(f"[WARN] Auth failed ({e.code}), retrying without token...")
            return _do_request(with_auth=False)
        raise


def download_file(url: str, dest: Path):
    print(f"  [DOWNLOAD] {dest.name}")
    token = os.environ.get("GITHUB_TOKEN")

    def _make_req(with_auth: bool):
        r = urllib.request.Request(url)
        r.add_header("Accept", "application/octet-stream")
        r.add_header("User-Agent", "MaaMeow-Setup")
        if with_auth and token:
            r.add_header("Authorization", f"token {token}")
        return r

    dest.parent.mkdir(parents=True, exist_ok=True)
    try:
        req = _make_req(with_auth=bool(token))
        resp_ctx = urllib.request.urlopen(req, timeout=600)
    except urllib.error.HTTPError as e:
        if e.code == 401 and token:
            print(f"[WARN] Auth failed ({e.code}), retrying without token...")
            req = _make_req(with_auth=False)
            resp_ctx = urllib.request.urlopen(req, timeout=600)
        else:
            raise
    with resp_ctx as resp:
        total = int(resp.headers.get("Content-Length", 0))
        downloaded = 0
        with open(dest, "wb") as f:
            while True:
                chunk = resp.read(1024 * 1024)  # 1MB chunks
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if total > 0:
                    pct = downloaded * 100 // total
                    mb = downloaded / (1024 * 1024)
                    total_mb = total / (1024 * 1024)
                    print(f"\r    {mb:.1f}/{total_mb:.1f} MB ({pct}%)", end="", flush=True)
        print()


def get_release_assets(tag: str = None) -> list:
    if tag:
        url = f"{API_BASE}/releases/tags/{tag}"
    else:
        url = f"{API_BASE}/releases/latest"
    print(f"[FETCH] Fetching release info: {url}")
    try:
        data = fetch_json(url)
    except urllib.error.HTTPError as e:
        print(f"[ERROR] Request failed: {e.code} {e.reason}")
        sys.exit(1)
    tag_name = data.get("tag_name", "unknown")
    print(f"  Tag: {tag_name}")
    return tag_name, data.get("assets", [])


def find_android_assets(assets: list) -> dict:
    """Find android-arm64 and android-x64 tar.gz assets."""
    result = {}
    for asset in assets:
        name = asset["name"]
        if not name.endswith(".tar.gz"):
            continue
        for keyword, abi in ABI_MAP.items():
            if keyword in name:
                result[abi] = {
                    "name": name,
                    "url": asset["browser_download_url"],
                    "size": asset["size"],
                }
    return result


def extract_and_deploy(tarball: Path, abi: str, project_root: Path):
    assets_dir = project_root / ASSETS_RESOURCE_DIR
    jnilib_dir = project_root / JNILIBS_DIR / abi

    if jnilib_dir.exists():
        # Only delete MAA-related .so files, keep libjnidispatch.so etc.
        for f in jnilib_dir.iterdir():
            if f.suffix == ".so" and f.name != "libjnidispatch.so":
                f.unlink()
                print(f"    [DELETE] {abi}/{f.name}")

    jnilib_dir.mkdir(parents=True, exist_ok=True)

    stats = {"resource": 0, "so": 0, "skipped": 0}

    print(f"  [EXTRACT] {tarball.name} -> {abi}")
    with tarfile.open(tarball, "r:gz") as tar:
        for member in tar.getmembers():
            if not member.isfile():
                continue

            name = Path(member.name).name
            parts = Path(member.name).parts

            # Skip header files
            if Path(name).suffix in IGNORE_EXTENSIONS:
                stats["skipped"] += 1
                continue

            # resource dir -> assets
            if "resource" in parts:
                res_idx = list(parts).index("resource")
                rel_parts = parts[res_idx + 1:]
                if not rel_parts:
                    continue
                dest = assets_dir / Path(*rel_parts)
                dest.parent.mkdir(parents=True, exist_ok=True)
                with tar.extractfile(member) as src:
                    dest.write_bytes(src.read())
                stats["resource"] += 1
                continue

            # .so files -> jniLibs
            if name.endswith(".so"):
                if name in EXCLUDE_SO:
                    stats["skipped"] += 1
                    continue
                dest = jnilib_dir / name
                with tar.extractfile(member) as src:
                    dest.write_bytes(src.read())
                stats["so"] += 1
                continue

            stats["skipped"] += 1

    print(f"    resource: {stats['resource']} files, so: {stats['so']} files, skipped: {stats['skipped']}")
    return stats


def write_version_file(version: str, project_root: Path):
    version_file = project_root / VERSION_FILE
    version_file.write_text(version + "\n", encoding="utf-8")
    print(f"  [VERSION] {VERSION_FILE}: {version}")


def main():
    parser = argparse.ArgumentParser(description="Download and deploy prebuilt MAA Core artifacts")
    parser.add_argument("--repo", "-r", default=DEFAULT_GITHUB_REPO,
                        help=f"GitHub repository (owner/repo, default: {DEFAULT_GITHUB_REPO})")
    parser.add_argument("--tag", "-t", help="Specify release tag (default: latest)")
    parser.add_argument("--clean", "-c", action="store_true",
                        help="Deprecated: target dirs are always cleaned before deploy (kept for compatibility)")
    parser.add_argument("--skip-download", "-s", action="store_true", help="Skip download, use cache")
    parser.add_argument("--abi", choices=["arm64-v8a", "x86_64", "all"], default="all",
                        help="Process only specified ABI (default: all)")
    parser.add_argument("--skip-ncnn", action="store_true",
                        help="Skip OCR onnx->ncnn conversion (Android OCR needs ncnn; debug only)")
    parser.add_argument("--keep-onnx", action="store_true",
                        help="Keep OCR inference.onnx after conversion (default: delete, ~72MB unused on Android)")
    parser.add_argument("--rec-fp16", action="store_true",
                        help="Store rec ncnn weights as fp16 (smaller APK; det always stays fp32)")
    args = parser.parse_args()

    global API_BASE
    API_BASE = f"https://api.github.com/repos/{args.repo}"

    project_root = get_project_root()
    cache_dir = project_root / CACHE_DIR

    print("=" * 55)
    print("==> MAA Core Download & Deploy")
    print("=" * 55)

    # Always clean assets resource dir to avoid stale files from previous versions
    assets_dir = project_root / ASSETS_RESOURCE_DIR
    if assets_dir.exists():
        print(f"[DELETE] Cleaning resource dir: {assets_dir}")
        shutil.rmtree(assets_dir)

    target_abis = list(ABI_MAP.values()) if args.abi == "all" else [args.abi]

    if not args.skip_download:
        tag_name, assets = get_release_assets(args.tag)
        android_assets = find_android_assets(assets)

        if not android_assets:
            print("[ERROR] No Android artifacts found. Check if the release contains android-arm64/android-x64 tar.gz files.")
            sys.exit(1)

        print(f"\n[INFO] Found {len(android_assets)} Android artifact(s):")
        for abi, info in android_assets.items():
            size_mb = info["size"] / (1024 * 1024)
            print(f"  {abi}: {info['name']} ({size_mb:.1f} MB)")

        # Download
        print(f"\n[DOWNLOAD] Downloading to cache: {cache_dir}")
        for abi, info in android_assets.items():
            if abi not in target_abis:
                continue
            dest = cache_dir / info["name"]
            if dest.exists() and dest.stat().st_size == info["size"]:
                print(f"  [CACHE] {info['name']} already exists, skipping download")
            else:
                download_file(info["url"], dest)
    else:
        print("[SKIP] Skipping download, using cache")

    # Deploy
    print(f"\n[DEPLOY] Deploying artifacts...")
    resource_deployed = False
    deployed_version = None
    for tarball in sorted(cache_dir.glob("*.tar.gz")):
        for keyword, abi in ABI_MAP.items():
            if keyword in tarball.name and abi in target_abis:
                extract_and_deploy(tarball, abi, project_root)
                resource_deployed = True
                m = TARBALL_VERSION_RE.search(tarball.name)
                if m:
                    deployed_version = m.group(1)

    if not resource_deployed:
        print("[ERROR] No tar.gz files found in cache. Run without --skip-download first.")
        sys.exit(1)

    # Record deployed MAA Core version for the Gradle build (BuildConfig.MAA_CORE_VERSION)
    if deployed_version:
        write_version_file(deployed_version, project_root)
    else:
        print(f"[WARN] Could not parse version from tarball name, {VERSION_FILE} not updated")

    # Convert OCR onnx -> ncnn in place. Android (OcrPackNcnn) needs ncnn; doing it here,
    # from the just-deployed onnx, keeps ncnn always in lockstep with the onnx/keys it
    # ships with, so the Android-only ncnn never has to live in MAA's shared resource.
    if not args.skip_ncnn:
        print("\n[NCNN] Converting OCR onnx -> ncnn (Android-only)...")
        ncnn_cache = cache_dir / "ncnn"
        stats = convert_ocr_ncnn.convert_tree(
            resource_dir=assets_dir,
            cache_dir=ncnn_cache,
            keep_onnx=args.keep_onnx,
            rec_fp16=args.rec_fp16,
        )
        print(f"[NCNN] converted={stats['converted']} cached={stats['cached']} "
              f"onnx_removed={stats['onnx_removed']} skipped={stats['skipped']}")
    else:
        print("\n[SKIP] Skipping OCR ncnn conversion (--skip-ncnn)")

    # Summary
    print("\n" + "=" * 55)
    print("[DONE] Deploy complete!")
    print("=" * 55)
    for abi in target_abis:
        jnilib_dir = project_root / JNILIBS_DIR / abi
        if jnilib_dir.exists():
            so_files = [f.name for f in jnilib_dir.iterdir() if f.suffix == ".so"]
            print(f"  {abi}/: {', '.join(sorted(so_files))}")
    assets_dir = project_root / ASSETS_RESOURCE_DIR
    if assets_dir.exists():
        file_count = sum(1 for f in assets_dir.rglob("*") if f.is_file())
        total_size = sum(f.stat().st_size for f in assets_dir.rglob("*") if f.is_file())
        print(f"  resource: {file_count} files, {total_size / (1024 * 1024):.1f} MB")


if __name__ == "__main__":
    main()
