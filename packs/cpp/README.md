# Pocket C/C++（0.2.0 Alpha 语言包）

这是独立安装的 ARM64 Android Language Pack API v1 APK，包名 `org.idecpp`。它在自己的 `:compiler` 服务进程中运行 Clang/LLD/LLDB 21.1.8。IDE 基础 APK 不含编译器或调试器。目标是课程中的单目标 C/C++ 项目；不提供交互式终端、任意第三方库管理或可导出的安装包。

## 使用

1. 安装阶段 4 及更新版 IDE，再安装本语言包 APK。安装包不需要网络权限，也不会在首次运行时联网下载工具链。
2. 在 IDE 左侧“语言包”选择 `org.idecpp`，查看来源、签名、APK 哈希及权限，点击“验证并启用”。自检逐项覆盖初始化、语法检查、补全、诊断、构建、运行、C/C++ 跨文件调试、取消、大输出、崩溃恢复和协议错误。安装或更新后须重新验证。
3. 通过“文件 → 打开文件夹”选择可写 SAF 工作区。打开 `.c`、`.cpp`、`.cc` 或 `.cxx`，可使用“构建”“运行”“检查当前文件”。输入标识符时，IDE 从本地轻量词表和当前文件声明立即给出候选，在光标旁显示与 Python 编辑器相同的补全弹窗；“文件 → 补全建议”会继续向已验证的语言包请求项目级候选，并更新同一个弹窗。↑↓ 选择、Enter 插入，Ctrl+K 或点击弹窗底部关闭。补全初版仅覆盖关键字、常用标准库名称和课程项目源码中的声明，不提供 LSP 语义推断。语言包请求在后台执行，使用临时源码快照，不会自动保存或写回 SAF 工作区。
4. 不带配置时仅编译当前源文件。多文件课程项目在入口文件同目录或工作区根目录放 **`.pocket-cpp.json`（文件名开头必须有英文句点 `.`）**。配置中的路径相对于所选 SAF 工作区根目录。若误命名为 `pocket-cpp.json`，IDE 会按单文件编译，运行时可能报缺少另一个源文件中的符号。例如本目录下的 [C 样例](examples/stage5_c_course/.pocket-cpp.json) 与 [C++ 样例](examples/stage5_cpp_course/.pocket-cpp.json) 都在根目录 `PocketIDEStage5` 的两个子目录中：

```json
{
  "standard": "c17",
  "sources": ["stage5_c_course/main.c", "stage5_c_course/answer.c"],
  "includeDirs": ["stage5_c_course"],
  "stdin": "21\n",
  "args": []
}
```

`standard` 当前支持 `c11`、`c17`、`c++17`、`c++20`。一次配置只接受同一种语言的源文件，最多 64 个源文件、16 个包含目录。`stdin` 是非交互式 UTF-8 输入，最多 64 KiB；`args` 最多 16 项。项目文件来自 IDE 的工作区 ZIP 快照，先解压到包的私有任务目录；SAF URI 不会传给 Clang。构建的 `.so` 只在任务目录中使用，任务结束即清理，API v1 尚不支持导出构建产物。编译器输出、程序标准输出和标准错误写入 IDE 输出面板；标准错误流只标记一次。编译错误回传首个源码行列，非零退出码由任务结果记录，55 秒截止或用户取消会杀掉子进程。

## Android 可执行文件路径

目标 `targetSdk=35`，最低 API 26。新编译的普通 ELF 不能直接从应用私有可写目录执行（Android 10 起面向目标 API 29+ 的限制）。本包把 Clang、LLD 和小型运行器作为 APK native libraries 安装在系统管理的只读 `nativeLibraryDir`；课程项目编译为 PIC 共享库，运行器经 `dlopen` 加载任务目录中的产物并调用 `main`。这条路径已在 MAG-AN00、Android API 36 上实测。它不等同于普通 Android 命令行程序：应用私有目录不能作为通用 `$PATH`，进程内加载的第三方代码仍与语言包 UID 共享权限。C++ 程序使用随包提供的 Android `libc++_shared.so`。

## 工具链来源与复现

`toolchain-lock.json` 固定 Termux 官方 ARM64 APT 仓库中 15 个包的版本、SHA-256 和归档大小。下载时使用 `probe/fetch-locked-toolchain.ps1` 逐包核验。仓库索引 `InRelease` 的签名在 MAG-AN00 上经 Termux 的 `termux-autobuilds.gpg` 验证，签名密钥指纹 `CC72CF8BA7DBFA0182877D045A897D96E57CF20C`；`Packages.gz` 摘要为 `eb14f03facaed9678e8b9b9c2bdf78ece1dd36740297cd46e12341ed5af177e2`。索引锁定脚本 `probe/lock_official_toolchain.py` 还会核对这个摘要。

在 Windows 工作区执行：

```powershell
./packs/cpp/probe/fetch-locked-toolchain.ps1
python ./packs/cpp/probe/extract_locked_toolchain.py
python ./packs/cpp/prepare_runtime.py
python ./packs/cpp/generate_manifest.py
./packs/cpp/build-native.ps1 -Ndk <Android NDK r30 目录>
./.toolchain/gradle-8.13/bin/gradle.bat --offline --no-daemon :packs:cpp:assembleDebug
```

`build-native.ps1` 只交叉编译两个很小的启动助手；真正的 Android ARM64 Clang 和 LLD 来自锁定的 `.deb`，不是 PC 版二进制。没有 NDK 的开发机可在有 Termux 21.1.8 工具链的 ARM64 测试设备上运行 `probe/build_native_helper.py` 重建助手。两个助手的 C 源码均在本目录。`toolchain.zip` 与大型原生文件由脚本生成并被 Git 忽略，APK 离线包含它们。首次启动校验 ZIP SHA-256 后解压；升级时先在私有 `usr.next` 校验新版本，再原子切换，失败时保留旧版。

阶段 8 还须执行 `python packs/cpp/probe/lock_lldb_runtime.py`、`python packs/cpp/probe/fetch_lldb_runtime.py`、`python packs/cpp/probe/extract_lldb_runtime.py`、`python packs/cpp/prepare_lldb_runtime.py`，再运行 `prepare_runtime.py`。`lldb-toolchain-lock.json` 固定 30 包的 ARM64 闭包，`probe/audit_debugger_closure.py` 检查打包 ELF 依赖。调试时 C/C++ 项目加 `-g -O0 -fno-omit-frame-pointer`，LLDB DAP 和 server 均由包内原生库目录启动；调试会话使用项目 ZIP 副本，退出后清理。MAG-AN00（Android API 36）已通过 C 与 C++ 两文件断点、单步、局部变量、调用栈和退出的 IDE 内置自检。MRO-W00 卓易通已验证 C 断点、变量、重复调试、运行中暂停与停止，以及 C++ 异常停止；首次覆盖后曾有一次自检未收到 `stopped`，后续通过。API 26、16 KB 页设备和编译进行中取消尚待实测，详情见[阶段 8 鸿蒙修复记录](../../docs/STAGE8-CPP-DEBUG-HARMONY-FIX-2026-10-31.md)。

## 体积与范围

阶段 8 LLDB 闭包的 `.deb` 总量为 **101,217,452 字节**；已在 MRO-W00 验证的当前 C/C++ 调试 APK 为 **103,278,590 字节**，IDE APK 为 **267,553 字节**。调试器使语言包明显增大，且设备私有工具链也需要更多空间。若要缩小体积，需定制只含 AArch64 后端的 LLVM/LLDB 并重新完成许可证与实机回归。

许可证和完整依赖清单见 APK 内 [第三方声明](src/main/assets/THIRD_PARTY_NOTICES.md) 与 `src/main/assets/licenses/`。已验证 APK 在 [v0.2.0-alpha.1 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1)，哈希与兼容条件见[静态语言包目录](../../docs/language-pack-index.json)。
