# 阶段 2：双应用工程拆分（2026-10-22）

> 本文记录阶段 2 当时的结构、产物和待验收项，是历史快照。阶段 3–5 后的实际状态见[阶段性报告](../PROGRESS-REPORT-2026-10-25.md)和[阶段 5 验收](../STAGE5-ACCEPTANCE-2026-10-25.md)；下文“尚未验证”等措辞指 2026-10-22 的状态。

## 模块与依赖

```text
:ide  ──┐
        ├──> :workbench  (编辑器、补全、SAF 文件树、创建/保存、运行副本与冲突检测、共享界面)
:lab  ──┘
  └────────> LabRuntimeClient -> :python 独立进程的 LabRuntimeService
                                  -> JNI bridge -> Lab 私有 CPython/PyTorch 运行时

app/  旧 beta.5 源码、运行时输入与已校验 APK；不再是当前 Gradle 构建模块
```

`settings.gradle` 当前只包含两个 `com.android.application` 模块 `:ide`、`:lab` 和一个确有复用的 `com.android.library` 模块 `:workbench`。`workbench` 的编辑器规则、补全、SAF 工作区和界面来自阶段 1 源码的**复制后分层**；原 `app/` 文件、旧 APK 与缓存保留原状。共享 `WorkbenchActivity` 通过抽象后端接口调用运行功能，IDE 返回“仅编辑”，Lab 接入独立的 Python 服务。这样当前无需继续在旧 `MainActivity.java` 上堆叠双产品逻辑。新的通用 IDE 仍沿用现有界面框架，视觉重做留给阶段 4。

| 产品 | `applicationId` | 名称 | 版本 | 调试 APK 字节 / SHA-256 |
| --- | --- | --- | --- | --- |
| beta.5 回退基线 | `com.example.pocketpythonide` | PocketPythonIDE | `0.1.2-beta.5` / code 9 | 158,103,416 / `bb898c236cb14f0218a799d7217432c65144b2ad37b2248cc7075c77f1b63ccb` |
| 通用 IDE | `com.example.pocketandroidide` | Pocket Android IDE | `0.2.0-alpha.1` / code 1 | 77,073 / `6e89100f8a4186c12b2c7468401ff61e2a5a9338da1c87353aa9a6b7b39120b5` |
| Lab | `com.example.pocketpythonlab` | Python · PyTorch Lab | `0.2.0-alpha.1` / code 1 | 158,073,164 / `3146d0e4f4d835be68a7f4b39ebe842b4e6524efe1accd251f3ddbac1302e39c` |

三者包名不同，Android 私有目录和偏好也由系统按包隔离。`minSdk` 保持 26；Lab 仅打包 `arm64-v8a`，通用 IDE 本身无 JNI。上述为本地调试产物，不是 Release，也未在设备上安装。当前调试签名的证书指纹与 beta.5 是否一致不是并存条件，因为包名不同。

## 运行时与包边界

- IDE 包的 ZIP 清单只有应用代码、资源及 MIT `LICENSE.txt`，没有 `runtime.zip`、PyTorch `.so`、JNI 库、科学模块补全目录或运行时下载逻辑。运行按钮显示“需安装兼容语言包”，没有语言包时不声称可执行。补全仍给出 Python 关键字/当前文件符号；Torch/NumPy 目录只编入 Lab。真正的语言包接口属于阶段 3。
- Lab 包含独立的 `runtime.json`、`runtime.zip`、JNI 桥、MIT 许可和 `THIRD_PARTY_NOTICES.md`。原 beta.5 ZIP 的 SHA-256 是 `45e7c3baa507b2c6b6d8b0338b5ef22d947b4cfed6e618234a5f57f4f527f7a4`；构建前由 `tools/rebase-lab-runtime.py` 校验它，再将等长包名 `com.example.pocketpythonide` 改为 `com.example.pocketpythonlab`，不重编译 PyTorch。转换了 653 个 ZIP 成员内的 2,221 处前缀；所得 Lab ZIP 为 164,754,807 字节，SHA-256 `170b4e26d9da3894d0acc62f3f0dd91c9a38459352a3e9f735ae602b1adf8311`。Lab 清单记录源和新资产摘要，逐包原始 `.deb` 哈希仍指向其来源包，不能误认为转换后文件哈希。原始二进制来源、许可证与复现入口见 [基线文档](beta5-baseline.md)。
- 等长重写维持 ELF 字段长度与既有 16 KB 段对齐布局，但可能影响内部路径、校验记录或运行时行为。**Lab 设备冷启动、`import torch` 和自检尚未通过，因此这仍是待实机验证的原型。**若失败，应从固定配方为 Lab 包名前缀重建相应包，不能直接宣称已支持 PyTorch。
- `LabRuntimeService` 声明为 `android:process=":python"`、`exported=false`。编辑器通过文件承载命令/结果、Messenger 传路径和状态；执行在服务的单线程工作队列上，Activity UI 线程不执行原生代码。“运行 → 取消运行”会中断等待并请求终止独立进程，已取消的工作区运行副本不写回；一旦进入写回阶段，界面拒绝取消以避免部分写回被误报为取消。服务进程崩溃时客户端得到断连错误，编辑器进程可继续。这些是已编译的隔离与取消原型，尚未在设备上证明；长训练任务的恢复和多任务队列仍待后续设计。
- 用户实测发现同一安装中的自检和示例运行间歇返回 `native result=-1`。JNI 在首次初始化时记录 `pthread_self()`，后续调用要求同一线程；原实现每次解绑服务后销毁实例工作线程，而 `:python` 进程及 JNI 静态状态可能继续存活。修复后工作队列为进程内静态实例，服务重建仍使用原线程；仅进程终止时一同重置。旧 Lab 调试 APK 已备份到 `.toolchain/lab-debug-before-thread-fix.apk`，SHA-256 `5357f85b87ddc7a023f9fdd066b12a70f1c57b1285bd1ba841fb7e6c3c0a238e`。用户于 2026-10-23 确认该间歇错误已修复；其余阶段 2 设备项目仍以各自测试记录为准。

## 数据边界与迁移入口

- 新应用从自己的 `getFilesDir()/scripts`、`workspace-runs` 和 Activity 私有偏好起步。它们不访问旧 `com.example.pocketpythonide` 私有目录，也不自动导入旧 `workspace_tree_uri`。外部 SAF 文件夹仍归用户选定的文档提供者；用户须在每个新应用中分别“打开文件夹”授权。现有保存与运行副本写回保留哈希冲突检测，提供者拒写会报告错误。
- 两个新应用的“文件 → 导入旧脚本”提供用户选择的 `ACTION_OPEN_DOCUMENT` 单个 `.py` 文件导入，限制 2 MB，目标为本应用私有 `scripts`。重名默认不覆盖，可跳过或另存为带编号副本；取消选择不写入。该入口只能读取用户已能在系统选择器中选择的文件。**beta.5 私有脚本没有可用的导出入口，当前不能据此声称私有脚本已经可迁移。**实现旧包显式导出与可验证归档仍是 [迁移设计](data-migration.md) 的后续门槛，不得通过卸载 beta.5 取得数据。
- 用户在 Lab 中明确打开 SAF 工作区并运行脚本时，运行副本按既有规则尝试写回，可能修改该外部目录；它不会写入旧包私有空间。MRO-W00 原生鸿蒙目录的提供者拒写限制仍适用。

## 独立构建与验证

在仓库根目录使用 PowerShell：

```powershell
.\build-local.ps1 -Product ide
.\build-local.ps1 -Product lab
.\build-local.ps1 -Product all
D:\Anaconda\python.exe .\tools\verify-stage2-artifacts.py
```

脚本优先使用 `.toolchain` 的 JDK 17、Android SDK 35 与 Gradle 8.13，离线增量构建。`ide` 命令不读取或生成 Lab 运行时；`lab` 命令先检验/生成 Lab 私有前缀 ZIP。若没有 `D:\Anaconda\python.exe`，可设置 `PYTHON` 指向 Python 3 或使用 PATH 中的 `python`。现有 `.toolchain/runtime-debs/`、wheel、WSL/Docker 缓存、断点日志和旧 APK 不清理。`tools/verify-stage2-artifacts.py` 只读检查三个 APK 包名、版本、大小、哈希及 IDE/Lab 包内容，不代替设备测试。

设备到位后先运行 `adb devices -l`，确认 ARM64 实机和序列号，再核对旧 beta.5 的安装与脚本备份。分别 `adb -s <序列号> install -r .\ide\build\outputs\apk\debug\ide-debug.apk` 和对应 Lab APK；**不要卸载旧包**。核对 `pm list packages` 同时包含三个 ID，逐一启动并验证 IDE 工作区创建/保存、重名导入、提供者拒写，以及 Lab 冷启动、自检、单文件运行、跨文件导入、失败和独立进程崩溃后的 UI 存活。记录安装前后 SAF 文件哈希。Lab 的自检与默认脚本可用以下命令复跑；它会安装 APK 并操作设备，不是只读检查：

```powershell
python .\tools\verify-device-runtime.py --serial <ARM64设备序列号> --apk .\lab\build\outputs\apk\debug\lab-debug.apk --package com.example.pocketpythonlab --activity com.example.pocketpythonide.LabActivity --expect-python 3.14.6 --expect-torch 2.14.1 --report .\.toolchain\device-lab-runtime-report.json
```

## 本阶段验证状态与后续依赖

- 已通过：两个模块的本地离线 `assembleDebug`；独立包名及 API 26 元数据检查；APK 资产隔离和 Lab ZIP 摘要检查；当前共享源码的编辑规则、补全与 IDE 禁用科学模块的宿主机冒烟测试。旧 beta.5 APK 哈希未变。
- 未通过门槛：Lab 间歇错误修复已由用户确认；三应用并存、迁移 UI 的输入/输出/失败/取消、SAF 用户文件未变化等项目尚无完整实机验收记录。阶段 3 应独立记录这些依赖，不能据此宣称阶段 2 全部设备门槛均已通过。
- 后续需解决：Lab 等长前缀转换的实机可靠性；旧包私有脚本显式导出；取消原型的设备验证与长任务恢复；原生运行时事故后的服务重启；在设备上逐项执行 [回归矩阵](regression-matrix.md)。
