package app.carplayrestart

import android.content.Context
import android.content.Intent

/**
 * Restarting the CarPlay app, and the deeper fixes, the same way wherever they're started: the main screen,
 * the floating button, the home screen widget and auto-fix.
 */
object Restarter {

    enum class Result {
        DONE_ROOT,      // closed and (if wanted) opened again with root
        HELPER_STARTED, // the helper is on it; it reports back through Outcome
        NEEDS_SETUP,    // no root and no helper yet
        NO_APP,         // no CarPlay app picked or found
        FAILED,
    }

    /**
     * Closes the CarPlay app and opens it again: directly with root, otherwise through the helper.
     * [clearCache] clears its cache first; [toast] says how it went at the bottom of the screen; [auto] marks it
     * as auto-fix's doing. [done] runs on the main thread once it's finished (root) or under way (helper).
     */
    fun restart(
        ctx: Context,
        clearCache: Boolean = false,
        toast: Boolean = true,
        auto: Boolean = false,
        done: ((Result) -> Unit)? = null,
    ) {
        val app = ctx.applicationContext
        val pkg = Prefs.target(app)
        if (pkg == null || !Apps.isSafeName(pkg)) {
            done?.invoke(Result.NO_APP)
            return
        }
        // Auto-fix always opens it again: its whole point is getting CarPlay back.
        val reopen = auto || Prefs.reopen(app)
        val name = Apps.label(app, pkg)
        HelperService.instance?.autoFix?.onRestarted(auto) // give CarPlay time to come back before judging again
        Work.background {
            // With root: close it directly. Instant, and no settings screens flash up.
            var stopped = false
            if (Root.isAvailable()) {
                stopped = Root.run("am force-stop $pkg").ok
                // Its temporary files, once it's closed (its settings and paired phones aren't in there).
                if (stopped && clearCache) {
                    Root.run(
                        "rm -rf /data/data/$pkg/cache/* /data/data/$pkg/code_cache/* " +
                            "/sdcard/Android/data/$pkg/cache/*",
                        20_000,
                    )
                }
            }
            Work.ui {
                if (stopped) {
                    val text = app.getString(
                        when {
                            auto -> R.string.done_auto_restarted
                            clearCache -> R.string.done_cache_restarted
                            else -> R.string.done_restarted
                        },
                        name,
                    )
                    Outcome.report(app, true, text, restart = true, toast = toast)
                    done?.invoke(Result.DONE_ROOT)
                    // Let "Done" land for a beat, then open a fresh copy.
                    if (reopen) {
                        Work.ui(900) {
                            Apps.launchIntent(app, pkg)?.let { safeStart(app, it) }
                            HelperService.instance?.pickMode(pkg) // "Open it as": needs the helper on, root or not
                        }
                    }
                } else {
                    val helper = HelperService.instance
                    val result = when {
                        helper == null -> Result.NEEDS_SETUP
                        helper.restart(pkg, reopen, clearCache, auto) -> Result.HELPER_STARTED
                        else -> Result.FAILED
                    }
                    done?.invoke(result)
                }
            }
        }
    }

    /**
     * Turns Wi-Fi and Bluetooth off and on again, then restarts the CarPlay app. Needs root: [done] gets false
     * (on the main thread) if this radio doesn't have it.
     */
    fun resetWireless(ctx: Context, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        Work.background {
            val ok = Root.isAvailable()
            if (ok) {
                // Newer Android has "cmd"; older has "svc". Whichever this radio understands.
                Root.run("svc wifi disable || cmd wifi set-wifi-enabled disabled", 10_000)
                Root.run("cmd bluetooth_manager disable || svc bluetooth disable", 10_000)
                Thread.sleep(3_000)
                Root.run("svc wifi enable || cmd wifi set-wifi-enabled enabled", 10_000)
                Root.run("cmd bluetooth_manager enable || svc bluetooth enable", 10_000)
                Thread.sleep(4_000) // give them a moment to come back before CarPlay looks for the phone
            }
            Work.ui {
                done(ok)
                if (ok) restart(app)
            }
        }
    }

    private fun safeStart(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
