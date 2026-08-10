#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
改写 libopencv_world4.so (arm64) 里 KleidiCV 的 SVE2 分派

背景见 issue #202：KleidiCV 在库初始化时按 HWCAP2_SVE2 在 NEON / SVE2 实现间二选一

    tst  x0, #0x2          ; getauxval(AT_HWCAP2) & HWCAP2_SVE2
    ldr  x8, [x8, #...]    ; SVE2 实现
    ldr  x9, [x9, #...]    ; NEON 实现
    csel x8, x9, x8, eq    ; SVE2 位为 0 -> 选 NEON
    str  x8, [...]         ; 写入分派表

探测本身没问题，问题是 BlueStacks 一类环境谎报 HWCAP2_SVE2，实际执行 SVE 指令却 SIGILL
改写 tst 的 Rn 和 csel 的条件域即可锁死分派结果，每个门控点只动 2 条指令

    mode=neon     tst xzr,#0x2 (Z=1) + csel ...,eq  -> 恒选 NEON     发包用
    mode=sve      tst xzr,#0x2 (Z=1) + csel ...,ne  -> 恒选 SVE2     本地复现用
    mode=runtime  tst x0, #0x2       + csel ...,eq  -> 原始运行时探测（还原）

mode=sve 是给本地验证准备的：在**不支持 SVE2** 的普通 arm64 真机上装这个构建，
可以主动复现 #202 的 SIGILL，无需真的找一台谎报 HWCAP2 的模拟器

不动节表段表，ELF 结构与文件大小保持不变

usage:
    python scripts/patch_kleidicv_sve.py app/src/main/jniLibs/arm64-v8a/libopencv_world4.so
    python scripts/patch_kleidicv_sve.py <so> --mode sve      # 造一个必崩的库用于复现
    python scripts/patch_kleidicv_sve.py <so> --mode runtime  # 还原
    python scripts/patch_kleidicv_sve.py <so> --verify        # 校验当前是否为目标模式
    python scripts/patch_kleidicv_sve.py <so> --dry-run
    python scripts/patch_kleidicv_sve.py <so> --expect 0      # 不校验门控点数量
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

COND_EQ = 0x0
COND_NE = 0x1

# mode -> (tst 指令, csel 条件域)
MODES = {
    "neon": (TST_XZR, COND_EQ),
    "sve": (TST_XZR, COND_NE),
    "runtime": (TST_X0, COND_EQ),
}

# OpenCV 4.12.0 + KleidiCV 0.5.0（MaaCore v6.16.x android-arm64）实测门控点数量
DEFAULT_EXPECT = 15

# tst 与配对 csel 之间最多隔几条指令
WINDOW = 4

PT_LOAD = 1
PF_X = 0x1


def is_csel64(word: int) -> bool:
    """64 位 CSEL Xd, Xn, Xm, cond"""
    return (word >> 21) & 0x7FF == 0x4D4 and (word >> 10) & 0x3 == 0x0


def csel_cond(word: int) -> int:
    return (word >> 12) & 0xF


def set_csel_cond(word: int, cond: int) -> int:
    return (word & ~(0xF << 12)) | (cond << 12)


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


def classify(tst_word: int, cond: int):
    for name, (tst, c) in MODES.items():
        if tst_word == tst and cond == c:
            return name
    return None


def find_gates(data: bytes, segments):
    """扫出全部门控点，返回 [(vaddr, tst_off, csel_off, state)]"""
    gates = []
    needles = (struct.pack("<I", TST_X0), struct.pack("<I", TST_XZR))
    for vaddr, offset, filesz in segments:
        end = offset + filesz - (WINDOW + 1) * 4
        for needle in needles:
            pos = offset + (-vaddr % 4)
            while pos < end:
                pos = data.find(needle, pos, end)
                if pos < 0:
                    break
                if (pos - offset + vaddr) % 4 == 0:
                    for i in range(1, WINDOW + 1):
                        word = struct.unpack_from("<I", data, pos + i * 4)[0]
                        if is_csel64(word):
                            tst_word = struct.unpack_from("<I", data, pos)[0]
                            gates.append((
                                vaddr + (pos - offset),
                                pos,
                                pos + i * 4,
                                classify(tst_word, csel_cond(word)),
                            ))
                            break
                pos += 4
    gates.sort(key=lambda g: g[0])
    return gates


def main() -> int:
    ap = argparse.ArgumentParser(description="改写 KleidiCV 的 SVE2 分派")
    ap.add_argument("so", help="libopencv_world4.so (arm64-v8a) 路径")
    ap.add_argument("--mode", choices=sorted(MODES), default="neon",
                    help="目标模式（默认 neon）")
    ap.add_argument("-o", "--output", help="输出路径，默认原地改写")
    ap.add_argument("--dry-run", action="store_true", help="只报告不写入")
    ap.add_argument("--verify", action="store_true", help="校验是否已全部处于目标模式")
    ap.add_argument("--expect", type=int, default=DEFAULT_EXPECT,
                    help=f"期望门控点数量，0 表示不校验（默认 {DEFAULT_EXPECT}）")
    args = ap.parse_args()

    src = Path(args.so)
    if not src.is_file():
        print(f"[ERROR] 文件不存在: {src}", file=sys.stderr)
        return 1

    data = bytearray(src.read_bytes())
    segments = load_exec_segments(bytes(data))
    gates = find_gates(bytes(data), segments)

    counts = {}
    for *_, state in gates:
        counts[state or "unknown"] = counts.get(state or "unknown", 0) + 1
    summary = ", ".join(f"{k}={v}" for k, v in sorted(counts.items())) or "无"

    print(f"[INFO] 目标: {src}")
    print(f"[INFO] 门控点 {len(gates)} 处，当前状态: {summary}")

    if not gates:
        print("[ERROR] 没找到任何 SVE2 门控点，OpenCV/KleidiCV 版本可能变了", file=sys.stderr)
        return 1

    unknown = [g for g in gates if g[3] is None]
    if unknown:
        print(f"[ERROR] {len(unknown)} 处门控点形态无法识别，拒绝改写:", file=sys.stderr)
        for vaddr, *_ in unknown:
            print(f"         vaddr=0x{vaddr:x}", file=sys.stderr)
        return 1

    if args.expect and len(gates) != args.expect:
        print(f"[ERROR] 门控点共 {len(gates)} 处，与期望的 {args.expect} 不符\n"
              f"        OpenCV/KleidiCV 版本可能变了，确认无误后用 --expect {len(gates)} 覆盖",
              file=sys.stderr)
        return 1

    stale = [g for g in gates if g[3] != args.mode]

    if args.verify:
        if stale:
            print(f"[FAIL] {len(stale)} 处不是 {args.mode} 模式:", file=sys.stderr)
            for vaddr, _, _, state in stale:
                print(f"         vaddr=0x{vaddr:x}  state={state}", file=sys.stderr)
            return 1
        print(f"[OK] 全部 {len(gates)} 处已处于 {args.mode} 模式")
        return 0

    if not stale:
        print(f"[SKIP] 全部 {len(gates)} 处已处于 {args.mode} 模式")
        return 0

    if args.mode == "sve":
        print("[WARN] sve 模式产出的库在不支持 SVE2 的设备上必定 SIGILL，仅用于本地复现")

    tst_word, cond = MODES[args.mode]
    for vaddr, tst_off, csel_off, state in stale:
        print(f"  {state} -> {args.mode}  vaddr=0x{vaddr:x}")
        struct.pack_into("<I", data, tst_off, tst_word)
        old = struct.unpack_from("<I", data, csel_off)[0]
        struct.pack_into("<I", data, csel_off, set_csel_cond(old, cond))

    if args.dry_run:
        print("[DRY-RUN] 未写入")
        return 0

    dst = Path(args.output) if args.output else src
    dst.write_bytes(bytes(data))
    print(f"[OK] 已写入 {dst}（{len(stale)} 处门控点）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
