# 鸿蒙开发交接（2026-11-13）

## 当前状态

这是实验性移植，尚不能执行日常 MAA 任务。

- 真机：Pura X View / VOL-AL00，HarmonyOS 7.0，API 26，游戏运行于卓易通。
- 原生跨应用采集通过：2232×1320 RGBA，约 19 FPS，映射、格式和释放错误为零。
- 本机 HDC 授权及 UITest TCP 触控服务通过，拔掉 USB 后可操作。
- OpenCV + C++ 后台触控闭环通过：首页识别→设置→识别设置页→返回首页。
- MaaCore、MaaUtils、ONNXRuntime 已交叉编译并打包，真机加载通过：
  `2026-11-13T10:37:47.124Z MaaCore host registered: DEBUG_VERSION`。
- 加载闪退已修复：CMake 缓存中的 ZLIB_LIBRARY_RELEASE 原来选中了动态 zlib，
  现在显式链接 libz.a；页面增加异常处理。

尚未验证 AsstLoadResource、AsstCreateEx、AsstConnect、真实任务和模型推理。
普通 click 在游戏内不响应，目前使用 longClick。精确拖动、暂停拖动和战斗部署未支持。
2232×1320 不是 16:9；experimental_stretch_16_9 仅为实验配置，需要验证识别和坐标。

## 换开发电脑

仓库包含源代码、锁定版本、补丁、识别模板和构建脚本，不包含 SDK、编译产物、
签名私钥、证书、设备日志、截图或本机 HDC 身份。Git clone 本身不会恢复工具链。

最快方式是另外安全复制旧电脑的 `~/Maindome/harmony` 到新电脑相同位置。
其中 signing 和设备身份属于私人材料，不要上传公开仓库；新测试手机还需加入调试
Profile 并重新生成 Profile。旧设备上的信任授权也不能假定对新设备有效。
新电脑架构或工具路径变化时，重新生成 CMake/Hvigor 缓存并构建。

从零搭建时：

1. 安装 Command Line Tools（已验证 26.0.0.865 / SDK 26.0.0.105）到
   `~/Maindome/harmony/command-line-tools`，JDK 21 到 `~/Maindome/harmony/jdk21`。
2. 复制 `experimental/harmony-core/env.sh.example` 为
   `~/Maindome/harmony/env.sh`，创建其中 tmp/cache 目录，source 后使用工具。
3. 根据 `experimental/harmony-core/sources.json` 克隆 core、maadeps 到
   `~/Maindome/harmony/research/`，检出锁定 commit；初始化 core 的 MaaUtils
   和 maadeps 的 vcpkg 子模块，并核对锁定版本。运行 vcpkg 的 bootstrap-vcpkg.sh。
4. 按 `scripts/build_harmony_probe.py` 中 required 列表准备仓库外 signing 文件，
   证书需绑定当前包名及测试设备。
5. 在仓库根目录依次运行：

```bash
python3 scripts/build_harmony_deps.py
bash scripts/build_harmony_core_deps.sh
bash scripts/build_harmony_core.sh
python3 scripts/build_harmony_probe.py --install
```

HAP：`~/Maindome/harmony/build/maa-harmony-probe-signed.hap`。
日志和运行证据原本在 `~/Maindome/harmony/artifacts/probe`，不会通过 Git 转移。

## 接手后的下一步

先确认“检查 MaaCore 加载”显示 host registered。接着增加异步 NAPI 探针，
依次验证资源加载、创建实例、Harmony 连接、取帧，再执行一个可恢复的最小导航任务。
独立 OpenCV 闭环通过不能视为 MaaCore 任务通过。具体接口在
`experimental/harmony-core/HarmonyHost.h`，实现与构建说明在同目录 README。

历史排查过程见 HARMONYOS.md 和 HARMONYOS-CONTROL-FEASIBILITY.md；其中早期
“尚未接入”等描述是当时状态，当前状态以本文件为准。
