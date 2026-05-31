package com.rostrum.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rostrum.core.util.SortOrder
import com.rostrum.core.util.SortType

/**
 * 排序菜单（下拉菜单或对话框）
 */
@Composable
fun SortMenu(
    currentSortType: SortType,
    currentSortOrder: SortOrder,
    onSortTypeChange: (SortType) -> Unit,
    onSortOrderToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "排序"
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 排序类型
            DropdownMenuItem(
                text = { Text("按名称") },
                onClick = {
                    onSortTypeChange(SortType.NAME)
                    expanded = false
                },
                leadingIcon = if (currentSortType == SortType.NAME) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("按大小") },
                onClick = {
                    onSortTypeChange(SortType.SIZE)
                    expanded = false
                },
                leadingIcon = if (currentSortType == SortType.SIZE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("按日期") },
                onClick = {
                    onSortTypeChange(SortType.DATE)
                    expanded = false
                },
                leadingIcon = if (currentSortType == SortType.DATE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text("按类型") },
                onClick = {
                    onSortTypeChange(SortType.TYPE)
                    expanded = false
                },
                leadingIcon = if (currentSortType == SortType.TYPE) {
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
                        Text("排序顺序: ")
                        Text(
                            if (currentSortOrder == SortOrder.ASCENDING) "升序" else "降序"
                        )
                    }
                },
                onClick = {
                    onSortOrderToggle()
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        if (currentSortOrder == SortOrder.ASCENDING) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                        contentDescription = null
                    )
                }
            )
        }
    }
}

