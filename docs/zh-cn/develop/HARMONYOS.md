# 鸿蒙移植验证记录

## 本机工作目录（2026-11-13 迁移后）

- 项目：`~/Maindome/MAA-Meow`
- SDK、下载包和工具：`~/Maindome/harmony`
- 签名材料：`~/Maindome/harmony/signing`（仓库外）
- Native 构建：`~/Maindome/harmony/build/helper`
- 探测截图和历史临时产物：`~/Maindome/harmony/artifacts/probe`
- Hvigor/OHPM 缓存：`~/Maindome/harmony/cache/hvigor`、`cache/ohpm`

项目旧路径、`~/Downloads/harmony`、`~/.hvigor` 和 `~/.ohpm` 的迁移软链接
已按用户要求移除。后续命令在 `~/Maindome/MAA-Meow` 执行，直接使用新路径。
迁移复制后进行了内容校验，Git 状态保留。实际数据位于 Maindome 独立磁盘。
原 Hvigor/OHPM 缓存保存在上述 cache 目录；安装完整工具链时需配置其缓存位置。
以后下载完整 Command Line Tools 也直接放入 `~/Maindome/harmony`。

后续终端可使用：

```bash
source ~/Maindome/harmony/env.sh
cd ~/Maindome/MAA-Meow
```

该环境文件将 hdc、临时文件和 npm 缓存指向 Maindome；不更改系统 Java/Node 安装。

## 当前结论

2026-11-13，在用户提供的华为真机上，通过已授权的 USB hdc 连接，
已验证鸿蒙侧可以截取卓易通中的《明日方舟》画面并发送触摸操作。
这只证明 shell 调试权限下的控制链路成立，不代表普通 HAP 有相同权限。

设备返回型号 VOL-AL00，系统 OpenHarmony-7.0.0.111，API 26；
卓易通包名 com.droi.tong，版本 1.0.10.93。当前横屏截图为 2232 × 1320。

| 能力 | 证据与限制 |
| --- | --- |
| 截图 | snapshot_display 成功取得容器内游戏画面；脚本采集约 0.5～0.7 秒，传输约 0.04～0.06 秒，非性能基准 |
| 点击 | 点击 START 进入开始唤醒页面；点击设置进入游戏设置 |
| 滑动 | 声音页音乐音量由 100 拖至 54，再反向拖回 100，截图确认恢复 |
| 应用内采集 | MP4 采集 20 秒通过；Native 原始像素回调约 20 FPS、节流复制约 5 FPS，并导出有效游戏帧 |
| 持续按下 | 800 ms down/interval/up 触发声音页签切换；尚未验证游戏长按语义或战斗拖拽 |
| 原生 HAP | 已编译、签名校验、安装和启动；设备信息与按钮交互正常，尚无游戏控制能力 |
| MaaCore | 尚未接入此控制后端；没有执行自动化作战 |

## 复现工具

仓库根目录使用 Python 3：

```bash
python3 scripts/harmony_probe.py status
python3 scripts/harmony_probe.py capture /tmp/game.jpeg
python3 scripts/harmony_probe.py tap 1116 1250
python3 scripts/harmony_probe.py long-press 230 495 --duration 800
python3 scripts/harmony_probe.py swipe 2080 390 1840 390 --duration 600
```

触摸例子来自测试时的画面，执行前必须确认当前页面。坐标使用截图物理像素，
旋转、折叠、显示尺寸改变后需重新取图定位。工具不会自动点击或启动任务。

通过 `--hdc /path/to/hdc` 或 `HDC` 环境变量指定工具；默认优先 PATH，
再尝试 `~/Maindome/harmony/linux/toolchains/hdc`。多设备用 `--target DEVICE_ID`。
截图使用独立的远端临时文件并在传输后清理。命令成功返回不保证触摸被游戏接受，
必须结合截图判断。

## 原生手机端的主要限制

官方 API 文档中，`OH_Input_InjectTouchEvent` 标注仅 PC/2in1 正常调用，
其他设备调用无效果；公开注入授权路径不能直接作为 Phone 的控制方案。
不能把 shell 下 uinput 可用推导成普通应用中执行 uinput 也可用。

- [输入 C API](https://developer.huawei.com/consumer/cn/doc/doccenter-references/api/capi-oh-input-manager-h/)
- [Native 屏幕采集](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/avscreencapture-c-basic-process/)

本机已有 OpenHarmony SDK 6.1.0.31 的 Linux 组件 ZIP 与解压的 toolchains。
另已安装 HarmonyOS Command Line Tools 26.0.0.865，完成 API 26 HAP 构建与真机验证。

## 下一阶段

### 辅助进程实测补充（2026-11-13）

已解压 Native SDK，确认 API 为 23（SDK 6.1.0.31），可使用其 Clang 15.0.4
交叉编译 ARM64 OHOS 可执行程序。实验源码见 `experimental/harmony-helper`。
辅助程序提供带令牌鉴权的本机 socket 协议，仅允许状态、取图、点击、滑动和停止；
电脑端已验证协议鉴权和参数检查。

真机 hdc shell 身份为 UID 2000、SELinux 域 `u:r:sh:s0`。自编译程序上传及
chmod 成功，但从 `/data/local/tmp` 和 `/data/local/tmp/debugserver` 执行均被拒绝。
目前不能按 Android Shizuku 的方式直接部署裸辅助进程；未验证断线存活与应用 IPC。
手机上测试上传的二进制、令牌和日志已清理。

[官方 Native 调试说明](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/debug-lldb/)
区分 root 镜像与 user 镜像，后者应通过编译签名的 debug HAP 调试。
因此下一阶段需要配套的 HarmonyOS SDK、HAP 构建工具及绑定该设备的调试签名；
签名只解决安装调试问题，不代表应用自动获得 shell 控制权限。

### 尚无开发者账号时的准备

无需为了命令行开发另备 Windows/Mac：官方提供 Linux 命令行构建流程，
包括配套 JDK、Node、Hvigor、OHPM 和 SDK。目前本机已确认 Node、Java、
OpenHarmony SDK 压缩包及 Native 编译器；现有 toolchains 目录中没有提供
完整 HAP 工程所需的 Hvigor/OHPM，因此还不能宣称 HAP 构建环境就绪。

- [官方 Linux 命令行构建流程](https://developer.huawei.com/consumer/cn/doc/doccenter-deveco-studio/ide-command-line-building-app/)
- [华为开发者联盟入口](https://developer.huawei.com/consumer/cn/)
- [自动签名流程](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/ide-signing-auto/)
- [调试设备注册](https://developer.huawei.com/consumer/cn/doc/app/agc-help-add-device-0000001946142249/)

用户需要本人注册/登录华为开发者账号，并按平台要求完成开发者认证。
后续使用官方自动签名，或申请调试证书、注册本机 UDID、生成绑定设备的调试 Profile。
普通华为账号实名认证与开发者实名认证不应混淆。账号身份操作不由脚本代办，
密码、验证码和身份证资料无需交给开发代理。

设备 UDID 可在本机执行以下命令取得，仅在注册调试设备时使用：

```bash
~/Maindome/harmony/linux/toolchains/hdc shell bm get --udid
```

在签名条件具备前，可以继续进行源码开发、Native 交叉编译及电脑端 hdc 控制验证。

### 签名材料和最小工程进展

用户已提供证书及调试 Profile。已验证本地密钥库、CSR、证书链中的叶子证书、
Profile 内嵌证书使用相同公钥；Profile 类型为 debug，包名为
`com.maameow.hmosprobe`，设备列表包含当前真机。CMS 内容签名验证通过；
未执行完整的华为 CA 信任链、吊销检查，最终以安装端校验为准。
密钥及证书材料均保存在仓库外，未写入源代码。

最小原生应用工程已创建于 `experimental/harmony-app`，目标 HarmonyOS API 26。
页面用于验证启动、读取屏幕尺寸和交互，不含控制游戏功能。
现已完成 ArkTS 编译、hap-sign-tool 签名与验签、hdc 安装及启动。
页面读取 phone / API 26 / 1320 × 2232，UI 布局确认按钮计数由 0 变成 1。
构建工具为 Command Line Tools 26.0.0.865 / SDK 26.0.0.105、Hvigor 6.26.9、
OHPM 26.0.0.630；JDK 21.0.13 与构建缓存也位于 Maindome。
复现命令：`python3 scripts/build_harmony_probe.py --install`。
签名产物：`~/Maindome/harmony/build/maa-harmony-probe-signed.hap`。
该版本尚无游戏取图、输入注入与核心自动化能力。

1. 研究 Phone 可用的控制授权路径，或由 hdc 启动的受控辅助进程与原生应用通信；
   必须实测进程身份、IPC、断开电脑后的行为及重启后的授权，不能预设类似 Shizuku 的方案成立。
2. 若保留电脑连接，先实现 MaaCore 与 hdc 后端适配；当前 Python 工具只是控制验证，
   不是 MAA 已支持的控制单元。识别输入还需处理非 16:9 画面、缩放与坐标映射。
3. 控制路径确定后，再编译鸿蒙版核心依赖，建立 Node-API 封装与最小 ArkUI 页面。
4. 用一次完整任务验证闭环，最后再研究后台运行、定时任务和虚拟屏。

### 应用内取图验证结果

2026-11-13，用户在原生应用中授权并切换到卓易通游戏后，已导出有效 MP4。
原先导出路径按应用基础目录填写，实测应使用模块私有路径
`data/storage/el2/base/haps/entry/files/screen-probe.mp4`。

样本 2,540,380 字节、6.123 秒，包含 H.264 视频与 AAC 音频；视频为
1320 × 2232。抽帧可见本应用、应用切换界面以及卓易通中的《明日方舟》开始页面。
因此已证明普通原生 HAP 经用户授权能采到跨应用游戏画面，无需 shell 执行截图工具。
该结果不代表游戏输入权限、实时帧接口或长期后台稳定性已打通。

样本短于预设 20 秒，旧版无持久化状态日志，无法确定停止原因；画面也未随游戏横屏转正。
已加入录屏后台长时任务、API 26 自动旋转、采集和 Ability 生命周期日志，完成编译签名更新，
等待用户再次授权复测。样本、元数据和抽帧图保存在仓库外 `harmony/artifacts/probe/`。

### 后台任务主子类型修正

新版日志明确显示：录屏进入 STARTED 后，后台任务返回 9800005，错误为主类型和子类型
不匹配，应用随后主动结束录制。原实现将 MODE_AUDIO_RECORDING 与
SUBMODE_SCREEN_RECORD_NORMAL_NOTIFICATION 搭配，属于实现错误。
官方映射要求该录屏子类型配合 MODE_AV_PLAYBACK_AND_RECORD；已修正并完成
编译、签名校验及安装。原始失败日志保存在 artifacts/probe/capture-report-9800005.txt。

[官方主子类型映射](https://developer.huawei.com/consumer/cn/doc/harmonyos-references/js-apis-resourceschedule-backgroundtaskmanager/)

### 9800005 修复后真机复测

修正版已编译、签名、验签、安装，并由用户再次授权采集。
日志显示后台任务 484 申请成功，07:55:36 UTC 进入后台，07:55:54 UTC 的
20 秒计时器正常触发，随后后台任务成功停止，文件正常关闭。
样本 `screen-probe-fixed.mp4` 为 6,902,954 字节，总时长 20.182 秒，视频轨
19.997689 秒；H.264 / AAC，视频画布 1320 × 2232。
第 10 秒抽帧可见卓易通《明日方舟》画面，方向已经转正，但竖屏画布上下存在黑边。
因此本次后台录屏失败已修复并实测通过；实时原始帧获取、有效游戏区域与触摸坐标映射仍未实现。

### Native 实时取帧实测

已新增 `entry/src/main/cpp/frameprobe.cpp` 与 ArkTS 的 `NativeFrameProbe`。
C API 使用 OH_ORIGINAL_STREAM、RGBA 视频源、零音频采样参数，用户授权后
在 Native 回调内映射 OH_NativeBuffer，按 stride 逐行复制至自有内存。
系统 buffer 在回调结束前解除映射并释放引用；不会跨回调持有借用像素地址。

初版将 SetMaxVideoFrameRate 放在 Start 前，实机返回 2；按 SDK 约束改为
采集进入 STARTED 后在工作线程调用，并补充 API 阶段字段。修正后已重新编译、
签名验签、安装并由用户完成授权复测。

实测约 20 秒收到 404 帧，20.0241 FPS；节流复制 100 帧。
最终有效游戏图像为 2232 × 1320，RGBA8888（12），stride 8960 字节。
映射和格式错误均为零，停止与释放返回零，后台任务成功释放。
平均回调映射复制步骤耗时 21.343 ms，不代表端到端画面延迟。
PPM 文件像素数量与尺寸一致，查看图像确认游戏画面颜色与方向正常，
没有竖屏 MP4 容器的上下黑边。

样本为仓库外 `harmony/artifacts/probe/native-frame.ppm`、`native-frame.png`、
`native-frame-report.json`，完整日志为 `capture-report-native-current.txt`。
尚未接入 MAA 核心；实时像素采集成立也不意味着普通应用获得跨应用触摸注入权限。
