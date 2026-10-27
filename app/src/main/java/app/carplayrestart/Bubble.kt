package app.carplayrestart

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.min

/**
 * The floating restart button over the CarPlay app (Settings › Quick restart). Tap it to restart the CarPlay app;
 * drag it anywhere (it settles against the nearer side and stays put); hold it to open this app.
 *
 * The helper draws it as an accessibility overlay, so it needs no "draw over other apps" permission, and it only
 * shows while the CarPlay app is in front.
 */
class FloatingButton(private val service: HelperService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val dp = service.resources.displayMetrics.density
    private val sizePx = (60 * dp).toInt()
    private val marginPx = (10 * dp).toInt()
    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var dragStartX = 0
    private var dragStartY = 0

    fun update(show: Boolean) {
        if (show) show() else hide()
    }

    fun setWorking(on: Boolean) {
        view?.working = on
    }

    /** Repaints it in the current look. */
    fun restyle() {
        view?.setTheme(Themes.current(service))
    }

    private fun show() {
        if (view != null) return
        val v = BubbleView(service, this)
        v.setTheme(Themes.current(service))
        val p = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        p.gravity = Gravity.TOP or Gravity.START
        val screen = screen()
        val savedX = Prefs.bubbleX(service)
        val savedY = Prefs.bubbleY(service)
        // First time: the right-hand edge, halfway down. CarPlay keeps its dock on the left.
        p.x = if (savedX >= 0) savedX else screen.width() - sizePx - marginPx
        p.y = if (savedY >= 0) savedY else (screen.height() - sizePx) / 2
        clamp(p, screen)
        try {
            wm.addView(v, p)
            view = v
            params = p
        } catch (_: Exception) {
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        params = null
        try {
            wm.removeViewImmediate(v)
        } catch (_: Exception) {
        }
    }

    // ---- Called by the button ----

    internal fun tapped() {
        service.restartFromBubble()
    }

    internal fun openApp() {
        try {
            service.startActivity(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    internal fun startDrag() {
        val p = params ?: return
        dragStartX = p.x
        dragStartY = p.y
    }

    internal fun dragBy(dx: Float, dy: Float) {
        val p = params ?: return
        p.x = dragStartX + dx.toInt()
        p.y = dragStartY + dy.toInt()
        clamp(p, screen())
        move(p)
    }

    /** Let go: glide to the nearer side, then remember the spot. */
    internal fun endDrag() {
        val p = params ?: return
        val v = view ?: return
        val screen = screen()
        val fromX = p.x
        val toX = if (p.x + sizePx / 2 < screen.width() / 2) marginPx else screen.width() - sizePx - marginPx
        val start = SystemClock.uptimeMillis()
        val glide = object : Runnable {
            override fun run() {
                if (view !== v) return
                val t = min(1f, (SystemClock.uptimeMillis() - start) / 180f)
                val ease = 1f - (1f - t) * (1f - t)
                p.x = (fromX + (toX - fromX) * ease).toInt()
                move(p)
                if (t < 1f) v.postOnAnimation(this) else Prefs.setBubblePos(service, p.x, p.y)
            }
        }
        v.postOnAnimation(glide)
    }

    private fun move(p: WindowManager.LayoutParams) {
        val v = view ?: return
        try {
            wm.updateViewLayout(v, p)
        } catch (_: Exception) {
        }
    }

    private fun clamp(p: WindowManager.LayoutParams, screen: Rect) {
        p.x = p.x.coerceIn(0, (screen.width() - sizePx).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screen.height() - sizePx).coerceAtLeast(0))
    }

    /** The whole screen, in the overlay's coordinates. */
    private fun screen(): Rect {
        val m = DisplayMetrics()
        try {
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            @Suppress("DEPRECATION")
            display.getRealMetrics(m)
        } catch (_: Exception) {
            m.setTo(service.resources.displayMetrics)
        }
        return Rect(0, 0, m.widthPixels, m.heightPixels)
    }
}

/** The floating button itself: a round button in the current look's colors, with a spinner while it works. */
class BubbleView(context: Context, private val owner: FloatingButton) : View(context) {

    var working = false
        set(value) {
            if (field == value) return
            field = value
            if (value) postOnAnimation(spinner)
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var theme: AppTheme? = null
    private val icon: Drawable? = context.getDrawable(R.drawable.ic_jumpstart)?.mutate()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()
    private var spin = 0f
    private var lastSpin = 0L

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var held = false
    private val hold = Runnable {
        held = true
        performLongClick()
    }

    private val spinner = object : Runnable {
        override fun run() {
            if (!working || !isAttachedToWindow) {
                lastSpin = 0L
                return
            }
            val now = SystemClock.uptimeMillis()
            if (lastSpin != 0L) spin = (spin + (now - lastSpin) * 0.36f) % 360f
            lastSpin = now
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        contentDescription = context.getString(R.string.bubble_description)
        isClickable = true
        isLongClickable = true
        alpha = 0.94f
    }

    fun setTheme(t: AppTheme) {
        theme = t
        rebuild()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        val t = theme ?: return
        val s = width.toFloat()
        if (s <= 0f) return
        val c = s / 2f
        val clamp = Shader.TileMode.CLAMP
        shadow.shader = RadialGradient(
            c, c + 2 * dp, c, intArrayOf(0x66000000, 0x66000000, 0x00000000), floatArrayOf(0f, 0.72f, 1f), clamp,
        )
        fill.shader = LinearGradient(0f, 0f, s, s, t.pill, t.pill2, clamp)
        edge.color = if (t.light) 0xE6FFFFFF.toInt() else 0x59FFFFFF
        arc.color = t.onPill
        icon?.setTint(t.onPill)
        val inset = 7 * dp
        arcRect.set(inset, inset, s - inset, s - inset)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width.toFloat()
        val c = s / 2f
        val r = c - 4 * dp
        canvas.save()
        if (isPressed) canvas.scale(0.92f, 0.92f, c, c)
        canvas.drawCircle(c, c + 1.5f * dp, c, shadow)
        canvas.drawCircle(c, c, r, fill)
        canvas.drawCircle(c, c, r - 0.6f * dp, edge)
        icon?.let { d ->
            val half = (r * 0.5f).toInt()
            d.setBounds((c - half).toInt(), (c - half).toInt(), (c + half).toInt(), (c + half).toInt())
            if (working) {
                canvas.save()
                canvas.rotate(spin, c, c)
                d.alpha = 140
                d.draw(canvas)
                canvas.restore()
                canvas.drawArc(arcRect, spin * 1.6f, 100f, false, arc)
            } else {
                d.alpha = 255
                d.draw(canvas)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                held = false
                isPressed = true
                owner.startDrag()
                postDelayed(hold, 650)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && !held && hypot(dx, dy) > slop) {
                    dragging = true
                    removeCallbacks(hold)
                    isPressed = false
                    invalidate()
                }
                if (dragging) owner.dragBy(dx, dy)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                isPressed = false
                invalidate()
                when {
                    dragging -> owner.endDrag()
                    !held -> {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        performClick()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                isPressed = false
                invalidate()
                if (dragging) owner.endDrag()
            }
        }
        return true
    }

    // Taps (and TalkBack's double-tap) restart; holding opens the app.
    override fun performClick(): Boolean {
        super.performClick()
        if (!working) owner.tapped()
        return true
    }

    override fun performLongClick(): Boolean {
        super.performLongClick()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        owner.openApp()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        removeCallbacks(spinner)
        super.onDetachedFromWindow()
    }
}
