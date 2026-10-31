# Python 基础语言包（阶段 6）

独立安装的 ARM64 CPython 3.14.6 语言包，应用 ID `com.example.pocketpythonstd`。
通过 Language Pack API v1 向通用 IDE 提供运行、语法检查、文本补全、诊断和取消；
Python 在包的 `:python` 进程与私有目录执行。基础 IDE APK 不含 Python 运行时。
本包不含 PyTorch、NumPy 或 pip，不承诺任意 wheel 安装。

先安装[通用 IDE](../../ide/README.md)，再从 [v0.2.0-alpha.1 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1) 下载本包；到 IDE“语言包”查看签名与权限，执行“验证并启用”“启用通过的能力”。版本、大小和 SHA-256 也列在[静态语言包目录](../../docs/language-pack-index.json)。卸载本包会删除其私有缓存；工作区文件仍属于 IDE 选择的 SAF 目录。

阶段 8 增加可选 DAP 调试通道，IDE 验证 `debug` 能力后可设置断点、单步、查看调用栈与局部变量，并在异常处暂停。适配器位于 `pocket_debug_adapter.py`，只使用 CPython 标准库；调试输入是工作区临时快照，写入不回传 SAF。MAG-AN00 实机结果和限制见[阶段 8 报告](../../docs/STAGE8-DEBUG-2026-10-27.md)。

在仓库根目录使用 `pwsh -File build-local.ps1 -Product python`。构建前
`packs/python/prepare_runtime.py` 验证固定 beta.5 源 ZIP 的 SHA-256，
按 CPython 原生扩展的 ELF `DT_NEEDED` 闭包保留依赖，并重定位等长应用前缀。
生成资产为 `packs/python/src/main/assets/runtime.zip` 和 `runtime.json`；大型 ZIP
不进入 Git。Gradle 再次校验 ZIP 大小和 SHA-256 后打包。

工作区输入仍受 API v1 的 2000 文件、单文件 1 MiB、总量 8 MiB 限制。
第一方 `changes-v1` 扩展把修改和新建文件交回 IDE 校验；最多 100 个文件、
变更总量 2 MiB，删除暂不回写。SAF 写回仍由 IDE 做冲突检查。
可在项目根目录或入口脚本同目录放 `.pocket-python.json`，例如：

```json
{"args": ["course"], "stdin": "Ada\n"}
```

脚本里的 `input()` 会读取 `stdin`，`sys.argv` 会包含 `args`。兼容范围与设备证据见
[`docs/python-pack-compatibility.md`](../../docs/python-pack-compatibility.md)。
