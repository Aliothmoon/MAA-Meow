# HarmonyOS shell helper experiment

Status: **builds, but execution is denied on the tested retail phone**.
This is a research artifact, not a working phone backend or deployable app.

Build with the extracted OpenHarmony Native SDK (tested: 6.1.0.31, API 23):

```bash
cmake -S experimental/harmony-helper -B $HOME/Maindome/harmony/build/helper \
  -DCMAKE_TOOLCHAIN_FILE="$HOME/Maindome/harmony/linux/native/build/cmake/ohos.toolchain.cmake" \
  -DOHOS_ARCH=arm64-v8a -DCMAKE_BUILD_TYPE=Release
cmake --build $HOME/Maindome/harmony/build/helper
```

The executable accepts `PORT TOKEN_FILE`. The token file must contain at least
32 characters. It listens only on IPv4 loopback and expires after 300 seconds.
Each connection sends the token as its first line. After `OK auth`, supported
line commands are `status`, `capture`, `tap X Y`,
`swipe X1 Y1 X2 Y2 DURATION_MS`, and `stop`.
Commands invoke only fixed system tool paths with validated arguments; there is
no shell command interface. Capture writes `/data/local/tmp/maa-helper-frame.jpeg`.
Tool exit status does not establish that the game accepted an input event.
This single-client prototype has no availability guarantees: partial client
input can occupy it until its hard expiry. It is not production IPC.

Validation on 2026-11-13:

- ARM64 OHOS executable compiled with SDK clang 15.0.4, strict warnings enabled.
- Linux-host build tested authentication rejection/acceptance, status, invalid
  coordinate/duration rejection, rejection of arbitrary commands, and shutdown.
- On VOL-AL00 / 7.0.0.111 / API 26, hdc shell has UID 2000 and `u:r:sh:s0`.
- Upload + chmod 700 succeeded. Execution from `/data/local/tmp` returned
  `Permission denied`; `/data/local/tmp/debugserver` also denied execution.
- Therefore phone-side socket IPC, child process input permissions, disconnection
  survival, and HAP-to-helper access remain unverified.
- Uploaded executable copies, token and log were removed; no helper was started.

The next supported experiment is a signed debug HAP, rather than assuming chmod
is sufficient or modifying device security settings. A HAP does not itself grant
shell permissions.

[Official native debugging guidance](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/debug-lldb/)
