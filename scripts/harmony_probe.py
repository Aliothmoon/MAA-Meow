#!/usr/bin/env python3
"""Development-only HarmonyOS controller over an authorized hdc connection."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hdc", default=os.environ.get("HDC") or shutil.which("hdc")
                        or str(Path.home() / "Maindome/harmony/linux/toolchains/hdc"))
    parser.add_argument("--target", help="Device ID; required when multiple devices are connected")
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("status")
    capture = sub.add_parser("capture")
    capture.add_argument("output", type=Path, help="Output JPEG path")
    for name in ("tap", "long-press"):
        cmd = sub.add_parser(name)
        cmd.add_argument("x", type=int)
        cmd.add_argument("y", type=int)
        if name == "long-press":
            cmd.add_argument("--duration", type=int, default=800, help="Milliseconds")
    swipe = sub.add_parser("swipe")
    for coord in ("x1", "y1", "x2", "y2"):
        swipe.add_argument(coord, type=int)
    swipe.add_argument("--duration", type=int, default=600, help="Milliseconds")
    args = parser.parse_args()
    if any(getattr(args, name, 0) < 0 for name in ("x", "y", "x1", "y1", "x2", "y2")):
        parser.error("Coordinates must be nonnegative physical screen pixels")
    if not 1 <= getattr(args, "duration", 600) <= 15000:
        parser.error("Duration must be between 1 and 15000 ms")

    def run(*command, target=True):
        argv = [args.hdc]
        if target and args.target:
            argv += ["-t", args.target]
        started = time.monotonic()
        result = subprocess.run(argv + list(command), text=True, capture_output=True, timeout=30)
        output = result.stdout + result.stderr
        if result.returncode or "[Fail]" in output:
            raise RuntimeError(output.strip() or f"hdc exited with {result.returncode}")
        print(f"{command[0]}: {time.monotonic() - started:.2f}s", file=sys.stderr)
        return output

    listing = run("list", "targets", "-v", target=False)
    devices = [line.split()[0] for line in listing.splitlines()
               if "Connected" in line.split()]
    if args.action == "status":
        print(listing, end="")
    if args.target:
        if args.target not in devices:
            raise RuntimeError("Selected device is not connected and authorized")
    elif len(devices) == 1:
        args.target = devices[0]
    else:
        raise RuntimeError("Expected one authorized device; connect it or use --target")

    if args.action == "status":
        for key in ("const.product.model", "const.ohos.fullname", "const.ohos.apiversion"):
            print(f"{key}: {run('shell', 'param', 'get', key).strip()}")
    elif args.action == "capture":
        remote = f"/data/local/tmp/maa-probe-{uuid.uuid4().hex}.jpeg"
        args.output.parent.mkdir(parents=True, exist_ok=True)
        try:
            output = run("shell", "snapshot_display", "-f", remote)
            if "success: snapshot" not in output:
                raise RuntimeError(output)
            print(output, end="")
            print(run("file", "recv", remote, str(args.output.resolve())), end="")
            if not args.output.is_file() or args.output.stat().st_size == 0:
                raise RuntimeError("Screenshot transfer produced no image")
        finally:
            run("shell", "rm", "-f", remote)
    elif args.action == "tap":
        print(run("shell", "uinput", "-T", "-c", str(args.x), str(args.y)), end="")
    elif args.action == "long-press":
        print(run("shell", "uinput", "-T", "-d", str(args.x), str(args.y),
                  "-i", str(args.duration), "-u", str(args.x), str(args.y)), end="")
    elif args.action == "swipe":
        print(run("shell", "uinput", "-T", "-m", str(args.x1), str(args.y1),
                  str(args.x2), str(args.y2), str(args.duration)), end="")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
