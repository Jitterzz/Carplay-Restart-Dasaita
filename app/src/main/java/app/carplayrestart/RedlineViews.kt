package app.carplayrestart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Redline's backdrop: carbon fibre, with a faint red glow behind the gauge. A still picture. */
class RedlineBackdrop(context: Context) : BackdropView(context) {

    override val frameMs = 0L

    private val base = Paint().apply { color = 0xFF0B0C0E.toInt() }
    private val weave = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // One light and one dark diagonal in a small tile; repeated, they weave.
        val cell = (10 * dp).toInt().coerceAtLeast(6)
        val f = cell.toFloat()
        val tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        val c = Canvas(tile)
        val light = Paint().apply {
            color = 0x06FFFFFF
            strokeWidth = 2 * dp
        }
        val dark = Paint().apply {
            color = 0x73000000
            strokeWidth = 2 * dp
        }
        c.drawLine(0f, f, f, 0f, light)
        c.drawLine(-f / 2, f / 2, f / 2, -f / 2, light)
        c.drawLine(f / 2, f * 1.5f, f * 1.5f, f / 2, light)
        c.drawLine(0f, 0f, f, f, dark)
        c.drawLine(-f / 2, f / 2, f / 2, f * 1.5f, dark)
        c.drawLine(f / 2, -f / 2, f * 1.5f, f / 2, dark)
        weave.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onLayoutChanged() {
        if (width == 0 || height == 0) return
        glow.shader = RadialGradient(
            fx(), fy(), fs() * 0.75f,
            intArrayOf(0x1FFF3B30, 0x00FF3B30), null, Shader.TileMode.CLAMP,
        )
    }

    override fun drawBackdrop(canvas: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        canvas.drawRect(0f, 0f, w, h, weave)
        if (!quiet) canvas.drawCircle(fx(), fy(), fs() * 0.75f, glow)
    }
}

/**
 * Redline's Restart button: a tachometer with an engine-start button in the middle. The needle idles around
 * 1,000 and twitches; while it restarts, the needle revs up and down and the shift lights come on with it;
 * done, it settles back and the button's ring turns green.
 */
class RedlineHero(context: Context) : HeroView(context) {

    private companion object {
        const val IDLE_ANGLE = -101.25f // 1,000 on the dial (0 is straight up, the dial runs -135 to 135)
        const val RED = 0xFFFF3B30.toInt()
        const val GREEN = 0xFF22C55E.toInt()
        const val AMBER = 0xFFF59E0B.toInt()
        val LEDS = intArrayOf(GREEN, GREEN, GREEN, AMBER, AMBER, AMBER, RED, RED, RED)
    }

    private var k = 1f
    private val arcRect = RectF()
    private val tickXY = FloatArray(17 * 4)
    private val numberXY = FloatArray(9 * 2)

    private val bezel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E0F12.toInt() }
    private val bezelEdge = stroke(0xFF2A2B30.toInt())
    private val innerRing = stroke(0xFF1C1D21.toInt())
    private val track = stroke(0xFF2E3036.toInt())
    private val redZone = stroke(RED)
    private val tick = stroke(0).apply { strokeCap = Paint.Cap.ROUND }
    private val numbers = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Fonts.get(context, Face.res(R.font.saira_sc_semibold))
    }
    private val led = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needleGlow = stroke(0x59FF3B30).apply { strokeCap = Paint.Cap.ROUND }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePath = Path()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chrome = Paint(Paint.ANTI_ALIAS_FLAG)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = stroke(RED)
    private val comet = stroke(0).apply { strokeCap = Paint.Cap.ROUND }

    private var angle = IDLE_ANGLE
    private var velocity = 0f
    private var cometSpin = 0f

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
    }

    override fun onGeometry() {
        k = size / 320f
        bezelEdge.strokeWidth = 2 * k
        innerRing.strokeWidth = 1 * k
        track.strokeWidth = 3 * k
        redZone.strokeWidth = 7 * k
        needleGlow.strokeWidth = 8 * k
        comet.strokeWidth = 4 * k
        numbers.textSize = 17 * k
        arcRect.set(cx - 132 * k, cy - 132 * k, cx + 132 * k, cy + 132 * k)

        // Ticks: 0 to 8 (thousands) round 270°, with a small tick between each.
        for (i in 0 until 17) {
            val major = i < 9
            val deg = if (major) 135.0 + 33.75 * i else 135.0 + 16.875 + 33.75 * (i - 9)
            val a = Math.toRadians(deg)
            val r1 = (if (major) 116f else 124f) * k
            val r2 = 132f * k
            tickXY[i * 4] = cx + r1 * cos(a).toFloat()
            tickXY[i * 4 + 1] = cy + r1 * sin(a).toFloat()
            tickXY[i * 4 + 2] = cx + r2 * cos(a).toFloat()
            tickXY[i * 4 + 3] = cy + r2 * sin(a).toFloat()
        }
        for (i in 0..8) {
            val a = Math.toRadians(135.0 + 33.75 * i)
            numberXY[i * 2] = cx + 99f * k * cos(a).toFloat()
            numberXY[i * 2 + 1] = cy + 99f * k * sin(a).toFloat()
        }

        // The needle points straight up; the canvas turns it.
        needlePath.reset()
        needlePath.moveTo(cx - 3 * k, cy)
        needlePath.lineTo(cx - 1 * k, cy - 130 * k)
        needlePath.lineTo(cx + 1 * k, cy - 130 * k)
        needlePath.lineTo(cx + 3 * k, cy)
        needlePath.close()
        val clamp = Shader.TileMode.CLAMP
        needle.shader = LinearGradient(
            cx, cy, cx, cy - 132 * k,
            intArrayOf(0xFFB91C1C.toInt(), RED, 0xFFFF9A92.toInt()), floatArrayOf(0f, 0.55f, 1f), clamp,
        )
        shadow.shader = RadialGradient(
            cx, cy + 8 * k, 80 * k,
            intArrayOf(0x99000000.toInt(), 0x99000000.toInt(), 0x00000000), floatArrayOf(0f, 0.6f, 1f), clamp,
        )
        chrome.shader = SweepGradient(
            cx, cy,
            intArrayOf(0xFF7C7F87.toInt(), 0xFF2B2D32.toInt(), 0xFFA6AAB2.toInt(), 0xFF2B2D32.toInt(), 0xFF7C7F87.toInt()),
            null,
        )
        disc.shader = RadialGradient(
            cx, cy - 18 * k, 76 * k,
            intArrayOf(0xFF2A2C31.toInt(), 0xFF141518.toInt(), 0xFF0E0F11.toInt()), floatArrayOf(0f, 0.62f, 1f), clamp,
        )
        comet.shader = SweepGradient(
            cx, cy,
            intArrayOf(0x00FF3B30, 0x00FF3B30, RED, 0xFFFFB4AE.toInt()), floatArrayOf(0f, 0.69f, 0.94f, 1f),
        )
    }

    override fun step(now: Long, dt: Long) {
        val t = now - modeAt
        val target = when (mode) {
            Mode.IDLE -> IDLE_ANGLE + 3f * sin(now % 2400L / 2400.0 * 2 * PI).toFloat()
            Mode.WORKING -> if ((t / 650L) % 2L == 0L) 114f else 52f
            Mode.DONE -> IDLE_ANGLE
            Mode.ERROR -> -135f
        }
        // A springy needle: it overshoots a little and settles, like a real one.
        val s = dt / 1000f
        velocity += ((target - angle) * 70f - velocity * 13f) * s
        angle = (angle + velocity * s).coerceIn(-138f, 138f)
        if (mode == Mode.WORKING) cometSpin = (cometSpin + 0.4f * dt) % 360f
    }

    override fun drawHero(canvas: Canvas, now: Long) {
        // The dial.
        canvas.drawCircle(cx, cy, 152 * k, bezel)
        canvas.drawCircle(cx, cy, 152 * k, bezelEdge)
        canvas.drawCircle(cx, cy, 108 * k, innerRing)
        canvas.drawArc(arcRect, 135f, 270f, false, track)
        canvas.drawArc(arcRect, 354.375f, 50.625f, false, redZone)
        for (i in 0 until 17) {
            val major = i < 9
            tick.strokeWidth = (if (major) 3f else 2f) * k
            tick.color = when {
                !major -> 0xFF71717A.toInt()
                i >= 7 -> RED
                else -> 0xFFE4E4E7.toInt()
            }
            canvas.drawLine(tickXY[i * 4], tickXY[i * 4 + 1], tickXY[i * 4 + 2], tickXY[i * 4 + 3], tick)
        }
        val lift = (numbers.descent() + numbers.ascent()) / 2f
        for (i in 0..8) {
            numbers.color = if (i >= 7) 0xFFFF6B61.toInt() else 0xFFD4D4D8.toInt()
            canvas.drawText(i.toString(), numberXY[i * 2], numberXY[i * 2 + 1] - lift, numbers)
        }

        // Shift lights: they come on as the needle climbs.
        val rpm = (angle + 135f) / 270f * 8000f
        for (i in 0 until 9) {
            val x = cx + (-56f + 14f * i) * k
            val y = cy + 107f * k
            val lit = mode == Mode.WORKING && rpm > 4200f + i * 350f
            if (lit) {
                led.color = withAlpha(LEDS[i], 0.35f)
                canvas.drawCircle(x, y, 9f * k, led)
            }
            led.color = withAlpha(LEDS[i], if (lit) 1f else 0.16f)
            canvas.drawCircle(x, y, 5f * k, led)
        }

        // The needle.
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - 126f * k, needleGlow)
        canvas.drawPath(needlePath, needle)
        canvas.restore()

        // The start button over the needle's hub.
        canvas.drawCircle(cx, cy, 80f * k, shadow)
        canvas.drawCircle(cx, cy, 66f * k, chrome)
        canvas.drawCircle(cx, cy, 60f * k, disc)
        when (mode) {
            Mode.DONE -> {
                ring.strokeWidth = 12f * k
                ring.color = withAlpha(GREEN, 0.35f)
                canvas.drawCircle(cx, cy, 62f * k, ring)
                ring.strokeWidth = 3f * k
                ring.color = GREEN
                canvas.drawCircle(cx, cy, 58.5f * k, ring)
            }
            Mode.ERROR -> {
                ring.strokeWidth = 3f * k
                ring.color = AMBER
                canvas.drawCircle(cx, cy, 58.5f * k, ring)
            }
            else -> {
                val pulse = if (mode == Mode.IDLE) {
                    0.5f - 0.5f * cos(now % 2400L / 2400.0 * 2 * PI).toFloat()
                } else {
                    1f
                }
                ring.strokeWidth = 10f * k
                ring.color = withAlpha(RED, 0.4f * pulse)
                canvas.drawCircle(cx, cy, 63f * k, ring)
                ring.strokeWidth = 2f * k
                ring.color = RED
                canvas.drawCircle(cx, cy, 59f * k, ring)
                if (mode == Mode.WORKING) {
                    canvas.save()
                    canvas.rotate(cometSpin, cx, cy)
                    canvas.drawCircle(cx, cy, 57f * k, comet)
                    canvas.restore()
                }
            }
        }
    }
}
