package app.carplayrestart

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView

/** The looks you can pick in Settings › Look. Each has its own backdrop, Restart button, colors and fonts. */
enum class ThemeId { AURORA, REDLINE, NEON, WARP, MIDNIGHT, SAKURA }

/** A font: one shipped as a font resource, or a variable font from assets/fonts at a given weight. */
class Face private constructor(val res: Int, val asset: String?, val weight: Int) {
    val key: String get() = if (asset != null) "$asset@$weight" else "res$res"

    companion object {
        fun res(id: Int) = Face(id, null, 0)
        fun asset(name: String, weight: Int) = Face(0, "fonts/$name.ttf", weight)
    }
}

/** display: big words and numbers; strong: titles; body: everything else; label: small capitals; hero: on the button. */
class FontSet(val display: Face, val strong: Face, val body: Face, val label: Face, val hero: Face)

class CardStyle(
    val fill: Int,
    val fill2: Int,      // the bottom of the fill (the same as fill for a flat card)
    val edge: Int,       // 0: the light-catching glass edge
    val radiusDp: Float,
    val sheen: Boolean,  // a soft highlight across the top
    val glow: Int,       // a soft halo around the card, 0 for none
)

class HoldStyle(
    val fill: Int,
    val stroke: Int,
    val stroke2: Int,
    val progress: Int,
    val progress2: Int,
    val text: Int,
    val icon: Int,
    val radiusDp: Float,
    val strokeDp: Float,
)

/** How the words sit on the Restart button. Sizes are shares of the button's size. */
class HeroText(
    val width: Float,
    val label: Float,
    val sub: Float,
    val icon: Float,
    val caps: Boolean,
    val subCaps: Boolean,
    val spacing: Float,
    val subSpacing: Float,
    val labelColor: Int,
    val subColor: Int,
    val quietIcon: Boolean, // the icon only shows when it's done (or didn't work)
)

class AppTheme(
    val id: ThemeId,
    val name: Int,
    val tagline: Int,
    val light: Boolean,
    val bg: Int,
    val text1: Int,
    val text2: Int,
    val text3: Int,
    val accent: Int,
    val pill: Int,
    val pill2: Int,
    val onPill: Int,
    val ok: Int,
    val okText: Int,
    val warn: Int,
    val idle: Int,
    val field: Int,     // round buttons, icon tiles and unselected choices
    val fieldEdge: Int,
    val card: CardStyle,
    val hold: HoldStyle,
    val fonts: FontSet,
    val heroText: HeroText,
    val heroMaxDp: Int,
    val doneIcon: Int,
    val swatch: IntArray, // for Settings › Look: sky top, sky bottom, button, button edge
    val makeHero: (Context) -> HeroView,
    val makeBackdrop: (Context) -> BackdropView,
)

object Themes {

    private fun c(v: Long): Int = v.toInt()

    fun current(ctx: Context): AppTheme = get(Prefs.theme(ctx))

    fun get(id: ThemeId): AppTheme = all.firstOrNull { it.id == id } ?: all[0]

    val all: List<AppTheme> by lazy {
        listOf(aurora(), redline(), neon(), warp(), midnight(), sakura())
    }

    private fun aurora() = AppTheme(
        id = ThemeId.AURORA, name = R.string.theme_aurora, tagline = R.string.theme_aurora_sub, light = false,
        bg = c(0xFF060912), text1 = c(0xFFF5F7FF), text2 = c(0xFF9AA8BF), text3 = c(0xFF64748B),
        accent = c(0xFF60A5FA), pill = c(0xFF3B82F6), pill2 = c(0xFF8B5CF6), onPill = Color.WHITE,
        ok = c(0xFF34D399), okText = c(0xFF34D399), warn = c(0xFFFBBF24), idle = c(0xFF64748B),
        field = 0x14FFFFFF, fieldEdge = 0x29FFFFFF,
        card = CardStyle(0x12FFFFFF, 0x12FFFFFF, 0, 22f, true, 0),
        hold = HoldStyle(
            0x14FBBF24, c(0xFFFBBF24), c(0xE6FB923C), 0x40FBBF24, c(0x99F97316),
            c(0xFFFCD34D), c(0xFFFBBF24), 20f, 1.6f,
        ),
        fonts = FontSet(
            Face.res(R.font.sora_semibold), Face.res(R.font.sora_semibold), Face.res(R.font.sora_regular),
            Face.res(R.font.sora_semibold), Face.res(R.font.sora_bold),
        ),
        heroText = HeroText(
            0.48f, 0.079f, 0.032f, 0.163f, caps = true, subCaps = true, spacing = 0.12f, subSpacing = 0.22f,
            labelColor = Color.WHITE, subColor = c(0xD9D6E4FF), quietIcon = false,
        ),
        heroMaxDp = 380, doneIcon = R.drawable.ic_check,
        swatch = intArrayOf(c(0xFF060912), c(0xFF0B1328), c(0xFF3B82F6), c(0xFF67E8F9)),
        makeHero = { OrbView(it) }, makeBackdrop = { AuroraView(it) },
    )

    private fun redline() = AppTheme(
        id = ThemeId.REDLINE, name = R.string.theme_redline, tagline = R.string.theme_redline_sub, light = false,
        bg = c(0xFF0B0C0E), text1 = c(0xFFF4F4F5), text2 = c(0xFFA1A1AA), text3 = c(0xFF9CA3AF),
        accent = c(0xFFFF6B61), pill = c(0xFFFF3B30), pill2 = c(0xFFB91C1C), onPill = Color.WHITE,
        ok = c(0xFF22C55E), okText = c(0xFF4ADE80), warn = c(0xFFF59E0B), idle = c(0xFF71717A),
        field = c(0xFF141519), fieldEdge = c(0xFF2A2B30),
        card = CardStyle(c(0xFF15161A), c(0xFF101114), c(0xFF26272C), 14f, false, 0),
        hold = HoldStyle(
            0x1FFF3B30, c(0xB3FF3B30), c(0xB3FF3B30), 0x40FF3B30, c(0x99F97316),
            c(0xFFFFC9C4), c(0xFFFF6B61), 14f, 1.5f,
        ),
        fonts = FontSet(
            Face.res(R.font.saira_sc_bold), Face.res(R.font.saira_sc_semibold), Face.res(R.font.saira_sc_medium),
            Face.res(R.font.saira_sc_semibold), Face.res(R.font.saira_sc_bold),
        ),
        heroText = HeroText(
            0.34f, 0.056f, 0.031f, 0.075f, caps = true, subCaps = true, spacing = 0.14f, subSpacing = 0.26f,
            labelColor = c(0xFFF4F4F5), subColor = c(0xFFA1A1AA), quietIcon = false,
        ),
        heroMaxDp = 330, doneIcon = R.drawable.ic_check,
        swatch = intArrayOf(c(0xFF0B0C0E), c(0xFF15161A), c(0xFF1C1D21), c(0xFFFF3B30)),
        makeHero = { RedlineHero(it) }, makeBackdrop = { RedlineBackdrop(it) },
    )

    private fun neon() = AppTheme(
        id = ThemeId.NEON, name = R.string.theme_neon, tagline = R.string.theme_neon_sub, light = false,
        bg = c(0xFF07001A), text1 = c(0xFFF5F3FF), text2 = c(0xFFD8B4FE), text3 = c(0xFFF9A8D4),
        accent = c(0xFF2DE2E6), pill = c(0xFFFF2975), pill2 = c(0xFFB31AFF), onPill = Color.WHITE,
        ok = c(0xFF5EEAD4), okText = c(0xFF5EEAD4), warn = c(0xFFFFE45E), idle = c(0xFF8B7FA8),
        field = c(0x9907001A), fieldEdge = c(0x992DE2E6),
        card = CardStyle(c(0xEB09011C), c(0xEB09011C), c(0x8CFF4FA0), 12f, false, 0x2EFF2975),
        hold = HoldStyle(
            c(0xFF201526), c(0xFFFFE45E), c(0xFFFFE45E), 0x40FFE45E, c(0x99FFB238),
            c(0xFFFFF3B0), c(0xFFFFE45E), 12f, 1.5f,
        ),
        fonts = FontSet(
            Face.res(R.font.audiowide), Face.asset("outfit", 600), Face.asset("outfit", 400),
            Face.res(R.font.audiowide), Face.res(R.font.audiowide),
        ),
        heroText = HeroText(
            0.62f, 0.105f, 0.055f, 0.13f, caps = true, subCaps = true, spacing = 0f, subSpacing = 0.32f,
            labelColor = c(0xFFF0FEFF), subColor = c(0xFFA5F3FC), quietIcon = false,
        ),
        heroMaxDp = 230, doneIcon = R.drawable.ic_check,
        swatch = intArrayOf(c(0xFF07001A), c(0xFF2A0A4A), c(0xFFFF5C8A), c(0xFF2DE2E6)),
        makeHero = { NeonHero(it) }, makeBackdrop = { NeonBackdrop(it) },
    )

    private fun warp() = AppTheme(
        id = ThemeId.WARP, name = R.string.theme_warp, tagline = R.string.theme_warp_sub, light = false,
        bg = c(0xFF04060F), text1 = c(0xFFEEF3FF), text2 = c(0xFFA9B8DA), text3 = c(0xFFA9B8DA),
        accent = c(0xFF67E8F9), pill = c(0xFF0891B2), pill2 = c(0xFF7C3AED), onPill = Color.WHITE,
        ok = c(0xFF67E8F9), okText = c(0xFF67E8F9), warn = c(0xFFFDBA74), idle = c(0xFF64748B),
        field = c(0x99070B1E), fieldEdge = c(0x59A9B8DA),
        card = CardStyle(c(0xDB0A1028), c(0xDB0A1028), c(0x388CAAFF), 16f, false, 0),
        hold = HoldStyle(
            c(0xFF1D1E2E), c(0xB3FDBA74), c(0xB3FDBA74), 0x40FDBA74, c(0x99F97316),
            c(0xFFFED7AA), c(0xFFFDBA74), 16f, 1.5f,
        ),
        fonts = FontSet(
            Face.asset("spacegrotesk", 700), Face.asset("spacegrotesk", 600), Face.asset("spacegrotesk", 400),
            Face.asset("spacegrotesk", 600), Face.asset("spacegrotesk", 700),
        ),
        heroText = HeroText(
            0.5f, 0.091f, 0.046f, 0.104f, caps = true, subCaps = true, spacing = 0.08f, subSpacing = 0.3f,
            labelColor = c(0xFFEEF3FF), subColor = c(0xFFA9B8DA), quietIcon = false,
        ),
        heroMaxDp = 250, doneIcon = R.drawable.ic_check,
        swatch = intArrayOf(c(0xFF04060F), c(0xFF0E1B44), c(0xFF14214A), c(0xFF67E8F9)),
        makeHero = { WarpHero(it) }, makeBackdrop = { WarpBackdrop(it) },
    )

    private fun midnight() = AppTheme(
        id = ThemeId.MIDNIGHT, name = R.string.theme_midnight, tagline = R.string.theme_midnight_sub, light = false,
        bg = c(0xFF0A0A0C), text1 = c(0xFFF4F1EA), text2 = c(0xFFA8A29E), text3 = c(0xFFA8A29E),
        accent = c(0xFFE7C48A), pill = c(0xFFE7C48A), pill2 = c(0xFFD4A95E), onPill = c(0xFF1A1408),
        ok = c(0xFF8FD9B6), okText = c(0xFF8FD9B6), warn = c(0xFFE8A87C), idle = c(0xFF78716C),
        field = 0x00000000, fieldEdge = 0x29F4F1EA,
        card = CardStyle(0x08FFFFFF, 0x08FFFFFF, 0x1AF4F1EA, 18f, false, 0),
        hold = HoldStyle(
            0x00000000, c(0x73E7C48A), c(0x73E7C48A), 0x26E7C48A, 0x66E7C48A,
            c(0xFFE7C48A), c(0xFFE7C48A), 26f, 1f,
        ),
        fonts = FontSet(
            Face.asset("manrope", 300), Face.asset("manrope", 600), Face.asset("manrope", 500),
            Face.asset("manrope", 600), Face.asset("manrope", 300),
        ),
        heroText = HeroText(
            0.62f, 0.142f, 0.046f, 0.125f, caps = false, subCaps = true, spacing = -0.01f, subSpacing = 0.32f,
            labelColor = c(0xFFF4F1EA), subColor = c(0xFFA8A29E), quietIcon = true,
        ),
        heroMaxDp = 260, doneIcon = R.drawable.ic_check,
        swatch = intArrayOf(c(0xFF0A0A0C), c(0xFF141416), c(0xFF18181B), c(0xFFE7C48A)),
        makeHero = { MidnightHero(it) }, makeBackdrop = { MidnightBackdrop(it) },
    )

    private fun sakura() = AppTheme(
        id = ThemeId.SAKURA, name = R.string.theme_sakura, tagline = R.string.theme_sakura_sub, light = true,
        bg = c(0xFFFFE4EF), text1 = c(0xFF4A1F3D), text2 = c(0xFF8A4F74), text3 = c(0xFF9C3F78),
        accent = c(0xFFC2185B), pill = c(0xFFFF9CC2), pill2 = c(0xFFF06292), onPill = Color.WHITE,
        ok = c(0xFF2FB383), okText = c(0xFF157A55), warn = c(0xFFC2410C), idle = c(0xFFB48AA6),
        field = Color.WHITE, fieldEdge = 0x00FFFFFF,
        card = CardStyle(c(0xC2FFFFFF), c(0xC2FFFFFF), c(0xF2FFFFFF), 22f, false, 0x1FC2185B),
        hold = HoldStyle(
            c(0xE0FFFFFF), c(0xFFF06292), c(0xFFF06292), 0x33F06292, c(0x80E64C8C),
            c(0xFFC2185B), c(0xFFE64C8C), 27f, 2f,
        ),
        fonts = FontSet(
            Face.asset("fredoka", 600), Face.asset("fredoka", 600), Face.asset("nunito", 600),
            Face.asset("nunito", 800), Face.asset("fredoka", 600),
        ),
        heroText = HeroText(
            0.4f, 0.095f, 0.041f, 0.082f, caps = false, subCaps = true, spacing = 0f, subSpacing = 0.22f,
            labelColor = c(0xFFC2185B), subColor = c(0xFF9C3F78), quietIcon = true,
        ),
        heroMaxDp = 240, doneIcon = R.drawable.ic_heart,
        swatch = intArrayOf(c(0xFFFFEAF3), c(0xFFF5B7D3), c(0xFFFFC9DE), c(0xFFF06292)),
        makeHero = { SakuraHero(it) }, makeBackdrop = { SakuraBackdrop(it) },
    )
}

/** Loads the theme fonts once and keeps them. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    fun get(ctx: Context, face: Face): Typeface {
        cache[face.key]?.let { return it }
        val tf = load(ctx, face)
        cache[face.key] = tf
        return tf
    }

    private fun load(ctx: Context, face: Face): Typeface = try {
        val asset = face.asset
        if (asset != null) {
            Typeface.Builder(ctx.assets, asset)
                .setFontVariationSettings("'wght' ${face.weight}")
                .setWeight(face.weight)
                .build() ?: Typeface.DEFAULT
        } else {
            ctx.resources.getFont(face.res)
        }
    } catch (_: Exception) {
        Typeface.DEFAULT
    }
}

/**
 * Paints a screen in a theme. Views say what they are with a tag in the layout:
 *  - "display|t1", "strong|t2", "body|t2", "label|t3"…: a text's font role, then its color
 *    (t1, t2, t3, accent, ok, warn, hold);
 *  - "icon|t2": an icon's tint; "pill", "pillwarn", "circle", "tile", "logo": a background ("circle|t2" and
 *    "tile|accent" tint the icon on it too); "switch"; "hairline": a divider.
 * Glass cards and the hold button are styled wherever they are.
 */
object Styler {

    fun apply(root: View, t: AppTheme) = walk(root, t)

    private fun walk(v: View, t: AppTheme) {
        if (v is GlassLayout) v.setStyle(t.card)
        if (v is HoldButton) v.setStyle(t.hold)
        (v.tag as? String)?.let { tagged(v, it, t) }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), t)
    }

    fun color(t: AppTheme, role: String): Int = when (role) {
        "t2" -> t.text2
        "t3" -> t.text3
        "accent" -> t.accent
        "ok" -> t.okText
        "warn" -> t.warn
        "hold" -> t.hold.text
        "holdicon" -> t.hold.icon
        "pill" -> t.onPill
        else -> t.text1
    }

    private fun face(t: AppTheme, role: String): Face? = when (role) {
        "display" -> t.fonts.display
        "strong" -> t.fonts.strong
        "body" -> t.fonts.body
        "label" -> t.fonts.label
        "hero" -> t.fonts.hero
        else -> null
    }

    private fun tagged(v: View, tag: String, t: AppTheme) {
        val parts = tag.split('|')
        val kind = parts[0]
        val role = if (parts.size > 1) parts[1] else "t1"
        val ctx = v.context
        when (kind) {
            "icon" -> (v as? ImageView)?.imageTintList = ColorStateList.valueOf(color(t, role))
            "pill" -> {
                v.background = pill(ctx, t)
                (v as? TextView)?.let {
                    it.setTextColor(t.onPill)
                    it.typeface = Fonts.get(ctx, t.fonts.strong)
                }
            }
            "pillwarn" -> {
                v.background = ripple(ctx, t, rounded(ctx, t.warn, 100f))
                (v as? TextView)?.let {
                    it.setTextColor(onColor(t.warn))
                    it.typeface = Fonts.get(ctx, t.fonts.strong)
                }
            }
            "circle" -> {
                v.background = circle(ctx, t)
                if (parts.size > 1) (v as? ImageView)?.imageTintList = ColorStateList.valueOf(color(t, role))
            }
            "tile" -> {
                v.background = GradientDrawable().apply {
                    cornerRadius = 14 * dp(ctx)
                    setColor(t.field)
                    setStroke(dp(ctx).toInt().coerceAtLeast(1), t.fieldEdge)
                }
                if (parts.size > 1) (v as? ImageView)?.imageTintList = ColorStateList.valueOf(color(t, role))
            }
            "logo" -> {
                v.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, intArrayOf(t.pill, t.pill2),
                ).apply { shape = GradientDrawable.OVAL }
                (v as? ImageView)?.imageTintList = ColorStateList.valueOf(t.onPill)
                (v as? TextView)?.let {
                    it.setTextColor(t.onPill)
                    it.typeface = Fonts.get(ctx, t.fonts.strong)
                }
            }
            "switch" -> (v as? Switch)?.let { styleSwitch(it, t) }
            "hairline" -> v.setBackgroundColor(withAlpha(t.text1, if (t.light) 0.14f else 0.1f))
            else -> {
                val f = face(t, kind) ?: return
                (v as? TextView)?.let {
                    it.typeface = Fonts.get(ctx, f)
                    it.setTextColor(color(t, role))
                }
            }
        }
    }

    private fun dp(ctx: Context) = ctx.resources.displayMetrics.density

    /** White or near-black, whichever reads better on [bg]. */
    fun onColor(bg: Int): Int {
        val l = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0
        return if (l > 0.6) 0xFF1A1204.toInt() else Color.WHITE
    }

    private fun rounded(ctx: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        cornerRadius = radiusDp * dp(ctx)
        setColor(color)
    }

    fun ripple(ctx: Context, t: AppTheme, content: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf(if (t.light) 0x33C2185B else 0x33FFFFFF), content, null)

    fun pill(ctx: Context, t: AppTheme): Drawable = ripple(
        ctx, t,
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(t.pill, t.pill2)).apply {
            cornerRadius = 100 * dp(ctx)
        },
    )

    fun choiceOff(ctx: Context, t: AppTheme): Drawable = ripple(
        ctx, t,
        GradientDrawable().apply {
            cornerRadius = 100 * dp(ctx)
            setColor(if (t.light) 0xB3FFFFFF.toInt() else withAlpha(t.text1, 0.08f))
            setStroke(dp(ctx).toInt().coerceAtLeast(1), if (t.light) 0xFFFFFFFF.toInt() else withAlpha(t.text1, 0.16f))
        },
    )

    fun circle(ctx: Context, t: AppTheme): Drawable = ripple(
        ctx, t,
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(t.field)
            setStroke(dp(ctx).toInt().coerceAtLeast(1), t.fieldEdge)
        },
    )

    /** The highlighted row in Settings' side menu. */
    fun navOn(ctx: Context, t: AppTheme): Drawable = GradientDrawable().apply {
        cornerRadius = 14 * dp(ctx)
        setColor(withAlpha(t.accent, if (t.light) 0.14f else 0.16f))
    }

    /** The window behind a screen, and the bars above and below it, in the look's background. */
    fun window(a: Activity, t: AppTheme) {
        val w = a.window
        w.setBackgroundDrawable(ColorDrawable(t.bg))
        @Suppress("DEPRECATION")
        w.statusBarColor = t.bg
        @Suppress("DEPRECATION")
        w.navigationBarColor = t.bg
        if (Build.VERSION.SDK_INT >= 30) {
            val dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (t.light) dark else 0, dark)
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = if (t.light) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
        }
    }

    /** A nearly solid version of the look's background, for dialogs. */
    fun dialogFill(t: AppTheme): Int =
        if (t.light) 0xFAFFFFFF.toInt() else (lerpColor(t.bg, t.text1, 0.07f) and 0x00FFFFFF) or 0xF5000000.toInt()

    /** The setup card's look: a solid card (the screen behind it is dimmed) with the accent edge. */
    fun dialogCard(t: AppTheme) =
        CardStyle(dialogFill(t), dialogFill(t), withAlpha(t.accent, 0.45f), t.card.radiusDp + 6f, false, 0)

    /** An ordinary dialog (the app picker, the reboot question) in light or dark to suit the look. */
    fun dialog(ctx: Context, t: AppTheme): AlertDialog.Builder =
        AlertDialog.Builder(ctx, if (t.light) R.style.Dialog_CarPlayRestart_Light else R.style.Dialog_CarPlayRestart)

    /** Gives a dialog from [dialog] the look's card, fonts and button color. Call after show(). */
    fun dress(d: AlertDialog, t: AppTheme) {
        val ctx = d.context
        val px = dp(ctx)
        val card = GradientDrawable().apply {
            cornerRadius = 26 * px
            setColor(dialogFill(t))
            setStroke(px.toInt().coerceAtLeast(1), withAlpha(t.accent, 0.35f))
        }
        d.window?.setBackgroundDrawable(
            InsetDrawable(card, (20 * px).toInt(), (16 * px).toInt(), (20 * px).toInt(), (16 * px).toInt()),
        )
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
            d.getButton(which)?.let {
                it.setTextColor(t.accent)
                it.typeface = Fonts.get(ctx, t.fonts.strong)
            }
        }
        d.findViewById<TextView>(android.R.id.message)?.let {
            it.typeface = Fonts.get(ctx, t.fonts.body)
            it.setTextColor(t.text2)
        }
    }

    fun styleSwitch(s: Switch, t: AppTheme) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.thumbTintList = ColorStateList(states, intArrayOf(t.accent, if (t.light) Color.WHITE else 0xFFCBD5E1.toInt()))
        s.trackTintList = ColorStateList(
            states,
            intArrayOf(withAlpha(t.accent, 0.55f), if (t.light) 0x33000000 else 0x33FFFFFF),
        )
    }
}
