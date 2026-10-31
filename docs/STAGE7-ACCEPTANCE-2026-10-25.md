# 阶段 7 验收：独立 Python · PyTorch Lab

日期：2026-10-25。范围仅为 `docs/prompts/07-PyTorch-Lab.md`；未开始阶段 8，未发布 Release。

## 交付与版本

| 项目 | 记录 |
| --- | --- |
| 最终候选 APK | `artifacts/stage7-lab-8bda6bab.apk`，158,190,157 字节（约 150.86 MiB），SHA-256 `8bda6babb3e25af2d5ea57782c49b62c39337179be2881f45eb87e184ebc530f` |
| 包名 / ABI / 最低 API | `com.example.pocketpythonlab` / `arm64-v8a` / 26；与旧版和通用 IDE 包名不同 |
| 运行时 | CPython 3.14.6、NumPy 2.5.4、CPU PyTorch 2.14.1；`runtime.zip` 164,754,807 字节，SHA-256 `170b4e26d9da3894d0acc62f3f0dd91c9a38459352a3e9f735ae602b1adf8311`；本阶段未裁减或改动其内容 |
| 清单与许可证 | `lab/src/main/assets/runtime.json` 固定包、wheel 和运行时哈希；`THIRD_PARTY_NOTICES.md` 已打入 APK；构建与许可证入口见 [Lab 说明](../lab/README.md) |
| 源码 | `LabHomeActivity`、`LabProjectStore`、`LabTrainingClient`、独立 `:python` 服务、Python 任务桥与两个离线样例，均在 `lab/` |

构建命令为 `./build-local.ps1 -Product lab`，成功完成 `:lab:assembleDebug` 并验证固定运行时哈希。最终候选使用 `adb install -r` 覆盖安装，旧 `files/scripts/main.py` 与阶段 7 项目均保留。原版 `v0.1.2-beta.5`、其标签和 Release 未改动。

## 实机条件与测量

测试设备为 **MAG-AN00**，原生 Android API 36，`arm64-v8a`；USB 供电，离线样例使用 CPU。通过 ADB 直接读取 `run-as` 私有项目日志、运行记录、检查点、系统属性和 SAF 导出 ZIP；没有依赖截图识读结果。PSS 由训练进程每 250 ms 采样。时间与内存是本机单次或数次样例记录，不代表其他设备。

| 测量 | 结果与条件 |
| --- | --- |
| 冷启动 Activity | `am force-stop` 后 `am start -W` 的 `TotalTime=351 ms`；运行时此前已解包，**不包含首次安装解包时间** |
| 解包后私有存储 | 经样例训练后 `files/usr` 为 642,048 KiB，`files` 合计 642,299 KiB（约 627 MiB）；含运行时及后续 Python 缓存 |
| 矩阵/求导样例 | 最终包运行 1.4 秒、峰值 PSS 约 239 MiB；先前同一阶段候选记录 `wallMs=2345`、`peakPssKb=241434` |
| 极小训练样例 | 最终包 80 轮，`wallMs=19056`、`pythonMs=18870`、`peakPssKb=335019`（约 327 MiB）；同阶段首次样例记录 `wallMs=24858`、`peakPssKb=364595`（约 356 MiB） |
| 使用导入 CSV 的训练 | 5 行数据，`wallMs=19067`、`peakPssKb=334908`；日志含 `dataset: data/stage7-linear-20261025.csv rows: 5` |

运行时间会受设备温度、后台负载与存储缓存影响；没有据此推定 GPU 或大型模型性能。安装占用还应加上 APK 和用户项目/导出文件。没有清除设备数据来测首次解包时长。

## 功能验收

| 项目 | 实机证据 |
| --- | --- |
| 导入、矩阵、自动求导与 NumPy | 最终包打印 `torch: 2.14.1 numpy: 2.5.4`、矩阵 `[[7.0,10.0],[15.0,22.0]]`、梯度 `[[7.0,11.0],[9.0,13.0]]`、`numpy bridge: 5.0`；任务成功 |
| 训练与文件读写 | 最终包损失从 `4.430414` 降至接近 0；生成 1,893 字节 `.pt` 检查点，`torch.load(..., weights_only=True)` 重新载入并输出 `reloaded prediction x=0: 0.9999999403953552`；日志、JSONL 指标与运行记录保留 |
| SAF 导入与同名保护 | 从 Android 下载目录选择 31 字节 CSV，复制入项目 `data/` 并更新空白 `data_file`；再次导入得到 `_2.csv`，原文件及已设置的 `data_file` 不变 |
| SAF 导出 | 最终包通过系统文件选择器写出 ZIP；直接解包核对包含 `main.py`、`params.json`、日志、指标、检查点、运行记录，模型内容完整，默认无 `data/` 原始数据。向同一下载目录再次导出时，系统新建 `tiny_train_1-results (1).zip`，原 ZIP 保持 3,405 字节而未被覆盖 |
| 取消与资源释放 | 启动后点“停止”，界面显示中断，`:python` PID 消失；修正中断标记引起的空记录后，最终包写出 125 字节 `ok:false` 取消 JSON，日志保留 |
| 异常报告与恢复 | 将**测试项目** `epochs` 设为 0，记录 `ok:false`、`file:main.py`、`line:18` 和参数范围错误；恢复参数后可重新训练成功 |
| 进程隔离与回收 | 在训练中仅杀死测试用 `:python` 进程，主界面继续可用、显示“Python 训练进程已退出”，保存失败记录与已有日志；随后新任务成功。此为**模拟进程死亡**，不等同于穷尽所有系统回收场景 |
| 后台 | 训练期间切回主屏，结果和检查点仍写出；前台服务通知用于返回 Lab。任务完成后训练 PID 退出 |
| 锁屏 | 用户在 ADB 启动的约 67 秒训练中按实体电源键，确认黑屏保持约 30 秒。中途直接读取 `dumpsys power` 为 `Dozing`、`mStayOn=false`，日志已写至第 60 轮，`:python` PID 仍存活；最终记录 `ok:true`、`wallMs=67391`、`peakPssKb=335042`，检查点重新载入成功，任务后 PID 退出。测试后已恢复 USB 常亮设置与样例参数 |

项目内代码/参数使用临时文件同步后改名；`save_checkpoint` 使用唯一文件名、临时文件同步及原子改名。同名项目不会覆盖已有项目。训练与运行记录分离在独立服务和私有项目目录，避免 Activity 阻塞和成功状态误报。

## 限制与后续依赖

- 已测试设备仅 MAG-AN00。鸿蒙兼容环境、其他 ARM64 设备与 API 26 设备没有本阶段完整实测；`minSdk 26` 是构建配置，不表示逐机验证。
- 仅离线 CPU 小模型；不承诺 GPU、任意桌面 wheel、通用 `pip` 安装或训练中断后的自动续训。任务最长 30 分钟，数据导入最多 10 MiB，结果归档源文件合计最多 100 MiB。
- 测试了 Android 下载目录提供者的 SAF 读写；其他提供者拒绝访问的错误路径由代码显示，但尚未逐个提供者实测。
- 实体锁屏与后台训练在上述短任务通过；更长训练仍受设备温度、电量、系统回收和 30 分钟任务上限约束。
- 下一阶段工作须由用户明确发起；本阶段未进行运行时裁减，也没有发布或更新 GitHub Release。
