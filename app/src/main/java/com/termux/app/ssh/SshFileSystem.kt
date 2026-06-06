package com.termux.app.ssh

import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.termux.app.data.FileItem
import com.termux.app.data.FileSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Vector

/**
 * SFTP File System - migrated from Rostrum
 */
class SshFileSystem(private val connection: SshConnection) : FileSystemService {
    
    private val channelMutex = Mutex()
    private var channel: ChannelSftp? = null
    
    private suspend fun ensureConnected(): ChannelSftp = channelMutex.withLock {
        channel?.let { if (it.isConnected) return@withLock it }
        
        val result = connection.openSftpChannel()
        val newChannel = result.getOrThrow()
        channel = newChannel
        newChannel
    }
    
    override suspend fun readFile(uri: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            val output = ByteArrayOutputStream()
            ch.get(uri, output)
            Result.success(output.toByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeFile(uri: String, content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            ch.put(ByteArrayInputStream(content), uri)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readTextFile(uri: String, encoding: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = readFile(uri).getOrThrow()
            Result.success(String(bytes, java.nio.charset.Charset.forName(encoding)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTextFile(uri: String, content: String, encoding: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            writeFile(uri, content.toByteArray(java.nio.charset.Charset.forName(encoding)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            ch.stat(uri)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getFileInfo(uri: String): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            val stat = ch.stat(uri)
            val name = uri.substringAfterLast('/')
            Result.success(FileItem(
                file = null,
                name = name,
                path = uri,
                isDirectory = stat.isDir,
                size = stat.size,
                lastModified = stat.mTime * 1000L,
                extension = name.substringAfterLast('.', ""),
                childCount = -1,
                permissions = stat.permissions.toString(),
                mimeType = null,
                isRemote = true
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listDirectory(uri: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            val entries = ch.ls(uri) as Vector<*>
            val files = mutableListOf<FileItem>()
            for (entry in entries) {
                if (entry is ChannelSftp.LsEntry) {
                    if (entry.filename == "." || entry.filename == "..") continue
                    val stat = entry.attrs
                    val path = if (uri.endsWith("/")) "$uri${entry.filename}" else "$uri/${entry.filename}"
                    files.add(FileItem(
                        file = null,
                        name = entry.filename,
                        path = path,
                        isDirectory = stat.isDir,
                        size = stat.size,
                        lastModified = stat.mTime * 1000L,
                        extension = entry.filename.substringAfterLast('.', ""),
                        childCount = -1,
                        permissions = stat.permissions.toString(),
                        mimeType = null,
                        isRemote = true
                    ))
                }
            }
            Result.success(files.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            ch.mkdir(uri)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun delete(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            val stat = ch.stat(uri)
            if (stat.isDir) {
                ch.rmdir(uri)
            } else {
                ch.rm(uri)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun copy(source: String, target: String): Result<Unit> = withContext(Dispatchers.IO) {
        // SFTP doesn't have native copy, would need to read and write
        Result.failure(Exception("Copy not supported via SFTP"))
    }

    override suspend fun move(source: String, target: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = ensureConnected()
            ch.rename(source, target)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setupAuthorizedKey(publicKey: String, username: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            channelMutex.withLock {
                val ch = ensureConnected()

                val sshDir = "/home/$username/.ssh"
                val authKeysFile = "$sshDir/authorized_keys"

                try {
                    ch.mkdir(sshDir)
                    ch.chmod(0x1C0, sshDir) // 0700
                } catch (ignored: Exception) {
                }

                val existingContent = try {
                    val output = ByteArrayOutputStream()
                    ch.get(authKeysFile, output)
                    String(output.toByteArray())
                } catch (ignored: Exception) {
                    ""
                }

                val keyContent = publicKey.trim()
                if (!existingContent.contains(keyContent)) {
                    val newContent = (existingContent.trimEnd() + "\n" + keyContent + "\n").toByteArray()
                    ch.put(ByteArrayInputStream(newContent), authKeysFile)
                    ch.chmod(0x180, authKeysFile) // 0600
                    Log.d("SshFileSystem", "Public key uploaded to $authKeysFile")
                } else {
                    Log.d("SshFileSystem", "Public key already present in authorized_keys")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SshFileSystem", "Failed to setup authorized key", e)
            Result.failure(e)
        }
    }
}
