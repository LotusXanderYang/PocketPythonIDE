# 阶段 5 检查点（2026-10-24）

> **结项更新（2026-10-25）：**用户确认问题解决并宣布阶段 5 结束。最终交付物、已验证证据与未测范围以 [阶段 5 结项记录](STAGE5-ACCEPTANCE-2026-10-25.md) 为准。以下是过程检查点，包含当时尚未验收的历史状态和旧安装包，请勿把它们当作当前待办。

## 2026-10-25 覆盖安装后缩进异常续记

用户反馈：两次覆盖安装 IDE 后，自己编写的示例代码中 `return 0;` 所在行首次打开时各多一级缩进；用户手动删去异常缩进并保存后，再次打开不再复发。鸿蒙设备上无法从文件管理器读取原始文件内容；本轮 HDC/ADB 均未发现连接设备，因此**没有拿到首次打开前的磁盘字节**。代码检查显示：`loadScript()` 原样读入工作区文件，语法着色不修改字符，但 `onCreate()` 在此之后无条件用 Android 保存的 Activity 编辑器状态覆盖已加载文本。这是与“覆盖安装后首次打开出现，保存后稳定”相符的高风险路径；尚不能证明多余空格最初怎样进入旧编辑缓存。

修复：`WorkbenchActivity` 在加载文件后比较旧编辑缓存、磁盘文件 SHA-256 基线和应用安装更新时间。同一安装版本且磁盘基线未变时，自动恢复未保存草稿；安装升级或磁盘内容变化时先显示磁盘版本，如旧缓存不同则弹窗让用户明确选“保留工作区文件”或“恢复草稿”，不再静默覆盖。选择恢复也不会自动写回，仍需用户保存。此前版本缺少校验字段时走保守弹窗。IDE 离线 `:ide:assembleDebug` 构建通过；新包 `artifacts/stage5-ide-draft-restore-f179da8a.apk`，187,834 字节，SHA-256 `f179da8ac6d4d0c19456d661e3f134778b07ca7e26dd01e2bd59c383c2431de1`。C/C++ 语言包不变。下一次请用户**直接覆盖安装**新 IDE，重开原文件，若出现草稿提示先选“保留工作区文件”，核对 `return 0;` 缩进，再保存、重启复核；未在鸿蒙设备实测前不要称问题彻底验证通过。不进入阶段 6 或发布 Release。

## 2026-10-25 补全速度修复续记

用户反馈前一版自动补全响应太慢。代码审查确定两个叠加原因：`PackRegistry.enabled()` 在每次输入后的能力检查中反复重新发现语言包并读取整个约 73 MB APK 计算 SHA-256；自动补全又为每个停顿重复遍历 SAF、复制源文件、打 ZIP、绑定语言包服务。电脑上对该 APK 单次 SHA-256 实测约 81.4 ms；这不是平板实测延迟。

已在 `ide/src/main/java/com/example/pocketpythonide/PackRegistry.java` 缓存启用且验证过的描述符，每次仅轻量检查包的安装/更新时间；更新或卸载会使缓存失效，重新走签名与 APK 哈希核验。新增 `workbench/src/main/java/com/example/pocketpythonide/CppCompletions.java`，输入时本地即时提供 C/C++ 关键字、常见符号和当前文件声明；“文件 → 补全建议”仍通过 API v1 请求语言包以取得跨文件项目候选，并更新同一弹窗。连续输入不再对 SAF 与语言包发起后台请求。保留原有用户数据及之前交付的包。

IDE 离线 `:ide:assembleDebug` 构建通过；约 95 KB C 源码的本地主机微基准为每次候选计算平均 1.13 ms，首项 `printf`。新包 `artifacts/stage5-ide-fast-completion-c385f662.apk`，186,663 字节，SHA-256 `c385f6624e4eec20571445bedecdf5df19aeb3bd755eb59a53a4e1dd81918579`。C/C++ 语言包无需更新，仍用 `artifacts/stage5-cpp-659c31fc.apk`。新版**尚待平板覆盖安装后实测输入响应、手动项目候选、运行能力**。下次直接覆盖安装 IDE（不可卸载以免丢 SAF 授权），在 `.c` 输入 `pri` 看 `printf` 是否迅速弹出并可 Enter 插入，再从菜单请求补全核对项目声明。此处不进入阶段 6 或发布 Release。

## 2026-10-25 补全交互续记

用户反馈前一轮在平板上查验的问题均正常，希望 C/C++ 补全恢复为此前 Python 编辑器的输入即提示方式。已在 `workbench/src/main/java/com/example/pocketpythonide/WorkbenchActivity.java` 将已验证 `complete` 能力的 C/C++ 文件接入输入停顿后的异步补全；`EditorCompletionPopup.java` 复用光标旁列表，支持 ↑↓、Enter、Ctrl+K 和触屏点击。“文件 → 补全建议”现在也打开此列表。后台读取不超过约 6 MiB 的 C/C++ 源码及项目配置到缓存快照，覆盖当前未保存的编辑文本；不会自动保存或写回 SAF。连续输入会取消过期请求并检查文件、文本、光标，避免旧结果插入。

IDE 离线 `:ide:assembleDebug` **构建通过**，新包为 `artifacts/stage5-ide-autocomplete-415f5597.apk`，185,362 字节，SHA-256 `415f5597174439b065cb105f9ed4240b9df93515f5adb6032f205065486c1799`。语言包源码与 APK 未改，可继续使用 `artifacts/stage5-cpp-659c31fc.apk`。新 IDE 的自动补全交互**尚待用户在平板覆盖安装后验证**；不要把编译通过写成实机通过。下次应请用户在 `.c` 文件输入 `pri`，观察光标旁是否自动出现 `printf`，再用 ↓/Enter 插入与 Ctrl+K 或底部点击关闭；无需从菜单触发。直接覆盖安装 IDE，保留工作区和语言包数据，不进入阶段 6 或发布 Release。

用户要求只执行 `docs/prompts/05-C与CPP语言包.md`，遵守 `README.md`、`00-共同约束.md`，保护已有 Git 和设备数据，不自动进入阶段 6、不发布 Release。本轮最后要求：先交付安装包，记录进度，下次直接继续。**阶段 5 尚未最终验收**；鸿蒙平板的最终候选补全和原生工具链未验证。

## 下次从这里开始

1. 先读 `docs/prompts/README.md`、`docs/prompts/00-共同约束.md`、`docs/prompts/05-C与CPP语言包.md`、本文件和 `packs/cpp/README.md`；运行 `git status --short`。本仓库从阶段 1 起已有大量暂存、未暂存和未跟踪文件，本轮未 reset、clean、全量 add、提交或发布。不要覆盖它们。
2. 核对本轮交付的两个 APK 是否仍在且哈希一致：

   | 文件 | 字节 | SHA-256 |
   | --- | ---: | --- |
   | `artifacts/stage5-ide-7e6f3d9b.apk` | 185,313 | `7e6f3d9be077315da9a08353acea3ba99e2140fc5680aa6f291bbefbfce1d52d` |
   | `artifacts/stage5-cpp-659c31fc.apk` | 73,555,370 | `659c31fce05f6b0bc7690fcab1dc1f835adccc04dd45feac75417d62b39d7110` |

   APK 来源分别为 `ide/build/outputs/apk/debug/ide-debug.apk` 和 `packs/cpp/build/outputs/apk/debug/cpp-debug.apk`。用户要把这两包安装到鸿蒙平板：先**直接覆盖安装** IDE，不卸载旧 IDE；再安装独立语言包。两个包均是本地调试签名，未发布 Release。
3. 向用户取得平板安装后的现象，继续验证最终候选：在 IDE“语言包”选择 `org.idecpp` →“验证并启用”，核对所有声明能力；在 `.c` 文件的 `pri` 后放光标，点“文件 → 补全建议”，应出现并能插入 `printf`；`#include` 与 C/C++ 关键字应按 C/C++ 着色。若卓易通禁止执行，收集实际错误，不声称平板支持。可用 `packs/cpp/examples/` 里的课程样例传到平板可写 SAF 目录，分别构建/运行 C17 与 C++20 两文件项目。平板 HDC 先检查连接；此前用户认为远程读取界面太慢，优先请其操作并回报现象，必要时才抓日志。
4. 如平板实测发现问题，修复后重建两个 APK，重新计算哈希并更新本文件与交付路径。若均通过，再写阶段 5 最终验收记录，按阶段 5 条件报告测试、限制、大小、许可证和数据影响。**不要进入阶段 6 或发布 Release。**

## 本轮代码与产物

- 新增独立 `packs/cpp/` 应用，包名 `org.idecpp`，`minSdk 26`、`targetSdk 35`、仅 `arm64-v8a`。Clang 21.1.8/LLD 来自锁定的 Termux ARM64 包，编译器和运行器作为 APK native library 安装；运行时把课程项目编译为 PIC `.so`，由只读运行器 `dlopen` 执行。包服务在 `:compiler` 进程实现 Language Pack API v1 的 `initialize/analyze/complete/diagnostics/build/run/cancel`。项目从 IDE 的 ZIP 快照解压到包私有任务目录，任务结束清理；SAF URI 不传给编译器。`.pocket-cpp.json` 支持多文件、`c11/c17/c++17/c++20`、包含目录、非交互式标准输入和参数。
- 新增 `packs/cpp/probe/` 的 Termux 编译验证、Android 执行路径探针、官方包锁定/下载/提取脚本；`packs/cpp/prepare_runtime.py` 生成裁剪的工具链 ZIP；`packs/cpp/build-native.ps1` 提供 NDK r30 重建小型助手的路径；`packs/cpp/README.md` 记使用和复现步骤；APK 内含 `THIRD_PARTY_NOTICES.md` 与许可证文本。
- IDE/Workbench 增加 C/C++ 基础语法着色、经 API v1 请求的光标位置补全菜单，以及构建/运行失败时保留错误说明。修复取消后状态停在“取消中…”和编译错误 `[stderr]` 重复刷屏的问题。
- `packs/cpp/src/main/assets/toolchain.zip` 和较大的 ARM64 原生文件由脚本生成、已在 `.gitignore` 中忽略；`artifacts/` 为本地交付文件。`settings.gradle` 增加 `:packs:cpp` 与测试探针模块。

## 已验证的证据

- Git 开始前为脏工作树；未清理既有改动。MAG-AN00：Android API 36，ARM64，ADB 序列号 `AF3GUT4516002511`。设备原有 IDE APK SHA 前缀 `166aa444`，与阶段 4 最终包一致。覆盖安装一律用 `adb install -r`，未卸载 IDE；新建独立 SAF 测试目录 `/sdcard/Documents/PocketIDEStage5`，原有 `main.py`、`.echo` 文件未被覆盖。手机旋转设置从 `user_rotation=0` 暂改横屏，结束时已恢复为 `0`。
- 官方 Termux `InRelease` 用设备已装密钥验证为 `Good signature from Termux Releases (Termux automatic builds)`，签名指纹 `CC72CF8BA7DBFA0182877D045A897D96E57CF20C`；`Packages.gz` SHA-256 与签名索引一致，15 个 `.deb` 均按索引 SHA-256 核验。闭包压缩大小 **86,787,304 字节**、元数据安装大小 **518,388 KiB**。裁剪工具链 ZIP **71,827,933 字节**，SHA-256 `10f416235177746b69c6d25c10e35b882c6dc460b294f21f809550a7a128e786`，3641 文件、解压原始内容 **240,296,493 字节**；MAG 上实际私有目录占 **244,172 KiB**，裁剪前 **316,517 KiB**。
- 隔离 Termux 原型 `hello.c`、两个 C 文件、`hello.cpp`、两个 C++ 文件编译运行成功，输出 `hello-c`、`multi-c:42`、`hello-cpp`、`multi-cpp:42`；标准输入/输出/错误和退出码 7、缺头文件源码位置 1:10 已记录在忽略目录 `.toolchain/stage5-probe/termux-report.json`。没有改动 Termux 已安装包，只用了其单独测试目录。
- Android 执行探针：目标 API 28 的应用私有文件可执行，目标 API 35 直接执行同处 ELF 报 `Permission denied`；目标 API 35 可从 APK nativeLibraryDir 启动运行器，并从私有目录加载生成的 `.so`。C/C++ 单/多文件 `.so` 四组在 MAG 上运行成功。
- 独立 APK 首次安装，C11 与 C++17 单文件在 `org.idecpp` 自己的应用沙箱内编译运行，输出 `independent-c`、`independent-cpp`。IDE API v1 自检曾全部通过：初始化、分析、补全、诊断、构建、运行、取消、大输出、版本不匹配、崩溃恢复。升级裁剪工具链后，用户再验证自检全通过；设备 `files/usr/.complete` 与新 ZIP SHA 一致，`usr.next/usr.previous` 无残留。
- 用户在 MAG-AN00 的 IDE SAF 工作区完成 C17 与 C++20 两文件项目，构建均成功，运行分别输出 `course-c:42`、`course-cpp:42`。`compile_error.c` 指向第 1 行缺头文件；`exit_7.c` 输出 `runtime-failure-probe` 且显示失败；`loop.c` 取消后可继续运行正常文件。针对用户发现的重复 `[stderr]` 和“取消中…”问题修复后，用户确认两项及目录保留都通过。用户对约 55 秒超时与重启后 C 项目继续运行回复“过”，按通过记录。
- **最终交付 APK 是上述验证后进一步加上光标补全和架构错误分类的版本**，编译与 JSON Schema 校验通过，尚未在平板上做补全和原生工具链最终验收；不要把前一版本的手机实测误写成最终版本的平板实测。

## 已知限制与风险

- 工具链体积仍较大：独立语言包 APK 约 73.6 MB、设备私有数据约 244 MiB。进一步定制只含 AArch64 后端的 LLVM 或 C/C++ 拆包是可选后续缩减，尚未实现。
- API v1 文本输出上限 4 MiB；构建产物只在包私有任务目录暂存，任务结束清理，不导出 ELF/APK。补全初版限关键字、常用标准库名称、项目源文件声明；不提供完整 LSP/调试。程序标准输入来自配置，没有交互式终端。当前一次只启用一个执行语言包。
- 只在 Android API 36 的 MAG-AN00 完成原生端验证，未在 API 26 设备和鸿蒙卓易通环境确认。为了保护现有 IDE 的 SAF 授权和文件，没有清空 IDE 数据来模拟真正“全新安装”；从空 IDE 安装的操作路径已写入 `packs/cpp/README.md`，平板将提供独立设备验证机会。
- 许可证清单、锁定版本与文本在 APK 和仓库中。发布正式 Release 前仍需按其许可证复核来源和对应源码提供方式；本轮没有发布行为。
