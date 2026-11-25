# Code Map (MaaMeow + MaaCore / MaaFramework)

## MaaMeow 应用与提权代码（当前仓库）

路径相对 `app/src/main/java/com/aliothmoon/maameow/`（native 路径相对仓库根）。

| 代码区域 | 关键文件 | 作用 |
|---|---|---|
| 任务参数与预检 | `data/model/*Config.kt`（`toTaskParams()`）、`domain/usecase/AnalyzeTaskChainUseCase.kt`、`PrepareTaskStartUseCase.kt` | 配置转 MaaCore JSON、启动前校验与引导 |
| 任务编排与生命周期 | `domain/service/MaaCompositionService.kt`、`TaskEndRegistry.kt`、`TaskExecutionService.kt`、`SpecialUseFgsGate.kt` | 完整执行状态机、`meow_log` footer status、任务结束关游戏/熄屏、FGS 保活与降级 |
| 运行监控 | `domain/service/AppWatchdog.kt`、`remote/internal/{ProcessLiveness,StaleFrameGuard,GameFpsMonitor}.kt` | 游戏存活/漂移看门狗、停帧刷黑、帧率监控 |
| 进程退出取证 | `data/log/ProcessExitRecorder.kt`、`ProcessExitLog.kt`、`remote/internal/ProcessExitQuery.kt` | `ApplicationExitInfo` 采集、去重、AOSP/MIUI/vivo 强停识别与死因归类 |
| 资源加载 | `domain/service/MaaResourceLoader.kt`、`remote/CoreDataDir.kt`、`remote/internal/{CoreDataStore,UserDirProbe}.kt` | `setup` → 投递 → `LoadResource`、`LOCAL_TMP` 独立目录与可读性探测 |
| 回调分发 | `maa/callback/MaaCallbackDispatcher.kt`、`ConnectionInfoHandler.kt`、`SubTaskHandler.kt`、`TaskChainHandler.kt`、`TaskChainStatusTracker.kt` | 路由 `AsstMsg`、生成用户日志、回调日志过滤（`CallbackLogPolicy`） |
| 提权服务入口 | `remote/RemoteServiceImpl.kt`、`SetupResult.kt`、`MaaCoreServiceImpl.kt`、`MaaCoreManager.kt` | AIDL 入口、`setup` 返回码、JNA 加载与调用封装 |
| 虚拟屏与电源 | `remote/internal/{BackgroundDisplay,VirtualDisplayManager,VdmDisplayController,PrimaryDisplayManager,PowerController,ActivityUtils}.kt` | Legacy（`vd.tier`）/ VDM 双后端、独立电源组唤醒与保活、硬件熄屏、拉起/拉回游戏 |
| 唤醒解锁 | `remote/internal/{WakeUnlockController,WakeKeyguardSupport,UnlockGestureReplay,WakeTrace}.kt`、`domain/launch/WakeDiagnosis.kt` | 提权侧一次性唤醒/PIN/手势解锁与拉起界面、诊断耗时记录 |
| JNA / JNI / Upcall | `maa/MaaCoreLibrary.java`、`maa/DriverClass.java`、`bridge/NativeBridgeLib.java`、`app/src/main/native/bridge*.{cpp,h}` | MaaCore C 映射、`startApp`/`awaitFirstFrame(5000ms)`/触控 upcall、`libbridge.so` 帧捕获与预览 |
| 进程拉起与绑定 | `manager/{RemoteServiceManager,ProcessServiceConnectorBackend,ShizukuSpawner,SuSpawner,RemoteAccessCoordinator}.kt`、`root/`、`app/src/main/native/launcher.c` | 绑定状态机、卡死自动重启（`callOrRestart`/`RemoteCallStuckException`/`ThreadDump`）、残留清理、launcher 降权与 binder 回投 |
| 诊断与日志落盘 | `manager/ServiceBootLogger.kt`、`remote/RemoteBootTrace.kt`、`domain/service/MaaSessionLogger.kt`、`data/log/ApplicationLogWriter.kt`、`utils/log/FileLogTree.kt`、`utils/CrashHandler.kt` | `service_bind_debug.log`、`service_boot_debug.log`、`meow_log`、`error.log`、`crash_logs/` |
| 日志导出与 logcat | `domain/service/{LogExportService,LogExportCollector}.kt`、`data/preferences/AppSettingsSnapshot.kt`、`constant/LogConfig.kt`、`manager/LogcatServiceManager.kt`、`remote/LogcatCaptureServiceImpl.kt`、`remote/internal/{LogcatRetention,LogcatNoiseFilter,RollingLogFile}.kt` | ZIP 打包/预算裁剪、`device_info.txt`、`app_settings.txt`、调试模式双进程 logcat |
| 定时与触发流水线 | `domain/launch/LaunchPipeline.kt`、`schedule/service/{ScheduleTriggerLogger,ScheduleAlarmManager,ScheduleFailureReporter,ShizukuDownMonitor}.kt`、`schedule/model/ExecutionResult.kt` | 定时/外部触发、黑名单、漏触发补记、Shizuku 掉线监控 |
| 遥测与事后取证 | `telemetry/{TelemetryController,RunTracer,IncidentReporter,TaskEvidence,TaskFailure,ServiceDeathForensics,TelemetryAnr,TelemetryFgs,TelemetryRoute,TelemetryShizuku}.kt` | Sentry 事件/附件、服务死后补取系统 logcat、ANR/FGS/路由标签 |
| 预览与浮窗 | `presentation/view/background/BackgroundTaskView.kt`、`presentation/viewmodel/{BackgroundTaskViewModel,PreviewSurfaceHandover}.kt`、`overlay/OverlayController.kt` | 预览 Surface 交接与生命周期、浮窗控制 |

---

## 上游源码获取与索引（MaaCore & MaaFramework）

本仓库**不包含** MaaCore / MaaFramework C++ 源码（由 `scripts/setup_maa_core.py` 下载预编译 so 与资源；其中 `libMaaAndroidNativeControlUnit.so` 默认固定在 `DEFAULT_MAAFW_TAG` = `v5.15.0`）。

查阅优先级：
1. **先读 `asst.log` / `asst.bak.log`**，多数问题无需碰上游源码。
2. **已有本地副本直接复用**（工作区或用户提供的路径），先 `git describe --tags` 确认版本。
3. **轻量查阅**：GitHub blob `https://github.com/MaaAssistantArknights/MaaAssistantArknights/blob/<ref>/src/MaaCore/...`。
4. **必要时浅克隆**（放到 `.cache/` 下勿提交，无网络/磁盘权限则跳过说明）：

```bash
git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/MaaAssistantArknights/MaaAssistantArknights.git \
  .cache/MaaAssistantArknights
cd .cache/MaaAssistantArknights
git sparse-checkout set src/MaaCore
# 对齐 device_info.txt 的 Core 版本：
# git fetch --depth 1 origin tag <tag> && git checkout <tag>
```

### MaaCore 关键路径（相对 `src/MaaCore`）

| 代码区域 | 关键文件 | 作用 |
|---|---|---|
| 日志系统 | `Utils/Logger.hpp` | 日志格式、`asst.log` 路径与 64MB 轮转 |
| 消息定义 | `Common/AsstMsg.h` | `AsstMsg` 枚举与回调类型 |
| 实例管理 | `Assistant.h/cpp` | 任务队列、回调路由、`working_proc` 失败存图 |
| 任务基类与状态机 | `Task/AbstractTask.cpp`、`Task/ProcessTask.cpp` | 任务生命周期、状态机识别与重试 |
| 接口任务 | `Task/InterfaceTask.cpp`、`Task/Interface/`（含 `StartUpTask.cpp`、`CloseDownTask.cpp`） | 各任务入口、参数校验（`AppendTask` 拒绝看这里）、`save_fail_img` |
| 业务任务 | `Task/Fight/`、`Task/Infrast/`、`Task/Roguelike/`、`Task/Miscellaneous/`（含 `StartGameTaskPlugin.cpp`、`AccountSwitchTask`、`AutoRecruitTask`）、`Task/Reclamation/`、`Task/SSS/`、`Task/MiniGame/` | 战斗、基建、肉鸽、启动/切号、公招、生息演算、保全、小游戏 |
| 控制器（Android 原生） | `Controller/MaaFwAndroidNativeController.cpp` | 对接 `libMaaAndroidNativeControlUnit.so`（`connect`、`screencap`、`start_game`、`click` 等） |
| 资源加载 | `Config/ResourceLoader.cpp`、`Config/TemplResource.cpp`、`Config/TaskData.cpp` | 资源、模板、任务数据加载 |

### MaaFramework 控制单元（仅排 `START_GAME` / `force_stop` / 桥接调用时查）

- 上游：`https://github.com/MaaXYZ/MaaFramework` → `source/MaaAndroidNativeControlUnit/`（`API/AndroidNativeControlUnitAPI.cpp`、`Manager/AndroidNativeControlUnitMgr.cpp`），负责解析 `config`（`library_path`、`screen_resolution`、`display_id`、`force_stop`）并调用 `libbridge.so`。
