# 鸿蒙触控与 MaaCore 可行性调查

调查日期：2026-11-13。目标设备 VOL-AL00 / HarmonyOS 7.0.0.111 / API 26，
游戏运行于卓易通。本文区分普通手机应用与调试 shell，不把 OpenHarmony 源码直接视为
该商业系统镜像的完整实现，也不把 PC/2in1 的权限开放范围套用于 Phone。

## 结论与停止线

最小验证已通过：电脑经 HDC 激活预编译 Hypium 1.1.0 服务后，原生 HAP 可以
通过 localhost:8012 完成跨应用触控。USB 拔掉后，应用内点击、连续滑块拖动，
以及后台采集期间对卓易通明日方舟音量滑块的拖动与恢复均实测成功。
因此当前设备存在可行的调试激活路线，可以继续评估 MaaCore 的鸿蒙控制后端。

这不等于普通 HAP 自行获得触控权限：通过应用内 HDC 客户端与用户无线调试授权
启动第三方预编译 agent，重启后本机首次激活已实测通过；辅助库最初仍由电脑下发。服务在此次观察中已存活约 15 分钟，但长期寿命、重启/关闭调试后的行为、
签名及分发条件、独立触点序列与异常释放仍待验证。若要求无需调试激活即可运行，
目前证据不足以支持该承诺。还没有编译或接入鸿蒙 MaaCore，也未验证自动战斗。

## MaaCore 实际怎样控制设备

检查了 MaaAssistantArknights 的 dev-v2 提交
`201cbb73e7505884f61027b06294ecf049c670c7`，以及 MaaFramework 的 main 提交
`00e5cd754f0cf647994d2e1577e6feab2f06f281`。
源码快照位于仓库外 `~/Maindome/harmony/research`，版本清单为该目录的 `sources.json`。

现有 Meow 控制链路为：

```text
任务与图像识别（MaaCore）
  → Controller / MaaFwAndroidNativeController
  → MaaAndroidNativeControlUnit
  → libbridge：GetLockedPixels / UnlockPixels / DispatchInputMessage
  → JNI DriverClass / InputControlUtils
  → Android InputManager.injectInputEvent
  → 在 Shizuku 或 Root 启动的提权进程中执行
```

MaaCore 的点击实际拆为 touch_down / touch_up，滑动拆为 touch_down / 多次 touch_move /
touch_up，并有时间控制、暂停操作和按键等配合。核心没有提供跨系统获取权限的机制。
因此仅实现一个“点击坐标”的系统命令，还不足以验证全部任务，尤其是持续拖拽和战斗操作。

截图方面，AndroidNative 控制单元将 bridge 返回的 BGR 三通道数据包装为 cv::Mat；
当前 Native 探测产生的是 RGBA，接入时还需转换颜色布局，并处理尺寸和坐标。

MaaCore 现有 AndroidNative 控制器在 `__ANDROID__` 条件下编译。
不能把 Android `.so` 原样打进鸿蒙，也不能把 MaaFramework 的通用 Custom Controller
API 误认为 MaaCore 已提供同样的 C API。后续需增加鸿蒙控制器或调整控制单元接入和构建条件。

源码依据：

- [MaaCore Controller 工厂](https://github.com/MaaAssistantArknights/MaaAssistantArknights/blob/201cbb73e7505884f61027b06294ecf049c670c7/src/MaaCore/Controller/Controller.cpp)
- [MaaCore AndroidNative 控制器](https://github.com/MaaAssistantArknights/MaaAssistantArknights/blob/201cbb73e7505884f61027b06294ecf049c670c7/src/MaaCore/Controller/MaaFwAndroidNativeController.cpp)
- [控制单元 bridge 函数加载](https://github.com/MaaXYZ/MaaFramework/blob/00e5cd754f0cf647994d2e1577e6feab2f06f281/source/MaaAndroidNativeControlUnit/General/AndroidExternalLib.cpp)
- [MaaFramework Custom Controller 回调接口](https://github.com/MaaXYZ/MaaFramework/blob/00e5cd754f0cf647994d2e1577e6feab2f06f281/include/MaaFramework/Instance/MaaCustomController.h)
- 本仓库 `app/src/main/native/bridge_input.cpp`、`maa/InputControlUtils.java`、
  `third/wrappers/InputManager.java`。

## 鸿蒙输入权限边界

| 路径 | 证据 | 当前判断 |
| --- | --- | --- |
| 普通 HAP 调用公开 Native 输入接口 | RequestInjection 仅 PC/2in1，InjectTouchEvent 在其他设备无效果 | 不作为手机方案 |
| CONTROL_DEVICE 权限 | API 26 对普通应用开放，但受限权限文档的支持设备为 PC/2in1，场景为远程登录器被控端 | 不能靠申请这个权限解决手机触控 |
| 无障碍 injectGestureSync | 官方明确 API 12 起废弃，系统不再开放相关能力 | 不能根据 SDK 残留声明判断可用 |
| hdc shell uinput / uitest | 本机已实际控制卓易通游戏点击和滑动 | 调试身份可行，不能推导为普通应用可行 |
| 上传裸 ELF 辅助程序 | 已在普通临时目录、debugserver 目录测试，chmod 后仍执行被拒绝 | 此部署方式不成立，不代表所有调试服务路径均不成立 |
| 系统应用/厂商合作授权 | 需要不同于普通开发者签名的系统权限条件 | 不能作为一般用户安装方案的前提 |

官方资料：

- [Native 输入接口及设备限制](https://developer.huawei.com/consumer/cn/doc/doccenter-references/api/capi-oh-input-manager-h/)
- [CONTROL_DEVICE 受限权限场景与设备类型](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/restricted-permissions/)
- [无障碍手势注入废弃说明](https://developer.huawei.com/consumer/cn/doc/harmonyos-references/js-apis-inner-application-accessibilityextensioncontext/)
- [hdc 的客户端、服务器与设备守护进程](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/hdc/)

OpenHarmony 输入子系统 `PermissionHelper::CheckInjectPermission` 明确区分 TOKEN_SHELL 与
普通调用者，后者需验证注入权限。这解释了为何 shell 命令成功不能证明普通 HAP 可用，
但该开源分支代码不等于量产鸿蒙镜像的所有实现细节。

[OpenHarmony 权限检查源码](https://github.com/openharmony/multimodalinput_input/blob/master/service/permission_helper/src/permission_helper.cpp)

## 类似开源项目到底提供什么

| 项目 | 已读源码/说明 | 能证明什么，不能证明什么 |
| --- | --- | --- |
| HMNextAuto | `hdc.py` 用 subprocess 调用 hdc；`_client.py` 下发 agent.so，启动 uitest start-daemon singleness，通过 fport/socket 调用 | 可参考调试控制服务协议，不是普通 HAP 自行获得权限 |
| hmdriver2 | HMNextAuto 的上游，同样依赖 hdc/UITest | 可作为协议与控制实现参考，不能视作免激活手机 SDK |
| MaaFramework HarmonyOS 提案 #1539 | 调查时仍 open，作者报告 demo 已跑通，讨论命名 HarmonyOSController；所读 main 树没有现成 HarmonyOS 控制单元 | 有上游合作线索，但不是已经发布的手机独立方案 |
| muka_rust_hdc | 主机侧 HDC 的 Rust 重实现，包含 TCP/USB 传输与 RSA 认证 | 可参考认证/传输；项目说明未证明普通鸿蒙 HAP 本机连接自己的守护进程 |
| hdc-js | README 明确只验证自环模拟服务器，未验证官方 hdc 互通，AUTH_TOKEN/RSA 未实现 | 不能作为“无电脑手机触控已经解决”的证据 |

源码与项目入口：

- [HMNextAuto `_client.py`](https://github.com/ziguiway/hmnextauto/blob/18f2cd59c4c8ea561c49f2c9f7e99f4cd58cc7e2/hmnextauto/_client.py)
- [HMNextAuto `hdc.py`](https://github.com/ziguiway/hmnextauto/blob/18f2cd59c4c8ea561c49f2c9f7e99f4cd58cc7e2/hmnextauto/hdc.py)
- [hmdriver2](https://github.com/codematrixer/hmdriver2)
- [MaaFramework #1539](https://github.com/MaaXYZ/MaaFramework/issues/1539)
- [muka_rust_hdc](https://github.com/Attect/muka_rust_hdc)
- [hdc-js](https://github.com/w1663/hdc-js)

另一个值得验证的入口来自 OpenHarmony UITest 自身源码：`ExecuteExtension` 从
`/data/local/tmp/agent.so` 或 `--extension-name` 指定文件加载扩展，通过 UiTestPort
交给扩展测试能力。它由已存在的系统测试进程加载共享库，区别于之前执行裸 ELF 的实验。
但共享库签名、系统版本、加载策略与 IPC 权限都需要真机实测；后续真机实验结果见下节。

[UITest 扩展加载源码](https://github.com/openharmony/testfwk_arkxtest/blob/master/uitest/addon/extension_executor.cpp)

## 下一步仅做触控门槛验证

1. 用 hdc 激活一个受控、限时的测试服务；确认在这台 API 26 手机上可启动。
2. 原生 HAP 从本机 IPC 连接该服务，点击游戏安全页面并持续拖动。
3. 断开电脑后重复点击和拖动，验证权限、后台存活与连接行为。
4. 验证按下、移动、抬起属于同一触点，停止/异常时不会遗留按下状态；再测长按及必要按键。
5. 记录重启和关闭调试后的失效条件。只有这一步成功，才恢复 MaaCore 移植投入。

若上述服务在手机上不可用，而目标坚持完全独立于电脑，现阶段应停止完整移植；
可以保留已经打通的取帧代码，但不能继续把它包装成“完整鸿蒙版可实现”。
若接受电脑常驻，则 hdc 控制路线已有实机证据，可作为独立的电脑协助模式评估。


## 2026-11-13 最小验证进展（API 26 真机）

- 自编译最小 UITest 扩展能够构建，但 XPM 阻止映射，日志显示
  `Xpm check failed`、`Dlopen ... Permission denied`；OnInit 未执行。
- HMNextAuto 固定提交附带的 Hypium 1.2.2 agent 能加载；其默认接口为域套接字，
  不能直接套用该项目 TCP 8012 客户端。尚未测试 HAP 连接该域套接字的权限。
- 改用该项目默认的 Hypium 1.1.0 agent，`uitest start-daemon singleness
  --extension-name maa-hypium-tcp.so` 能启动。通过 HDC fport 到 TCP 8012，
  `Driver.create` 实际返回 `{"result":"Driver#0"}`。这一步只证明 PC 能与服务通信。
- 验证 HAP 已增加 INTERNET 普通权限与短连接本机客户端；“验证本机服务点击”
  只创建驱动并点击本页“交互测试”按钮中心。构建、签名验证、安装成功。
  用户解锁后实测并反馈“计数自动增加”：确认 HAP 经本机 TCP 调用服务能够触发本页点击。
  该结果发生于 USB 连接状态，尚不代表拔线可用或无需电脑激活。
- 第三方 agent 的超时与退出机制尚未验证，shell kill 对测试进程返回 Permission denied；
  当前不能声称实现了受控限时服务。对重启、拔线及异常收尾的验证仍未完成。

预编译 agent 仅从 research 中读取，未加入项目或最终应用；其来源为上文固定提交的
`hmnextauto/assets/uitest_agent_v1.1.0.so`。SHA-256：
`950df1c67e0e611808c05a6f85589e93488278a5e4e20b6573665b247fe2da27`。

后续拖动测试版已构建、签名验证通过，待拔线点击验证结束后安装；新增本页 Slider
拖动及事件日志，尚未运行，不能据此认定持续触点或卓易通游戏拖动已验证。


### 本机点击与滑块拖动结果

- 用户实测：USB 拔掉后等待并重复调用“验证本机服务点击”，计数继续增加。
  因而这次已激活的服务不需要持续 USB 连接；尚未测试重启或关闭开发者模式后能否使用。
- 滑块测试版构建、签名验证、安装及启动均成功。HDC 只点击“验证本机服务拖动”
  按钮，后续由 HAP 建立 localhost:8012 连接，创建 Driver 并发送 Driver.swipe。
- 应用事件日志显示滑块连续变化，结束于 value=82、mode=2（SliderChangeMode.End）；
  之后服务返回成功。表明本页控件收到连续拖动及正常结束，不能直接等同于
  已验证游戏内干员拖放或独立的 touchDown/touchMove/touchUp 接口。
- 日志保存在本机 `harmony/artifacts/probe/touch-report.txt`；下一项为拔线滑块拖动、
  手动再次拖动确认无遗留按下，以及卓易通游戏安全页面的手势验证。


用户随后确认拔线滑块拖动与再次手动拖动均可正常完成。至此已验证：一次 HDC 激活后，
HAP 可通过本机服务完成点击和完整滑块拖动，USB 不必持续连接。
尚未验证游戏窗口、应用后台执行、服务重启、设备重启和禁用调试后的行为；
不能推导为无需电脑激活的正式独立应用。


### 游戏内延时测试首次失败与修正

读取游戏音量页实际尺寸为 2232×1320，音乐音量 100；计划从 (2086,393) 拖到
(1837,393)，再反向恢复。首次仅使用 ArkTS 15 秒定时器：08:46:01 计划，
08:46:04 退后台，08:46:46 回前台时才执行并因竖屏尺寸不符取消，未发出手势。
这支持普通后台定时器被冻结/延迟的判断，不能认定为游戏拒绝输入。

修正版复用 Native 实时采集与屏幕录制长时任务：只有后台任务取得 ID 后才启动
10 秒计时，随后前进、等待 2 秒、反向恢复。界面及日志保留 Error.message。
修正版构建、签名验证和安装成功；启动受锁屏阻止，等待用户授权与真机验证。


### 游戏内拖动成功（后台采集长时任务）

用户确认音乐音量出现变化；对应应用日志：

- 08:49:38.046：长时任务 501 启动并安排 10 秒延时测试。
- 08:49:39.608：验证应用退后台。
- 08:49:48.051：开始发送游戏拖动；08:49:49.270 服务接受。
- 08:49:52.472：恢复手势获服务接受。
- 08:49:56.401：用户切回验证应用。
- 08:49:57.924：采集结束，400 帧、99 次像素复制，error=0，stopResult=0，releaseResult=0。
- 08:49:57.933：后台任务停止。

证明 HAP 在采集长时任务支持下，可以在后台通过本机 Hypium 服务拖动卓易通游戏的
音量滑块。恢复指令成功返回，但该次结束截图为验证应用，最终游戏音量未单独取图核对。
下一项补测拔掉 USB 后相同的游戏流程，并由用户核对最终回到 100。
取证日志：`harmony/artifacts/probe/game-touch-report.txt`。

目前仍不代表可直接发布：服务的激活/寿命/退出行为、预编译 agent 的签名与分发条件、
重启和调试关闭后的恢复，以及干员部署需要的精确触点序列，均待验证。


### 拔线游戏验证通过：最小通道闭环

用户确认 USB 拔掉后相同游戏音量测试仍可用。回接后取得的日志显示：
08:50:58.649 后台任务 502 就绪，08:50:59.742 应用退后台，08:51:08.651
开始手势，08:51:09.835 前进指令成功，08:51:13.020 恢复指令成功；
08:51:18.689 采集结束，400 帧、99 次复制、error=0、停止与释放均为 0，随后停止后台任务。
该次采集结束前没有回前台日志，与用户报告的拔线后台操作相符。日志本身不记录 USB 状态，
拔线事实来自用户实测反馈。

最小验证门槛现已通过。下一阶段按顺序评估：

1. 固定激活步骤，提供服务状态检测与断线错误，验证空闲寿命及重启后的重新激活。
2. 检查按下/移动/抬起、长按、取消与异常释放，再用无资源消耗的游戏场景验证部署手势。
3. 为鸿蒙控制后端封装取帧（RGBA 转 BGR）、点击与手势，保留横竖屏坐标校验。
4. 再处理 MaaCore 依赖交叉编译和最小识别闭环；本次不宣称完整 MAA 已可运行。


### 本机 HDC 认证仍在验证

本机 HAP 到无线 HDC 端口 39499 的握手已成功。认证探针首次因空字符串编码结果
为空而在组包时异常，已修复。之后出现系统 MAA-HDC-Probe 信任弹窗，
但响应中的 authType=4 同时包含 DAEMON_UNAUTH，不能视作有效认证成功。
先前仅凭 authType=4 判断成功的中间结论已撤回；id 仍未成功返回。

已修正每次点击重新生成 RSA 身份的问题：密钥保存于应用私有目录，后续重试复用；
收到 DAEMON_UNAUTH 保持连接等待授权后续响应。构建、签名验证、安装通过，
待用户在系统弹窗确认后继续实测。现阶段不能宣称已实现免电脑激活。


### 本机 HDC 认证及 shell 命令实测通过

2026-11-13 09:29:33（设备 UTC 日志）：原生 HAP 使用自身私有目录保存的固定
RSA 身份连接 127.0.0.1:39499，完成公钥与 RSA-PSS/SHA512 认证，执行只读 `id`
返回 `uid=2000(shell) gid=2000(shell)`，SELinux context 为 `u:r:sh:s0`。

修复点：空字符串字段编码；固定密钥持久化；声明 authtype=1 以协商 RSA-PSS；
区分认证通道 0 的关闭与命令通道 1 的关闭。此前“命令通道立即关闭”判断应更正：
读到的是认证通道正常关闭，命令输出随后到达。

证明手机原生应用能自行连接本机无线 HDC 并执行 shell 命令。当前测试过程仍保留 USB，
尚未测试拔线后由该客户端启动全新 UITest 服务，也没有完成辅助库下发和服务激活集成。
握手版本暂固定为本机实测版本及消息 hash，后续需要做版本兼容。


### 自激活重启验证出现误判，尚未通过

用户反馈通过后复查日志发现：重启脚本依赖手机不存在的 tr，导致没有结束旧服务；
服务 PID 仍为 47815，启动输出还匹配到了 shell 命令自身。该次点击确实成功，
但不能证明启动了全新服务。因此不把这次反馈算作免电脑激活完整闭环。

修正版通过 ps + sed 精确匹配服务命令，结束失败立即报错，并要求旧进程退出、新 PID
与所有旧 PID 不同，才输出 MAA_RESTART_OK；已构建签名安装，等待拔线实测。
辅助库仍是之前电脑下发的文件，手机端首次部署/重启后启动也尚待验证。


### 旧服务不可结束，切换为重启后首次启动验证

09:38:00 本机 HDC 认证成功后执行重启脚本，kill 47815 返回 Permission denied，
MAA_RESTART_FAILED_KILL。故失败发生在清理旧进程阶段，新进程尚未启动。
不能据此断言本机 HDC 无法启动 UITest；同样不能把旧服务可用当作新激活成功。

新增“启动新触控服务（无旧服务时）”：先确认无指定旧进程，再检查辅助库存在，
执行系统启动命令并确认出现新进程。若旧服务存在则明确拒绝并返回其 PID。
需要用户重启手机、打开无线调试、使用最新端口，拔掉 USB 后进行首次启动测试。
此阶段只验证设备已预置辅助库的重启激活；首次安装无电脑下发文件尚未实现。


### 重启后本机首次激活实测通过

2026-11-13 用户按要求重启、开启无线调试并拔线测试后反馈成功。回接 USB 取证：
设备 uptime 约 1 分钟，原服务 47815 已不存在；09:41:01 本机固定 RSA 身份完成
HDC 认证，09:41:03 返回 `MAA_START_OK new=14939`，09:41:10 本页收到
`touch-counter=1`，Driver.click 返回成功。当前实际服务 PID 同为 14939。

因此“设备已预置辅助库 → 重启 → 用户打开无线调试并填写当前端口 → 手机自行认证
启动新 UITest 服务 → 本机点击”的闭环通过。拔线状态由用户操作及反馈确认，日志
本身不记录 USB 状态。自激活不再阻塞 MaaCore 最小移植。

仍未覆盖：首次安装时手机自行下发辅助库、端口自动发现、不同系统版本兼容、长时间
稳定性及游戏部署手势。此前游戏后台拖动验证在重启前完成，此次只补测新服务点击。
产品就绪与自动战斗支持不可据此直接宣称完成。
