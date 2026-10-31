# 阶段 6 提示词：通用 IDE 的 Python 基础语言包

请执行本阶段，并遵守 `docs/prompts/00-共同约束.md` 和 Language Pack API v1。这里的 Python 包服务通用 IDE 的课程开发；PyTorch、NumPy 和小模型训练属于独立 Lab。若语言包 SDK 尚未通过示例包验收，先修复 SDK。

## 任务

1. 从现有 `runtime.json` 和构建脚本识别 CPython 本体、标准库、纯 Python 依赖与 PyTorch／NumPy 的真实边界。尽量复用可验证的构建产物，但语言包不能将大型训练运行时重新塞回通用 IDE 基础 APK。
2. 构建独立 Android ARM64 Python 语言包，提供版本信息、标准库能力、必要的本地依赖与许可证。运行时在语言包自己的进程与存储空间内工作，IDE 经 API v1 传入项目副本、入口文件和参数。
3. 保留 `Ctrl+S`、补全、括号配对、缩进、多文件模块导入和输出定位等已有体验。补全范围先写实：关键字、内置函数、当前项目符号和可用标准库；更复杂的类型推断需要单独证明。
4. 对第三方库明确分级：纯 Python wheel 可以在兼容条件下试验安装；带本地扩展的 wheel 必须有 Android ABI 与依赖匹配。不要向用户承诺任意 `pip install` 可用。包管理 UI 必须在安装前显示兼容性与失败原因。
5. 验证项目文件改写后的同步和冲突处理；Python 任务崩溃、长循环与取消不应使 IDE 丢失未保存编辑内容。

## 交付物

- Python 基础包 APK、API v1 清单、版本和许可证清单、构建／复现脚本。
- `docs/python-pack-compatibility.md`：标准库、纯 Python wheel、本地扩展的支持范围与已测设备。
- 实机验证：单文件、跨文件导入、异常栈定位、输入输出、文件读写、取消任务、重启后继续运行。

## 验收

- 通用 IDE 基础 APK 仍不包含 Python 运行时；未安装语言包时仍可编辑 `.py` 文件。
- 安装包后可在 ARM64 实机运行多文件 Python 项目，错误可定位到源码行。
- 与 PyTorch Lab 的依赖、数据和应用安装相互独立。
