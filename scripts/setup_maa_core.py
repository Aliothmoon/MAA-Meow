#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MAA Core Download & Deploy Script
Download prebuilt MAA Core artifacts from GitHub Release,
deploy resources to assets and shared libraries to jniLibs.

usage:
    python scripts/setup_maa_core.py                    # download latest release and deploy
    python scripts/setup_maa_core.py --tag v6.3.0       # download specific tag
    python scripts/setup_maa_core.py --clean             # clean target dirs before deploy
    python scripts/setup_maa_core.py --skip-download     # deploy from cache only
"""

import argparse
import io
import json
import os
import shutil
import sys
import tarfile
import urllib.request
import urllib.error
from pathlib import Path

# Fix Windows console encoding
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

# ── Config ──────────────────────────────────────────────
GITHUB_REPO = "MaaAssistantArknights/MaaAssistantArknights"
API_BASE = f"https://api.github.com/repos/{GITHUB_REPO}"

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


def get_project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def fetch_json(url: str) -> dict:
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/vnd.github.v3+json")
    req.add_header("User-Agent", "MaaMeow-Setup")
    # Support GITHUB_TOKEN env var (CI or rate limit)
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"token {token}")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def download_file(url: str, dest: Path):
    print(f"  [DOWNLOAD] {dest.name}")
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/octet-stream")
    req.add_header("User-Agent", "MaaMeow-Setup")
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        req.add_header("Authorization", f"token {token}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(req, timeout=600) as resp:
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


def extract_and_deploy(tarball: Path, abi: str, project_root: Path, clean: bool):
    assets_dir = project_root / ASSETS_RESOURCE_DIR
    jnilib_dir = project_root / JNILIBS_DIR / abi

    if clean:
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


def main():
    parser = argparse.ArgumentParser(description="Download and deploy prebuilt MAA Core artifacts")
    parser.add_argument("--tag", "-t", help="Specify release tag (default: latest)")
    parser.add_argument("--clean", "-c", action="store_true", help="Clean target dirs before deploy")
    parser.add_argument("--skip-download", "-s", action="store_true", help="Skip download, use cache")
    parser.add_argument("--abi", choices=["arm64-v8a", "x86_64", "all"], default="all",
                        help="Process only specified ABI (default: all)")
    args = parser.parse_args()

    project_root = get_project_root()
    cache_dir = project_root / CACHE_DIR

    print("=" * 55)
    print("==> MAA Core Download & Deploy")
    print("=" * 55)

    # Clean assets resource dir
    if args.clean:
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
    for tarball in sorted(cache_dir.glob("*.tar.gz")):
        for keyword, abi in ABI_MAP.items():
            if keyword in tarball.name and abi in target_abis:
                extract_and_deploy(tarball, abi, project_root, args.clean)
                resource_deployed = True

    if not resource_deployed:
        print("[ERROR] No tar.gz files found in cache. Run without --skip-download first.")
        sys.exit(1)

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
