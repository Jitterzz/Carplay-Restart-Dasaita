package app.carplayrestart

import android.os.SystemClock
import android.text.format.DateFormat
import java.util.Date

/**
 * Auto-fix: restarts the CarPlay app by itself when CarPlay doesn't come back (Settings › Auto-fix).
 *
 * Every few seconds the helper glances at the CarPlay app (see [HelperService.lookAt]): is it in front, and is it
 * showing CarPlay (the phone's picture), its own screen, or "Connecting…"? Then:
 *  - Dropped: CarPlay was on, and now the app has sat on its own screen for 30 seconds. Restart.
 *  - Stuck: the app has said "Connecting…" for 40 seconds. Restart.
 *  - Plugged in: a phone was plugged in, and 25 seconds later CarPlay still hasn't shown up. Restart.
 *
 * It holds back whenever it isn't sure:
 *  - nothing at all until it has seen CarPlay running in the app once, so it knows it can tell CarPlay apart;
 *  - nothing while another app is in front or someone is tapping around in the CarPlay app (its settings, say);
 *  - nothing for 45 seconds after any restart, or for a minute after the radio wakes up (CarPlay needs a moment
 *    to connect at start-up, and the radio may have slept through any timing);
 *  - one try per drop, per plug-in and per stuck spell; after 3 tries that don't bring CarPlay back it waits
 *    until CarPlay shows up again or you restart it yourself, and it never restarts more than 3 times in 10 minutes.
 */
class AutoFix(private val helper: HelperService) {

    private companion object {
        const val DROP_MS = 30_000L        // on its own screen this long after CarPlay was on
        const val STUCK_MS = 40_000L       // "Connecting…" this long
        const val PLUG_MS = 25_000L        // no CarPlay this long after plugging in
        const val PLUG_OWN_MS = 10_000L    // ...and on its own screen for at least this long
        const val PLUG_FORGET_MS = 90_000L // a plug-in older than this no longer counts
        const val QUIET_MS = 45_000L       // after any restart, give CarPlay time to come back
        const val WAKE_MS = 60_000L        // after a gap in the checks (the radio slept, or auto-fix was off)
        const val GAP_MS = 10_000L         // checks come every 3 seconds; a longer gap means they stopped
        const val WINDOW_MS = 10 * 60_000L
        const val MAX_TRIES = 3
    }

    private var lastCheckAt = 0L
    private var wasShowing = false   // CarPlay has been on since the last drop was dealt with
    private var ownSince = 0L        // on its own screen (or connecting) since; 0: it isn't
    private var connectingSince = 0L // saying "Connecting…" since; 0: it isn't
    private var pluggedAt = 0L       // a phone was plugged in at; 0: not waiting for one
    private var stuckTried = false   // already restarted for this stuck spell
    private var quietUntil = 0L
    private var misses = 0           // restarts since CarPlay was last seen
    private val fixes = mutableListOf<Long>()
    private var pausedNoted = false

    // elapsedRealtime keeps counting while the radio sleeps; the gap check below makes up for that.
    private fun now() = SystemClock.elapsedRealtime()

    /** A phone was plugged into the radio's USB. */
    fun onPhonePlugged() {
        pluggedAt = now()
        stuckTried = false // a new attempt to connect
    }

    /** Someone tapped or scrolled in the CarPlay app: they're using it, so wait. */
    fun onUserActivity() {
        val t = now()
        if (ownSince != 0L) ownSince = t
        if (connectingSince != 0L) connectingSince = t
    }

    /** Any restart just started ([auto]: auto-fix's own; otherwise a button, the floating button or the widget). */
    fun onRestarted(auto: Boolean) {
        quietUntil = now() + QUIET_MS
        ownSince = 0L
        connectingSince = 0L
        if (!auto) misses = 0 // you tried it yourself: auto-fix may try again later
    }

    /** One of auto-fix's restarts didn't go through (the helper couldn't find its buttons, say). */
    fun onFailed() {
        log(helper.getString(R.string.fix_log_failed))
    }

    /** One glance, every few seconds while auto-fix is on. */
    fun check() {
        val pkg = helper.targetPkg() ?: return
        if (helper.isBusy) return
        val t = now()
        if (lastCheckAt == 0L || t - lastCheckAt > GAP_MS) {
            // The first check, or the first after the radio slept: forget what was seen before.
            wasShowing = false
            ownSince = 0L
            connectingSince = 0L
            stuckTried = false
            if (pluggedAt != 0L && t - pluggedAt > GAP_MS) pluggedAt = 0L // keep a plug-in from just now
            quietUntil = maxOf(quietUntil, t + WAKE_MS)
        }
        lastCheckAt = t
        val look = helper.lookAt(pkg)

        if (look == HelperService.Look.SHOWING) {
            // CarPlay's picture is on screen: it's working.
            wasShowing = true
            pluggedAt = 0L
            stuckTried = false
            misses = 0
            if (Prefs.videoSeen(helper) != pkg) Prefs.setVideoSeen(helper, pkg)
        }
        val notShowing = look == HelperService.Look.OWN_SCREEN || look == HelperService.Look.CONNECTING
        if (notShowing) {
            if (ownSince == 0L) ownSince = t
        } else {
            ownSince = 0L // CarPlay is on, another app is in front, or it can't tell: nothing to judge
        }
        if (look == HelperService.Look.CONNECTING) {
            if (connectingSince == 0L) connectingSince = t
        } else {
            connectingSince = 0L
        }
        if (pluggedAt != 0L && t - pluggedAt > PLUG_FORGET_MS) pluggedAt = 0L

        // Not until it has seen CarPlay in this app once: until then it can't be sure what CarPlay looks like.
        if (Prefs.videoSeen(helper) != pkg) return
        if (t < quietUntil) return

        val own = if (ownSince == 0L) 0L else t - ownSince
        val stuck = if (connectingSince == 0L) 0L else t - connectingSince
        when {
            Prefs.autoDrop(helper) && wasShowing && own >= DROP_MS -> fix(R.string.fix_log_dropped)
            Prefs.autoDrop(helper) && !stuckTried && stuck >= STUCK_MS -> {
                stuckTried = true
                fix(R.string.fix_log_stuck)
            }
            Prefs.autoPlug(helper) && pluggedAt != 0L && t - pluggedAt >= PLUG_MS && own >= PLUG_OWN_MS ->
                fix(R.string.fix_log_plugged)
        }
    }

    private fun fix(reason: Int) {
        val t = now()
        // Whatever happens next, this drop or plug-in has had its try.
        wasShowing = false
        pluggedAt = 0L
        ownSince = 0L
        connectingSince = 0L
        fixes.removeAll { t - it > WINDOW_MS }
        if (misses >= MAX_TRIES || fixes.size >= MAX_TRIES) {
            if (!pausedNoted) {
                pausedNoted = true
                log(helper.getString(R.string.fix_log_paused, MAX_TRIES))
            }
            return
        }
        pausedNoted = false
        fixes.add(t)
        misses++
        quietUntil = t + QUIET_MS
        log(helper.getString(reason))
        Restarter.restart(helper, toast = true, auto = true) { result ->
            if (result == Restarter.Result.FAILED) onFailed()
        }
    }

    private fun log(text: String) {
        val time = DateFormat.getTimeFormat(helper).format(Date())
        Prefs.addFixLog(helper, "$time · $text")
    }
}
