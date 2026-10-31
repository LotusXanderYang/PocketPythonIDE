# 阶段 8 C/C++ 调试接入记录（2026-10-29）

> 此文记录 10 月 29 日的 MAG-AN00 初次接入结果。MRO-W00 的后续修复、C++ 异常停止与当前 APK 见 [鸿蒙修复记录](STAGE8-CPP-DEBUG-HARMONY-FIX-2026-10-31.md)；下文“尚需验证”是当时的状态。

## 状态

在 MAG-AN00（ARM64、Android API 36、非 root）上，IDE 内置自检对 C 和 C++ 各自的两文件程序通过：`initialize`、`launch`、跨文件断点命中、调用栈（`helper` → `main`）、局部变量 `value=21`、单步、继续与正常退出。完整语言包自检的 `initialize`、`analyze`、`complete`、`diagnostics`、`build`、`run`、`debug`、`cancel`、`largeOutput`、`versionMismatch`、`crashRecovery` 均显示“通过”。首轮暴露调试器正常终止后仍保持 DAP 进程的问题，修复为 `terminated` 后关闭适配器并等待清理，第二轮通过。随后将自检扩为 C 与 C++ 双用例，第三轮也全通过。

实机原生探针另记录：LLDB 21.1.8 启动、`libprojectrunner.so` 加载调试产物、`helper.c:2` 断点命中；调用栈包含 `helper.c:2` 和 `main.c:4`；局部变量 `value=21`；`next` 返回 `reason=step`。原生探针曾用固定线程号 `1` 导致调用栈为空，已据真实 `stopped.threadId` 修复 IDE 与验证器。LLDB 本地 server 通过 `LLDB_DEBUGSERVER_PATH` 明确定位。

## 产物

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| `artifacts/stage8-debug-ide-d2c44535.apk` | 267,553 字节 | `d2c445354253746447797ff0aa6f452570a22d29e7e5d76b983f55d2438158e5` |
| `artifacts/stage8-debug-cpp-2d54bb91.apk` | 103,250,287 字节 | `2d54bb91f52164c804b926895f10bff2e175e8d0b6b94214c364dfa06d0809d0` |

两份均为调试签名候选 APK，已在 MAG-AN00 覆盖安装，未卸载或清除工作区数据。未创建 Release。阶段 5 C/C++ APK 在 `artifacts/` 保留，未覆盖。

## 实现与复现

- `packs/cpp/lldb-toolchain-lock.json` 固定 Termux ARM64 LLDB 闭包 30 包、版本、大小与 SHA-256；`probe/fetch_lldb_runtime.py` 与 `probe/extract_lldb_runtime.py` 准备归档；`probe/audit_debugger_closure.py` 检查 ELF 依赖。
- `prepare_lldb_runtime.py` 将 LLDB DAP/server 安排在 Android 原生库目录；`prepare_runtime.py` 将必要库装入校验过的工具链 ZIP。构建前仍要求本机已准备大体积、被 Git 忽略的二进制资产。
- `CppDebugSession` 从 IDE 的工作区 ZIP 建立私有副本，以 `-g -O0 -fno-omit-frame-pointer` 编译，启动 LLDB DAP，桥接 Binder socket，并把源码路径在工作区相对路径和包私有绝对路径之间转换。终止时清理子进程和目录。
- IDE 的 `DebugPanel` 使用 DAP 停止事件中的真实线程号；`PackValidator` 对 C/C++ 两种语言逐一验证调试操作。

## 尚需验证

1. C++ 抛出异常后的 `cpp_throw` 停止与异常信息；当前验收只覆盖正常退出。
2. 无断点运行时的手动暂停与不同设备上的真实线程号；当前验收覆盖断点停住后的继续与单步。
3. 用户在普通编辑器中反复启动和停止调试的触屏体验，以及跨安装重启后的断点持久化；内置自检已通过连续 C、C++ 会话。
4. Android API 26、16 KB 页设备及鸿蒙兼容环境。现有结论仅针对 MAG-AN00 API 36。
5. LLDB 新增依赖的许可证声明与二进制再发行资料需在正式 Release 前逐件审计；本轮没有发布。

工作区 Git 起始状态包含大量既有暂存、未暂存和未跟踪文件，本轮未执行 reset/clean 或全量暂存。较早的 `STAGE8-CPP-DEBUG-FEASIBILITY-2026-10-29.md` 是可行性原型当时的快照，阅读当前状态应以本记录和源码为准。
