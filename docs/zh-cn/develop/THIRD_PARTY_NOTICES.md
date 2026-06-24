# 第三方代码声明

本项目包含来自以下开源项目的代码，按各自原始许可证进行分发。

## scrcpy

- **项目地址**：[Genymobile/scrcpy](https://github.com/Genymobile/scrcpy)
- **版权**：Copyright 2018 Genymobile
- **许可证**：[Apache License 2.0](../LICENSE-Apache-2.0)
- **原始代码**：[server/src/main/java/com/genymobile/scrcpy](https://github.com/Genymobile/scrcpy/tree/master/server/src/main/java/com/genymobile/scrcpy)
- **本项目中的位置**：[`app/src/main/java/com/aliothmoon/maameow/third/`](../app/src/main/java/com/aliothmoon/maameow/third/)

### 用途

这些代码用于在 Shizuku 用户服务进程中构造 Android `Context`，并通过反射访问 Android Hidden API，实现虚拟显示器管理、输入事件注入、屏幕信息获取等功能。

### 包含文件

| 文件 | 说明 |
|------|------|
| `third/FakeContext.java` | 伪造的 Android Context，用于在无 Activity 的进程中获取系统服务 |
| `third/Ln.java` | 日志工具，同时输出到 Android Logger 和标准输出 |
| `third/Workarounds.java` | Android 系统兼容性处理，构造 ActivityThread 和 Looper |
| `third/Command.java` | Shell 命令执行工具 |
| `third/IO.java` | I/O 工具类 |
| `third/Size.java` | 尺寸数据类 |
| `third/DisplayInfo.java` | 显示器信息数据类 |
| `third/wrappers/ServiceManager.java` | Android ServiceManager 反射封装，获取各系统服务实例 |
| `third/wrappers/DisplayManager.java` | DisplayManagerGlobal 反射封装，管理显示器信息和虚拟显示器 |
| `third/wrappers/InputManager.java` | InputManager 反射封装，注入输入事件 |
| `third/wrappers/WindowManager.java` | IWindowManager 反射封装，管理旋转、显示尺寸、IME 策略等 |
| `third/wrappers/ActivityManager.java` | ActivityManagerNative 反射封装，获取 ContentProvider、启动 Activity |
| `third/wrappers/PowerManager.java` | IPowerManager 反射封装，查询屏幕状态 |
| `third/wrappers/StatusBarManager.java` | IStatusBarService 反射封装，控制通知栏和快捷设置面板 |
| `third/wrappers/SurfaceControl.java` | SurfaceControl 反射封装，管理物理显示器令牌和电源模式 |

### 主要修改

以下是相对于 scrcpy 原始代码的主要修改：

- 调整包名从 `com.genymobile.scrcpy` 至 `com.aliothmoon.maameow.third`
- 移除了与屏幕录制、视频编码、音频采集相关的代码，仅保留系统服务反射封装部分
- 新增 `ActivityManager`、`SurfaceControl`、`StatusBarManager`、`PowerManager` 等封装类
- `DisplayManager` 增加了 `createNewVirtualDisplay()` 方法，用于创建独立虚拟显示器
- `WindowManager` 增加了 `captureDisplay()`、`setForcedDisplaySize()`、`clearForcedDisplaySize()` 等方法
- `Ln` 的日志 TAG 和前缀修改为本项目标识

---

## MaaAssistantArknights

- **项目地址**：[MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights)
- **许可证**：[AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html)
- **使用方式**：通过 `scripts/setup_maa_core.py` 下载预编译产物（`libMaaCore.so` 及资源文件），运行时由 JNA 动态加载
