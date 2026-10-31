# 第三方二进制对应源码与许可材料

本页与新测试版 APK 一同发布，说明如何取得其中第三方 ARM64 二进制的对应源码、构建配方与补丁。项目自身源码位于同一 Release 标签；`LICENSE` 只覆盖项目自身，不替代第三方许可。

## 固定清单与本地校验

- Lab 及旧版运行时：`app/src/main/assets/runtime.json`、`lab/src/main/assets/runtime.json`、`packaging/pure-wheels.sha256` 固定包和 wheel 的版本、归档哈希；组装、审计及应用前缀补丁在 `tools/`、`packaging/`。Termux 配方基线为[提交 `6be420e7536d1e228f3ffee2eb74ca2736a73fc1`](https://github.com/termux/termux-packages/commit/6be420e7536d1e228f3ffee2eb74ca2736a73fc1)。
- Python 基础包：`packs/python/src/main/assets/runtime.json` 和 `packs/python/prepare_runtime.py` 记录从已核验源 ZIP 到基础包的派生过程。
- C/C++ 包：`packs/cpp/toolchain-lock.json`、`packs/cpp/lldb-toolchain-lock.json` 记录官方 Termux APT `.deb` 的版本、大小、SHA-256 和依赖；`packs/cpp/probe/` 与准备脚本说明筛选、重定位和原生启动助手构建。APT 仓库为 [Termux main](https://packages-cf.termux.dev/apt/termux-main/)，相应构建配方在 [Termux packages](https://github.com/termux/termux-packages/tree/master/packages)。锁定包归档的哈希是确切二进制来源；Termux 主分支配方可能继续更新，应按包版本及源码哈希查找对应配方。
- APK 内有第三方声明及通用许可证文本；C/C++ 包还在 `assets/upstream-notices/` 保存了从已核验 `.deb` 中提取的 24 个原始版权或许可文件。运行 `python tools/audit-locked-deb-notices.py` 会比对这些文件与锁定归档的字节。

## GPL/LGPL 涉及的源码入口

下表列出锁定运行时中需要特别保持源码获取路径的组件。精确版本和归档哈希以各包清单为准；下列上游源码和 Termux 配方、补丁应与 Release 标签中的构建脚本一起使用。

| 组件 | 上游源码入口 | 构建配方与补丁 |
| --- | --- | --- |
| gdbm 1.26 | [GNU gdbm](https://ftp.gnu.org/gnu/gdbm/) | [Termux gdbm](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/gdbm) |
| libiconv 1.19 | [GNU libiconv](https://ftp.gnu.org/gnu/libiconv/) | [Termux libiconv](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/libiconv) |
| xz/liblzma 5.8.4 | [xz 5.8.4](https://github.com/tukaani-project/xz/releases/tag/v5.8.4) | [Termux liblzma](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/liblzma) |
| readline 8.3.6 | [GNU readline](https://ftp.gnu.org/gnu/readline/) | [Termux readline](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/readline)；配方应用 8.3 的补丁级别 |
| zstd 1.5.7 | [zstd v1.5.7](https://github.com/facebook/zstd/releases/tag/v1.5.7) | [Termux zstd](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/zstd) |

如果上述第三方托管地址失效，应联系本仓库维护者提供对应版本源码、构建脚本和补丁的等效获取途径。为网络分发二进制提供源码获取指引的依据见 [GNU GPLv3 §6](https://www.gnu.org/licenses/gpl-3.0.html) 和 [GNU GPL FAQ](https://www.gnu.org/licenses/gpl-faq.en.html)。本页不是对所有包适用同一许可证的声明；具体权利和义务以 APK 内第三方声明及各上游许可为准。
