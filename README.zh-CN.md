[English](README.md) | **简体中文**

# Pocket Android IDE

面向 ARM64 Android 平板的轻量代码工作台。IDE 本体只负责编辑、项目文件和任务界面；Python 与 C/C++ 的运行、构建和调试由用户自行安装并验证的独立语言包提供。

**当前版本：`0.2.0-alpha.1`（测试版）** · Android API 26+ · ARM64

`main` 分支新增了外观选择和语言包易读名称，仍待实机验证；已发布的 `0.2.0-alpha.1` APK 尚不包含这些更新。

> [Python · PyTorch Lab 已拆分为独立项目](https://github.com/LotusXanderYang/PocketPyTorchLab)。Lab 面向离线 CPU 小模型训练，不需要安装本 IDE。

## 亮点

- **按需安装语言能力**：IDE 基础 APK 约 268 KB，不捆绑 Python、PyTorch 或 C/C++ 工具链。安装语言包后，IDE 会检查签名、权限与能力并运行自检，通过后才开放对应操作。
- **从编辑到调试都在工作台内**：文件树、标签页、搜索、输出/问题/构建记录和任务状态集中显示。调试支持断点、单步、继续、暂停、变量与调用栈；失败和异常能定位到源码。
- **适合触屏，也支持实体键盘**：长按文件可删除，补全可点选；支持缩进、括号配对、保存快捷键、自定义字体及代码高亮配色。
- **工作区由用户选择**：通过 Android 系统文件选择器授权可写文件夹。运行前复制项目快照，写回时检测外部修改，避免覆盖用户同时编辑的文件。
- **开放语言包接口**：第三方可按 [Language Pack API v1](docs/language-pack-api/v1/README.md) 和 [SDK](pack-sdk/README.md) 制作独立 APK；[静态目录](docs/language-pack-index.json)只列真实发布并校验过的语言包。

## 当前版本的能力

| 组件 | 已提供 | 范围 |
| --- | --- | --- |
| IDE | 文件与项目编辑、SAF 工作区、主题/背景/字体/高亮设置、任务与调试界面 | 本体不运行代码 |
| [Python 包](packs/python/README.md) | CPython 3.14.6 运行、补全、诊断、DAP 调试、取消 | 不含 PyTorch、NumPy 或通用 `pip install` |
| [C/C++ 包](packs/cpp/README.md) | Clang/LLD 构建运行、补全、诊断、LLDB 调试 | 面向单目标课程项目；多文件配置使用 `.pocket-cpp.json` |
| [Echo 示例包](sample-pack/README.md) | 语言包接口与权限边界示例 | 供接口开发和测试 |

IDE 与两个正式语言包已在 BTK-W00 完成全新安装、工作区读写、自检和多文件 C 项目运行；Python 写回、C/C++ 调试另有 MAG-AN00 与 MRO-W00 实机记录。API 26 和 16 KB 内存页设备尚未完成实机验证。各项限制见对应组件 README 和 [阶段 9 验收记录](docs/STAGE9-RELEASE-PREFLIGHT-2026-10-31.md)。

## 安装与开始

1. 从 [v0.2.0-alpha.1 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1) 下载 IDE APK，并按需下载 Python 或 C/C++ 语言包；核对 Release 中的 SHA-256 后安装。
2. 在 IDE 中选择“文件 → 打开文件夹”，授权一个实际可写的目录。鸿蒙兼容环境中，部分原生目录可能只能浏览，不能写入。
3. 打开“语言包”，核对包名、签名与权限，执行“验证并启用”及“启用通过的能力”。打开 `.py`、`.c` 或 `.cpp` 文件后即可按已验证能力运行、构建或调试。

新 IDE 的包名是 `com.example.pocketandroidide`，可与[旧版 `v0.1.2-beta.5`](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.1.2-beta.5)并存。旧应用的私有文件不会自动迁移；请从新 IDE 重新授权原 SAF 工作区。覆盖同包名安装时须保持签名一致，卸载前先备份应用私有文件。`v0.2.0-alpha.1` Release 是拆分前的历史发布，仍包含 Lab APK；Lab 今后的源码和发布以其[独立仓库](https://github.com/LotusXanderYang/PocketPyTorchLab)为准。

## 从源码构建

需要 JDK 17、Android SDK Platform 35 和 Gradle 8.13。仓库含 Gradle wrapper；语言包运行时与工具链按各自锁定清单准备，不提交大型二进制到 Git。

```powershell
git clone https://github.com/LotusXanderYang/PocketPythonIDE.git
cd PocketPythonIDE
.\build-local.ps1 -Product ide
.\build-local.ps1 -Product python
.\build-local.ps1 -Product sample
```

C/C++ 包的固定工具链与构建步骤见[组件说明](packs/cpp/README.md)；第三方组件的归档、源码与许可线索见[第三方源码指引](docs/THIRD-PARTY-SOURCE.md)。独立 Lab 的构建方法见[Lab 仓库](https://github.com/LotusXanderYang/PocketPyTorchLab)。旧一体化源码保留在 `v0.2.0-alpha.1` 标签和原提交历史中。

本项目自有源码采用 [MIT License](LICENSE)。语言包及内嵌工具链的第三方许可证见各包声明；旧版一体化运行时声明见[历史说明](THIRD_PARTY_NOTICES.md)。
