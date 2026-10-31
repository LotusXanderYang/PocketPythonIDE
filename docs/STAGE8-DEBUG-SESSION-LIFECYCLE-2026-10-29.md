# 阶段 8：自然结束后的重复调试修复（2026-10-29）

## 现象

用户在同一项目连续调试：前两轮单步至结束，第三轮连续点“继续”至结束，第四轮调试界面短暂出现后退出。隔十数小时重试可暂时恢复；单步和步入多轮正常，再一次“继续”至结束后又复现。此前已修复调试时工作区模块缓存，本次处理会话收尾竞态。

## 代码检查与修复

原顺序是 Python 适配器发送 DAP `terminated`，IDE 随即以“主动停止”方式向已结束的适配器发送 `disconnect`，最多等待 2 秒后调用语言包的 `stop()`；后者会终止整个 Python 进程。与此同时，语言包在清理任务目录、释放 `DEBUG_BUSY` **之前**向 IDE 报告 `finished`。因此旧会话可以在语言包还忙时被认定完成，下一轮启动可能收到 `A Python debug session is active`，并表现为界面闪退。这里的具体设备错误码仍待实机日志确认，但顺序竞态在源码中可直接确认。

- `DebugPanel` 区分用户停止与收到 `terminated` 后的自然结束。
- `DebugPackClient` 自然结束时不再发送多余的 `disconnect`，等待语言包完成回调（最多 15 秒）后释放会话；用户停止仍可主动中止。
- `PythonPackService` 先清理任务目录、复位 `activeDebug` 和 `DEBUG_BUSY`，再发送 `finished`，使完成回调成为下一轮可启动的明确边界。
- `tools/test_debug_reentry.py` 扩展为同一 Python 进程连续十轮，覆盖多轮单步至结束、继续至结束，以及其后的新会话。

## 验证与交付

- `python -u tools/test_debug_reentry.py`：十轮通过。
- `python -u tools/test_pocket_debug_adapter.py`：通过。
- `:ide:assembleDebug :packs:python:assembleDebug --offline --no-daemon`：通过。
- 两个 APK 均经 `apksigner verify --print-certs` 验证，签名证书 SHA-256 为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`，与此前调试包相同，可覆盖安装。
- IDE：`artifacts/debug-session-lifecycle-ide-6015acd8.apk`，267,413 字节，SHA-256 `6015acd8a592c0da354e7896ec3dae2e1291432b604613b1d5eca6c893d2a34c`。
- Python 语言包：`artifacts/debug-session-lifecycle-python-cf601df2.apk`，11,241,738 字节，SHA-256 `cf601df29e71f1a20fe501208f5fb2c2b6c2eef814ac4762f9404dcbfca5f2aa`。
- ADB 未发现设备，ARM64 实机连续调试未验证。需直接覆盖安装两个 APK（不要卸载），在 IDE 重新验证并启用 Python，按“单步至结束、单步至结束、继续至结束、再次调试”复验。工作区和断点数据不迁移、不清除。

本次未进入下一阶段，未发布 Release。
