# Rostrum Server 设计

`rostrum-server` 是安装在目标 SSH 主机上的轻量远端服务。它不是 SSH 的替代品，而是连接成功后由 SSH 启动，并通过 SSH local port forwarding 暴露给 Android 客户端，从而让文件列表、读写、预览和后续增量同步走结构化 HTTP API，减少反复 SFTP/命令解析带来的延迟。

## 默认模型

- 安装路径：`$HOME/.rostrum/bin/rostrum-server`
- 默认监听：`127.0.0.1:8080`
- Android 访问方式：`127.0.0.1:<localPort> -> SSH tunnel -> remote 127.0.0.1:8080`
- SSH 仍负责认证、加密和端口转发。
- `rostrum-server` 只承担工作区文件 API、基础命令执行和能力声明。

## 当前 API

- `GET /api/ping`
- `GET /api/status`
- `GET /api/capabilities`
- `GET /api/files/list?path=...`
- `GET /api/files/read?path=...`
- `POST /api/files/write`
- `POST /api/files/delete`
- `POST /api/files/mkdir`
- `GET /api/files/watch`
- `POST /api/command/execute`

## 待完善

- 路径 sandbox：默认限制在 workspace 内，危险路径需要显式授权。
- 结构化错误：统一错误码、错误类型和可显示消息。
- 大文件/二进制：分块读写、哈希校验、断点续传。
- 真实 watch：替换当前基础轮询，实现平台原生事件或高效扫描。
- 版本升级：客户端检测 protocol/server 版本，自动下载兼容包。
- 安全开关：命令执行默认可配置关闭，删除/覆盖可配置确认策略。
- 发布包：为 linux/darwin amd64/arm64 构建 GitHub Release tar.gz。

## Android 安装策略

Android 客户端优先使用 APK 内置安装包，通过 SFTP 上传到远端 `/tmp` 后安装到 `$HOME/.rostrum/server/current/rostrum-server`。

当前内置包：

- `app/src/main/assets/rostrum-server/rostrum-server-linux-amd64.tar.gz`
- `app/src/main/assets/rostrum-server/rostrum-server-linux-arm64.tar.gz`

如果内置包不支持远端 OS/架构，或上传安装失败，客户端再 fallback 到 GitHub Release 下载脚本；如果下载也失败，文件功能必须继续 fallback 到 SFTP。

默认 workspace 为远端 `$HOME`，不是 `/`。只有用户或调用方显式指定 `/` 时才允许 rostrum-server 暴露根目录。
