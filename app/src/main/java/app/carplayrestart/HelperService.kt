package app.carplayrestart

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The helper, for radios without root. Android doesn't let one app close another by itself, so when you press
 * Restart, the helper opens the CarPlay app's App info page, presses Force stop (and OK), then opens the app
 * again. For Reboot radio, it opens the power menu and presses Restart.
 *
 * It does nothing unless one of those is in progress: every other screen change on the radio is ignored.
 */
class HelperService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: HelperService? = null
            private set

        /** Turned on in the radio's Accessibility settings. */
        fun isOn(ctx: Context): Boolean {
            if (instance != null) return true
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(ctx, HelperService::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        // Button names to look for: English first, then Spanish and Chinese (some radios mix in Chinese).
        // A button only counts when its whole name matches, ignoring capitals.
        private val FORCE_STOP = listOf(
            "force stop", "force close", "forzar detención", "forzar detencion", "forzar cierre",
            "forzar la detención", "强行停止", "强制停止", "強制停止", "結束運作",
        )
        private val OK = listOf("ok", "aceptar", "确定", "確定", "yes", "sí", "si")
        private val RESTART = listOf("restart", "reboot", "reiniciar", "重启", "重新启动", "重新啟動", "重新開機")

        // Names of the CarPlay / Android Auto buttons inside the CarPlay app, for "Open it as".
        private val CARPLAY = listOf("carplay", "apple carplay", "apple car play", "car play", "carplay mode")
        private val ANDROID_AUTO = listOf("android auto", "androidauto", "google android auto", "android auto mode")

        private const val TICK_MS = 250L
    }

    private enum class Step { FIND_FORCE_STOP, CONFIRM_FORCE_STOP, REOPEN, FIND_RESTART, CONFIRM_RESTART, PICK_MODE }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { onTick() }
    private var step: Step? = null
    private var stepStartedAt = 0L
    private var greyedSince = 0L
    private var target = ""
    private var reopenAfter = true

    val isBusy: Boolean get() = step != null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Outcome.listener?.invoke() // the main screen can show "Ready" right away
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // A new screen or dialog just opened: look at it now instead of waiting for the next tick.
        if (step != null && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 120)
        }
    }

    override fun onInterrupt() {}

    /** Force stops [pkg] from its App info page, then opens it again if [reopen]. False if it couldn't start. */
    fun restart(pkg: String, reopen: Boolean): Boolean {
        stop()
        target = pkg
        reopenAfter = reopen
        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        return try {
            startActivity(appInfo)
            go(Step.FIND_FORCE_STOP, 500)
            true
        } catch (_: Exception) {
            Outcome.report(this, false, getString(R.string.err_no_app_info), restart = true)
            false
        }
    }

    /** After the CarPlay app [pkg] opens, presses its Apple CarPlay or Android Auto button (per "Open it as").
     *  Quiet and best effort: if the button never shows up, it just stops after a while. */
    fun pickMode(pkg: String, delayMs: Long = 1_500) {
        if (Prefs.openMode(this) == Prefs.MODE_AUTO || step != null) return
        target = pkg
        go(Step.PICK_MODE, delayMs)
    }

    /** Opens the power menu and presses Restart. False if the radio has no power menu to open. */
    fun reboot(): Boolean {
        stop()
        if (!performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) {
            Outcome.report(this, false, getString(R.string.err_no_power_menu), restart = false)
            return false
        }
        go(Step.FIND_RESTART, 600)
        return true
    }

    private fun go(next: Step, delayMs: Long = TICK_MS) {
        step = next
        stepStartedAt = SystemClock.uptimeMillis()
        greyedSince = 0L
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    private fun again() {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    private fun stop() {
        step = null
        handler.removeCallbacks(tick)
    }

    private fun onTick() {
        val now = SystemClock.uptimeMillis()
        val waited = now - stepStartedAt
        when (step ?: return) {
            Step.FIND_FORCE_STOP -> {
                val button = find(FORCE_STOP)?.let { clickableOf(it) }
                when {
                    button == null ->
                        if (waited > 10_000) fail(getString(R.string.err_no_force_stop), restart = true) else again()
                    !button.isEnabled -> {
                        // Greyed out means the app isn't running, so there's nothing to stop. Give the page a moment
                        // first, since some radios grey it out while the page is still loading.
                        if (greyedSince == 0L) greyedSince = now
                        if (now - greyedSince > 1_500) go(Step.REOPEN, 0) else again()
                    }
                    tap(button) -> go(Step.CONFIRM_FORCE_STOP, 300)
                    else -> fail(getString(R.string.err_no_force_stop), restart = true)
                }
            }

            Step.CONFIRM_FORCE_STOP -> {
                // The "Force stop?" question. Its OK button is android:id/button1 on nearly every radio.
                val ok = findId("android:id/button1", activeWindowOnly = true) ?: find(OK, activeWindowOnly = true)
                when {
                    ok != null && clickableOf(ok).isEnabled -> {
                        tap(ok)
                        go(Step.REOPEN, 700) // give the app a moment to finish closing
                    }
                    waited > 5_000 -> go(Step.REOPEN, 0) // some radios stop the app without asking
                    else -> again()
                }
            }

            Step.REOPEN -> finishRestart()

            Step.PICK_MODE -> {
                val labels = if (Prefs.openMode(this) == Prefs.MODE_CARPLAY) CARPLAY else ANDROID_AUTO
                val button = find(labels, activeWindowOnly = true, onlyPackage = target)
                when {
                    button != null && tap(button) -> stop()
                    waited > 15_000 -> stop()
                    else -> again()
                }
            }

            Step.FIND_RESTART -> {
                val button = find(RESTART)
                when {
                    button != null && tap(button) -> go(Step.CONFIRM_RESTART, 900)
                    waited > 4_000 -> {
                        performGlobalAction(GLOBAL_ACTION_BACK) // close whatever menu did open
                        fail(getString(R.string.err_no_power_menu), restart = false, bringBack = false)
                    }
                    else -> again()
                }
            }

            Step.CONFIRM_RESTART -> {
                // Most radios restart on the first press. Some ask again: press OK, or Restart once more.
                (findId("android:id/button1", activeWindowOnly = true) ?: find(RESTART, activeWindowOnly = true))?.let { tap(it) }
                stop()
                Outcome.report(this, true, getString(R.string.done_rebooting), restart = false)
            }
        }
    }

    private fun finishRestart() {
        stop()
        val name = Apps.label(this, target)
        val launch = if (reopenAfter) Apps.launchIntent(this, target) else null
        try {
            startActivity(launch ?: mainScreen())
        } catch (_: Exception) {
        }
        Outcome.report(this, true, getString(R.string.done_restarted, name), restart = true)
        if (launch != null) pickMode(target, 2_500)
    }

    /** [restart]: it was a CarPlay restart that failed (false for a reboot). */
    private fun fail(message: String, restart: Boolean, bringBack: Boolean = true) {
        stop()
        if (bringBack) {
            try {
                startActivity(mainScreen())
            } catch (_: Exception) {
            }
        }
        Outcome.report(this, false, message, restart)
    }

    private fun mainScreen() = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    // ---- Looking at the screen ----

    /** What's on screen, minus this app's own windows (its big "Restart" button mustn't be mistaken for the
     *  power menu's). */
    private fun roots(activeWindowOnly: Boolean, onlyPackage: String? = null): List<AccessibilityNodeInfo> {
        val list = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { list.add(it) }
        if (!activeWindowOnly) {
            try {
                windows.mapNotNullTo(list) { it.root }
            } catch (_: Exception) {
            }
        }
        return list.filter {
            val p = it.packageName?.toString()
            p != packageName && (onlyPackage == null || p == onlyPackage)
        }
    }

    /** The first visible button or label whose whole name is one of [labels]. */
    private fun find(labels: List<String>, activeWindowOnly: Boolean = false, onlyPackage: String? = null): AccessibilityNodeInfo? {
        var budget = 4_000 // nodes to look at, at most
        for (root in roots(activeWindowOnly, onlyPackage)) {
            val stack = mutableListOf(root)
            while (stack.isNotEmpty() && budget-- > 0) {
                val node = stack.removeAt(stack.size - 1)
                if (node.isVisibleToUser && (matches(node.text, labels) || matches(node.contentDescription, labels))) {
                    return node
                }
                for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.add(it) }
            }
        }
        return null
    }

    private fun matches(text: CharSequence?, labels: List<String>): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.toString().trim().trimEnd('?', '…', '.', '!').trim().lowercase()
        return t in labels
    }

    private fun findId(viewId: String, activeWindowOnly: Boolean = false): AccessibilityNodeInfo? {
        for (root in roots(activeWindowOnly)) {
            val hits = try {
                root.findAccessibilityNodeInfosByViewId(viewId)
            } catch (_: Exception) {
                null
            }
            hits?.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        return null
    }

    /** The button a label belongs to: the label itself if it's clickable, otherwise the nearest parent that is. */
    private fun clickableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 6) {
            if (n.isClickable) return n
            n = n.parent
            hops++
        }
        return node
    }

    private fun tap(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 6) {
            if (n.isClickable && n.isEnabled && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            hops++
        }
        // Nothing there admits to being clickable: tap the middle of it, like a finger would.
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
