package com.jellyfish.ocean

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Choreographer
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/** 透明背景，只畫水母。放在不可觸控的全螢幕懸浮視窗裡。 */
class OverlayView(context: Context) : View(context), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs = Prefs.get(context)
    private val random = Random(System.nanoTime())
    private val jellies = ArrayList<Jelly>()
    private val choreographer = Choreographer.getInstance()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var count = Prefs.DEF_COUNT
    private var speed = 1f
    private var opacity = 0.6f
    private var powerSave = false

    private var running = false
    private var lastNanos = 0L
    private var time = 0f
    private var tiltX = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            var dt = if (lastNanos == 0L) 0.016f else (frameTimeNanos - lastNanos) / 1e9f
            // 省電模式：約 30 FPS，過短的間隔直接略過
            if (powerSave && dt < 0.028f) {
                choreographer.postFrameCallback(this)
                return
            }
            lastNanos = frameTimeNanos
            dt = dt.coerceIn(0.001f, 0.05f)
            time += dt
            for (j in jellies) j.update(dt, speed, tiltX, width.toFloat(), height.toFloat(), time)
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopLoop()
                Intent.ACTION_SCREEN_ON -> startLoop()
            }
        }
    }

    init {
        loadPrefs()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(screenReceiver, filter)
        }
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        jellies.clear()
        syncJellies()
    }

    override fun onDraw(canvas: Canvas) {
        val a = (opacity * 1.2f).coerceAtMost(1f)
        for (j in jellies) j.draw(canvas, time, a, 0f)
    }

    // ---------- 迴圈控制 ----------

    private fun startLoop() {
        if (running) return
        running = true
        lastNanos = 0L
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
        sensorManager.unregisterListener(this)
    }

    // ---------- 設定 ----------

    private fun loadPrefs() {
        count = prefs.getInt(Prefs.KEY_COUNT, Prefs.DEF_COUNT)
            .coerceIn(Prefs.MIN_COUNT, Prefs.MAX_COUNT)
        speed = prefs.getInt(Prefs.KEY_SPEED, Prefs.DEF_SPEED) / 100f
        opacity = prefs.getInt(Prefs.KEY_OPACITY, Prefs.DEF_OPACITY) / 100f
        powerSave = prefs.getBoolean(Prefs.KEY_POWER_SAVE, false)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        loadPrefs()
        syncJellies()
    }

    private fun syncJellies() {
        if (width == 0 || height == 0) return
        val target = if (powerSave) min(count, 4) else count
        while (jellies.size > target) jellies.removeAt(random.nextInt(jellies.size))
        val base = min(width, height) * 0.14f
        while (jellies.size < target) {
            jellies.add(Jelly(random, width.toFloat(), height.toFloat(), base))
        }
        jellies.sortBy { it.depth } // 遠的先畫
    }

    // ---------- 傾斜感應 ----------

    override fun onSensorChanged(event: SensorEvent) {
        // 手機右側朝下時 x 讀數為負，水母應往右漂
        val raw = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
        tiltX += (raw - tiltX) * 0.1f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
