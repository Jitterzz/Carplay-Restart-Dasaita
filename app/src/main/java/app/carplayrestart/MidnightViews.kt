package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Midnight's backdrop: near-black with a warm glow that slowly breathes behind the Restart ring. */
class MidnightBackdrop(context: Context) : BackdropView(context) {

    override val frameMs = 50L

    private var k = 1f
    private val base = Paint().apply { color = 0xFF0A0A0C.toInt() }
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onLayoutChanged() {
        if (width == 0 || height == 0) return
        k = fs() / 240f
        val clamp = Shader.TileMode.CLAMP
        haze.shader = RadialGradient(
            fx(), fy(), width * 0.42f, intArrayOf(0x12E7C48A, 0x00E7C48A), null, clamp,
        )
        glow.shader = RadialGradient(
            fx(), fy(), 160f * k, intArrayOf(0x29E7C48A, 0x00E7C48A), floatArrayOf(0f, 0.68f), clamp,
        )
    }

    override fun drawBackdrop(canvas: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        if (quiet) return
        canvas.drawRect(0f, 0f, w, h, haze)
        val b = 0.5f - 0.5f * cos(now % 5000L / 5000.0 * 2 * PI).toFloat()
        val scale = 0.9f + 0.16f * b
        canvas.save()
        canvas.scale(scale, scale, fx(), fy())
        glow.alpha = ((0.55f + 0.45f * b) * 255).toInt()
        canvas.drawCircle(fx(), fy(), 160f * k, glow)
        canvas.restore()
    }
}

/**
 * Midnight's Restart button: a calm ring with a small light circling it. While it restarts, a gold arc draws
 * itself round the ring; done, the ring is complete.
 */
class MidnightHero(context: Context) : HeroView(context) {

    private companion object {
        const val GOLD = 0xFFE7C48A.toInt()
        const val CLAY = 0xFFE8A87C.toInt()
    }

    override val spinsIcon = false

    private var k = 1f
    private var sweep = 0f       // the gold arc, in degrees
    private var arcAlpha = 0f
    private var dotAlpha = 1f
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
    private val dotGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcRect = RectF()

    override fun onGeometry() {
        k = size / 240f
        val r = 115f * k
        val clamp = Shader.TileMode.CLAMP
        shadow.shader = RadialGradient(
            cx, cy + 24f * k, r * 1.3f,
            intArrayOf(0x8C000000.toInt(), 0x8C000000.toInt(), 0x00000000), floatArrayOf(0f, 0.6f, 1f), clamp,
        )
        disc.shader = RadialGradient(
            cx, cy - 0.24f * r, r * 1.15f,
            intArrayOf(0xFF18181B.toInt(), 0xFF0E0E10.toInt()), floatArrayOf(0f, 0.72f), clamp,
        )
        dotGlow.shader = RadialGradient(0f, 0f, 10 * dp, intArrayOf(0xCCE7C48A.toInt(), 0x00E7C48A), null, clamp)
        arcRect.set(cx - r, cy - r, cx + r, cy + r)
    }

    override fun onModeChanged(old: Mode, new: Mode) {
        if (new == Mode.WORKING) sweep = 0f
    }

    override fun step(now: Long, dt: Long) {
        val t = (now - modeAt).toFloat()
        when (mode) {
            Mode.WORKING -> {
                sweep = 360f * (1f - exp(-t / 2600f))
                arcAlpha = min(1f, arcAlpha + dt / 200f)
            }
            Mode.DONE -> {
                sweep = min(360f, sweep + dt * 1.2f)
                arcAlpha = min(1f, arcAlpha + dt / 200f)
            }
            Mode.ERROR -> arcAlpha = max(0f, arcAlpha - dt / 1500f)
            Mode.IDLE -> arcAlpha = max(0f, arcAlpha - dt / 600f)
        }
        val wantDot = if (mode == Mode.IDLE) 1f else 0f
        dotAlpha += (wantDot - dotAlpha) * min(1f, dt / 300f)
    }

    override fun drawHero(canvas: Canvas, now: Long) {
        val r = 115f * k
        canvas.drawCircle(cx, cy, r * 1.3f, shadow)
        canvas.drawCircle(cx, cy, r - 0.5f * dp, disc)
        line.strokeWidth = 1f * dp
        line.color = 0x12F4F1EA
        canvas.drawCircle(cx, cy, r - 14f * k, line)
        line.color = 0x38F4F1EA
        canvas.drawCircle(cx, cy, r, line)

        if (arcAlpha > 0f && sweep > 0f) {
            val c = if (mode == Mode.ERROR) CLAY else GOLD
            arcPaint.strokeWidth = 6f * dp
            arcPaint.color = withAlpha(c, 0.25f * arcAlpha)
            canvas.drawArc(arcRect, -90f, sweep, false, arcPaint)
            arcPaint.strokeWidth = 2f * dp
            arcPaint.color = withAlpha(c, arcAlpha)
            canvas.drawArc(arcRect, -90f, sweep, false, arcPaint)
        }

        if (dotAlpha > 0.01f) {
            val a = Math.toRadians(now % 12_000L / 12_000.0 * 360.0 - 90.0)
            canvas.save()
            canvas.translate(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
            dotGlow.alpha = (dotAlpha * 255).toInt()
            canvas.drawCircle(0f, 0f, 10 * dp, dotGlow)
            dot.alpha = (dotAlpha * 255).toInt()
            canvas.drawCircle(0f, 0f, 3 * dp, dot)
            canvas.restore()
        }
    }
}
