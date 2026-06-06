package com.termux.app.ui

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

private const val TAG = "TerminalPane"

/**
 * Terminal pane using Termux's own TerminalView, filling the allocated area.
 * Handles the critical issue where TermuxService creates sessions with a
 * no-op TerminalSessionClient that doesn't trigger view invalidation.
 *
 * The fix: after attachSession(), we replace the session's client with one
 * that calls terminalView.onScreenUpdated() on every onTextChanged() callback.
 */
@Composable
fun TerminalPane(
    session: TerminalSession?,
    pendingCommand: String? = null,
    existingView: TerminalView? = null,
    onCommandWritten: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fontSize = com.termux.app.settings.SettingsStore.terminalFontSize

    val terminalView = remember { existingView ?: TerminalView(context, null).apply {
        isFocusable = true
        isFocusableInTouchMode = true
        setTextSize(fontSize)
        setTerminalViewClient(TerminalPaneViewClient(this))
    } }.also {
        if (existingView != null) {
            existingView.isFocusable = true
            existingView.isFocusableInTouchMode = true
            existingView.requestFocus()
            existingView.setTerminalViewClient(TerminalPaneViewClient(existingView))
        }
    }

    // React to font size setting changes
    LaunchedEffect(fontSize) {
        terminalView.setTextSize(fontSize)
        Log.d(TAG, "Font size changed to $fontSize")
    }

    LaunchedEffect(session, pendingCommand) {
        if (session != null) {
            Log.d(TAG, "Attaching session to TerminalView")
            attachSessionSafely(terminalView, session, pendingCommand, onCommandWritten)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "TerminalPane disposed")
        }
    }

    AndroidView(
        factory = { terminalView },
        modifier = modifier.fillMaxSize()
    )
}

private fun attachSessionSafely(terminalView: TerminalView, session: TerminalSession, pendingCommand: String? = null, onCommandWritten: (() -> Unit)? = null) {
    terminalView.post {
        if (terminalView.width > 0 && terminalView.height > 0) {
            try {
                terminalView.attachSession(session)
                session.updateTerminalSessionClient(RefreshSessionClient(terminalView, session))
                terminalView.requestFocus()
                if (pendingCommand != null) {
                    terminalView.postDelayed({
                        try {
                            session.write(pendingCommand)
                            Log.d(TAG, "Wrote pending command: ${pendingCommand.take(50)}...")
                            onCommandWritten?.invoke()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write pending command", e)
                        }
                    }, 500)
                }
                Log.d(TAG, "Session attached, client updated for refresh, focused=${terminalView.isFocused}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach session, retrying...", e)
                terminalView.postDelayed({ attachSessionSafely(terminalView, session, pendingCommand, onCommandWritten) }, 100)
            }
        } else {
            Log.d(TAG, "TerminalView not ready (${terminalView.width}x${terminalView.height}), deferring...")
            terminalView.postDelayed({ attachSessionSafely(terminalView, session, pendingCommand, onCommandWritten) }, 100)
        }
    }
}

/**
 * TerminalSessionClient that delegates to the original client for everything,
 * but ensures onTextChanged() triggers TerminalView.onScreenUpdated() → invalidate() → onDraw().
 */
private class RefreshSessionClient(
    private val terminalView: TerminalView,
    private val session: TerminalSession
) : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) {
        if (changedSession == session) {
            Log.v(TAG, "onTextChanged → calling onScreenUpdated()")
            terminalView.onScreenUpdated()
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int = 0

    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "Exception", e) }
}

/**
 * TerminalViewClient for keyboard/IME interaction.
 */
private class TerminalPaneViewClient(private val terminalView: TerminalView) : TerminalViewClient {
    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) {
        terminalView.requestFocus()
        val imm = terminalView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() {
        Log.d(TAG, "onEmulatorSet - terminal emulator ready")
    }

    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "Exception", e) }
}
