package app.carplayrestart

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Shapes shared by Sakura's backdrop and button. */
private object SakuraShapes {

    /** A four-point sparkle, 24 units across, centred on (12, 12). */
    val sparkle: Path = Path().apply {
        moveTo(12f, 0f)
        cubicTo(13f, 7f, 17f, 11f, 24f, 12f)
        cubicTo(17f, 13f, 13f, 17f, 12f, 24f)
        cubicTo(11f, 17f, 7f, 13f, 0f, 12f)
        cubicTo(7f, 11f, 11f, 7f, 12f, 0f)
        close()
    }

    /** A loose petal: a lens shape 1 unit long, centred on (0, 0). */
    val petal: Path = Path().apply {
        moveTo(-0.5f, 0f)
        quadTo(0f, -0.8f, 0.5f, 0f)
        quadTo(0f, 0.8f, -0.5f, 0f)
        close()
    }

    fun drawSparkle(canvas: Canvas, x: Float, y: Float, sizePx: Float, scale: Float, paint: Paint) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(sizePx / 24f * scale, sizePx / 24f * scale)
        canvas.translate(-12f, -12f)
        canvas.drawPath(sparkle, paint)
        canvas.restore()
    }
}

/**
 * Sakura's backdrop: a pink sky with a soft sun, drifting cloud and rolling hills, a cherry tree in bloom over
 * the Restart button, petals falling and the odd twinkle.
 */
class SakuraBackdrop(context: Context) : BackdropView(context) {

    private companion object {
        // Blossom clusters on the tree (drawn 480 x 440): x, y, radius.
        val CLUSTERS = floatArrayOf(
            104f, 150f, 26f, 150f, 120f, 24f, 196f, 100f, 24f, 244f, 84f, 21f, 290f, 92f, 22f, 340f, 90f, 20f,
            392f, 92f, 18f, 430f, 98f, 13f, 352f, 126f, 15f, 114f, 94f, 19f, 62f, 110f, 21f, 24f, 130f, 22f,
            8f, 180f, 18f, 300f, 114f, 13f, 238f, 112f, 14f, 164f, 150f, 16f, 70f, 152f, 15f,
        )
        // How each cluster's five blooms sit around its centre: x, y, size (shares of its radius).
        val PUFFS = floatArrayOf(0f, 0f, 1f, -0.75f, 0.25f, 0.72f, 0.78f, 0.2f, 0.68f, -0.3f, -0.55f, 0.66f, 0.38f, -0.5f, 0.6f)
        val PINKS = intArrayOf(
            0xFFFFB7D3.toInt(), 0xFFF99AC0.toInt(), 0xFFFFC9DE.toInt(),
            0xFFFFD6E6.toInt(), 0xFFFDA4C8.toInt(), 0xFFF78FB7.toInt(),
        )
        val BLOSSOMS = floatArrayOf(
            130f, 104f, 178f, 132f, 222f, 72f, 268f, 104f, 318f, 80f, 372f, 108f,
            410f, 86f, 86f, 126f, 40f, 100f, 20f, 160f, 90f, 170f, 330f, 138f,
        )
        // Branches: start, two control points, end, width.
        val BRANCHES = floatArrayOf(
            98f, 172f, 140f, 136f, 196f, 116f, 258f, 106f, 8f,
            258f, 106f, 318f, 98f, 368f, 94f, 418f, 102f, 5f,
            36f, 262f, 26f, 214f, 20f, 168f, 2f, 128f, 7f,
            196f, 118f, 206f, 96f, 222f, 84f, 240f, 76f, 3.5f,
            300f, 100f, 314f, 120f, 332f, 128f, 352f, 132f, 3f,
            142f, 140f, 130f, 116f, 122f, 100f, 106f, 88f, 3.5f,
            380f, 98f, 392f, 82f, 404f, 74f, 420f, 70f, 2.5f,
        )
        // Falling petals: x (at 853 wide), fall time (s), sway time (s), head start (s), width, height (dp).
        val PETALS = floatArrayOf(
            40f, 8f, 2.6f, 0f, 12f, 10f, 95f, 9.7f, 2.97f, 1.9f, 13f, 10f, 150f, 11.4f, 3.34f, 3.8f, 12f, 10f,
            210f, 8.6f, 3.71f, 5.7f, 12f, 10f, 270f, 10.3f, 2.88f, 7.6f, 13f, 10f, 330f, 12f, 3.25f, 9.5f, 13f, 10f,
            390f, 9.2f, 3.62f, 2.2f, 10f, 8f, 450f, 10.9f, 2.79f, 2.4f, 10f, 8f, 510f, 8.1f, 3.16f, 7.1f, 13f, 10f,
            570f, 9.8f, 3.53f, 7.3f, 12f, 10f, 630f, 11.5f, 2.7f, 7.5f, 13f, 10f, 690f, 8.7f, 3.07f, 3.5f, 10f, 8f,
            750f, 10.4f, 3.44f, 2f, 9f, 7f, 810f, 12.1f, 2.61f, 0.5f, 12f, 10f, 120f, 9.3f, 2.98f, 8f, 11f, 9f,
            600f, 11f, 3.35f, 6.5f, 10f, 8f,
        )
        // Twinkles: x, y (on the tree's 853 x 440 layout), size (dp), period (s), head start (s).
        val TWINKLES = floatArrayOf(338f, 168f, 14f, 2.6f, 0f, 100f, 352f, 10f, 3.1f, 0.9f, 420f, 300f, 12f, 2.8f, 1.6f)
    }

    private var k = 1f
    private var treeScale = 1f
    private val trunk = Path()
    private val branchPaths = ArrayList<Path>()
    private val backHill = Path()
    private val frontHill = Path()
    private val matrix = Matrix()

    private val sky = Paint()
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
    private val hillBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CF2A9C9.toInt() }
    private val hillFront = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99EC97BD.toInt() }
    private val bark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E3B55.toInt() }
    private val branch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF7C4763.toInt()
    }
    private val bloom = Paint(Paint.ANTI_ALIAS_FLAG)
    private val petalOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6F99AC0.toInt() }
    private val petalInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFD3E4.toInt() }
    private val twinkle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val twinkleGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40E64C8C }
    private val cloudRect = RectF()

    init {
        trunk.moveTo(-20f, 440f)
        trunk.cubicTo(22f, 382f, 30f, 330f, 24f, 284f)
        trunk.cubicTo(18f, 238f, 44f, 198f, 94f, 166f)
        trunk.lineTo(104f, 180f)
        trunk.cubicTo(62f, 206f, 46f, 242f, 50f, 284f)
        trunk.cubicTo(56f, 334f, 48f, 388f, 22f, 440f)
        trunk.close()
        var i = 0
        while (i < BRANCHES.size) {
            branchPaths.add(Path().apply {
                moveTo(BRANCHES[i], BRANCHES[i + 1])
                cubicTo(BRANCHES[i + 2], BRANCHES[i + 3], BRANCHES[i + 4], BRANCHES[i + 5], BRANCHES[i + 6], BRANCHES[i + 7])
            })
            i += 9
        }
        backHill.moveTo(0f, 70f)
        backHill.cubicTo(120f, 30f, 220f, 40f, 320f, 62f)
        backHill.cubicTo(420f, 84f, 520f, 50f, 640f, 56f)
        backHill.cubicTo(740f, 60f, 800f, 40f, 853f, 46f)
        backHill.lineTo(853f, 122f)
        backHill.lineTo(0f, 122f)
        backHill.close()
        frontHill.moveTo(0f, 96f)
        frontHill.cubicTo(110f, 72f, 230f, 80f, 330f, 98f)
        frontHill.cubicTo(450f, 118f, 560f, 86f, 700f, 92f)
        frontHill.cubicTo(780f, 96f, 820f, 84f, 853f, 88f)
        frontHill.lineTo(853f, 122f)
        frontHill.lineTo(0f, 122f)
        frontHill.close()
    }

    override fun onLayoutChanged() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        k = fs() / 220f
        treeScale = min(w / 853f, h / 440f)
        sky.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFFFFEAF3.toInt(), 0xFFFFD8E8.toInt(), 0xFFFAC6DD.toInt(), 0xFFF5B7D3.toInt()),
            floatArrayOf(0f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
        sun.shader = RadialGradient(
            0f, 0f, 50f * max(k, 0.7f),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF6FA.toInt(), 0x00FFF6FA), floatArrayOf(0f, 0.42f, 0.7f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawBackdrop(canvas: Canvas, now: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, sky)

        // The sun, breathing softly.
        val b = 0.5f - 0.5f * cos(now % 6000L / 6000.0 * 2 * PI).toFloat()
        val sx = if (quiet) w * 0.47f else fx() + 180f * k
        val sy = if (quiet) h * 0.42f else fy() - 57f * k
        canvas.save()
        canvas.translate(sx, sy)
        canvas.scale(0.94f + 0.12f * b, 0.94f + 0.12f * b)
        sun.alpha = ((0.75f + 0.25f * b) * 255).toInt()
        canvas.drawCircle(0f, 0f, 50f * max(k, 0.7f), sun)
        canvas.restore()

        // A cloud drifting to and fro.
        val drift = 28 * dp * (0.5f - 0.5f * cos(now % 14_000L / 14_000.0 * 2 * PI).toFloat())
        val cxl = w * 0.352f + drift
        val cyl = 24 * dp
        cloudRect.set(cxl, cyl, cxl + 70 * dp, cyl + 22 * dp)
        canvas.drawRoundRect(cloudRect, 11 * dp, 11 * dp, cloud)
        canvas.drawCircle(cxl + 26 * dp, cyl + 2 * dp, 14 * dp, cloud)
        canvas.drawCircle(cxl + 45 * dp, cyl + 4 * dp, 11 * dp, cloud)

        // Hills along the bottom.
        val hs = h / 440f
        canvas.save()
        canvas.translate(0f, h - 122f * hs)
        canvas.scale(w / 853f, hs)
        canvas.drawPath(backHill, hillBack)
        canvas.drawPath(frontHill, hillFront)
        canvas.restore()

        // The cherry tree.
        canvas.save()
        canvas.scale(treeScale, treeScale)
        canvas.drawPath(trunk, bark)
        for ((n, p) in branchPaths.withIndex()) {
            branch.strokeWidth = BRANCHES[n * 9 + 8]
            canvas.drawPath(p, branch)
        }
        var c = 0
        var ci = 0
        while (c < CLUSTERS.size) {
            val x = CLUSTERS[c]
            val y = CLUSTERS[c + 1]
            val r = CLUSTERS[c + 2]
            var p = 0
            var j = 0
            while (p < PUFFS.size) {
                bloom.color = PINKS[(ci + j) % PINKS.size]
                canvas.drawCircle(x + PUFFS[p] * r, y + PUFFS[p + 1] * r, PUFFS[p + 2] * r, bloom)
                p += 3
                j++
            }
            bloom.color = 0x8CFFFFFF.toInt()
            canvas.drawCircle(x + 0.12f * r, y - 0.12f * r, 0.3f * r, bloom)
            c += 3
            ci++
        }
        var bI = 0
        while (bI < BLOSSOMS.size) {
            bloom.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(BLOSSOMS[bI], BLOSSOMS[bI + 1], 3.4f, bloom)
            bloom.color = 0xFFE64C8C.toInt()
            canvas.drawCircle(BLOSSOMS[bI], BLOSSOMS[bI + 1], 1.3f, bloom)
            bI += 2
        }
        canvas.restore()

        // Falling petals.
        var q = 0
        while (q < PETALS.size) {
            val fallMs = (PETALS[q + 1] * 1000f).toLong()
            val swayMs = (PETALS[q + 2] * 1000f).toLong()
            val head = (PETALS[q + 3] * 1000f).toLong()
            val u = ((now + head) % fallMs) / fallMs.toFloat()
            val sway = 26 * dp * (0.5f - 0.5f * cos((now + head) % swayMs / swayMs.toDouble() * 2 * PI).toFloat())
            val x = PETALS[q] * w / 853f - 140 * dp * u + sway
            val y = -30 * dp + (h + 60 * dp) * u
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(600f * u)
            canvas.scale(PETALS[q + 4] * dp, PETALS[q + 5] * dp)
            canvas.drawPath(SakuraShapes.petal, petalOuter)
            canvas.scale(0.55f, 0.55f)
            canvas.drawPath(SakuraShapes.petal, petalInner)
            canvas.restore()
            q += 6
        }

        // Twinkles.
        var s = 0
        while (s < TWINKLES.size) {
            val periodMs = (TWINKLES[s + 3] * 1000f).toLong()
            val headMs = (TWINKLES[s + 4] * 1000f).toLong()
            val tw = 0.5f - 0.5f * cos((now + headMs) % periodMs / periodMs.toDouble() * 2 * PI).toFloat()
            val x = TWINKLES[s] * treeScale
            val y = TWINKLES[s + 1] * treeScale
            val size = TWINKLES[s + 2] * dp
            twinkleGlow.alpha = (0x40 * tw).toInt()
            canvas.drawCircle(x, y, size * 0.6f * (0.5f + 0.5f * tw), twinkleGlow)
            twinkle.alpha = ((0.35f + 0.65f * tw) * 255).toInt()
            SakuraShapes.drawSparkle(canvas, x, y, size, 0.5f + 0.5f * tw, twinkle)
            s += 5
        }
    }
}

/**
 * Sakura's Restart button: a cherry blossom that turns slowly. While it restarts it spins and throws petals;
 * done, sparkles pop round it (and the icon is a little heart).
 */
class SakuraHero(context: Context) : HeroView(context) {

    private companion object {
        val BURST_ANGLES = floatArrayOf(10f, 55f, 100f, 145f, 190f, 235f, 280f, 325f)
        val BURST_DELAYS = floatArrayOf(0f, 0.14f, 0.28f, 0.42f, 0.57f, 0.71f, 0.85f, 0.5f) // shares of a burst
        val SPARKLE_ANGLES = floatArrayOf(20f, 80f, 140f, 200f, 260f, 320f)
    }

    override val spinsIcon = false

    private var k = 1f
    private var fs = 1f          // the flower's scale (it's drawn 200 units across)
    private var turn = 0f
    private var turnSpeed = 7.3f // degrees per second

    private val flower = Path()
    private val stamens = Path()
    private val stamenDots = FloatArray(20)
    private val petalFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val petalEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x80F06292.toInt()
        strokeWidth = 1.2f
    }
    private val stamenLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFF48FB8.toInt()
        strokeWidth = 1.2f
    }
    private val stamenDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE64C8C.toInt() }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centre = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centreEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xE6FFFFFF.toInt()
    }
    private val centreShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val burst = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val sparkGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59E64C8C }

    init {
        // One notched petal, drawn pointing up from the flower's centre (100, 100), turned five times.
        val petal = Path().apply {
            moveTo(100f, 100f)
            cubicTo(70f, 84f, 58f, 50f, 76f, 22f)
            cubicTo(84f, 10f, 94f, 12f, 100f, 24f)
            cubicTo(106f, 12f, 116f, 10f, 124f, 22f)
            cubicTo(142f, 50f, 130f, 84f, 100f, 100f)
            close()
        }
        val m = Matrix()
        for (i in 0 until 5) {
            m.setRotate(72f * i, 100f, 100f)
            flower.addPath(petal, m)
        }
        for (i in 0 until 10) {
            val a = Math.toRadians(i * 36.0 + 18.0)
            val sa = sin(a).toFloat()
            val ca = cos(a).toFloat()
            stamens.moveTo(100f + 47f * sa, 100f - 47f * ca)
            stamens.lineTo(100f + 53f * sa, 100f - 53f * ca)
            stamenDots[i * 2] = 100f + 55f * sa
            stamenDots[i * 2 + 1] = 100f - 55f * ca
        }
        petalFill.shader = RadialGradient(
            100f, 100f, 92f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE3EE.toInt(), 0xFFFFB3D0.toInt(), 0xFFF78FB7.toInt()),
            floatArrayOf(0f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun onGeometry() {
        k = size / 220f
        fs = size * 0.91f / 200f
        val r = 46f * fs
        val clamp = Shader.TileMode.CLAMP
        shadow.shader = RadialGradient(
            cx, cy + 8f * k, 100f * fs,
            intArrayOf(0x47D6336C, 0x47D6336C, 0x00D6336C), floatArrayOf(0f, 0.55f, 1f), clamp,
        )
        centre.shader = RadialGradient(
            cx, cy - 0.3f * r, r * 1.25f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFEAF2.toInt(), 0xFFFFD9E7.toInt()), floatArrayOf(0f, 0.7f, 1f), clamp,
        )
        centreShadow.shader = RadialGradient(
            cx, cy + 4f * k, r + 8f * k,
            intArrayOf(0x47D6336C, 0x47D6336C, 0x00D6336C), floatArrayOf(0f, 0.75f, 1f), clamp,
        )
        centreEdge.strokeWidth = 2f * fs
    }

    override fun step(now: Long, dt: Long) {
        val target = when (mode) {
            Mode.WORKING -> 230f
            Mode.IDLE -> 7.3f
            Mode.DONE -> 4f
            Mode.ERROR -> 0f
        }
        turnSpeed += (target - turnSpeed) * min(1f, dt / 450f)
        turn = (turn + turnSpeed * dt / 1000f) % 360f
    }

    override fun drawHero(canvas: Canvas, now: Long) {
        val since = (now - modeAt).toFloat()

        // Petals thrown out while it restarts (behind the flower).
        if (mode == Mode.WORKING) {
            for (i in BURST_ANGLES.indices) {
                val u = ((since / 1200f) - BURST_DELAYS[i]).let { if (it < 0f) -1f else it % 1f }
                if (u < 0f) continue
                val d = 30f * k + 75f * k * (1f - (1f - u) * (1f - u))
                val a = Math.toRadians(BURST_ANGLES[i].toDouble())
                val alpha = if (u < 0.15f) u / 0.15f else 1f - (u - 0.15f) / 0.85f
                burst.color = withAlpha(if (i % 2 == 0) 0xFFF06292.toInt() else 0xFFF78FB7.toInt(), alpha)
                canvas.save()
                canvas.translate(cx + d * cos(a).toFloat(), cy + d * sin(a).toFloat())
                canvas.rotate(320f * u + BURST_ANGLES[i])
                canvas.scale(13f * k, 10f * k)
                canvas.drawPath(SakuraShapes.petal, burst)
                canvas.restore()
            }
        }

        // The blossom.
        canvas.drawCircle(cx, cy + 8f * k, 100f * fs, shadow)
        canvas.save()
        canvas.translate(cx - 100f * fs, cy - 100f * fs)
        canvas.scale(fs, fs)
        canvas.rotate(turn, 100f, 100f)
        canvas.drawPath(flower, petalFill)
        canvas.drawPath(flower, petalEdge)
        canvas.drawPath(stamens, stamenLine)
        for (i in 0 until 10) canvas.drawCircle(stamenDots[i * 2], stamenDots[i * 2 + 1], 2.3f, stamenDot)
        canvas.restore()

        // Its centre, where the words go.
        val r = 46f * fs
        canvas.drawCircle(cx, cy + 4f * k, r + 8f * k, centreShadow)
        canvas.drawCircle(cx, cy, r, centre)
        canvas.drawCircle(cx, cy, r - fs, centreEdge)

        // Sparkles when it's done.
        if (mode == Mode.DONE) {
            for (i in SPARKLE_ANGLES.indices) {
                val t = since - i * 70f
                if (t < 0f) continue
                val s = when {
                    t < 150f -> t / 150f * 1.25f
                    t < 300f -> 1.25f - (t - 150f) / 150f * 0.35f
                    t < 500f -> 0.9f + (t - 300f) / 200f * 0.1f
                    t < 2200f -> 1f
                    t < 2600f -> 1f - (t - 2200f) / 400f
                    else -> 0f
                }
                if (s <= 0f) continue
                val a = Math.toRadians(SPARKLE_ANGLES[i].toDouble())
                val x = cx + 100f * k * sin(a).toFloat()
                val y = cy - 100f * k * cos(a).toFloat()
                val px = (if (i % 2 == 0) 16f else 12f) * k
                canvas.drawCircle(x, y, px * 0.6f * s, sparkGlow)
                canvas.save()
                canvas.rotate(30f + min(60f, t / 30f), x, y)
                SakuraShapes.drawSparkle(canvas, x, y, px, s, spark)
                canvas.restore()
            }
        }
    }
}
