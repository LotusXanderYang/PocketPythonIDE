# 阶段 8：连续调试后入口文件误切换（2026-10-29）

用户复验发现：在辅助文件中按“继续”直到结束，下一次点“调试”立即显示退出码 0，且没有程序输出。调试控制台仍显示提交了 3 个断点。上一轮会话收尾修复未解决这一现象。

## 根因

调试器停在 `debug_helper.py` 时，会自动导航并把它设为编辑器当前文件。`WorkbenchActivity.debugCurrent()` 下一次总以编辑器当前文件为启动入口。若辅助文件只定义函数，没有顶层调用或 `print`，它正常执行后会立即以 0 退出且没有输出。连续单步通常会回到 `debug_main.py`，所以这一现象与“继续结束”高度相关。

## 修复

- 增加独立的 `DebugLaunchTarget`，记录当前调试入口。调试器自动导航调用栈文件时保留入口；用户主动切换文件或工作区时重新以所选文件为入口。
- `WorkbenchActivity` 给自动调试导航添加标识，并在启动时查询保留的入口。
- 调试控制台显示本次实际入口和断点提交数，方便复核。

## 验证与交付

- `DebugLaunchTargetSmokeTest` 通过，覆盖进入辅助文件、继续结束后再次启动、主动换文件和换工作区。
- `tools/test_debug_reentry.py` 十轮通过，并检查每轮实际输出 `debug-result: 22`。
- `:ide:assembleDebug --offline --no-daemon` 通过；APK 经 `apksigner verify --print-certs` 验证，签名证书与上一包相同。
- IDE 包：`artifacts/debug-entry-retained-ide-cdcad451.apk`，267,553 字节，SHA-256 `cdcad451621882f631e20dbe816d3e0fcc8756e500a0560992c4455cb4fa9b2d`。
- Python 语言包无需重装，继续使用上一轮 `debug-session-lifecycle-python-cf601df2.apk`。
- ADB 未发现设备，ARM64 实机仍待用户复验。直接覆盖安装 IDE，保留应用数据；使用“继续”结束后再次点“调试”，控制台应继续显示 `入口：debug_main.py`，并在原断点停住。主动打开 `debug_helper.py` 后点调试则应以辅助文件为入口。

未进入下一阶段，未发布 Release。
