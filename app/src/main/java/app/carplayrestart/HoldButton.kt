package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min

/**
 * "Hold to reboot radio": press and hold, and an amber glow fills the button left to right. Let go early and it
 * drains back; hold all the way (about 1.4 seconds) and it fires. Safer than a tap while driving, and no
 * dialog with small buttons. Timing comes from the display's frame clock, not system animations, so a radio
 * with animations turned off can't make it fire on a tap.
 *
 * Accessibility services (TalkBack) can't hold, so their click goes to the click listener instead.
 */
class HoldButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Held all the way. */
    var onConfirmed: (() -> Unit)? = null

    /** Tapped instead of held. */
    var onShortTap: (() -> Unit)? = null

    private companion object {
        const val HOLD_MS = 1400f
        const val DRAIN_MS = 260f
    }

    private val dp = resources.displayMetrics.density
    private val radius = 20 * dp
    private val rect = RectF()
    private val clip = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FBBF24 }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FDE68A.toInt() }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
    }

    private var progress = 0f
    private var holding = false
    private var confirmed = false
    private var downAt = 0L
    private var lastFrame = 0L
    private var label: TextView? = null

    private val frame = Runnable { tick() }

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        label = findViewById(R.id.hold_label)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokePaint.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        progressPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            0x40FBBF24, 0x99F97316.toInt(), Shader.TileMode.CLAMP,
        )
        strokePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFFFBBF24.toInt(), 0x80F97316.toInt(), 0xE6FB923C.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || confirmed) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                holding = true
                downAt = SystemClock.uptimeMillis()
                isPressed = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                label?.setText(R.string.hold_holding)
                kick()
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = 24 * dp
                val outside = event.x < -slop || event.y < -slop || event.x > width + slop || event.y > height + slop
                if (holding && outside) release()
            }
            MotionEvent.ACTION_UP -> {
                val quick = holding && SystemClock.uptimeMillis() - downAt < 300
                release()
                if (quick) onShortTap?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    /** Back to "Hold to reboot radio" (after a reboot that didn't happen, or coming back to the screen). */
    fun reset() {
        if (holding) return
        confirmed = false
        progress = 0f
        isPressed = false
        label?.setText(R.string.hold_idle)
        invalidate()
    }

    private fun release() {
        holding = false
        isPressed = false
        if (!confirmed) label?.setText(R.string.hold_idle)
        kick()
    }

    private fun kick() {
        removeCallbacks(frame)
        lastFrame = 0L
        postOnAnimation(frame)
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16L else min(50L, now - lastFrame)
        lastFrame = now
        if (holding && !confirmed) {
            progress = (progress + dt / HOLD_MS).coerceAtMost(1f)
            if (progress >= 1f) confirm()
        } else if (!confirmed) {
            progress = (progress - dt / DRAIN_MS).coerceAtLeast(0f)
        }
        invalidate()
        val more = !confirmed && (holding || progress > 0f)
        if (more) postOnAnimation(frame) else lastFrame = 0L
    }

    private fun confirm() {
        confirmed = true
        holding = false
        isPressed = false
        progress = 1f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        label?.setText(R.string.hold_done)
        onConfirmed?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (progress > 0f) {
            canvas.save()
            canvas.clipPath(clip)
            val x = rect.left + rect.width() * progress
            canvas.drawRect(rect.left, rect.top, x, rect.bottom, progressPaint)
            if (progress < 1f) canvas.drawRect(x - 2 * dp, rect.top, x, rect.bottom, edgePaint)
            canvas.restore()
        }
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
}
