# Python · PyTorch Lab

独立的 ARM64 Android CPU 小模型训练应用，包名 `com.example.pocketpythonlab`。它与通用 IDE、旧版 PocketPythonIDE 可并存；原有 Lab 脚本保存在 `files/scripts/`，新版项目保存在 `files/projects/`，覆盖安装不会清空它们。阶段 7 的 [实机验收记录](../docs/STAGE7-ACCEPTANCE-2026-10-25.md)列出已测范围和限制。

## 使用

1. 点“矩阵/求导样例”核验 PyTorch、NumPy、矩阵乘法和自动求导；或点“极小训练样例”创建可复现的线性回归项目。也可“新建项目”或“打开项目”。同名项目会另取新名字，已有项目不会被样例覆盖。
2. 项目包含 `main.py`、`params.json`、`data/`、`logs/`、`metrics/`、`checkpoints/` 和 `runs/`。编辑代码或参数后点“保存”。训练启动前也会保存当前编辑内容。
3. 点“导入数据”，经 Android 文件选择器读取一个最多 10 MiB 的文件并复制到项目 `data/`。同名文件会追加编号；若 `params.json` 的 `data_file` 为空，导入路径会自动填入。导入是明确的一次复制，不会持续同步来源文件。
4. 点“启动 CPU 任务”查看日志与状态。训练通过独立的前台服务和 `:python` 进程运行，可切后台或锁屏；从通知返回应用查看结果或点“停止”。异常会显示文件、行号和 traceback。用户取消或进程退出后，可重新运行。
5. 训练脚本可调用 `from lab_api import log_metric, save_checkpoint`。`log_metric(epoch, loss)` 将指标写入项目 JSONL 日志；`save_checkpoint(value, label)` 先写临时文件并同步，再原子改名为唯一的 `.pt` 文件。样例会重新载入检查点并计算一次预测。
6. 点“导出模型与日志”，经 Android 文件选择器创建 ZIP。导出包括代码、参数、日志、指标、检查点和运行记录；默认不包括 `data/` 原始数据。单文件上限 50 MiB，归档源文件总量上限 100 MiB。应自行保管导出文件，卸载应用会删除私有项目。

“旧版脚本/自检”仍可打开旧编辑器，已有脚本不自动迁入训练项目。

## 运行边界

- 仅支持离线、CPU、ARM64 小模型；最低 Android API 26。未承诺 GPU、任意桌面 wheel 或任意 `pip install`。一次只运行一个训练任务，任务上限 30 分钟。
- PyTorch 导入和训练会占用数百 MiB 内存；阶段 7 样例在 MAG-AN00 的训练进程峰值 PSS 为约 236–356 MiB。运行时解包和 Python 缓存会占用约 627 MiB 私有存储，另需 APK、项目数据和结果空间。设备发热与耗电会随训练时长增加；小样例也应保持足够电量和可用存储。
- 前台服务提高后台运行的连续性，但系统仍可回收进程。中断时已写出的日志及完整检查点保留，未完成的训练不会被标为成功；请重新运行。临时检查点不会作为完成结果导出。
- 只在 MAG-AN00（Android 16 / API 36，ARM64）完成本阶段端到端实机验证。其他设备和鸿蒙兼容环境需要各自复测。

## 构建与来源

从仓库根目录运行 `./build-local.ps1 -Product lab`，脚本会验证或按固定前缀重打包现有 ARM64 运行时，然后构建 `lab/build/outputs/apk/debug/lab-debug.apk`。`lab/src/main/assets/runtime.json` 固定 CPython 3.14.6、NumPy 2.5.4、CPU PyTorch 2.14.1、包和 wheel 的版本与 SHA-256；`runtime.zip` 的 SHA-256 为 `170b4e26d9da3894d0acc62f3f0dd91c9a38459352a3e9f735ae602b1adf8311`。运行时组装/审计入口为 [`tools/assemble-modern-runtime.py`](../tools/assemble-modern-runtime.py)、[`tools/audit_android_runtime.py`](../tools/audit_android_runtime.py) 和 [`tools/rebase-lab-runtime.py`](../tools/rebase-lab-runtime.py)；Termux 源码固定到提交 `6be420e7536d1e228f3ffee2eb74ca2736a73fc1`。详情见根目录 [构建说明](../README.md)和 [第三方声明](../THIRD_PARTY_NOTICES.md)。第三方声明也打入 APK 的 `assets/THIRD_PARTY_NOTICES.md`。

已验证 APK、大小、SHA-256 与签名见 [v0.2.0-alpha.1 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1)。当前构建沿用项目保留的调试密钥，直接覆盖同签名旧版 Lab 可保留私有项目；跨签名安装会被系统拒绝，不能通过先卸载来绕过数据备份。
