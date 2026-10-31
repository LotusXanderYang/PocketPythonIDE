# Language Pack API v1（阶段 3 原型）

本目录是独立 Android APK 语言包的接口规范。当前宿主 `com.example.pocketandroidide` 为 `0.2.0-alpha.1`，最低 API 26。`pack-sdk/` 是可引用的 AAR 源码，`sample-pack/` 是无原生工具链的 `.echo` 示例。示例在 ARM64 的 MAG-AN00（API 36）上通过阶段 3 实机验收；范围与证据见 [验证记录](VALIDATION.md)。

## 安装、发现与信任

1. 包 APK 的 `Service` 必须 `exported=true`，intent action 为 `org.pocketide.pack.v1.LANGUAGE_PACK`，`meta-data` 名为 `org.pocketide.pack.v1.MANIFEST`，资源指向 `res/raw` 中的 UTF-8 JSON。`packId` 必须等于 APK `applicationId`，`entryService` 必须等于服务的完整类名。
2. IDE 通过包可见性 `<queries>` 发现服务，按 [包清单 Schema](pack-manifest.schema.json) 检查包名、入口、SDK、设备 ABI、宿主版本和能力，再从 Android `PackageManager` 取得**已安装 APK 的签名证书 SHA-256**。安装或发现不会自动启用。用户在扩展面板看到来源、版本、许可证、权限、签名及声明能力，明确选择“验证并启用”。
3. IDE 通过显式 `ComponentName` 绑定、查询 AIDL 协议版本并执行每项声明能力的 `selfTests`。结果存为包名、签名指纹、APK 哈希、包版本、系统安装时间和通过能力的集合。运行按钮只对通过 `run` 与大输出测试的对应扩展名开放；更新或重装使旧测试结果失效。用户可禁用；卸载广播和每次能力查询都会核对批准记录，重装必须重新验证。已用过的扩展名保留为可编辑文本类型，卸载不删除源码。包不能通过修改自己的 JSON 获得 SAF 授权。
4. 可选 `warmupActivity` 指向**同一 APK 内、已导出的无显示 Activity**。部分 Honor 系统会拦截另一个应用对尚未运行的服务的冷启动；IDE 遇到绑定失败时启动该 Activity，再重试显式绑定。示例实现见 `PackWarmupActivity`。它只启动包进程，不授予权限、不跳过 UID 检查；若设备仍禁止启动，任务报告 `PACK_BIND_FAILED`。第三方包无需声明此字段，除非目标设备实测需要。
4. 第三方 APK 自行拥有其 Android 权限和进程。示例包只接受 `com.example.pocketandroidide` 的 UID；正式包应检查调用方包名及签名证书，按自己的风险模型限制调用。用户安装第三方 APK 等同安装可执行软件，须自行核对来源和权限。宿主只能限制传给包的项目输入，不能沙箱化该 APK 的系统权限。

资源包（主题、关键字、片段）可使用单独的无执行代码格式，不能声明本规范的可执行能力，IDE 中须标作“资源”。本阶段仅实现可执行 APK 的接口。

## AIDL 与任务

源码位于 `pack-sdk/src/main/aidl/org/pocketide/pack/v1/`。所有调用在工作线程发起：

```text
ILanguagePack.getProtocolVersion() -> 1
ILanguagePack.submit(Bundle request, ParcelFileDescriptor input,
                     ParcelFileDescriptor output, ITaskCallback callback)
ILanguagePack.cancel(String taskId)
ITaskCallback.finished(Bundle result)  // oneway，恰好一次
```

请求 Bundle 必含 `taskId`（UUID）、`protocolVersion=1`、`operation`、`workspace`（任务作用域标识，如 `task:<UUID>`）、`entry`（ZIP 内相对路径；`initialize` 可为空）。任务 ID 在一个服务进程中不可复用。`operation` 为 `initialize`、`analyze`、`complete`、`build`、`run`、`diagnostics`；`cancel` 为单独 AIDL 方法。包必须实现 `initialize` 与 `cancel`，其他能力按清单选择；不支持的操作须回 `unsupported`，不能伪装成功。`build` 可产生构建日志与退出码；本示例不实现。

`input` 是 IDE 私有工作区运行副本生成的只读 ZIP 文件描述符。ZIP 条目为相对路径，禁止绝对路径、`..`、反斜杠与符号链接逃逸。`output` 是宿主私有输出文件的可写描述符。大输入/输出走 FD；Binder 只载小元数据。当前宿主限制 2000 文件、单文件 1 MiB、总计 8 MiB、输出 4 MiB，超限明确报错。v1 当前示例的输出是 UTF-8 文本；更复杂的产物须在后续兼容版本中定义结果归档和显式写回清单，不能自行改写宿主 SAF 工作区。

结果 Bundle 必含 `taskId`、`protocolVersion`、`status`、`errorCode`、`message`、`exitCode`、`line`、`column`、`endLine`、`endColumn`、`resultPath`。`status` 是 `ok|error|cancelled|unsupported`。位置均为**1 起始的 Unicode 码点行/列**；未知位置填 0。文本偏移若未来与 LSP 通信，需显式转换为 LSP 的 0 起始 UTF-16 位置，不能直接混用。诊断可与编译错误共享这些字段。`resultPath` 目前只允许空字符串，预留给后续显式产物清单。IDE 展示错误时带包 ID 与任务 ID，便于追溯。

`analyze`/`complete`/`diagnostics` 的 v1 简例通过同一个文本输出 FD 返回可显示 UTF-8 结果。正式结构化补全和多条诊断需要 API 的后续兼容扩展；不得把这一文本通道称作完整 LSP。阶段 8 增加了可选的独立[调试通道](DEBUG.md)：不改变原 v1 任务 Binder；目前只有独立 Python 包通过 DAP 实机验证，C/C++ 包未声明调试。

阶段 6 的第一方 Python 包可选声明 `workspaceOutput: "changes-v1"`。此时输出 FD 改为 UTF-8 JSON：`stdout`、`stderr` 和 `changes` 数组；每项变更给出相对 `path`、原文件 `baseSha256`（新文件为空串）及 `dataBase64`。只有与 IDE 同签名的包才可使用此格式。宿主先核验路径、基线哈希、单文件 1 MiB、变更总量 2 MiB、最多 100 个文件，再写入私有项目副本；SAF 工作区仍经宿主原有哈希冲突检测写回。取消或不完整响应不写回。v1 不支持删除文件回传，包须明确报错。未声明该字段的现有包仍输出普通 UTF-8 文本，行为不变。此扩展是阶段 6 的第一方文件回传能力，第三方包不能借此直接取得 SAF 授权。

`selfTests.cancel.operation` 指定被取消的长任务操作（`analyze|complete|build|run|diagnostics`）；不声明时默认 `run`。只实现分析能力的包可以测试取消分析，而不必虚报运行能力。

## 生命周期

```text
未安装 ──用户安装 APK──> 已发现
已发现 ──查看来源/签名/权限，验证──> 已启用（仅通过能力）
已启用 ──显式 bind + 协议握手──> 运行中 ──callback──> 已启用
运行中 ──用户取消/超时──> cancel(taskId) ──结果或断连──> 已启用
运行中 ──包进程退出/卸载──> PACK_CRASH/PACK_BIND_FAILED ──> 不提供该任务结果
已启用 ──用户禁用/卸载──> 已发现/未安装，批准记录清除
```

宿主当前连接等待 10 秒，任务调用默认最多 60 秒，验证用调用限时 3–60 秒。超时调用 `cancel` 并记录 `PACK_TIMEOUT`；用户取消调用 `cancel` 并丢弃未确认的工作区副本；Binder 断连记录 `PACK_CRASH`。包应在收到取消后尽快停工、回 `cancelled` 并关闭 FD。后台任务受 Android 进程限制，包若需要长时间执行，应按 Android 前台服务规则自行实现并在其说明中披露通知与权限；v1 示例不提供持久后台训练。宿主不把 SAF URI 交给包，也不会让包写回 SAF。日志由宿主输出面板和任务 ID 关联；建议包另保留有限的任务日志，不包含用户源码全文。

## 版本、索引与发布

`schemaVersion=1` 和 AIDL `protocolVersion=1` 必须同时匹配。当前宿主仅接受 `minHostVersion=0.2.0-alpha.1`，避免把预发行版本误判为兼容；后续宿主放宽时仍须保持 v1 字段语义。新必填字段或改变 Bundle 字段含义须提升协议主版本并提供迁移说明。包升级后版本字段改变会撤销能力验证；签名改变会撤销启用。当前一时只启用一个可执行包，后续可扩展为按语言选择。

[静态索引 Schema](static-index.schema.json) 另记发布后的 APK HTTPS 链接、SHA-256、签名证书 SHA-256 和字节数。它只是下载目录，不是权限来源。包内清单不含自身 APK 哈希，避免自引用。示例索引中的 `a…`、`b…` 是 Schema 测试占位值，**不是可发布的真实哈希**。正式发布时必须用实际签名 APK 的摘要替换，并核对包内清单与索引的 ID、版本、ABI 和许可证。

正式目录位于仓库根目录的 [`docs/language-pack-index.json`](../../language-pack-index.json)，仅列已经上传至 GitHub Release 的 APK；尚未发布时 `packs` 为空。发布前执行 `python tools/verify-static-pack-index.py`，核验 Schema、重复的包 ID/版本、Release URL、能力、工具链大小、兼容条件和最近实测设备。该脚本无法替代 GitHub 附件的下载后哈希复核或 IDE 内的安装验证。

## 构建与验证

```powershell
.\build-local.ps1 -Product ide
.\build-local.ps1 -Product sample
D:\Anaconda\python.exe .\tools\verify-language-pack.py
python .\tools\verify-language-pack-device.py --serial <设备序列号> --operate-ui
```

验证器脚本需要 `jsonschema>=4`；检查有效/无效示例、实际示例清单与 APK 资源。设备上安装 IDE 与示例包后，在 IDE 扩展图标选择 `org.pocketide.sample.echo` →“验证并启用”，完成 `initialize`、`analyze`、`complete`、`run`、`diagnostics`、`cancel` 与大输出测试。建立 `demo.echo` 写入 `ECHO hello`，运行后应输出 `hello`。`ERROR` 应显示第 1 行位置；`WAIT 5000` 可用于取消。卸载示例包后 IDE 应保留编辑功能，已启用状态撤销。

设备脚本会覆盖安装 IDE、示例包和单独 UID 的权限探针（保留安装数据），操作 IDE 完成逐项验证并禁用示例包；运行前保存编辑器内容。它不会卸载包。`pack-probe` 只用于验证非宿主 UID 被服务拒绝，不是用户功能。卸载及同 APK 重装后的批准撤销已另行在设备上核对，见验证记录。

阶段 3 实机门槛包括包发现、权限拒绝、绑定、取消、包进程崩溃、版本不兼容、卸载恢复、每项声明能力与大输出；本示例在上述设备上已通过。详细错误码见 [ERRORS.md](ERRORS.md)，精确 APK 哈希、未完成的 SDK AAR 打包事项见 [VALIDATION.md](VALIDATION.md)。
