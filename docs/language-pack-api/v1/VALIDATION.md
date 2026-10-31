# 阶段 3 验证记录（2026-10-23）

## 设备与产物

实机：Honor MAG-AN00，Android API 36，ABI `arm64-v8a,armeabi-v7a,armeabi`，ADB 序列号 `AF3GUT4516002511`。IDE、示例包和独立 UID 权限探针均为本阶段调试 APK；旧 `v0.1.2-beta.5` 与 Lab 未卸载或覆盖。IDE 现有数据通过 `adb install -r` 保留。阶段 3 构建前的 IDE APK 备份仍在 `.toolchain/ide-debug-before-stage3.apk`。

| 产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ide/build/outputs/apk/debug/ide-debug.apk` | 116,973 | `7779759b8ec5121de90217aeffaf15d20f53775cabeceae4fcf7e3990d00ee77` |
| `sample-pack/build/outputs/apk/debug/sample-pack-debug.apk` | 26,151 | `96c0b49b15533ca451ca7cc55de2dc38718ae8c0561a1309f3e8cf1eacdbfbda` |
| `pack-probe/build/outputs/apk/debug/pack-probe-debug.apk` | 12,971 | `9aa26320e3a54d0a8efa2ce1cb2b4bbd7686bdf5f1dc01bd1349cc2cf15168d8` |
| `pack-sdk/dist/pack-sdk-v1-source.zip` | 3,263 | `aa61348f7485f58738fa413e280ae9de69377e8348551fb179c3c9879a15898c` |

三个调试 APK 的签名证书 SHA-256 均为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`。示例包不含原生库，只声明 `.echo`，不声称支持 Python、C/C++ 或 PyTorch。

## 实测结果

`D:\Anaconda\python.exe .\tools\verify-language-pack.py` 通过包清单和静态索引的有效／无效 Schema 示例、APK 清单资源、无原生库与 SDK 源码 ZIP 一致性检查。`D:\Anaconda\python.exe .\tools\verify-language-pack-device.py --serial AF3GUT4516002511 --operate-ui` 使用上述最终 APK 哈希通过；机器可读结果保存在 `.toolchain/language-pack-device-report.json`。

| 阶段 3 门槛 | 实机结果 |
| --- | --- |
| 发现与用户确认启用 | IDE 找到 `org.pocketide.sample.echo 1.0.0`，显示来源、许可证、证书、已安装 APK 哈希、权限和能力；未验证前无运行能力 |
| 独立 UID 权限拒绝 | 探针调用服务得到 `PERMISSION_DENIED_OK` |
| 声明能力最小实测 | `initialize`、`analyze`、`complete`、`run`、`cancel`、`diagnostics` 均显示“通过”；诊断样例 `ERROR` 对应第 1 行第 1 列 |
| 大输出、崩溃与版本不兼容 | `largeOutput`、`crashRecovery`、`versionMismatch` 均显示“通过”；崩溃后重新绑定并初始化成功 |
| 编辑器端到端调用 | 新建 `stage3_probe.echo`，输入 `ECHO stage3-ui`，运行面板显示 `stage3-ui` 和“运行完成”；最终 IDE APK 再次复验相同结果 |
| 错误可追溯 | 将同一文件临时改为 `ERROR`，面板显示 `SOURCE_ERROR · 1:1`、包 ID 与任务 UUID `459df982-d51f-427b-936e-c573774b17a7`；随后恢复原文件并再次运行成功 |
| 禁用 | 语言能力面板显示“未启用”，保存的执行批准记录清空 |
| 卸载与同 APK 重装 | 卸载前检查示例包 `files` 为空且没有 `shared_prefs`；卸载后 IDE 仍能打开 `stage3_probe.echo`，运行提示需安装语言包；重装**完全相同 APK** 后仍显示“未启用”，`language_packs.xml` 为空，必须重新验证 |

首次测试发现 Honor 系统会以 `Service starting has been prevented by iaware or trustsbase` 拦截跨应用服务冷启动。示例包新增可选 `warmupActivity`，宿主在绑定失败时启动同包无显示 Activity 并重试；最终脚本在权限测试后强制停止示例包，验证了冷启动恢复。另发现卸载广播在该设备未清除批准记录；现以安装时间连同包名、证书、APK 哈希和版本钉住批准，并在查询时清理过期记录。同 APK 卸载重装已实测不会自动恢复权限。已使用过的扩展名独立保存，卸载后源码仍可编辑。

设备测试结束状态：IDE、示例包和既有 Lab 保留安装；示例包未启用；仅测试用权限探针已卸载。`stage3_probe.echo` 是本轮新建的验证文件，保留在 IDE 私有 `files/scripts/` 中供复查。旧 beta.5 与 Lab 数据未迁移、删除或修改。

## 鸿蒙兼容环境复测（2026-10-23）

设备为 HUAWEI MatePad Pro `MRO-W00`，原生系统 `OpenHarmony-6.1.1.120`（鸿蒙 API 24），HDC 序列号 `3XTYD25B19200691`。三个 Android 调试 APK 均由用户通过卓易通安装；卓易通版本 `1.0.10.81`。原生 HDC 的 `install` 不接受 APK，电脑端 ADB 未发现该兼容环境，因此本次应用内结果由用户操作反馈，设备信息及初次界面状态由 HDC 核对。

初装 IDE APK（上表哈希）时，IDE 能发现示例包，显示的已安装 APK SHA-256 与 `96c0b49b15533ca451ca7cc55de2dc38718ae8c0561a1309f3e8cf1eacdbfbda` 一致。首次验证中，`initialize`、`analyze`、`complete`、`run`、`cancel`、`diagnostics`、`largeOutput`、`versionMismatch` 通过；`crashRecovery` 因 `PACK_BIND_FAILED` 失败。初次启动权限探针也曾显示 `PROBE_BIND_FAILED`。问题集中在示例服务被故意杀死后的立即重绑，不能把这次结果记为完整通过。

IDE 的崩溃恢复验证现对 `PACK_BIND_FAILED` 作最多四次、有界间隔的重试；未通过时仍撤销 `run` 能力。只重建并覆盖安装 IDE，新的 `ide-debug.apk` 为 **117,295 字节**，SHA-256 `e76b5a270bde61293b5ec4d838c9ebce91f28c84f148ddecd5b320ceb701d230`，签名证书 SHA-256 仍为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`；示例包和探针未改动。`build-local.ps1 -Product ide` 构建成功，`verify-language-pack.py` 静态校验通过。

用户覆盖安装后连续运行三次完整验证，均反馈所有项目通过；启用语言包后，新建 `harmony_probe.echo` 执行 `ECHO harmony-ok`，输出面板显示 `harmony-ok`；独立权限探针显示 `PERMISSION_DENIED_OK`。因此这台设备上的卓易通兼容环境已验证包发现、AIDL 能力调用、崩溃后恢复、编辑器端到端运行及非宿主 UID 拒绝。鸿蒙设备上的禁用、卸载／同 APK 重装撤销和 Android 原生 ADB 自动化未执行，不能外推为原生鸿蒙 HAP 兼容。

## 限制与后续依赖

SDK 的 AIDL 与 Java 模块已随 IDE、示例包及探针编译，源码 ZIP 可供外部项目引用。单独生成 AAR 时，离线 Gradle 缓存缺少 `com.android.tools.lint:lint-gradle:31.13.0`，`extractDebugAnnotations` 无法运行；因此本次未交付预构建 AAR。该限制不影响上述 APK 与设备验收，但第三方开发者当前应引入 `pack-sdk/` 源码模块或源码 ZIP。原生 Android 已验证一台 ARM64/API 36 设备；鸿蒙设备的结果来自卓易通 Android 兼容环境。Android API 26 和 16 KB 内存页设备尚无阶段 3 实机结果。阶段 4 可基于已验证的 API v1 开始，但本轮不执行阶段 4 或发布 Release。
