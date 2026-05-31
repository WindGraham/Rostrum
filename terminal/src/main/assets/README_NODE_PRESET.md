# Node.js 预置方案技术文档

## 背景

OmniMaster 的 MCP 服务端（filesystem-server、terminal-server）运行在 PRoot 容器内的 Ubuntu 环境中，需要 Node.js 运行时。当前方案在首次启动时通过 `apt-get install nodejs npm` 在线安装，这在离线环境下会失败。

本文档评估三种 Node.js 预置方案，供后续决策参考。

---

## 方案 A：APK assets 预置官方 Node 二进制（推荐）

### 思路
在构建 APK 时，下载 Node.js 官方 `linux-arm64` 预编译二进制（如 `node-v20.x.x-linux-arm64.tar.xz`），放入 Android assets 目录（如 `terminal/src/main/assets/node/`）。

### 流程
1. **构建阶段**：Gradle task 或 CI 脚本从 `https://nodejs.org/dist/` 下载对应版本的 `linux-arm64` tar.xz。
2. **APK 打包**：将 tar.xz 放入 `terminal/src/main/assets/node/`（约 25–30 MB）。
3. **PRoot 初始化**：在 `PRootEnvironment` 解压 rootfs 后，将 assets 中的 Node 二进制解压到容器内 `/usr/local/node/` 并创建软链接：
   ```bash
   ln -sf /usr/local/node/bin/node /usr/bin/node
   ln -sf /usr/local/node/bin/npm /usr/bin/npm
   ln -sf /usr/local/node/bin/npx /usr/bin/npx
   ```

### 优点
- 不依赖 rootfs 重新打包，解耦性强。
- 官方二进制性能稳定，版本可控（可固定 LTS 如 v20 或 v22）。
- APK 增量可控（~25 MB）。

### 缺点
- APK 体积增加约 25–30 MB。
- 需要 Gradle 构建脚本支持下载/校验 Node 二进制。

### 适用场景
- 需要快速迭代、不想重新打包 rootfs 的场景。
- 对 APK 体积增长可接受的场景。

---

## 方案 B：在 rootfs 构建阶段预装 Node

### 思路
在打包 `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz` 之前，先启动一个 Ubuntu 容器（或 chroot），执行：
```bash
apt-get update
apt-get install -y nodejs npm
clean up apt cache
```
然后将整个 rootfs 重新打包为 tar.xz。

### 流程
1. 使用已有的 rootfs tar.xz 解压到临时目录。
2. 通过 proot 或 docker run 进入该环境安装 Node。
3. 清理 apt 缓存（`apt-get clean`）以尽量减小体积增长。
4. 重新压缩为 tar.xz 替换现有文件。

### 优点
- 最可靠：Node 和 npm 完全集成到 rootfs，启动时无需任何额外步骤。
- 不需要修改 Android assets 拷贝逻辑。
- 容器内所有程序共享同一 Node 环境。

### 缺点
- rootfs 体积增加约 30–50 MB（Node + npm + 依赖库）。
- 重新打包 rootfs 流程较重，CI 构建时间增加。
- rootfs 是平台无关基础镜像，混入应用层运行时不够纯粹。

### 适用场景
- 对启动速度和可靠性要求极高，接受 rootfs 增大的场景。

---

## 方案 C：保持现状（apt 在线安装）

### 思路
首次启动 MCP 服务端时，`McpBridgeManager` 检测到容器内没有 Node，则调用 `setup.sh`，通过 `apt-get install nodejs npm` 在线安装。

### 优点
- 最简单，无需修改构建流程或 APK 体积。
- 总是获取 Ubuntu 官方维护的 Node 版本，安全补丁自动跟随。

### 缺点
- **严重依赖网络**：离线环境（无 Wi-Fi/移动数据）下完全不可用。
- apt 源速度不稳定，首次启动延迟高。
- Ubuntu apt 中的 nodejs 版本通常较旧（如 v18），可能缺少新特性。

### 适用场景
- 原型验证阶段或始终在线的测试环境。

---

## 当前状态与建议

| 项目 | 现状 |
|------|------|
| Node 预置 | **方案 C**，依赖 apt 在线安装 |
| tsx 安装 | 已从 `devDependencies` 移至 `dependencies`，`npm install --production` 可覆盖 |
| 离线 fallback | `setup.sh` 已增加失败警告，但如果 npm install 失败且未预置 node_modules，`npx tsx` 仍不可用 |

### 短期改进（已落地）
1. `tsx` 加入 `dependencies`，确保 `npm install --production` 会安装它。
2. `setup.sh` 增加 apt/npm 失败时的友好提示，避免 `set -e` 导致脚本静默崩溃。

### 中长期建议
- **优先实施方案 A**：在 Gradle 构建时下载 Node 官方 binary 到 assets，PRoot 初始化后自动解压到 `/usr/local/`。该方案改动面最小、体积增量可控、且完全离线可用。
- 若未来 rootfs 需要整体重构，可同步评估方案 B。

---

## 相关文件

- `linux-mcp/setup.sh` — 容器内 Node 与依赖安装脚本
- `linux-mcp/start-servers.sh` — MCP 服务端启动脚本
- `app/src/main/java/com/omnimaster/core/bridge/McpBridgeManager.kt` — Android 侧 MCP 启动管理器
- `terminal/src/main/java/com/omnimaster/terminal/PRootEnvironment.kt` — PRoot 环境初始化
