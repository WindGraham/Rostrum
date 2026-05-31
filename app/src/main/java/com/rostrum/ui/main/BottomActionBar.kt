package com.rostrum.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rostrum.core.util.FileShareUtils
import kotlinx.coroutines.delay

/**
 * 底部操作栏（文件操作按钮）
 */
@Composable
fun BottomActionBar(
    viewModel: MainViewModel,
    leftSelectedFiles: Set<String>,
    rightSelectedFiles: Set<String>,
    onBookmarksClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showZipDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var createFolderIsLeft by remember { mutableStateOf(true) }
    var deleteFromLeft by remember { mutableStateOf(true) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var showMessage by remember { mutableStateOf(false) }
    
    // 显示操作结果消息
    if (showMessage && operationMessage != null) {
        LaunchedEffect(operationMessage) {
            delay(2000)
            showMessage = false
            operationMessage = null
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            // 消息提示
            if (showMessage && operationMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = operationMessage!!,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 书签按钮
                IconButton(onClick = onBookmarksClick) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = "书签"
                    )
                }

                // 同步按钮
                IconButton(
                    onClick = {
                        viewModel.syncPath(true) // 将左侧同步到右侧
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "同步路径"
                    )
                }
                
                // 新建文件夹按钮
                IconButton(
                    onClick = {
                        createFolderIsLeft = leftSelectedFiles.isEmpty() && rightSelectedFiles.isEmpty()
                        showCreateFolderDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "新建文件夹"
                    )
                }

                // 新建文件按钮
                IconButton(
                    onClick = {
                        createFolderIsLeft = leftSelectedFiles.isEmpty() && rightSelectedFiles.isEmpty()
                        showCreateFileDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.NoteAdd,
                        contentDescription = "新建文件"
                    )
                }
                
                // 如果有选中文件，显示操作按钮
                val hasLeftSelection = leftSelectedFiles.isNotEmpty()
                val hasRightSelection = rightSelectedFiles.isNotEmpty()
                
                if (hasLeftSelection || hasRightSelection) {
                    // 复制按钮（从选中侧复制到另一侧）
                    IconButton(
                        onClick = {
                            val fromLeft = hasLeftSelection
                            viewModel.copyFiles(fromLeft) { success, message ->
                                operationMessage = message
                                showMessage = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制"
                        )
                    }
                    
                    // 移动按钮
                    IconButton(
                        onClick = {
                            val fromLeft = hasLeftSelection
                            viewModel.moveFiles(fromLeft) { success, message ->
                                operationMessage = message
                                showMessage = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = "移动"
                        )
                    }

                    // 对比按钮
                    if (leftSelectedFiles.size == 1 && rightSelectedFiles.size == 1) {
                        IconButton(
                            onClick = {
                                viewModel.openFileComparator()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = "对比"
                            )
                        }
                    }

                    // 压缩按钮
                    IconButton(
                        onClick = {
                            createFolderIsLeft = hasLeftSelection // 复用这个变量来标记是在哪边操作
                            showZipDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "压缩"
                        )
                    }
                    
                    // 分享按钮
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val files = if (hasLeftSelection) {
                                leftSelectedFiles.map { java.io.File(it) }
                            } else {
                                rightSelectedFiles.map { java.io.File(it) }
                            }
                            FileShareUtils.shareFiles(context, files)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享"
                        )
                    }
                    // 批量重命名按钮
                    if ((hasLeftSelection && viewModel.leftSelectedFiles.size > 1) || 
                        (hasRightSelection && viewModel.rightSelectedFiles.size > 1)) {
                        IconButton(
                            onClick = {
                                createFolderIsLeft = hasLeftSelection
                                showBatchRenameDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = "批量重命名"
                            )
                        }
                    }                    
                    // 删除按钮
                    IconButton(
                        onClick = {
                            deleteFromLeft = hasLeftSelection
                            showDeleteDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // 选中文件数量显示
                if (hasLeftSelection || hasRightSelection) {
                    val totalSelected = leftSelectedFiles.size + rightSelectedFiles.size
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已选中: $totalSelected",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的文件吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFiles(deleteFromLeft) { success, message ->
                            operationMessage = message
                            showMessage = true
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { folderName ->
                if (folderName.isNotBlank()) {
                    viewModel.createDirectory(folderName, createFolderIsLeft) { success, message ->
                        operationMessage = message
                        showMessage = true
                    }
                }
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // 新建文件对话框
    if (showCreateFileDialog) {
        CreateFileDialog(
            onConfirm = { fileName ->
                if (fileName.isNotBlank()) {
                    viewModel.createFile(fileName, createFolderIsLeft) { success, message ->
                        operationMessage = message
                        showMessage = true
                    }
                }
                showCreateFileDialog = false
            },
            onDismiss = { showCreateFileDialog = false }
        )
    }

    // 压缩文件对话框
    if (showZipDialog) {
        CreateZipDialog(
            onConfirm = { zipName ->
                if (zipName.isNotBlank()) {
                    viewModel.zipFiles(createFolderIsLeft, zipName) { success, message ->
                        operationMessage = message
                        showMessage = true
                    }
                }
                showZipDialog = false
            },
            onDismiss = { showZipDialog = false }
        )
    }

    // 批量重命名对话框
    if (showBatchRenameDialog) {
        // 获取当前选中侧的文件列表
        val selectedFiles = if (createFolderIsLeft) {
             viewModel.leftFileList.filter { viewModel.leftSelectedFiles.contains(it.path) }
        } else {
             viewModel.rightFileList.filter { viewModel.rightSelectedFiles.contains(it.path) }
        }
        
        BatchRenameDialog(
            files = selectedFiles,
            onConfirm = { renames ->
                viewModel.batchRenameFiles(createFolderIsLeft, renames) { success, message ->
                    operationMessage = message
                    showMessage = true
                }
                showBatchRenameDialog = false
            },
            onDismiss = { showBatchRenameDialog = false }
        )
    }
}

/**
 * 压缩文件对话框
 */
@Composable
private fun CreateZipDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var zipName by remember { mutableStateOf("archive.zip") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("压缩文件") },
        text = {
            OutlinedTextField(
                value = zipName,
                onValueChange = { zipName = it },
                label = { Text("压缩包名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (zipName.isNotBlank()) {
                        onConfirm(zipName)
                    }
                },
                enabled = zipName.isNotBlank()
            ) {
                Text("压缩")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 新建文件夹对话框
 */
@Composable
private fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("新建文件夹") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("文件夹名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onConfirm(folderName)
                    }
                },
                enabled = folderName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 新建文件对话框
 */
@Composable
private fun CreateFileDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var fileName by remember { mutableStateOf("new_file.txt") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件") },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("文件名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onConfirm(fileName)
                    }
                },
                enabled = fileName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
