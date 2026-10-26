package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** A status light: a glowing dot with a ring that keeps pulsing out of it, like a "live" indicator. */
class PulseDot @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var color: Int = 0xFF34D399.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var pulsing: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            updateLoop()
            invalidate()
        }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        val cyy = height / 2f
        val rMax = min(width, height) / 2f
        val rDot = rMax * 0.34f
        if (pulsing) {
            val phase = (SystemClock.uptimeMillis() % 1800L) / 1800f
            ringPaint.color = withAlpha(color, (1f - phase) * 0.5f)
            canvas.drawCircle(c, cyy, rDot + (rMax - rDot) * phase, ringPaint)
        }
        glowPaint.color = withAlpha(color, 0.3f)
        canvas.drawCircle(c, cyy, rDot * 1.7f, glowPaint)
        dotPaint.color = color
        canvas.drawCircle(c, cyy, rDot, dotPaint)
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
        val go = pulsing && isAttachedToWindow && windowVisibility == View.VISIBLE
        if (go && !running) {
            running = true
            postOnAnimation(frame)
        } else if (!go) {
            running = false
            removeCallbacks(frame)
        }
    }
}
