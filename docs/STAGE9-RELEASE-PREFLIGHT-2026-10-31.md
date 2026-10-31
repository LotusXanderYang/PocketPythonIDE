# 阶段 9 发布前清单（2026-10-31）

状态：**本地准备，未发布**。本轮已核对远端 `main` 提交 `6282cd2f2ab789366e7915a3336135c67a706b42` 并恢复本地 `HEAD` 指向它，未改变工作树或旧暂存内容；当前源码仍包含大量未提交改动，下表 APK 尚不能绑定到包含这些改动的确切提交，因此不满足发布验收。没有改写 beta.5 标签或 Release，没有清除设备数据。续作细节见[发布任务记录](RELEASE-RESUME-2026-10-31.md)。

## 当前源码离线构建产物

使用工作区 JDK 17、Android SDK 35 和 Gradle 8.13，离线执行 `:ide:assembleDebug :lab:assembleDebug :packs:python:assembleDebug :packs:cpp:assembleDebug`。`aapt dump badging` 核对包名、版本、API；`apksigner verify --print-certs` 核对签名；`Get-FileHash -Algorithm SHA256` 核对 APK。四个产物均为 `arm64-v8a` 目标、最低 API 26；IDE 自身没有原生库。它们是**调试签名候选包**，并非可发布的 Release 附件。

| 产品 | 本地候选 APK | 版本 / 包名 | 字节 | SHA-256 |
| --- | --- | --- | ---: | --- |
| IDE | `artifacts/stage8-debug-ide-final-bf9ab118.apk` | `0.2.0-alpha.1` / `com.example.pocketandroidide` | 267,553 | `bf9ab118fee104756453337f2b4c73163d870aad0a25e7deff06ffec8c5b5479` |
| Lab | `artifacts/stage9-lab-signed-85e4d201.apk` | `0.2.0-alpha.1` / `com.example.pocketpythonlab` | 158,133,259 | `85e4d201a43297df763afb8ab12be775e8624c50865d6f62a81ce3b7d597ac71` |
| Python 基础包 | `artifacts/stage9-python-cf601df2.apk` | `0.1.0-stage8` / `com.example.pocketpythonstd` | 11,241,738 | `cf601df29e71f1a20fe501208f5fb2c2b6c2eef814ac4762f9404dcbfca5f2aa` |
| C/C++ 包 | `artifacts/stage9-cpp-notices-8ca988d8.apk` | `0.1.0-stage8` / `org.idecpp` | 103,328,609 | `8ca988d8c7d50968e552023f88855296d66c8e2db3c913e50478b568d07d3411` |

四包的签名证书 SHA-256 均为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`。首次阶段 9 构建发现 Lab 默认使用另一签名；已将 `lab/build.gradle` 改为沿用项目保留的调试密钥，重建后证书一致。误签产物 `artifacts/stage9-lab-d09f7671.apk` **不得用于覆盖安装**，保留仅供追溯。

## 体积与测量边界

- IDE 基础 APK 267,553 字节；Python 包 11,241,738 字节；C/C++ 调试包 103,328,609 字节；Lab 158,133,259 字节。IDE 轻量化来自将语言运行时、编译器和 LLDB 移入独立 APK，并未在首次启动时隐式下载。
- 只读查询旧 [v0.1.2-beta.5 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.1.2-beta.5) 的 GitHub 附件元数据：旧 APK 为 **158,103,416 字节**、SHA-256 `bb898c236cb14f0218a799d7217432c65144b2ad37b2248cc7075c77f1b63ccb`；另有运行时 ZIP **164,754,701 字节**、SHA-256 `45e7c3baa507b2c6b6d8b0338b5ef22d947b4cfed6e618234a5f57f4f527f7a4`。IDE 基础 APK 约为旧 APK 的 **0.17%**；这一对比只说明编辑器本体已拆分，不代表 Python、C/C++ 或 PyTorch 所需的全部下载量也缩到该尺寸。旧 Release 和附件未修改。
- Lab 在 MAG-AN00 上已有实测：解包及一次训练后 `files` 约 627 MiB、缓存已存在时 Activity 启动 351 ms、80 轮小样例约 19.1 秒。见[阶段 7 验收](STAGE7-ACCEPTANCE-2026-10-25.md)。首次全新安装解包耗时没有测量，不填推断值。
- BTK-W00 在本轮全新安装并完成样例后，系统“应用信息 → 存储”显示：IDE **298 KB**、C/C++ 包 **444 MB**、Lab **810 MB**、Python 包 **47.02 MB**。这是设备界面的应用占用读数，可能包括数据和缓存，不能与 APK 下载字节数相加等同；用户未给出安装前读数。首次启动耗时未测。

## 已有设备证据与未完成验收

- MAG-AN00：阶段 5 C/C++ 编译运行、阶段 7 Lab 训练与检查点、阶段 8 Python 和 C/C++ 调试有实机记录。MRO-W00：Python 工作区读写、C/C++ 调试断点、暂停、异常停止，以及 SAF 可写目录有记录；原生鸿蒙目录曾拒绝创建文件。详见各阶段验收与[阶段 8 修复记录](STAGE8-CPP-DEBUG-HARMONY-FIX-2026-10-31.md)。
- 阶段 9 的**全新安装 → 打开工作区 → 新建多文件项目 → 安装和卸载语言包 → 构建运行**已在 BTK-W00 由用户连续操作验证；Lab 新候选 `85e4d201` 也完成极小训练与检查点重读。ADB/HDC 均未建立目标会话，以下现象来自用户的界面反馈，不是直接读取设备日志。BTK-W00 的精确 API 级别与首次启动耗时仍未测得。
- BTK-W00 空白设备已手动首次安装四包：IDE `bf9ab118`、Lab `85e4d201`、Python `cf601df2`、C/C++ `4d76dca1`。用户在新建可写 SAF 工作区 `PocketStage9` 对 Python 与 C/C++ 执行验证和启用，两包完整自检通过，新建与保存文件正常。随后创建 `main.c`、`helper.c` 与项目配置，初次误把 `.pocket-cpp.json` 命名为 `pocket-cpp.json`，导致单文件编译、运行时缺少 `twice`；修正文件名后多文件构建运行成功并输出 `stage9:42`。新建 `stage9.py` 在同一 SAF 工作区写出 `stage9_output.txt`，输出与重开后的文件内容均正常；Lab 极小训练、运行记录及检查点保存和重读也由用户确认正常。用户只卸载 C/C++ 包后，IDE 正确提示缺少已验证运行能力，原 SAF 文件保留；重新安装声明更新版 `0e3356ac` 后验证启用正常。BTK-W00 为鸿蒙 4.2 Android 内核设备，不提供原生鸿蒙目录，故该项拒写测试不适用；MRO-W00 的原生目录拒写证据仍见根 README 与早期验收。
- 本地 `HEAD` 已与远端 `main` 的旧提交一致，但当前大部分阶段 2–9 源码尚未提交；不能建立“当前源码提交 ↔ APK ↔ 哈希 ↔ GitHub 文件”对应关系。需先审阅并选择性提交现有工作树，再从确切提交重建或核对 APK。不能为凑齐对应关系盲目提交全部已有文件。
- C/C++ 包的第三方声明先前缺少 15 个 LLDB 闭包包名，现已补上锁定清单全部 30 包及版本。`python tools/audit-cpp-release-notices.py` 通过；`python tools/audit-locked-deb-notices.py` 用锁定 `.deb` 的 SHA-256 核对 24 份原始版权或许可文件，并确认它们已进入新 APK。其余锁定归档本身未带逐包版权文件，APK 保留通用许可证文本；对应源码和构建脚本获取路径见[第三方源码指引](THIRD-PARTY-SOURCE.md)。这是可复核的工程许可材料盘点，不替代法律意见。API 26、16 KB 页设备及调试构建中取消未做实机验证。新增原始许可文件的 `8ca988d8` 已在 BTK-W00 覆盖安装、完整自检通过，两文件 C 项目仍输出 `stage9:42`。

## 静态目录与发布文案草案

`docs/language-pack-index.json` 当前为 `packs: []`，因为新版语言包均未发布。运行 `python tools/verify-static-pack-index.py` 校验 Schema、字段、重复项与 Release URL。发布后才将实际 APK URL、哈希、签名、工具链大小、能力、兼容条件和最近实测设备加入目录。

拟议标题：`Pocket Android IDE / Python · PyTorch Lab 0.2.0 Alpha（待验收）`。说明应列出四个独立 APK、ARM64/API 26 限制、安装顺序、SAF 工作区操作、调试限制与上述 SHA-256。升级时同包名必须保持签名并直接覆盖；旧 `com.example.pocketpythonide` 可并存，工作区须由用户在新应用中重新授权。回滚可继续使用不变的 `v0.1.2-beta.5`，新应用私有数据需先自行导出。**这段仅是草案，不授权创建标签或 Release。**
