package com.rostrum.terminal

import android.util.Log
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class Pty(
    val process: Process,
    val masterFd: FileDescriptor?,
    private val ptyMaster: Int,
    val stdout: InputStream,
    val stdin: OutputStream
) {
    constructor(process: Process, masterFd: FileDescriptor, ptyMaster: Int) : this(
        process = process,
        masterFd = masterFd,
        ptyMaster = ptyMaster,
        stdout = FileInputStream(masterFd),
        stdin = FileOutputStream(masterFd)
    )

    fun waitFor(): Int = process.waitFor()

    fun destroy() {
        process.destroy()
        try {
            stdout.close()
            stdin.close()
        } catch (e: IOException) {
            Log.e("Pty", "Error closing PTY streams", e)
        }
    }

    fun getPtyMode(): PtyMode {
        if (ptyMaster <= 0) {
            return PtyMode(
                isCanonicalMode = true,
                isEchoEnabled = true,
                isSignalEnabled = true,
                isExtendedEnabled = true,
                availableBytes = 0
            )
        }
        val flags = Companion.getTerminalFlags(ptyMaster)
        val availableBytes = Companion.getAvailableBytes(ptyMaster)
        return PtyMode(
            isCanonicalMode = (flags and 0x01) != 0,
            isEchoEnabled = (flags and 0x02) != 0,
            isSignalEnabled = (flags and 0x04) != 0,
            isExtendedEnabled = (flags and 0x08) != 0,
            availableBytes = availableBytes
        )
    }

    fun setWindowSize(rows: Int, cols: Int): Boolean {
        if (ptyMaster <= 0) return false
        return setPtyWindowSize(ptyMaster, rows, cols) == 0
    }

    private external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int): Int

    companion object {
        private const val TAG = "Pty"

        init {
            try {
                System.loadLibrary("pty")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libpty.so", e)
            }
        }

        @Throws(IOException::class)
        fun start(command: Array<String>, environment: Map<String, String>, workingDir: File): Pty {
            val envArray = environment.map { "${it.key}=${it.value}" }.toTypedArray()
            val processInfo = createSubprocess(command, envArray, workingDir.absolutePath)
            val pid = processInfo[0]
            val masterFdInt = processInfo[1]

            if (pid <= 0 || masterFdInt <= 0) {
                throw IOException("Failed to create subprocess with PTY. pid=$pid, fd=$masterFdInt")
            }

            val fileDescriptor = Reflect.getFileDescriptor(masterFdInt)
            val dummyProcess = object : Process() {
                override fun destroy() {
                    try { android.os.Process.sendSignal(pid, 1) } catch (_: Exception) {}
                    try { android.os.Process.sendSignal(pid, 9) } catch (_: Exception) {}
                }

                override fun exitValue(): Int {
                    try {
                        android.os.Process.sendSignal(pid, 0)
                        throw IllegalThreadStateException("Process hasn't exited")
                    } catch (_: Exception) {
                        return 0
                    }
                }

                override fun getErrorStream(): InputStream? = null
                override fun getInputStream(): InputStream? = null
                override fun getOutputStream(): OutputStream? = null
                override fun waitFor(): Int = Companion.waitFor(pid)
            }
            return Pty(dummyProcess, fileDescriptor, masterFdInt)
        }

        private external fun createSubprocess(
            cmdArray: Array<String>,
            envArray: Array<String>,
            workingDir: String
        ): IntArray

        private external fun waitFor(pid: Int): Int
        private external fun getTerminalFlags(fd: Int): Int
        private external fun getAvailableBytes(fd: Int): Int
    }
}

data class PtyMode(
    val isCanonicalMode: Boolean,
    val isEchoEnabled: Boolean,
    val isSignalEnabled: Boolean,
    val isExtendedEnabled: Boolean,
    val availableBytes: Int
) {
    fun isWaitingForInput(): Boolean {
        if (!isCanonicalMode && availableBytes == 0) return true
        if (availableBytes == 0) return true
        return false
    }
}

object Reflect {
    fun getFileDescriptor(fd: Int): FileDescriptor {
        val fileDescriptor = FileDescriptor()
        try {
            val field = FileDescriptor::class.java.getDeclaredField("descriptor")
            field.isAccessible = true
            field.set(fileDescriptor, fd)
        } catch (e: Exception) {
            throw IOException("Failed to create FileDescriptor from integer fd", e)
        }
        return fileDescriptor
    }
}
