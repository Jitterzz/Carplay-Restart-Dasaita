package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Aurora's Restart button: a glowing orb inside a gauge ring, with a comet orbiting it.
 *
 *  - Idle: the comet drifts around, lighting up the gauge ticks it passes, and the glow slowly breathes.
 *  - Working: the comet races, the icon spins and rings ripple outwards.
 *  - Done / error: the whole orb fades to green / amber for a moment.
 */
class OrbView(context: Context) : HeroView(context) {

    private companion object {
        const val IDLE_SPEED = 0.05f // degrees per millisecond: one lap in about 7 seconds
        const val WORK_SPEED = 0.45f // a lap in under a second
        const val TICKS = 60

        // Each palette: orb light, orb mid, orb deep, halo, comet, inner comet.
        val IDLE_COLORS = intArrayOf(
            0xFF7DD3FC.toInt(), 0xFF3B82F6.toInt(), 0xFF6D28D9.toInt(),
            0xFF3B82F6.toInt(), 0xFF67E8F9.toInt(), 0xFFA78BFA.toInt(),
        )
        val DONE_COLORS = intArrayOf(
            0xFFA7F3D0.toInt(), 0xFF10B981.toInt(), 0xFF047857.toInt(),
            0xFF10B981.toInt(), 0xFF6EE7B7.toInt(), 0xFF34D399.toInt(),
        )
        val ERROR_COLORS = intArrayOf(
            0xFFFDE68A.toInt(), 0xFFF59E0B.toInt(), 0xFFB45309.toInt(),
            0xFFF59E0B.toInt(), 0xFFFCD34D.toInt(), 0xFFFB923C.toInt(),
        )
    }

    // Colors: the current mix, fading from one palette to the next.
    private val colors = IDLE_COLORS.copyOf()
    private val fromColors = IDLE_COLORS.copyOf()
    private val toColors = IDLE_COLORS.copyOf()
    private var blend = 1f
    private var shadersDirty = true

    // Geometry, worked out when the size is known.
    private var r = 0f
    private var rCore = 0f
    private var rRing = 0f
    private var rInner = 0f
    private var headX = 0f
    private var headY = 0f
    private val ringRect = RectF()
    private val innerRect = RectF()
    private val tickCos = FloatArray(TICKS)
    private val tickSin = FloatArray(TICKS)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * dp
        color = 0x17FFFFFF
    }
    private val innerTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * dp
        color = 0x0FFFFFFF
    }
    private val cometPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val comet2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val headGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }

    // Motion.
    private var spin = -90f
    private var spin2 = 0f
    private var spinSpeed = IDLE_SPEED
    private var breathe = 0.5f
    private val waves = FloatArray(6) { -1f } // each ripple's progress (0 to 1), or -1 when unused
    private var lastWaveAt = 0L

    override fun onModeChanged(old: Mode, new: Mode) {
        colors.copyInto(fromColors)
        val next = when (new) {
            Mode.DONE -> DONE_COLORS
            Mode.ERROR -> ERROR_COLORS
            else -> IDLE_COLORS
        }
        next.copyInto(toColors)
        blend = 0f
        if (new != Mode.IDLE) ripple()
    }

    /** One ring rippling out from the orb: on a tap, and every so often while working. */
    private fun ripple() {
        val slot = waves.indexOfFirst { it < 0f }
        if (slot >= 0) waves[slot] = 0f
        lastWaveAt = SystemClock.uptimeMillis()
    }

    override fun onPress() = ripple()

    override fun onGeometry() {
        r = size / 2f
        rCore = r * 0.64f
        rInner = r * 0.735f
        rRing = r * 0.825f
        ringRect.set(cx - rRing, cy - rRing, cx + rRing, cy + rRing)
        innerRect.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner)
        for (i in 0 until TICKS) {
            val a = Math.toRadians(i * 360.0 / TICKS)
            tickCos[i] = cos(a).toFloat()
            tickSin[i] = sin(a).toFloat()
        }
        // The comet's head sits at 120° on the turning canvas.
        val head = Math.toRadians(120.0)
        headX = cx + rRing * cos(head).toFloat()
        headY = cy + rRing * sin(head).toFloat()
        shadersDirty = true
    }

    private fun buildShaders() {
        if (r <= 0f) return
        val light = colors[0]
        val mid = colors[1]
        val deep = colors[2]
        val glow = colors[3]
        val comet = colors[4]
        val comet2 = colors[5]
        val clamp = Shader.TileMode.CLAMP
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(withAlpha(glow, 0.6f), withAlpha(glow, 0.6f), withAlpha(glow, 0.2f), withAlpha(glow, 0f)),
            floatArrayOf(0f, rCore / r, (rCore / r + 1f) / 2f, 1f), clamp,
        )
        corePaint.shader = LinearGradient(
            cx - rCore, cy - rCore, cx + rCore, cy + rCore,
            intArrayOf(light, mid, deep), floatArrayOf(0f, 0.5f, 1f), clamp,
        )
        // Darker toward the bottom edge, for depth.
        shadePaint.shader = RadialGradient(
            cx, cy - rCore * 0.25f, rCore * 1.25f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.6f, 1f), clamp,
        )
        // A soft glassy highlight, top left.
        sheenPaint.shader = RadialGradient(
            cx - rCore * 0.32f, cy - rCore * 0.55f, rCore * 0.85f,
            intArrayOf(0x73FFFFFF, 0x00FFFFFF), null, clamp,
        )
        rimPaint.shader = LinearGradient(
            cx, cy - rCore, cx, cy + rCore,
            intArrayOf(0xB3FFFFFF.toInt(), 0x14FFFFFF, 0x40FFFFFF), floatArrayOf(0f, 0.55f, 1f), clamp,
        )
        // The comet: a tail fading in clockwise to a white-hot head at 120°.
        cometPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(withAlpha(comet, 0f), withAlpha(comet, 0.18f), comet, Color.WHITE, withAlpha(comet, 0f)),
            floatArrayOf(0f, 0.12f, 0.31f, 0.333f, 0.336f),
        )
        // The inner comet turns the other way, so its bright end is at 0°.
        comet2Paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(withAlpha(comet2, 0.85f), withAlpha(comet2, 0f), withAlpha(comet2, 0f)),
            floatArrayOf(0f, 0.2f, 1f),
        )
        headGlowPaint.shader = RadialGradient(
            headX, headY, 16 * dp,
            intArrayOf(withAlpha(comet, 0.9f), withAlpha(comet, 0f)), null, clamp,
        )
        shadersDirty = false
    }

    override fun step(now: Long, dt: Long) {
        val target = if (mode == Mode.WORKING) WORK_SPEED else IDLE_SPEED
        spinSpeed += (target - spinSpeed) * min(1f, dt / 320f)
        spin = (spin + spinSpeed * dt) % 360f
        spin2 = (spin2 - spinSpeed * 0.55f * dt) % 360f
        breathe = 0.5f + 0.5f * sin(now / 2800.0 * 2 * PI).toFloat()

        if (mode == Mode.WORKING && now - lastWaveAt > 700) ripple()
        for (i in waves.indices) {
            if (waves[i] >= 0f) {
                waves[i] += dt / 1500f
                if (waves[i] >= 1f) waves[i] = -1f
            }
        }

        if (blend < 1f) {
            blend = min(1f, blend + dt / 450f)
            for (i in colors.indices) colors[i] = lerpColor(fromColors[i], toColors[i], blend)
            shadersDirty = true
        }
    }

    override fun drawHero(canvas: Canvas, now: Long) {
        if (r <= 0f) return
        if (shadersDirty) buildShaders()
        val working = mode == Mode.WORKING

        // Halo.
        glowPaint.alpha = ((0.55f + 0.45f * breathe) * (if (working) 1f else 0.85f) * 255).toInt()
        canvas.drawCircle(cx, cy, r, glowPaint)

        // Ripples.
        for (p in waves) {
            if (p < 0f) continue
            wavePaint.color = withAlpha(colors[4], (1f - p).pow(1.6f) * 0.7f)
            canvas.drawCircle(cx, cy, rCore + (r * 0.99f - rCore) * p, wavePaint)
        }

        // Gauge ticks: the ones near the comet's head light up in its color.
        val head = spin + 120f
        for (i in 0 until TICKS) {
            val major = i % 5 == 0
            val diff = abs(((i * 360f / TICKS - head) % 360f + 540f) % 360f - 180f)
            val boost = (1f - diff / 38f).coerceAtLeast(0f).let { it * it }
            val base = if (major) 0.26f else 0.11f
            tickPaint.strokeWidth = (if (major) 2f else 1.4f) * dp
            tickPaint.color = withAlpha(lerpColor(Color.WHITE, colors[4], boost), base + boost * 0.74f)
            val r1 = r * (if (major) 0.885f else 0.9f)
            val r2 = r * (if (major) 0.955f else 0.94f)
            canvas.drawLine(
                cx + tickCos[i] * r1, cy + tickSin[i] * r1,
                cx + tickCos[i] * r2, cy + tickSin[i] * r2,
                tickPaint,
            )
        }

        // Tracks and comets.
        canvas.drawCircle(cx, cy, rRing, trackPaint)
        canvas.drawCircle(cx, cy, rInner, innerTrackPaint)

        canvas.save()
        canvas.rotate(spin2, cx, cy)
        canvas.drawArc(innerRect, 0f, 72f, false, comet2Paint)
        canvas.restore()

        canvas.save()
        canvas.rotate(spin, cx, cy)
        canvas.drawArc(ringRect, 0f, 120f, false, cometPaint)
        canvas.drawCircle(headX, headY, 16 * dp, headGlowPaint)
        canvas.drawCircle(headX, headY, 3.2f * dp, headDotPaint)
        canvas.restore()

        // The orb.
        canvas.drawCircle(cx, cy, rCore, corePaint)
        canvas.drawCircle(cx, cy, rCore, shadePaint)
        canvas.drawCircle(cx, cy, rCore, sheenPaint)
        canvas.drawCircle(cx, cy, rCore - rimPaint.strokeWidth / 2f, rimPaint)
    }
}
