package app.carplayrestart

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The few things the app remembers. */
object Prefs {

    private fun sp(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The CarPlay app to restart: the one picked in Settings, or else ZLink (or another CarPlay app) on the radio. */
    fun target(ctx: Context): String? {
        val picked = sp(ctx).getString("target", null)
        if (picked != null && Apps.isInstalled(ctx, picked)) return picked
        return Apps.detect(ctx)
    }

    fun setTarget(ctx: Context, pkg: String) {
        sp(ctx).edit().putString("target", pkg).apply()
    }

    /** Open the CarPlay app again after closing it (on unless turned off in Settings). */
    fun reopen(ctx: Context): Boolean = sp(ctx).getBoolean("reopen", true)

    fun setReopen(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("reopen", on).apply()
    }

    const val MODE_AUTO = 0
    const val MODE_CARPLAY = 1
    const val MODE_ANDROID_AUTO = 2

    /** Which side to open after a restart: Auto lets the CarPlay app choose, or Apple CarPlay / Android Auto. */
    fun openMode(ctx: Context): Int = sp(ctx).getInt("open_mode", MODE_AUTO).coerceIn(MODE_AUTO, MODE_ANDROID_AUTO)

    fun setOpenMode(ctx: Context, mode: Int) {
        sp(ctx).edit().putInt("open_mode", mode).apply()
    }

    /** The look picked in Settings › Look. */
    fun theme(ctx: Context): ThemeId = try {
        ThemeId.valueOf(sp(ctx).getString("theme", null) ?: ThemeId.AURORA.name)
    } catch (_: Exception) {
        ThemeId.AURORA
    }

    fun setTheme(ctx: Context, id: ThemeId) {
        sp(ctx).edit().putString("theme", id.name).apply()
    }

    /** The floating restart button over the CarPlay app (off unless turned on), and where it was left. */
    fun bubble(ctx: Context): Boolean = sp(ctx).getBoolean("bubble", false)

    fun setBubble(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("bubble", on).apply()
    }

    fun bubbleX(ctx: Context): Int = sp(ctx).getInt("bubble_x", -1)
    fun bubbleY(ctx: Context): Int = sp(ctx).getInt("bubble_y", -1)

    fun setBubblePos(ctx: Context, x: Int, y: Int) {
        sp(ctx).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
    }

    /** Auto-fix (off until turned on), and its two rules (on). */
    fun autoFix(ctx: Context): Boolean = sp(ctx).getBoolean("auto_fix", false)
    fun autoDrop(ctx: Context): Boolean = sp(ctx).getBoolean("auto_drop", true)
    fun autoPlug(ctx: Context): Boolean = sp(ctx).getBoolean("auto_plug", true)

    fun setAutoFix(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("auto_fix", on).apply()
    }

    fun setAutoDrop(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("auto_drop", on).apply()
    }

    fun setAutoPlug(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("auto_plug", on).apply()
    }

    /** The CarPlay app auto-fix has seen showing CarPlay at least once (it only acts after that). */
    fun videoSeen(ctx: Context): String? = sp(ctx).getString("video_seen", null)

    fun setVideoSeen(ctx: Context, pkg: String) {
        sp(ctx).edit().putString("video_seen", pkg).apply()
    }

    /** What auto-fix saw and did lately, newest first (a few lines). */
    fun fixLog(ctx: Context): List<String> =
        (sp(ctx).getString("fix_log", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addFixLog(ctx: Context, line: String) {
        val list = (listOf(line) + fixLog(ctx)).take(6)
        sp(ctx).edit().putString("fix_log", list.joinToString("\n")).apply()
    }

    class Last(val time: Long, val ok: Boolean, val text: String)

    /** How the last CarPlay restart went, for the "Last restart" tile. */
    fun last(ctx: Context): Last? {
        val p = sp(ctx)
        val time = p.getLong("last_time", 0L)
        if (time == 0L) return null
        return Last(time, p.getBoolean("last_ok", true), p.getString("last_text", "") ?: "")
    }

    /** Notes a CarPlay restart: when, whether it worked, and one more for today's count if it did. */
    fun recordRestart(ctx: Context, ok: Boolean, text: String) {
        val p = sp(ctx)
        val today = dayKey()
        val count = if (p.getString("count_day", null) == today) p.getInt("count", 0) else 0
        p.edit()
            .putLong("last_time", System.currentTimeMillis())
            .putBoolean("last_ok", ok)
            .putString("last_text", text)
            .putString("count_day", today)
            .putInt("count", if (ok) count + 1 else count)
            .apply()
    }

    /** Restarts that worked today (since midnight). */
    fun restartsToday(ctx: Context): Int {
        val p = sp(ctx)
        return if (p.getString("count_day", null) == dayKey()) p.getInt("count", 0) else 0
    }

    private fun dayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
