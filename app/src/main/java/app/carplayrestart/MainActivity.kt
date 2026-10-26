package app.carplayrestart

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.Date

/** The one screen that matters: the glowing Restart orb for CarPlay, and Hold to reboot radio for when that's not enough. */
class MainActivity : Activity() {

    private lateinit var orb: OrbView
    private lateinit var orbIcon: ImageView
    private lateinit var orbLabel: TextView
    private lateinit var orbSub: TextView
    private lateinit var appCard: View
    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appSub: TextView
    private lateinit var statusCard: GlassLayout
    private lateinit var statusDot: PulseDot
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var setupButton: View
    private lateinit var statsRow: View
    private lateinit var lastValue: TextView
    private lateinit var lastSub: TextView
    private lateinit var todayValue: TextView
    private lateinit var todayUnit: TextView
    private lateinit var holdButton: HoldButton

    private val main = Handler(Looper.getMainLooper())
    private val settle = Runnable { showOrb(OrbView.Mode.IDLE) }
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        orb = findViewById(R.id.orb)
        orbIcon = findViewById(R.id.orb_icon)
        orbLabel = findViewById(R.id.orb_label)
        orbSub = findViewById(R.id.orb_sub)
        appCard = findViewById(R.id.app_card)
        appIcon = findViewById(R.id.app_icon)
        appName = findViewById(R.id.app_name)
        appSub = findViewById(R.id.app_sub)
        statusCard = findViewById(R.id.status_card)
        statusDot = findViewById(R.id.status_dot)
        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        setupButton = findViewById(R.id.setup_button)
        statsRow = findViewById(R.id.stats_row)
        lastValue = findViewById(R.id.last_value)
        lastSub = findViewById(R.id.last_sub)
        todayValue = findViewById(R.id.today_value)
        todayUnit = findViewById(R.id.today_unit)
        holdButton = findViewById(R.id.hold_button)

        orb.setOnClickListener { restartCarPlay() }
        setupButton.setOnClickListener { SetupDialog.show(this) }
        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        holdButton.onConfirmed = { reboot() }
        holdButton.onShortTap = { Toast.makeText(this, R.string.hold_hint, Toast.LENGTH_SHORT).show() }
        holdButton.setOnClickListener { confirmReboot() } // TalkBack and other accessibility clicks can't hold

        showOrb(OrbView.Mode.IDLE)
        if (savedInstanceState == null) playEntrance()

        // Find out whether this radio has root: it decides how restarts happen.
        Work.background {
            Root.isAvailable()
            Work.ui { if (!isFinishing) refresh() }
        }
    }

    override fun onResume() {
        super.onResume()
        Outcome.listener = { onOutcome() }
        // Back from a helper run (it opened ZLink over this screen): show how it went.
        if (busy && HelperService.instance?.isBusy != true) onOutcome()
        holdButton.reset()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Outcome.listener = null
    }

    override fun onDestroy() {
        main.removeCallbacks(settle)
        super.onDestroy()
    }

    // ---- Restart CarPlay ----

    private fun restartCarPlay() {
        if (busy) return
        val pkg = Prefs.target(this)
        if (pkg == null || !Apps.isSafeName(pkg)) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PICK, true))
            return
        }
        busy = true
        showOrb(OrbView.Mode.WORKING)
        val reopen = Prefs.reopen(this)
        val name = Apps.label(this, pkg)
        Work.background {
            // With root: close it directly. Instant, and no settings screens flash up.
            val stopped = Root.isAvailable() && Root.run("am force-stop $pkg").ok
            Work.ui {
                val helper = HelperService.instance
                when {
                    stopped -> {
                        busy = false
                        Outcome.report(this, true, getString(R.string.done_restarted, name), restart = true, toast = false)
                        showOrb(OrbView.Mode.DONE, settleMs = 2600)
                        // Let the green "Done" land for a beat, then open a fresh copy.
                        if (reopen) Work.ui(900) { Apps.launchIntent(this, pkg)?.let { safeStart(it) } }
                    }
                    helper == null -> {
                        // No root and no helper yet: explain the one-time setup.
                        busy = false
                        showOrb(OrbView.Mode.IDLE)
                        SetupDialog.show(this)
                    }
                    // The helper presses Force stop for us, then reports back (see onOutcome).
                    !helper.restart(pkg, reopen) -> {
                        busy = false
                        showOrb(OrbView.Mode.ERROR, settleMs = 2600)
                    }
                    else -> Unit // the helper is on it
                }
            }
        }
    }

    /** A restart or reboot finished (or the helper just switched on). */
    private fun onOutcome() {
        holdButton.reset()
        if (busy && HelperService.instance?.isBusy != true) {
            busy = false
            val last = Prefs.last(this)
            if (last != null && System.currentTimeMillis() - last.time < 30_000) {
                showOrb(if (last.ok) OrbView.Mode.DONE else OrbView.Mode.ERROR, settleMs = 2600)
            } else {
                showOrb(OrbView.Mode.IDLE)
            }
        }
        refresh()
    }

    // ---- Reboot radio ----

    /** Only for accessibility clicks; everyone else holds the button. */
    private fun confirmReboot() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reboot_title)
            .setMessage(R.string.reboot_message)
            .setPositiveButton(R.string.reboot_yes) { _, _ -> reboot() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reboot() {
        Work.background {
            if (Root.isAvailable()) {
                Work.ui { Toast.makeText(this, R.string.done_rebooting, Toast.LENGTH_LONG).show() }
                Root.run("svc power reboot", 6_000)
                Thread.sleep(4_000)
                Root.run("reboot", 6_000) // only reached if the first way didn't take the radio down
                Thread.sleep(4_000)
                // Still here: root couldn't do it, so let the helper try the power menu.
            }
            Work.ui {
                val helper = HelperService.instance
                if (helper == null) {
                    holdButton.reset()
                    SetupDialog.show(this)
                } else if (!helper.reboot()) {
                    holdButton.reset()
                }
            }
        }
    }

    // ---- Screen ----

    private fun showOrb(mode: OrbView.Mode, settleMs: Long = 0L) {
        main.removeCallbacks(settle)
        orb.mode = mode
        when (mode) {
            OrbView.Mode.IDLE -> orbText(R.drawable.ic_restart, getString(R.string.orb_restart), getString(R.string.orb_sub))
            OrbView.Mode.WORKING ->
                orbText(R.drawable.ic_restart, getString(R.string.orb_working), getString(R.string.orb_working_sub))
            OrbView.Mode.DONE ->
                orbText(R.drawable.ic_check, getString(R.string.orb_done), getString(R.string.orb_done_sub, targetName()))
            OrbView.Mode.ERROR ->
                orbText(R.drawable.ic_alert, getString(R.string.orb_error), getString(R.string.orb_error_sub))
        }
        if (settleMs > 0) main.postDelayed(settle, settleMs)
    }

    private fun orbText(icon: Int, label: String, sub: String) {
        orbIcon.setImageResource(icon)
        orbLabel.text = label
        orbSub.text = sub
        for (v in listOf(orbIcon, orbLabel, orbSub)) {
            v.alpha = 0.2f
            v.animate().alpha(1f).setDuration(220).start()
        }
    }

    private fun targetName(): String = Prefs.target(this)?.let { Apps.label(this, it) } ?: getString(R.string.orb_sub)

    private fun refresh() {
        val pkg = Prefs.target(this)
        if (pkg == null) {
            appIcon.setImageResource(R.drawable.ic_app_placeholder)
            appName.setText(R.string.no_carplay_app)
            appSub.setText(R.string.pick_in_settings)
        } else {
            val icon = Apps.icon(this, pkg)
            if (icon != null) appIcon.setImageDrawable(icon) else appIcon.setImageResource(R.drawable.ic_app_placeholder)
            appName.text = Apps.label(this, pkg)
            appSub.setText(R.string.carplay_app_sub)
        }

        val root = Root.known()
        when {
            root == true -> status(R.color.ok, R.string.status_ready, R.string.status_root_detail, setup = false)
            HelperService.isOn(this) -> status(R.color.ok, R.string.status_ready, R.string.status_helper_detail, setup = false)
            root == null -> status(R.color.idle, R.string.status_checking, R.string.status_checking_detail, setup = false)
            else -> status(R.color.warn, R.string.status_setup, R.string.status_setup_detail, setup = true)
        }

        val last = Prefs.last(this)
        if (last == null) {
            lastValue.setText(R.string.stat_none)
            lastSub.setText(R.string.stat_never)
            lastSub.setTextColor(getColor(R.color.text_tertiary))
        } else {
            lastValue.text = DateFormat.getTimeFormat(this).format(Date(last.time))
            lastSub.setText(if (last.ok) R.string.stat_worked else R.string.stat_failed)
            lastSub.setTextColor(getColor(if (last.ok) R.color.ok else R.color.warn))
        }
        val today = Prefs.restartsToday(this)
        todayValue.text = today.toString()
        todayUnit.text = resources.getQuantityString(R.plurals.restarts_today, today)
    }

    private fun status(colorRes: Int, title: Int, detail: Int, setup: Boolean) {
        val color = getColor(colorRes)
        val live = colorRes != R.color.idle
        statusDot.color = color
        statusDot.pulsing = live
        statusCard.setAccent(if (live) color else 0)
        statusTitle.setText(title)
        statusDetail.setText(detail)
        setupButton.visibility = if (setup) View.VISIBLE else View.GONE
    }

    /** The orb fades up, then the cards rise in one after another. */
    private fun playEntrance() {
        val ease = DecelerateInterpolator(2f)
        val lift = 26 * resources.displayMetrics.density
        orb.alpha = 0f
        orb.animate().alpha(1f).setDuration(700).setInterpolator(ease).start()
        listOf(appCard, statusCard, statsRow, holdButton).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = lift
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(140L + i * 80L).setDuration(520).setInterpolator(ease).start()
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
