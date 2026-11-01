# 第三方可执行语言包制包提示词 · API v1

将尖括号变量填为真实信息，再连同本目录规范、`pack-sdk/`、`sample-pack/` 和 `tools/verify-language-pack.py` 交给 AI 编程助手。目标语言 `<名称>`，包 ID `<反向域名>`，上游工具链/解释器 `<版本及官方源码>`，项目 `<GitHub 仓库>`，目标设备 `<型号/API/ABI>`，课程任务 `<输入与预期输出>`，所需能力 `<逐项列出>`。当前 IDE 为 `0.2.0-alpha.2`，但 API v1 清单中的 `minHostVersion` 仍须为 `0.2.0-alpha.1`。

1. 先读 `docs/prompts/00-共同约束.md`、本目录 [README](README.md)、两个 JSON Schema、[可选调试通道](DEBUG.md)、AIDL 源码、示例服务与仓库 `AGENTS.md`。只读检查 Git 状态，保护既有未提交工作。列出已证实、待设备验证、不可行的能力；不得用 PC/Linux 原生程序冒充 Android 二进制。
2. 创建**独立安装的 APK**，使用 `pack-sdk` 中的 `ILanguagePack` 与 `ITaskCallback`。Manifest 服务 action 固定为 `org.pocketide.pack.v1.LANGUAGE_PACK`，`meta-data` 名固定为 `org.pocketide.pack.v1.MANIFEST`。包内 JSON 必填：`schemaVersion=1`、`packId`、`version`、`minHostVersion=0.2.0-alpha.1`、`minSdk>=26`、`supportedAbis`、`languageIds`、`extensions`、`capabilities`、`entryService`、`source`、`license`、`dependencies`、`selfTests`。`initialize` 与 `cancel` 必须声明；每项声明能力给出能在目标设备运行的最小 `selfTests` 样例。声明 `run` 时另给 `largeOutput` 样例。
   如果目标设备拦截跨应用服务冷启动，可选填 `warmupActivity`，指向同包内已导出的无显示 Activity；参考示例包实现并实测从 IDE 启动后的重试行为。此入口不得自行授予文件权限。
3. 每个请求传 UUID `taskId`、`protocolVersion=1`、`operation`、`workspace`、`entry`。输入为 IDE 给的只读 ZIP 文件描述符，输出为可写 FD；不得把 SAF `content://` 当本地路径，不得直接改写用户工作区。结果 Bundle 包含 `taskId`、`protocolVersion`、`status`、`errorCode`、`message`、`exitCode`、`line`、`column`、`endLine`、`endColumn`、`resultPath`。位置为 1 起始 Unicode 码点，未知为 0。支持取消、超时、异常、服务重启与大输出；不支持的任务回 `unsupported`。
4. 安全核对包的 Android 权限、调用方 UID/签名、ABI、最低 API、原生依赖闭包、许可证和离线行为。若需要原生依赖，使用可复现的 Android 构建流程和目标设备实测，提交源码、版本锁定与真实证据。不得隐式上传源码、声称任意 `pip` 包可用、声称 GPU/DAP/LSP 已支持。
5. 本地运行 `.\build-local.ps1 -Product sample`（示例参考），然后对自己的包运行 `python tools/verify-language-pack.py --apk <你的APK> --manifest <包清单源码JSON>`；静态脚本不能替代设备验证。在目标 ARM64 设备上安装 IDE 和包，运行语言包面板验证，实测每项声明能力、错误位置、取消、大输出、权限拒绝、进程崩溃、版本不兼容和卸载恢复。未通过的能力从清单移除，并在 UI 中标为不可用。若声明 `debug`，还须实测断点、单步、变量、调用栈、停止和再次调试。记录设备型号、系统/API、ABI、输入、输出、任务 ID、日志及 APK 哈希。
6. APK 构建并签名后，按 [静态索引 Schema](static-index.schema.json) 填写 `schemaVersion`、`packs[]` 中的 `packId`、`version`、`apkUrl`、`apkSha256`、`signingCertificateSha256`、`apkBytes`、`minSdk`、`supportedAbis`、`source`、`license`。包内清单不填自己的 APK 哈希。静态索引只做目录，不能代替用户安装、签名确认和 IDE 验证。
7. 交付源码、构建脚本、包内清单、静态索引条目、第三方许可证、APK、SHA-256、签名指纹、设备测试报告和学生安装说明。说明下载/安装空间、支持的文件与命令、失败处理、禁用与卸载方法。若设备或工具链缺失，明确列为**未验证原型**，给出复测命令和剩余门槛，不发布 Release。

完成标准：IDE 在 ARM64 实机发现并经用户同意启用 APK，所有声明能力通过验证器和对应端到端场景；取消、禁用、卸载后编辑器可继续使用。只做宿主机编译或 Schema 检查不算通过。
