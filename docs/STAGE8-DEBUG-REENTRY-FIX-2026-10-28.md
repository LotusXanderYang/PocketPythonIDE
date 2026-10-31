# 阶段 8：重复调试与暂停状态修复（2026-10-28）

后续确认仍有“第二次调试单步/继续直接结束”，其根因和修复见 `STAGE8-DEBUG-MODULE-CACHE-FIX-2026-10-28.md`。以下记录保留首次修复的验证范围。

用户报告：第一次调试正常，再次调试时“单步/继续”未让程序恢复；已暂停时点“暂停”返回 `DAP pause: Unsupported while stopped: pause`。等待后 Python 包在 `debug_main.py:8` 的断点处 `receive(120)` 超时，退出码 1。回溯证明断点确实命中，失败发生在暂停后的控制命令通路，而非示例脚本计算。

## 修复

- IDE 的 DAP 事件线程固定读取创建它时的 transport，不再在后续会话中读取可变的 `dap` 字段，避免旧线程竞争新会话事件。
- “继续/暂停/单步”改用独立命令队列，不受调用栈和变量读取队列阻塞；按钮按状态提示，但在状态提示落后于适配器时仍允许发送恢复命令。重复点击时给出“程序正在运行”或“当前已暂停”的状态，不把正常状态冲突显示为解释器错误。
- 每次启动时在调试控制台显示提交的断点数；未映射或未验证的断点给出提示。旧会话结束时清理异步变量查询，不用旧结果覆盖新状态。
- Python 适配器允许已暂停时重复 `pause`，并在断点处持续等待客户端命令；IDE 断开 socket 时正常退出，避免用户检查变量超过两分钟就产生大段超时回溯。
- Python 包的调试构建固定使用仓库保存的旧签名密钥，防止再次出现覆盖安装签名不一致。

## 验证与交付

- `python -u tools/test_pocket_debug_adapter.py` 通过：断点、单步、变量、路径、异常、退出、暂停和已暂停时重复暂停。`python tools/verify-language-pack.py` 通过。运行 socketpair 测试时使用允许本机回环的执行环境。
- `:ide:assembleDebug :packs:python:assembleDebug --offline --no-daemon` 通过。IDE 与 Python 包证书 SHA-256 均为 `f1fa1170a83b9ac5d8436b25d9c163a07b68ad9a5567492d568906edeaa888e6`。
- IDE APK：`artifacts/debug-reentry-ide-cb67e300.apk`，267,413 字节，SHA-256 `cb67e300976317d7807178c931b7c186da747d14cfb6e48da98400bc7e983ae9`。
- Python APK：`artifacts/debug-reentry-python-e945ef5d.apk`，11,220,344 字节，SHA-256 `e945ef5d6ffa2c4a5fdaf52c23de28ab35c39644c9c2438f8ef6e90d282a843b`。
- x86_64 模拟器可安装两包，但 Python 原生运行时在该模拟器上 `PACK_CRASH`，不把该环境用于 DAP 结果判定。本轮 ARM64 实机重复会话尚待用户覆盖安装后验证。

两份 APK 可直接覆盖安装，不清除 IDE 工作区和断点偏好设置。未进入下一阶段或发布 Release。
