# HarmonyOS MaaCore port experiment

Target: VOL-AL00 / HarmonyOS 7.0 / arm64-v8a. Everything is built under
`~/Maindome/harmony`; Android ELF files are not used as native dependencies.
Pinned source revisions are recorded in `sources.json`.

## Build

The SDK and signing setup are shared with `scripts/build_harmony_probe.py`.
Research checkouts must exist at `harmony/research/core` and `research/maadeps`,
including the MaaUtils and vcpkg submodules at their recorded revisions.

```sh
python3 scripts/build_harmony_deps.py
bash scripts/build_harmony_core_deps.sh
bash scripts/build_harmony_core.sh
python3 scripts/build_harmony_probe.py
```

`build_harmony_deps.py` builds OpenCV (core/imgproc/imgcodecs), ncnn CPU and zlib
with the SDK CMake/toolchain. Source archive SHA256 values are recorded and checked
on subsequent builds in `harmony/build/deps-ohos-arm64/sources.json`.

The vcpkg triplet builds static support dependencies and a shared ONNXRuntime.
The local ONNX overlay retains the version expected by ONNXRuntime and disables
static registration. The ORT patch excludes Linux thread-affinity calls unavailable
in this SDK and the BF16 kernel requiring unsupported compiler types; it retains
the regular CPU inference path. These changes still require model inference tests.

The core build applies the checked patches to the research checkout and copies
the three Harmony controller files into it. It builds Debug, uses the ncnn OCR
implementation without defining `__ANDROID__`, and does not link Android liblog.

## Host interface

`AsstSetHarmonyHost` registers a versioned native callback table. The host supplies
fresh BGR frames and local click/swipe operations from MaaCore worker threads.
The callbacks must outlive connected core instances. Screen size changes and
stale frames cause operations to fail. The controller reports no precise swipe
or swipe-with-pause features. Unsupported operations return false.

The probe has native host callbacks and an optional `dlopen` check. Merely compiling
these callbacks does not prove that MaaCore can load resources or execute tasks.
The core libraries must be packaged before the check can succeed.

The device currently renders the game at 2232 × 1320. MaaCore's scale proxy
requires exact 16:9. Do not bypass this check and claim ordinary tasks work;
image/coordinate mapping and task recognition need explicit validation.

## Proven recognition experiment

The probe's separate OpenCV navigation test is not a MaaCore task. It matches a
device-specific home settings icon in two fresh frames, long-clicks its detected
center through the phone's local service, verifies a distinct settings header,
returns to the home page and verifies the original icon again. Both templates
are packaged as raw resources. The test deliberately rejects other sizes and
orientations. A missing source icon alone is not accepted as success.

Real-device log (2026-11-13, UTC):

```text
09:58:04.313 recognition before score=0.999133 x=79 y=67 frame=52
09:58:08.772 recognition after settings=true settingsScore=0.999285 frame=73
09:58:12.780 recognition restored score=0.999127 frame=91
09:58:12.780 recognition closed-loop PASS
```

Capture: 385 frames, 96 copies, 19.097 FPS; map/format/stop/release errors zero.
Evidence is in `harmony/artifacts/probe/recognition-report.txt` and
`recognition-frame-report.json`. Ordinary short clicks were acknowledged by the
service but did not open the settings page; long-click did. Gesture timing is
therefore part of the remaining compatibility work.

Base management, claiming rewards, farming and battle deployment are not yet
validated by this experiment.
