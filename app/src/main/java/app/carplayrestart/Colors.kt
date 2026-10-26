package app.carplayrestart

import kotlin.math.roundToInt

/** [color] with its opacity set to [alpha] (0 to 1). */
internal fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

/** Mixes [from] toward [to] by [t] (0 to 1), channel by channel, alpha included. */
internal fun lerpColor(from: Int, to: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val a = (from ushr shift) and 0xFF
        val b = (to ushr shift) and 0xFF
        return (a + (b - a) * f).roundToInt() and 0xFF
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
