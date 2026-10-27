package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * A little picture of a look for Settings › Look: its sky, its Restart button with a hint of what's around it,
 * and the cards on the right. Still, so six of them cost nothing.
 */
class ThemeSwatch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var theme: AppTheme? = null
        set(value) {
            field = value
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val clip = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val t = theme ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val corner = 10 * dp
        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, corner, corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        val sw = t.swatch // sky top, sky bottom, button, button edge
        fill.shader = LinearGradient(0f, 0f, 0f, h, sw[0], sw[1], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        val cx = w * 0.27f
        val cy = h * 0.52f
        val r = h * 0.3f
        behind(canvas, t, w, h, cx, cy, r)

        fill.color = sw[2]
        canvas.drawCircle(cx, cy, r, fill)
        line.strokeWidth = 1.8f * dp
        line.color = sw[3]
        canvas.drawCircle(cx, cy, r, line)
        onTop(canvas, t, cx, cy, r)

        // The cards and the hold button, on the right.
        val left = w * 0.55f
        val right = w - 8 * dp
        card(canvas, t, left, h * 0.16f, right, h * 0.36f)
        card(canvas, t, left, h * 0.42f, right, h * 0.62f)
        line.strokeWidth = 1.2f * dp
        line.color = t.hold.stroke
        rect.set(left, h * 0.69f, right, h * 0.85f)
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, line)
        canvas.restore()
    }

    private fun card(canvas: Canvas, t: AppTheme, l: Float, top: Float, r: Float, b: Float) {
        rect.set(l, top, r, b)
        val rr = 4 * dp
        fill.color = if (t.card.fill ushr 24 < 0x30) withAlpha(t.text1, 0.1f) else t.card.fill
        canvas.drawRoundRect(rect, rr, rr, fill)
        line.strokeWidth = 1f * dp
        line.color = if (t.card.edge != 0) t.card.edge else withAlpha(t.text1, 0.2f)
        canvas.drawRoundRect(rect, rr, rr, line)
        // A line of "text".
        line.strokeWidth = 2f * dp
        line.color = withAlpha(t.text2, 0.7f)
        val y = (top + b) / 2f
        canvas.drawLine(l + 5 * dp, y, l + (r - l) * 0.55f, y, line)
    }

    /** What's around each look's button. */
    private fun behind(canvas: Canvas, t: AppTheme, w: Float, h: Float, cx: Float, cy: Float, r: Float) {
        val clamp = Shader.TileMode.CLAMP
        when (t.id) {
            ThemeId.AURORA -> {
                glow(canvas, w * 0.2f, h * 0.25f, h * 0.8f, 0x803B82F6.toInt())
                glow(canvas, w * 0.62f, h * 0.9f, h * 0.7f, 0x668B5CF6)
                glow(canvas, cx, cy, r * 1.9f, 0x5567E8F9)
            }
            ThemeId.REDLINE -> {
                glow(canvas, cx, cy, r * 2.2f, 0x40FF3B30)
                rect.set(cx - r * 1.32f, cy - r * 1.32f, cx + r * 1.32f, cy + r * 1.32f)
                line.strokeWidth = 2f * dp
                line.color = 0x40FFFFFF
                canvas.drawArc(rect, 135f, 220f, false, line)
                line.color = 0xFFFF3B30.toInt()
                canvas.drawArc(rect, 355f, 50f, false, line)
            }
            ThemeId.NEON -> {
                val horizon = h * 0.7f
                canvas.save()
                canvas.clipRect(0f, 0f, w, horizon)
                fill.shader = LinearGradient(
                    0f, cy - r * 1.5f, 0f, horizon, 0xFFFFE45E.toInt(), 0xFFFF2975.toInt(), clamp,
                )
                canvas.drawCircle(cx, cy + r * 0.3f, r * 1.45f, fill)
                fill.shader = null
                canvas.restore()
                fill.color = 0xCC1A0533.toInt()
                canvas.drawRect(0f, horizon, w, h, fill)
                line.strokeWidth = 1f * dp
                line.color = 0xB3FF2975.toInt()
                for (j in 1..3) {
                    val y = horizon + (h - horizon) * (j / 3.2f) * (j / 3.2f)
                    canvas.drawLine(0f, y, w, y, line)
                }
                for (j in -4..4) canvas.drawLine(cx + j * 4 * dp, horizon, cx + j * 26 * dp, h, line)
                line.color = 0xFF2DE2E6.toInt()
                canvas.drawLine(0f, horizon, w, horizon, line)
            }
            ThemeId.WARP -> {
                fill.color = 0xCCFFFFFF.toInt()
                for (i in 0 until 16) {
                    val x = w * ((i * 37 % 100) / 100f)
                    val y = h * ((i * 61 % 100) / 100f)
                    canvas.drawCircle(x, y, (0.6f + (i % 3) * 0.35f) * dp, fill)
                }
                glow(canvas, cx, cy, r * 2f, 0x6667E8F9)
                // A ringed planet up top.
                val px = w * 0.47f
                val py = h * 0.2f
                val pr = h * 0.07f
                fill.color = 0xFFC4B5FD.toInt()
                canvas.drawCircle(px, py, pr, fill)
                canvas.save()
                canvas.rotate(-18f, px, py)
                rect.set(px - pr * 2.2f, py - pr * 0.6f, px + pr * 2.2f, py + pr * 0.6f)
                line.strokeWidth = 1f * dp
                line.color = 0xCCE9D5FF.toInt()
                canvas.drawOval(rect, line)
                canvas.restore()
            }
            ThemeId.MIDNIGHT -> glow(canvas, cx, cy, r * 2.3f, 0x38E7C48A)
            ThemeId.SAKURA -> {
                // A hill, and a branch of blossoms in the corner.
                fill.color = 0xFFF9C9DD.toInt()
                canvas.drawCircle(w * 0.3f, h * 1.55f, h * 0.85f, fill)
                fill.color = 0xFFFFB3CF.toInt()
                canvas.drawCircle(w * 0.48f, h * 0.02f, h * 0.16f, fill)
                canvas.drawCircle(w * 0.38f, h * 0.07f, h * 0.12f, fill)
                fill.color = 0xFFF48FB1.toInt()
                canvas.drawCircle(w * 0.43f, h * 0.12f, h * 0.08f, fill)
                fill.color = 0xFFFF9CC2.toInt()
                canvas.drawCircle(w * 0.08f, h * 0.3f, 2 * dp, fill)
                canvas.drawCircle(w * 0.45f, h * 0.55f, 1.6f * dp, fill)
                canvas.drawCircle(w * 0.12f, h * 0.82f, 1.8f * dp, fill)
            }
        }
    }

    /** Touches on the button itself. */
    private fun onTop(canvas: Canvas, t: AppTheme, cx: Float, cy: Float, r: Float) {
        when (t.id) {
            ThemeId.MIDNIGHT -> {
                rect.set(cx - r, cy - r, cx + r, cy + r)
                line.strokeWidth = 2f * dp
                line.color = 0xFFE7C48A.toInt()
                canvas.drawArc(rect, -90f, 250f, false, line)
            }
            ThemeId.SAKURA -> {
                // A five-petal blossom.
                fill.color = 0xFFF06292.toInt()
                for (i in 0 until 5) {
                    val a = Math.toRadians(i * 72.0 - 90.0)
                    canvas.drawCircle(
                        cx + r * 0.42f * cos(a).toFloat(), cy + r * 0.42f * sin(a).toFloat(), r * 0.3f, fill,
                    )
                }
                fill.color = 0xFFFFF3B0.toInt()
                canvas.drawCircle(cx, cy, r * 0.2f, fill)
            }
            ThemeId.NEON -> {
                line.strokeWidth = 1f * dp
                line.color = 0x99FFFFFF.toInt()
                canvas.drawCircle(cx, cy, r * 0.78f, line)
            }
            else -> {
                // A soft highlight, like light catching it.
                fill.shader = RadialGradient(
                    cx - r * 0.35f, cy - r * 0.4f, r * 0.9f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(cx, cy, r, fill)
                fill.shader = null
            }
        }
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill.shader = RadialGradient(x, y, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, fill)
        fill.shader = null
    }
}
