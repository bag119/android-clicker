package com.creation.city.clicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * 悬浮控制器服务（前台服务）：
 * - 一个可拖动的悬浮面板：录制 / 停止录制 / 播放 / 停止播放 / 清除
 * - 一个全屏透明捕获层：录制时变为可触摸，用来抓屏幕坐标；空闲时设为 NOT_TOUCHABLE 让触摸穿透到目标 App
 * - 播放时用 AutoClickerAccessibilityService 的 dispatchGesture 真正点下去
 */
class FloatingControlService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var panelView: View
    private lateinit var captureView: View
    private val handler = Handler(Looper.getMainLooper())
    private val points = mutableListOf<TapAction>()

    private var recording = false
    private var playing = false
    private var lastTapTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var lastIndex = -1
    private var loop = false
    private var remainingLoops = 0

    companion object {
        var instance: FloatingControlService? = null
        const val ACTION_SAVE = "com.creation.city.clicker.ACTION_SAVE"
        const val ACTION_LOAD = "com.creation.city.clicker.ACTION_LOAD"
        const val EXTRA_NAME = "name"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1, buildNotification())
        addCaptureLayer()
        addPanel()
    }

    override fun onDestroy() {
        instance = null
        try { wm.removeView(panelView) } catch (_: Exception) {}
        try { wm.removeView(captureView) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SAVE -> {
                val name = intent.getStringExtra(EXTRA_NAME)
                if (!name.isNullOrEmpty()) {
                    ScriptStore.saveScript(this, Script(name, points.toList()))
                    toast("已保存：$name")
                }
            }
            ACTION_LOAD -> {
                val name = intent.getStringExtra(EXTRA_NAME)
                if (!name.isNullOrEmpty()) {
                    val s = ScriptStore.loadScript(this, name)
                    if (s != null) {
                        points.clear(); points.addAll(s.points)
                        updateStatus()
                        toast("已载入：$name (${points.size} 点)")
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun addCaptureLayer() {
        captureView = LayoutInflater.from(this).inflate(R.layout.capture_layer, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        captureView.setOnTouchListener { _, event -> onCaptureTouch(event) }
        wm.addView(captureView, params)
    }

    private fun addPanel() {
        panelView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200
        bindPanel()
        wm.addView(panelView, params)
        makeDraggable(panelView.findViewById(R.id.dragHandle), params)
    }

    private fun bindPanel() {
        panelView.findViewById<Button>(R.id.btnRecord).setOnClickListener { startRecording() }
        panelView.findViewById<Button>(R.id.btnStopRec).setOnClickListener { stopRecording() }
        panelView.findViewById<Button>(R.id.btnPlay).setOnClickListener { startPlaying() }
        panelView.findViewById<Button>(R.id.btnStopPlay).setOnClickListener { stopPlaying() }
        panelView.findViewById<Button>(R.id.btnClear).setOnClickListener {
            points.clear(); lastTapTime = 0; updateStatus()
        }
        panelView.findViewById<CheckBox>(R.id.chkLoop)
            .setOnCheckedChangeListener { _, c -> loop = c }
        updateStatus()
    }

    private fun makeDraggable(handle: View, params: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var initX = 0; var initY = 0
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX.toInt(); startY = e.rawY.toInt()
                    initX = params.x; initY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (e.rawX.toInt() - startX)
                    params.y = initY + (e.rawY.toInt() - startY)
                    wm.updateViewLayout(panelView, params)
                }
            }
            true
        }
    }

    private fun startRecording() {
        recording = true
        lastTapTime = 0
        // 让捕获层变为可触摸（去掉 NOT_TOUCHABLE）
        val p = captureView.layoutParams as WindowManager.LayoutParams
        p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        wm.updateViewLayout(captureView, p)
        captureView.setBackgroundColor(0x11000000) // 轻微蒙层提示正在录制
        updateStatus()
        toast("录制中：在屏幕上点击 / 滑动来记录动作")
    }

    private fun stopRecording() {
        recording = false
        val p = captureView.layoutParams as WindowManager.LayoutParams
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(captureView, p)
        captureView.setBackgroundColor(0x00000000)
        updateStatus()
    }

    private fun onCaptureTouch(event: MotionEvent): Boolean {
        if (!recording) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY
                val now = System.currentTimeMillis()
                val delay = if (lastTapTime == 0L) 0L else now - lastTapTime
                lastTapTime = now
                points.add(TapAction("tap", downX, downY, delayBeforeMs = delay))
                lastIndex = points.size - 1
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                // 拖动了超过 30px 就当成一次滑动
                if (Math.hypot(dx.toDouble(), dy.toDouble()) > 30 && lastIndex >= 0) {
                    val t = points[lastIndex]
                    points[lastIndex] = t.copy(type = "swipe", ex = event.rawX, ey = event.rawY)
                }
                updateStatus()
            }
        }
        return true
    }

    private fun startPlaying() {
        if (points.isEmpty()) { toast("没有可播放的动作"); return }
        if (AutoClickerAccessibilityService.instance == null) { toast("请先开启无障碍服务"); return }
        playing = true
        remainingLoops = if (loop) Int.MAX_VALUE else 1
        playNext(0)
        updateStatus()
    }

    private fun stopPlaying() {
        playing = false
        updateStatus()
    }

    private fun playNext(index: Int) {
        if (!playing) return
        if (index >= points.size) {
            remainingLoops--
            if (loop && remainingLoops > 0) {
                handler.postDelayed({ playNext(0) }, 500)
            } else {
                playing = false
                updateStatus()
            }
            return
        }
        val p = points[index]
        handler.postDelayed({
            if (!playing) return@postDelayed
            val svc = AutoClickerAccessibilityService.instance
            if (svc == null) {
                playing = false; updateStatus(); toast("无障碍服务丢失，请重新开启"); return@postDelayed
            }
            if (p.type == "swipe") {
                svc.performSwipe(p.x, p.y, p.ex, p.ey, p.swipeMs) { playNext(index + 1) }
            } else {
                svc.performTap(p.x, p.y, 1) { playNext(index + 1) }
            }
        }, p.delayBeforeMs)
    }

    private fun updateStatus() {
        handler.post {
            val tv = panelView.findViewById<TextView?>(R.id.txtStatus)
            val state = if (recording) "录制中" else if (playing) "播放中" else "就绪"
            tv?.text = "$state | 点数:${points.size}${if (loop) " | 循环" else ""}"
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                "clicker", "Clicker", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "clicker")
            .setContentTitle("创城自动点击器")
            .setContentText("控制器运行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

    private fun toast(s: String) {
        handler.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    }
}
