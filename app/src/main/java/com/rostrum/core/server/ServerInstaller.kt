package com.rostrum.core.server

import android.content.Context
import android.util.Log
import com.rostrum.core.ssh.connection.ISshConnection
import com.rostrum.core.ssh.session.SshCommandSession
import com.rostrum.core.ssh.session.SshFileSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

/**
 * Rostrum Server 安装器。
 * 负责检测和安装 rostrum-server 到远程服务器。
 */
class ServerInstaller {
    
    companion object {
        private const val TAG = "ServerInstaller"
        private const val SERVER_VERSION = "1.0.0"
        private const val INSTALL_SCRIPT_URL = "https://raw.githubusercontent.com/WindGraham/Rostrum/main/rostrum-server/install.sh"
        private const val SERVER_BINARY_NAME = "rostrum-server"
        private const val SERVER_REMOTE_PATH = "\$HOME/.rostrum/server/current/rostrum-server"
        private const val LEGACY_SERVER_REMOTE_PATH = "\$HOME/.rostrum/bin/rostrum-server"
        private const val DEFAULT_PORT = 8080
        private val TOKEN_RANDOM = SecureRandom()
        
        /**
         * 检查 rostrum-server 是否已安装。
         */
        suspend fun isServerInstalled(session: SshCommandSession): Boolean = withContext(Dispatchers.IO) {
            try {
                val result = session.exec("test -x $SERVER_REMOTE_PATH && $SERVER_REMOTE_PATH --version")
                if (result.isSuccess) {
                    val cmdResult = result.getOrThrow()
                    cmdResult.isSuccess && cmdResult.output.contains(SERVER_VERSION)
                } else {
                    val legacyResult = session.exec("test -x $LEGACY_SERVER_REMOTE_PATH && $LEGACY_SERVER_REMOTE_PATH --version")
                    if (legacyResult.isSuccess) {
                        val cmdResult = legacyResult.getOrThrow()
                        cmdResult.isSuccess && cmdResult.output.contains(SERVER_VERSION)
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check server installation", e)
                false
            }
        }
        
        /**
         * 获取 rostrum-server 版本。
         */
        suspend fun getServerVersion(session: SshCommandSession): String? = withContext(Dispatchers.IO) {
            try {
                val result = session.exec("$SERVER_REMOTE_PATH --version")
                if (result.isSuccess) {
                    val cmdResult = result.getOrThrow()
                    if (cmdResult.isSuccess) {
                        cmdResult.output.trim()
                    } else {
                        null
                    }
                } else {
                    val legacyResult = session.exec("$LEGACY_SERVER_REMOTE_PATH --version")
                    if (legacyResult.isSuccess) {
                        val cmdResult = legacyResult.getOrThrow()
                        if (cmdResult.isSuccess) cmdResult.output.trim() else null
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get server version", e)
                null
            }
        }
        
        /**
         * 安装 rostrum-server。
         */
        suspend fun installServer(
            session: SshCommandSession,
            port: Int = DEFAULT_PORT,
            workspace: String? = null
        ): Result<ServerInfo> = withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting Rostrum Server installation...")
                
                // 检测系统架构
                val archResult = session.exec("uname -m")
                if (archResult.isFailure) {
                    return@withContext Result.failure(Exception("Failed to detect system architecture"))
                }
                val archCmd = archResult.getOrThrow()
                if (!archCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Failed to detect system architecture"))
                }
                
                val arch = when (archCmd.output.trim()) {
                    "x86_64" -> "amd64"
                    "aarch64" -> "arm64"
                    "armv7l" -> "arm"
                    else -> return@withContext Result.failure(Exception("Unsupported architecture: ${archCmd.output}"))
                }
                
                // 检测操作系统
                val osResult = session.exec("uname -s")
                if (osResult.isFailure) {
                    return@withContext Result.failure(Exception("Failed to detect operating system"))
                }
                val osCmd = osResult.getOrThrow()
                if (!osCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Failed to detect operating system"))
                }
                
                val os = when (osCmd.output.trim()) {
                    "Linux" -> "linux"
                    "Darwin" -> "darwin"
                    else -> return@withContext Result.failure(Exception("Unsupported OS: ${osCmd.output}"))
                }
                
                // 构建安装命令
                val installCommand = buildInstallCommand(os, arch, port, workspace)
                
                // 执行安装
                Log.i(TAG, "Executing installation command...")
                val installResult = session.exec(installCommand, timeoutMs = 120_000)
                if (installResult.isFailure) {
                    return@withContext Result.failure(Exception("Installation failed"))
                }
                val installCmd = installResult.getOrThrow()
                if (!installCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Installation failed: ${installCmd.stderr}"))
                }
                
                // 验证安装
                val verifyResult = session.exec("$SERVER_REMOTE_PATH --version")
                if (verifyResult.isFailure) {
                    return@withContext Result.failure(Exception("Installation verification failed"))
                }
                val verifyCmd = verifyResult.getOrThrow()
                if (!verifyCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Installation verification failed"))
                }
                
                // 启动远端服务
                Log.i(TAG, "Starting Rostrum Server...")
                val token = generateToken()
                val startInfo = startServerProcess(commandSession = session, port = port, workspace = workspace ?: "/", token = token)
                    .getOrElse { return@withContext Result.failure(it) }
                 
                val serverInfo = ServerInfo(
                    version = startInfo.version.ifBlank { verifyCmd.output.trim() },
                    port = startInfo.port,
                    host = startInfo.host,
                    workspace = workspace ?: "/",
                    token = token,
                    protocolVersion = startInfo.protocolVersion
                )
                
                Log.i(TAG, "Rostrum Server installed successfully: $serverInfo")
                Result.success(serverInfo)
                
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed", e)
                Result.failure(e)
            }
        }

        suspend fun installBundledServer(
            context: Context,
            connection: ISshConnection,
            session: SshCommandSession,
            port: Int = DEFAULT_PORT,
            workspace: String? = null
        ): Result<ServerInfo> = withContext(Dispatchers.IO) {
            val packageName = detectBundledPackageName(session)
                .getOrElse { return@withContext Result.failure(it) }
            val assetPath = "rostrum-server/$packageName"
            val remotePackagePath = "/tmp/$packageName"
            val tempFile = File(context.cacheDir, packageName)
            val fileSession = SshFileSession(connection)

            try {
                context.assets.open(assetPath).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                fileSession.start().getOrElse { return@withContext Result.failure(it) }
                fileSession.upload(tempFile, remotePackagePath).getOrElse { return@withContext Result.failure(it) }

                val installCommand = buildInstallUploadedCommand(packageName, port, workspace)
                val installResult = session.exec(installCommand, timeoutMs = 120_000)
                if (installResult.isFailure) {
                    return@withContext Result.failure(Exception("Bundled installation failed"))
                }
                val installCmd = installResult.getOrThrow()
                if (!installCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Bundled installation failed: ${installCmd.stderr.ifBlank { installCmd.output }}"))
                }

                val verifyResult = session.exec("$SERVER_REMOTE_PATH --version")
                val verifyCmd = verifyResult.getOrElse { return@withContext Result.failure(it) }
                if (!verifyCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Bundled installation verification failed"))
                }

                val token = generateToken()
                val effectiveWorkspace = workspace ?: "/"
                val startInfo = startServerProcess(commandSession = session, port = port, workspace = effectiveWorkspace, token = token)
                    .getOrElse { return@withContext Result.failure(it) }

                Result.success(
                    ServerInfo(
                        version = startInfo.version.ifBlank { verifyCmd.output.trim() },
                        port = startInfo.port,
                        host = startInfo.host,
                        workspace = effectiveWorkspace,
                        token = token,
                        protocolVersion = startInfo.protocolVersion
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bundled installation failed", e)
                Result.failure(e)
            } finally {
                fileSession.close()
                tempFile.delete()
                session.exec("rm -f ${shellQuote(remotePackagePath)}")
            }
        }
        
        /**
         * 启动 rostrum-server。
         */
        suspend fun startServer(
            session: SshCommandSession,
            port: Int = DEFAULT_PORT,
            workspace: String = "/"
        ): Result<ServerInfo> = withContext(Dispatchers.IO) {
            try {
                // 检查服务是否已运行
                session.exec("pkill -f '$SERVER_BINARY_NAME --host 127.0.0.1 --port $port' >/dev/null 2>&1 || true")
                 
                // 启动服务
                val token = generateToken()
                val startInfo = startServerProcess(commandSession = session, port = port, workspace = workspace, token = token)
                    .getOrElse { return@withContext Result.failure(it) }

                val versionResult = session.exec("$SERVER_REMOTE_PATH --version")
                val versionCmd = versionResult.getOrThrow()
                val serverInfo = ServerInfo(
                    version = startInfo.version.ifBlank { versionCmd.output.trim() },
                    port = startInfo.port,
                    host = startInfo.host,
                    workspace = workspace,
                    token = token,
                    protocolVersion = startInfo.protocolVersion
                )
                
                Result.success(serverInfo)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                Result.failure(e)
            }
        }
        
        /**
         * 停止 rostrum-server。
         */
        suspend fun stopServer(session: SshCommandSession, port: Int = DEFAULT_PORT): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                // 查找服务进程
                val findResult = session.exec("pgrep -f '$SERVER_BINARY_NAME'")
                if (findResult.isFailure) {
                    return@withContext Result.success(Unit) // 服务未运行
                }
                val findCmd = findResult.getOrThrow()
                if (!findCmd.isSuccess) {
                    return@withContext Result.success(Unit) // 服务未运行
                }
                
                // 停止服务
                val stopResult = session.exec("pkill -f '$SERVER_BINARY_NAME'")
                if (stopResult.isFailure) {
                    return@withContext Result.failure(Exception("Failed to stop server"))
                }
                val stopCmd = stopResult.getOrThrow()
                if (!stopCmd.isSuccess) {
                    return@withContext Result.failure(Exception("Failed to stop server"))
                }
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop server", e)
                Result.failure(e)
            }
        }
        
        /**
         * 卸载 rostrum-server。
         */
        suspend fun uninstallServer(session: SshCommandSession): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                // 停止服务
                stopServer(session)
                
                // 删除服务二进制文件
                val removeResult = session.exec("rm -f $SERVER_REMOTE_PATH $LEGACY_SERVER_REMOTE_PATH")
                if (removeResult.isFailure) {
                    Log.w(TAG, "Failed to remove server binary")
                }
                
                // 删除配置目录
                session.exec("rm -rf ~/.rostrum")
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to uninstall server", e)
                Result.failure(e)
            }
        }
        
        /**
         * 构建安装命令
         */
        private fun buildInstallCommand(
            os: String,
            arch: String,
            port: Int,
            workspace: String?
        ): String {
            return """
                |# 检查依赖
                |command -v curl >/dev/null 2>&1 || { echo "curl is required"; exit 1; }
                |command -v tar >/dev/null 2>&1 || { echo "tar is required"; exit 1; }
                |
                |# 创建安装目录
                |INSTALL_DIR="${'$'}HOME/.rostrum/server"
                |BIN_DIR="${'$'}INSTALL_DIR/bin/${SERVER_VERSION}"
                |mkdir -p "${'$'}BIN_DIR"
                |mkdir -p "${'$'}INSTALL_DIR/current"
                |mkdir -p "${'$'}INSTALL_DIR/config"
                |mkdir -p "${'$'}INSTALL_DIR/logs"
                |mkdir -p "${'$'}INSTALL_DIR/locks"
                |mkdir -p "${workspace ?: "/"}"
                |
                |# 下载 rostrum-server
                |FILENAME="rostrum-server-${os}-${arch}.tar.gz"
                |DOWNLOAD_URL="https://github.com/WindGraham/Rostrum/releases/download/v${SERVER_VERSION}/${'$'}FILENAME"
                |curl -fsSL "${'$'}DOWNLOAD_URL" -o "/tmp/${'$'}FILENAME"
                |
                |# 解压安装
                |tar -xzf "/tmp/${'$'}FILENAME" -C "${'$'}BIN_DIR"
                |chmod +x "${'$'}BIN_DIR/$SERVER_BINARY_NAME"
                |ln -sfn "${'$'}BIN_DIR/$SERVER_BINARY_NAME" "${'$'}INSTALL_DIR/current/$SERVER_BINARY_NAME"
                |
                |# 创建配置文件
                |cat > "${'$'}INSTALL_DIR/config/server.json" <<EOF
                |{
                |    "port": $port,
                |    "host": "127.0.0.1",
                |    "log_file": "${'$'}INSTALL_DIR/logs/server.log",
                |    "workspace": "${workspace ?: "/"}",
                |    "max_file_size": 10485760,
                |    "allow_command": true,
                |    "token": ""
                |}
                |EOF
                |
                |# 清理临时文件
                |rm -f "/tmp/${'$'}FILENAME"
                |
                |# 添加到PATH（如果需要）
                |if [[ ":${'$'}PATH:" != *":${'$'}INSTALL_DIR/current:"* ]]; then
                |    echo 'export PATH="${'$'}HOME/.rostrum/server/current:${'$'}PATH"' >> ~/.bashrc
                |    echo 'export PATH="${'$'}HOME/.rostrum/server/current:${'$'}PATH"' >> ~/.profile
                |    export PATH="${'$'}INSTALL_DIR/current:${'$'}PATH"
                |fi
                |
                |echo "Installation completed successfully"
            """.trimMargin()
        }

        private fun buildInstallUploadedCommand(
            packageName: String,
            port: Int,
            workspace: String?
        ): String {
            return """
                |INSTALL_DIR="${'$'}HOME/.rostrum/server"
                |BIN_DIR="${'$'}INSTALL_DIR/bin/${SERVER_VERSION}"
                |mkdir -p "${'$'}BIN_DIR"
                |mkdir -p "${'$'}INSTALL_DIR/current"
                |mkdir -p "${'$'}INSTALL_DIR/config"
                |mkdir -p "${'$'}INSTALL_DIR/logs"
                |mkdir -p "${'$'}INSTALL_DIR/locks"
                |mkdir -p "${workspace ?: "/"}"
                |tar -xzf "/tmp/$packageName" -C "${'$'}BIN_DIR"
                |chmod +x "${'$'}BIN_DIR/$SERVER_BINARY_NAME"
                |test -x "${'$'}BIN_DIR/$SERVER_BINARY_NAME"
                |ln -sfn "${'$'}BIN_DIR/$SERVER_BINARY_NAME" "${'$'}INSTALL_DIR/current/$SERVER_BINARY_NAME"
                |cat > "${'$'}INSTALL_DIR/config/server.json" <<EOF
                |{
                |    "port": $port,
                |    "host": "127.0.0.1",
                |    "log_file": "${'$'}INSTALL_DIR/logs/server.log",
                |    "workspace": "${workspace ?: "/"}",
                |    "max_file_size": 10485760,
                |    "allow_command": true,
                |    "token": ""
                |}
                |EOF
                |echo "Bundled installation completed successfully"
            """.trimMargin()
        }

        private suspend fun detectBundledPackageName(session: SshCommandSession): Result<String> = withContext(Dispatchers.IO) {
            val osResult = session.exec("uname -s")
            val osCmd = osResult.getOrElse { return@withContext Result.failure(it) }
            val os = when (osCmd.output.trim()) {
                "Linux" -> "linux"
                else -> return@withContext Result.failure(Exception("Bundled server unsupported OS: ${osCmd.output.trim()}"))
            }

            val archResult = session.exec("uname -m")
            val archCmd = archResult.getOrElse { return@withContext Result.failure(it) }
            val arch = when (archCmd.output.trim()) {
                "x86_64" -> "amd64"
                "aarch64" -> "arm64"
                else -> return@withContext Result.failure(Exception("Bundled server unsupported architecture: ${archCmd.output.trim()}"))
            }
            Result.success("rostrum-server-$os-$arch.tar.gz")
        }

        private suspend fun startServerProcess(
            commandSession: SshCommandSession,
            port: Int,
            workspace: String,
            token: String
        ): Result<ServerStartupInfo> {
            val logPath = "\$HOME/.rostrum/server/logs/start-$port.log"
            val command = """
                |mkdir -p "${'$'}HOME/.rostrum/server/logs"
                |rm -f "$logPath"
                |ROSTRUM_TOKEN=${shellQuote(token)} nohup $SERVER_REMOTE_PATH --host 127.0.0.1 --port $port --workspace ${shellQuote(workspace)} --token ${shellQuote(token)} > "$logPath" 2>&1 &
                |for i in ${'$'}(seq 1 50); do
                |    if grep -q 'ROSTRUM_SERVER_READY' "$logPath" 2>/dev/null; then
                |        grep 'ROSTRUM_SERVER_READY' "$logPath" | tail -n 1
                |        exit 0
                |    fi
                |    sleep 0.2
                |done
                |cat "$logPath" 2>/dev/null
                |exit 1
            """.trimMargin()

            val startResult = commandSession.exec(command, timeoutMs = 15_000)
            if (startResult.isFailure) {
                return Result.failure(startResult.exceptionOrNull() ?: Exception("Failed to start server"))
            }
            val startCmd = startResult.getOrThrow()
            if (!startCmd.isSuccess) {
                return Result.failure(Exception("Failed to start server: ${startCmd.output}"))
            }
            return parseStartupInfo(startCmd.output)
        }

        private fun parseStartupInfo(output: String): Result<ServerStartupInfo> {
            val readyLine = output.lineSequence().firstOrNull { it.startsWith("ROSTRUM_SERVER_READY ") }
                ?: return Result.failure(Exception("Missing ROSTRUM_SERVER_READY output"))
            val values = readyLine.removePrefix("ROSTRUM_SERVER_READY ")
                .split(' ')
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
                }
                .toMap()
            return Result.success(
                ServerStartupInfo(
                    host = values["host"] ?: "127.0.0.1",
                    port = values["port"]?.toIntOrNull() ?: DEFAULT_PORT,
                    version = values["version"].orEmpty(),
                    protocolVersion = values["protocol"].orEmpty()
                )
            )
        }

        private fun generateToken(): String {
            val bytes = ByteArray(32)
            TOKEN_RANDOM.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun shellQuote(value: String): String {
            return "'${value.replace("'", "'\\''")}'"
        }
    }
}

/**
 * 远端 rostrum-server 信息。
 */
data class ServerInfo(
    val version: String,
    val port: Int,
    val host: String,
    val workspace: String,
    val token: String? = null,
    val protocolVersion: String = ""
) {
    val url: String
        get() = "http://$host:$port"
}

private data class ServerStartupInfo(
    val host: String,
    val port: Int,
    val version: String,
    val protocolVersion: String
)
