# C/C++ 可调试语言包续作记录（2026-10-29）

## 目标与额度

用户要求完成可调试 C/C++ 包；优先将额度保护规则写入 `C:\Users\15940\.codex\AGENTS.md`，已完成。当前 5 小时额度窗口最后检查剩余约 6%，触线前停止高成本步骤。全局规则要求下次先读本记录和 Git 状态，不声称未验证功能完成。

## 已完成

- 官方 Termux ARM64 LLDB 21.1.8 闭包 30 包已锁定；15 个新增 `.deb` 哈希验证通过；`probe/audit_debugger_closure.py` 静态 ELF 依赖审计通过。二进制包在被 Git 忽略的 `.toolchain/stage8-probe/`，不能清理。
- C/C++ 包的 `toolchain.zip` 加入 LLDB 库，`liblldb_dap.so` 和 `liblldb_server.so` 放在 APK 原生库目录；`Toolchain.java` 提供路径。
- `CppPackService` 接入 `IDebugPack`；新 `CppDebugSession.java` 负责项目快照、DWARF 构建、DAP 代理、路径映射、退出清理。`DebugPanel.java` 使用真实线程号，`PackValidator.java` 验证 C 与 C++ 两文件断点、变量、栈、单步、退出。
- MAG-AN00（`AF3GUT4516002511`，API 36）上的 IDE 自检：`initialize`、`analyze`、`complete`、`diagnostics`、`build`、`run`、`debug`（C 与 C++ 两用例）、`cancel`、`largeOutput`、`versionMismatch`、`crashRecovery` 均通过。首轮清理问题已修复，后续两轮自检通过。原生探针也读到 `helper.c:2` 断点、双文件调用栈、`value=21`、单步事件。
- 最终候选 `artifacts/stage8-debug-ide-d2c44535.apk`（267,553 字节，SHA-256 `d2c445354253746447797ff0aa6f452570a22d29e7e5d76b983f55d2438158e5`）与 `artifacts/stage8-debug-cpp-2d54bb91.apk`（103,250,287 字节，SHA-256 `2d54bb91f52164c804b926895f10bff2e175e8d0b6b94214c364dfa06d0809d0`）。已覆盖安装到 MAG-AN00，数据未清除；最后一次 APK 仅移除临时探针 Activity 并更新 `versionName`，未对该哈希再跑完整自检。
- 详情见 `docs/STAGE8-CPP-DEBUG-INTEGRATION-2026-10-29.md`。未创建 Release。

## 工作区状态与保护

Git 主分支尚未产生提交，原有大量已暂存、未暂存和未跟踪文件（包括 `ide/`、`packs/`、`docs/`、`artifacts/`）；本轮未 reset/clean、未全量暂存。切勿把原有工作误当成此轮独占改动。本轮新增或改动集中在 `packs/cpp/` 的 LLDB 脚本、锁、工具链构建与服务，`ide/src/main/java/com/example/pocketpythonide/{DebugPanel,PackValidator}.java`，以及本记录和整合报告。大型 `.toolchain` 资产与生成的 APK 未入 Git；不要清理或覆盖。

## 后续工作

1. `git status --short`，核对上述文件与当前额度。
2. 再测最后候选 APK 的 IDE 内置自检，尤其 C 与 C++ 连续会话；用户工作区数据应保持。
3. 补齐 C++ `cpp_throw` 异常停止、运行中的手动暂停、普通编辑器反复调试、跨重启断点持久化；如失败则修正并重测。API 26、16 KB 页和鸿蒙环境尚无证据，不能泛称支持。
4. 审计新增 LLDB/Python 原生依赖的许可证及随 APK 声明，再考虑正式发布；用户未授权 Release，本轮不发布。
5. 若复编译：在工作区设置 `JAVA_HOME=.toolchain/zulu17.68.203-ca-jdk17.0.20.1-win_x64`、`ANDROID_HOME=.toolchain/android-sdk`、`GRADLE_USER_HOME=.toolchain/gradle-home`，运行 `.toolchain/gradle-8.13/bin/gradle.bat :packs:cpp:assembleDebug :ide:assembleDebug --offline --no-daemon`。本机受限沙箱的 Gradle daemon 本地端口需获已授权的 escalated 执行。ADB 路径 `.toolchain/android-sdk/platform-tools/adb.exe`，设备序列号如上。

## 风险

`CppDebugSession` 当前转发 DAP 消息时捕获并忽略桥接异常，若用户发送异常路径请求，可能等待到超时；应显式关闭适配器并回传错误。`DebugPanel` 无断点首次暂停仍使用默认线程号 1，LLDB 实际线程号不同；应从 `threads` 请求选取。发布前要修复这两项。包体积由阶段 5 的约 73 MB 增至约 103 MB。第三方依赖资产没有干净检出自动再现的完整证明，构建必须先准备锁定二进制。
