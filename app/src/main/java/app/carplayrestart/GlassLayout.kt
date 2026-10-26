package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout

/**
 * A frosted-glass card: a see-through panel with a soft top highlight and a hairline edge that catches the light.
 * Give it an accent (in code or with app:glassAccent) and the edge glows in that color instead.
 * It clips what's inside to its rounded corners, so a tap ripple on it stays rounded too.
 */
class GlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dp = resources.displayMetrics.density
    private val radius: Float
    private val fill: Int
    private var accent: Int

    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
    }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.GlassLayout)
        radius = a.getDimension(R.styleable.GlassLayout_glassRadius, 24 * dp)
        fill = a.getColor(R.styleable.GlassLayout_glassFill, 0x12FFFFFF)
        accent = a.getColor(R.styleable.GlassLayout_glassAccent, 0)
        a.recycle()
        fillPaint.color = fill
        setWillNotDraw(false)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
                outline.alpha = 0f
            }
        }
        clipToOutline = true
    }

    /** Tints the edge (0 for the plain white edge). */
    fun setAccent(color: Int) {
        if (color == accent) return
        accent = color
        updateShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = edgePaint.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
        updateShaders()
    }

    private fun updateShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0x1AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP,
        )
        val edge = if (accent != 0) {
            intArrayOf(withAlpha(accent, 0.75f), withAlpha(accent, 0.15f), withAlpha(accent, 0.45f))
        } else {
            intArrayOf(0x40FFFFFF, 0x0DFFFFFF, 0x24FFFFFF)
        }
        edgePaint.shader = LinearGradient(0f, 0f, w, h, edge, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, sheenPaint)
        canvas.drawRoundRect(rect, radius, radius, edgePaint)
        super.onDraw(canvas)
    }
}
