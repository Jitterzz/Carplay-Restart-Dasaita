package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout

/**
 * A card: by default a frosted-glass panel with a soft top highlight and a hairline edge that catches the light.
 * A theme can restyle it ([setStyle]): solid or gradient fill, a plain edge, its own corners and a colored halo.
 * Give it an accent (in code or with app:glassAccent) and the edge glows in that color instead.
 * It clips what's inside to its rounded corners, so a tap ripple on it stays rounded too.
 */
class GlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dp = resources.displayMetrics.density
    private var radius: Float
    private var fillTop: Int
    private var fillBottom: Int
    private var edgeColor = 0 // 0: the light-catching glass edge
    private var sheen = true
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
        fillTop = a.getColor(R.styleable.GlassLayout_glassFill, 0x12FFFFFF)
        fillBottom = fillTop
        accent = a.getColor(R.styleable.GlassLayout_glassAccent, 0)
        a.recycle()
        setWillNotDraw(false)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
                // Only a card with a halo casts its (colored) shadow.
                outline.alpha = if (view.elevation > 0f) 1f else 0f
            }
        }
        clipToOutline = true
    }

    /** Takes on a theme's card look. */
    fun setStyle(s: CardStyle) {
        radius = s.radiusDp * dp
        fillTop = s.fill
        fillBottom = s.fill2
        edgeColor = s.edge
        sheen = s.sheen
        if (s.glow != 0 && Build.VERSION.SDK_INT >= 28) {
            elevation = 6 * dp
            outlineAmbientShadowColor = s.glow
            outlineSpotShadowColor = s.glow
        } else {
            elevation = 0f
        }
        edgePaint.strokeWidth = (if (s.edge != 0) 1f else 1.2f) * dp
        invalidateOutline()
        updateShaders()
        invalidate()
    }

    /** Tints the edge (0 for the theme's own edge). */
    fun setAccent(color: Int) {
        if (color == accent) return
        accent = color
        updateShaders()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
    }

    private fun updateShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val inset = edgePaint.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
        val clamp = Shader.TileMode.CLAMP
        if (fillTop == fillBottom) {
            fillPaint.shader = null
            fillPaint.color = fillTop
        } else {
            fillPaint.shader = LinearGradient(0f, 0f, 0f, h, fillTop, fillBottom, clamp)
        }
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0x1AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.6f), clamp,
        )
        when {
            accent != 0 -> {
                edgePaint.color = accent
                edgePaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(withAlpha(accent, 0.75f), withAlpha(accent, 0.15f), withAlpha(accent, 0.45f)),
                    floatArrayOf(0f, 0.5f, 1f), clamp,
                )
            }
            edgeColor != 0 -> {
                edgePaint.shader = null
                edgePaint.color = edgeColor
            }
            else -> edgePaint.shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(0x40FFFFFF, 0x0DFFFFFF, 0x24FFFFFF), floatArrayOf(0f, 0.5f, 1f), clamp,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (sheen) canvas.drawRoundRect(rect, radius, radius, sheenPaint)
        canvas.drawRoundRect(rect, radius, radius, edgePaint)
        super.onDraw(canvas)
    }
}
