# 阶段 8：C/C++ 原生调试可行性原型（2026-10-29）

## 设备与现状

- MAG-AN00，ARM64，Android API 36；ADB 与 `run-as org.idecpp` 可用。
- 已有 `org.idecpp` 是 Clang 21.1.8 编译／运行包，不声明 `debug` 能力。当前 IDE 仅对经过验证且声明 DAP 的包显示调试能力。
- 阶段约束要求先验证 LLDB 进程权限、符号文件与真实用户程序，再开放 C/C++ 调试。不能用编译诊断冒充断点调试。

## 已完成的原型

1. 从 Termux 官方 ARM64 仓库锁定 `lldb_21.1.8-3_aarch64.deb`，SHA-256 `705b1a59918a2aedab0177ee0c8d51a1155faea681cb8a513b0ce25e406f574b`；`probe/inspect_lldb_package.py` 校验包哈希并审计 `lldb-dap`、`lldb-server` 和 `liblldb.so` 的 ELF 依赖。
2. 原型包只将 `lldb-server` 作为可执行原生库放入 APK，按既有等长规则将私有前缀 `com.termux` 改为 `org.idecpp`。在设备 `org.idecpp` UID 下启动成功，`version` 输出 `lldb version 21.1.8`。
3. 使用同 UID 的 `libprojectrunner.so` 作为目标时，系统进程表显示运行器为 LLDB server 的子进程且处于 `t` 停止状态。这证明至少该设备允许调试包跟踪自己拉起的子进程。
4. 不预启动目标时，经仅本机 ADB 转发发送 RSP `qSupported` 得到 LLDB server 的协议响应；`?` 返回 `E02`（没有选定目标），符合该原型会话的状态。

## 尚未完成，不能宣称支持调试

- 正式包需要把 `lldb-dap`、`liblldb.so` 及其依赖 `libpython3.14.so`、`libpanelw.so.6`、`libncursesw.so.6`、`libedit.so` 等打包并重定位，检查完整依赖闭包与许可证。现有 Clang 包没有这些文件。
- 需用 Binder 传递的本地 socket／管道把 DAP 连接到 IDE，保持工作区路径映射、编译调试符号、断点、继续、单步、栈、局部变量、异常和取消；不得依赖实验用的 INTERNET 权限或 ADB 转发。
- 还未用两文件 C/C++ 源码完成上述端到端实机测试。`pack_manifest.json` 保持原样，不声明 `debug`，IDE 仍只显示编译与运行。
- 首次带目标的本机 TCP RSP 握手超时；无目标握手成功。该差异需要在正式 DAP 原型中继续调查，不能以进程 `t` 状态代替源码断点验证。

## 清理与构建边界

- 实验 APK 使用与旧包相同的签名覆盖安装，未卸载、未清除工作区。测试后已重新覆盖安装此前的 `artifacts/stage5-cpp-659c31fc.apk`。
- 已移除实验性的 `src/debug/AndroidManifest.xml` INTERNET 权限、打入生产源码树的 LLDB server 原生库、ADB 转发和测试进程；设备查询未发现临时权限或进程。
- 保留可重复的包检查、提取和 RSP 探针脚本；下载的官方 deb 在忽略的 `.toolchain/stage8-probe` 下。`packs/cpp/build.gradle` 现固定沿用旧调试签名，防止将来生成无法覆盖安装的包。
- 未发布 Release，未将原型标为已支持的功能。

参考：[Android 官方原生调试设备条件](https://developer.android.com/studio/debug)、[Termux LLDB 打包清单](https://github.com/termux/termux-packages/blob/master/packages/libllvm/lldb.subpackage.sh)。
