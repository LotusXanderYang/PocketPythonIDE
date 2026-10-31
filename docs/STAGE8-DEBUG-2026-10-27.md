# 阶段 8：调试能力与实机可行性

日期：2026-10-27。只覆盖阶段 8；未执行阶段 9，也未发布 Release。设备为 MAG-AN00（原生 Android、ARM64、API 36、SELinux enforcing），ADB 序列号在本地测试时为 `AF3GUT4516002511`。阶段开始时 Git 工作区已有大量已暂存、未暂存和未跟踪文件，修改前在忽略目录保存了相关原文件快照 `.toolchain/checkpoints/stage8-before-debug-source-20261027.zip`；未做 reset、clean 或覆盖旧版 Release。

## 结论与支持矩阵

| 语言包 / 功能 | 阶段 8 结论 | 实机证据 |
| --- | --- | --- |
| Python：DAP 断点、继续、单步、局部变量、多文件调用栈、异常停止、退出 | 已支持，需安装并验证独立 Python 基础包 | 语言包 `debug` 自检通过；IDE UI 在 `stage8_main.py:3` 命中断点，单步至第 4 行，异常在第 5 行停止，输出 `stage8-result: 42`，会话结束。自检另验证 `helper.py` 与 `fixture.py` 两层相对路径、`number=21` 和异常退出码 1。 |
| Python：暂停、结束、恢复正常运行 | 已验证；暂停只在 Python 跟踪点响应 | 最终包的 `debug` 自检含**无断点暂停**与断开，MAG-AN00 通过；包的 `files/tasks` 为空。另在 IDE UI 退出暂停会话后，原 `main.py` 再运行输出 `Hello from Pocket Android IDE` 与 `[运行完成]`。长 C 扩展调用中的即时暂停未测试。 |
| 工作区断点迁移 | 已验证唯一锚点的行位移与改名 | 原 `stage8_main.py:3` 断点随顶部插入一行并改名自动映射到 `stage8_renamed.py:4`，实机启动再次停在第 4 行。歧义锚点显示未映射，不猜测位置。SAF 再同步走同一快照映射路径；未单独对每一种 SAF 提供者实测。 |
| C/C++：课程多文件构建、运行与编译诊断 | 维持阶段 5 已验证能力 | 阶段 8 两文件 `stage8_ptrace_main.cpp` + `stage8_ptrace_helper.cpp` 在 MAG-AN00 构建运行，输出 `stage8-ptrace:0`。 |
| C/C++：IDE 断点调试 | **不支持，不声明 `debug`** | 当前 APK 的锁定工具链没有 `lldb-server` / `gdbserver`，设备系统也未提供；没有 DAP 适配器和断点、单步、变量的同类实机测试。点击“调试”显示“此语言包不支持调试”。 |

## 适配器选择

调查了现有 [debugpy](https://github.com/microsoft/debugpy) 与最小标准库实现。debugpy 有完整 DAP、远程附加及成熟的客户端生态，但本项目固定的 Android CPython 运行时里没有已验证的 Android 发行物；引入其 pydevd 依赖、原生加速件、进程启动与隔离方式要另做移植和授权审计。阶段 8 先使用 `bdb`、`socket`、`select` 的小范围适配器，只实现实机验收所需 DAP 子集，不把它描述为完整 debugpy。协议顺序参照 [DAP 官方概览](https://microsoft.github.io/debug-adapter-protocol/overview.html)。后续若替换为 debugpy，须保持同样的路径、安全和实机门槛。

独立 Python 包在相同签名的 IDE 调用下，通过额外的 `IDebugPack` Binder 服务接收 ZIP 快照和 Unix socket FD。最初直接跨应用连接 abstract Unix socket 的设备结果是 `debug: 失败 · IOException: Permission denied`；改用 `ParcelFileDescriptor.createSocketPair()` 经 Binder 传递后，完整 `debug` 自检在 MAG-AN00 通过。宿主 API v1 的运行 Binder 未改；接口与错误处理见[调试协议](language-pack-api/v1/DEBUG.md)。适配器限制单帧 1 MiB，调用栈 40 帧、作用域变量 100 项、变量显示值 256 字符。调试期间 Python 包拒绝并发运行任务；调试完成或取消后删除私有任务副本。

## C/C++ 可行性与阻断项

两文件程序在语言包当前的实际执行模型（项目编译为 `.so`，由包内启动助手加载）中调用 `fork`、`PTRACE_TRACEME`、`PTRACE_CONT`，返回 `stage8-ptrace:0`。这证明本测试设备允许最基本的同应用子进程跟踪，**不能推断所有用户设备都允许，也不能等同于源码级调试**。

锁定的 Termux ARM64 仓库索引 `official-Packages.gz` 中有 `lldb 21.1.8-3`（归档 5,493,496 字节，SHA-256 `705b1a59918a2aedab0177ee0c8d51a1155faea681cb8a513b0ce25e406f574b`），但当前 C++ 工具链锁定集缺少它及 `libandroid-spawn`、`libedit`、`python`、`ncurses-ui-libs` 等依赖。已安装工具链 ZIP 中没有 `lldb-server` 或 `gdbserver`；设备 `which lldb-server gdbserver lldb` 无结果。当前 C++ 编译命令使用 `-O0`，未加 `-g`，运行产物在临时任务目录，任务结束即清理。需锁定并核对完整 Android ABI 依赖、可重建带 DWARF 的产物、验证调试服务进程权限与路径映射，再实现 DAP 桥接，并在普通未 root 设备完成与 Python 相同的断点、单步、变量、多文件、异常和退出测试。仅完成这些后才能在清单中声明 C/C++ `debug`。官方 [LLDB 构建文档](https://lldb.llvm.org/resources/build.html)指出 Android 侧功能重点是 `lldb-server`；不能把 PC 版 LLDB 复制进 APK 当作 Android 可用。

## 实机记录与复现

- 最终调试 APK：`artifacts/stage8-ide-002da5d0.apk`（237,134 字节，SHA-256 `002da5d0386c9496c93a7bfc416491546bf3cd740ac734d5da3a960de493ba4c`）；`artifacts/stage8-python-da8b2639.apk`（11,235,580 字节，SHA-256 `da8b2639bbc4b4d9ecf17c42005167f0fff84fb9d8569b51289f793852ec4605`）。两份均为本地调试签名，无 Release 发布。
- `python -u tools/test_pocket_debug_adapter.py`：主机侧 DAP 帧、断点、单步、变量、路径、异常、无断点暂停、断开与退出测试通过；它不替代实机证据。
- `python tools/verify-language-pack.py` 和 Python 清单的 JSON Schema 检查通过。
- MAG-AN00 上最终 Python 语言包“验证并启用”：`initialize/analyze/complete/run/diagnostics/debug/cancel/largeOutput/versionMismatch/crashRecovery` 全部通过。自检使用独立两文件样例和 DAP `stackTrace/scopes/variables/exceptionInfo`，再启动无断点循环验证 `pause` 和断开清理。
- IDE UI：断点命中、单步、异常暂停、结束；插行加改名后断点从第 3 行映射到第 4 行并实测命中；C++ 点击“调试”显示不支持提示；C++ 两文件探针输出 `stage8-ptrace:0`。
- 暂停会话退出后，包内 `files/tasks` 为空，原 `main.py` 正常运行。测试用设备文件和断点配置已经移除；原来的 `main.py`、Echo 文件和阶段 5 两个课程目录保留。演示步骤与源码见[阶段 8 示例](stage8-demo/README.md)。

## 风险与数据影响

Python `bdb` 在纯 Python 行上工作；长时间阻塞的本地扩展、子进程、表达式求值、热修改与多个同时会话不在本阶段范围。调试入口保存当前文件，再复制 SAF 工作区到临时镜像；调试中的写入不会回写到 SAF，避免覆盖用户文件。断点保存在 IDE 私有偏好设置内，只用于定位行；源文件或邻近内容变化后若无法唯一对应，会提示未映射。IDE 与 Python 包以覆盖安装方式更新，原应用数据未清除；已验证能力因 APK 变化需重新验证。未涉及 Lab 安装数据或旧版 beta.5。

阶段 9 的发布、签名和 Release 验收尚未开始。
