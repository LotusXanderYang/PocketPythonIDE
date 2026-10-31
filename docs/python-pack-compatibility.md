# Python 基础语言包兼容范围（阶段 6）

## 包与设备

本包是独立安装的 `com.example.pocketpythonstd`，内含 ARM64 CPython 3.14.6 与标准库；IDE `com.example.pocketandroidide` 本身不打包 Python、NumPy 或 PyTorch。包在自己的 `:python` 进程运行，IDE 通过 Language Pack API v1 交付有界项目副本。最低 Android API 为 26，仅提供 `arm64-v8a`。MRO-W00 鸿蒙平板的 Android 兼容环境已验证连续三次语言包自检、跨文件导入、标准库文件读写、配置输入与参数、异常行号、手动取消、重启运行和并发外部改动冲突。其他设备不由此推定通过。

| 内容 | 当前范围 |
| --- | --- |
| Python 基础语法与标准库 | 从固定 `runtime.zip` 提取的 CPython 3.14.6 标准库和所需 ARM64 原生依赖；`ssl`、`sqlite3` 随 `initialize` 自检。具体模块仍以设备测试为准。 |
| 项目内 `.py` 模块与包 | 项目根目录及入口脚本目录加入 `sys.path`；复制的项目文件可跨文件导入。 |
| 补全 | Python 关键字、内置函数、标准库模块名、当前文件及最多 100 个其他项目 `.py` 文件的简单定义与赋值名。静态文本索引，不提供类型推断、成员推断或完整 LSP。 |
| 输入与参数 | 项目根目录或入口脚本同目录的 `.pocket-python.json` 可提供字符串 `args` 数组和 `stdin` 字符串；默认输入为空。没有交互式运行终端。 |
| 文件改写 | 运行时仅看到项目副本；第一方 `changes-v1` 回传新增或修改文件，最多 100 个文件、单文件 1 MiB、变更合计 2 MiB。IDE 再核对基线哈希，并对 SAF 工作区检查外部改动。删除和空目录不回写。 |
| 纯 Python wheel | 理论上与 CPython 3.14 且依赖均为纯 Python 的包可单独试验：用户可自行解包，将顶层 Python 模块放进项目目录，按普通项目文件导入。当前不解析 wheel 元数据、依赖或入口脚本，也没有安装器或包管理 UI；尚未验证任何第三方 wheel，不能通过 IDE 直接执行任意 `pip install`。 |
| 带原生扩展的 wheel | 必须同时匹配 Android ARM64、CPython 3.14 ABI、最低 API、应用前缀和原生依赖闭包；本阶段没有通用安装能力，也未验证任何第三方扩展包。桌面 Linux/Windows wheel 不能直接使用。 |
| 训练栈 | NumPy、PyTorch、GPU 后端及训练依赖不在此包中。独立 Lab 的数据、安装与许可证清单互不迁移。 |

脚本的输出、异常栈和源码行号会返回 IDE。语法检查使用 `ast.parse`，只检查当前入口文件。取消长期运行任务时会终止语言包自己的进程；IDE 编辑草稿和 SAF 文件由宿主管理。设备证据见[阶段 6 验收记录](STAGE6-ACCEPTANCE-2026-10-25.md)。

运行时来源、SHA-256、裁剪脚本和第三方许可证见 [`packs/python/README.md`](../packs/python/README.md)、随 APK 提供的 `THIRD_PARTY_NOTICES.md` 和 `runtime.json`。这份兼容说明只陈述当前包边界，不承诺未来 wheel 的兼容性。
