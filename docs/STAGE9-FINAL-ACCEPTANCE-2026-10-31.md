# 阶段 9 最终验收记录（2026-10-31）

## 发布与源码

- IDE 仓库：[PocketPythonIDE](https://github.com/LotusXanderYang/PocketPythonIDE)。已发布的 `v0.2.0-alpha.1` 对应源码提交 `930ff544e42a4586d32e4c1ff7a83cb665055bf2`，包含 IDE、Python 与 C/C++ 包的 APK；该历史 Release 还保留拆分前的 Lab 附件。
- 独立 Lab 仓库：[PocketPyTorchLab](https://github.com/LotusXanderYang/PocketPyTorchLab)。其 `v0.2.0-alpha.1` 发布 Lab APK，附件与上述 IDE 仓库历史附件字节相同。
- IDE [v0.2.0-alpha.2](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.2) 为编辑器外观、字体选择和语言包易读名称更新，沿用 Language Pack API v1 的 `0.2.0-alpha.1` 兼容基线。实测 APK 为 185,037 字节，SHA-256 `f0e26aa8602ec45076494b9778a63008256b8a6c0f665a6aaacdac310975020d`；包名 `com.example.pocketandroidide`，版本码 2，最低 API 26，证书 SHA-256 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`。确切源码提交与远端附件以 Release 页面复核结果为准。
- `v0.1.2-beta.5` 标签与附件未改动。Python、C/C++ 和 Lab 的 `alpha.1` 发布包不因 IDE `alpha.2` 更新而改变。

## 已完成验收

| 项目 | 证据与结果 |
| --- | --- |
| BTK-W00 全新安装 | 四包首次安装；Python、C/C++ 自检通过；新 SAF 工作区可创建和保存文件。 |
| 多文件 C 项目 | `main.c`、`helper.c` 与准确命名的 `.pocket-cpp.json` 构建运行，输出 `stage9:42`。 |
| Python 写回 | `stage9.py` 输出 `stage9-python`，`stage9_output.txt` 创建并在重开后保留。 |
| Lab | 极小训练、检查点保存及重读正常。 |
| 语言包卸载与恢复 | 仅卸载 C/C++ 包后，IDE 正确降级提示，SAF 文件保留；重装后自检和多文件运行正常。 |
| C/C++ 调试 | MRO-W00 上断点、变量、继续、暂停、异常停止与再次调试通过；详见[阶段 8 记录](STAGE8-CPP-DEBUG-HARMONY-FIX-2026-10-31.md)。 |
| 外观更新 | 用户在 ARM64 平板覆盖安装旧候选 `ide-palette-font-language-46994e9b.apk` 后，确认字体预设、配色色块、语言包易读名称及重启持久化生效；又覆盖安装确切 `alpha.2` 候选 `f0e26aa8`，确认原工作区、设置持久化、语言包识别及已有项目运行正常。 |
| 静态目录 | [JSON 目录](language-pack-index.json)只列已发布且验证过的 Python、C/C++ 包；`python tools/verify-static-pack-index.py` 通过。 |

设备界面反馈由用户提供，未建立可直接读取的 BTK-W00 ADB/HDC 日志。MRO-W00 的鸿蒙兼容环境存在部分原生目录拒写的情况，应选实际可写的 SAF 目录。BTK-W00 为鸿蒙 4.2 Android 内核设备，未提供相应原生目录，因此未重复拒写测试。

## 体积、兼容性与边界

`alpha.1` 的下载大小：IDE 267,553 字节、Python 11,241,738 字节、C/C++ 103,328,609 字节、Lab 158,133,259 字节。BTK-W00 系统应用存储页在测试后显示 IDE 298 KB、Python 47.02 MB、C/C++ 444 MB、Lab 810 MB。这些存储读数包含应用数据/缓存，不能等同下载量。旧 `v0.1.2-beta.5` APK 为 158,103,416 字节；基础 IDE 变小来自语言运行时与工具链拆分，并不表示完整环境只有基础 APK 的体积。

目标为 ARM64、Android API 26 及以上。API 26 实机、16 KB 内存页设备、首次全新启动耗时和 C/C++ 编译中取消仍未实测。Python 包不包含 PyTorch；Lab 用于离线 CPU 小模型训练。C/C++ 面向受限的单目标项目，不提供通用第三方库管理。以上边界在 README 与 Release 中保持明确。

## 升级、迁移与回退

同包名升级应直接覆盖，并核对签名；卸载会清除应用私有文件。新 IDE 包名 `com.example.pocketandroidide` 可与旧 `com.example.pocketpythonide` 并存，旧应用私有工作区不会自动迁移。应在新 IDE 重新授予原 SAF 目录权限，迁移异常时继续使用未改动的旧版及原工作区；重要私有文件先导出。语言包卸载不删除外部 SAF 工作区文件。发布文件、具体哈希与源码提交均以对应 GitHub Release 页面为准。

发布前许可证材料、锁定工具链审计及四包原始哈希见[阶段 9 发布前记录](STAGE9-RELEASE-PREFLIGHT-2026-10-31.md)；该文件保留历史检查状态，不作为当前发布状态来源。
