# Common Failure Patterns & Diagnostic Paths

排查时按症状定位到对应模式，逐项核对证据链，避免漏掉级联因果。

## 1. 服务异常终止（`SERVICE_DIED`）与远程调用卡死（`REMOTE_CALL_STUCK`）

- **典型原因**：
  - 提权进程被系统后台管控/内存压力强杀（任务结束后 `TaskExecutionService` 退出 FGS，App 降为 `CACHED(400)`，部分 ROM 息屏后杀 Shizuku 派生进程）。
  - MaaCore native crash（`SIGSEGV`/`SIGABRT`）或 `libbridge.so` 崩溃。
  - Shizuku 自身掉线（adb 无线调试断开、重启未激活）。
  - 远端调用超时卡死（`startVirtualDisplay`/`launchGame` 30s、其它显示操作 15s）触发 `RemoteServiceManager.callOrRestart()` 主动重启提权进程。
- **级联现象（勿误判！）**：提权进程一死（或用户清后台强杀 MaaMeow 导致生命周期 binder 断链），其持有的虚拟屏被系统销毁；因 `VirtualDisplayManager` 带 `VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL`，游戏进程往往在 **0.5~2 秒内收到 `SIGNALED(2) status=9`**——这是虚拟屏销毁连带，不是游戏先崩。
- **诊断路径**：
  1. `service_bind_debug.log` 找 `STATE_DIED` / `BINDER_DIED` / `CALL_STUCK` 精确时间点。
  2. `crash.log` + `logcat/core` 排查 native crash；无 crash 则查 Sentry `ServiceDied` 是否有 `ServiceDeathForensics` 补取的系统 logcat，或查 `RemoteCallStuck` 附带的 `ThreadDump`。
  3. 对照 `error.log` 确认同一时刻 App 自身是否被 `single-cleaner` / 杀后台强停。

## 2. 提权进程拉不起来 / 残留进程竞争

- **典型原因**：
  - `launcher exited early code=126/127`：shell 域执行 `/data/app/.../liblauncher.so` 被 ROM/SELinux 拦截。
  - `binder not attached within 15000ms`：进程起了但 `BootstrapClient` 未成功回投 binder。
  - `service_boot_debug.log` 停在 `MAA_LOAD_BEGIN` 或报 `MAA_LOAD_FAIL`：JNA/dlopen 加载 `libMaaCore.so` 失败。
  - **残留/分身进程竞争**：`service_bind_debug.log` 出现 `*_RESIDUAL_FOUND`、`error.log` 出现 `Residual ... found before spawn`；应用分身/多开时不同用户下进程会互相争抢。
- **诊断路径**：`service_bind_debug.log` 定卡点 → `error.log` launcher 全文 / `*_launch_debug.log` → `service_boot_debug.log` 看进程内停在哪一步。

## 3. 后端未授权

- `meow_log` footer = `BACKEND_NOT_GRANTED`，`service_bind_debug.log` 有 `BIND_DENIED`。
- 旧版本曾把未授权报成「资源加载失败」，排查旧版日志需先核对 `device_info.txt` 的 `Shizuku` 授权状态。

## 4. 资源加载失败 / 数据目录不可访问

- **目录不可访问**：`error.log` 报 `Remote setup failed: USER_DIR_INACCESSIBLE`，`service_boot_debug.log` 报 `SETUP_USER_DIR_INACCESSIBLE <原因>`。常见于部分 Android 11 ROM（shell 读不了 `Android/data/<pkg>`）或手机分身/多用户（提权进程在 user 0，够不到 `/storage/emulated/<非0>`）。前者切独立目录（`LOCAL_TMP`）可解，分身/多用户不支持。
- **资源损坏/冲突**：App 侧只写 `LoadResource failed: <path>`，真实原因在 `asst.log` `[ERR]`（如 `Templ file exists in multiple paths`、热更中断、资源与 MaaCore 版本不匹配）。若 `asst.log` 无加载记录，说明卡在 `Remote setup` 或 `Core data prepare failed`（`LOCAL_TMP` 推文件失败）。

## 5. 后台虚拟屏黑屏 / 唤醒无效 / 降级 / 漂移

后台虚拟屏由 `BackgroundDisplay` 分流到 **Legacy**（`VirtualDisplayManager`）与 **VDM**（`VdmDisplayController`，Android 14+，强制独立显示组且失败不回退 Legacy）。

- **息屏/闲置后复用虚拟屏黑屏，清后台才恢复（#367）**：
  - 触发条件：`closeAppOnTaskEnd = false`（任务结束后不关虚拟屏），任务结束撤掉 `userActivityKeepAlive`，手机黑屏/闲置后独立电源组（`group > 0`）的虚拟屏进入 `ASLEEP`/`STATE_OFF`。亮屏只唤醒 `group 0`，再次复用该虚拟屏跑 `StartUp` 时 SurfaceFlinger 不出帧。
  - 特征指纹（无 `logcat/core` 也能确诊）：`asst.log` 同一 `pid`/`display_id` 复用时 `start_game | leave, 55xx~57xx ms`（`forceStop` 已成功 + `DriverClass.awaitFirstFrame()` 5000ms 超时），随后 OCR 全空或 `interface/*.png` 为纯黑帧；用户清后台重启 App 重建新虚拟屏后 `start_game` 恢复 `1xx~2xx ms`。
  - 版本分界：`v0.27.0`（commit `b9bee02a`）起新增 `PowerController.ensureDisplayAwake`（API 36 调 `wakeUpWithDisplayId` 原位唤醒，API 34–35 重建虚拟屏）；**`v0.26.0` 与 `v0.27.0-beta.1` 均不含此修复**。若在 `v0.27.0+` / `v0.28.1+` 仍复现，必须开调试模式取 `logcat/core` 看 `VD effective: group=...` 与 `PowerController: display X asleep, wake ok|failed`（以及 `setMonitorSurface` 丢弃唤醒返回值、`Display.state` 与交互态竞态等）。
- **Legacy 降级 / 被 ROM 静默压回 `group 0`**：
  - `vd.tier > 0`（`meow_log` 有兼容模式警告）或 `logcat/core` 有 `VD requested OWN_DISPLAY_GROUP but landed in group 0`：虚拟屏随主屏熄屏/锁屏暂停，注入可能抢主屏焦点。可建议 Android 14+ 设备在设置里切换 **VDM** 后端。
- **VDM 建屏失败（`VDM_DISPLAY_ERROR`）**：需 shell uid=2000 且系统允许 `VirtualDeviceManager`；若 ROM 限制，只能切回 Legacy。切换后端时若旧虚拟屏仍留着游戏会报 `DISPLAY_BACKEND_ERROR`，需先「关闭游戏」。
- **残影黑帧（`StaleFrameGuard`）**：虚拟屏上游戏退出/无人绘制时主动刷黑帧防误识别，勿把游戏已被杀导致的黑帧误判成截屏故障——同时查 `error.log` 的 `Process exit [game]`。
- **游戏窗口漂移主屏**：`meow_log` 报 `游戏窗口已离开虚拟显示器且自动拉回失败`（如 One UI），由 `ActivityUtils.ensureAppOnDisplay` 拦截，建议开「后台模式强制全屏」或切前台。
- **硬件熄屏（`useHardwareScreenOff`）**：通过 `PowerController.setDisplayPower` 调 `SurfaceControl`/`DisplayControl` 关面板，部分机型/荣耀需走内置 display 分支，看 `logcat/core` 的 `PowerController`。

## 6. 游戏进程中途被杀（`AppWatchdog`）

- 特征：`meow_log` 报 `游戏进程未启动或被异常关闭(<pkg>)`，新版（`5106474d`+）紧跟 `退出原因（时间）：… [reason, …]`。
- 诊断路径：查 `error.log` 的 `Process exit [game]`：
  - `LOW_MEMORY(3)` / `SIGNALED(2) status=9` / `FREEZER(14)` / `AutoLockOffClean` / `single-cleaner` → ROM 省电/内存/锁屏清理查杀（先确认不是 `service_bind_debug.log` 里提权进程先死引发的连带 `SIGKILL`）。
  - `CRASH` / `CRASH_NATIVE` / `ANR` / `SIGNALED`（非 9）→ 游戏自身崩溃。
  - `USER_REQUESTED` 且 `desc` 含 `from pid N` / `from process:N` / `stop by N` → 核对 `N` 是否等于当时 `SERVICE_PID`（等于则是 MaaMeow 自己强停重启游戏）。

## 7. MaaCore 连接 / 初始化 / 任务执行失败

- **连接与初始化**：看 `meow_log` footer status（`CREATE_INSTANCE_ERROR`、`SET_TOUCH_MODE_ERROR`、`MAA_CONNECT_ERROR`、`APPEND_ERROR`、`START_ERROR`、`PORTRAIT`、`INVALID_ASPECT_RATIO`）。`AsyncConnect` 2 秒超时查 `logcat/core` `libbridge.so` 与 `asst.log` `MaaFwAndroidNativeController::connect`。
- **识别与业务失败**：先看 `interface/*.png` 判断卡在哪个界面 → `asst.log` 看对应 `ProcessTask` / OCR / 模板匹配分数 → `meow_log` 核对 `[TaskParams]` → 对比 `device_info.txt` 的 `Game` 与 `Resource` 版本、帧率警告（`<30`/`<50` FPS）。

## 8. 定时任务漏触发 / 跳过 / 解锁失败

- **根本没有对应时间点的 `schedule/trigger_*.log`**：闹钟未到达或被系统吞掉；检查 `device_info.txt` 的 `Battery Opt` 与 `Schedule: alarmClock=..., exactAlarm=...`。
- **`result = MISSED`**：重排闹钟时补记，文案直接区分「期间重启/关机」还是「系统未按时唤起（自启动/省电管控）」。
- **`result = SKIPPED_BLACKLIST` / `SKIPPED_BUSY` / `CANCELLED`**：均为预期策略拦截，非故障。
- **`result = SKIPPED_LOCKED`**：查看触发日志里的 `WakeDiagnosis`（解锁方式、凭据是否已填、唤醒前亮灭屏/锁屏状态、各步耗时）与 `logcat/core` 的 `WakeUnlock`（冷唤醒 PIN/手势时序、bouncer 弹出）。
- **`result = FAILED_UI_LAUNCH`**：后台拉起倒计时 Activity 被 ROM 后台弹窗限制拦截（注：静默启动模式下后台不拉主界面）。
- **`result = FAILED_START` / `STARTED` 但任务没跑起来**：转查同时间 `meow_log` 的 footer `status` 及 `ShizukuDown`（Shizuku 掉线）。

## 9. 前台服务被拒 & 日志与用户描述不一致

- **FGS 被拒**：`meow_log` 报 `系统拒绝了前台服务权限(FOREGROUND_SERVICE_SPECIAL_USE)`（`SpecialUseFgsGate`），任务降级无进度通知运行，切后台极易被系统回收；可用提示里的 `appops set` 命令恢复。
- **日志与描述不一致**：
  - 确认事发时刻的实际版本号（用 `asst.log` `Version` + Sentry `release` 交叉核验），警惕「用户刚升级完新版才导出日志，但复现发生在旧版」或「记错 beta 版本号」。
  - 若日志中该轮成功完成或未包含复现操作，明确说明「本日志未复现」，列清需要补充的复现步骤（通常需开启调试模式后再复现导出）。
