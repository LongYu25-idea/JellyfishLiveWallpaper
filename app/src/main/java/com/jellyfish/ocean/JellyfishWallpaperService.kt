package com.jellyfish.ocean

import android.app.WallpaperManager
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class JellyfishWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = JellyfishEngine()

    private class Ripple(val x: Float, val y: Float, val born: Long)

    private inner class JellyfishEngine : Engine(), SensorEventListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = Prefs.get(this@JellyfishWallpaperService)
        private val density = resources.displayMetrics.density
        private val random = Random(System.nanoTime())

        private val ocean = Ocean(density)
        private val jellies = ArrayList<Jelly>()
        private val ripples = ArrayList<Ripple>()
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = 0xFFBFD8FF.toInt()
        }

        private var width = 0
        private var height = 0
        private var visible = false
        private var lastFrame = 0L
        private var lastTouch = 0L
        private var time = 0f
        private var xOffset = 0.5f

        // 設定值
        private var count = Prefs.DEF_COUNT
        private var speed = 1f
        private var brightness = 1f
        private var powerSave = false

        // 傾斜
        private val sensorManager =
            getSystemService(SENSOR_SERVICE) as SensorManager
        private val accelerometer: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private var tiltX = 0f

        private val frame = object : Runnable {
            override fun run() {
                if (!visible) return
                val start = SystemClock.uptimeMillis()
                val dt = ((start - lastFrame) / 1000f).coerceIn(0.001f, 0.05f)
                lastFrame = start
                time += dt

                step(dt)
                render(start)

                val interval = if (powerSave) 33L else 16L
                val elapsed = SystemClock.uptimeMillis() - start
                handler.postDelayed(this, max(1L, interval - elapsed))
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            loadPrefs()
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            sensorManager.unregisterListener(this)
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            ocean.resize(w, h)
            jellies.clear()
            syncJellies()
            if (visible) render(SystemClock.uptimeMillis())
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (isVisible) {
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                lastFrame = SystemClock.uptimeMillis()
                handler.post(frame)
            } else {
                sensorManager.unregisterListener(this)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xStep: Float, yStep: Float,
            xPixels: Int, yPixels: Int
        ) {
            this.xOffset = xOffset
        }

        // ---------- 設定 ----------

        private fun loadPrefs() {
            count = prefs.getInt(Prefs.KEY_COUNT, Prefs.DEF_COUNT)
                .coerceIn(Prefs.MIN_COUNT, Prefs.MAX_COUNT)
            speed = prefs.getInt(Prefs.KEY_SPEED, Prefs.DEF_SPEED) / 100f
            brightness = prefs.getInt(Prefs.KEY_BRIGHTNESS, Prefs.DEF_BRIGHTNESS) / 100f
            powerSave = prefs.getBoolean(Prefs.KEY_POWER_SAVE, false)
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            loadPrefs()
            syncJellies()
        }

        private fun targetCount(): Int = if (powerSave) min(count, 6) else count

        private fun syncJellies() {
            if (width == 0 || height == 0) return
            val target = targetCount()
            while (jellies.size > target) jellies.removeAt(random.nextInt(jellies.size))
            val base = min(width, height) * 0.16f
            while (jellies.size < target) {
                jellies.add(Jelly(random, width.toFloat(), height.toFloat(), base))
            }
            jellies.sortBy { it.depth } // 遠的先畫
        }

        // ---------- 模擬與繪製 ----------

        private fun step(dt: Float) {
            ocean.update(dt, speed)
            for (j in jellies) j.update(dt, speed, tiltX, width.toFloat(), height.toFloat(), time)
        }

        private fun render(now: Long) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        holder.surface.lockHardwareCanvas()
                    } catch (e: Exception) {
                        holder.lockCanvas()
                    }
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) draw(canvas, now)
            } catch (_: Exception) {
                // Surface 可能剛好被系統回收，略過這一幀
            } finally {
                if (canvas != null) {
                    try {
                        holder.surface.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun draw(canvas: Canvas, now: Long) {
            val particles = if (powerSave) 30 else Ocean.MAX_PARTICLES
            ocean.draw(canvas, time, brightness, particles)

            val parallax = -(xOffset - 0.5f) * width * 0.12f
            for (j in jellies) j.draw(canvas, time, brightness, parallax)

            // 觸控漣漪
            val iter = ripples.iterator()
            val life = 1200L
            while (iter.hasNext()) {
                val r = iter.next()
                val age = now - r.born
                if (age > life) {
                    iter.remove()
                    continue
                }
                val p = age / life.toFloat()
                val maxR = min(width, height) * 0.28f
                val fade = (1f - p) * brightness
                ripplePaint.alpha = (150f * fade).toInt().coerceIn(0, 255)
                canvas.drawCircle(r.x, r.y, maxR * p, ripplePaint)
                ripplePaint.alpha = (90f * fade).toInt().coerceIn(0, 255)
                canvas.drawCircle(r.x, r.y, maxR * p * 0.62f, ripplePaint)
            }
        }

        // ---------- 觸控 ----------

        override fun onTouchEvent(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touch(event.x, event.y)
                MotionEvent.ACTION_MOVE -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastTouch > 90) touch(event.x, event.y)
                }
            }
        }

        override fun onCommand(
            action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean
        ): Bundle? {
            // 多數桌面程式會用 COMMAND_TAP 把點擊轉給動態桌布
            if (action == WallpaperManager.COMMAND_TAP) touch(x.toFloat(), y.toFloat())
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun touch(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            lastTouch = now
            if (ripples.size >= 8) ripples.removeAt(0)
            ripples.add(Ripple(x, y, now))
            val radius = min(width, height) * 0.45f
            for (j in jellies) j.push(x, y, radius, j.size * 5f)
        }

        // ---------- 傾斜感應 ----------

        override fun onSensorChanged(event: SensorEvent) {
            // 手機右側朝下時 x 讀數為負，水母應往右漂
            val raw = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
            tiltX += (raw - tiltX) * 0.1f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
