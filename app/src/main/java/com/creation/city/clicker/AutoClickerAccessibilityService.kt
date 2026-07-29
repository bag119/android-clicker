package com.creation.city.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：真正在系统层面模拟点击/滑动的地方。
 * 通过 dispatchGesture 实现全局坐标点击，无需 root。
 */
class AutoClickerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        // canPerformGestures 已在 accessibility_service_config.xml 中声明为 true
        // 系统会自动赋予 dispatchGesture 能力，无需额外设 flag
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 单击：在 (x,y) 处点一下 */
    fun performTap(x: Float, y: Float, durationMs: Long = 1, onDone: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {}
        }, Handler(Looper.getMainLooper()))
    }

    /** 滑动：从 (x1,y1) 滑到 (x2,y2)，耗时 durationMs */
    fun performSwipe(
        x1: Float, y1: Float, x2: Float, y2: Float,
        durationMs: Long, onDone: (() -> Unit)? = null
    ) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone?.invoke()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {}
        }, Handler(Looper.getMainLooper()))
    }

    companion object {
        var instance: AutoClickerAccessibilityService? = null
    }
}
