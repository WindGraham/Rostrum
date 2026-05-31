package com.rostrum.core.shell

// 暂时禁用，因为 emulatorview 模块已被禁用
/*
import android.os.Handler
import android.os.Looper
import jackpal.androidterm.emulatorview.TermSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.Charset

/**
 * TermSession适配器
 * 将IShellSession适配到Android Terminal Emulator的TermSession接口
 */
class TermSessionAdapter(
    private val shellSession: IShellSession,
    private val scope: CoroutineScope
) : TermSession() {
    
    private val handler = Handler(Looper.getMainLooper())
    private val charset = Charset.forName("UTF-8")
    private var lastOutput = ""
    private val pipedOut = PipedOutputStream()
    private val pipedIn = PipedInputStream(pipedOut)
    private val commandBuffer = StringBuilder()
    
    init {
        try {
            // 设置输入输出流（TermSession会从pipedIn读取，我们写入pipedOut）
            setTermIn(pipedIn)
            setTermOut(pipedOut)
            
            // 启动Shell会话
            shellSession.startSession()
            
            // 监听输出并写入终端
            scope.launch(Dispatchers.IO) {
                shellSession.output.collect { output ->
                    // 只处理新增的输出
                    val newOutput = if (output.length > lastOutput.length) {
                        output.substring(lastOutput.length)
                    } else if (output != lastOutput) {
                        // 输出被清空或重置
                        output
                    } else {
                        ""
                    }
                    
                    if (newOutput.isNotEmpty()) {
                        try {
                            // 将新输出写入pipedOut（TermSession会读取并显示）
                            val bytes = newOutput.toByteArray(charset)
                            pipedOut.write(bytes)
                            pipedOut.flush()
                        } catch (e: Exception) {
                            android.util.Log.e("TermSessionAdapter", "Error writing output", e)
                        }
                        lastOutput = output
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TermSessionAdapter", "Error initializing", e)
        }
    }
    
    override fun initializeEmulator(columns: Int, rows: Int) {
        // 调用父类初始化
        super.initializeEmulator(columns, rows)
    }
    
    override fun write(data: ByteArray, offset: Int, count: Int) {
        // 将用户输入添加到缓冲区
        val input = String(data, offset, count, charset)
        commandBuffer.append(input)
        
        // 处理换行符（表示命令完成）
        if (input.contains("\n") || input.contains("\r")) {
            val command = commandBuffer.toString().trim()
            commandBuffer.clear()
            
            if (command.isNotEmpty()) {
                // 发送命令到Shell会话
                shellSession.sendCommand(command)
            }
        }
    }
    
    override fun finish() {
        try {
            pipedOut.close()
            pipedIn.close()
        } catch (e: Exception) {
            android.util.Log.e("TermSessionAdapter", "Error closing streams", e)
        }
        shellSession.close()
        super.finish()
    }
}
*/

