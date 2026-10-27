package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Warp's backdrop: deep space. Stars stream out from behind the Restart button; a ringed planet, a moon and a
 * spiral galaxy hang in the distance, a satellite circles the button and now and then a shooting star goes by.
 * While it restarts, everything dims and the stars stretch into hyperspace streaks; when it's done there's a
 * flash as it arrives.
 */
class WarpBackdrop(context: Context) : BackdropView(context) {

    private companion object {
        val STAR_COLORS = intArrayOf(0xFFD8E6FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFC4B5FD.toInt(), 0xFFA5F3FC.toInt())
        // Fixed stars: x, y (shares of the screen), size (dp), period (ms).
        val FIXED = floatArrayOf(
            0.056f, 0.218f, 2f, 3000f, 0.188f, 0.159f, 1.5f, 2600f, 0.445f, 0.273f, 2f, 3400f,
            0.352f, 0.909f, 1.5f, 2800f, 0.082f, 0.864f, 2f, 3100f, 0.61f, 0.077f, 1.5f, 2400f,
            0.75f, 0.045f, 2f, 3600f, 0.504f, 0.75f, 1.5f, 2900f, 0.026f, 0.341f, 1.5f, 2700f,
            0.129f, 0.466f, 1f, 3300f, 0.293f, 0.295f, 1.5f, 2500f, 0.373f, 0.477f, 1f, 3700f,
            0.528f, 0.136f, 2f, 2900f, 0.657f, 0.216f, 1.5f, 3200f, 0.715f, 0.164f, 1f, 2600f,
            0.821f, 0.239f, 1.5f, 3500f, 0.985f, 0.318f, 1.5f, 2800f, 0.991f, 0.682f, 1f, 3100f,
            0.164f, 0.955f, 1.5f, 2400f, 0.455f, 0.568f, 1f, 3400f, 0.288f, 0.95f, 1.5f, 2900f,
            0.021f, 0.568f, 1f, 3600f, 0.539f, 0.936f, 1.5f, 2700f, 0.903f, 0.973f, 1f, 3000f,
            0.387f, 0.682f, 1.5f, 2500f, 0.141f, 0.141f, 1.5f, 3300f,
        )
        const val FLYERS = 28
    }

    private var k = 1f
    private var workMix = 0f
    private var lastNow = 0L
    private var fcx = 0f
    private var fcy = 0f

    // Each flying star / streak: direction (degrees), how long one trip takes (ms), and a head start (0 to 1).
    private val flyAngle = FloatArray(FLYERS) { it * 360f / FLYERS + ((it * 37) % 9 - 4) }
    private val flyMs = FloatArray(FLYERS) { 3200f + ((it * 7) % 15) * 100f }
    private val flyPhase = FloatArray(FLYERS) { (it * 0.53f) % 1f }
    private val flySize = FloatArray(FLYERS) { if (it % 3 == 0) 3f else if (it % 3 == 1) 2f else 2.5f }
    private val streakAngle = FloatArray(FLYERS) { it * 360f / FLYERS + 6.4f + ((it * 53) % 7 - 3) }
    private val streakMs = FloatArray(FLYERS) { 550f + ((it * 3) % 6) * 50f }
    private val streakPhase = FloatArray(FLYERS) { (it * 0.137f) % 1f }

    private val base = Paint()
    private val nebulaA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaB = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val galaxy = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planet = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planetShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planetGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x8CC4B5FD.toInt()
    }
    private val ringFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xD9DDD6FE.toInt()
    }
    private val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x3DA9B8DA
    }
    private val satellite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE68A.toInt() }
    private val satelliteGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flash = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringRect = RectF()

    override fun onLayoutChanged() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        k = fs() / 230f
        fcx = if (quiet) w * 0.3f else fx()
        fcy = if (quiet) h * 0.6f else fy()
        val clamp = Shader.TileMode.CLAMP
        base.shader = RadialGradient(
            fcx, fcy, max(w, h) * 1.1f,
            intArrayOf(0xFF0E1B44.toInt(), 0xFF070B1E.toInt(), 0xFF04060F.toInt()), floatArrayOf(0f, 0.48f, 1f), clamp,
        )
        nebulaA.shader = RadialGradient(
            0f, 0f, w * 0.305f,
            intArrayOf(0x29A78BFA, 0x00A78BFA), floatArrayOf(0f, 0.65f), clamp,
        )
        nebulaB.shader = RadialGradient(
            0f, 0f, w * 0.258f,
            intArrayOf(0x1A67E8F9, 0x0067E8F9), floatArrayOf(0f, 0.65f), clamp,
        )
        galaxy.shader = RadialGradient(
            0f, 0f, 60f * k,
            intArrayOf(0xBFFFFFFF.toInt(), 0x52C4B5FD, 0x1A67E8F9, 0x0067E8F9), floatArrayOf(0f, 0.22f, 0.5f, 0.72f), clamp,
        )
        val mr = 11f * k
        moon.shader = RadialGradient(
            -0.3f * mr, -0.36f * mr, mr * 1.4f,
            intArrayOf(0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt(), 0xFF64748B.toInt()), floatArrayOf(0f, 0.45f, 1f), clamp,
        )
        moonGlow.shader = RadialGradient(0f, 0f, mr * 2f, intArrayOf(0x59CBD5E1, 0x00CBD5E1), null, clamp)
        val pr = 30f * k
        planet.shader = RadialGradient(
            -0.3f * pr, -0.4f * pr, pr * 1.45f,
            intArrayOf(0xFFF5D0FE.toInt(), 0xFFA78BFA.toInt(), 0xFF4C1D95.toInt()), floatArrayOf(0f, 0.45f, 1f), clamp,
        )
        planetShade.shader = RadialGradient(
            0.25f * pr, 0.3f * pr, pr * 1.15f,
            intArrayOf(0x00040610, 0x00040610, 0x99040610.toInt()), floatArrayOf(0f, 0.55f, 1f), clamp,
        )
        planetGlow.shader = RadialGradient(0f, 0f, pr * 1.9f, intArrayOf(0x59A78BFA, 0x00A78BFA), null, clamp)
        ringBack.strokeWidth = 3f * k
        ringFront.strokeWidth = 3f * k
        ringRect.set(-60f * k, -17f * k, 60f * k, 17f * k)
        orbit.strokeWidth = 1f * dp
        orbit.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f)
        satelliteGlow.shader = RadialGradient(0f, 0f, 8 * dp, intArrayOf(0x99FDE68A.toInt(), 0x00FDE68A), null, clamp)
        flash.shader = RadialGradient(
            fcx, fcy, max(w, h) * 0.6f,
            intArrayOf(0xE6EAF6FF.toInt(), 0x00EAF6FF), null, clamp,
        )
    }

    override fun drawBackdrop(canvas: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        val dt = if (lastNow == 0L) 16L else min(100L, now - lastNow)
        lastNow = now
        val target = if (mode == HeroView.Mode.WORKING) 1f else 0f
        workMix += (target - workMix) * min(1f, dt / 300f)

        canvas.drawRect(0f, 0f, w, h, base)

        // Nebulae drifting.
        val driftA = 0.5f - 0.5f * cos(now % 22_000L / 22_000.0 * 2 * PI).toFloat()
        val driftB = 0.5f - 0.5f * cos(now % 18_000L / 18_000.0 * 2 * PI).toFloat()
        canvas.save()
        canvas.translate(w * 0.856f - 22 * dp * driftA, h * 0.205f + 14 * dp * driftA)
        canvas.drawCircle(0f, 0f, w * 0.305f, nebulaA)
        canvas.restore()
        canvas.save()
        canvas.translate(w * 0.645f - 22 * dp * driftB, h * 1.02f + 14 * dp * driftB)
        canvas.drawCircle(0f, 0f, w * 0.258f, nebulaB)
        canvas.restore()

        // Fixed stars, twinkling.
        var i = 0
        var n = 0
        while (i < FIXED.size) {
            val period = FIXED[i + 3].toLong()
            val tw = 0.3f + 0.7f * (0.5f - 0.5f * cos(now % period / period.toDouble() * 2 * PI + n).toFloat())
            dot.color = withAlpha(STAR_COLORS[n % 4], tw)
            canvas.drawCircle(FIXED[i] * w, FIXED[i + 1] * h, FIXED[i + 2] * dp / 2f, dot)
            i += 4
            n++
        }

        // Far away: they dim while it jumps.
        if (!quiet) drawFarAway(canvas, now, 1f - 0.75f * workMix)

        // Stars flying out of the portal...
        val calm = 1f - workMix
        if (calm > 0.01f) {
            for (s in 0 until FLYERS) {
                val u = ((now % flyMs[s].toLong()) / flyMs[s] + flyPhase[s]) % 1f
                val e = u * u
                val r = 40 * dp + 520 * dp * e
                val len = flySize[s] * dp * (1f + 4f * u)
                val a = Math.toRadians(flyAngle[s].toDouble())
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                val alpha = (if (u < 0.15f) u / 0.15f * 0.9f else 0.9f + 0.1f * u) * calm
                line.color = withAlpha(STAR_COLORS[s % 4], alpha)
                line.strokeWidth = flySize[s] * dp * 0.7f
                canvas.drawLine(fcx + ca * r, fcy + sa * r, fcx + ca * (r + len), fcy + sa * (r + len), line)
            }
        }
        // ...and stretched into hyperspace while it restarts.
        if (workMix > 0.01f) {
            val since = (now - modeAt).coerceAtLeast(0L)
            for (s in 0 until FLYERS) {
                val u = (since / streakMs[s] + streakPhase[s]) % 1f
                val r = 60 * dp + 560 * dp * u.pow(1.8f)
                val len = 8 * dp + 240 * dp * u
                val a = Math.toRadians(streakAngle[s].toDouble())
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                val alpha = min(1f, u / 0.2f) * 0.9f * workMix
                line.color = withAlpha(STAR_COLORS[(s + 1) % 4], alpha)
                line.strokeWidth = 2f * dp
                canvas.drawLine(fcx + ca * r, fcy + sa * r, fcx + ca * (r + len), fcy + sa * (r + len), line)
            }
        }

        // Shooting stars every 7 and 9 seconds.
        shootingStar(canvas, now, 7000L, 1000L, w * 0.352f, h * 0.145f, 25.0, 90 * dp)
        shootingStar(canvas, now, 9000L, 5000L, w * 0.023f, h * 0.68f, 22.0, 70 * dp)

        // Arrival flash.
        if (!quiet && mode == HeroView.Mode.DONE) {
            val t = (now - modeAt).toFloat()
            if (t < 700f) {
                flash.alpha = ((1f - t / 700f) * 0.6f * 255).toInt()
                canvas.drawRect(0f, 0f, w, h, flash)
            }
        }
    }

    private fun drawFarAway(canvas: Canvas, now: Long, alpha: Float) {
        val a = (alpha * 255).toInt()

        // A spiral galaxy, low right.
        canvas.save()
        canvas.translate(fcx + 180f * k, fcy + 95f * k)
        canvas.rotate(-20f)
        canvas.scale(1f, 46f / 120f)
        galaxy.alpha = a
        canvas.drawCircle(0f, 0f, 60f * k, galaxy)
        canvas.restore()

        // A moon, top left.
        canvas.save()
        canvas.translate(fcx - 138f * k, fcy - 147f * k)
        moonGlow.alpha = a
        canvas.drawCircle(0f, 0f, 22f * k, moonGlow)
        moon.alpha = a
        canvas.drawCircle(0f, 0f, 11f * k, moon)
        canvas.restore()

        // A ringed planet, top right, bobbing gently. The ring passes behind it, then in front.
        val bob = -5 * dp * (0.5f - 0.5f * cos(now % 8000L / 8000.0 * 2 * PI).toFloat())
        canvas.save()
        canvas.translate(fcx + 185f * k, fcy - 140f * k + bob)
        planetGlow.alpha = a
        canvas.drawCircle(0f, 0f, 57f * k, planetGlow)
        canvas.rotate(-18f)
        ringBack.alpha = (0x8C * alpha).toInt()
        canvas.drawOval(ringRect, ringBack)
        planet.alpha = a
        canvas.drawCircle(0f, 0f, 30f * k, planet)
        planetShade.alpha = a
        canvas.drawCircle(0f, 0f, 30f * k, planetShade)
        canvas.clipRect(-62f * k, 0f, 62f * k, 19f * k)
        ringFront.alpha = (0xD9 * alpha).toInt()
        canvas.drawOval(ringRect, ringFront)
        canvas.restore()

        // A satellite circling the portal.
        val orbitR = 112f * k
        orbit.alpha = (0x3D * alpha).toInt()
        canvas.drawCircle(fcx, fcy, orbitR, orbit)
        val sa = Math.toRadians(now % 16_000L / 16_000.0 * 360.0 - 90.0)
        val sx = fcx + orbitR * cos(sa).toFloat()
        val sy = fcy + orbitR * sin(sa).toFloat()
        canvas.save()
        canvas.translate(sx, sy)
        satelliteGlow.alpha = a
        canvas.drawCircle(0f, 0f, 8 * dp, satelliteGlow)
        satellite.alpha = a
        canvas.drawCircle(0f, 0f, 3 * dp, satellite)
        canvas.restore()
    }

    private fun shootingStar(
        canvas: Canvas, now: Long, period: Long, offset: Long,
        x: Float, y: Float, angleDeg: Double, length: Float,
    ) {
        val p = ((now + offset) % period) / period.toFloat()
        if (p >= 0.12f) return
        val q = p / 0.12f
        val a = Math.toRadians(angleDeg)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val head = 230 * dp * q
        val alpha = if (q < 0.25f) q / 0.25f else 1f - (q - 0.25f) / 0.75f
        line.strokeWidth = 1.5f * dp
        line.color = withAlpha(0xFFFFFFFF.toInt(), alpha * 0.35f)
        canvas.drawLine(x + ca * (head - length), y + sa * (head - length), x + ca * head, y + sa * head, line)
        line.color = withAlpha(0xFFFFFFFF.toInt(), alpha)
        canvas.drawLine(x + ca * (head - length * 0.35f), y + sa * (head - length * 0.35f), x + ca * head, y + sa * head, line)
    }
}

/**
 * Warp's Restart button: a portal with a slowly turning rainbow-blue ring and a faint swirl inside. While it
 * restarts, a white comet races round it; done, it glows cyan.
 */
class WarpHero(context: Context) : HeroView(context) {

    private companion object {
        const val CYAN = 0xFF67E8F9.toInt()
        const val AMBER = 0xFFFDBA74.toInt()
    }

    private var k = 1f
    private var spin = 0f
    private var swirl = 0f
    private var comet = 0f
    private val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val cometPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = RectF()

    override fun onGeometry() {
        k = size / 230f
        val r = 84f * k
        val clamp = Shader.TileMode.CLAMP
        outerGlow.shader = RadialGradient(
            cx, cy, r + 34f * k,
            intArrayOf(0x0067E8F9, 0x0067E8F9, 0x3867E8F9, 0x0067E8F9),
            floatArrayOf(0f, (r - 6f * k) / (r + 34f * k), r / (r + 34f * k), 1f), clamp,
        )
        ring.shader = SweepGradient(
            cx, cy,
            intArrayOf(CYAN, 0xFFA78BFA.toInt(), 0xFF22D3EE.toInt(), 0xFF818CF8.toInt(), CYAN), null,
        )
        core.shader = RadialGradient(
            cx, cy - 0.2f * r, r * 1.2f,
            intArrayOf(0xFF14214A.toInt(), 0xFF070C22.toInt()), floatArrayOf(0f, 0.7f), clamp,
        )
        cometPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.61f, 0.97f, 1f),
        )
    }

    override fun step(now: Long, dt: Long) {
        spin = (spin + dt * 0.06f) % 360f   // one turn every 6 seconds
        swirl = (swirl - dt * 0.026f) % 360f
        if (mode == Mode.WORKING) comet = (comet + dt * 0.4f) % 360f
    }

    override fun drawHero(canvas: Canvas, now: Long) {
        val r = 84f * k
        canvas.drawCircle(cx, cy, r + 34f * k, outerGlow)
        canvas.drawCircle(cx, cy, r - 2f * k, core)

        // The swirl inside.
        swirlPaint.strokeWidth = 1.5f * k
        canvas.save()
        canvas.rotate(swirl, cx, cy)
        swirlPaint.color = 0x2E67E8F9
        arc.set(cx - 0.62f * r, cy - 0.62f * r, cx + 0.62f * r, cy + 0.62f * r)
        canvas.drawArc(arc, 0f, 110f, false, swirlPaint)
        canvas.drawArc(arc, 180f, 110f, false, swirlPaint)
        canvas.restore()
        canvas.save()
        canvas.rotate(-swirl * 0.7f, cx, cy)
        swirlPaint.color = 0x2EA78BFA
        arc.set(cx - 0.78f * r, cy - 0.78f * r, cx + 0.78f * r, cy + 0.78f * r)
        canvas.drawArc(arc, 60f, 90f, false, swirlPaint)
        canvas.drawArc(arc, 240f, 90f, false, swirlPaint)
        canvas.restore()

        // The ring.
        canvas.save()
        canvas.rotate(spin, cx, cy)
        ring.strokeWidth = 4f * k
        canvas.drawCircle(cx, cy, r, ring)
        canvas.restore()

        when (mode) {
            Mode.WORKING -> {
                canvas.save()
                canvas.rotate(comet, cx, cy)
                cometPaint.strokeWidth = 12f * k
                cometPaint.alpha = 90
                canvas.drawCircle(cx, cy, r + 4f * k, cometPaint)
                cometPaint.strokeWidth = 5f * k
                cometPaint.alpha = 255
                canvas.drawCircle(cx, cy, r + 4f * k, cometPaint)
                canvas.restore()
            }
            Mode.DONE -> {
                val fadeTo = 0.6f + 0.4f * max(0f, 1f - (now - modeAt) / 2000f)
                glowRing.strokeWidth = 16f * k
                glowRing.color = withAlpha(CYAN, 0.45f * fadeTo)
                canvas.drawCircle(cx, cy, r, glowRing)
                glowRing.strokeWidth = 3f * k
                glowRing.color = CYAN
                canvas.drawCircle(cx, cy, r, glowRing)
            }
            Mode.ERROR -> {
                glowRing.strokeWidth = 3f * k
                glowRing.color = AMBER
                canvas.drawCircle(cx, cy, r, glowRing)
            }
            Mode.IDLE -> Unit
        }
    }

    override fun styleWords(label: TextView, sub: TextView, mode: Mode) {
        sub.setTextColor(
            when (mode) {
                Mode.WORKING, Mode.DONE -> 0xFFA5F3FC.toInt()
                else -> 0xFFA9B8DA.toInt()
            },
        )
    }
}
