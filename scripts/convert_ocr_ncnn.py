#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert PP-OCR onnx models (already deployed under MaaResource) to ncnn in place.

Why this exists:
    Android OCR uses ncnn (OcrPackNcnn), desktop uses fastdeploy/onnx. The ncnn
    weights are Android-only and ~50 MB, so they are NOT shipped in MAA's shared
    `resource/`. Instead we convert them here, at build time, straight from the
    `inference.onnx` that the MAA release tarball already deploys into
    app/src/main/assets/MaaSync/MaaResource. Because the ncnn is generated from the
    very same onnx in the very same step, it can never drift out of version with the
    onnx / keys.txt that ships alongside it.

Recipe (kept faithful to MAA's src/OCRBenchmark/convert.py, validated cos=1.0):
    - rec: onnxsim fix shape to [1,3,48,320] (avoids SVTR dynamic-shape pitfalls)
           -> pnnx inputshape=[1,3,48,320] fp16=0
    - det: pnnx inputshape=[1,3,640,640] fp16=0 (no onnxsim; keep DBNet fully-conv
           dynamic sizing). det MUST be fp32 -- fp16 destroys DBNet probability maps.
    onnx2ncnn is deprecated upstream (2025-09); only pnnx is used.

Driven by glob: every `*/det/inference.onnx` -> det.ncnn.*, every `*/rec/inference.onnx`
-> rec.ncnn.*, written next to the onnx. Automatically covers CN, PaddleCharOCR and all
global languages, and any language MAA adds later.

Usage:
    python scripts/convert_ocr_ncnn.py --resource app/src/main/assets/MaaSync/MaaResource
    # options: --cache .maa-cache/ncnn  --keep-onnx  --rec-fp16
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Bump when the conversion recipe changes so cached artifacts are invalidated.
RECIPE_VERSION = "1"

REC_INPUTSHAPE = "[1,3,48,320]"
REC_SIM_SHAPE = "1,3,48,320"
DET_INPUTSHAPE = "[1,3,640,640]"


def _find_pnnx() -> str:
    exe = shutil.which("pnnx")
    if not exe:
        raise SystemExit(
            "pnnx not found. Install conversion deps: pip install -r scripts/requirements.txt"
        )
    return exe


def _run(cmd: list[str], cwd: Path | None = None) -> None:
    res = subprocess.run(
        [str(c) for c in cmd],
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if res.returncode != 0:
        sys.stderr.write(res.stdout or "")
        sys.stderr.write(res.stderr or "")
        raise SystemExit(f"command failed (exit={res.returncode}): {' '.join(map(str, cmd))}")


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _cache_key(onnx_path: Path, kind: str, rec_fp16: bool) -> str:
    fp16 = rec_fp16 and kind == "rec"
    return f"{_sha256(onnx_path)}-{kind}-fp16{int(fp16)}-r{RECIPE_VERSION}"


def _pnnx_outputs(workdir: Path) -> tuple[Path, Path]:
    params = list(workdir.glob("*.ncnn.param"))
    if len(params) != 1:
        raise SystemExit(f"expected exactly one *.ncnn.param in {workdir}, found {len(params)}")
    param = params[0]
    binp = param.with_suffix("").with_suffix(".bin")  # foo.ncnn.param -> foo.ncnn.bin
    binp = param.parent / (param.name[: -len(".param")] + ".bin")
    if not binp.exists():
        raise SystemExit(f"pnnx .bin not found next to {param}")
    return param, binp


def _convert_into_cache(onnx_path: Path, kind: str, rec_fp16: bool, cache_param: Path, cache_bin: Path) -> None:
    pnnx = _find_pnnx()
    with tempfile.TemporaryDirectory(prefix=f"ncnn_{kind}_") as tmp:
        work = Path(tmp)
        if kind == "rec":
            work_onnx = work / "rec_sim.onnx"
            _run([sys.executable, "-m", "onnxsim", str(onnx_path), str(work_onnx),
                  "--overwrite-input-shape", REC_SIM_SHAPE])
            inputshape = REC_INPUTSHAPE
            fp16 = 1 if rec_fp16 else 0
        else:  # det
            work_onnx = work / "det.onnx"
            shutil.copy(onnx_path, work_onnx)
            inputshape = DET_INPUTSHAPE
            fp16 = 0  # det must stay fp32
        _run([pnnx, work_onnx.name, f"inputshape={inputshape}", f"fp16={fp16}"], cwd=work)
        param, binp = _pnnx_outputs(work)
        cache_param.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(param, cache_param)
        shutil.copy(binp, cache_bin)


def convert_one(onnx_path: Path, kind: str, cache_dir: Path | None, rec_fp16: bool) -> bool:
    """Produce <kind>.ncnn.param/.bin next to onnx_path. Returns True if (re)converted."""
    dst_param = onnx_path.parent / f"{kind}.ncnn.param"
    dst_bin = onnx_path.parent / f"{kind}.ncnn.bin"

    if cache_dir is not None:
        key = _cache_key(onnx_path, kind, rec_fp16)
        cache_param = cache_dir / f"{key}.param"
        cache_bin = cache_dir / f"{key}.bin"
        hit = cache_param.exists() and cache_bin.exists()
        if not hit:
            _convert_into_cache(onnx_path, kind, rec_fp16, cache_param, cache_bin)
        shutil.copy(cache_param, dst_param)
        shutil.copy(cache_bin, dst_bin)
        return not hit

    # No cache: convert into a temp cache pair then copy out.
    with tempfile.TemporaryDirectory(prefix="ncnn_nocache_") as tmp:
        cache_param = Path(tmp) / "out.param"
        cache_bin = Path(tmp) / "out.bin"
        _convert_into_cache(onnx_path, kind, rec_fp16, cache_param, cache_bin)
        shutil.copy(cache_param, dst_param)
        shutil.copy(cache_bin, dst_bin)
    return True


def convert_tree(resource_dir: Path, cache_dir: Path | None, keep_onnx: bool, rec_fp16: bool) -> dict:
    stats = {"converted": 0, "cached": 0, "onnx_removed": 0, "skipped": 0}
    onnx_files = sorted(resource_dir.rglob("inference.onnx"))
    if not onnx_files:
        raise SystemExit(f"no inference.onnx under {resource_dir}; run setup deploy first")

    for onnx_path in onnx_files:
        kind = onnx_path.parent.name
        if kind not in ("det", "rec"):
            stats["skipped"] += 1
            print(f"  [SKIP] not det/rec: {onnx_path}")
            continue
        rel = onnx_path.relative_to(resource_dir)
        reconverted = convert_one(onnx_path, kind, cache_dir, rec_fp16)
        stats["converted" if reconverted else "cached"] += 1
        tag = "CONVERT" if reconverted else "CACHE"
        print(f"  [{tag}] {kind}: {rel.parent}")
        if not keep_onnx:
            onnx_path.unlink()
            stats["onnx_removed"] += 1
    return stats


def main() -> None:
    if sys.platform == "win32":
        # Standalone run: ensure UTF-8 console. (When imported by setup_maa_core.py the
        # caller already handles this; re-wrapping there would close the shared buffer.)
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

    ap = argparse.ArgumentParser(description="Convert deployed PP-OCR onnx to ncnn in place")
    ap.add_argument("--resource", required=True, help="MaaResource dir (contains PaddleOCR/, global/, ...)")
    ap.add_argument("--cache", default=None, help="cache dir keyed by onnx hash (e.g. .maa-cache/ncnn)")
    ap.add_argument("--keep-onnx", action="store_true", help="keep inference.onnx (default: delete OCR onnx after convert)")
    ap.add_argument("--rec-fp16", action="store_true", help="store rec weights as fp16 (smaller; det stays fp32)")
    args = ap.parse_args()

    resource_dir = Path(args.resource).resolve()
    if not resource_dir.is_dir():
        raise SystemExit(f"resource dir not found: {resource_dir}")
    cache_dir = Path(args.cache).resolve() if args.cache else None

    print(f"[NCNN] Converting OCR onnx -> ncnn under {resource_dir}")
    stats = convert_tree(resource_dir, cache_dir, args.keep_onnx, args.rec_fp16)
    print(f"[NCNN] done: converted={stats['converted']} cached={stats['cached']} "
          f"onnx_removed={stats['onnx_removed']} skipped={stats['skipped']}")


if __name__ == "__main__":
    main()
