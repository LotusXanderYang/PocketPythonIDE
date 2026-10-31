# 可选调试通道（阶段 8）

Language Pack API v1 的 `ILanguagePack`、运行任务和 SAF 回写语义不变。需要调试的包额外声明 `debug` 能力、`debugAdapter` 描述符和 `selfTests.debug`，并在原服务上接受 action `org.pocketide.pack.debug.v1.DEBUG_PACK`。当前描述符要求 `protocol=dap-1`、`transport=binder-socketpair`、`service=entryService`。IDE 仍按包签名、安装 APK 哈希和版本验证能力；`debug` 自检未通过时不启用。

## 连接与生命周期

1. IDE 以显式组件绑定服务，确认同签名和 `IDebugPack.getProtocolVersion()==1`。同一时间仅打开一个调试会话。
2. IDE 把已保存的项目做成有大小限制的 ZIP 快照。SAF 文档树先复制到 IDE 私有镜像；不把 SAF URI 给语言包，调试中的文件改动也不自动写回原工作区。
3. IDE 创建一对 Unix socket，并通过 Binder 将项目输入 FD 和其中一个 socket FD 交给 `IDebugPack.start(request,input,channel,callback)`。包在独立进程中解包，再把 socket FD 交给适配器。`ready` 回调只表示适配器可以接收 DAP 消息；`finished` 传回最终状态。跨应用直接连接 abstract Unix socket 在 MAG-AN00 上返回 `Permission denied`，所以采用 Binder FD 传递。
4. socket 内是 UTF-8 `Content-Length` 帧，单帧上限 1 MiB。IDE 依次发 `initialize`、`launch`、各文件 `setBreakpoints`、`setExceptionBreakpoints`、`configurationDone`；运行中处理 `stopped`、`continued`、`output`、`exited`、`terminated`。暂停后读取 `stackTrace`、`scopes`、`variables`，再发 `continue`、`next`、`stepIn` 或 `stepOut`。退出页面发 `disconnect(terminateDebuggee=true)`，必要时发 `IDebugPack.stop(sessionId)`；包清理运行副本。Binder 断开、DAP 超时、响应失败和包退出均显示错误，不视为成功。

## 路径与断点

DAP `source.path` 使用 ZIP 工作区内的 `/` 分隔相对路径，例如 `pkg/helper.py`。IDE 拒绝 `..`、绝对路径、符号链接越界和不存在的源码；语言包只把自己的临时绝对路径映射回相对路径。IDE 将断点按 SAF 树 URI 或内部工作区保存，并记录源码行文本及相邻行。下次打开或重新复制工作区时按内容重新定位；改名后只在候选唯一时迁移。候选缺失或同分歧义时显示未映射状态，不静默放到可能错误的行。

## 当前能力边界

独立 Python 包使用标准库 `bdb` 和最小 DAP 适配器；已在 ARM64 Android 实机验证断点、单步、局部变量、多文件栈、异常停止、退出和取消清理。`pause` 需等待下一条 Python 跟踪事件；长时间停在 C 扩展内部时不会立即响应。首版只显示最多 40 层栈、每个作用域 100 个变量、单个值 256 字符；不支持求值表达式、变量修改、子进程跟踪或同时调试多个会话。

C/C++ 包当前没有通过 DAP 自检，不声明 `debug`。IDE 对它和其他未验证语言包显示“此语言包不支持调试”。证据、LLDB 打包研究和支持矩阵见[阶段 8 报告](../../STAGE8-DEBUG-2026-10-27.md)。
