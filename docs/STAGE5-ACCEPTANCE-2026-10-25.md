# 阶段 5 结项记录（2026-10-25）

状态：**用户确认本阶段问题解决，阶段 5 结束**。本记录按 `docs/prompts/05-C与CPP语言包.md` 的验收项归档现有证据与未验证范围；没有进入阶段 6，也没有发布 Release。

## 交付物

| 交付物 | 大小 | SHA-256 |
| --- | ---: | --- |
| `artifacts/stage5-ide-draft-restore-f179da8a.apk` | 187,834 字节 | `f179da8ac6d4d0c19456d661e3f134778b07ca7e26dd01e2bd59c383c2431de1` |
| `artifacts/stage5-cpp-659c31fc.apk` | 73,555,370 字节 | `659c31fce05f6b0bc7690fcab1dc1f835adccc04dd45feac75417d62b39d7110` |

两者均为本地调试 APK。C/C++ 包是独立应用 `org.idecpp`，仅支持 `arm64-v8a`、最低 Android API 26、Clang/LLD 21.1.8，提供 C11、C17、C++17、C++20。IDE 不包含编译器。包清单在 `packs/cpp/src/main/res/raw/pack_manifest.json`，工具链锁定、构建复现、许可证和第三方声明在 `packs/cpp/`。安装与两文件课程项目的完整操作路径见 `packs/cpp/README.md` 和 `packs/cpp/examples/`。

主要改动位于 `packs/cpp/`、`ide/src/main/java/com/example/pocketpythonide/`、`workbench/src/main/java/com/example/pocketpythonide/` 和 `settings.gradle`：独立语言包、API v1 接入、C/C++ 着色与补全、构建/运行/取消、错误输出、安装后草稿恢复保护。最后一次 `:ide:assembleDebug` 离线构建通过；此前 `:packs:cpp:assembleDebug` 构建及清单 Schema 校验通过。本次核对两份 APK 的大小和 SHA-256 与上表一致，`git diff --check` 没有报告补丁空白错误。

## 验收证据

| 验收点 | 已有结果 |
| --- | --- |
| Android ARM64 原型 | MAG-AN00、Android API 36：C/C++ 单文件及两文件编译、链接和运行均成功；C17/C++20 课程项目分别输出 `course-c:42`、`course-cpp:42`。 |
| 输入、输出、失败与取消 | 标准输入/输出/错误、缺头文件位置、非零退出码、55 秒超时、用户取消和重启后继续运行已有实机记录；用户确认重复 `[stderr]` 和取消状态问题修复。 |
| API v1 | `initialize/analyze/complete/diagnostics/build/run/cancel` 及大输出、版本不匹配、崩溃恢复自检曾在 MAG-AN00 全部通过；裁剪工具链升级后用户再次确认自检通过。 |
| 补全与编辑 | 手动 API v1 补全可插入 `printf`；后续改为输入时快速提示、菜单请求跨文件候选。用户确认此前响应慢问题解决。 |
| 覆盖安装与数据 | IDE 均采用覆盖安装，原 SAF 工作区和课程目录保留；用户确认覆盖安装后 `return 0;` 缩进异常已解决。最后的草稿恢复修复会在升级后旧缓存与文件不同的情况下先显示文件并征询用户选择。 |

工具链来自经签名索引与包 SHA-256 核验的 Termux ARM64 包，15 个 `.deb` 压缩总量 86,787,304 字节。随 APK 打包的裁剪 ZIP 为 71,827,933 字节，MAG-AN00 上私有工具链目录约 244,172 KiB。运行课程程序采用只读 APK 原生库目录中的运行器加载临时 `.so`；构建产物保存在语言包私有任务目录，任务结束清理，不把 SAF URI 当本地文件路径。

## 验收边界与数据影响

- 原生编译运行的详细日志证据来自 MAG-AN00（Android API 36）。鸿蒙平板的编辑交互与问题修复有用户回报，但本记录没有最终 APK 在卓易通中的独立 HDC 原生日志，因此不把它写成完整的原生工具链回归。
- 未在 API 26 实机验证；为了保留现有 SAF 授权和文件，也没有清空 IDE 数据来做真正的全新安装实测。`packs/cpp/README.md` 已给出从空 IDE 到两文件课程项目的操作路径。这两项作为未测范围保留。
- 当前只支持单目标课程项目、受限大小的工作区快照、非交互式标准输入和最多 55 秒任务；不提供完整 LSP、调试器、通用第三方库管理或导出可执行产物。约 73.6 MB 的语言包体积仍是学生场景的成本；可选缩减方向见 `packs/cpp/README.md`。
- 只新增独立语言包及受控缓存；覆盖安装未清除 IDE 或语言包数据，未迁移或上传用户源码。Git 工作树在阶段开始前已有大量暂存、未暂存和未跟踪文件，本阶段没有 reset、clean、全量 add、提交或发布。正式 Release 前仍需复核第三方许可证与源码提供方式。

下一阶段若由用户提出，可沿用已验证的 Language Pack API v1 和本阶段的设备回归方式；本次结项不自动启动下一阶段。
