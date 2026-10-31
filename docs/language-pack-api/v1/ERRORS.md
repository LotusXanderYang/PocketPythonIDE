# v1 错误码

| 码 | 产生方 | 含义与处理 |
| --- | --- | --- |
| `SOURCE_ERROR` | 包 | 源码诊断；读取 `line`、`column`、`message`。 |
| `PACK_ERROR` | 包 | 包执行异常；附包 ID 和任务 ID，检查包日志。 |
| `UNSUPPORTED` | 包 | 未实现操作；该能力不得显示为可用。 |
| `CANCELLED` | 包 | 用户取消；不写回运行副本。 |
| `PACK_BIND_FAILED` | IDE | 服务未发现、权限拒绝或绑定超时；核对安装与服务导出设置。 |
| `PROTOCOL_MISMATCH` | IDE | `getProtocolVersion()` 或结果版本不是 1；禁用该版本。 |
| `PACK_BAD_RESULT` | IDE | 回调缺失或任务 ID 不匹配；丢弃结果。 |
| `PACK_TIMEOUT` | IDE | 任务超时并已请求取消；可在包日志中按任务 ID 追踪。 |
| `PACK_CRASH` | IDE | 服务进程断连；编辑器继续工作，当前任务失败。 |
| `PACK_OUTPUT_LIMIT` | IDE | 输出超过当前 4 MiB 上限；包应减少日志或分批处理。 |

错误消息不得将包内部 Java 堆栈直接当作用户可执行指令。宿主显示包 ID、任务 ID、位置和安全的描述；完整诊断可由开发者按明确授权另行收集。
