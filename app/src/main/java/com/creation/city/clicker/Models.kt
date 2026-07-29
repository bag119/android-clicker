package com.creation.city.clicker

data class TapAction(
    val type: String,            // "tap" or "swipe"
    val x: Float,                // start x (display pixels)
    val y: Float,                // start y
    val ex: Float = 0f,          // swipe end x
    val ey: Float = 0f,          // swipe end y
    val delayBeforeMs: Long = 0, // wait before this action (since previous action)
    val swipeMs: Long = 300      // swipe duration
)

data class Script(
    val name: String,
    val points: List<TapAction>,
    val createdAt: Long = System.currentTimeMillis()
)
