package com.rostrum.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rostrum.core.domain.model.Bookmark

/**
 * 书签列表底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onEditBookmark: (Bookmark, String, String) -> Unit, // oldPath, newName, newPath
    onDismiss: () -> Unit
) {
    var bookmarkToEdit by remember { mutableStateOf<Bookmark?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "书签",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无书签",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(bookmarks) { bookmark ->
                        ListItem(
                            headlineContent = { Text(bookmark.name) },
                            supportingContent = { Text(bookmark.path) },
                            leadingContent = {
                                Icon(
                                    imageVector = if (bookmark.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { bookmarkToEdit = bookmark }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "编辑书签"
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bookmark) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除书签"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                onBookmarkClick(bookmark)
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
    
    if (bookmarkToEdit != null) {
        EditBookmarkDialog(
            bookmark = bookmarkToEdit!!,
            onConfirm = { name, path ->
                onEditBookmark(bookmarkToEdit!!, name, path)
                bookmarkToEdit = null
            },
            onDismiss = { bookmarkToEdit = null }
        )
    }
}

@Composable
fun EditBookmarkDialog(
    bookmark: Bookmark,
    onConfirm: (String, String) -> Unit, // name, path
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(bookmark.name) }
    var path by remember { mutableStateOf(bookmark.path) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑书签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && path.isNotBlank()) {
                        onConfirm(name, path)
                    }
                },
                enabled = name.isNotBlank() && path.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}