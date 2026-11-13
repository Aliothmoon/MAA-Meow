# UITest 扩展加载最小探针

仅验证系统 UITest 是否接受我们编译的扩展，不提供触控服务。
`extension_c_api.h` 来自 OpenHarmony testfwk_arkxtest，保留 Apache-2.0 声明。

在加载期间读取 UITest 版本、初始化 LowLevelFunctions，并尝试写出
`/data/local/tmp/maa-uitest-probe.json`，随后退出。不注入触摸、不打开端口。

```sh
source /home/meizu/Maindome/harmony/env.sh
cmake -S experimental/harmony-uitest-bridge \
  -B "$MAA_HARMONY_HOME/build/uitest-bridge" \
  -DCMAKE_TOOLCHAIN_FILE="$DEVECO_SDK_HOME/default/openharmony/native/build/cmake/ohos.toolchain.cmake" \
  -DOHOS_ARCH=arm64-v8a -DOHOS_STL=c++_static
cmake --build "$MAA_HARMONY_HOME/build/uitest-bridge"
"$HDC" file send "$MAA_HARMONY_HOME/build/uitest-bridge/libmaa-uitest-bridge.so" /data/local/tmp/maa-uitest-bridge.so
"$HDC" shell uitest start-daemon singleness --extension-name maa-uitest-bridge.so
```

2026-11-13，VOL-AL00/API 26/UITest 7.0.0.1：编译成功；运行时因 XPM 校验被拒绝，
`dlopen` 返回 Permission denied，探针没有进入 OnInit。勿视作可用服务。

同机另测 HMNextAuto 附带的预编译 Hypium agent：1.2.2 能加载，默认使用域套接字，
不是客户端所用 TCP 8012；1.1.0 能加载且 TCP 8012 的 Driver.create 返回 Driver#0。
这里不打包第三方二进制，来源与 hash 见开发文档；还未证明签名来源、分发许可或生产可用性。
