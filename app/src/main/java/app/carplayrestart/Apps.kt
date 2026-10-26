package app.carplayrestart

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Finds the radio's CarPlay app (ZLink on Dasaita radios) and the other apps you can pick instead. */
object Apps {

    /** Apps known to run CarPlay and Android Auto on Android head units, most likely first. */
    private val KNOWN = listOf(
        "com.zjinnova.zlink", // ZLink: comes on Dasaita radios (and Joying, Atoto and others)
        "com.carletter.car",  // CarLetter: used instead of ZLink on some radios
    )

    /** Words in an app's name that give it away as a CarPlay app, for radios with something other than ZLink. */
    private val HINTS = listOf("zlink", "tlink", "carplay", "car play", "autokit", "carlink", "carletter")

    class App(val pkg: String, val label: String)

    private val SAFE_NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    /** A package name only ever goes into a root command after passing this. */
    fun isSafeName(pkg: String) = SAFE_NAME.matches(pkg)

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    fun icon(ctx: Context, pkg: String): Drawable? = try {
        ctx.packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }

    /** Opens the app the way its home-screen icon does. */
    fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

    /** Every app with a home-screen icon except this one: likely CarPlay apps first, then A to Z. */
    fun launchable(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(home, 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<App>({ !looksLikeCarPlay(it) }, { it.label.lowercase() }))
    }

    /** ZLink if it's there, otherwise the first app that looks like a CarPlay app, otherwise null. */
    fun detect(ctx: Context): String? {
        KNOWN.firstOrNull { isInstalled(ctx, it) }?.let { return it }
        return launchable(ctx).firstOrNull { looksLikeCarPlay(it) }?.pkg
    }

    private fun looksLikeCarPlay(app: App): Boolean {
        if (app.pkg in KNOWN) return true
        val name = app.label.lowercase()
        val pkg = app.pkg.lowercase()
        return HINTS.any { name.contains(it) || pkg.contains(it.replace(" ", "")) }
    }
}
