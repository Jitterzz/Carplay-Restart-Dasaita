package app.carplayrestart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The backdrop: deep night blue with soft blue, violet and cyan glows drifting very slowly, a faint dot grid
 * and darker corners. Redraws about 30 times a second while showing, and not at all otherwise.
 */
class AuroraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Glow(
        val color: Int,
        val x: Float, val y: Float, // where it sits, as a share of the width and height
        val size: Float,            // radius, as a share of the longer side
        val dx: Float, val dy: Float, // how far it wanders
        val periodMs: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var radius = 0f
    }

    private val glows = listOf(
        Glow(0x663B82F6, 0.22f, 0.58f, 0.50f, 0.05f, 0.07f, 21_000f), // blue, behind the orb
        Glow(0x4D8B5CF6, 0.88f, 0.04f, 0.42f, 0.06f, 0.06f, 26_000f), // violet, top right
        Glow(0x3322D3EE, 0.80f, 1.05f, 0.40f, 0.05f, 0.04f, 18_000f), // cyan, bottom right
    )

    private val dp = resources.displayMetrics.density
    private val basePaint = Paint()
    private val gridPaint = Paint()
    private val vignettePaint = Paint()

    private var running = false
    private var lastDraw = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            if (now - lastDraw >= 33) {
                lastDraw = now
                invalidate()
            }
            postOnAnimation(this)
        }
    }

    init {
        // A tiny tile with one faint dot, repeated: the grid.
        val cell = (24 * dp).toInt().coerceAtLeast(8)
        val tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawCircle(
            cell / 2f, cell / 2f, 0.9f * dp,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF },
        )
        gridPaint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        basePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xFF060912.toInt(), 0xFF0B1328.toInt(), Shader.TileMode.CLAMP,
        )
        val longSide = max(w, h).toFloat()
        for (g in glows) {
            g.radius = g.size * longSide
            // Drawn around (0, 0); the canvas moves it into place each frame.
            g.paint.shader = RadialGradient(
                0f, 0f, g.radius,
                intArrayOf(g.color, withAlpha(g.color, (g.color ushr 24) / 255f * 0.4f), 0),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
        }
        vignettePaint.shader = RadialGradient(
            w / 2f, h * 0.45f, longSide * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0x99000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        canvas.drawRect(0f, 0f, w, h, basePaint)
        val t = SystemClock.uptimeMillis().toFloat()
        for (g in glows) {
            val phase = (2 * PI * (t % g.periodMs) / g.periodMs).toFloat()
            val x = (g.x + g.dx * sin(phase)) * w
            val y = (g.y + g.dy * cos(phase * 0.85f)) * h
            canvas.save()
            canvas.translate(x, y)
            canvas.drawCircle(0f, 0f, g.radius, g.paint)
            canvas.restore()
        }
        canvas.drawRect(0f, 0f, w, h, gridPaint)
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLoop()
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateLoop()
    }

    private fun updateLoop() {
        val show = isAttachedToWindow && windowVisibility == View.VISIBLE
        if (show && !running) {
            running = true
            postOnAnimation(frame)
        } else if (!show) {
            running = false
            removeCallbacks(frame)
        }
    }
}
