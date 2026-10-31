# 发布说明草案：Pocket Android IDE / Python · PyTorch Lab 0.2.0 Alpha

**历史草案。** 最终版已发布在 [v0.2.0-alpha.1 Release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1)；以下为发布前撰写的草案，不作为当前下载与校验依据。阶段 9 的 BTK-W00 全新安装流程与设备边界见发布前清单。

## 包含内容

- 通用 Pocket Android IDE：编辑 SAF 工作区、标签页、补全、主题、背景、字体与高亮配色；基础 APK 不含语言运行时。
- Python 基础语言包：本地 CPython 运行、文件写回与 DAP 调试；不含 PyTorch、NumPy或任意 `pip` 安装。
- C/C++ 语言包：Clang/LLD 多文件课程项目构建运行与 LLDB 调试；项目文件名须为 `.pocket-cpp.json`。
- Python · PyTorch Lab：独立离线 CPU 小模型训练、日志、检查点和模型导出。

## 安装与升级

目标为 ARM64 Android API 26 及以上。先装 IDE，再装所需语言包，并在 IDE“语言包”中查看签名、权限及自检结果后启用。Lab 独立安装。四包的当前本地候选、版本、大小和 SHA-256 见[阶段 9 清单](STAGE9-RELEASE-PREFLIGHT-2026-10-31.md)；正式附件链接与对应源码提交尚未确定。

旧 `v0.1.2-beta.5` 应用与新产品可并存；旧应用数据不会自动搬入。通过 Android 文件选择器在新 IDE 重新授权可写 SAF 文件夹。升级同包名应用须保持签名并直接覆盖；卸载会清除该应用私有数据，先从 Lab 导出项目和模型。回滚时仍可使用未改动的旧版及其原工作区。

## 已知限制

- Python 包不提供 PyTorch；Lab 不提供 GPU 或大型模型保证。C/C++ 仅支持单目标、受限项目快照与非交互式标准输入，不提供通用第三方库管理。
- 部分鸿蒙原生目录的文档提供者拒绝创建文件；应选择实际可写的兼容环境目录。不能把 `content://` 地址直接交给语言包。
- C/C++ 调试在 MRO-W00 首次覆盖后五次自检中曾有一次未收到 `stopped`，随后四次通过；API 26、16 KB 页设备、编译中停止尚缺实机测试。
- 安装后私有存储与首次全新解包时间仍需在目标设备测量。Lab 在 MAG-AN00 上一次已解包状态的训练数据见[阶段 7 验收](STAGE7-ACCEPTANCE-2026-10-25.md)。

正式发布前须完成[发布前清单](STAGE9-RELEASE-PREFLIGHT-2026-10-31.md)的所有阻断项；不改写或删除 beta.5 Release。
