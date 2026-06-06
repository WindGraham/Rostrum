package com.termux.app

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * Resizable terminal pane with white background and pink drag handles.
 * Replicates the successful embeddable terminal design.
 */
class ResizableTerminalPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val TAG = "ResizableTerminalPane"

    private lateinit var terminalView: TerminalView
    private lateinit var handleTop: View
    private lateinit var handleBottom: View
    private lateinit var handleLeft: View
    private lateinit var handleRight: View

    private var containerLeft = 0
    private var containerTop = 0
    private var containerWidth = 0
    private var containerHeight = 0
    private var originalContainerTop = 0

    private val handleSize = dpToPx(24)
    private val minContainerSize = dpToPx(120)

    init {
        setupView()
    }

    private fun setupView() {
        // White background for the pane
        setBackgroundColor(0xFFFFFFFF.toInt())

        // Create TerminalView (black background)
        terminalView = TerminalView(context, null).apply {
            setBackgroundColor(0xFF000000.toInt())
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            isFocusableInTouchMode = true
            isFocusable = true
            // CRITICAL: Initialize renderer before attachSession
            setTextSize(14)
            // CRITICAL: Set client before attachSession
            setTerminalViewClient(SimpleTerminalViewClient())
        }
        addView(terminalView)

        // Create pink drag handles
        handleTop = createHandle()
        handleBottom = createHandle()
        handleLeft = createHandle()
        handleRight = createHandle()

        addView(handleTop)
        addView(handleBottom)
        addView(handleLeft)
        addView(handleRight)

        // Setup drag listeners
        setupDragHandles()

        Log.d(TAG, "ResizableTerminalPane created")
    }

    private fun createHandle(): View {
        return View(context).apply {
            setBackgroundColor(0xFFFF69B4.toInt()) // Pink
            layoutParams = LayoutParams(handleSize, handleSize)
        }
    }

    private fun setupDragHandles() {
        handleTop.setOnTouchListener(DragHandleListener(DragEdge.TOP))
        handleBottom.setOnTouchListener(DragHandleListener(DragEdge.BOTTOM))
        handleLeft.setOnTouchListener(DragHandleListener(DragEdge.LEFT))
        handleRight.setOnTouchListener(DragHandleListener(DragEdge.RIGHT))
    }

    private enum class DragEdge { TOP, BOTTOM, LEFT, RIGHT }

    private inner class DragHandleListener(private val edge: DragEdge) : OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startLeft = 0
        private var startTop = 0
        private var startWidth = 0
        private var startHeight = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startLeft = containerLeft
                    startTop = containerTop
                    startWidth = containerWidth
                    startHeight = containerHeight
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()

                    when (edge) {
                        DragEdge.TOP -> {
                            containerTop = startTop + dy
                            containerHeight = startHeight - dy
                        }
                        DragEdge.BOTTOM -> {
                            containerHeight = startHeight + dy
                        }
                        DragEdge.LEFT -> {
                            containerLeft = startLeft + dx
                            containerWidth = startWidth - dx
                        }
                        DragEdge.RIGHT -> {
                            containerWidth = startWidth + dx
                        }
                    }

                    applyContainerBounds()
                    return true
                }
            }
            return false
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (containerWidth == 0) {
            // Initial size: 80% of parent width, 60% of parent height, centered
            containerWidth = (width * 0.8).toInt()
            containerHeight = (height * 0.6).toInt()
            containerLeft = (width - containerWidth) / 2
            containerTop = (height - containerHeight) / 2
            originalContainerTop = containerTop
        }

        applyContainerBounds()
    }

    private fun applyContainerBounds() {
        // Constrain to parent bounds
        containerLeft = containerLeft.coerceIn(0, width - minContainerSize)
        containerTop = containerTop.coerceIn(0, height - minContainerSize)
        containerWidth = containerWidth.coerceIn(minContainerSize, width - containerLeft)
        containerHeight = containerHeight.coerceIn(minContainerSize, height - containerTop)

        // Layout terminal view
        terminalView.layout(
            containerLeft,
            containerTop,
            containerLeft + containerWidth,
            containerTop + containerHeight
        )

        // Layout handles at corners/edges
        val halfHandle = handleSize / 2

        // Top handle (center of top edge)
        handleTop.layout(
            containerLeft + containerWidth / 2 - halfHandle,
            containerTop - halfHandle,
            containerLeft + containerWidth / 2 + halfHandle,
            containerTop + halfHandle
        )

        // Bottom handle (center of bottom edge)
        handleBottom.layout(
            containerLeft + containerWidth / 2 - halfHandle,
            containerTop + containerHeight - halfHandle,
            containerLeft + containerWidth / 2 + halfHandle,
            containerTop + containerHeight + halfHandle
        )

        // Left handle (center of left edge)
        handleLeft.layout(
            containerLeft - halfHandle,
            containerTop + containerHeight / 2 - halfHandle,
            containerLeft + halfHandle,
            containerTop + containerHeight / 2 + halfHandle
        )

        // Right handle (center of right edge)
        handleRight.layout(
            containerLeft + containerWidth - halfHandle,
            containerTop + containerHeight / 2 - halfHandle,
            containerLeft + containerWidth + halfHandle,
            containerTop + containerHeight / 2 + halfHandle
        )
    }

    private var pendingSession: TerminalSession? = null

    fun attachSession(session: TerminalSession) {
        pendingSession = session
        // Defer attach until TerminalView has been laid out and renderer initialized
        post {
            if (terminalView.width > 0 && terminalView.height > 0) {
                try {
                    terminalView.attachSession(session)
                    Log.d(TAG, "Session attached successfully")
                    pendingSession = null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to attach session, will retry", e)
                    postDelayed({ attachSession(session) }, 100)
                }
            } else {
                Log.d(TAG, "TerminalView not ready, deferring session attach")
                postDelayed({ attachSession(session) }, 100)
            }
        }
    }

    fun getTerminalView(): TerminalView = terminalView

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Minimal TerminalViewClient implementation for the workspace terminal.
     */
    inner class SimpleTerminalViewClient : com.termux.view.TerminalViewClient {
        override fun onScale(scale: Float): Float = scale
        override fun onSingleTapUp(e: android.view.MotionEvent) {
            terminalView.requestFocus()
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        override fun shouldBackButtonBeMappedToEscape(): Boolean = false
        override fun shouldEnforceCharBasedInput(): Boolean = false
        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
        override fun isTerminalViewSelected(): Boolean = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent, session: com.termux.terminal.TerminalSession): Boolean = false
        override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent): Boolean = false
        override fun onLongPress(event: android.view.MotionEvent): Boolean = false
        override fun readControlKey(): Boolean = false
        override fun readAltKey(): Boolean = false
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: com.termux.terminal.TerminalSession): Boolean = false
        override fun onEmulatorSet() {}
        override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "Exception", e) }
    }
}
