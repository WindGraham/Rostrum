package com.rostrum.ui.main.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rostrum.core.filesystem.ActiveFileSystemManager
import com.rostrum.core.util.FileOperations
import com.rostrum.core.util.RestrictedPathHelper
import com.rostrum.core.util.ZipUtils
import com.rostrum.ui.main.ClipboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文件操作结果回调类型
 */
typealias OperationCallback = (Boolean, String?) -> Unit

/**
 * 文件操作的辅助类
 * 负责复制、移动、删除、重命名、创建、压缩、解压等文件操作
 * 
 * 作为 MainViewModel 的内部委托使用
 * 支持本地和远程（SSH）文件系统
 */
class FileOperationsViewModel(
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "FileOperationsViewModel"
    }
    
    // 操作进度状态
    var isOperationRunning by mutableStateOf(false)
        private set
    
    var operationProgress by mutableStateOf(0f)
        private set
        
    var operationStatus by mutableStateOf("")
        private set
    
    
    /**
     * 复制文件到目标目录
     * @param sourcePaths 源文件路径集合
     * @param targetPath 目标目录路径
     * @param onResult 操作结果回调
     */
    fun copyFiles(
        sourcePaths: Set<String>,
        targetPath: String,
        onResult: OperationCallback
    ) {
        scope.launch {
            if (sourcePaths.isEmpty()) {
                onResult(false, "未选择文件")
                return@launch
            }
            
            isOperationRunning = true
            operationProgress = 0f
            operationStatus = "正在准备复制..."
            
            val result = withContext(Dispatchers.IO) {
                executeFileOperation(sourcePaths, targetPath, "复制") { sourceFile, finalTarget ->
                    FileOperations.copy(sourceFile, finalTarget)
                }
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 移动文件到目标目录
     * @param sourcePaths 源文件路径集合
     * @param targetPath 目标目录路径
     * @param onResult 操作结果回调
     */
    fun moveFiles(
        sourcePaths: Set<String>,
        targetPath: String,
        onResult: OperationCallback
    ) {
        scope.launch {
            if (sourcePaths.isEmpty()) {
                onResult(false, "未选择文件")
                return@launch
            }
            
            isOperationRunning = true
            operationProgress = 0f
            operationStatus = "正在准备移动..."
            
            val result = withContext(Dispatchers.IO) {
                executeFileOperation(sourcePaths, targetPath, "移动") { sourceFile, finalTarget ->
                    FileOperations.move(sourceFile, finalTarget)
                }
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 设置剪贴板内容
     */
    fun setClipboard(paths: List<String>, isCut: Boolean) {
        if (isCut) {
            ClipboardManager.cutFiles(paths)
        } else {
            ClipboardManager.copyFiles(paths)
        }
    }
    
    /**
     * 从剪贴板粘贴文件
     */
    fun pasteFiles(targetPath: String, onResult: OperationCallback) {
        scope.launch {
            val clipboardFiles = ClipboardManager.clipboardFiles
            val isCutMode = ClipboardManager.isCutMode.value
            
            if (clipboardFiles.isEmpty()) {
                onResult(false, "剪贴板为空")
                return@launch
            }
            
            isOperationRunning = true
            operationProgress = 0f
            operationStatus = if (isCutMode) "正在准备移动..." else "正在准备复制..."
            
            val result = withContext(Dispatchers.IO) {
                val operation: (File, File) -> Result<Unit> = if (isCutMode) {
                    { source, target -> FileOperations.move(source, target) }
                } else {
                    { source, target -> FileOperations.copy(source, target) }
                }
                
                val (success, message) = executeFileOperation(
                    clipboardFiles.toSet(), 
                    targetPath, 
                    if (isCutMode) "移动" else "复制",
                    operation
                )
                
                // 如果是剪切模式，清空剪贴板
                if (isCutMode && success) {
                    ClipboardManager.clear()
                }
                
                Pair(success, message)
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 删除文件
     * 支持本地和远程（SSH）文件系统
     */
    fun deleteFiles(filePaths: Set<String>, onResult: OperationCallback) {
        scope.launch {
            if (filePaths.isEmpty()) {
                onResult(false, "未选择文件")
                return@launch
            }

            isOperationRunning = true
            operationProgress = 0f
            operationStatus = "正在删除..."
            
            val result = withContext(Dispatchers.IO) {
                var successCount = 0
                var failCount = 0
                val errorMessages = mutableListOf<String>()
                val totalFiles = filePaths.size
                
                if (ActiveFileSystemManager.isUsingRemote()) {
                    // 远程文件系统
                    val fileSystem = ActiveFileSystemManager.getActiveFileSystem()
                    
                    filePaths.forEachIndexed { index, filePath ->
                        val fileName = filePath.substringAfterLast('/')
                        operationStatus = "正在删除: $fileName"
                        operationProgress = index.toFloat() / totalFiles
                        
                        val deleteResult = fileSystem.delete(filePath)
                        if (deleteResult.isSuccess) {
                            successCount++
                            Log.d(TAG, "远程文件删除成功: $filePath")
                        } else {
                            failCount++
                            val errorMsg = deleteResult.exceptionOrNull()?.message ?: "未知错误"
                            errorMessages.add("$fileName: $errorMsg")
                            Log.e(TAG, "远程文件删除失败: $filePath - $errorMsg")
                        }
                    }
                } else {
                    // 本地文件系统
                    filePaths.forEachIndexed { index, filePath ->
                        val file = File(filePath)
                        operationStatus = "正在删除: ${file.name}"
                        operationProgress = index.toFloat() / totalFiles
                        
                        val deleteResult = FileOperations.delete(file)
                        if (deleteResult.isSuccess) {
                            successCount++
                        } else {
                            failCount++
                            errorMessages.add("${file.name}: ${deleteResult.exceptionOrNull()?.message}")
                        }
                    }
                }
                
                buildResultMessage(successCount, failCount, errorMessages, "删除")
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 移动单个文件到文件夹
     */
    fun moveFileToFolder(sourcePath: String, targetFolderPath: String, onResult: OperationCallback) {
        scope.launch {
            isOperationRunning = true
            operationProgress = 0f
            operationStatus = "正在移动文件..."
            
            val result = withContext(Dispatchers.IO) {
                val sourceFile = File(sourcePath)
                val targetFolder = File(targetFolderPath)
                
                if (!sourceFile.exists()) {
                    return@withContext Pair(false, "源文件不存在")
                }
                
                if (!targetFolder.isDirectory || !targetFolder.exists()) {
                    return@withContext Pair(false, "目标文件夹不存在")
                }
                
                val finalTarget = generateUniqueTargetFile(targetFolder, sourceFile)
                
                val moveResult = FileOperations.move(sourceFile, finalTarget)
                if (moveResult.isSuccess) {
                    Pair(true, "移动成功")
                } else {
                    Pair(false, "移动失败: ${moveResult.exceptionOrNull()?.message}")
                }
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 重命名文件
     */
    fun renameFile(filePath: String, newName: String, onResult: OperationCallback) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val file = File(filePath)
                val renameResult = FileOperations.rename(file, newName)
                if (renameResult.isSuccess) {
                    Pair(true, "重命名成功")
                } else {
                    Pair(false, "重命名失败: ${renameResult.exceptionOrNull()?.message}")
                }
            }
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 批量重命名文件
     */
    fun batchRenameFiles(renames: List<Pair<String, String>>, onResult: OperationCallback) {
        scope.launch {
            if (renames.isEmpty()) {
                onResult(true, "没有文件需要重命名")
                return@launch
            }
            
            isOperationRunning = true
            operationStatus = "正在重命名..."
            operationProgress = 0f
            
            val result = withContext(Dispatchers.IO) {
                var successCount = 0
                var failCount = 0
                val errorMessages = mutableListOf<String>()
                val total = renames.size
                
                renames.forEachIndexed { index, (oldPath, newName) ->
                    operationStatus = "正在重命名: $newName"
                    operationProgress = index.toFloat() / total
                    
                    val oldFile = File(oldPath)
                    val renameResult = FileOperations.rename(oldFile, newName)
                    
                    if (renameResult.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                        errorMessages.add("${oldFile.name}: ${renameResult.exceptionOrNull()?.message}")
                    }
                }
                
                buildResultMessage(successCount, failCount, errorMessages, "重命名")
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 创建文件夹
     * 支持本地和远程（SSH）文件系统
     */
    fun createDirectory(parentPath: String, folderName: String, onResult: OperationCallback) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (ActiveFileSystemManager.isUsingRemote()) {
                    // 远程文件系统
                    val fileSystem = ActiveFileSystemManager.getActiveFileSystem()
                    val fullPath = if (parentPath.endsWith("/")) {
                        "$parentPath$folderName"
                    } else {
                        "$parentPath/$folderName"
                    }
                    
                    // 检查是否已存在
                    if (fileSystem.exists(fullPath)) {
                        Pair(false, "文件夹已存在")
                    } else {
                        val createResult = fileSystem.createDirectory(fullPath)
                        if (createResult.isSuccess) {
                            Log.d(TAG, "远程文件夹创建成功: $fullPath")
                            Pair(true, "创建文件夹成功")
                        } else {
                            Log.e(TAG, "远程文件夹创建失败: ${createResult.exceptionOrNull()?.message}")
                            Pair(false, "创建文件夹失败: ${createResult.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    // 本地文件系统
                    val parentDir = File(parentPath)
                    val createResult = FileOperations.createDirectory(parentDir, folderName)
                    if (createResult.isSuccess) {
                        Pair(true, "创建文件夹成功")
                    } else {
                        Pair(false, "创建文件夹失败: ${createResult.exceptionOrNull()?.message}")
                    }
                }
            }
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 创建新文件
     * 支持本地和远程（SSH）文件系统
     */
    fun createFile(parentPath: String, fileName: String, onResult: OperationCallback) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (ActiveFileSystemManager.isUsingRemote()) {
                    // 远程文件系统
                    val fileSystem = ActiveFileSystemManager.getActiveFileSystem()
                    val fullPath = if (parentPath.endsWith("/")) {
                        "$parentPath$fileName"
                    } else {
                        "$parentPath/$fileName"
                    }
                    
                    // 检查是否已存在
                    if (fileSystem.exists(fullPath)) {
                        Pair(false, "文件已存在")
                    } else {
                        // 创建空文件
                        val createResult = fileSystem.writeTextFile(fullPath, "")
                        if (createResult.isSuccess) {
                            Log.d(TAG, "远程文件创建成功: $fullPath")
                            Pair(true, "创建文件成功")
                        } else {
                            Log.e(TAG, "远程文件创建失败: ${createResult.exceptionOrNull()?.message}")
                            Pair(false, "创建文件失败: ${createResult.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    // 本地文件系统
                    val parentDir = File(parentPath)
                    val newFile = File(parentDir, fileName)
                    
                    if (newFile.exists()) {
                        Pair(false, "文件已存在")
                    } else {
                        try {
                            if (newFile.createNewFile()) {
                                Pair(true, "创建文件成功")
                            } else {
                                Pair(false, "创建文件失败")
                            }
                        } catch (e: Exception) {
                            Pair(false, "创建文件错误: ${e.message}")
                        }
                    }
                }
            }
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 保存文件内容
     * 支持本地和远程（SSH）文件系统
     */
    fun saveFileContent(filePath: String, content: String, onResult: OperationCallback) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val fileSystem = ActiveFileSystemManager.getActiveFileSystem()
                    val writeResult = fileSystem.writeTextFile(filePath, content)
                    
                    if (writeResult.isSuccess) {
                        Log.d(TAG, "文件保存成功: $filePath (${if (ActiveFileSystemManager.isUsingRemote()) "远程" else "本地"})")
                        Pair(true, "保存成功")
                    } else {
                        val errorMsg = writeResult.exceptionOrNull()?.message ?: "未知错误"
                        Log.e(TAG, "文件保存失败: $filePath - $errorMsg")
                        Pair(false, "保存失败: $errorMsg")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "文件保存异常: $filePath", e)
                    Pair(false, "保存失败: ${e.message}")
                }
            }
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 压缩文件
     */
    fun zipFiles(
        sourcePaths: Set<String>,
        targetDir: String,
        zipName: String,
        onResult: OperationCallback
    ) {
        scope.launch {
            if (sourcePaths.isEmpty()) {
                onResult(false, "未选择文件")
                return@launch
            }
            
            isOperationRunning = true
            operationStatus = "正在压缩..."
            
            val result = withContext(Dispatchers.IO) {
                val sources = sourcePaths.map { File(it) }
                // 自动补全 .zip 后缀
                val finalName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
                val destination = File(targetDir, finalName)
                
                if (destination.exists()) {
                    Pair(false, "目标文件已存在: ${destination.name}")
                } else {
                    val zipResult = ZipUtils.zip(sources, destination)
                    if (zipResult.isSuccess) {
                        Pair(true, "压缩成功")
                    } else {
                        Pair(false, "压缩失败: ${zipResult.exceptionOrNull()?.message}")
                    }
                }
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 压缩文件（指定文件路径列表）
     */
    fun compressFiles(
        filePaths: List<String>,
        targetDir: String,
        zipName: String,
        onResult: OperationCallback
    ) {
        scope.launch {
            if (filePaths.isEmpty()) {
                onResult(false, "未选择文件")
                return@launch
            }
            
            isOperationRunning = true
            operationProgress = 0f
            operationStatus = "正在压缩..."
            
            val result = withContext(Dispatchers.IO) {
                try {
                    val files = filePaths.map { File(it) }
                    val targetFile = File(targetDir, zipName)
                    
                    ZipOutputStream(FileOutputStream(targetFile)).use { zipOut ->
                        files.forEachIndexed { index, file ->
                            operationStatus = "正在压缩: ${file.name}"
                            operationProgress = index.toFloat() / files.size
                            addToZip(file, file.name, zipOut)
                        }
                    }
                    
                    Pair(true, "压缩成功: $zipName")
                } catch (e: Exception) {
                    Pair(false, "压缩失败: ${e.message}")
                }
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    /**
     * 解压文件
     */
    fun unzipFile(zipFilePath: String, targetDir: String, onResult: OperationCallback) {
        scope.launch {
            isOperationRunning = true
            operationStatus = "正在解压..."
            
            val result = withContext(Dispatchers.IO) {
                val zipFile = File(zipFilePath)
                val folderName = zipFile.nameWithoutExtension
                val destinationDir = File(targetDir, folderName)
                
                val unzipResult = ZipUtils.unzip(zipFile, destinationDir)
                if (unzipResult.isSuccess) {
                    Pair(true, "解压成功")
                } else {
                    Pair(false, "解压失败: ${unzipResult.exceptionOrNull()?.message}")
                }
            }
            
            isOperationRunning = false
            onResult(result.first, result.second)
        }
    }
    
    // ============ Private Helper Methods ============
    
    /**
     * 执行文件操作的通用方法
     */
    private fun executeFileOperation(
        sourcePaths: Set<String>,
        targetPath: String,
        operationName: String,
        operation: (File, File) -> Result<Unit>
    ): Pair<Boolean, String> {
        var successCount = 0
        var failCount = 0
        val errorMessages = mutableListOf<String>()
        val totalFiles = sourcePaths.size
        
        sourcePaths.forEachIndexed { index, sourcePath ->
            val sourceFile = File(sourcePath)
            
            if (!sourceFile.exists()) {
                failCount++
                errorMessages.add("${sourceFile.name}: 源文件不存在")
                return@forEachIndexed
            }
            
            operationStatus = "正在${operationName}: ${sourceFile.name}"
            operationProgress = index.toFloat() / totalFiles
            
            val finalTarget = generateUniqueTargetFile(File(targetPath), sourceFile)
            
            val result = operation(sourceFile, finalTarget)
            if (result.isSuccess) {
                successCount++
            } else {
                failCount++
                errorMessages.add("${sourceFile.name}: ${result.exceptionOrNull()?.message}")
            }
        }
        
        return buildResultMessage(successCount, failCount, errorMessages, operationName)
    }
    
    /**
     * 生成唯一的目标文件路径（处理同名文件）
     */
    private fun generateUniqueTargetFile(targetDir: File, sourceFile: File): File {
        var targetFile = File(targetDir, sourceFile.name)
        var counter = 1
        
        while (targetFile.exists()) {
            val nameWithoutExt = sourceFile.nameWithoutExtension
            val ext = sourceFile.extension
            val newName = if (ext.isNotEmpty()) {
                "$nameWithoutExt ($counter).$ext"
            } else {
                "$nameWithoutExt ($counter)"
            }
            targetFile = File(targetDir, newName)
            counter++
        }
        
        return targetFile
    }
    
    /**
     * 构建操作结果消息
     */
    private fun buildResultMessage(
        successCount: Int,
        failCount: Int,
        errorMessages: List<String>,
        operationName: String
    ): Pair<Boolean, String> {
        val message = when {
            successCount > 0 && failCount == 0 -> "成功${operationName} $successCount 个文件"
            successCount > 0 && failCount > 0 -> "成功: $successCount, 失败: $failCount"
            else -> "${operationName}失败: ${errorMessages.joinToString("; ")}"
        }
        return Pair(successCount > 0, message)
    }
    
    /**
     * 递归添加文件到 ZIP
     */
    private fun addToZip(file: File, name: String, zipOut: ZipOutputStream) {
        if (file.isDirectory) {
            val entries = RestrictedPathHelper.listFilesWithZeroWidthPriority(file.absolutePath) ?: return
            for (entry in entries) {
                addToZip(entry, "$name/${entry.name}", zipOut)
            }
        } else {
            file.inputStream().use { input ->
                zipOut.putNextEntry(ZipEntry(name))
                input.copyTo(zipOut)
                zipOut.closeEntry()
            }
        }
    }
}
