package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Neon Drive's backdrop: a synthwave sunset. Stars twinkle over a striped sun behind the Restart button,
 * mountains sit on a glowing horizon and a neon grid road scrolls toward you. While it restarts, the road
 * speeds up and turns cyan.
 */
class NeonBackdrop(context: Context) : BackdropView(context) {

    private companion object {
        const val MAGENTA = 0xFFFF2975.toInt()
        const val CYAN = 0xFF2DE2E6.toInt()
        // Stars: x, y (shares of the width and the sky's height), size (dp), period (ms).
        val STARS = floatArrayOf(
            0.07f, 0.31f, 2f, 3200f, 0.155f, 0.15f, 1.5f, 2600f, 0.30f, 0.10f, 1.5f, 3600f,
            0.436f, 0.36f, 2f, 2900f, 0.49f, 0.60f, 1.5f, 3400f, 0.55f, 0.23f, 2f, 2400f,
            0.657f, 0.13f, 1.5f, 3000f, 0.75f, 0.26f, 2f, 2700f, 0.825f, 0.16f, 1.5f, 3300f,
            0.042f, 0.67f, 1.5f, 2800f,
        )
        // Mountains, as drawn at 853 wide: x, height above the horizon.
        val LEFT_RANGE = floatArrayOf(0f, 0f, 0f, 30f, 40f, 48f, 80f, 26f, 120f, 56f, 165f, 30f, 200f, 40f,
            240f, 18f, 290f, 34f, 330f, 12f, 380f, 24f, 420f, 0f)
        val RIGHT_RANGE = floatArrayOf(430f, 0f, 470f, 18f, 520f, 40f, 560f, 24f, 610f, 50f, 660f, 28f,
            700f, 42f, 750f, 22f, 800f, 46f, 853f, 26f, 853f, 0f)
    }

    private var k = 1f
    private var horizon = 0f
    private var sunR = 0f
    private var gridPhase = 0f
    private var workMix = 0f
    private var lastNow = 0L

    private val sky = Paint()
    private val floor = Paint()
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mountainFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10032A.toInt() }
    private val mountainEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xB3FF4FA0.toInt()
    }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fade = Paint()
    private val horizonLine = Paint()
    private val horizonGlow = Paint()
    private val mountains = Path()

    override fun onLayoutChanged() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        k = fs() / 200f
        horizon = if (quiet) h * 0.68f else min(h - 24 * dp, fy() + 89f * k)
        sunR = 125f * k
        val clamp = Shader.TileMode.CLAMP
        sky.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(0xFF07001A.toInt(), 0xFF140431.toInt(), 0xFF2A0A4A.toInt()), floatArrayOf(0f, 0.63f, 1f), clamp,
        )
        floor.shader = LinearGradient(0f, horizon, 0f, h, 0xFF1A0436.toInt(), 0xFF08011A.toInt(), clamp)
        val sx = fx()
        val sy = fy()
        sun.shader = LinearGradient(
            0f, sy - sunR, 0f, sy + sunR,
            intArrayOf(0xFFFFE45E.toInt(), 0xFFFFB238.toInt(), 0xFFFF5C8A.toInt(), 0xFFC026D3.toInt()),
            floatArrayOf(0f, 0.32f, 0.64f, 1f), clamp,
        )
        sunGlow.shader = RadialGradient(
            sx, sy, sunR * 1.4f,
            intArrayOf(0x59FF5C8A, 0x59FF5C8A, 0x00FF5C8A), floatArrayOf(0f, 0.6f, 1f), clamp,
        )
        fade.shader = LinearGradient(0f, horizon, 0f, horizon + 46f * k, 0xFF1A0436.toInt(), 0x001A0436, clamp)
        horizonLine.color = 0xFFFF4FA0.toInt()
        horizonGlow.shader = LinearGradient(
            0f, horizon - 10 * dp, 0f, horizon + 10 * dp,
            intArrayOf(0x00FF2975, 0x80FF2975.toInt(), 0x00FF2975), null, clamp,
        )
        mountainEdge.strokeWidth = 1.2f * dp
        mountains.reset()
        addRange(LEFT_RANGE, w)
        addRange(RIGHT_RANGE, w)
    }

    private fun addRange(pts: FloatArray, w: Float) {
        val sx = w / 853f
        val hk = max(k, 0.6f)
        mountains.moveTo(pts[0] * sx, horizon - pts[1] * hk)
        var i = 2
        while (i < pts.size) {
            mountains.lineTo(pts[i] * sx, horizon - pts[i + 1] * hk)
            i += 2
        }
        mountains.close()
    }

    override fun drawBackdrop(canvas: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        val dt = if (lastNow == 0L) 16L else min(100L, now - lastNow)
        lastNow = now
        val target = if (mode == HeroView.Mode.WORKING) 1f else 0f
        workMix += (target - workMix) * min(1f, dt / 300f)
        gridPhase = (gridPhase + dt / (1100f - 800f * workMix)) % 1f

        canvas.drawRect(0f, 0f, w, horizon, sky)
        canvas.drawRect(0f, horizon, w, h, floor)

        var i = 0
        while (i < STARS.size) {
            val period = STARS[i + 3].toLong()
            val tw = 0.25f + 0.75f * (0.5f - 0.5f * sin(now % period / period.toDouble() * 2 * PI + i).toFloat())
            star.alpha = (tw * 255).toInt()
            canvas.drawCircle(STARS[i] * w, STARS[i + 1] * horizon, STARS[i + 2] * dp / 2f, star)
            i += 4
        }

        if (!quiet) {
            val sx = fx()
            val sy = fy()
            canvas.drawCircle(sx, sy, sunR * 1.4f, sunGlow)
            // The sun's lower half is cut by bands that slide down.
            canvas.save()
            val period = 19f * k
            val gap = 6f * k
            val offset = (now % 1600L) / 1600f * period
            var y = sy + offset - period
            while (y < sy + sunR) {
                val top = max(y, sy)
                if (y + gap > sy) canvas.clipOutRect(sx - sunR - 1f, top, sx + sunR + 1f, y + gap)
                y += period
            }
            canvas.drawCircle(sx, sy, sunR, sun)
            canvas.restore()
        }

        canvas.drawPath(mountains, mountainFill)
        canvas.drawPath(mountains, mountainEdge)

        // The road: lines spread out as they come closer; vertical lines meet under the sun.
        val color = lerpColor(MAGENTA, CYAN, workMix)
        val floorH = h - horizon
        grid.strokeWidth = 1.5f * dp
        val n = 9
        for (j in 0..n) {
            val d = (j + gridPhase) / n
            val y = horizon + floorH * d.pow(2.3f)
            grid.color = withAlpha(color, 0.25f + 0.65f * d)
            canvas.drawLine(0f, y, w, y, grid)
        }
        val vx = fx()
        grid.color = withAlpha(color, 0.75f)
        for (j in -16..16) {
            canvas.drawLine(vx + j * 4 * dp, horizon, vx + j * 58 * dp, h, grid)
        }
        canvas.drawRect(0f, horizon, w, horizon + 46f * k, fade)
        canvas.drawRect(0f, horizon - 10 * dp, w, horizon + 10 * dp, horizonGlow)
        canvas.drawRect(0f, horizon - 1 * dp, w, horizon + 1 * dp, horizonLine)
    }
}

/**
 * Neon Drive's Restart button: a neon ring on the sunset that flickers now and then like a sign. While it
 * restarts, a pink light races round it; done, it flashes gold.
 */
class NeonHero(context: Context) : HeroView(context) {

    private companion object {
        const val CYAN = 0xFF2DE2E6.toInt()
        const val PINK = 0xFFFF2975.toInt()
        const val GOLD = 0xFFFFE45E.toInt()
        const val ORANGE = 0xFFFB923C.toInt()
    }

    private var k = 1f
    private var spin = 0f
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x9E0A001E.toInt() }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val comet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override val spinsIcon = true

    override fun onGeometry() {
        k = size / 200f
        val r = 75f * k
        inner.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(0x002DE2E6, 0x002DE2E6, 0x732DE2E6), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
        comet.shader = SweepGradient(
            cx, cy,
            intArrayOf(0x00FF2975, 0x00FF2975, PINK, 0xFFFFD1E3.toInt()), floatArrayOf(0f, 0.56f, 0.92f, 1f),
        )
    }

    override fun step(now: Long, dt: Long) {
        if (mode == Mode.WORKING) spin = (spin + 0.45f * dt) % 360f
    }

    /** Neon signs blink now and then. */
    private fun flicker(now: Long): Float {
        val p = (now % 6000L) / 6000f
        return if ((p > 0.17f && p < 0.21f) || (p > 0.61f && p < 0.64f)) 0.55f else 1f
    }

    override fun drawHero(canvas: Canvas, now: Long) {
        val r = 75f * k
        val glow = when (mode) {
            Mode.DONE -> GOLD
            Mode.ERROR -> ORANGE
            Mode.WORKING -> PINK
            Mode.IDLE -> CYAN
        }
        val ringColor = when (mode) {
            Mode.DONE -> GOLD
            Mode.ERROR -> ORANGE
            else -> CYAN
        }
        val f = if (mode == Mode.IDLE) flicker(now) else 1f
        var scale = 1f
        if (mode == Mode.DONE) {
            val t = (now - modeAt).toFloat()
            scale = if (t < 120f) 0.92f + t / 120f * 0.15f else 1f + 0.07f * max(0f, 1f - (t - 120f) / 280f)
        }
        val rr = r * scale
        ring.strokeWidth = 26f * k
        ring.color = withAlpha(glow, 0.1f * f)
        canvas.drawCircle(cx, cy, rr, ring)
        ring.strokeWidth = 12f * k
        ring.color = withAlpha(glow, 0.26f * f)
        canvas.drawCircle(cx, cy, rr, ring)
        canvas.drawCircle(cx, cy, rr - 1.5f * k, disc)
        canvas.drawCircle(cx, cy, rr - 1.5f * k, inner)
        ring.strokeWidth = 3f * k
        ring.color = withAlpha(ringColor, f)
        canvas.drawCircle(cx, cy, rr, ring)

        if (mode == Mode.WORKING) {
            canvas.save()
            canvas.rotate(spin, cx, cy)
            comet.strokeWidth = 12f * k
            comet.alpha = 80
            canvas.drawCircle(cx, cy, r + 7f * k, comet)
            comet.strokeWidth = 5f * k
            comet.alpha = 255
            canvas.drawCircle(cx, cy, r + 7f * k, comet)
            canvas.restore()
        }
    }

    override fun styleWords(label: TextView, sub: TextView, mode: Mode) {
        val glow = when (mode) {
            Mode.WORKING -> PINK
            Mode.DONE -> GOLD
            Mode.ERROR -> ORANGE
            Mode.IDLE -> CYAN
        }
        label.setShadowLayer(10f * dp, 0f, 0f, glow)
        label.setTextColor(
            when (mode) {
                Mode.WORKING -> 0xFFFFE4F1.toInt()
                Mode.DONE -> 0xFFFFF7CC.toInt()
                else -> 0xFFF0FEFF.toInt()
            },
        )
        sub.setTextColor(
            when (mode) {
                Mode.WORKING -> 0xFFF9A8D4.toInt()
                Mode.DONE -> 0xFFFDE68A.toInt()
                else -> 0xFFA5F3FC.toInt()
            },
        )
    }
}
