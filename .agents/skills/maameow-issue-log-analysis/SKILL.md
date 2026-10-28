---
name: maameow-issue-log-analysis
description: >
  分析 MaaMeow（Aliothmoon/MAA-Meow）的 GitHub Issue 或本地 `maa_logs_*.zip` 日志包。
  下载附件后从 device_info.txt、gui/meow_log、error_logs、crash_logs、schedule/trigger、
  service_bind/boot/launch debug log、logcat/core|app、asst.log、interface 截图交叉取证，
  对照双进程（App + Shizuku/Root 提权）与 MaaCore/bridge 代码判断根因。
  Use when analyzing MaaMeow/MAA-Meow issues, log zips, task failures, service death,
  Shizuku/Root elevation, virtual display, recognition errors, connection init failures,
  or scheduled tasks that did not run.
---

# MaaMeow Issue / Log Analysis

仓库架构与包边界以仓库根目录 `CLAUDE.md` 为准；本 skill 只保留**排障所需**的内容。分析时若与本文冲突，以当前 checkout 的 `CLAUDE.md` 与源码为准。

## Scope

- 适用于 `https://github.com/Aliothmoon/MAA-Meow` 仓库的公开 Issue。
- 也适用于用户直接提供的本地 `maa_logs_*.zip` 日志包。
- 输入可以是完整 issue URL、`#1234` 形式的 issue 编号，或本地 ZIP 文件路径。
- 如果 issue 没有 `maa_logs_*.zip` 附件，先明确说明证据不足，再基于 issue 文本、截图和代码给出初步判断。
- **默认只有本仓库 checkout**（无 MaaCore C++ 树）。内核行为优先用 `asst.log`；需要对照 C++ 实现时，按下方「MaaCore 源码获取」必要时 clone 上游。

## Background: MaaMeow Architecture

MaaMeow 是 Android 平台上的 MAA 客户端，经 Shizuku 或 Root 拉起独立的提权进程，在其中加载 MaaCore C++ 自动化内核。日志分布在两个进程里，分析时先分清一条日志出自哪边：

**App 进程**（普通 Android 应用）：
- Jetpack Compose UI、ViewModel、Domain 层
- 通过 AIDL/Binder 与提权进程通信
- 日志系统：Timber（`FileLogTree` → `error_logs/`）+ `MaaSessionLogger`（任务会话 → `meow_log_*.log`）+ `ServiceBootLogger`（绑定链路 → `service_bind_debug.log`）+ `ScheduleTriggerLogger`（定时/外部触发 → `schedule/trigger_*.log`）

**提权服务进程**（Shizuku 或 Root 后端，均由 `liblauncher.so` 起 `app_process` 进入 `RemoteServiceStarter`；没有 Shizuku `bindUserService` 路径）：
- RemoteServiceImpl — AIDL 服务入口
- MaaCoreServiceImpl / MaaCoreManager — JNA 加载 libMaaCore.so
- libbridge.so — C++ 原生桥接（截屏、输入分发）
- 虚拟显示管理
- 日志系统：`Ln`（logcat tag `MaaMeow` + stdout/stderr）+ `RemoteBootTrace`（启动阶段 → `service_boot_debug.log`）；Timber 在这个进程里静默 no-op

**IPC 回调链路**：
```
libMaaCore.so (C++) → JNA AsstApiCallback → MaaCoreServiceImpl → AIDL → MaaCoreCallback.onCallback()
→ MaaCompositionService.handleCallback（先截走 AsyncCallInfo）→ MaaCallbackDispatcher
→ ConnectionInfoHandler / TaskChainHandler / SubTaskHandler → MaaSessionLogger → UI + meow_log_*.log
```

**两个数据目录**（`device_info.txt` 的 `Core Dir` 一行写明当前模式）：
- `APP_DIR`（默认）：App 与 core 共用 `Android/data/<pkg>/files/Maa/`，core 的 `debug/` 就是 App 的 `debug/`
- `LOCAL_TMP`：core 在 `/data/local/tmp/maameow`，App 读不到；core 侧日志导出时经 binder 拉进 zip 的 `remote/`

## Workflow

### 1. 规范化输入

- `#1234` 视为 `https://github.com/Aliothmoon/MAA-Meow/issues/1234`
- 如果是本地路径（`.zip` 文件或已解压目录），跳过 issue 获取步骤，直接进入日志分析
- 如果指向其他仓库，停止并说明此 skill 不适用

### 2. 获取 Issue 内容

- 用 `gh issue view <number> --repo Aliothmoon/MAA-Meow --comments` 读取 issue 正文和评论
- 提取关键信息：MaaMeow 版本、提权后端、运行模式（前台/后台）、客户端类型（官服/B服/国际服等）、执行的任务、预期行为、实际行为、复现步骤、维护者评论
- 如果维护者已给出结论，不要直接照抄；仍要用日志和代码自行验证

### 3. 提取日志附件链接

- 关注 `maa_logs_*.zip` 附件
- 用 `gh` 命令或 API 获取 issue 中的附件 URL
- 如果同一 issue 有多个日志包，优先看最新的复现

### 4. 下载并解压日志包

- 用 `curl -L` 下载到临时目录，例如 `.cache/issue-logs/issue-<number>/`
- 解压后先 `ls -la` 列出目录结构，不要假定结构固定
- 不要把整份日志塞进回复，只摘取关键片段

### 5. 日志分析（详见 Log Map 章节）

- 先读 `device_info.txt`：版本、后端、运行模式、数据目录模式都以它为准
- 建立时间线：串联各日志文件的时间戳（注意各文件时间格式不同，见 Log Map）
- 锁定本次复现的任务实例
- 按问题类型选择重点日志源（见 How To Filter Evidence）

### 6. 回溯代码

- MaaMeow 应用代码：当前仓库
- MaaCore C++ 源码：本仓库不带，按「MaaCore 源码获取」取；版本尽量对齐 `device_info.txt` 的 `Core`，对不上时在结论里写明"按 tag X 对照"
- 任务参数构建：各 `data/model/*Config.kt` 的 `toTaskParams()`，由 `AnalyzeTaskChainUseCase` 汇总；实际下发的参数直接看 `meow_log` 里的 `[TaskParams]` 行
- 回调处理链：`MaaCallbackDispatcher` → `SubTaskHandler` / `TaskChainHandler`

## Log Map

zip 内路径相对 App 的 `debug/` 目录。`LOCAL_TMP` 模式下，下文标"core 侧"的文件都出现在 `remote/` 前缀下（如 `remote/asst.log`、`remote/logcat/core/...`）。

**导出筛选**（`LogExportCollector`，阈值在 `LogConfig`）：

- `gui/`、`schedule/`、`error_logs/`、`crash_logs/`、`logcat/` 只带近 7 天修改过的文件
- 截图（png/jpg）只带近 14 天的，并且排在最后按 24 MB 包体预算装：各目录轮流出最新的一张，装不下的记进 `export_skipped.txt`（`over export size budget`）
- `asst.log`、`asst.bak.log` 和其余文件全带，不参与裁剪
- 旧版本没有 logcat 和截图这两条限制，整目录打包，包可以上百 MB

用户说的事发时间超过窗口，对应文件不在包里是正常的。

### `device_info.txt` / `app_settings.txt`（导出时的环境快照）

- **每次导出都会生成**，由 `LogExportService` 写入
- **`device_info.txt`** 字段：
  - `Version` / `Build Type`、`Telemetry ID`（= Sentry `user.id`）、`Device`、`Android`、`ABI`
  - `Core`（MaaCore 版本）、`Resource`（磁盘资源版本戳）、`Client`、`Core Dir`（`APP_DIR` / `LOCAL_TMP` + 实际路径）、`Game`（游戏包名与版本，未安装会写 `not installed`）
  - `Run Mode`：`<FOREGROUND|BACKGROUND> / <后台分辨率> / forceFullscreen=<bool>`
  - `Startup`（Shizuku / Root）、`Shizuku`：`available, granted, API n, uid n (root|adb)`
  - `Screen`、`RAM`、`Storage`、`Battery Opt`（`NOT ignored` = 未豁免电池优化，定时任务易被杀）、`SELinux`
- **`app_settings.txt`**：全部 App 设置 `key = value`；`mirrorChyanCdk` / `wakeCredential` / `yituliuOpenApiToken` / `penguinId` / `customBackgroundToken` 只显示 `<set>` / `<empty>`
- **最适合看**：建立锚点，比 issue 文本可靠。注意它是**导出那一刻**的快照，不一定等于出事时的设置

### `gui/meow_log_*.log`（任务会话日志）

- **归属**：App 进程 → `MaaSessionLogger`
- **格式**：JSON lines（每行一个 JSON 对象），`time` 为 epoch 毫秒
  ```
  {"type":"header","startTime":1710449920000,"tasks":["StartGame","Fight"]}
  {"type":"log","time":1710449920100,"level":"INFO","content":"开始执行任务，共 2 项"}
  {"type":"log","time":1710449920150,"level":"TRACE","content":"[TaskParams] Fight: {...}"}
  {"type":"log","time":1710449921000,"level":"ERROR","content":"MAA服务异常终止"}
  {"type":"footer","endTime":1710449930000,"status":"SERVICE_DIED"}
  ```
- **`level`** 是 `LogLevel` 枚举名：`MESSAGE`、`INFO`、`SUCCESS`、`WARNING`、`ERROR`、`TRACE`；公招 `RECRUIT_STAR_1`~`6`、`RECRUIT_ROBOT`；肉鸽 `ROGUELIKE_SUCCESS` / `ROGUELIKE_COMBAT` / `ROGUELIKE_EMERGENCY` / `ROGUELIKE_BOSS` / `ROGUELIKE_ABANDON`；`RARE`
- **只写文件、UI 不显示的 TRACE 行**：
  - `[TaskParams] <type>: <json>` — 实际下发给 `AsstAppendTask` 的参数，核对配置问题的第一手证据
  - `[Telemetry] run_id=<id>` — 本轮在 Sentry 里的 run id
- **`content` 跟随界面语言**：英文用户的包里是英文文案，搜关键字时中英都要试（对照 `values/strings.xml` 与 `values-en/strings.xml` 的 `runlog_*`）
- **footer `status`**：

  | status | 含义 |
  |---|---|
  | `COMPLETED` | `AllTasksCompleted` 正常收尾（单条任务链出错不影响它） |
  | `STOPPED` / `STOP_FAILED` | 用户或运行时长上限停止 |
  | `SERVICE_DIED` | 运行中提权进程死亡 |
  | `INIT_FAILED` / `DESTROYED` | core 回调 `InitFailed` / `Destroyed` |
  | `SERVICE_CONNECTING` / `BACKEND_UNAVAILABLE` / `BACKEND_NOT_GRANTED` / `RESOURCE_ERROR` / `PORTRAIT` / `INVALID_ASPECT_RATIO` / `REMOTE_ACCESS_UNAVAILABLE` | 前置检查拒绝启动（WARNING 级，状态机回 IDLE） |
  | `CREATE_INSTANCE_ERROR` / `SET_TOUCH_MODE_ERROR` / `DISPLAY_MODE_ERROR` / `VIRTUAL_DISPLAY_ERROR` / `MAA_CONNECT_ERROR` / `START_ERROR` | 启动链路中途失败（ERROR 级） |

  没有 footer = 会话没正常收尾（App 被杀或崩溃），去 `crash_logs/` 和 `error_logs/` 找
- **文件名规则**：`meow_log_YYYYMMDD_HHmmss_N.log`（本地时间，N 为任务数量）
- 超过 30 天（`LogConfig.MAX_TASK_LOG_DAYS`）的在 App 启动时自动清理；旧版本只能在日志历史页手动清

### `error_logs/error.log`（应用错误日志）

- **归属**：App 进程 → Timber `FileLogTree` → `ApplicationLogWriter`
- **格式**：
  ```
  [2026-03-18 14:35:42.123 (+0800)] [ERROR] [MaaCompositionService] ...
  ```
- **特性**：2MB 轮转，`error.log` + `error.1.log`~`error.4.log`
- **默认只记 WARN 及以上**；用户在设置里开了调试模式（`debugMode`）才记全部级别，且此时每次启动写一段 `=== Application Start ===` 头（版本、设备、API、ABI）
- 调试模式下每条回调有一行 `onEvent: msg=...`，但 SubTaskStart / SubTaskCompleted 不记（`CallbackLogPolicy`），子任务起止只能在 `asst.log` 里找
- **最适合看**：
  - App 进程侧的异常和堆栈
  - 不在任务会话期间的错误（启动失败、权限、资源加载、导出）
  - 提权进程拉起失败时的完整 launcher 日志（`<process> launch debug log (...)`）
  - `LoadResource failed: <path>`、`Remote setup failed: <code>`

### `crash_logs/crash_*.txt`（App 崩溃）

- **归属**：App 进程 → `CrashHandler`（Java 未捕获异常）
- 每次崩溃一个文件：时间、设备、App 版本 + 完整堆栈；最多保留 10 个
- native 崩溃和 ANR 不在这里

### `schedule/trigger_*.log`（定时 / 外部触发日志）

- **归属**：App 进程 → `ScheduleTriggerLogger`；每次触发一个文件，最多 100 个
- **格式**：JSON lines
  ```
  {"type":"header","strategyId":"...","strategyName":"...","scheduledTimeMs":...,"actualTimeMs":...,"runMode":"BACKGROUND"}
  {"type":"log","time":...,"message":"..."}
  {"type":"footer","time":...,"result":"FAILED_START","message":"..."}
  ```
- **`result`**（`ExecutionResult`）：`STARTED`、`FAILED_VALIDATION`、`FAILED_START`、`FAILED_UI_LAUNCH`、`SKIPPED_BUSY`、`SKIPPED_LOCKED`、`CANCELLED`
- `log` 行按顺序记下每一步（收到触发 → 唤醒解锁 → 切配置 → 拉界面 → 倒计时 → 启动任务），文案跟随界面语言
- `actualTimeMs - scheduledTimeMs` = 闹钟晚到多久
- **最适合看**：定时任务"没跑"到底是闹钟没触发（根本没有对应文件）、触发了但被拦下，还是启动失败

### `service_bind_debug.log`（绑定链路，App 侧）

- **归属**：App 进程 → `ServiceBootLogger`；512KB 轮转到 `.1`
- **格式**：`MM-dd HH:mm:ss.SSS  STAGE  msg`
- **阶段**：`SESSION`（App 启动）→ `BIND` → `CONNECTING` → `<SHIZUKU|ROOT>_SPAWN_CALL` → `<..>_PROCESS_CONNECTED` → `CB_ON_CONNECTED` → `BINDER_CONNECTED`
- **失败 / 终态**：`BIND_DENIED`（后端未授权）、`<..>_PROCESS_FAIL`、`CB_ON_ERROR`、`CONNECT_TIMEOUT`、`GET_INSTANCE_TIMEOUT`、`BINDER_DIED`、`STATE_DIED`、`STATE_DISCONNECTED`；logcat 进程对应 `LOGCAT_BIND_CALL` / `LOGCAT_BIND_ERROR` 与 `<..>_LOGCAT_*`
- 停在 `CONNECTING` 后面没有终态行 = 服务进程没起来或没回投 binder

### `service_boot_debug.log`（提权进程启动阶段，core 侧）

- **归属**：提权进程 → `RemoteBootTrace`；超 256KB 删除重建
- **格式**：`==== service boot pid=... ====` 头 + `<epochMs>  STAGE  msg`
- **阶段**：`CTOR_START` → `CTOR_DONE` → `SETUP_BEGIN` → `SETUP_XMSF_RESTORED` → `MAA_LOAD_BEGIN` → `MAA_LOAD_OK` → `SETUP_DONE`；失败 `SETUP_USER_DIR_INACCESSIBLE`、`MAA_LOAD_FAIL`
- 同一批标记也以 `[BOOT] STAGE` 进 logcat

### `{root,shizuku}_launch_debug.log`（launcher 日志，core 侧）

- **归属**：`liblauncher.so`（`launcher.c`）以 shell/root 身份写；每次拉起前先删，只留最近一次
- logcat 进程对应 `{root,shizuku}_logcat_launch_debug.log`
- 拉起失败时 App 会把全文打进 `error.log`，并把尾 5 行拼进异常信息（`...; launcher log tail: a | b | c`）
- `LOCAL_TMP` 模式下 App 读不到这份文件，`error.log` 里会写 `launch debug log is in core dir, unreadable from app`，此时只能看 `remote/`

### `logcat/core/logcat_*.log`、`logcat/app/logcat_*.log`（logcat 捕获，core 侧）

- **只有开了调试模式（`debugMode`）才会有**；没有这两个目录不代表出错，先看 `app_settings.txt` 的 `debugMode`
- **归属**：独立的 logcat 提权进程（`LogcatCaptureServiceImpl`）跑 `logcat -T 10 --pid=<pid>`，每个 pid 一个文件，文件名是开始捕获的时间
- **保留**：提权进程每次 `setup` 时清理，删 7 天前的，再按总量 64 MB 从旧到新删（`LogcatRetention`）；单次抓取最多 16 MB，分两段滚动，`logcat_<时间>.1.log` 是较早的一段，再早的被覆盖
- **去噪**：同一条 `avc:  denied` 只留首条（`LogcatNoiseFilter`），看到一条 SELinux 拒绝不代表只发生过一次；`Callback:` 行不含 SubTaskStart(20001) / SubTaskCompleted(20002)
- **`core/`**（提权服务进程）：
  ```
  03-18 14:35:42.123  6789  6789 I MaaMeow : MaaCoreService: SetUserDir(/storage/...) = true
  03-18 14:35:42.456  6789  6790 I MaaMeow : MaaCoreService: Callback: SubTaskError, {...}
  03-18 14:35:42.460  6789  6789 I MaaMeow : [BOOT] SETUP_DONE
  ```
  - `Ln` 输出：logcat tag 恒为 `MaaMeow`，正文以类名开头（`RemoteService:` / `MaaCoreService:` / `MaaCoreManager:` / `ScreenManager:` / `WakeUnlock:` / `GameFpsMonitor:` …）。`[MC] ` 前缀只出现在 stdout/stderr 副本里，不在 logcat 行里
  - libbridge.so 的 native 日志、Fatal signal / tombstone 摘要
  - 每个 JNA 调用都有一行 `Xxx(...) = <返回值>`：`SetUserDir`、`LoadResource`、`CreateInstance`、`SetInstanceOption`、`AsyncConnect`、`AppendTask`、`Start`、`Stop`
- **`app/`**（App 进程）：Compose / Koin / ViewModel 异常、系统对本进程的记录；正式版 Timber 进 logcat 的只有 WARN 及以上，全级别的 App 日志在 `error.log`（调试模式下）
- **最适合看**：服务进程启动与崩溃、JNA 加载、虚拟显示创建/销毁、授权操作、native crash 堆栈

### `asst.log` / `asst.bak.log`（MaaCore 原生日志，core 侧）

- **归属**：MaaCore C++ 运行时
- **格式**：
  ```
  [2026-03-18 14:35:42.123][INF][Px12345][Tx6789] Assistant::append_callback | SubTaskStart {"taskchain":"FightTask",...}
  [2026-03-18 14:35:43.456][ERR][Px12345][Tx6790] ProcessTask::run | get stage info failed
  ```
- **日志级别**：TRC、DBG、INF、WRN、ERR
- **最适合看**：
  - MaaCore 内部任务执行的完整流程
  - 图像识别（模板匹配、OCR）的详细结果
  - 任务状态机转换、截屏和输入操作的底层细节
  - 资源加载失败的真实原因
- **关键消息类型**（`Common/AsstMsg.h`）：
  - `InternalError`(0)、`InitFailed`(1)、`ConnectionInfo`(2)、`AllTasksCompleted`(3)、`AsyncCallInfo`(4)、`Destroyed`(5)
  - `TaskChainError`(10000)、`TaskChainStart`、`TaskChainCompleted`、`TaskChainExtraInfo`、`TaskChainStopped`
  - `SubTaskError`(20000)、`SubTaskStart`、`SubTaskCompleted`、`SubTaskExtraInfo`、`SubTaskStopped`
  - `ReportRequest`(30000)
- **`asst.bak.log`** 是轮转前的旧日志（单文件 64MB 上限）
- **这是分析 MaaCore 内部行为的最权威证据**

### `interface/`、`crash.log`（MaaCore 失败现场，core 侧）

- **`interface/*.png`**：任务链失败时 core 自己存的那一帧，早于失败回调。v6.19.0-beta.2 起改在 `Assistant::working_proc` 用缓存帧存，抛异常也存（此前在 `InterfaceTask::run` 现拍）。后台模式下是虚拟屏上的游戏画面，直接用 Read 看图判断卡在哪个界面
- **`crash.log`**：MaaCore 的崩溃记录，提权进程死亡类问题必看
- debug 目录下其它 core 产物（截图、识别调试图等）导出时也会一并带上，按需翻

### `properties.txt`（设备属性）

- **每次导出都会附带**，`getprop` 全量输出
- **最适合看**：ROM 类型与版本、厂商定制属性（影响虚拟显示、提权、后台保活）

### `remote/`（独立数据目录模式下的 core 侧文件）

- 仅 `Core Dir` 为 `LOCAL_TMP` 时存在：提权进程 `debug/` 下的**所有**文件经 binder 拉进来，`asst.log`、`logcat/`、`interface/`、`service_boot_debug.log`、`*_launch_debug.log` 都在这里
- 导出时提权服务不在线则整个目录缺失——此时拿不到 `asst.log`，要让用户连上服务后重新导出

### `export_skipped.txt`

- 两类条目：提权进程写的文件对 App 不可读（`<文件>: <异常信息>`），以及被包体预算挤掉的旧截图（`<文件>: over export size budget`）。包里缺 `asst.log` 或缺某张截图先看这个文件

## How To Filter Evidence

### 1. 建立锚点（`device_info.txt` 优先，issue 文本补充）

- MaaMeow 版本、MaaCore 与资源版本
- 提权后端（Shizuku / Root），Shizuku 是 adb 还是 root 身份
- 运行模式：前台（FOREGROUND，使用主显示，需横屏且 16:9）还是后台（BACKGROUND，虚拟显示默认 1280x720）
- 客户端类型（官服 Official / B服 Bilibili / 国际服等）与游戏版本
- 数据目录模式（决定 core 侧文件在根目录还是 `remote/`）
- 执行的任务（Fight、Recruit、Infrast、Roguelike、StartGame 等）
- 用户描述的异常现象

### 2. 从日志中找高价值信号

**meow_log（任务会话）**——中文 / English：
- `"level":"ERROR"` 或 `"level":"WARNING"` 的条目，非 `COMPLETED` 的 footer（对照上面的 status 表）
- `MAA服务异常终止` / `MAA service terminated unexpectedly` — 提权进程死亡
- `<后端> 未授权，已取消启动` / `is not authorized; start canceled` — 后端在运行但没授权
- `<后端> 不可用，已取消启动` / `无法连接远程服务：<原因>` — 后端不可用或拉不起来，原因里常带 launcher log tail
- `资源加载失败` / `Failed to load resources`
- `创建 MaaCore 实例失败`、`设置触控模式失败`、`设置显示模式失败`
- `启动虚拟显示失败`（带"Shizuku 以 Root 身份运行"的变体 = Root 授权的 Shizuku，要改用内置 Root 模式）
- `启动 MaaCore 超时或失败` / `MaaCore connection timed out or failed` — `AsyncConnect` 2 秒内没回 `AsyncCallInfo`
- `MaaCore 启动失败` / `Failed to start MaaCore` — `AsstStart` 返回 false
- `任务加入队列失败：<任务名>` — core 拒绝了任务参数，对照同一文件里的 `[TaskParams]` 行
- `游戏进程未启动或被异常关闭(<包名>)` — 游戏崩溃或被杀
- `游戏窗口已离开虚拟显示器且自动拉回失败` — 游戏跑到主屏去了
- `游戏画面帧率仅 N FPS` — 帧率过低，自动战斗时机会不准
- `已达到运行时长上限` — 是设置项主动停的，不是故障
- `系统拒绝了前台服务权限(FOREGROUND_SERVICE_SPECIAL_USE)` — 设备侧 appop 被拒

**asst.log（MaaCore 内部）**：
- `[ERR]` 和 `[WRN]` 级别的所有条目
- `Templ file exists in multiple paths` — 同名模板跨目录，core 整体拒绝加载资源
- `get stage info failed` — 关卡识别失败
- `update deployment failed` — 部署更新失败
- `operator name recognition failed` — 干员名称 OCR 失败
- `analyze level failed` — 等级分析失败
- `Unknown facility` / `unknown facility` — 基建设施识别失败
- `task disabled, pass` — 任务被禁用跳过
- 连续重复的识别失败模式

**logcat/core（服务进程，需调试模式）**：
- `DeadObjectException` / `Callback DROPPED` — IPC 断开，App 进程或回调通道已死
- `SIGSEGV` / `SIGABRT` / Fatal signal — Native crash
- `setup failed - userDir inaccessible` — 提权进程读写不了数据目录
- `Failed to load MaaCore` — JNA 加载失败
- `SetUserDir ... = false`、`LoadResource(...) = false`、`CreateInstance() = false`、`AsyncConnect(...) = false`、`AppendTask(<type>) = 0`、`Start() = false`

**error.log（App 侧）**：
- `Remote setup failed: CORE_NOT_LOADED | USER_DIR_INACCESSIBLE | SET_USER_DIR_FAILED`
- `LoadResource failed: <path>` — 只说失败不说原因，原因在 `asst.log`
- `<pkg>:<suffix> start failed`、`launcher exited early code=N`、`binder not attached within 15000ms`
- `<pkg>:<suffix> process died unexpectedly.`、`RemoteService binder died`
- `Core data prepare failed`、`core user data push failed before start` — 独立目录下投递失败

### 3. 按问题类型选择重点日志

| 问题类型 | 主要日志 | 辅助日志 |
|----------|----------|----------|
| 任务执行失败/识别错误 | `asst.log` + `interface/` 截图 | `meow_log`（含 `[TaskParams]`） |
| 服务崩溃/异常终止 | `service_bind_debug.log`、`crash.log`、`logcat/core` | `error_logs/error.log`、`meow_log`、`asst.log` 尾部 |
| 提权进程拉不起来 / 连接超时 | `service_bind_debug.log` → `*_launch_debug.log` → `service_boot_debug.log` | `error_logs`、`device_info.txt`（后端、Shizuku 状态） |
| 虚拟显示问题 | `logcat/core` | `meow_log`、`device_info.txt`（分辨率、ROM、Shizuku 身份） |
| 资源加载失败 | `asst.log` | `error_logs`、`device_info.txt`（Core / Resource 版本、Core Dir） |
| MaaCore 初始化/连接失败 | `asst.log` + `logcat/core` | `meow_log`、`service_boot_debug.log` |
| 定时任务没跑 / 没解锁 | `schedule/trigger_*.log` | `error_logs`、`device_info.txt`（Battery Opt）、`app_settings.txt` |
| App 崩溃 / 闪退 | `crash_logs/` | `error_logs`、`logcat/app` |
| UI/权限问题 | `logcat/app` | `error_logs` |
| 公招/基建/肉鸽业务逻辑 | `asst.log` + `meow_log` | — |

### 4. 区分"本次复现"

- 日志包里可能混有多次运行的记录
- `meow_log` 文件名包含时间戳，先定位 issue 描述时间段对应的文件
- `asst.log` 用 `TaskChainStart` 的时间戳定位本次任务；`meow_log` 的 epoch 毫秒要换算成本地时间再对
- 如果 `asst.log` 中本次任务实际 `AllTasksCompleted` 成功，但用户说失败了，要明确写出"本日志未复现用户描述的失败"

### 5. Sentry 交叉查证（可选）

遥测默认开启（设置项"帮助改进本项目"）。`device_info.txt` 的 `Telemetry ID` 就是 Sentry `user.id`，`meow_log` 里的 `[Telemetry] run_id=` 是那一轮的 run id。有项目 Sentry 访问权限时（仅维护者），按 `user.id` 搜能找到同一设备的 `TaskFailure` / 启动失败 / 进程死亡 / 定时启动失败事件，以及事件附带的日志尾和截图；日志包缺 core 侧文件、或用户只给了 ID 时很有用。没有权限就跳过这一步，并在"缺失的证据"里写明。上报范围见 `docs/zh-cn/develop/TELEMETRY.md`。

## Common Patterns

### 服务异常终止（最常见）

用户看到"MAA服务异常终止"。可能原因：
- 提权服务进程被系统杀死（内存压力、电池优化、厂商后台管控）
- MaaCore native crash（SIGSEGV/SIGABRT）
- Shizuku 服务自己停了（adb 模式下 USB 调试断开、重启后未重新激活）
- **诊断路径**：`service_bind_debug.log` 找 `BINDER_DIED` 时间点 → `crash.log` / `logcat/core` 找崩溃堆栈 → `asst.log` 看崩溃前最后操作 → `meow_log` 确认当时在跑哪条任务链

### 提权进程拉不起来

任务启动报"无法连接远程服务"或一直连接中。可能原因：
- 后端 exec `liblauncher.so` 被拒：`launcher exited early code=126/127`（Shizuku adb 模式 shell 域跑 `/data/app` 下的 launcher，跨 ROM 兼容性未完全验证，这类 issue 优先查这里）
- 进程起来了但 binder 没回投：`binder not attached within 15000ms`
- 进程起来后在加载阶段死掉：`service_boot_debug.log` 停在 `MAA_LOAD_BEGIN` 没有 `MAA_LOAD_OK`，或出现 `MAA_LOAD_FAIL`
- **诊断路径**：`service_bind_debug.log` 看停在哪个阶段 → `error.log` 里的 launcher 日志全文或 `*_launch_debug.log` → `service_boot_debug.log` 看进程内走到哪一步

### 后端未授权

- `meow_log` footer 为 `BACKEND_NOT_GRANTED`，`service_bind_debug.log` 有 `BIND_DENIED`
- 新版本启动前会先引导授权；旧版本会把这种情况报成"资源加载失败"，遇到旧版本的"资源加载失败"要先排除未授权

### 资源加载失败

用户无法启动任务。可能原因：
- 首次安装未完成资源解压、存储空间不足、热更新下载中断
- 资源版本与 MaaCore 版本不匹配
- 同名模板跨目录（`Templ file exists in multiple paths`），core 整体拒绝加载
- 提权进程读写不了数据目录（见下一条）
- **诊断路径**：直接看 `asst.log` 的 `[ERR]` 行——App 侧只打 `LoadResource failed: <path>`，不带原因；`asst.log` 里没有任何加载记录，说明根本没走到 `LoadResource`，回头看 `error.log` 的 `Remote setup failed` 和 `Core data prepare failed`

### 数据目录不可访问

- `error.log`：`Remote setup failed: USER_DIR_INACCESSIBLE`；`service_boot_debug.log`：`SETUP_USER_DIR_INACCESSIBLE <原因>`
- 部分 Android 11 ROM 下 shell 读不了 `Android/data/<pkg>`；手机分身 / 多用户下提权进程（user 0）够不到 `/storage/emulated/<非0>`
- 处理：让用户在设置里把 MaaCore 数据目录切到独立目录（`LOCAL_TMP`）；分身 / 多用户属于不支持场景

### MaaCore 连接/初始化失败

任务无法启动。MaaCore 通过 `libbridge.so` 作为自定义连接库（`AsstSetStaticOption(3, "libbridge.so")`）与显示通信。可能原因：
- libbridge.so 加载失败
- 虚拟显示未就绪（`startVirtualDisplay` 返回 -1 → `VIRTUAL_DISPLAY_ERROR`）
- `AsyncConnect` 2 秒内未回调（`MAA_CONNECT_ERROR`）
- 前台模式竖屏或非 16:9（`PORTRAIT` / `INVALID_ASPECT_RATIO`，属于前置拒绝而非故障）
- **诊断路径**：`meow_log` footer status 定位失败环节 → `logcat/core` 看 JNA/JNI 调用返回值 → `asst.log` 看连接日志

### 任务识别/执行错误

特定任务执行异常。MaaCore 使用模板匹配和 OCR 识别游戏画面。可能原因：
- 分辨率不匹配（后台模式默认 1280x720，会根据客户端类型自动调整）
- 游戏版本更新导致 UI 变化（对比 `device_info.txt` 的 `Game` 与 `Resource` 版本）
- 客户端类型配置错误导致加载了错误的资源分支
- 游戏帧率过低、游戏窗口离开虚拟屏
- 任务参数被 core 拒绝（`任务加入队列失败`），该任务位本轮没有产出
- **诊断路径**：`asst.log` 看 ProcessTask 识别详情 → `interface/` 截图看卡在哪个界面 → `[TaskParams]` 核对下发参数 → 对照 MaaCore 源码中的任务实现

### 虚拟显示问题

后台模式卡顿或黑屏。可能原因：
- HardwareBuffer 三缓冲帧同步问题
- 设备不支持虚拟显示、DisplayManager 资源耗尽
- Shizuku 以 root 身份运行（`device_info.txt` 的 `Shizuku` 行写 `uid 0 (root)`）
- 锁屏未解除：keyguard 会占住虚拟显示，后台模式同样需要先唤醒解锁
- **诊断路径**：`logcat/core` 看 `ScreenManager` / 虚拟显示日志 → 看 bridge 帧捕获日志

### 定时任务没跑

- 没有对应时间的 `schedule/trigger_*.log`：闹钟根本没触发——看 `Battery Opt`、ROM 自启动管控
- footer `SKIPPED_LOCKED`：唤醒解锁失败且锁屏还在，`message` 区分"没填凭证"和"设备仍锁定"；看 `app_settings.txt` 的 `wakeUnlockType` / `wakeCredential` 和 `logcat/core` 的 `WakeUnlock`
- footer `FAILED_UI_LAUNCH`：后台模式要先拉起界面做倒计时，Activity 没拉起来（多为 ROM 的后台弹出界面限制）
- footer `SKIPPED_BUSY`：当时已有任务或另一次触发在跑
- footer `FAILED_VALIDATION`：目标配置已不存在、任务链里没有启用的任务，或启动前校验没过，`message` 里有原因
- footer `FAILED_START`：走到了启动任务但失败，转去看同一时间的 `meow_log` footer status
- footer `CANCELLED`：用户在倒计时里取消，不是故障
- footer `STARTED` 但用户说没跑：转去看同一时间的 `meow_log`
- 代码入口：`domain/launch/LaunchPipeline.kt`

### 日志和用户描述不一致

- 如果日志显示任务成功但用户说失败：说明此次日志未复现问题，不代表问题不存在
- 如果用户版本较旧，先确认版本对应的代码逻辑（`git log` / `git show <tag>:<path>`），不要用当前主线否定旧版本的问题；本文描述的日志文件和文案以当前主线为准，旧版本的包可能缺文件或文案不同
- 如果当前主线已修复该问题：确认修复是否已发版，已发版建议升级，未发版建议等待

## Correlating With Code

### MaaMeow 应用代码（当前仓库）

路径相对 `app/src/main/java/com/aliothmoon/maameow/`。

| 代码区域 | 路径 | 作用 |
|----------|------|------|
| 任务参数构建 | `data/model/*Config.kt`（`toTaskParams()`）+ `domain/usecase/AnalyzeTaskChainUseCase.kt` | 把 UI 配置转成 MaaCore 任务参数 JSON |
| 启动前检查 | `domain/usecase/PrepareTaskStartUseCase.kt` | 启动前校验与引导 |
| 任务编排 | `domain/service/MaaCompositionService.kt` | MAA 完整生命周期状态机，meow_log 的 footer status 都出自这里和回调分发 |
| 资源加载 | `domain/service/MaaResourceLoader.kt` | `setup` → 投递 → `LoadResource` 链，换客户端档位重启提权进程 |
| 回调分发 | `maa/callback/MaaCallbackDispatcher.kt` | 路由 MaaCore 回调到各 handler |
| 连接信息 | `maa/callback/ConnectionInfoHandler.kt` | `ConnectionInfo` 各 `what` 的处理 |
| 子任务处理 | `maa/callback/SubTaskHandler.kt` | 生成用户可见的日志 |
| 任务链处理 | `maa/callback/TaskChainHandler.kt`、`TaskChainStatusTracker.kt` | 任务链级别状态跟踪与总结 |
| 远程服务 | `remote/RemoteServiceImpl.kt` | 提权进程服务入口，`setup` 返回码见 `remote/SetupResult.kt` |
| MaaCore 服务 | `remote/MaaCoreServiceImpl.kt`、`remote/MaaCoreManager.kt` | JNA 调用封装与加载 |
| 独立数据目录 | `remote/CoreDataDir.kt`、`remote/internal/CoreDataStore.kt` | `LOCAL_TMP` 模式下 core 侧资源与 debug 文件 |
| JNA 接口 | `maa/MaaCoreLibrary.java` | MaaCore C 函数映射 |
| JNI 桥接 | `bridge/NativeBridgeLib.java`、`app/src/main/native/bridge*.{cpp,h}` | libbridge.so：帧捕获、输入、预览 |
| 服务管理 | `manager/RemoteServiceManager.kt` | 服务绑定状态机 |
| 进程拉起 | `manager/ProcessServiceConnectorBackend.kt`、`ShizukuSpawner.kt`、`SuSpawner.kt`、`app/src/main/native/launcher.c` | launcher 拉起、binder 回投、失败归因 |
| 提权状态 | `manager/RemoteAccessCoordinator.kt` | Shizuku / Root 可用性与授权 |
| 绑定诊断 | `manager/ServiceBootLogger.kt`、`remote/RemoteBootTrace.kt` | `service_bind_debug.log` / `service_boot_debug.log` |
| 会话日志 | `domain/service/MaaSessionLogger.kt`、`data/model/LogLevel.kt` | 内存日志 + `meow_log_*.log` JSON 落盘 |
| 错误日志 | `data/log/ApplicationLogWriter.kt`、`utils/log/FileLogTree.kt` | `error_logs/error.log` 写入与轮转 |
| 崩溃日志 | `utils/CrashHandler.kt` | `crash_logs/` |
| 日志导出 | `domain/service/LogExportService.kt`、`LogExportCollector.kt`、`data/preferences/AppSettingsSnapshot.kt`、`constant/LogConfig.kt` | ZIP 打包、筛选与预算、设置快照、各项保留阈值 |
| logcat 捕获 | `manager/LogcatServiceManager.kt`、`remote/LogcatCaptureServiceImpl.kt`、`remote/internal/{LogcatRetention,LogcatNoiseFilter,RollingLogFile}.kt` | 调试模式下双进程 logcat 抓取、保留、去噪、滚动 |
| 定时任务 | `domain/launch/LaunchPipeline.kt`、`schedule/service/ScheduleTriggerLogger.kt`、`schedule/` | 触发流程、触发日志与闹钟 |
| 遥测 | `telemetry/`（`RunTracer`、`IncidentReporter`、`TaskEvidence`） | Sentry 事件与证据附件 |
| 浮窗控制 | `overlay/OverlayController.kt` | 浮窗生命周期 |

### MaaCore 源码获取

本仓库**不包含** MaaCore C++（仅有 `scripts/setup_maa_core.py` 下发的预编译 so + 资源）。对照内核实现时按优先级：

1. **先读 `asst.log` / `asst.bak.log`**，多数问题不必碰源码。
2. **轻量查阅**：GitHub blob
   `https://github.com/MaaAssistantArknights/MaaAssistantArknights/blob/<ref>/src/MaaCore/...`
3. **必要时本地 clone**（无磁盘或无网络权限的环境跳过并说明）：

```bash
# 浅克隆即可；目录放在本仓库外或 .cache 下，勿提交
git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/MaaAssistantArknights/MaaAssistantArknights.git \
  .cache/MaaAssistantArknights
cd .cache/MaaAssistantArknights
git sparse-checkout set src/MaaCore
# 对齐 device_info.txt 的 Core 版本：
# git fetch --depth 1 origin tag <tag> && git checkout <tag>
```

- 已有本地上游副本时直接复用，以实际工作区或用户告知的路径为准；先 `git describe --tags` 确认它在哪个版本。
- 引用上游代码时用上游 blob 链接（带 commit/tag）。

下表路径相对 `src/MaaCore`。

| 代码区域 | 关键文件 | 作用 |
|----------|----------|------|
| 日志系统 | `Utils/Logger.hpp` | 日志格式、`asst.log` 路径与 64MB 轮转 |
| 消息定义 | `Common/AsstMsg.h` | AsstMsg 枚举和回调类型 |
| 实例管理 | `Assistant.h/cpp` | 消息队列、回调路由 |
| 任务基类 | `Task/AbstractTask.cpp` | 任务生命周期、basic_info() |
| 流程任务 | `Task/ProcessTask.cpp` | 状态机驱动的任务执行 |
| 接口任务 | `Task/InterfaceTask.cpp`、`Task/Interface/` | 各任务类型入口、参数校验（`AppendTask` 被拒看这里）、失败截图（`save_fail_img`） |
| 战斗任务 | `Task/Fight/` | 刷理智相关 |
| 基建任务 | `Task/Infrast/` | 基建管理逻辑 |
| 肉鸽任务 | `Task/Roguelike/` | 肉鸽模式逻辑 |
| 其它任务 | `Task/Miscellaneous/`、`Task/Reclamation/`、`Task/SSS/`、`Task/MiniGame/` | 公招（`AutoRecruitTask`）、信用商店、生息演算、保全、小游戏等 |
| 控制器 | `Controller/` | 连接、截图、触控；自定义连接库接入点 |
| 资源加载 | `Config/ResourceLoader.cpp`、`Config/TemplResource.cpp`、`Config/TaskData.cpp` | 资源、模板、任务数据加载 |

## Output Format

```markdown
## Issue 概要

- Issue：`#1234`（或"本地日志包"）
- MaaMeow 版本 / 提权后端 / 运行模式 / 客户端类型：
- 执行任务：
- 用户现象：

## 关键证据

- `meow_log`：...
- `asst.log`：...
- `logcat/core`：...
- `service_bind_debug.log` / `service_boot_debug.log`：...
- `error_logs`：...
- 缺失的证据：...

## 根因判断

- 直接结论：
- 证据链：
- 当前主线是否可能已修复：

## 修复方案

1. 代码层修复（指明具体文件和改动；遵守 `domain` 不依赖 `presentation` / `data` 的边界）
2. 需要补充的日志或测试
3. 如属于不支持场景，说明如何限制入口或改进提示

## 给用户的建议

- 用户现在可以尝试的操作：
- 是否建议升级 / 重装 / 同步资源 / 重置配置 / 切换后端或数据目录：
- 是否需要等待开发者修复：
- 是否有临时绕过方案：

## 给 AI 的修复指令（可复制）

~~~text
已确认事实：
- ...

已确认根因：
- ...

请按以下要求修复：
1. 优先修改这些文件：...
2. 目标改动：...
3. 不要采用这些修法：...
4. 回归验证：...
5. 如果暂时无法彻底修复，至少补上：...
~~~

## 置信度

- 高 / 中 / 低
- 还缺什么证据
```

只列实际读到的日志源；包里没有的文件写进"缺失的证据"，不要留空行凑数。

## Reminders

- 不要只看一个日志文件下结论，要交叉验证多个日志源。
- 不要把维护者评论当成唯一证据，要用日志和代码自行验证。
- `meow_log` 是用户视角的摘要，`asst.log` 才是 MaaCore 内部的完整记录——涉及识别/任务逻辑问题时以 `asst.log` 为准。
- 没有 `logcat/` 多半只是没开调试模式；需要 logcat 才能定论时，请用户开启调试模式复现后重新导出。
- 包里缺 core 侧文件时先查 `Core Dir`、`remote/`、`export_skipped.txt`，再判断是不是导出时服务不在线。
- 把已验证的事实和推断分开写；结论依赖手头没有的信息（日志、设备行为、上游源码）时，直接向用户要，不要猜。
- 如果 issue 版本较旧，要区分"当时的根因"和"当前代码是否已修复"。
- 如果日志与用户描述不一致，显式说明"证据未复现"还是"证据已复现但用户表述不精确"。
- 如果结论是"设备/ROM 不兼容"或"功能不支持"，必须给出代码级依据。
- 回答时只摘取支撑结论的关键片段，不要倾倒整份日志。
- 代码引用用 GitHub blob：`https://github.com/Aliothmoon/MAA-Meow/blob/<sha>/...#L..`；上游同理。
- 用户可见文案以 `values/strings.xml`（中文源）/ `values-en` 为准，不要直接甩内部 key。
- 架构细节以 `CLAUDE.md` 为准；本 skill 过时处按源码与 `CLAUDE.md` 校正。
