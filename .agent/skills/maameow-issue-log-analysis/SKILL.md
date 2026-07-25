---
name: maameow-issue-log-analysis
description: >
  分析 MaaMeow（Aliothmoon/MAA-Meow）的 GitHub Issue 或本地 `maa_logs_*.zip` 日志包。
  下载附件后从 gui/meow_log、error_logs、logcat/core|app、asst.log、properties.txt 交叉取证，
  对照双进程（App + Shizuku/Root 提权）与 MaaCore/bridge 代码判断根因。
  Use when analyzing MaaMeow/MAA-Meow issues, log zips, task failures, service death,
  Shizuku/Root elevation, virtual display, recognition errors, or connection init failures.
---

# MaaMeow Issue / Log Analysis

仓库架构与包边界以仓库根目录 `Claude.md` / `CLAUDE.md` 为准；本 skill 只保留**排障所需**摘要。分析时若与本文冲突，以当前 checkout 的 `Claude.md` 与源码为准。

## Scope

- 适用于 `https://github.com/Aliothmoon/MAA-Meow` 的公开 Issue。
- 也适用于本地 `maa_logs_*.zip` 或已解压日志目录。
- 输入：完整 issue URL、`#1234`、或本地路径。
- 无 `maa_logs_*.zip` 时先声明证据不足，再基于正文/截图/代码做初步判断。
- 非本仓库 issue 时停止并说明不适用。
- **默认只有本仓库 checkout**（无 MaaCore C++ 树）。内核行为优先用 `asst.log`；需要对照 C++ 实现时，按下方「MaaCore 源码获取」**必要时 clone 上游**。

## Background（对齐 Claude.md）

MaaMeow 在 Android 上通过 **Shizuku 或 Root** 启动独立提权进程，用 JNA 加载 `libMaaCore.so`，用 `libbridge.so` 做截屏/输入，经 AIDL 与 App 进程通信。Compose UI + 浮窗；自动化核心在提权进程。

### 双进程模型

| 进程 | 职责 | 日志 |
|------|------|------|
| **App** | Compose UI、Domain、ViewModel、Koin；经 `RemoteServiceManager` 绑远程 | Timber；`MaaSessionLogger` → 任务会话 `meow_log` |
| **Elevated**（Shizuku user-service **或** Root/libsu） | `RemoteServiceImpl` 入口；`MaaCoreServiceImpl`/`MaaCoreManager`；JNA `libMaaCore.so`；JNI `libbridge.so`；虚拟显示；hidden API 授权；`third/` 系统服务包装 | **`Ln`（scrcpy 风格）**；`LogcatCaptureServiceImpl` 回传 logcat |

**关键约定（Claude.md）**：`Timber.plant` 仅在 App 的 `MaaApplication`（`LogTreeHolder`）调用；提权进程里 Timber **静默 no-op**，必须用 `Ln`。共享代码不要依赖 Timber。

### 提权后端（Shizuku ⟷ Root）

- `domain/models/RemoteBackend`：`SHIZUKU` | `ROOT`（设置项 `startupBackend`）
- `manager/RemoteAccessCoordinator`：统一可用性与权限（`RemoteAccessState`）
- `manager/RemoteServiceManager`：绑定状态机 Disconnected → Connecting → Connected → Died（与后端无关）
- Connector：`ShizukuRemoteServiceConnector` / `RootRemoteServiceConnector`
- Root：`root/`（`RootUserService`、`RootServiceStarter` 等）

排障时先确认用户用的是 **Shizuku 还是 Root**，再解释「服务异常终止」。

### 生命周期与 IPC

- 编排：`domain/service/MaaCompositionService`  
  资源加载 → 实例创建 → 虚拟显示 → connect → append tasks → start  
  状态：`MaaExecutionState` = IDLE / STARTING / RUNNING / STOPPING / ERROR
- `UnifiedStateDispatcher`：观察远程连接，连接后触发资源加载，转发服务死亡
- AIDL（`app/src/main/aidl/`）：`RemoteService`、`MaaCoreService`、`MaaCoreCallback`、`ILogcatService`、…
- 回调链：

```text
libMaaCore.so → JNA AsstApiCallback → MaaCoreServiceImpl
  → AIDL MaaCoreCallback → MaaCallbackDispatcher
  → SubTaskHandler / TaskChainHandler → MaaSessionLogger → UI + meow_log
```

### 原生与连接

- `app/src/main/native/` → `libbridge.so`：`bridge_frame_buffer` / `bridge_capture` / `bridge_input` / `bridge_preview`
- `bridge/NativeBridgeLib.java`：JNI；`maa/MaaCoreLibrary.java`：JNA `Asst*`
- MaaCore 自定义连接库：`AsstSetStaticOption(3, "libbridge.so")`
- **MaaCore 不是 submodule**，由 `scripts/setup_maa_core.py` 下载；版本写在 `.maaversion` → `BuildConfig.MAA_CORE_VERSION`

### 运行模式

- `FOREGROUND`：主显示，需横屏
- `BACKGROUND`：虚拟显示（默认 1280×720）

### 其他易混模块

- 浮窗：`overlay/`（`ACCESSIBILITY` / `FLOAT_BALL`）
- 定时：`schedule/`（`AlarmManager`；国产 ROM 自启动可能吞闹钟）
- 权限：`manager/PermissionManager`、`data/permission/`
- 包边界：`domain` 不依赖 `presentation`/`data`；分析修复建议时尊重此边界

## Workflow

1. **规范化输入**  
   - `#N` → `https://github.com/Aliothmoon/MAA-Meow/issues/N`  
   - 本地 zip/目录 → 跳过 issue 拉取  

2. **读 Issue**（`gh issue view N --repo Aliothmoon/MAA-Meow`）  
   提取：App 版本、**提权后端（Shizuku/Root）**、运行模式、客户端、任务、期望/实际、复现步骤、维护者评论。  
   评论结论需用日志/代码自证，勿照抄。

3. **附件**  
   优先 `maa_logs_*.zip`（`LogExportService` 命名）；多包取最新复现。

4. **下载解压**  
   例如 `.cache/issue-logs/issue-<N>/`；先列目录；只摘相关片段。

5. **时间线 + 本次复现**  
   串联各日志时间戳；用 `meow_log` 文件名与 `asst.log` 的 `TaskChainStart` 锁定本次。

6. **按问题类型选主日志**（见下表）→ 回溯本仓库代码；内核侧用 `asst.log`，必要时 clone 上游 MaaCore 源码（见「MaaCore 源码获取」）。

## Log Map

### `gui/meow_log_*.log`（任务会话）

- App：`domain/service/MaaSessionLogger`（JSON lines，前缀 `meow_log_`）
- 级别：MESSAGE / INFO / SUCCESS / WARNING / ERROR / TRACE；另有公招/肉鸽特殊级别
- 最适合：用户可见任务流程、SubTask 错误、业务结果摘要
- 命名：`meow_log_YYYYMMDD_HHmmss_N.log`

### `error_logs/error.log`（App 错误）

- App：Timber → `data/log/ApplicationLogWriter`
- 最适合：非任务会话错误、启动/权限、App 崩溃
- Release 通常只留 WARN+

### `logcat/core/logcat_*.log`（提权进程 logcat）

- 提权进程经 `LogcatCaptureServiceImpl` 捕获（**不限 Shizuku**，Root 同样）
- 含：`Ln`（tag `MaaMeow`，前缀常有 `[MC]`）、MaaCore stdout、`libbridge`、native crash
- 最适合：服务启停/崩溃、JNA 加载、虚拟显示、授权、`DeadObjectException`、SIGSEGV/ABRT

### `logcat/app/logcat_*.log`（App logcat）

- UI / Koin / ViewModel / OkHttp / 权限检查

### `asst.log` / `asst.bak.log`（MaaCore）

- C++ spdlog；识别与任务状态机的**权威**记录
- 关注 `[ERR]`/`[WRN]`、`TaskChain*`、`SubTask*`、`ConnectionInfo`、`AllTasksCompleted`
- `asst.bak.log` 为轮转前备份

### `properties.txt`

- 多在 Debug 导出；`getprop`；看 ROM/厂商定制对虚拟显示、提权兼容性的影响

## How To Filter Evidence

### Issue 锚点

- 版本、**Shizuku 或 Root**、前台/后台、客户端、任务、现象时间点

### 高价值信号

**meow_log**：`ERROR`/`WRN`、非 `COMPLETED` footer、`远程服务异常终止`、`资源加载失败`、`MaaCore 启动/创建失败`、`启动虚拟显示失败`、游戏进程异常

**asst.log**：`get stage info failed`、部署/OCR 失败、`Unknown facility`、连续识别失败

**logcat/core**：`DeadObjectException`、Fatal signal、`SetUserDir/LoadResource/CreateInstance ... = false`

### 问题类型 → 主日志

| 问题类型 | 主日志 | 辅助 |
|----------|--------|------|
| 识别/任务逻辑 | `asst.log` | `meow_log` |
| 服务崩溃/异常终止 | `logcat/core` | `error_logs`、`meow_log` |
| 提权/绑定失败 | `logcat/app` + `logcat/core` | `error_logs` |
| 虚拟显示 | `logcat/core` | `logcat/app`、native bridge |
| 资源加载 | `meow_log` | `asst.log`、`error_logs` |
| IPC / DeadObject | `logcat/core` + `logcat/app` | `error_logs` |
| UI/权限/浮窗 | `logcat/app` | `error_logs` |
| 定时任务未触发 | issue 文本 + ROM 信息 | `error_logs`、schedule 相关 |

### 本次复现

- 日志常混多次运行；先对齐时间窗  
- `asst.log` 已 `AllTasksCompleted` 但用户说失败 → 写明「本日志未复现」

## Common Patterns

### 服务异常终止

- 提权进程被杀（内存/省电）、native crash、Shizuku/Root 通道断开、绑定状态机 Died  
- 路径：`logcat/core` 堆栈 → `meow_log` 时间点 → `asst.log` 崩溃前最后操作  
- 区分后端：Root 看 libsu/bootstrap；Shizuku 看 Shizuku 版本与授权

### 资源加载失败

- 首次解压未完成、空间不足、热更中断、资源与 `BuildConfig.MAA_CORE_VERSION` / `.maaversion` 不匹配  
- 路径：`meow_log` → `asst.log` 的 LoadResource

### 连接 / 初始化失败

- `libbridge` 未就绪、虚拟显示未建好、`AsstAsyncConnect` 超时  
- 路径：`logcat/core` JNA/JNI → `asst.log` ConnectionInfo

### 识别 / 执行错误

- 分辨率/客户端资源分支/游戏 UI 变更  
- 以 `asst.log` 为准；需读 C++ 实现时按「MaaCore 源码获取」clone 或 blob

### 虚拟显示

- HardwareBuffer 三缓冲、设备不支持 VD、DisplayManager 耗尽  
- `logcat/core` + `app/src/main/native/bridge_frame_buffer.*` / `bridge_capture.*`

### 版本与描述不一致

- 旧 issue 先按用户版本理解，再对照当前主线是否已修  
- 已发版则建议升级；未发版说明等待

## Correlating With Code

包根：`app/src/main/java/com/aliothmoon/maameow/`（下表相对该根，除非写了 `app/src/...`）。

| 区域 | 路径 | 作用 |
|------|------|------|
| 生命周期编排 | `domain/service/MaaCompositionService.kt` | 资源→实例→VD→connect→任务 |
| 远程绑定 | `manager/RemoteServiceManager.kt` | 连接状态机 |
| 提权统一状态 | `manager/RemoteAccessCoordinator.kt` | Shizuku/Root |
| Root 启动 | `root/` | libsu bootstrap |
| 提权入口 | `remote/RemoteServiceImpl.kt` | Elevated 入口 |
| MaaCore AIDL 封装 | `remote/MaaCoreServiceImpl.kt` | JNA 调用 |
| JNA | `maa/MaaCoreLibrary.java` | `Asst*` |
| JNI | `bridge/NativeBridgeLib.java` | `libbridge.so` |
| 回调分发 | `maa/callback/MaaCallbackDispatcher.kt` | 回调路由 |
| 子任务/任务链 | `maa/callback/SubTaskHandler.kt`、`TaskChainHandler.kt` | 用户可见日志 |
| 任务准备 | `domain/usecase/PrepareTaskStartUseCase.kt` 等 | 启动前检查 |
| 日志会话 | `domain/service/MaaSessionLogger.kt` | meow_log 写入与清理 |
| 日志导出 | `domain/service/LogExportService.kt` | `maa_logs_*.zip` |
| logcat IPC | `remote/LogcatCaptureServiceImpl.kt` | 双进程 logcat |
| 浮窗 | `overlay/OverlayController.kt` | 浮窗生命周期 |
| 原生 | `app/src/main/native/bridge*.{cpp,h}` | 截屏/输入/帧缓冲 |
| AIDL | `app/src/main/aidl/` | 双端契约 |
| MaaCore 部署 | `scripts/setup_maa_core.py`、`.maaversion` | 预编译 so + 资源 |

### MaaCore 源码获取

本仓库**不包含** MaaCore C++（仅有 `setup_maa_core.py` 下发的预编译 so + 资源）。对照内核实现时按优先级：

1. **先读 `asst.log` / `asst.bak.log`**，多数问题不必碰源码。
2. **轻量查阅**：GitHub blob（可点链接即可）  
   `https://github.com/MaaAssistantArknights/MaaAssistantArknights/blob/<ref>/src/MaaCore/...`
3. **必要时本地 clone**（本地 IDE / 可写磁盘的 agent；CI 若无磁盘或无网络权限则跳过并说明）：

```bash
# 浅克隆即可；目录放在本仓库外或 .cache 下，勿提交
git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/MaaAssistantArknights/MaaAssistantArknights.git \
  .cache/MaaAssistantArknights
cd .cache/MaaAssistantArknights
git sparse-checkout set src/MaaCore
# 若 issue / .maaversion 对应特定版本，再 checkout 对应 tag：
# git fetch --depth 1 origin tag <tag> && git checkout <tag>
```

- 版本尽量对齐 issue 中的 MaaMeow / Core 版本，或仓库根 `.maaversion` / `BuildConfig.MAA_CORE_VERSION`；对不上时在结论中写明「按 tag X 对照」。
- 已有本地上游副本时直接复用，以实际工作区或用户告知的路径为准。
- 引用上游代码时用上游 blob 链接（带 commit/tag）。
- 关注目录/概念：`src/MaaCore` 下 `Assistant`、`ProcessTask`、`Task/Fight|Recruit|Infrast|Roguelike`、`AsstMsg`、`Utils/Logger`

## Output Format

```markdown
## Issue 概要

- Issue：`#N`（或本地日志包）
- MaaMeow 版本 / 提权后端 / 运行模式 / 客户端：
- 执行任务：
- 用户现象：

## 附件概览

- 已读文件：
- 缺失证据：

## 关键证据

- `meow_log`：...
- `asst.log`：...
- `logcat/core`：...
- `logcat/app`：...
- `error_logs`：...

## 根因判断

- 直接结论：
- 证据链：
- 当前主线是否可能已修复：

## 修复方案

1. 代码/配置改动（遵守 domain 边界；点名文件）
2. 建议补充的日志或测试（高 churn：`MaaCompositionService`、`RemoteServiceManager`）
3. 不支持场景时的入口限制或提示改进

## 给用户的建议

- 可立即尝试的操作：
- 是否升级 / 重装 / 同步资源 / 换提权后端 / 关电池优化：
- 临时绕过：

## 给修复 AI 的指令（可复制）

~~~text
已确认事实：
- ...

已确认根因：
- ...

请按以下要求修复：
1. 优先修改：...
2. 目标改动：...
3. 避免：...
4. 回归验证：...
5. 若无法彻底修复，至少补上：...
~~~

## 置信度

- 高 / 中 / 低
- 还缺什么证据
```

## Reminders

- 多日志交叉验证；维护者评论不是唯一证据。
- 用户可见摘要看 `meow_log`；识别/任务逻辑以 `asst.log` 为准。
- 提权进程日志是 `Ln` + logcat/core，不是 Timber。
- 服务问题先分清 Shizuku vs Root 与绑定状态机。
- 旧版本 issue 区分「当时根因」与「主线是否已修」。
- 证据未复现要写明，勿硬凑。
- 设备/ROM 不兼容须有代码或日志依据。
- 代码引用用 GitHub blob：`https://github.com/Aliothmoon/MAA-Meow/blob/<sha>/...#L..`；上游同理。
- 识别/任务逻辑需要读 C++ 时，**可以** clone `MaaAssistantArknights/MaaAssistantArknights`（见「MaaCore 源码获取」）；仅 blob 不够或要全局搜索时再 clone。
- 用户可见文案以 `values/strings.xml`（中文源）/ `values-en` 为准，勿直接甩内部 key。
- 架构细节以 `Claude.md` 为准；本 skill 过时处按源码与 Claude.md 校正。
