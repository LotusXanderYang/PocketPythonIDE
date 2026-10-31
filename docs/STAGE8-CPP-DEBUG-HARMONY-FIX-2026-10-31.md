# 阶段 8 C/C++ 调试修复（2026-10-31）

## 用户报告

- MRO-W00 卓易通：语言包普通 `build`、`run` 等自检通过，`debug` 失败，Clang 报 `unable to make temporary file: No such file or directory`。
- 单文件 `main.c`：调试正常退出，控制台显示已提交 0 个断点和“已存断点未能映射到当前源文件”；用户原本在输出语句行设置断点。

## 原因与改动

- `CppProcess.run` 为普通构建设置 `TMPDIR`，独立的 `CppDebugSession` 编译路径没有设置。为调试编译器和 LLDB DAP 进程都指定任务私有目录为 `TMPDIR`。这与用户报告中的普通构建通过、调试构建失败一致；仍需鸿蒙实机复核。
- `BreakpointStore.gather` 仅扫描 `.py`，重映射 C/C++ 断点时找不到原文件，改为支持 `.c`、`.cpp`、`.cc`、`.cxx`。已有未映射断点保留文本锚点，下一次调试会重新映射。
- 无断点手动暂停时，从 LLDB 的 `threads` 响应选用实际线程 ID；DAP 桥接异常现在结束适配器并回传错误，避免通道异常后长时间等待。这两条尚无新的实机证据。

## 构建与待验收

离线 Gradle `:packs:cpp:assembleDebug :ide:assembleDebug` 构建成功。候选产物：
两份 APK 经 `apksigner verify --print-certs` 核对，签名证书 SHA-256 均为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`，与此前沿用的调试签名一致，可直接覆盖安装。

| 产物 | SHA-256 | 字节 |
| --- | --- | ---: |
| `artifacts/stage8-debug-ide-1b8f0f91.apk` | `1b8f0f917d7eac68119d13b0fc99daecb238c351083959fe32bcaf3b81e55467` | 267,553 |
| `artifacts/stage8-debug-cpp-716d9988.apk` | `716d9988438caf8be81826cf9160a344791f0a0f254ab4fc6c2632547d985aa6` | 103,277,991 |

HDC 已识别 MRO-W00 `3XTYD25B19200691`，ADB 没发现卓易通 Android 容器；HDC 无法读其私有数据，因此两 APK 需要用户在卓易通直接覆盖安装。下一步先“语言包 → org.idecpp → 验证并启用”，确认 `debug` 通过；再用简单 `main.c` 在 `printf` 行设断点，检查提交数量、是否暂停、调用栈和局部变量，然后继续退出并重复一轮。安装与实测反馈尚待用户。未发布 Release，未清除设备数据，未改动旧产物。

用户覆盖安装后，原先 Clang 临时文件错误已不再出现，但 `debug` 自检返回 `IOException: DAP launch:`，错误尾部为空。检查 `DapTransport` 后发现 IDE 只读取 DAP 响应顶层的 `message`，忽略了 LLDB 常用的 `body.error.format`。已修正错误提取并单独构建 `artifacts/stage8-debug-ide-error-f191cf43.apk`（SHA-256 `f191cf43547a646d0e9f532dd7f88e53e67704fddd697c108e68a4a6c11f0aff`）；等待用户覆盖安装后回报完整错误。此前“鸿蒙通过”仍未成立，不能据此宣称卓易通支持原生调试。

错误详情版 IDE 在 MRO-W00 上显示：`Cannot launch .../libprojectrunner.so: personality set failed: Operation not permitted`。LLDB 默认 `target.disable-aslr=true`，启动时会请求修改进程地址随机化；卓易通拒绝了这一步。依据 LLDB 的 `preRunCommands` 和 `target.disable-aslr` 官方文档，在 C/C++ 包的 DAP `launch` 请求中加 `!settings set target.disable-aslr false`，让目标保持系统 ASLR，避免这次 `personality()` 调用。新包 `artifacts/stage8-debug-cpp-aslr-b261427a.apk`（SHA-256 `b261427ab26eee0ff9ab0311328d3b2bcb49b5050d42e25fc28866cd9d23465a`）已编译；等待平板实测，不预先宣称成功。参考：[LLDB DAP launch 命令](https://lldb.llvm.org/use/lldbdap.html)、[LLDB target.disable-aslr 设置](https://lldb.llvm.org/use/settings.html)。

平板复测 `b261427a` 仍报告同一 `personality` 错误。LLVM 的 [DAP 测试修订](https://github.com/llvm/llvm-project/commit/2d26ef09fc87472cd42ea219c8f9267599872958) 明示 DAP `launch` 参数 `disableASLR` 会覆盖 `initCommands` 的设置；因此又显式加入 `"disableASLR": false`。新包 `artifacts/stage8-debug-cpp-aslr2-ba379c36.apk`（SHA-256 `ba379c3638a729ebffb89b1910cac95df47690b74acd3868f36c6eaec525d0f4`），等待平板实测。

用户在 MRO-W00 对 `ba379c36` 运行五次完整自检：第一次 `debug` 失败，提示“DAP 未收到 stopped”；后四次均通过。其后用户在新建的 `stage8_probe.c` 的 `printf` 行设置断点，首次与再连续两轮调试都停在该行，局部变量 `value=21`、继续后的 `probe:42` 正常；已有 `loop.c` 的运行中暂停、调用栈和停止也正常。首次冷启动未收到 stopped 的原因仍需复现，不能标为完全稳定。

控制台显示“已提交 5 个断点”是因 IDE 把同一工作区内其他语言的持久断点也提交给 C/C++ 调试器。已改为仅提交当前语言包接受的文件；C++ 使用 `cpp_throw` 异常断点；C/C++ 自检等待首次停止延至 45 秒，并在提前退出时报告事件。新 IDE `artifacts/stage8-debug-ide-final-bf9ab118.apk`（SHA-256 `bf9ab118fee104756453337f2b4c73163d870aad0a25e7deff06ffec8c5b5479`）已编译，等待用户覆盖安装后复验。未发布 Release。

## 最终实机反馈与后续候选

- 用户将 IDE `bf9ab118` 与 C/C++ 包 `ba379c36` 覆盖安装在 MRO-W00。`org.idecpp` 完整自检通过；`stage8_probe.c` 第 5 行断点再次命中，控制台现在显示只提交 **1** 个断点。
- `stage8_exception.cpp` 在三次重试中均于第 5 行以 `exception` 原因停止，调用栈顶部为 `__cxa_throw`；停止后能立即再次调试 C 样例。第一次试验显示第 6 行，后续三次显示第 5 行，因此异常源码定位仍存在一次行号差异，但异常停止本身已获实机验证。
- `loop.c` 的运行中手动暂停、调用栈和停止已由用户在同一平板验证。原生 Android MAG-AN00 上的 C/C++ 两文件、断点、单步、变量、继续与退出记录见 `STAGE8-CPP-DEBUG-INTEGRATION-2026-10-29.md`。
- 此后修复了调试构建阶段的取消路径：停止会终止正在运行的编译子进程，编译超时为 55 秒，异常输出过大时也会清理子进程。离线 Gradle `:packs:cpp:assembleDebug` 构建通过，候选 `artifacts/stage8-debug-cpp-cancel-4d76dca1.apk`，SHA-256 `4d76dca13e11672fb4a4eeabbac69c7601d45a371cd58e98e8cf85a1a15651db`，103,278,590 字节。`apksigner verify --print-certs` 显示其调试签名证书 SHA-256 为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`，可覆盖上一候选。
- 用户已在 MRO-W00 覆盖安装 `4d76dca1`，`org.idecpp` 完整自检通过，`stage8_probe.c` 第 5 行断点及 `probe:42` 输出正常。编译进行中点击停止的路径尚未被单独实机触发，因此只将其记录为已编译、常规调试未回归；不能宣称该取消边界场景已实测通过。
- 首次覆盖 `ba379c36` 后的五次自检中有一次未收到 `stopped`，后四次通过。此冷启动现象未能稳定复现，不能宣称零故障。未发布 Release，正式发布前仍需完成 LLDB 许可证与再发行审计，以及低 API 和 16 KB 页设备验证。

## 支持范围

| 环境 | 已验证 | 限制 |
| --- | --- | --- |
| MAG-AN00，原生 ARM64 Android API 36 | C/C++ 两文件断点、调用栈、变量、单步、继续和退出；完整语言包自检 | 编译中取消、C++ 异常未在该设备单独实测 |
| MRO-W00，鸿蒙卓易通 Android 兼容环境 | 完整自检、C 断点与变量、重复调试、手动暂停和停止、C++ 异常停止 | 首次覆盖后五次自检中有一次未收到 `stopped`；编译中取消未单独实测 |
| Android API 26 及 16 KB 页设备 | 未测试 | 不作调试兼容承诺 |

调试仅通过项目内的 ARM64 LLDB 和用户应用进程完成，不要求 root。调试包仍是本地候选 APK；公开 Release 与用户旧数据均未变动。
