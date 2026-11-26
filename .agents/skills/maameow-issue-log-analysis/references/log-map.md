# Log Map & High-Value Signals

zip 内路径相对 App 的 `debug/` 目录。`Core Dir = LOCAL_TMP` 时，下文标「core 侧」的文件全部位于 `remote/` 前缀下（如 `remote/asst.log`、`remote/logcat/core/...`）；若导出时提权服务未连接，整个 `remote/` 会缺失。

## 导出筛选与预算（`LogExportCollector` / `LogConfig`）

- `gui/`、`schedule/`、`error_logs/`、`crash_logs/`、`logcat/`：仅导出近 **7 天**修改过的文件（`EXPORT_ROLLING_LOG_DAYS`）。
- 截图（`.png` / `.jpg` / `.jpeg`）：仅导出近 **14 天**（`EXPORT_SCREENSHOT_DAYS`），且排在最后按 **24 MB** 总包体预算（`EXPORT_MAX_ZIP_BYTES`）各目录轮流出最新一张；超预算或不可读的记入 `export_skipped.txt`。
- `asst.log`、`asst.bak.log` 及根目录诊断文件全带，不受 7 天窗口裁剪。

---

## 各日志文件详解

### `device_info.txt` & `app_settings.txt`（导出时快照）

每次导出由 `LogExportService` 生成。**注意它是导出时刻的快照，不保证等于事发时的版本或设置。**

- **`device_info.txt` 字段**：
  - `Version`（`name (code)`）、`Build Type`、`Telemetry ID`（= Sentry `user.id`）
  - `Device`、`Android`（含 API）、`Security`（安全补丁）、`ABI`
  - `Core`（MaaCore 版本）、`Resource`（磁盘资源戳）、`Client`、`Core Dir`（`APP_DIR` / `LOCAL_TMP` + 路径）、`Game`（包名与版本，未安装写 `not installed`）
  - `Run Mode`：`<FOREGROUND|BACKGROUND> / <后台分辨率> / forceFullscreen=<bool>`
  - `Startup`（Shizuku / Root）、`Shizuku`：`available, granted, API n, uid n (root|adb)`（或 `unavailable`）
  - `Screen`、`RAM`、`Storage`、`Battery Opt`（`NOT ignored` = 未豁免电池优化）、`Schedule`（`alarmClock=<bool>, exactAlarm=<bool>`）、`SELinux`
- **`app_settings.txt`**：全部 App 设置 `key = value`；`mirrorChyanCdk`、`wakeCredential`、`yituliuOpenApiToken`、`penguinId`、`customBackgroundToken` 脱敏为 `<set>` / `<empty>`；`pendingChangelogContent` / `currentChangelogContent` / `liveUpdateCustomTrackerPath` 记在尾部 `omitted`。重点关注 `debugMode`、`virtualDisplayBackend`（`LEGACY`/`VDM`）、`closeAppOnTaskEnd`、`useHardwareScreenOff`、`wakeUnlockType`、`updateChannel`。

### `gui/meow_log_*.log`（任务会话日志，App 侧）

- 归属：`MaaSessionLogger`，文件名 `meow_log_YYYYMMDD_HHmmss_N.log`（本地时间，N 为任务数），超 30 天启动时清理。
- 格式：JSON lines，`time` / `startTime` / `endTime` 为 epoch 毫秒；`content` 跟随界面语言（`zh`/`en`/`ko`），搜关键字时中英都要试。
- 只落盘、UI 不显示的 `TRACE` 行：
  - `[TaskParams] <type>: <json>` — 实际传给 `AsstAppendTask` 的参数
  - `[Telemetry] run_id=<id>` — 对应 Sentry 本轮 run id
- **footer `status`**（无 footer = App 进程被杀或崩溃未正常收尾，查 `error.log` 的 `Process exit [self]` 和 `crash_logs/`）：

  | status | 含义 |
  |---|---|
  | `COMPLETED` | `AllTasksCompleted` 收尾（单条任务链出错不影响它） |
  | `STOPPED` / `STOP_FAILED` | 用户、时长上限或看门狗停止 |
  | `SERVICE_DIED` | 运行中提权进程死亡 |
  | `INIT_FAILED` / `DESTROYED` | MaaCore 回调 `InitFailed` / `Destroyed` |
  | `SERVICE_CONNECTING` / `BACKEND_UNAVAILABLE` / `BACKEND_NOT_GRANTED` / `RESOURCE_ERROR` / `PORTRAIT` / `INVALID_ASPECT_RATIO` / `REMOTE_ACCESS_UNAVAILABLE` | 前置检查拒绝启动（WARNING 级） |
  | `CREATE_INSTANCE_ERROR` / `SET_TOUCH_MODE_ERROR` / `DISPLAY_BACKEND_ERROR` / `DISPLAY_MODE_ERROR` / `VIRTUAL_DISPLAY_ERROR` / `VDM_DISPLAY_ERROR` / `MAA_CONNECT_ERROR` / `APPEND_ERROR` / `START_ERROR` / `REMOTE_CALL_STUCK` | 启动链路中途失败（ERROR 级） |

### `error_logs/error.log`（应用错误日志，App 侧）

- 归属：Timber `FileLogTree` → `ApplicationLogWriter`；2 MB 轮转（`error.log` + `.1`~`.4`）。
- 格式：`[yyyy-MM-dd HH:mm:ss.SSS (+0800)] [LEVEL] [Tag] ...`（Release 版类 Tag 会被 R8 混淆）。
- 默认只记 `WARN` 及以上；开 `debugMode` 后记全级别，并写 `=== Application Start ===` 头与回调 `onEvent: msg=...`（不含 `SubTaskStart`/`SubTaskCompleted`）。
- **`Process exit [self|game]`（API 30+，`ProcessExitRecorder` / `ProcessExitLog`）**：
  - 格式：`Process exit [self|game] <proc> pid=<n> at MM-dd HH:mm:ss.SSS reason=<NAME>(<id>) [detail=...] status=<s> importance=<NAME>(<n>) [pss=...] [rss=...] [desc="..."]`
  - 预期退出（升级/正常退出/任务自身 `forceStopPackage`）记 `INFO`，非预期记 `WARN`。
  - 强停调用方识别：`due to from pid N`（AOSP）、`due to from process:N`（MIUI）、`due to stop by N`（vivo OriginOS）——比对 `service_bind_debug.log` 的 `SERVICE_PID` 可判断是 MaaMeow 自己重启游戏还是外部强停。
  - 常见厂商查杀签名：`SIGNALED(2) status=9`（无 AMS 痕迹 `SIGKILL`，厂商守护进程杀，或虚拟屏销毁连带）、`LOW_MEMORY(3)`、`FREEZER(14)`、`AutoLockOffClean`（MIUI 锁屏清理）、`single-cleaner`（vivo 单划/后台清理）。
- 其它高价值行：`Remote setup failed: <code>`、`LoadResource failed: <path>`、`Residual <proc> found before spawn`、`launcher exited early code=N`、`binder not attached within 15000ms`、`process died unexpectedly`、`Core data prepare failed`、`下载 APK 失败`。

### `service_bind_debug.log`（绑定链路，App 侧）

- 归属：`ServiceBootLogger`，512 KB 轮转到 `.1`，格式 `MM-dd HH:mm:ss.SSS  STAGE  msg`。
- 正常链路：`SESSION`（App 启动 pid）→ `BIND` → `CONNECTING` → `<SHIZUKU|ROOT>_SPAWN_CALL` → `<..>_PROCESS_CONNECTED` → `CB_ON_CONNECTED` → `BINDER_CONNECTED` → `SERVICE_PID pid=<n>`。
- 异常/诊断标记：
  - `BIND_DENIED`（未授权）、`BIND_SKIP`、`<..>_PROCESS_FAIL`、`CB_ON_ERROR`、`CB_ON_CONNECTED_DEAD`、`CONNECT_TIMEOUT`、`GET_INSTANCE_TIMEOUT`
  - `<..>_RESIDUAL_FOUND` / `RESIDUAL_EXITED` / `RESIDUAL_KILLED`（启动前清理残留提权进程）
  - `CALL_STUCK call=<name> after <ms>ms`（远程调用卡死触发自动重启）
  - `CB_ON_DISCONNECTED` / `BINDER_DIED` / `BINDER_DIED_STALE` / `STATE_DIED` / `STATE_DISCONNECTED`
  - logcat 守护进程对应 `LOGCAT_BIND_CALL` / `LOGCAT_BIND_ERROR` / `<..>_LOGCAT_*`

### `service_boot_debug.log` & `{root,shizuku}_launch_debug.log`（core 侧）

- **`service_boot_debug.log`**（`RemoteBootTrace`，超 256 KB 重建，`<epochMs>  STAGE  msg`）：
  `CTOR_START` → `CTOR_DONE` → `SETUP_BEGIN` → `SETUP_XMSF_RESTORED` → `MAA_LOAD_BEGIN` → `MAA_LOAD_OK` → `SETUP_DONE`；失败：`SETUP_USER_DIR_INACCESSIBLE`、`MAA_LOAD_FAIL`。同期以 `[BOOT] STAGE` 进 logcat。
- **`{root,shizuku}_launch_debug.log`**（`liblauncher.so` 写，每次拉起覆盖，logcat 对应 `*_logcat_launch_debug.log`）：记录 apk 路径、mount namespace、shell uid 降权、`execv app_process`、`BootstrapClient` 回投 binder 及 `[MC] WARN:` 早期告警。失败时 App 会抄全文进 `error.log`。

### `schedule/trigger_*.log`（定时 / 外部触发日志，App 侧）

- 归属：`ScheduleTriggerLogger`，JSON lines，最多保留 100 个。
- `header` 含 `scheduledTimeMs`、`actualTimeMs`（差值 = 闹钟延迟）、`runMode`；`log` 行按步骤记录；`footer` 含 `result`（`ExecutionResult`）：
  - `STARTED`：已交由任务引擎启动（后续看同时间 `meow_log`）
  - `MISSED`：重排时发现漏触发补记，文案区分「设备重启期间」与「系统未按时唤起」
  - `SKIPPED_BLACKLIST`：主屏命中应用黑名单，主动跳过
  - `SKIPPED_BUSY`：已有任务或触发在跑
  - `SKIPPED_LOCKED`：唤醒解锁失败且锁屏仍在；触发日志附带 `WakeDiagnosis`（唤醒前亮屏/锁屏状态、手势录制时间、各步耗时，不含敏感凭据）
  - `FAILED_VALIDATION` / `FAILED_UI_LAUNCH` / `FAILED_START` / `CANCELLED`

### `logcat/core/logcat_*.log` & `logcat/app/logcat_*.log`（core 侧，需开 `debugMode`）

- 归属：独立 logcat 提权进程（`LogcatCaptureServiceImpl`）按 pid 捕获；7 天 / 64 MB 清理，单次 16 MB 分两段滚动（`.1.log` 较早）；`LogcatNoiseFilter` 对重复 `avc:  denied` 去重。
- **`core/`**：`Ln` 日志 tag 恒为 `MaaMeow`，含 `VD created` / `VD effective: group=..., flags=...`、`VdmDisplayController`、`PowerController`、`StaleFrameGuard`、`DriverClass`、`GameFpsMonitor`、`WakeUnlock`、`MaaCoreService` JNA 调用返回值、native `libbridge.so` 日志与 Fatal signal。
- **`app/`**：App 进程异常与系统记录（Release 版 Timber 仅 `WARN+` 进 logcat）。

### `asst.log` / `asst.bak.log` & `interface/*.png` & `crash.log`（core 侧）

- **`asst.log`**（64 MB 轮转到 `asst.bak.log`）：`[yyyy-MM-dd HH:mm:ss.SSS][LVL][Px<pid>][Tx<tid>]`，分析 MaaCore 内部状态机、OCR/模板匹配、截屏耗时、`start_game` 耗时与资源加载失败的最权威依据。
- **`interface/*.png`**：任务链失败时的缓存帧截图，直接看图判断卡在哪个界面或是否纯黑（例如 1280×720 仅数 KB 通常是纯黑帧）。
- **`crash.log`**：MaaCore native 崩溃记录。
- **`crash_logs/crash_*.txt`**（App 侧，最多 10 个）：Java 未捕获异常，不含 native crash 与 ANR。
- **`properties.txt`**：`getprop` 全量输出，看 ROM（如 `ro.vivo.os.build.display.id`、`ro.miui.*`）、SurfaceFlinger/display 属性。

---

## 高价值搜索信号速查

- **`meow_log`**：
  - `MAA服务异常终止` / `MAA service terminated unexpectedly`
  - `无法连接远程服务` / `远程服务无响应，已自动重启服务`（`REMOTE_CALL_STUCK`）
  - `VDM 独立虚拟屏创建失败`（`VDM_DISPLAY_ERROR`） / `虚拟屏模式已更改，但上一轮的虚拟屏还在`（`DISPLAY_BACKEND_ERROR`） / `启动虚拟显示失败`（`VIRTUAL_DISPLAY_ERROR`）
  - `当前系统缺少后台虚拟屏所需权限，已改用兼容方式创建`（Legacy 降级 `vd.tier > 0`，落回 `group 0`）
  - `启动 MaaCore 超时或失败`（`AsyncConnect` 2s 超时） / `任务加入队列失败` / `没有任务成功加入队列`（`APPEND_ERROR`）
  - `游戏进程未启动或被异常关闭` + 紧跟的 `退出原因（时间）：… [reason, …]`
  - `游戏窗口已离开虚拟显示器且自动拉回失败` / `游戏画面帧率`（`<30` 或 `<50` FPS）
  - `系统拒绝了前台服务权限(FOREGROUND_SERVICE_SPECIAL_USE)` / `已达到运行时长上限`（非故障）
- **`asst.log`**：
  - `[ERR]` / `[WRN]`、`Templ file exists in multiple paths`、`get stage info failed`、`operator name recognition failed`、`Unknown facility`、`Login failed, entering game-restart loop`
  - `start_game | leave, 5xxx ms`（≥5000 ms 强烈指向 `DriverClass.awaitFirstFrame()` 5s 等帧超时，虚拟屏休眠/无帧输出）
- **`logcat/core`**：
  - `VD effective: group=0` / `VD requested OWN_DISPLAY_GROUP but landed in group 0`
  - `PowerController: display <id> asleep, wake ok|failed` / `RemoteService: display <id> asleep, recreating`
  - `DriverClass: awaitFirstFrame timed out after 5000ms` / `StaleFrameGuard: nothing draws display ... frame buffer blanked`
  - `DeadObjectException` / `Callback DROPPED` / `SIGSEGV` / `SIGABRT` / `setup failed - userDir inaccessible` / `Failed to load MaaCore`
