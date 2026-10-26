package app.carplayrestart

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

/** The glass "Turn on the helper" card, and the way into the radio's Accessibility settings. */
object SetupDialog {

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = Dialog(activity, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_setup)

        val app = Prefs.target(activity)?.let { Apps.label(activity, it) }
            ?: activity.getString(R.string.the_carplay_app)
        dialog.findViewById<TextView>(R.id.setup_body).text = activity.getString(R.string.setup_body, app)
        dialog.findViewById<View>(R.id.setup_open).setOnClickListener {
            dialog.dismiss()
            openAccessibilitySettings(activity)
        }
        dialog.findViewById<View>(R.id.setup_later).setOnClickListener { dialog.dismiss() }

        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val metrics = activity.resources.displayMetrics
            // The wide-screen card is two columns, so it can be wider and still short enough for the radio.
            val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val maxDp = if (landscape) 760 else 620
            val width = min((metrics.widthPixels * 0.92f).toInt(), (maxDp * metrics.density).toInt())
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    fun openAccessibilitySettings(activity: Activity) {
        // Some radios replace Android's settings app; fall back to whatever settings screen they have.
        val tries = listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (intent in tries) {
            try {
                activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(activity, R.string.err_no_settings, Toast.LENGTH_LONG).show()
    }
}
