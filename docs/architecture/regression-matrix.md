# beta.5 回归矩阵（阶段 1）

本矩阵是后续版本可重复执行的清单，**不是新双应用的测试报告**。`MAG` 指 MAG-AN00（Android API 36、ARM64、4 KB 页），`MRO` 指 MRO-W00（OpenHarmony 6.1.1.120 的 Android 兼容环境），`宿主机` 指当前 Windows/JDK。设备列中的“旧记录”只说明其记录所用构建；不能向 beta.5 或未来新应用外推。当前 beta.5 APK 哈希为 `bb898c…`；每次实测须记下 APK SHA-256、应用版本、设备版本、页面大小、时间、输入与实际输出。

| 功能 / 可重复输入与预期 | MAG 旧记录 | MRO 旧记录 | 宿主机 | beta.5 确切 APK / 新应用待测 |
| --- | --- | --- | --- | --- |
| 编辑器：打开两个 `.py`、修改、切换标签后回到原文件；保留内容、行号和当前文件名 | 未单项记录 | 未单项记录 | 不适用 | 待实机；另测取消切换/未保存处理 |
| SAF 工作区：选可写目录、重启后再打开、展开嵌套树；撤销授权后须明确提示 | 未单项记录 | beta.4 在兼容环境 `Android` 目录验证打开/编辑/保存；原生 `我的平板/个人/PythonProject` 可浏览但拒绝写入 | 不适用 | beta.5 与新应用均待测；每个新包需用户重新授权 |
| 创建文件/文件夹：创建 `lesson/main.py`，确认为 `.py` 而非 `.py.txt`；重复名、只读提供者须报错且不覆盖 | 未单项记录 | beta.4 已验证兼容环境新建 `.py` 不追加 `.txt`；原生目录创建失败 | 不适用 | beta.5 Release 描述修复，但设备交互未逐项验证 |
| 保存：改动后 `Ctrl+S`，重开核对字节；外部同时修改同一 SAF 文件应阻止覆盖；提供者拒写须提示 | 未单项记录 | beta.4 兼容环境保存已验证；外部冲突未测 | 不适用 | 待实机，含失败路径 |
| 跨文件导入：`main.py` 导入同目录 `helper.py` 及包 `pkg/util.py`；输出预期值，外部修改后检查写回/冲突 | 未单项记录 | 未单项记录 | 不适用 | 待实机；README 已明确仍需单独实测 |
| 运行：`print(7)`、抛出异常、环境自检、文件读写；输出完成/失败及错误；长任务取消行为另记 | 主工程整合构建的 `.toolchain/device-main-runtime-report.json`：Python 3.14.6、Torch 2.14.1、自检及编辑器脚本通过；该报告未绑定 beta.5 发布 APK 哈希 | 未单项记录 | 不适用 | beta.5 确切 APK 待复测；当前进程内执行的取消/故障隔离未证实 |
| 补全：输入 `p` 应优先 `print`，`torch.te`、当前文件函数应提示；注释/字符串不触发；方向键、Enter、Esc、`Ctrl+Space` 与触控逐项操作 | 未单项记录 | 未单项记录 | 当前工作区的 `CompletionSmokeTest` 通过纯逻辑用例；弹窗交互未测 | beta.5 弹窗待实机；跨文件类型推断不在现有能力内 |
| 键盘缩进/配对：Tab 四空格、选区整体缩进、Shift+Tab 反缩进、冒号后 Enter、五类成对符号及越过右符号 | 未单项记录 | 未单项记录 | 当前工作区的 `EditorRulesSmokeTest` 通过纯规则用例 | beta.5 输入法/实体键盘交互待实机 |
| 字体：`查看 → 字体大小` 选 10 与 32 sp，预览、取消、保存并重启；窄屏入口 | 未单项记录 | 未单项记录 | 不适用 | beta.5 待实机 |

本阶段宿主机复测：用本地 JDK 17 `javac -encoding UTF-8` 编译 `PythonEditorRules.java`、`PythonCompletions.java` 和两个 `tools/*SmokeTest.java` 到系统临时目录；输出 `Editor rules OK`、`Completion smoke test passed`。这验证的是**当前未提交源码快照**，不是远端标签源码。首次不指定 UTF-8 的编译因 Windows 默认 GBK 无法解析源码字符而失败；指定编码后通过，故后续命令必须显式带 `-encoding UTF-8`。

可复跑的宿主机命令（输出至临时目录，不改动仓库）：

```powershell
$jdk = '.\.toolchain\zulu17.68.203-ca-jdk17.0.20.1-win_x64\bin'
$out = Join-Path $env:TEMP ('pocketpy-smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
& "$jdk\javac.exe" -encoding UTF-8 -d $out .\app\src\main\java\com\example\pocketpythonide\PythonEditorRules.java .\app\src\main\java\com\example\pocketpythonide\PythonCompletions.java .\tools\EditorRulesSmokeTest.java .\tools\CompletionSmokeTest.java
& "$jdk\java.exe" -cp $out com.example.pocketpythonide.EditorRulesSmokeTest
& "$jdk\java.exe" -cp $out com.example.pocketpythonide.CompletionSmokeTest
```

设备复测命令见 [beta5-baseline.md](beta5-baseline.md)。`verify-device-runtime.py` 仅覆盖环境自检与编辑器脚本，不覆盖本表其他 UI/SAF 项。每个 UI 项还须人工在正常输入、失败和取消路径观察结果。此次 `adb devices -l` 尝试启动 ADB server 失败（`could not read ok from ADB Server`），所以没有本次实机结论。16 KB 页设备、beta.5 在 MRO 的新输入交互、跨文件导入与补全弹窗仍待验证。
