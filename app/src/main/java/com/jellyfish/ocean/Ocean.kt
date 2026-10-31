package com.jellyfish.ocean

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/** 海洋背景：漸層、光束、浮游微粒 */
class Ocean(private val density: Float) {

    private var w = 0f
    private var h = 0f
    private val rnd = Random(7)

    private val bgPaint = Paint()
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 215, 255) }

    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var pr = FloatArray(0)
    private var ps = FloatArray(0)
    private var pp = FloatArray(0)

    private val rayX = floatArrayOf(0.15f, 0.40f, 0.65f, 0.90f)
    private val rayW = floatArrayOf(0.10f, 0.16f, 0.08f, 0.13f)

    fun resize(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.rgb(14, 36, 108), Color.rgb(28, 20, 104), Color.rgb(3, 5, 26)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        rayPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.9f,
            Color.argb(46, 150, 200, 255), Color.argb(0, 150, 200, 255),
            Shader.TileMode.CLAMP
        )
        allocParticles(MAX_PARTICLES)
    }

    private fun allocParticles(n: Int) {
        px = FloatArray(n) { rnd.nextFloat() * w }
        py = FloatArray(n) { rnd.nextFloat() * h }
        pr = FloatArray(n) { (0.8f + rnd.nextFloat() * 1.8f) * density }
        ps = FloatArray(n) { (6f + rnd.nextFloat() * 14f) * density }
        pp = FloatArray(n) { rnd.nextFloat() * 6.28f }
    }

    fun update(dt: Float, speed: Float) {
        for (i in py.indices) {
            py[i] -= ps[i] * dt * speed
            if (py[i] < -10f) {
                py[i] = h + 10f
                px[i] = rnd.nextFloat() * w
            }
        }
    }

    fun draw(canvas: Canvas, time: Float, brightness: Float, particleCount: Int) {
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 緩慢擺動的光束
        rayPaint.alpha = (255f * brightness).toInt().coerceIn(0, 255)
        for (i in rayX.indices) {
            val sway = sin(time * 0.15f + i * 1.7f) * 4f
            canvas.save()
            canvas.translate(rayX[i] * w, -h * 0.05f)
            canvas.rotate(14f + sway)
            val half = rayW[i] * w * 0.5f
            canvas.drawRect(-half, 0f, half, h * 1.2f, rayPaint)
            canvas.restore()
        }

        // 浮游微粒
        val n = minOf(particleCount, px.size)
        for (i in 0 until n) {
            val twinkle = 0.5f + 0.5f * sin(time * 1.3f + pp[i])
            dotPaint.alpha = (150f * twinkle * brightness).toInt().coerceIn(0, 255)
            canvas.drawCircle(px[i] + sin(time * 0.4f + pp[i]) * 6f * density, py[i], pr[i], dotPaint)
        }
    }

    companion object {
        const val MAX_PARTICLES = 80
    }
}
