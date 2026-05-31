package com.rostrum.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rostrum.core.util.SortOrder
import com.rostrum.core.util.SortType

/**
 * 面板选项菜单
 */
@Composable
fun PaneOptionsMenu(
    viewModel: MainViewModel,
    isLeft: Boolean,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        // 使用 MoreVert 图标代替 Sort 图标，因为它包含更多功能
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "选项",
                modifier = Modifier.size(20.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 选择操作
            DropdownMenuItem(
                text = { Text("全选") },
                onClick = {
                    viewModel.selectAll(isLeft)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("反选") },
                onClick = {
                    viewModel.invertSelection(isLeft)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("取消选中") },
                onClick = {
                    viewModel.clearSelection(isLeft)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
            )
            
            Divider()

            // 刷新
            DropdownMenuItem(
                text = { Text("刷新") },
                onClick = {
                    viewModel.loadFileList(isLeft)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            )

            // 添加当前路径到书签
            DropdownMenuItem(
                text = { Text("添加书签") },
                onClick = {
                    val path = if (isLeft) viewModel.leftCurrentPath else viewModel.rightCurrentPath
                    com.rostrum.ui.bookmarks.BookmarkManager.add(path)
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                }
            )
            
            // 粘贴功能
            DropdownMenuItem(
                text = { Text("粘贴") },
                onClick = {
                    val targetPath = if (isLeft) viewModel.leftCurrentPath else viewModel.rightCurrentPath
                    viewModel.pasteFiles(targetPath) { success, message ->
                        // 粘贴结果通过回调处理，刷新文件列表
                        viewModel.loadFileList(isLeft)
                    }
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                },
                enabled = !ClipboardManager.isEmpty()
            )
            
            // 过滤
            DropdownMenuItem(
                text = { Text("过滤...") },
                onClick = {
                    onFilterClick()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                }
            )
            
            Divider()
            
            // 排序部分标题 (可选)
            // DropdownMenuItem(text = { Text("排序方式", style = MaterialTheme.typography.labelSmall) }, onClick = {})
            
            // 排序类型
            DropdownMenuItem(
                text = { Text("按名称") },
                onClick = {
                    viewModel.updateSortType(SortType.NAME)
                    expanded = false
                },
                leadingIcon = if (viewModel.sortType == SortType.NAME) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("按大小") },
                onClick = {
                    viewModel.updateSortType(SortType.SIZE)
                    expanded = false
                },
                leadingIcon = if (viewModel.sortType == SortType.SIZE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("按日期") },
                onClick = {
                    viewModel.updateSortType(SortType.DATE)
                    expanded = false
                },
                leadingIcon = if (viewModel.sortType == SortType.DATE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("按类型") },
                onClick = {
                    viewModel.updateSortType(SortType.TYPE)
                    expanded = false
                },
                leadingIcon = if (viewModel.sortType == SortType.TYPE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            
            Divider()
            
            // 排序顺序
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("倒序排列")
                    }
                },
                onClick = {
                    viewModel.toggleSortOrder()
                    expanded = false
                },
                leadingIcon = {
                    Checkbox(
                        checked = viewModel.sortOrder == SortOrder.DESCENDING,
                        onCheckedChange = null // Click handled by parent
                    )
                }
            )
        }
    }
}
