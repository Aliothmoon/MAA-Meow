#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Print the MaaFramework version embedded in each deployed libMaaAndroidNativeControlUnit.so.

MAA releases bundle whatever MaaFramework was "latest" on their build day, and
--maafw-tag can override it, so the build log should state what actually shipped.
"""

import re
import sys
from pathlib import Path

JNILIBS_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "jniLibs"
CONTROL_UNIT_SO = "libMaaAndroidNativeControlUnit.so"
VERSION_RE = re.compile(rb"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?")


def main() -> int:
    found = False
    for so in sorted(JNILIBS_DIR.glob(f"*/{CONTROL_UNIT_SO}")):
        found = True
        versions = sorted({m.group().decode() for m in VERSION_RE.finditer(so.read_bytes())})
        print(f"{so.parent.name}/{CONTROL_UNIT_SO}: {', '.join(versions) or 'unknown'}")
    if not found:
        print(f"[WARN] no {CONTROL_UNIT_SO} under {JNILIBS_DIR}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
