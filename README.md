# Pocket Android IDE / Python · PyTorch Lab

当前开发工作区有两个独立 Android 应用：通用 [IDE](ide/) 和 [Python · PyTorch Lab](lab/)。IDE 可安装经验证的语言包；阶段 5 的 [C/C++ 包](packs/cpp/)已在 ARM64 Android 实机完成课程样例、编译错误、取消、超时等验证。阶段 6 的独立 [Python 基础包](packs/python/)已在 MRO-W00 的 Android 兼容环境通过实机验收；范围见[兼容说明](docs/python-pack-compatibility.md)及[阶段 6 验收](docs/STAGE6-ACCEPTANCE-2026-10-25.md)。Lab 的阶段 7 离线 CPU 训练流程已在 MAG-AN00 实机完成验证；使用方法和支持边界见 [Lab 说明](lab/README.md)与[阶段 7 验收](docs/STAGE7-ACCEPTANCE-2026-10-25.md)。阶段 8 的 Python 包 DAP 调试在 MAG-AN00 上通过断点、单步、变量、多文件、异常与退出测试；C/C++ 调试已在 MAG-AN00 与 MRO-W00 验证断点和调用栈，MRO-W00 还验证了运行中暂停及 C++ 异常停止，详见[阶段 8 C/C++ 实机记录](docs/STAGE8-CPP-DEBUG-HARMONY-FIX-2026-10-31.md)。此前进度和阶段 5 证据见[阶段性报告](docs/PROGRESS-REPORT-2026-10-25.md)及[阶段 5 验收](docs/STAGE5-ACCEPTANCE-2026-10-25.md)。

调试 APK 可在[本地 artifacts 目录](artifacts/)找到；旧版 `v0.1.2-beta.5` 仍是公开 Release 和回退锚点，新版调试包尚未发布。IDE、Lab 和旧版使用不同包名；覆盖安装新版 IDE 请保留原应用数据，不要先卸载。不同应用的私有脚本不会自动互通，SAF 工作区需分别授权。语言包协议见 [API v1](docs/language-pack-api/v1/README.md)，SDK 源码与调试 AAR 见 [pack-sdk](pack-sdk/README.md)。

新版的安装顺序、各 APK 的精确大小与哈希、签名、实测范围和发布前缺口见[阶段 9 清单](docs/STAGE9-RELEASE-PREFLIGHT-2026-10-31.md)。[IDE 使用说明](ide/README.md)、[Lab 使用说明](lab/README.md)、[Python 包](packs/python/README.md)与[C/C++ 包](packs/cpp/README.md)分别列出各自的能力和限制。公开[静态语言包目录](docs/language-pack-index.json)目前为空；本地测试候选不会伪装成已发布下载。

第三方运行时的固定归档、构建配方、许可证及源码获取途径见[第三方源码指引](docs/THIRD-PARTY-SOURCE.md)。

## 已发布旧版：PocketPythonIDE 0.1.2 Beta

面向 Android 平板的离线 Python IDE。内置 ARM64 版 **CPython 3.14.6、PyTorch 2.14.1（CPU）和 NumPy 2.5.4**，在设备本地执行脚本。界面采用深色工作区、文件侧栏、标签页、代码小地图和底部输出面板；应用标志与图标由本项目设计。

> 当前是测试版。只支持 `arm64-v8a`、Android API 26 及以上；尚未提供 GPU 后端、`torchvision`、完整语言服务或任意桌面 Python wheel 的直接安装。

## 下载

从 [v0.1.2-beta.5 测试版](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.1.2-beta.5) 下载 APK。此版本修复了部分鸿蒙兼容环境中创建 `.py` 文件时被自动补上 `.txt` 的问题，并改进了编辑器输入。测试 APK 使用调试签名，适合在自己的设备上试用。首次打开 Python 环境时，应用会核对内置运行时的 SHA-256 并解包；后续运行直接使用已解包内容。

APK 的 Python/PyTorch 运行时约 165 MB。为避免将大文件写入 Git 历史，`runtime.zip` 放在同一 Release 的附件中，仓库保留 [哈希与依赖清单](app/src/main/assets/runtime.json)。

## 功能

- 在“文件 → 打开文件夹”中选择 Android 文档提供者里的文件夹，授予持久读写权限，将其作为工作区。资源管理器显示嵌套目录与文件，可新建文件、文件夹，编辑并保存常见文本文件。关闭文件夹后仍可使用应用内部的 `files/scripts` 目录。
- 部分鸿蒙设备的安卓兼容环境会在文件选择器中同时显示原生鸿蒙目录和兼容环境目录。MRO-W00（OpenHarmony 6.1.1.120）上，原生“我的平板/个人/PythonProject”目录能浏览，但文档提供者拒绝新建文件，系统创建文件窗口也无法保存；兼容环境的 `Android` 目录可以创建、编辑和保存 `.py` 文件。请在文件选择器中选可写的兼容环境目录作为工作区。
- 运行工作区内的 `.py` 文件时，IDE 将项目复制到应用私有运行目录，支持同目录模块、包和项目根目录导入；执行后将新增或修改的文件写回所选文件夹。若外部文件同时发生变化，IDE 跳过覆盖并在输出面板报告冲突。
- 行号、基础语法着色、当前行提示、小地图、资源管理器和运行输出面板。
- 输入 Python 标识符时弹出离线代码提示：关键字、内置函数、当前文件的函数/类/变量/导入名，以及已导入的 `torch`、`numpy` 和常见标准库模块成员。`p` 会优先显示 `print`；点按候选或用实体键盘方向键、Enter 插入。Tab 始终插入四个空格，选中多行时整体缩进，Shift+Tab 减少缩进。内置函数会插入括号并将光标放在括号中。注释和字符串内不触发提示。当前补全依靠轻量静态索引，不提供跨文件类型推断、签名文档或完整的 PyTorch API 清单。
- 输入左括号、左方括号、左大括号、单引号或双引号时自动补全另一半；选择文本后输入这些符号会包围所选文本。输入右侧符号时可越过紧邻的匹配符号。回车继承当前行缩进，代码块冒号后增加四个空格。上方“查看 → 字体大小”提供 10–32 sp 调节与预览，设置会保存；窄屏可从左下角设置按钮打开。
- 实体键盘支持 `Ctrl+S` 保存，`Ctrl+Enter` 或 `F5` 运行，`Ctrl+Space` 手动触发当前前缀的代码提示。
- “环境自检”在设备本地检查 PyTorch 导入、矩阵乘法、自动求导、NumPy 桥接及文件读写。
- 升级安装保留原有脚本。应用只修复内容完全匹配旧版自动生成模板的示例 `main.py`，不会改写用户编辑过的脚本。

## 从源码构建 APK

需要 JDK 17、Android SDK Platform 35、Android Gradle Plugin 8.13、Gradle 8.13 和 PowerShell 7。仓库内的 JNI 桥已包含 ARM64 `.so`；仅当修改 C 代码时才需要 Android NDK r30。

```powershell
git clone https://github.com/LotusXanderYang/PocketPythonIDE.git
cd PocketPythonIDE
.\tools\fetch-runtime.ps1   # 若本地尚无已校验的 beta.5 runtime.zip
.\build-local.ps1 -Product ide   # 不打包 Python/PyTorch 运行时
.\build-local.ps1 -Product lab   # 从 beta.5 运行时生成 Lab 专属前缀，再单独构建
.\build-local.ps1 -Product sample # 构建 API v1 示例包及权限测试包，不重建 Lab
.\build-local.ps1 -Product python # 验证固定运行时并构建独立 Python 基础包
.\build-local.ps1 -Product all   # 构建 IDE、Lab、Python 包和阶段 3 的两个测试 APK
```

当前工作区的调试产物分别位于 `ide/build/outputs/apk/debug/ide-debug.apk` 和 `lab/build/outputs/apk/debug/lab-debug.apk`。下方 beta.5 的旧版说明仍适用于该 Release 标签；若要复现旧版，请在**另一个目录**检出 `v0.1.2-beta.5` 并使用其构建说明，不要覆盖本工作区已有缓存或 APK。

```powershell
# 以下两行仅适用于单独检出的 beta.5 标签源码
.\tools\fetch-runtime.ps1   # 下载 Release 中的运行时并按 runtime.json 校验
.\build-local.ps1
```

beta.5 产物位于 `app/build/outputs/apk/debug/app-debug.apk`。构建脚本优先使用工作区 `.toolchain/` 中的已安装工具；公共克隆可通过 `JAVA_HOME`、`ANDROID_HOME` 和 Gradle wrapper 使用系统工具。调试签名在每台开发机上可能不同，跨签名升级时须先导出自己的脚本。

如要从源代码重新编译 Python/PyTorch，而不是使用 Release 的运行时附件：

1. 在 Linux 或 WSL2 安装 Docker、Git、Python 3；运行 `tools/setup-linux-builder.sh`。
2. `tools/build-modern-runtime.sh` 会检出固定的 [Termux packages 提交](https://github.com/termux/termux-packages/commit/6be420e7536d1e228f3ffee2eb74ca2736a73fc1)，应用 [续编补丁](packaging/termux/python-torch-resume.patch)，以应用专属前缀构建 ARM64 `.deb`。该步骤需要较长时间和充足磁盘/内存。
3. 将目标 `.deb` 放到 `.toolchain/runtime-debs/`，九个纯 Python wheel 放到 `.toolchain/pure-wheels/`；按 [固定哈希清单](packaging/pure-wheels.sha256)核对 wheel，然后在 WSL2 运行 `tools/assemble-modern-runtime.sh`。
4. 在 beta.5 标签源码中运行 `.\build-local.ps1`。组装器会审计包版本、应用前缀、Python ABI、依赖闭包及 ARM64 ELF 的 16 KB 装载对齐，并生成 `runtime.json`。当前双应用 Lab 的等长包名前缀重打包由 `tools/rebase-lab-runtime.py` 完成，仍须实机验证。

## 已验证范围

在 MAG-AN00（Android API 36、ARM64、4 KB 内存页）上，内嵌 Python 3.14.6 / PyTorch 2.14.1 / NumPy 2.5.4 的完整端到端自检通过，包含矩阵乘法、自动求导、文件读写和编辑器脚本执行。可用以下命令在自己的设备上复测：

```powershell
python .\tools\verify-device-runtime.py --serial <ADB设备序列号> --expect-python 3.14.6 --expect-torch 2.14.1
```

0.1.2-beta.4 在 MRO-W00 上验证了兼容环境工作区中新建、编辑和保存 `.py` 文件，且文件名不会被追加 `.txt`。0.1.2-beta.5 的编辑规则通过宿主机冒烟测试并完成 APK 编译；新输入交互尚未在平板上逐项操作验证。跨文件导入和代码提示弹窗也仍需单独实测。此前的 Python/PyTorch 完整自检来自 0.1.0 构建。目前仅在 4 KB 内存页实机上运行过；16 KB 对齐通过静态审计，还需要 16 KB 内存页设备实测。运行时是在应用进程内执行的 CPU 版 CPython/PyTorch，耗时脚本与原生扩展故障可能影响 IDE 进程。

工作区通过 Android 的文档访问框架访问，不能把 `content://` 路径直接交给 CPython。运行副本当前最多 2000 项、单文件 256 MB、总计 512 MB；编辑器只打开不超过 2 MB 的常见文本文件。`.git`、虚拟环境、构建目录和 Python 缓存不会复制。脚本创建的空文件夹和删除操作暂不回写，支持的文件修改与新增会回写；写回失败时应用会保留私有运行副本并报告错误。若脚本改写了当前打开的文件，请重新打开它查看结果。云盘提供者的速度与离线可用性取决于其自身实现。

## 代码与许可证

核心代码位于 `app/src/main/java/com/example/pocketpythonide/`、`app/src/main/python/` 和 `tools/native-runtime/`。运行时组装与审计逻辑位于 `tools/assemble-modern-runtime.py`、`tools/audit_android_runtime.py`。

本项目自有源码采用 [MIT License](LICENSE)。CPython、PyTorch、NumPy、Termux 运行依赖和纯 Python wheel 各自适用其上游许可证；组件及来源见 [第三方说明](THIRD_PARTY_NOTICES.md)。

---

**English:** PocketPythonIDE is an experimental Android tablet IDE bundling CPython 3.14.6, CPU PyTorch 2.14.1, and NumPy 2.5.4 for ARM64. Version 0.1.2-beta.5 adds offline Python completion, fixes `.py.txt` filenames in a HarmonyOS Android compatibility workspace, and improves indentation, delimiter pairing, and font controls. Download the beta.5 APK and runtime asset from Releases, or fetch the pinned runtime and run `build-local.ps1`. The app source is MIT licensed; bundled dependencies retain their own licenses.
