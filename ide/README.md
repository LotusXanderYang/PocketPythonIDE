# Pocket Android IDE（0.2.0 Alpha）

通用 ARM64 Android 代码编辑器，包名 `com.example.pocketandroidide`，最低 Android API 26。基础 APK 约 0.26 MB，不包含 Python、PyTorch、C/C++ 编译器或调试器；运行能力来自用户另外安装并验证的语言包。当前公开预览版为 [v0.2.0-alpha.1](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1)。

## 安装与使用

1. 从 [v0.2.0-alpha.1 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1) 下载 IDE APK，核对页面中的 SHA-256；安装后打开应用。旧版 `com.example.pocketpythonide` 可并存。覆盖此新版 IDE 时保持同一签名，不要先卸载，否则应用私有数据会被删除。
2. 在“文件 → 打开文件夹”中选择可写的 Android SAF 目录并授权；新建文件或打开已有项目。文件树、标签页、保存、主题、背景、字体导入与自定义代码高亮配色均属于 IDE。SAF 文档提供者拒写时应改选可写目录；不要把原生鸿蒙目录当作普通文件路径。
3. 另行安装 [Python 包](../packs/python/README.md)或 [C/C++ 包](../packs/cpp/README.md)，在“语言包”中查看包名、签名、权限和能力，再点“验证并启用”“启用通过的能力”。支持文件的顶部状态才会显示可运行或可调试。`.echo` 是 SDK 的示例语言包。
4. 构建、运行和调试结果进入底部面板。调试时在行号处设置断点，使用工具条继续、单步、暂停和停止，并在左侧查看变量与调用栈。C/C++ 项目配置和 Python 输入参数分别见对应语言包 README。

当前 `main` 分支的“设置 → 代码高亮配色”提供可点选的预设及单项色块，仍可输入 `#RRGGBB` 精调；“编辑器字体”提供系统等宽字体预设和导入选项，导入格式为 `.ttf`、`.otf`，单文件上限 10 MiB。语言包列表使用 Python、C / C++ 等易读名称，详情继续显示真实包 ID、签名和 APK 哈希。这些更新尚待实机验证，未进入 `v0.2.0-alpha.1` Release APK。

## 范围与数据

- 已在 MAG-AN00 原生 ARM64 Android 与 MRO-W00 卓易通环境验证核心运行和调试路径；各语言包的设备差异见[阶段 8 记录](../docs/STAGE8-CPP-DEBUG-HARMONY-FIX-2026-10-31.md)。不承诺任意语言、完整 LSP、任意 `pip` 包或 GPU。
- 文件夹授权保留在 IDE 中；运行用工作区快照，语言包不能直接访问 SAF URI。写回由 IDE 检查外部修改冲突。先保存重要项目到外部可写工作区；卸载应用会删除应用私有文件与授权。
- 旧版工作区不会自动迁移。可从两个应用分别选择同一可写 SAF 文件夹；不要在两个应用同时编辑同一文件。迁移失败时继续使用旧版及原工作区，旧版 Release 未变动。

## 源码构建

需要 JDK 17、Android SDK Platform 35、Gradle 8.13、Android Gradle Plugin 8.13，以及仓库锁定的 `.toolchain` 或等效本机环境。仓库根目录执行 `pwsh -File build-local.ps1 -Product ide`，输出为 `ide/build/outputs/apk/debug/ide-debug.apk`。保留同一调试签名才能覆盖已安装的新版 IDE；更换签名必须先备份工作区数据。更完整的构建与原生运行时步骤见[仓库 README](../README.md)。
