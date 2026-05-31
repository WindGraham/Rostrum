package com.rostrum.core.plugin.impl

import com.rostrum.core.plugin.Workspace
import com.rostrum.core.plugin.WorkspaceConfiguration
import com.rostrum.core.plugin.WorkspaceFolder
import com.rostrum.core.domain.model.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Workspace实现
 * 
 * 集成到主程序的文件管理功能
 */
class WorkspaceImpl(
    private val rootPath: String,
    private val selectedFilesFlow: StateFlow<List<FileItem>>
) : Workspace {
    
    override val folders: List<WorkspaceFolder> = listOf(
        WorkspaceFolder(
            uri = "file://$rootPath",
            name = File(rootPath).name.ifEmpty { "Root" },
            index = 0
        )
    )
    
    override val selectedFiles: StateFlow<List<FileItem>> = selectedFilesFlow
    
    override suspend fun openFile(file: FileItem) {
        // TODO: 集成到MainViewModel的openFile逻辑
        // 这里可以通过回调或事件总线通知主程序打开文件
    }
    
    override fun getConfiguration(section: String?): WorkspaceConfiguration {
        return com.rostrum.core.plugin.impl.WorkspaceConfigurationImpl()
    }
}

