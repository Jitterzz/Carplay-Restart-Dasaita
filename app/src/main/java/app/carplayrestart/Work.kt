package app.carplayrestart

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

/** One background thread for root commands (one thing at a time), and the main thread for the screen. */
object Work {
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun background(block: () -> Unit) {
        pool.execute { block() }
    }

    fun ui(block: () -> Unit) {
        main.post { block() }
    }

    fun ui(delayMs: Long, block: () -> Unit) {
        main.postDelayed({ block() }, delayMs)
    }
}

/** How a restart or reboot went: a short message on screen, and the stats on the main screen. */
object Outcome {
    /** Set by the main screen while it's showing, so it updates right away. Called on the main thread. */
    var listener: (() -> Unit)? = null

    /**
     * [restart]: a CarPlay restart, which goes in the "Last restart" and "Today" tiles (a reboot doesn't).
     * [toast]: also say it in a message at the bottom of the screen.
     */
    fun report(ctx: Context, ok: Boolean, text: String, restart: Boolean, toast: Boolean = true) {
        if (restart) Prefs.recordRestart(ctx, ok, text)
        if (toast) {
            Toast.makeText(ctx.applicationContext, text, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        }
        listener?.invoke()
    }
}
