package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/**
 * The scenery behind a screen, whatever the look (aurora glows, carbon fibre, a synthwave road, deep space,
 * a quiet glow, a cherry tree). Some scenery is centred on the Restart button, so the main screen tells it
 * where the button is ([setFocus]); Settings has no button and asks for a [quiet] version instead.
 *
 * Redraws every [frameMs] while showing (never, for a still picture), and not at all otherwise.
 */
abstract class BackdropView(context: Context) : View(context) {

    var mode: HeroView.Mode = HeroView.Mode.IDLE
        set(value) {
            if (field == value) return
            val old = field
            field = value
            modeAt = SystemClock.uptimeMillis()
            onModeChanged(old, value)
            invalidate()
        }

    protected var modeAt: Long = SystemClock.uptimeMillis()
    protected val dp: Float = resources.displayMetrics.density

    /** No decorations tied to the Restart button, for screens that don't have one. */
    var quiet = false
        set(value) {
            field = value
            onLayoutChanged()
            invalidate()
        }

    private var focusX = 0f
    private var focusY = 0f
    private var focusSize = 0f

    /** Milliseconds between redraws while showing; 0 for a still picture. */
    protected open val frameMs = 33L

    /** The Restart button's centre and size, in this view's coordinates. */
    fun setFocus(x: Float, y: Float, s: Float) {
        if (x == focusX && y == focusY && s == focusSize) return
        focusX = x
        focusY = y
        focusSize = s
        onLayoutChanged()
        invalidate()
    }

    /** Where the button is, or a sensible spot on screens without one. */
    protected fun fx(): Float = if (focusSize > 0f) focusX else width * 0.27f
    protected fun fy(): Float = if (focusSize > 0f) focusY else height * 0.58f
    protected fun fs(): Float = if (focusSize > 0f) focusSize else min(width, height) * 0.6f

    protected open fun onModeChanged(old: HeroView.Mode, new: HeroView.Mode) {}

    /** The size or the button's position changed: rebuild shaders and paths. */
    protected open fun onLayoutChanged() {}

    protected abstract fun drawBackdrop(canvas: Canvas, now: Long)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) onLayoutChanged()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        drawBackdrop(canvas, SystemClock.uptimeMillis())
    }

    private var running = false
    private var lastDraw = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            if (now - lastDraw >= frameMs) {
                lastDraw = now
                invalidate()
            }
            postOnAnimation(this)
        }
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
        val show = frameMs > 0 && isAttachedToWindow && windowVisibility == View.VISIBLE
        if (show && !running) {
            running = true
            postOnAnimation(frame)
        } else if (!show) {
            running = false
            removeCallbacks(frame)
        }
    }
}
