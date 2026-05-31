package com.rostrum.core.plugin.impl

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import com.rostrum.core.plugin.InputBoxOptions
import com.rostrum.core.plugin.StatusBarAlignment
import com.rostrum.core.plugin.StatusBarItem
import com.rostrum.core.plugin.Window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Window实现
 * 
 * 集成到主程序的UI显示功能
 */
class WindowImpl(
    private val context: Context,
    private val snackbarHostState: SnackbarHostState?,
    private val coroutineScope: CoroutineScope
) : Window {
    
    override suspend fun showInformationMessage(
        message: String,
        vararg items: String
    ): String? {
        // 显示Snackbar
        snackbarHostState?.let { snackbar ->
            coroutineScope.launch {
                snackbar.showSnackbar(message)
            }
        }
        // TODO: 如果需要选择项，显示对话框
        return null
    }
    
    override suspend fun showWarningMessage(
        message: String,
        vararg items: String
    ): String? {
        snackbarHostState?.let { snackbar ->
            coroutineScope.launch {
                snackbar.showSnackbar(message)
            }
        }
        return null
    }
    
    override suspend fun showErrorMessage(
        message: String,
        vararg items: String
    ): String? {
        snackbarHostState?.let { snackbar ->
            coroutineScope.launch {
                snackbar.showSnackbar(message)
            }
        }
        return null
    }
    
    override suspend fun showInputBox(options: InputBoxOptions?): String? {
        // TODO: 实现输入框对话框
        // 可以使用AlertDialog或自定义对话框
        return null
    }
    
    override fun createStatusBarItem(alignment: StatusBarAlignment): StatusBarItem {
        return com.rostrum.core.plugin.impl.StatusBarItemImpl()
    }
}

