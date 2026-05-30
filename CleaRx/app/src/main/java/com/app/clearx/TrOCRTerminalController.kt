package com.app.clearx

import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.*

class TrOCRTerminalController(
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle,
    private val overlay: View,
    private val statusText: TextView,
    private val progressText: TextView,
    private val cursorText: TextView
) {
    private var typewriterJob: Job? = null
    private var cursorJob: Job? = null
    private var dismissJob: Job? = null

    var onHidden: (() -> Unit)? = null

    fun showTerminal() {
        overlay.visibility = View.VISIBLE
        startCursorBlink()
    }

    fun updateStatus(stage: TrOCRStage) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        typewriterJob?.cancel()
        dismissJob?.cancel()
        typewriterJob = scope.launch(Dispatchers.Main) {
            progressText.text = "[ stage ${stage.displayIndex} / 5 ]"
            statusText.text = ""
            for (char in stage.label) {
                if (!isActive) return@launch
                statusText.append(char.toString())
                delay(120)
            }
            if (stage == TrOCRStage.DONE) scheduleDismiss()
        }
    }

    private fun scheduleDismiss() {
        dismissJob?.cancel()
        dismissJob = scope.launch(Dispatchers.Main) {
            delay(1500)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                overlay.visibility = View.GONE
                stopCursorBlink()
                val cb = onHidden
                onHidden = null
                cb?.invoke()
            }
        }
    }

    private fun startCursorBlink() {
        cursorJob?.cancel()
        cursorJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                cursorText.visibility = View.VISIBLE
                delay(500)
                cursorText.visibility = View.INVISIBLE
                delay(500)
            }
        }
    }

    private fun stopCursorBlink() {
        cursorJob?.cancel()
        cursorText.visibility = View.INVISIBLE
    }

    fun forceHide() {
        typewriterJob?.cancel()
        dismissJob?.cancel()
        stopCursorBlink()
        overlay.visibility = View.GONE
        onHidden = null
    }

    fun cancel() {
        typewriterJob?.cancel()
        cursorJob?.cancel()
        dismissJob?.cancel()
    }
}
