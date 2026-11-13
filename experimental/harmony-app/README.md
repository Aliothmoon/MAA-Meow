# 鸿蒙最小验证应用

包名：`com.maameow.hmosprobe`，目标 HarmonyOS API 26。

已使用 Linux Command Line Tools 26.0.0.865 编译、签名校验并在真机安装启动。
页面读到 phone / API 26 / 1320 × 2232，点击后计数由 0 变为 1。
当前页面支持屏幕信息、短时屏幕采集、Native 实时取帧与本机 Hypium 触控验证；尚无 MAA 核心任务能力。

从仓库根目录执行完整构建、签名校验和安装启动：

```bash
python3 scripts/build_harmony_probe.py --install
```

省略 `--install` 只构建、签名和验证；多设备时使用 `--target DEVICE_ID`。
本机工具链和 JDK 21 位于仓库外 `~/Maindome/harmony`，环境文件为其中的 `env.sh`。
构建脚本直接配置这些路径，无需提前加载环境文件。
签名材料保留于 `~/Maindome/harmony/signing/`，未写入工程签名配置。
不要将私钥、密码或含密码的配置提交到仓库。

签名产物：`~/Maindome/harmony/build/maa-harmony-probe-signed.hap`。
验证截图及 UI 布局：`~/Maindome/harmony/artifacts/probe/`。
启动时手机需要保持解锁，锁屏时系统会返回 10106102。

Native 辅助进程实验仍独立保留于 `../harmony-helper`，该裸可执行程序此前在手机上被拒绝执行。
原生 HAP 安装成功并不代表应用自动拥有 shell 触摸权限。

## 应用内采集验证

点击“开始采集验证”，在系统弹窗中自行选择采集范围并授权，再切换到卓易通游戏。
正常采集开始后设置 20 秒停止计时器，也可返回应用点击“停止采集”。
文件位于应用私有目录 `files/screen-probe.mp4`，每次验证覆盖上次文件。
录制包含屏幕和系统音频，明确关闭麦克风；不申请系统专用截屏权限。录屏使用 `KEEP_BACKGROUND_RUNNING` 和 `audioRecording` 后台模式，
通过录屏子类型申请长时任务，结束后释放。
该探测验证应用本身的采集通路，与 shell 截图不同；MP4 成功不代表已经获得可用于识别的实时帧。
后台长时任务与自动旋转已在真机复测通过：视频约 20 秒，游戏内容保持正向；
视频画布仍为竖屏并有黑边，后续实时取帧需要另做尺寸与坐标适配。采集状态和 Ability 生命周期写入私有目录的 `capture-report.txt`。

取回调试应用中的文件：

```bash
source ~/Maindome/harmony/env.sh
hdc file recv -b com.maameow.hmosprobe data/storage/el2/base/haps/entry/files/screen-probe.mp4 \
  ~/Maindome/harmony/artifacts/probe/screen-probe.mp4
```

已验证样本：`screen-probe.mp4`，2,540,380 字节，时长 6.123 秒，H.264 / AAC，
视频 1320 × 2232；抽帧确认含从本应用切换至卓易通《明日方舟》的画面。
样本证明应用级跨应用采集成立；停止原因、20 秒稳定性和旋转适配仍需下一轮带日志复测。

修正版样本：`screen-probe-fixed.mp4`，6,902,954 字节，总时长 20.182 秒，
视频轨 19.998 秒，1320 × 2232。日志确认后台任务申请成功，切到后台后仍采集，
20 秒计时触发并正常释放后台任务。画面方向正确，仍有竖屏画布黑边。
此前的 9800005 来自主子类型不匹配：录屏子类型必须配 MODE_AV_PLAYBACK_AND_RECORD，
而非 MODE_AUDIO_RECORDING；不能仅凭主类型的说明推断其允许的子类型。

## Native 实时取帧探测

点击“开始实时取帧”并完成系统授权，切换到游戏停留约 25 秒。
该模式使用 OH_AVScreenCapture 的 OH_ORIGINAL_STREAM 和 RGBA 数据回调，
音频参数全部为零，不采集麦克风与系统音频。与 MP4 采集互斥。
采集开始后再调用 SetMaxVideoFrameRate 设置 20 FPS；该接口在开始前调用会返回 2。
像素复制采用约 5 FPS 节流，保留单张最新帧，按实际 stride 逐行复制。
原生线程在授权后约 20 秒停止；等待用户授权最多 90 秒。

目前由 ArkTS 轮询 Native 状态并申请已经验证的录屏后台长时任务，
原生线程结束后导出 `native-frame.ppm` 和 `native-frame-report.json`。
状态报告包括 API 阶段、帧数、复制帧数、宽高、像素格式、stride、时间戳、
实测接收 FPS、平均映射复制耗时，以及映射和格式错误数。
复制耗时不是端到端画面延迟。PPM 为开发验证文件，尚不是 MaaCore 控制后端。

```bash
source ~/Maindome/harmony/env.sh
hdc file recv -b com.maameow.hmosprobe data/storage/el2/base/haps/entry/files/native-frame.ppm \
  ~/Maindome/harmony/artifacts/probe/native-frame.ppm
hdc file recv -b com.maameow.hmosprobe data/storage/el2/base/haps/entry/files/native-frame-report.json \
  ~/Maindome/harmony/artifacts/probe/native-frame-report.json
```

OH_AVBuffer_GetNativeBuffer 返回的引用在回调内使用完后解除，映射也在回调返回前解除；
不会把系统回调 buffer 指针跨线程或跨回调留存。保留的是自身拥有的像素副本。

### Native 真机验证结果

2026-11-13：20 秒采集收到 404 帧，实测 20.0241 FPS，节流复制 100 帧。
末帧尺寸为 2232 × 1320，格式值 12（RGBA8888），stride 8960 字节。
映射、格式错误均为 0；停止及释放返回 0，后台任务正常释放。
PPM 像素负载严格为 width × height × 3 字节，抽帧检查游戏颜色、方向和画面完整性正常，
没有之前竖屏视频画布引入的上下黑边。
平均映射复制耗时 21.343 ms，仅表示回调内该处理步骤的耗时，不等于端到端延迟。
该版本仍为 20 秒探测工具，不是连续运行的 MAA 控制后端。


## 本机触控最小验证

触控服务需要 HDC 调试身份启动。应用内 HDC 客户端已验证可在用户开启无线调试并
信任应用密钥后，于重启后自行启动预置的 Hypium agent；无需电脑持续参与。
“验证本机服务点击/拖动”通过 localhost:8012 控制本页按钮/滑块；
“采集并测试游戏音量拖动”在系统采集授权、后台任务就绪后等待 10 秒执行游戏音量
往返拖动。测试坐标从应用私有目录 `touch-game-plan.json` 读取，须按实际截图配置，
方向或尺寸不符时取消。不要在其他游戏页面使用旧坐标。

2026-11-13 真机已验证 USB 拔掉后：本页点击、连续拖动，以及后台采集时对卓易通
明日方舟音量页的拖动均可用。复合测试结束时采集和长时任务正常停止。
普通 ArkTS 定时器退后台会延迟，该测试必须配合已授权的屏幕采集任务。
当前服务仅完成实验验证，尚未解决长期寿命、分发、重启后激活和异常触点释放。
详情见 `docs/zh-cn/develop/HARMONYOS-CONTROL-FEASIBILITY.md`。


2026-11-13 补测：重启后本机首次启动新服务 PID 14939 成功，随后点击计数增加。
使用“启动新触控服务（无旧服务时）”，无线端口需手动填写，辅助库仍需预先部署。
“本机重新激活触控服务”尝试 kill 旧服务会被系统拒绝，不能作为当前通用重启方式。
