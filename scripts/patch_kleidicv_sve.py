#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
关闭 libopencv_world4.so (arm64) 里 KleidiCV 的 SVE2 分派

背景见 issue #202：KleidiCV 在库初始化时按 HWCAP2_SVE2 在 NEON / SVE2 实现间二选一

    tst  x0, #0x2          ; getauxval(AT_HWCAP2) & HWCAP2_SVE2
    ldr  x8, [x8, #...]    ; SVE2 实现
    ldr  x9, [x9, #...]    ; NEON 实现
    csel x8, x9, x8, eq    ; SVE2 位为 0 -> 选 NEON
    str  x8, [...]         ; 写入分派表

探测本身没问题，问题是 BlueStacks 一类环境谎报 HWCAP2_SVE2，实际执行 SVE 指令直接 SIGILL
把 tst 的 Rn 从 x0 改成 xzr，结果恒为 0 -> Z=1 -> eq 成立 -> 恒选 NEON

    f27f001f  tst x0,  #0x2
    f27f03ff  tst xzr, #0x2

只改 15 处 4 字节，不动节表段表，ELF 结构与文件大小保持不变

usage:
    python scripts/patch_kleidicv_sve.py app/src/main/jniLibs/arm64-v8a/libopencv_world4.so
    python scripts/patch_kleidicv_sve.py <so> --dry-run     # 只报告不写入
    python scripts/patch_kleidicv_sve.py <so> --verify      # 校验是否已全部打完
    python scripts/patch_kleidicv_sve.py <so> --expect 0    # 不校验门控点数量
"""

import argparse
import io
import struct
import sys
from pathlib import Path

# Fix Windows console encoding
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

TST_X0 = 0xF27F001F   # tst x0,  #0x2
TST_XZR = 0xF27F03FF  # tst xzr, #0x2

# OpenCV 4.12.0 + KleidiCV 0.5.0（MaaCore v6.16.x android-arm64）实测门控点数量
DEFAULT_EXPECT = 15

# csel 之后最多隔几条指令
WINDOW = 4

PT_LOAD = 1
PF_X = 0x1


def is_csel_eq(word: int) -> bool:
    """64 位 CSEL Xd, Xn, Xm, EQ"""
    return (
        (word >> 21) & 0x7FF == 0x4D4  # sf=1 op=0 S=0 11010100
        and (word >> 12) & 0xF == 0x0  # cond = EQ
        and (word >> 10) & 0x3 == 0x0  # op2
    )


def load_exec_segments(data: bytes):
    """返回可执行 PT_LOAD 段 [(vaddr, offset, filesz)]"""
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise SystemExit("[ERROR] 不是 64 位小端 ELF")
    if struct.unpack_from("<H", data, 0x12)[0] != 0xB7:
        raise SystemExit("[ERROR] 不是 AArch64 目标")

    e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
    e_phentsize, e_phnum = struct.unpack_from("<HH", data, 0x36)

    segments = []
    for i in range(e_phnum):
        base = e_phoff + i * e_phentsize
        p_type, p_flags = struct.unpack_from("<II", data, base)
        if p_type != PT_LOAD or not (p_flags & PF_X):
            continue
        p_offset, p_vaddr = struct.unpack_from("<QQ", data, base + 0x08)
        p_filesz = struct.unpack_from("<Q", data, base + 0x20)[0]
        segments.append((p_vaddr, p_offset, p_filesz))
    if not segments:
        raise SystemExit("[ERROR] 没有可执行 PT_LOAD 段")
    return segments


def scan(data: bytes, segments, opcode: int):
    """扫可执行段里 opcode + 后随 csel-eq 的门控点，返回 [(vaddr, file_off)]"""
    hits = []
    target = struct.pack("<I", opcode)
    for vaddr, offset, filesz in segments:
        # 段首未必 4 字节对齐到指令边界，按 vaddr 对齐起步
        start = offset + (-vaddr % 4)
        end = offset + filesz - (WINDOW + 1) * 4
        pos = start
        while pos < end:
            pos = data.find(target, pos, end)
            if pos < 0:
                break
            if (pos - offset + vaddr) % 4 == 0:
                window = struct.unpack_from(f"<{WINDOW}I", data, pos + 4)
                if any(is_csel_eq(w) for w in window):
                    hits.append((vaddr + (pos - offset), pos))
            pos += 4
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description="关闭 KleidiCV 的 SVE2 分派")
    ap.add_argument("so", help="libopencv_world4.so (arm64-v8a) 路径")
    ap.add_argument("-o", "--output", help="输出路径，默认原地改写")
    ap.add_argument("--dry-run", action="store_true", help="只报告不写入")
    ap.add_argument("--verify", action="store_true", help="校验是否已无残留门控点")
    ap.add_argument(
        "--expect",
        type=int,
        default=DEFAULT_EXPECT,
        help=f"期望门控点数量，0 表示不校验（默认 {DEFAULT_EXPECT}）",
    )
    args = ap.parse_args()

    src = Path(args.so)
    if not src.is_file():
        print(f"[ERROR] 文件不存在: {src}", file=sys.stderr)
        return 1

    data = bytearray(src.read_bytes())
    segments = load_exec_segments(bytes(data))

    pending = scan(data, segments, TST_X0)
    patched = scan(data, segments, TST_XZR)

    print(f"[INFO] 目标: {src}")
    print(f"[INFO] 待处理门控点 {len(pending)}，已处理 {len(patched)}")

    if args.verify:
        if pending:
            print(f"[FAIL] 仍有 {len(pending)} 处 SVE2 门控未处理:", file=sys.stderr)
            for vaddr, _ in pending:
                print(f"         vaddr=0x{vaddr:x}", file=sys.stderr)
            return 1
        if not patched:
            print("[FAIL] 一处门控点都没找到，补丁可能没生效", file=sys.stderr)
            return 1
        print(f"[OK] 全部 {len(patched)} 处已走 NEON")
        return 0

    if not pending:
        if patched:
            print(f"[SKIP] 已打过补丁（{len(patched)} 处）")
            return 0
        print("[ERROR] 没找到任何 SVE2 门控点，OpenCV/KleidiCV 版本可能变了", file=sys.stderr)
        return 1

    total = len(pending) + len(patched)
    if args.expect and total != args.expect:
        print(
            f"[ERROR] 门控点共 {total} 处，与期望的 {args.expect} 不符\n"
            f"        OpenCV/KleidiCV 版本可能变了，请重新确认后用 --expect {total} 覆盖",
            file=sys.stderr,
        )
        return 1

    for vaddr, off in pending:
        print(f"  patch vaddr=0x{vaddr:x}  file_off=0x{off:x}")

    if args.dry_run:
        print("[DRY-RUN] 未写入")
        return 0

    new_bytes = struct.pack("<I", TST_XZR)
    for _, off in pending:
        data[off:off + 4] = new_bytes

    dst = Path(args.output) if args.output else src
    dst.write_bytes(bytes(data))
    print(f"[OK] 已写入 {dst}（{len(pending)} 处 × 4 字节）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
