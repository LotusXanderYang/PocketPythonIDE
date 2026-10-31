# 阶段 6 验收记录（通过）

日期：2026-10-25。范围只包括 `docs/prompts/06-Python基础语言包.md`；不含阶段 7 或 Release。

## 固定产物

| 产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| `artifacts/stage6-ide-62ff75f2.apk` | 189786 | `62ff75f249db7855b1d3d14e54871d66aa0d4942693281d9702c20bbf0da9370` |
| `artifacts/stage6-python-99acd151.apk` | 11214474 | `99acd1515abd4766f1d7e9ff3ef804b3f1f932a3d570a70a13c2739fd637fa9c` |
| Python 基础运行时 ZIP（不入 Git） | 11255268 | `339cdbdd070f106abcb36830389a5817402fc4e700d79ed38a958ade9a0a78dd` |

两 APK 均用本机 Android Debug 证书签署，证书 SHA-256 为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`。正式发布需另按发布流程签名和重新核对哈希。

## 已完成的本地核验

- `python packs/python/prepare_runtime.py` 核对固定源 ZIP 并复现派生物；派生 ZIP 有 684 个文件、约 31.9 MB 解包大小，不包含 torch、numpy 或第三方 `site-packages`。
- `pwsh -File build-local.ps1 -Product python` 成功；`:ide:assembleDebug` 成功。IDE APK 静态检查无 Python 运行时，Python APK 含 ARM64 JNI、运行时和许可证文件。两 APK 包名分别为 `com.example.pocketandroidide`、`com.example.pocketpythonstd`，最低 API 26。
- Python 包清单通过 API v1 JSON Schema。`python tools/test_python_pack_bridge.py` 在宿主机通过跨文件导入、参数和 stdin、文件写入、异常行号、语法错误、补全和 `sys.exit` 行为。
- `python tools/verify-language-pack.py` 对阶段 3 Schema、示例包和 SDK 源码回归通过。IDE 自检针对取消后语言包进程重启增加仅限绑定/断连错误的重试；设备上连续三次自检通过。
- MRO-W00 首次安装后的语言包列表未显示 Python。检查发现 IDE 把预热 Activity 的 Java 包名前缀误当成 applicationId 要求；改为验证组件属于同一 APK 并导出后，用户确认已找到语言包。
- 首次自检多次出现 `diagnostics: 失败 null` 与 `largeOutput: PACK_BIND_FAILED/PACK_CRASH` 交替。定位为取消测试后语言包进程延迟退出和后续 Binder 调用竞争。当前候选改为取消时同步终止语言包进程、重启时清理遗留任务，并让 IDE 对重连边界报告明确异常类型。
- 用户在 MRO-W00 覆盖安装 `62ff75f2` IDE 与 `99acd151` Python 包后，连续三次执行“验证并启用”，三次均全通过。包发现、自检能力、取消后恢复、大输出、版本错误与崩溃恢复已通过此项设备回归。
- 用户在可写 SAF 工作区运行 `stage6_main.py`，成功导入 `stage6_helper.py`，输出 `course-python: 42` 与 `readback: written-by-python`；`stage6_output.txt` 出现并保留内容。
- 用户确认 `.pocket-python.json` 的 stdin/args 分别输出 `Ada` 和 `course`；长循环手动取消后可重新运行正常脚本；重启 IDE 后仍可运行。`stage6_error.py` 的设备栈指出实际 `raise` 行第 3 行，返回的 `PYTHON_EXCEPTION · 3:1` 与源码一致，任务显示“运行失败”。
- 并发改写测试中，用户运行 20 秒延迟脚本，同时在 IDE 保存同名输出文件；任务结束显示“1 个文件被外部修改，未覆盖；运行副本已保留”。用户确认最终内容仍为 `manual-edit`，IDE 可继续编辑和保存。

## 验收结论与边界

MRO-W00 鸿蒙平板的 Android 兼容环境满足本阶段实机验收：包发现与启用、单文件与跨文件运行、异常行号、输入输出、文件新增与读写、并发改写冲突、长循环取消及重启后继续运行均有设备结果。IDE 未安装 Python 包时 `.py` 仍可编辑的路径在阶段 4 已由用户验证为“仅编辑”，本阶段 IDE 静态检查也确认不含 Python 运行时。第三方 wheel、其他 Android 设备和鸿蒙原生文件目录未在本阶段验证；不能据此宣称兼容。

既有 IDE/Lab/语言包的用户数据和安装均未清除。本阶段仅生成新的独立 Python 包，IDE 覆盖安装才可能更新 IDE 代码；不同应用的私有数据不迁移。旧版 `v0.1.2-beta.5` Release 与标签未改动。
