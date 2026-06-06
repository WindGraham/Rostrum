package com.termux.app.data

import java.io.File

/**
 * File item data class - migrated from Rostrum
 */
data class FileItem(
    val file: File? = null,           // null for remote files
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val childCount: Int = 0,          // -1 for remote (unknown)
    val permissions: String? = null,
    val mimeType: String? = null,
    val isRemote: Boolean = false
) {
    companion object {
        fun fromFile(file: File): FileItem {
            return FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified(),
                extension = file.extension,
                childCount = if (file.isDirectory) (file.list()?.size ?: 0) else 0,
                permissions = null,
                mimeType = null
            )
        }
    }
}
