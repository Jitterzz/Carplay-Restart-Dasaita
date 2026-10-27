package app.carplayrestart

import android.animation.AnimatorInflater
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.Date

/** The one screen that matters: the big Restart button for CarPlay, and Hold to reboot radio for when that's not enough. */
class MainActivity : Activity() {

    companion object {
        /** Open with the "Turn on the helper" card showing (the widget asks for it before the first restart). */
        const val EXTRA_SETUP = "setup"
    }

    private lateinit var t: AppTheme
    private lateinit var hero: HeroView
    private lateinit var backdrop: BackdropView
    private lateinit var heroIcon: ImageView
    private lateinit var heroLabel: TextView
    private lateinit var heroSub: TextView
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
    private val settle = Runnable { showHero(HeroView.Mode.IDLE) }
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        t = Themes.current(this)
        setContentView(R.layout.activity_main)
        Styler.window(this, t)
        val root = findViewById<View>(R.id.root)

        // The look's scenery and Restart button go in the two slots the layout leaves for them.
        backdrop = t.makeBackdrop(this)
        findViewById<FrameLayout>(R.id.backdrop_slot).addView(
            backdrop, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        hero = t.makeHero(this).apply {
            id = R.id.hero
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.orb_description)
            stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.press_scale)
        }
        layoutInflater.inflate(R.layout.hero_text, hero, true)
        findViewById<FrameLayout>(R.id.hero_slot).addView(
            hero,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER,
            ),
        )
        hero.applyTheme(t)
        Styler.apply(root, t)

        // Some scenery centres on the button (a glow behind it, the sun behind the neon ring…).
        root.viewTreeObserver.addOnGlobalLayoutListener { focusBackdrop() }

        heroIcon = findViewById(R.id.orb_icon)
        heroLabel = findViewById(R.id.orb_label)
        heroSub = findViewById(R.id.orb_sub)
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

        hero.setOnClickListener { restartCarPlay() }
        setupButton.setOnClickListener { SetupDialog.show(this) }
        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        holdButton.onConfirmed = { reboot() }
        holdButton.onShortTap = { Toast.makeText(this, R.string.hold_hint, Toast.LENGTH_SHORT).show() }
        holdButton.setOnClickListener { confirmReboot() } // TalkBack and other accessibility clicks can't hold

        showHero(HeroView.Mode.IDLE)
        if (savedInstanceState == null) {
            playEntrance()
            if (intent.getBooleanExtra(EXTRA_SETUP, false)) SetupDialog.show(this)
        }

        // Find out whether this radio has root: it decides how restarts happen.
        Work.background {
            Root.isAvailable()
            Work.ui { if (!isFinishing) refresh() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SETUP, false)) SetupDialog.show(this)
    }

    override fun onResume() {
        super.onResume()
        // A new look picked in Settings: build the screen again in it.
        if (Prefs.theme(this) != t.id) {
            recreate()
            return
        }
        Outcome.listener = { onOutcome() }
        // Back from a helper run (it opened the CarPlay app over this screen): show how it went.
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
        if (busy || HelperService.instance?.isBusy == true) return
        val pkg = Prefs.target(this)
        if (pkg == null || !Apps.isSafeName(pkg)) {
            Toast.makeText(this, R.string.pick_first, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PICK, true))
            return
        }
        busy = true
        showHero(HeroView.Mode.WORKING)
        // The button says how it went, so no message at the bottom of the screen as well.
        Restarter.restart(this, toast = false) { result ->
            if (isFinishing || isDestroyed) return@restart
            when (result) {
                Restarter.Result.DONE_ROOT -> {
                    busy = false
                    showHero(HeroView.Mode.DONE, settleMs = 2600)
                }
                Restarter.Result.HELPER_STARTED -> Unit // it reports back when it's done (see onOutcome)
                Restarter.Result.NEEDS_SETUP -> {
                    // No root and no helper yet: explain the one-time setup.
                    busy = false
                    showHero(HeroView.Mode.IDLE)
                    SetupDialog.show(this)
                }
                Restarter.Result.NO_APP -> {
                    busy = false
                    showHero(HeroView.Mode.IDLE)
                }
                Restarter.Result.FAILED -> {
                    busy = false
                    showHero(HeroView.Mode.ERROR, settleMs = 2600)
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
                showHero(if (last.ok) HeroView.Mode.DONE else HeroView.Mode.ERROR, settleMs = 2600)
            } else {
                showHero(HeroView.Mode.IDLE)
            }
        }
        refresh()
    }

    // ---- Reboot radio ----

    /** Only for accessibility clicks; everyone else holds the button. */
    private fun confirmReboot() {
        Styler.dialog(this, t)
            .setTitle(R.string.reboot_title)
            .setMessage(R.string.reboot_message)
            .setPositiveButton(R.string.reboot_yes) { _, _ -> reboot() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { Styler.dress(it, t) }
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

    private fun showHero(mode: HeroView.Mode, settleMs: Long = 0L) {
        main.removeCallbacks(settle)
        hero.mode = mode
        backdrop.mode = mode
        when (mode) {
            HeroView.Mode.IDLE ->
                heroText(R.drawable.ic_restart, getString(R.string.orb_restart), getString(R.string.orb_sub))
            HeroView.Mode.WORKING ->
                heroText(R.drawable.ic_restart, getString(R.string.orb_working), getString(R.string.orb_working_sub))
            HeroView.Mode.DONE ->
                heroText(t.doneIcon, getString(R.string.orb_done), getString(R.string.orb_done_sub, targetName()))
            HeroView.Mode.ERROR ->
                heroText(R.drawable.ic_alert, getString(R.string.orb_error), getString(R.string.orb_error_sub))
        }
        if (settleMs > 0) main.postDelayed(settle, settleMs)
    }

    private fun heroText(icon: Int, label: String, sub: String) {
        heroIcon.setImageResource(icon)
        heroLabel.text = label
        heroSub.text = sub
        for (v in listOf(heroIcon, heroLabel, heroSub)) {
            v.alpha = 0.2f
            v.animate().alpha(1f).setDuration(220).start()
        }
    }

    private fun targetName(): String = Prefs.target(this)?.let { Apps.label(this, it) } ?: getString(R.string.orb_sub)

    /** Tells the scenery where the Restart button sits. */
    private fun focusBackdrop() {
        if (hero.width == 0 || backdrop.width == 0) return
        val h = IntArray(2)
        val b = IntArray(2)
        hero.getLocationInWindow(h)
        backdrop.getLocationInWindow(b)
        backdrop.setFocus(
            h[0] - b[0] + hero.width / 2f, h[1] - b[1] + hero.height / 2f, hero.width.toFloat(),
        )
    }

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
            root == true -> status(t.ok, R.string.status_ready, R.string.status_root_detail, live = true, setup = false)
            HelperService.isOn(this) ->
                status(t.ok, R.string.status_ready, R.string.status_helper_detail, live = true, setup = false)
            root == null ->
                status(t.idle, R.string.status_checking, R.string.status_checking_detail, live = false, setup = false)
            else -> status(t.warn, R.string.status_setup, R.string.status_setup_detail, live = true, setup = true)
        }

        val last = Prefs.last(this)
        if (last == null) {
            lastValue.setText(R.string.stat_none)
            lastSub.setText(R.string.stat_never)
            lastSub.setTextColor(t.text3)
        } else {
            lastValue.text = DateFormat.getTimeFormat(this).format(Date(last.time))
            lastSub.setText(if (last.ok) R.string.stat_worked else R.string.stat_failed)
            lastSub.setTextColor(if (last.ok) t.okText else t.warn)
        }
        val today = Prefs.restartsToday(this)
        todayValue.text = today.toString()
        todayUnit.text = resources.getQuantityString(R.plurals.restarts_today, today)
    }

    private fun status(color: Int, title: Int, detail: Int, live: Boolean, setup: Boolean) {
        statusDot.color = color
        statusDot.pulsing = live
        statusCard.setAccent(if (live) color else 0)
        statusTitle.setText(title)
        statusDetail.setText(detail)
        setupButton.visibility = if (setup) View.VISIBLE else View.GONE
    }

    /** The button fades up, then the cards rise in one after another. */
    private fun playEntrance() {
        val ease = DecelerateInterpolator(2f)
        val lift = 26 * resources.displayMetrics.density
        hero.alpha = 0f
        hero.animate().alpha(1f).setDuration(700).setInterpolator(ease).start()
        listOf(appCard, statusCard, statsRow, holdButton).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = lift
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(140L + i * 80L).setDuration(520).setInterpolator(ease).start()
        }
    }
}
