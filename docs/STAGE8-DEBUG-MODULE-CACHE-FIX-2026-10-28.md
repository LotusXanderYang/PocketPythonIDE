# 阶段 8：重复调试的模块缓存修复（2026-10-28）

用户确认第一轮在 `debug_main.py:8` 停下后按“停止”；第二轮仍在第 8 行停下，但“单步/继续”直接输出 `debug-result: 22` 并结束。上一轮已消除“暂停”报错，但未修复这条路径。

## 根因与复现

SAF 每次调试都会复制到新的任务工作区。Python 包在同一进程中保留 `sys.modules`，第一次导入的 `debug_helper` 在第二轮被复用，其函数代码仍指向旧任务路径。调试适配器把旧路径视为当前工作区外的代码，在进入该函数时调用 `set_continue()`，清除原本的单步状态；新工作区的 helper 断点也无法匹配旧代码路径。

`tools/test_debug_reentry.py` 在三个不同的 `tasks/<id>/workspace` 中重复调试用户示例。修复前，第二轮“单步”预期进入 `debug_helper.py:3`，实际收到连接结束；修复后按“第一轮停止、第二轮单步、第三轮继续”的顺序均在 helper 第 3 行停下。此测试在同一个 Python 进程内运行，以覆盖模块缓存的生命周期。

## 修复与边界

Python 调试适配器在执行前后清除来自当前及先前任务工作区的已导入模块，并刷新导入缓存和源码行缓存。仅识别包私有 `tasks/<id>/workspace` 下的模块，保留标准库和语言包运行时模块，避免全量清空 `sys.modules`。已将源码与 APK 资产副本同步；IDE 本轮无需改动。

## 验证与交付

- `python -u tools/test_debug_reentry.py` 和 `python -u tools/test_pocket_debug_adapter.py` 通过。
- `:packs:python:assembleDebug --offline --no-daemon` 通过，构建中校验适配器源码与打包资产一致。
- 修复包：`artifacts/debug-reentry-python-modules-a38764fc.apk`，11,226,142 字节，SHA-256 `a38764fc5064b45a3cf9754121c23af6c0440fbc8d3e84625690fc95060b9a16`，签名证书 SHA-256 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6` 与旧 Python 包一致，可直接覆盖安装。
- ARM64 实机重复会话尚待用户复验；安装新语言包后须在 IDE 重新“验证并启用”及“启用通过的能力”。工作区和 IDE 断点数据无需清除。未进入下一阶段或发布 Release。
