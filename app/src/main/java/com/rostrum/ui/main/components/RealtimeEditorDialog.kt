package com.rostrum.ui.main.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rostrum.ui.main.HtmlRealtimeEditorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * HTML实时编辑器对话框
 * 全屏覆盖显示HTML编辑器
 */
@Composable
fun RealtimeEditorDialog(
    filePath: String,
    onClose: () -> Unit,
    onSave: (String, String, (Boolean, String?) -> Unit) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            HtmlRealtimeEditorScreen(
                filePath = filePath,
                onClose = onClose,
                onSave = { path, content ->
                    onSave(path, content) { success, message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message ?: (if (success) "保存成功" else "保存失败")
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
