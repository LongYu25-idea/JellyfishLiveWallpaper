package com.jellyfish.ocean

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 月亮水母（Aurelia）風格：扁圓半透明的傘、發亮的邊緣、放射管、
 * 中央四個馬蹄形生殖腺、短而柔軟的口腕與邊緣細毛。
 * 約 18% 的水母是「從上方看」的圓盤造型，看得到完整的四葉草圖案。
 *
 * 形狀都用單位座標描述（傘半寬 = 1），繪製時再用 size 縮放。
 */
class Jelly(rnd: Random, width: Float, height: Float, base: Float) {

    var x = rnd.nextFloat() * width
    var y = rnd.nextFloat() * height
    var vx = 0f
    var vy = 0f

    /** 0.55 ~ 1.0，越大代表越靠近鏡頭（較大、較亮） */
    val depth = 0.55f + rnd.nextFloat() * 0.45f
    val size = base * (0.45f + depth * 0.75f) * (0.8f + rnd.nextFloat() * 0.4f)

    private val diskView = rnd.nextFloat() < 0.18f
    private var rot = rnd.nextFloat() * 360f
    private val rotSpeed = (rnd.nextFloat() - 0.5f) * 10f

    private var phase = rnd.nextFloat()
    private val pulseRate = 0.30f + rnd.nextFloat() * 0.15f
    private val wanderSeed = rnd.nextFloat() * 100f

    private val armPhase = FloatArray(ARMS) { rnd.nextFloat() * 6.28f }
    private val armLen = FloatArray(ARMS) { 0.8f + rnd.nextFloat() * 0.9f }
    private val finePhase = FloatArray(FINE) { rnd.nextFloat() * 6.28f }
    private val fineLen = FloatArray(FINE) { 0.3f + rnd.nextFloat() * 0.35f }

    // ---- 顏色：偏藍紫的螢光藍 ----
    private val baseColor = blend(BLUE, VIOLET, rnd.nextFloat() * 0.7f)
    private val lightColor = blend(baseColor, Color.WHITE, 0.55f)

    // ---- 畫筆（每隻建立一次）----
    private val bellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, -0.26f, 1f,
            intArrayOf(
                withAlpha(lightColor, 120),
                withAlpha(baseColor, 95),
                withAlpha(baseColor, 85),
                withAlpha(lightColor, 150)
            ),
            floatArrayOf(0f, 0.5f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        ).also { s ->
            // 讓圓形漸層變成貼合扁傘的橢圓
            s.setLocalMatrix(Matrix().apply { setScale(1f, 0.37f, 0f, -0.26f) })
        }
    }
    private val diskFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(
                withAlpha(lightColor, 85),
                withAlpha(baseColor, 65),
                withAlpha(baseColor, 70),
                withAlpha(lightColor, 150)
            ),
            floatArrayOf(0f, 0.55f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, -0.2f, 1.9f,
            intArrayOf(withAlpha(baseColor, 70), withAlpha(baseColor, 0)),
            null,
            Shader.TileMode.CLAMP
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val rimPaint = strokePaint(0.022f, lightColor)
    private val canalPaint = strokePaint(0.008f, lightColor)
    private val hairPaint = strokePaint(0.012f, lightColor)
    private val domeArcPaint = strokePaint(0.012f, Color.WHITE)
    private val organStroke = strokePaint(0.032f, blend(baseColor, Color.WHITE, 0.8f))
    private val organFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = blend(baseColor, Color.WHITE, 0.7f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private val armPaint = strokePaint(0.085f, lightColor).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1.7f,
            withAlpha(lightColor, 120), withAlpha(baseColor, 0),
            Shader.TileMode.CLAMP
        )
    }
    private val armCorePaint = strokePaint(0.02f, Color.WHITE).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 1.7f,
            withAlpha(Color.WHITE, 150), withAlpha(lightColor, 0),
            Shader.TileMode.CLAMP
        )
    }
    private val finePaint = strokePaint(0.014f, lightColor).apply {
        shader = LinearGradient(
            0f, 0f, 0f, 0.7f,
            withAlpha(lightColor, 140), withAlpha(baseColor, 0),
            Shader.TileMode.CLAMP
        )
    }

    private val strandPath = Path()

    // ------------------------------------------------------------------
    // 物理
    // ------------------------------------------------------------------

    fun update(dt: Float, speed: Float, tiltX: Float, w: Float, h: Float, time: Float) {
        phase += dt * pulseRate * speed
        if (phase > 1f) phase -= 1f
        rot += rotSpeed * dt * speed

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

    // ------------------------------------------------------------------
    // 繪製
    // ------------------------------------------------------------------

    fun draw(canvas: Canvas, time: Float, brightness: Float, parallax: Float) {
        val a = brightness * (0.55f + 0.45f * depth)
        canvas.save()
        canvas.translate(x + parallax * depth, y)
        if (diskView) drawDisk(canvas, a) else drawSide(canvas, time, a)
        canvas.restore()
    }

    /** 從側面看：扁圓傘 + 垂下的口腕 */
    private fun drawSide(canvas: Canvas, time: Float, a: Float) {
        val c = cos(phase * TWO_PI)
        val sx = size * (1f + 0.10f * c)
        val sy = size * (1f - 0.14f * c)

        // 光暈與垂下的部分（以 size 為單位）
        canvas.save()
        canvas.scale(size, size)
        glowPaint.alpha = alpha255(a * 0.85f)
        canvas.drawCircle(0f, -0.2f, 1.9f, glowPaint)

        val drift = (vx / size).coerceIn(-1.5f, 1.5f) * 0.1f
        val widthRatio = sx / size
        val top = 0.05f

        armPaint.alpha = alpha255(a)
        armCorePaint.alpha = alpha255(a)
        for (i in 0 until ARMS) {
            val bx = (-0.3f + 0.2f * i) * widthRatio
            buildStrand(bx, top, armLen[i], 0.10f, 2.6f, 1.2f, armPhase[i], time, drift)
            canvas.drawPath(strandPath, armPaint)
            canvas.drawPath(strandPath, armCorePaint)
        }

        finePaint.alpha = alpha255(a * 0.8f)
        for (i in 0 until FINE) {
            val bx = (-0.85f + 1.7f * i / (FINE - 1)) * widthRatio
            val by = 0.03f + 0.065f * (1f - (bx / widthRatio) * (bx / widthRatio))
            buildStrand(bx, by, fineLen[i], 0.05f, 3.4f, 1.8f, finePhase[i], time, drift)
            canvas.drawPath(strandPath, finePaint)
        }
        canvas.restore()

        // 傘體
        canvas.scale(sx, sy)
        bellFill.alpha = alpha255(a)
        canvas.drawPath(BELL, bellFill)

        canalPaint.alpha = alpha255(a * 0.22f)
        canvas.drawPath(SIDE_CANALS, canalPaint)

        domeArcPaint.alpha = alpha255(a * 0.28f)
        canvas.drawArc(DOME_ARC, 200f, 140f, false, domeArcPaint)

        // 四個馬蹄形生殖腺
        organFill.alpha = alpha255(a * 0.16f)
        organStroke.alpha = alpha255(a * 0.6f)
        for (k in 0 until 4) {
            canvas.drawOval(SIDE_ORGANS[k], organFill)
            canvas.drawArc(SIDE_ORGANS[k], 40f + 90f * k, 290f, false, organStroke)
        }

        rimPaint.alpha = alpha255(a * 0.7f)
        canvas.drawPath(BELL, rimPaint)

        hairPaint.alpha = alpha255(a * 0.45f)
        canvas.drawPath(SIDE_HAIRS, hairPaint)

        highlightPaint.alpha = alpha255(a * 0.14f)
        canvas.drawOval(HIGHLIGHT, highlightPaint)
    }

    /** 從上方看：圓盤 + 完整的四葉草生殖腺 */
    private fun drawDisk(canvas: Canvas, a: Float) {
        val c = cos(phase * TWO_PI)
        val s = size * 0.95f * (1f + 0.05f * c)

        canvas.rotate(rot)
        canvas.scale(s, s * 0.92f)

        glowPaint.alpha = alpha255(a * 0.8f)
        canvas.drawCircle(0f, 0f, 1.8f, glowPaint)

        diskFill.alpha = alpha255(a)
        canvas.drawCircle(0f, 0f, 1f, diskFill)

        canalPaint.alpha = alpha255(a * 0.3f)
        canvas.drawPath(DISK_CANALS, canalPaint)

        organFill.alpha = alpha255(a * 0.18f)
        organStroke.alpha = alpha255(a * 0.65f)
        for (k in 0 until 4) {
            val ang = (45f + 90f * k) * PI.toFloat() / 180f
            val cx = cos(ang) * 0.2f
            val cy = sin(ang) * 0.2f
            DISK_RECT.set(cx - 0.16f, cy - 0.16f, cx + 0.16f, cy + 0.16f)
            canvas.drawOval(DISK_RECT, organFill)
            canvas.drawArc(DISK_RECT, 45f + 90f * k + 150f, 280f, false, organStroke)
        }
        // 中央口部
        DISK_RECT.set(-0.07f, -0.07f, 0.07f, 0.07f)
        canvas.drawOval(DISK_RECT, organFill)

        rimPaint.alpha = alpha255(a * 0.75f)
        canvas.drawCircle(0f, 0f, 1f, rimPaint)

        hairPaint.alpha = alpha255(a * 0.4f)
        canvas.drawPath(DISK_HAIRS, hairPaint)
    }

    private fun buildStrand(
        bx: Float, by: Float, length: Float, amp: Float, waves: Float,
        speed: Float, off: Float, time: Float, drift: Float
    ) {
        val segments = 10
        strandPath.reset()
        strandPath.moveTo(bx, by)
        for (k in 1..segments) {
            val t = k / segments.toFloat()
            val px = bx + sin(time * speed + off + t * waves) * amp * t - drift * t * 1.5f
            strandPath.lineTo(px, by + t * length)
        }
    }

    companion object {
        private const val ARMS = 4
        private const val FINE = 10
        private const val TWO_PI = (2.0 * PI).toFloat()

        private val BLUE = Color.rgb(85, 105, 255)
        private val VIOLET = Color.rgb(135, 100, 255)

        private fun alpha255(v: Float): Int = (255f * v).toInt().coerceIn(0, 255)

        private fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

        private fun blend(a: Int, b: Int, t: Float): Int {
            val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
            val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
            val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
            return Color.rgb(r, g, bl)
        }

        private fun strokePaint(width: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }

        // ---- 共用的單位形狀（所有水母相同）----

        /** 扁圓的傘 */
        private val BELL = Path().apply {
            moveTo(-1f, 0.02f)
            cubicTo(-1.0f, -0.45f, -0.55f, -0.62f, 0f, -0.62f)
            cubicTo(0.55f, -0.62f, 1.0f, -0.45f, 1.0f, 0.02f)
            quadTo(0.5f, 0.1f, 0f, 0.085f)
            quadTo(-0.5f, 0.1f, -1f, 0.02f)
            close()
        }

        private val DOME_ARC = RectF(-0.8f, -0.56f, 0.8f, 0.0f)
        private val HIGHLIGHT = RectF(-0.62f, -0.57f, -0.1f, -0.4f)
        private val DISK_RECT = RectF()

        /** 四個生殖腺：壓扁的橢圓，排成四葉草 */
        private val SIDE_ORGANS = arrayOf(
            RectF(0.06f, -0.25f, 0.30f, -0.14f),
            RectF(-0.30f, -0.25f, -0.06f, -0.14f),
            RectF(-0.30f, -0.37f, -0.06f, -0.26f),
            RectF(0.06f, -0.37f, 0.30f, -0.26f)
        )

        /** 側視的放射管：從中心向邊緣的細線 */
        private val SIDE_CANALS = Path().apply {
            val n = 22
            for (i in 0 until n) {
                val ang = i * TWO_PI / n
                val ca = cos(ang)
                val sa = sin(ang)
                moveTo(ca * 0.32f, -0.26f + sa * 0.37f * 0.32f)
                lineTo(ca * 0.94f, -0.26f + sa * 0.37f * 0.94f)
            }
        }

        /** 傘緣下方的細毛 */
        private val SIDE_HAIRS = Path().apply {
            val n = 36
            for (i in 0 until n) {
                val t = -0.97f + 1.94f * i / (n - 1)
                val edge = 0.02f + 0.065f * (1f - t * t)
                val len = 0.07f + ((i * 37) % 5) * 0.025f
                moveTo(t, edge)
                lineTo(t * 1.01f, edge + len)
            }
        }

        private val DISK_CANALS = Path().apply {
            val n = 28
            for (i in 0 until n) {
                val ang = i * TWO_PI / n
                moveTo(cos(ang) * 0.42f, sin(ang) * 0.42f)
                lineTo(cos(ang) * 0.95f, sin(ang) * 0.95f)
            }
        }

        private val DISK_HAIRS = Path().apply {
            val n = 48
            for (i in 0 until n) {
                val ang = i * TWO_PI / n
                val len = 0.06f + ((i * 37) % 5) * 0.02f
                moveTo(cos(ang) * 1.0f, sin(ang) * 1.0f)
                lineTo(cos(ang) * (1.0f + len), sin(ang) * (1.0f + len))
            }
        }
    }
}
