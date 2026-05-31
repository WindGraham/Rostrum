package com.rostrum.core.util

import com.rostrum.core.domain.model.FileItem
import java.io.File

/**
 * 文件操作工具类
 */
object FileOperations {
    
    /**
     * 复制文件或文件夹
     */
    fun copy(source: File, destination: File): Result<Unit> {
        return try {
            if (!source.exists()) {
                Result.failure(Exception("源文件不存在: ${source.path}"))
            } else {
                if (source.isDirectory) {
                    copyDirectory(source, destination)
                } else {
                    copyFile(source, destination)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 复制文件
     */
    private fun copyFile(source: File, destination: File): Result<Unit> {
        return try {
            if (destination.exists()) {
                return Result.failure(Exception("目标文件已存在: ${destination.path}"))
            }
            
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 递归复制目录
     */
    private fun copyDirectory(source: File, destination: File): Result<Unit> {
        return try {
            if (!destination.exists()) {
                destination.mkdirs()
            }
            
            RestrictedPathHelper.listFilesWithZeroWidthPriority(source.absolutePath)?.forEach { file ->
                val destFile = File(destination, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    copyFile(file, destFile)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 移动文件或文件夹
     */
    fun move(source: File, destination: File): Result<Unit> {
        return try {
            if (!source.exists()) {
                Result.failure(Exception("源文件不存在: ${source.path}"))
            } else {
                if (destination.exists()) {
                    Result.failure(Exception("目标文件已存在: ${destination.path}"))
                } else {
                    if (source.renameTo(destination)) {
                        Result.success(Unit)
                    } else {
                        // 如果renameTo失败，尝试复制后删除
                        val copyResult = copy(source, destination)
                        if (copyResult.isSuccess) {
                            delete(source)
                            Result.success(Unit)
                        } else {
                            copyResult
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除文件或文件夹
     */
    fun delete(file: File): Result<Unit> {
        return try {
            if (!file.exists()) {
                Result.failure(Exception("文件不存在: ${file.path}"))
            } else {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    if (file.delete()) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("删除文件失败: ${file.path}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 递归删除目录
     */
    private fun deleteDirectory(directory: File): Result<Unit> {
        return try {
            RestrictedPathHelper.listFilesWithZeroWidthPriority(directory.absolutePath)?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            
            if (directory.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除目录失败: ${directory.path}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 重命名文件或文件夹
     */
    fun rename(file: File, newName: String): Result<File> {
        return try {
            if (!file.exists()) {
                Result.failure(Exception("文件不存在: ${file.path}"))
            } else {
                val parent = file.parentFile ?: return Result.failure(Exception("无法获取父目录"))
                val newFile = File(parent, newName)
                
                if (newFile.exists()) {
                    Result.failure(Exception("目标名称已存在: $newName"))
                } else {
                    if (file.renameTo(newFile)) {
                        Result.success(newFile)
                    } else {
                        Result.failure(Exception("重命名失败: ${file.path}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建新文件夹
     */
    fun createDirectory(parent: File, name: String): Result<File> {
        return try {
            val newDir = File(parent, name)
            if (newDir.exists()) {
                Result.failure(Exception("文件夹已存在: $name"))
            } else {
                if (newDir.mkdirs()) {
                    Result.success(newDir)
                } else {
                    Result.failure(Exception("创建文件夹失败: $name"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * AI生成内容 新建文件并写入
     * @param saveDir 保存目录路径
     * @param fileName 文件名(含后缀)
     * @param aiContent AI生成的完整内容
     */
    fun createNewFileWithAIContent(saveDir: String, fileName: String, aiContent: String): Result<File> {
        return try {
            val dirFile = File(saveDir)
            if (!dirFile.exists() && !dirFile.mkdirs()) {
                return Result.failure(Exception("无法创建目录: $saveDir"))
            }
            val newFile = File(dirFile, fileName)
            if (newFile.exists()) {
                return Result.failure(Exception("文件已存在: ${newFile.absolutePath}"))
            }
            
            // UTF-8编码写入
            newFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(aiContent)
                writer.flush()
            }
            Result.success(newFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * AI修改内容 覆盖写入原文件
     * @param targetPath 目标文件路径
     * @param aiContent AI生成的完整新内容
     */
    fun overrideFileWithAIContent(targetPath: String, aiContent: String): Result<Unit> {
        return try {
            val targetFile = File(targetPath)
            // 如果文件不存在，尝试创建
             if (!targetFile.exists()) {
                if (!targetFile.createNewFile()) {
                     return Result.failure(Exception("文件不存在且无法创建: $targetPath"))
                }
            }
            
            // UTF-8编码覆盖写入
            targetFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(aiContent)
                writer.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

