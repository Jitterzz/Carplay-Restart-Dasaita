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
