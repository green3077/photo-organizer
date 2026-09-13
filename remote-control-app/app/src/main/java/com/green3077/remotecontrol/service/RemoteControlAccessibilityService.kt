package com.green3077.remotecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.view.accessibility.AccessibilityEvent

/**
 * Bridges input events received from a remote controller into real touch gestures on this
 * device. The user must explicitly enable this service under Settings > Accessibility before any
 * input can be injected; Android will not allow it to run silently in the background otherwise.
 */
class RemoteControlAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RemoteControlAccessibilityService? = null
            private set

        private const val STEP_DURATION_MS = 50L
    }

    private val lock = Any()
    private var currentStroke: GestureDescription.StrokeDescription? = null
    private var lastPoint: PointF? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used: this service only injects gestures, it does not read screen content.
    }

    override fun onInterrupt() {}

    /** action: 0 = down, 1 = move, 2 = up. x/y are absolute pixel coordinates on this device. */
    fun handleInput(action: Int, x: Float, y: Float) = synchronized(lock) {
        when (action) {
            0 -> {
                val path = Path().apply { moveTo(x, y) }
                val stroke = GestureDescription.StrokeDescription(path, 0, STEP_DURATION_MS, true)
                dispatch(stroke)
                currentStroke = stroke
                lastPoint = PointF(x, y)
            }
            1 -> {
                val from = lastPoint ?: PointF(x, y)
                val stroke = currentStroke
                val path = Path().apply {
                    moveTo(from.x, from.y)
                    lineTo(x, y)
                }
                val next = if (stroke != null) {
                    stroke.continueStroke(path, 0, STEP_DURATION_MS, true)
                } else {
                    GestureDescription.StrokeDescription(path, 0, STEP_DURATION_MS, true)
                }
                dispatch(next)
                currentStroke = next
                lastPoint = PointF(x, y)
            }
            2 -> {
                val from = lastPoint ?: PointF(x, y)
                val stroke = currentStroke
                val path = Path().apply {
                    moveTo(from.x, from.y)
                    lineTo(x, y)
                }
                val next = if (stroke != null) {
                    stroke.continueStroke(path, 0, STEP_DURATION_MS, false)
                } else {
                    GestureDescription.StrokeDescription(path, 0, STEP_DURATION_MS, false)
                }
                dispatch(next)
                currentStroke = null
                lastPoint = null
            }
        }
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
