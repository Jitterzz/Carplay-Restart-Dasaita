package app.carplayrestart

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.min

/**
 * The big Restart button, whatever the look. Each theme paints its own button (an orb, a tachometer, a neon ring,
 * a portal, a ring of light, a blossom) by extending this; the icon and words on it are ordinary child views
 * (layout/hero_text.xml) that this sizes to fit.
 *
 * It keeps itself square, as big as its space allows up to the theme's maximum. The animation runs on the
 * display's frame clock, so it keeps moving on radios with system animations turned off, and it stops whenever
 * the screen isn't showing.
 */
abstract class HeroView(context: Context) : FrameLayout(context) {

    enum class Mode { IDLE, WORKING, DONE, ERROR }

    var mode: Mode = Mode.IDLE
        set(value) {
            if (field == value) return
            val old = field
            field = value
            modeAt = SystemClock.uptimeMillis()
            onModeChanged(old, value)
            updateIcon()
            invalidate()
        }

    /** When [mode] last changed. */
    protected var modeAt: Long = SystemClock.uptimeMillis()
    protected val dp: Float = resources.displayMetrics.density

    private var maxSizePx = (380 * dp).toInt()
    private var text: HeroText? = null
    private var laidOutFor = -1

    protected var cx = 0f
    protected var cy = 0f
    protected var size = 0f

    /** Whether the icon spins while it's working. */
    protected open val spinsIcon = true
    private var iconSpin = 0f

    private var running = false
    private var lastFrame = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = if (lastFrame == 0L) 16L else min(50L, now - lastFrame)
            lastFrame = now
            step(now, dt)
            spin(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        setWillNotDraw(false)
    }

    /** Moves things along by [dt] milliseconds. */
    protected abstract fun step(now: Long, dt: Long)

    /** Paints the button (behind the words). */
    protected abstract fun drawHero(canvas: Canvas, now: Long)

    protected open fun onModeChanged(old: Mode, new: Mode) {}

    /** Called once the size is known, and again if it changes. */
    protected open fun onGeometry() {}

    /** Lets a theme recolor the words for each state (a neon glow, say). */
    protected open fun styleWords(label: TextView, sub: TextView, mode: Mode) {}

    protected open fun onPress() {}

    /** Takes on [t]'s fonts, sizes and colors for the words. Call after the words are added. */
    fun applyTheme(t: AppTheme) {
        text = t.heroText
        maxSizePx = (t.heroMaxDp * dp).toInt()
        label()?.apply {
            typeface = Fonts.get(context, t.fonts.hero)
            setAllCaps(t.heroText.caps)
            letterSpacing = t.heroText.spacing
            setTextColor(t.heroText.labelColor)
        }
        sub()?.apply {
            typeface = Fonts.get(context, t.fonts.label)
            setAllCaps(t.heroText.subCaps)
            letterSpacing = t.heroText.subSpacing
            setTextColor(t.heroText.subColor)
        }
        icon()?.imageTintList = ColorStateList.valueOf(t.heroText.labelColor)
        laidOutFor = -1
        updateIcon()
        requestLayout()
    }

    private fun label(): TextView? = findViewById(R.id.orb_label)
    private fun sub(): TextView? = findViewById(R.id.orb_sub)
    private fun icon(): ImageView? = findViewById(R.id.orb_icon)

    private fun updateIcon() {
        val quiet = text?.quietIcon == true && (mode == Mode.IDLE || mode == Mode.WORKING)
        icon()?.visibility = if (quiet) View.GONE else View.VISIBLE
        val l = label()
        val s = sub()
        if (l != null && s != null) styleWords(l, s, mode)
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed && !isPressed) onPress()
        super.setPressed(pressed)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val wKnown = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        val hKnown = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED
        val s = when {
            wKnown && hKnown -> minOf(w, h)
            wKnown -> w
            hKnown -> h
            else -> maxSizePx
        }.coerceAtMost(maxSizePx)
        layoutWords(s)
        val exact = MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
    }

    /** Sizes the words for a button [s] pixels across (only when that changes). */
    private fun layoutWords(s: Int) {
        val t = text ?: return
        if (s == laidOutFor || s <= 0) return
        laidOutFor = s
        getChildAt(0)?.layoutParams?.let { lp -> lp.width = (s * t.width).toInt() }
        label()?.let { tv ->
            val max = (s * t.label).toInt().coerceAtLeast(10)
            val min = (max * 0.5f).toInt().coerceAtLeast(8)
            tv.layoutParams?.let { it.height = (max * 1.38f).toInt() }
            if (min < max) {
                tv.setAutoSizeTextTypeUniformWithConfiguration(min, max, 1, TypedValue.COMPLEX_UNIT_PX)
            } else {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, max.toFloat())
            }
        }
        sub()?.setTextSize(TypedValue.COMPLEX_UNIT_PX, s * t.sub)
        icon()?.layoutParams?.let {
            val px = (s * t.icon).toInt()
            it.width = px
            it.height = px
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        size = min(w, h).toFloat()
        if (size > 0f) onGeometry()
    }

    override fun onDraw(canvas: Canvas) {
        if (size > 0f) drawHero(canvas, SystemClock.uptimeMillis())
        super.onDraw(canvas)
    }

    /** The icon spins while working, then rolls on round to upright. */
    private fun spin(dt: Long) {
        val v = icon() ?: return
        if (spinsIcon && mode == Mode.WORKING) {
            iconSpin = (iconSpin + 0.4f * dt) % 360f
        } else if (iconSpin != 0f) {
            iconSpin += 0.6f * dt
            if (iconSpin >= 360f) iconSpin = 0f
        }
        v.rotation = iconSpin
    }

    // Animate only while the screen is actually showing.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateLoop()
    }

    private fun updateLoop() {
        if (isAttachedToWindow && windowVisibility == View.VISIBLE) startLoop() else stopLoop()
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastFrame = 0L
        postOnAnimation(frame)
    }

    private fun stopLoop() {
        running = false
        removeCallbacks(frame)
    }
}
