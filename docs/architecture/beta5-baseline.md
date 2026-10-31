# beta.5 可回退基线（阶段 1，2026-10-22）

## 版本和证据边界

- [GitHub Release `v0.1.2-beta.5`](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.1.2-beta.5) 已在本阶段只读核对：公开预发布，标签指向提交 `62d7ec1ee6defa0fb622fc05f0c103a019fc10cc`。标签下的 `app/build.gradle` 写有 `applicationId com.example.pocketpythonide`、`versionCode 9`、`versionName 0.1.2-beta.5`、`minSdk 26`、`arm64-v8a`；本地文件的这些值相同。用本地 Android SDK 35 的 `aapt dump badging` 独立读取已校验 APK，包名、版本码、版本名、最低 SDK 与 ABI 也相符。
- 本地分支为 `main`，**没有任何本地提交，因此 `HEAD` 不存在**。本地既无可用的 beta.5 Git 提交，也不能把当前工作区称作标签源码。远端标签及 Release 附件是外部回退锚点；使用前应再次核对标签提交和附件哈希。
- 本地 Git 起始状态：51 个已暂存新增文件；其中 `README.md`、`app/build.gradle`、`EditorEditText.java`、`MainActivity.java`、`NativePythonBridge.java`、`ide_runner.py`、`tools/fetch-runtime.ps1` 又有未暂存改动。另有未跟踪的 `EditorCompletionPopup.java`、`PythonCompletions.java`、`PythonEditorRules.java`、`WorkspaceFiles.java`、`docs/`、两个 Java 冒烟测试及 `tools/install-openharmony-hdc.py`。这些都是开始阶段 1 前已有内容；本阶段不暂存、提交、重置、清理或覆盖它们。

## 已有产物（本阶段只读测量）

| 对象 | 字节 | SHA-256 | 关系 |
| --- | ---: | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 158,103,416 | `bb898c236cb14f0218a799d7217432c65144b2ad37b2248cc7075c77f1b63ccb` | 与 beta.5 Release APK 附件摘要一致；调试签名 |
| `app/src/main/assets/runtime.zip` | 164,754,701 | `45e7c3baa507b2c6b6d8b0338b5ef22d947b4cfed6e618234a5f57f4f527f7a4` | 与 Release 附件及 `runtime.json` 的 `asset_sha256` 一致 |
| `app/src/main/jniLibs/arm64-v8a/libnativepythonbridge.so` | 8,784 | `fcef0e6e7b5122f56ebbb265c5f2c5488eba0722681b2fa6437423fbe8345774` | 本地桥接库，非单独 Release 附件 |
| `.toolchain/runtime-debs/python_3.14.6-1_aarch64.deb` | 4,823,256 | `240b105c35121ff81b623691f6ac82ed72b9395b28cb54dab3f740163b3cb726` | 与 `runtime.json` 包清单一致 |
| `.toolchain/runtime-debs/python-torch_2.14.1-2_aarch64.deb` | 36,409,748 | `b5b99672073984adf6f46e88b4fba7371852f3daf87eda36eb4f4ab20ad7a3ab` | 与 `runtime.json` 包清单一致 |
| `.toolchain/device-scripts-backup-20261020-refresh.tar` | 2,560 | `dc98f2636300b4b5ca359ae9c9b675216f116ff1fcb244296138f283d3df792f` | 既有设备脚本备份；不能代替迁移导出验证 |

`docs/BUILD-COMPLETE-2026-10-21.md` 记录的是更早的 158,024,420 字节 APK，哈希 `0ad234…`；该记录仍是构建史，但**不是**当前 beta.5 Release APK。`.toolchain/` 中的 `.deb`、wheel、WSL/Docker 构建对象、断点日志、旧 APK 及备份均应保留。阶段 1 没有重新生成 APK 或运行时，也没有重新编译 PyTorch。

## 来源、许可证和复现入口

- 应用自有源码：远端标签提交如上；许可证为根目录 `LICENSE`（MIT）。本地源码仍含未提交改动，重现精确 Release 源码须从该标签单独检出，不能以当前目录代替。JNI 桥源码在 `tools/native-runtime/native_python_bridge.c`，构建入口为 `tools/native-runtime/build-bridge.sh`；本地 `.so` 的精确构建环境与 Release 中对应关系尚待独立复核。
- 运行时：`runtime.zip` 来自同一 Release；`app/src/main/assets/runtime.json` 记录 25 个 Termux 包、9 个纯 Python wheel 的版本和逐件 SHA-256，以及运行时 ZIP 摘要。ARM64 Termux 配方固定在 `6be420e7536d1e228f3ffee2eb74ca2736a73fc1`，PyTorch 源码提交记录为 `5c4886908584029761b579af026dcfb627c84070`。CPython 3.14.6、PyTorch 2.14.1、NumPy 2.5.4；目标前缀绑定旧应用 ID。
- 第三方许可证逐件见根目录 `THIRD_PARTY_NOTICES.md` 和上游配方；不能把整个运行时当作 MIT。九个 wheel 的锁定哈希还见 `packaging/pure-wheels.sha256`。原生包的本地备份与逐包哈希见 `.toolchain/runtime-debs/`、`.toolchain/runtime-debs.sha256`。复现入口为 `tools/setup-linux-builder.sh`、`tools/build-modern-runtime.sh`、`tools/assemble-modern-runtime.sh`、`tools/audit_android_runtime.py`，构建记录见 `docs/BUILD-COMPLETE-2026-10-21.md`。阶段 1 不执行这些会修改缓存或产物的入口。

## 无破坏的检查与后续命令

在 PowerShell 的仓库根目录运行。检查命令不会清空工作区：

```powershell
git status --short --branch
git rev-parse --verify HEAD  # 当前无本地提交，预期失败
git diff --cached --stat
git diff --stat
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
Get-FileHash .\app\src\main\assets\runtime.zip -Algorithm SHA256
$manifest = Get-Content .\app\src\main\assets\runtime.json -Raw | ConvertFrom-Json
$manifest.asset_sha256
```

最小增量构建入口是 `.\build-local.ps1`（JDK 17、Android SDK 35、Gradle 8.13 和现存且哈希匹配的 `runtime.zip`）；它会写入 `app/build/`，应先保留当前 APK 的独立副本及哈希再构建。不要为了做基线检查运行 `tools/fetch-runtime.ps1`，它会替换本地 ZIP。设备验证先检查 `.\.toolchain\android-sdk\platform-tools\adb.exe devices -l`，确认确切序列号，再运行 `python .\tools\verify-device-runtime.py --serial <序列号> --expect-python 3.14.6 --expect-torch 2.14.1`。该验证器会操作设备上的旧包和文件，需使用已备份的测试设备；不能把它当成只读检查。本阶段 ADB server 启动失败，未执行设备验证或安装。

## 回退

1. 保存当前工作区及用户脚本；不要在这个无 HEAD 的仓库运行 `reset --hard`、`clean` 或覆盖式检出。要查看 beta.5 源码，在**另一个目录**检出远端标签，并核对提交 `62d7ec1…`。
2. 从既有 Release 重新取得 beta.5 APK 与 `runtime.zip`，按上表 SHA-256 核对。不要改写标签、Release 或附件。
3. 新应用须采用不同 `applicationId`，因此回退到旧应用通常只需启动仍安装的 beta.5。若旧应用被卸载或签名不兼容，先通过已验证的导出包保全数据，再决定安装路径；不得为解决签名冲突直接卸载含私有数据的旧包。
4. 用户曾主动导入到新应用的数据，可按 `data-migration.md` 的导出包/逐文件记录回滚；外部 SAF 目录仍属于用户选定的文档提供者，回滚前复核是否被任何应用改动。

功能回归证据和未测项目见 [regression-matrix.md](regression-matrix.md)，迁移方案见 [data-migration.md](data-migration.md)。
