package com.jellyfish.ocean

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 一隻半透明發光水母。
 * 所有形狀都用「單位座標」描述（傘寬約 ±1），繪製時再用 size 縮放。
 */
class Jelly(rnd: Random, width: Float, height: Float, base: Float) {

    var x = rnd.nextFloat() * width
    var y = rnd.nextFloat() * height
    var vx = 0f
    var vy = 0f

    /** 0.55 ~ 1.0，越大代表越靠近鏡頭（較大、較亮） */
    val depth = 0.55f + rnd.nextFloat() * 0.45f
    val size = base * (0.45f + depth * 0.75f) * (0.8f + rnd.nextFloat() * 0.4f)

    private var phase = rnd.nextFloat()
    private val pulseRate = 0.35f + rnd.nextFloat() * 0.2f
    private val wanderSeed = rnd.nextFloat() * 100f

    private val tentacleCount = 6 + rnd.nextInt(4)
    private val tentaclePhase = FloatArray(tentacleCount) { rnd.nextFloat() * 6.28f }
    private val tentacleLen = FloatArray(tentacleCount) { 1.7f + rnd.nextFloat() * 1.1f }

    private val armPhase = FloatArray(3) { rnd.nextFloat() * 6.28f }

    // ---- 顏色：藍 → 紫 → 粉紫 ----
    private val tint = rnd.nextFloat()
    private val baseColor = mixColor(tint)
    private val lightColor = blend(baseColor, Color.WHITE, 0.55f)

    // ---- 形狀與畫筆（建立一次，重複使用）----
    private val bellPath = Path().apply {
        moveTo(-1f, 0f)
        cubicTo(-1.05f, -1.25f, 1.05f, -1.25f, 1f, 0f)
        quadTo(0.55f, 0.22f, 0f, 0.1f)
        quadTo(-0.55f, 0.22f, -1f, 0f)
        close()
    }
    private val strandPath = Path()
    private val organRect = RectF(-0.4f, -0.7f, 0.4f, -0.05f)
    private val highlightRect = RectF(-0.6f, -0.88f, -0.12f, -0.55f)

    private val bellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0.1f, 1.1f,
            intArrayOf(
                withAlpha(baseColor, 28),
                withAlpha(baseColor, 60),
                withAlpha(lightColor, 150),
                withAlpha(baseColor, 70)
            ),
            floatArrayOf(0f, 0.55f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.035f
        strokeJoin = Paint.Join.ROUND
        color = lightColor
    }
    private val organPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.04f
        strokeCap = Paint.Cap.ROUND
        color = blend(baseColor, Color.WHITE, 0.8f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, -0.3f, 2.2f,
            intArrayOf(withAlpha(baseColor, 80), withAlpha(baseColor, 0)),
            null,
            Shader.TileMode.CLAMP
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val tentaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.028f
        shader = LinearGradient(
            0f, 0f, 0f, 3f,
            withAlpha(lightColor, 170), withAlpha(baseColor, 0),
            Shader.TileMode.CLAMP
        )
    }
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.075f
        shader = LinearGradient(
            0f, 0f, 0f, 1.6f,
            withAlpha(lightColor, 110), withAlpha(baseColor, 0),
            Shader.TileMode.CLAMP
        )
    }

    fun update(dt: Float, speed: Float, tiltX: Float, w: Float, h: Float, time: Float) {
        phase += dt * pulseRate * speed
        if (phase > 1f) phase -= 1f

        // 傘收縮（sin > 0）時向上推進，放鬆時緩緩下沉
        val thrust = max(0f, sin(phase * TWO_PI))
        vy += (-thrust * 2.0f + 0.9f) * size * speed * dt

        // 隨機漂移 + 手機傾斜
        val wander = sin(time * 0.37f + wanderSeed) + 0.6f * sin(time * 0.91f + wanderSeed * 2f)
        vx += (wander * 0.35f + tiltX * 2.4f) * size * speed * dt

        val drag = min(1f, 1.4f * dt)
        vx -= vx * drag
        vy -= vy * drag

        x += vx * dt
        y += vy * dt

        val m = size * 4f
        if (y < -m) y = h + size * 2f
        if (y > h + m) y = -size * 3f
        if (x < -size * 2f) x = w + size * 2f
        if (x > w + size * 2f) x = -size * 2f
    }

    /** 被觸碰驚動：推開並立刻收縮一次 */
    fun push(px: Float, py: Float, radius: Float, strength: Float) {
        val dx = x - px
        val dy = y - py
        val d = max(1f, hypot(dx, dy))
        if (d < radius) {
            val f = 1f - d / radius
            vx += dx / d * f * strength * depth
            vy += dy / d * f * strength * depth
            phase = 0.02f
        }
    }

    fun draw(canvas: Canvas, time: Float, brightness: Float, parallax: Float) {
        val c = cos(phase * TWO_PI)
        val sx = size * (1f + 0.12f * c)
        val sy = size * (1f - 0.10f * c)
        val a = brightness * (0.55f + 0.45f * depth)

        canvas.save()
        canvas.translate(x + parallax * depth, y)

        // 外圈光暈
        glowPaint.alpha = alpha255(a * 0.9f)
        canvas.save()
        canvas.scale(size, size)
        canvas.drawCircle(0f, -0.3f, 2.2f, glowPaint)

        // 觸手與口腕（單位座標，隨速度向後拖曳）
        val drift = (vx / size).coerceIn(-1.5f, 1.5f) * 0.1f
        tentaclePaint.alpha = alpha255(a)
        armPaint.alpha = alpha255(a)
        val widthRatio = sx / size
        for (i in 0 until tentacleCount) {
            val bx = (-0.75f + 1.5f * i / (tentacleCount - 1)) * widthRatio
            drawStrand(canvas, bx, tentacleLen[i], 0.16f, 3.2f, 1.6f, tentaclePhase[i], time, drift, tentaclePaint)
        }
        for (i in 0 until 3) {
            val bx = (-0.25f + 0.25f * i) * widthRatio
            drawStrand(canvas, bx, 1.0f + 0.15f * i, 0.12f, 2.4f, 1.1f, armPhase[i], time, drift, armPaint)
        }
        canvas.restore()

        // 傘體
        canvas.scale(sx, sy)
        bellFill.alpha = alpha255(a)
        canvas.drawPath(bellPath, bellFill)

        rimPaint.alpha = alpha255(a * 0.55f)
        canvas.drawPath(bellPath, rimPaint)

        organPaint.alpha = alpha255(a * 0.5f)
        canvas.drawArc(organRect, 200f, 140f, false, organPaint)
        canvas.drawArc(organRect, 20f, 140f, false, organPaint)

        highlightPaint.alpha = alpha255(a * 0.18f)
        canvas.drawOval(highlightRect, highlightPaint)

        canvas.restore()
    }

    private fun drawStrand(
        canvas: Canvas,
        bx: Float,
        length: Float,
        amp: Float,
        waves: Float,
        speed: Float,
        off: Float,
        time: Float,
        drift: Float,
        paint: Paint
    ) {
        val segments = 10
        strandPath.reset()
        strandPath.moveTo(bx, 0.08f)
        for (k in 1..segments) {
            val t = k / segments.toFloat()
            val px = bx + sin(time * speed + off + t * waves) * amp * t - drift * t * 1.5f
            val py = 0.08f + t * length
            strandPath.lineTo(px, py)
        }
        canvas.drawPath(strandPath, paint)
    }

    companion object {
        private const val TWO_PI = (2.0 * PI).toFloat()

        private fun alpha255(v: Float): Int = (255f * v).toInt().coerceIn(0, 255)

        private fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

        private fun blend(a: Int, b: Int, t: Float): Int {
            val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
            val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
            val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
            return Color.rgb(r, g, bl)
        }

        private val BLUE = Color.rgb(90, 170, 255)
        private val VIOLET = Color.rgb(165, 115, 255)
        private val PINK = Color.rgb(235, 130, 235)

        private fun mixColor(t: Float): Int =
            if (t < 0.5f) blend(BLUE, VIOLET, t * 2f) else blend(VIOLET, PINK, abs(t - 0.5f) * 2f)
    }
}
