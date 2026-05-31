package com.rostrum.core.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ZIP文件操作工具类
 */
object ZipUtils {

    /**
     * 压缩文件或文件夹
     * @param sources 源文件列表
     * @param destination 目标zip文件
     */
    fun zip(sources: List<File>, destination: File): Result<Unit> {
        return try {
            if (sources.isEmpty()) {
                return Result.failure(Exception("没有文件被选中"))
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zos ->
                for (file in sources) {
                    if (file.exists()) {
                        zipFile(file, file.name, zos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun zipFile(fileToZip: File, fileName: String, zos: ZipOutputStream) {
        if (fileToZip.isHidden) {
            return
        }
        if (fileToZip.isDirectory) {
            if (fileName.endsWith("/")) {
                zos.putNextEntry(ZipEntry(fileName))
                zos.closeEntry()
            } else {
                zos.putNextEntry(ZipEntry("$fileName/"))
                zos.closeEntry()
            }
            val children = RestrictedPathHelper.listFilesWithZeroWidthPriority(fileToZip.absolutePath)
            children?.forEach { childFile ->
                zipFile(childFile, "$fileName/${childFile.name}", zos)
            }
            return
        }
        
        FileInputStream(fileToZip).use { fis ->
            val zipEntry = ZipEntry(fileName)
            zos.putNextEntry(zipEntry)
            val bytes = ByteArray(1024)
            var length: Int
            while (fis.read(bytes).also { length = it } >= 0) {
                zos.write(bytes, 0, length)
            }
            zos.closeEntry()
        }
    }

    /**
     * 解压文件
     * @param zipFile 源zip文件
     * @param destinationDir 目标文件夹
     */
    fun unzip(zipFile: File, destinationDir: File): Result<Unit> {
        return try {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var zipEntry = zis.nextEntry
                val buffer = ByteArray(1024)
                while (zipEntry != null) {
                    val newFile = File(destinationDir, zipEntry.name)
                    
                    // 防止Zip Slip漏洞
                    if (!newFile.canonicalPath.startsWith(destinationDir.canonicalPath + File.separator)) {
                        throw Exception("Zip entry is outside of the target dir: ${zipEntry.name}")
                    }
                    
                    if (zipEntry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        // 确保父文件夹存在
                        File(newFile.parent!!).mkdirs()
                        
                        FileOutputStream(newFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zipEntry = zis.nextEntry
                }
                zis.closeEntry()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}