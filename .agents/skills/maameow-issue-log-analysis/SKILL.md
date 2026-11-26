---
name: maameow-issue-log-analysis
description: >
  分析 MaaMeow（Aliothmoon/MAA-Meow）的 GitHub Issue 或本地 `maa_logs_*.zip` 日志包。
  下载附件后交叉比对 device_info.txt、app_settings.txt、gui/meow_log、error_logs（含 Process exit）、
  crash_logs、schedule/trigger、service_bind/boot/launch debug log、logcat/core|app、asst.log、
  interface 截图及 Sentry 遥测，对照双进程（App + Shizuku/Root 提权）、虚拟屏（Legacy/VDM）与
  MaaCore/MaaFramework/bridge 代码定位根因。
  Use when analyzing MaaMeow/MAA-Meow issues, log zips, task failures, service death, CALL_STUCK,
  Shizuku/Root elevation, virtual display black screen or VDM/Legacy fallback, Process exit kills,
  recognition errors, connection init failures, or scheduled tasks that were missed or skipped.
---

# MaaMeow Issue / Log Analysis

仓库架构与包边界以根目录 `CLAUDE.md` 为准；本 skill 只保留**排障工作流与索引**，细节拆在 `references/` 下按需读取。与当前 checkout 源码冲突时，一律以源码和 `CLAUDE.md` 为准。

## Scope & Core Mental Model

- 适用对象：`https://github.com/Aliothmoon/MAA-Meow` 公开 Issue（URL 或 `#1234`）或本地 `maa_logs_*.zip` / 解压目录。指向其它仓库时停止并说明不适用。
- **双进程日志归属**（先分清日志出自哪边）：
  - **App 进程**：Timber `FileLogTree`（`error_logs/`，含 `ProcessExitRecorder` 记的系统退出原因）+ `MaaSessionLogger`（`gui/meow_log_*.log`）+ `ServiceBootLogger`（`service_bind_debug.log`）+ `ScheduleTriggerLogger`（`schedule/trigger_*.log`）+ `CrashHandler`（`crash_logs/`）。
  - **提权服务进程**（Shizuku / Root 均经 `liblauncher.so` 降权至 shell uid=2000 起 `app_process` → `RemoteServiceStarter`，无 Shizuku `bindUserService`）：`Ln`（logcat tag `MaaMeow` + stdout/stderr）+ `RemoteBootTrace`（`service_boot_debug.log`）+ MaaCore（`asst.log` / `interface/` / `crash.log`）。Timber 在提权进程静默 no-op。
- **两个数据目录**（以 `device_info.txt` 的 `Core Dir` 为准）：
  - `APP_DIR`（默认）：App 与 core 共用 `Android/data/<pkg>/files/Maa/`。
  - `LOCAL_TMP`：core 在 `/data/local/tmp/maameow`，导出时经 binder 拉进 zip 的 `remote/`；导出时若提权服务不在线则 `remote/` 整体缺失。
- **默认无 MaaCore C++ 源码**：内核行为先看 `asst.log`，必要时再按 [references/code-map.md](references/code-map.md) 对照上游 `MaaAssistantArknights` / `MaaFramework`。

## Workflow

1. **规范化输入并获取附件**
   - Issue 编号用 `gh issue view <n> --repo Aliothmoon/MAA-Meow --comments` 拉取正文与评论；提取 `maa_logs_*.zip` 链接后 `curl -L` 下载并解压到 `.cache/issue-logs/issue-<n>/`（勿提交）。本地路径直接跳过此步。
   - 若无日志附件，先明确指出证据不足，再基于文本/截图/代码给初步判断。
   - 维护者评论只作线索，仍须用日志和代码自行验证。
2. **盘点目录与锚点（先读快照，别信口述版本号）**
   - 列出解压后的顶层与子目录，先读 `device_info.txt`、`app_settings.txt`、`export_skipped.txt`。
   - **区分「导出时版本/设置」与「事发时版本/设置」**：`device_info.txt` 只是导出那一刻的快照；事发时的真实版本用 `asst.log` 的 `Version v6.*`、`error.log` 调试头和 Sentry `release` 复核。
3. **串联时间线，锁定「本次复现」**
   - 用 `service_bind_debug.log`（`SESSION` / `SERVICE_PID` / `STATE_DIED`）串起 App 与提权进程生命周期。
   - 用 `SERVICE_PID` 对齐 `asst.log` 的 `[Px<pid>]`、`error.log` 的 `Process exit [self|game]` 以及 `meow_log_*.log`（epoch 毫秒换算本地时间）。
   - 若 `asst.log` 显示该轮实际 `AllTasksCompleted`，明确写「本日志未复现用户描述的失败」。
4. **按问题类型读取对应参考与重点日志**
   - 详细文件格式、裁剪规则、状态码与关键字见 [references/log-map.md](references/log-map.md)。
   - 高频根因模式与排障路径见 [references/common-patterns.md](references/common-patterns.md)。

   | 问题类型 | 首选日志源 | 辅助日志源 |
   |---|---|---|
   | 任务失败 / 识别错误 | `asst.log` + `interface/*.png` | `meow_log`（含 `[TaskParams]`） |
   | 服务死亡 / 卡死重启 | `service_bind_debug.log`、`error.log`、`crash.log`、`logcat/core` | `meow_log`、`asst.log` 尾部、Sentry `ServiceDied`/`RemoteCallStuck` |
   | 提权进程拉不起来 / 超时 | `service_bind_debug.log` → `*_launch_debug.log` → `service_boot_debug.log` | `error.log`、`device_info.txt`（Backend / Shizuku） |
   | 后台虚拟屏黑屏 / 卡顿 / 漂移 / 唤醒无效 | `logcat/core`、`asst.log`（`start_game` 耗时与 OCR）、`interface/*.png` | `error.log`（`Process exit`）、`app_settings.txt`（`virtualDisplayBackend` / `closeAppOnTaskEnd`）、Sentry `vd.backend`/`vd.tier` |
   | 游戏进程中途消失 / 被杀 | `error.log`（`Process exit [game]`）、`meow_log`（`退出原因`） | `service_bind_debug.log`（排除提权死亡连带销毁 VD） |
   | 资源加载 / 初始化 / 连接失败 | `asst.log`、`meow_log`（footer `status`） | `error.log`、`service_boot_debug.log`、`logcat/core` |
   | 定时任务漏触发 / 跳过 / 解锁失败 | `schedule/trigger_*.log` | `device_info.txt`（`Battery Opt`、`Schedule`）、`app_settings.txt`、`logcat/core`（`WakeUnlock`） |
   | App 崩溃 / ANR / UI / 权限 | `crash_logs/`、`error.log` | `logcat/app`、Sentry（路由/FGS/ANR 栈） |

5. **Sentry 交叉查证（可选，有权限时强烈推荐）**
   - `device_info.txt` 的 `Telemetry ID` = Sentry `user.id`；`meow_log` 的 `[Telemetry] run_id=` = 单轮 run id。
   - 四个高频用途：
     1. 搜 `span.op:maameow.run` 还原该设备连续真实的版本升级曲线（排除用户记错版本）。
     2. 没开 `debugMode` 缺 `logcat/core` 时，直接从事件 tag 读 `vd.backend`、`vd.tier`、`rom`、`rom.version`、`screen_power_off`、`failure.node`、`shizuku.identity`。
     3. 提权进程意外死亡时，查 `ServiceDied` 事件是否有 `ServiceDeathForensics` 补传的系统 logcat（`forensics.delay_s`）。
     4. 查 `RemoteCallStuck`（附 `ThreadDump`）、`LaunchFailure`（`MISSED` / `SKIPPED_LOCKED`）、`ShizukuDown`、崩溃路由与 FGS 时间线。
   - 无权限时跳过，并在「缺失的证据」里写明。上报口径见 `docs/zh-cn/develop/TELEMETRY.md`。
6. **回溯源码并核对版本差异**
   - 代码索引见 [references/code-map.md](references/code-map.md)。
   - 旧版本 issue 必须用 `git show <tag>:<path>` / `git log <tag>..HEAD` 区分「事发版本的根因」与「当前主线是否已修复」，禁止用主线代码直接否定旧版问题。

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

1. 代码层修复（指明具体文件和改动；遵守 `domain` 不依赖 `presentation` / `data`；无实证的防御性改动须标明回归风险）
2. 需要补充的日志或测试
3. 如属于不支持场景，说明如何限制入口或改进提示

## 给用户的建议

- 用户现在可以尝试的操作：
- 是否建议升级 / 重装 / 同步资源 / 重置配置 / 切换后端或数据目录：
- 是否需要等待开发者修复：
- 是否有临时绕过方案：

## 置信度

- 高 / 中 / 低
- 还缺什么证据
```

只列实际读到的日志源；包里没有的文件写进「缺失的证据」，不要留空行凑数。

## Reminders

- 交叉验证多个日志源，不凭单一文件或维护者评论下结论；`meow_log` 是摘要，识别/任务逻辑以 `asst.log` 为准。
- 严格区分**已验证事实**与**推断**；结论依赖缺失信息（如没开调试模式缺 `logcat/`）时直接请用户补充，不猜测。
- 缺 `logcat/` 先看 `app_settings.txt` 的 `debugMode`；缺 core 侧文件先查 `Core Dir`、`remote/`、`export_skipped.txt`。
- 日志与用户描述不一致时，明确写明「未复现」还是「已复现但用户表述/版本记忆有偏差」。
- 判为「设备/ROM 不兼容」或「不支持」必须给出代码级依据；只摘取支撑结论的关键片段，不倾倒整份日志。
- 代码引用在 GitHub 场景用 blob 链接（`https://github.com/Aliothmoon/MAA-Meow/blob/<sha>/...#L..`，上游同理）；本地对话可用 `file:///` 链接。
- 用户可见文案以 `values/strings.xml`（中文源）/ `values-en` / `values-ko` 为准，不甩内部 key。
