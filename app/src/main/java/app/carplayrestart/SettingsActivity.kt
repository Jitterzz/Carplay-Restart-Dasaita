package app.carplayrestart

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Settings, one section at a time: the CarPlay app, the look, auto-fix, quick restart (the floating button and
 * the widget), deeper fixes, and how this radio restarts apps (helper and root).
 */
class SettingsActivity : Activity() {

    companion object {
        /** Open straight into "Which app runs CarPlay?" */
        const val EXTRA_PICK = "pick"
        private const val STATE_SECTION = "section"
    }

    private class Section(val viewId: Int, val label: Int, val icon: Int)

    private val sections = listOf(
        Section(R.id.sec_app, R.string.nav_app, R.drawable.ic_car),
        Section(R.id.sec_look, R.string.nav_look, R.drawable.ic_palette),
        Section(R.id.sec_auto, R.string.nav_auto, R.drawable.ic_auto_fix),
        Section(R.id.sec_quick, R.string.nav_quick, R.drawable.ic_touch),
        Section(R.id.sec_fixes, R.string.nav_fixes, R.drawable.ic_build),
        Section(R.id.sec_helper, R.string.nav_helper, R.drawable.ic_shield),
    )

    private lateinit var t: AppTheme
    private val navItems = mutableListOf<View>()
    private var current = 0
    private var working = false

    // CarPlay app
    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appPkg: TextView
    private lateinit var reopenSwitch: Switch
    private lateinit var modeRow: View
    private lateinit var modeChoices: List<TextView>

    // Auto-fix
    private lateinit var autoLead: TextView
    private lateinit var autoSub: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var autoRules: View
    private lateinit var dropSub: TextView
    private lateinit var dropSwitch: Switch
    private lateinit var plugSwitch: Switch
    private lateinit var autoLog: TextView
    private lateinit var autoLimit: TextView

    // Quick restart
    private lateinit var quickLead: TextView
    private lateinit var bubbleSub: TextView
    private lateinit var bubbleSwitch: Switch
    private lateinit var widgetSub: TextView
    private lateinit var widgetAdd: TextView

    // Fixes
    private lateinit var cacheSub: TextView
    private lateinit var cacheButton: TextView
    private lateinit var wirelessSub: TextView
    private lateinit var wirelessButton: TextView

    // Helper & root
    private lateinit var helperDot: PulseDot
    private lateinit var helperStatus: TextView
    private lateinit var helperButton: TextView
    private lateinit var rootDot: PulseDot
    private lateinit var rootStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        t = Themes.current(this)
        setContentView(R.layout.activity_settings)
        Styler.window(this, t)
        val root = findViewById<View>(R.id.root)
        findViewById<FrameLayout>(R.id.backdrop_slot).addView(
            t.makeBackdrop(this).apply { quiet = true },
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        buildNav()
        buildThemeGrid()
        Styler.apply(root, t)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        setUpApp()
        setUpAuto()
        setUpQuick()
        setUpFixes()
        setUpHelper()

        current = savedInstanceState?.getInt(STATE_SECTION, 0) ?: 0
        show(current)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_PICK, false)) pickApp()

        Work.background {
            Root.isAvailable()
            Work.ui { if (!isFinishing) refresh() }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SECTION, current)
    }

    // ---- The menu ----

    private fun buildNav() {
        val nav = findViewById<LinearLayout>(R.id.nav)
        val vertical = nav.orientation == LinearLayout.VERTICAL
        val gap = (4 * resources.displayMetrics.density).toInt()
        sections.forEachIndexed { i, s ->
            val item = layoutInflater.inflate(R.layout.item_nav, nav, false)
            item.findViewById<ImageView>(R.id.nav_icon).setImageResource(s.icon)
            item.findViewById<TextView>(R.id.nav_label).setText(s.label)
            item.setOnClickListener { show(i) }
            val lp = if (vertical) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (i > 0) topMargin = gap }
            } else {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (i > 0) marginStart = gap }
            }
            nav.addView(item, lp)
            navItems.add(item)
        }
    }

    /** Shows one section and marks it in the menu. */
    private fun show(index: Int) {
        current = index.coerceIn(0, sections.lastIndex)
        sections.forEachIndexed { i, s ->
            findViewById<View>(s.viewId).visibility = if (i == current) View.VISIBLE else View.GONE
        }
        val dp = resources.displayMetrics.density
        navItems.forEachIndexed { i, item ->
            val on = i == current
            item.isSelected = on // TalkBack says "selected"
            item.background = if (on) {
                Styler.navOn(this, t)
            } else {
                RippleDrawable(
                    ColorStateList.valueOf(if (t.light) 0x33C2185B else 0x26FFFFFF), null,
                    GradientDrawable().apply {
                        cornerRadius = 14 * dp
                        setColor(Color.WHITE)
                    },
                )
            }
            item.findViewById<ImageView>(R.id.nav_icon).imageTintList =
                ColorStateList.valueOf(if (on) t.accent else t.text3)
            item.findViewById<TextView>(R.id.nav_label).apply {
                setTextColor(if (on) t.text1 else t.text2)
                typeface = Fonts.get(context, if (on) t.fonts.strong else t.fonts.body)
            }
        }
        findViewById<ScrollView>(R.id.content_scroll).scrollTo(0, 0)
        // Tall screens: keep the chosen tab in view.
        findViewById<HorizontalScrollView?>(R.id.nav_scroll)?.let { bar ->
            val item = navItems[current]
            bar.post { bar.smoothScrollTo((item.left - bar.width / 3).coerceAtLeast(0), 0) }
        }
    }

    // ---- CarPlay app ----

    private fun setUpApp() {
        appIcon = findViewById(R.id.app_icon)
        appName = findViewById(R.id.app_name)
        appPkg = findViewById(R.id.app_pkg)
        reopenSwitch = findViewById(R.id.reopen_switch)
        modeRow = findViewById(R.id.mode_row)
        findViewById<View>(R.id.app_row).setOnClickListener { pickApp() }

        reopenSwitch.isChecked = Prefs.reopen(this)
        reopenSwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setReopen(this, on)
            showMode()
        }
        findViewById<View>(R.id.reopen_row).setOnClickListener { reopenSwitch.toggle() }

        modeChoices = listOf(R.id.mode_auto, R.id.mode_carplay, R.id.mode_android).map { findViewById<TextView>(it) }
        modeChoices.forEachIndexed { mode, view ->
            view.setOnClickListener {
                Prefs.setOpenMode(this, mode)
                showMode()
            }
        }
        showMode()
    }

    /** Marks the chosen "Open it as" button; the whole row dims while "open it again" is off. */
    private fun showMode() {
        val chosen = Prefs.openMode(this)
        modeChoices.forEachIndexed { mode, view ->
            val on = mode == chosen
            view.background = if (on) Styler.pill(this, t) else Styler.choiceOff(this, t)
            view.setTextColor(if (on) t.onPill else t.text1)
            view.typeface = Fonts.get(this, t.fonts.strong)
            view.isSelected = on // TalkBack says "selected"
        }
        modeRow.alpha = if (Prefs.reopen(this)) 1f else 0.4f
    }

    private fun pickApp() {
        val apps = Apps.launchable(this)
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_LONG).show()
            return
        }
        val adapter = object : ArrayAdapter<Apps.App>(this, R.layout.item_app, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
                val app = apps[position]
                val icon = Apps.icon(context, app.pkg)
                val iconView = row.findViewById<ImageView>(R.id.icon)
                if (icon != null) iconView.setImageDrawable(icon) else iconView.setImageResource(R.drawable.ic_app_placeholder)
                row.findViewById<TextView>(R.id.name).apply {
                    text = app.label
                    setTextColor(t.text1)
                    typeface = Fonts.get(context, t.fonts.strong)
                }
                row.findViewById<TextView>(R.id.pkg).apply {
                    text = app.pkg
                    setTextColor(t.text2)
                    typeface = Fonts.get(context, t.fonts.body)
                }
                return row
            }
        }
        Styler.dialog(this, t)
            .setTitle(R.string.pick_title)
            .setAdapter(adapter) { _, which ->
                Prefs.setTarget(this, apps[which].pkg)
                HelperService.instance?.refresh()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { Styler.dress(it, t) }
    }

    // ---- Look ----

    private fun buildThemeGrid() {
        val grid = findViewById<LinearLayout>(R.id.theme_grid)
        val cols = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        val gap = (10 * resources.displayMetrics.density).toInt()
        var row: LinearLayout? = null
        Themes.all.forEachIndexed { i, theme ->
            if (i % cols == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                }
                grid.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { if (i > 0) topMargin = gap },
                )
            }
            val parent = row ?: return@forEachIndexed
            val card = layoutInflater.inflate(R.layout.item_theme, parent, false) as GlassLayout
            val chosen = theme.id == t.id
            card.findViewById<ThemeSwatch>(R.id.theme_swatch).theme = theme
            card.findViewById<TextView>(R.id.theme_name).setText(theme.name)
            card.findViewById<TextView>(R.id.theme_tagline).setText(theme.tagline)
            card.findViewById<View>(R.id.theme_check).visibility = if (chosen) View.VISIBLE else View.GONE
            card.contentDescription = getString(theme.name) + ". " + getString(theme.tagline)
            card.isSelected = chosen
            if (chosen) card.setAccent(t.accent)
            card.setOnClickListener { pickTheme(theme.id) }
            parent.addView(
                card,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i % cols > 0) marginStart = gap },
            )
        }
    }

    private fun pickTheme(id: ThemeId) {
        if (id == t.id) return
        Prefs.setTheme(this, id)
        HelperService.instance?.refresh() // the floating button
        RestartWidget.updateAll(this)
        recreate() // this screen, in the new look (the main screen follows when you go back)
    }

    // ---- Auto-fix ----

    private fun setUpAuto() {
        autoLead = findViewById(R.id.auto_lead)
        autoSub = findViewById(R.id.auto_sub)
        autoSwitch = findViewById(R.id.auto_switch)
        autoRules = findViewById(R.id.auto_rules)
        dropSub = findViewById(R.id.drop_sub)
        dropSwitch = findViewById(R.id.drop_switch)
        plugSwitch = findViewById(R.id.plug_switch)
        autoLog = findViewById(R.id.auto_log)
        autoLimit = findViewById(R.id.auto_limit)

        autoSwitch.isChecked = Prefs.autoFix(this)
        autoSwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setAutoFix(this, on)
            HelperService.instance?.refresh()
            showAuto()
            if (on && !HelperService.isOn(this)) SetupDialog.show(this)
        }
        findViewById<View>(R.id.auto_row).setOnClickListener { autoSwitch.toggle() }

        dropSwitch.isChecked = Prefs.autoDrop(this)
        dropSwitch.setOnCheckedChangeListener { _, on -> Prefs.setAutoDrop(this, on) }
        findViewById<View>(R.id.drop_row).setOnClickListener { if (dropSwitch.isEnabled) dropSwitch.toggle() }

        plugSwitch.isChecked = Prefs.autoPlug(this)
        plugSwitch.setOnCheckedChangeListener { _, on -> Prefs.setAutoPlug(this, on) }
        findViewById<View>(R.id.plug_row).setOnClickListener { if (plugSwitch.isEnabled) plugSwitch.toggle() }
    }

    private fun showAuto() {
        val name = targetLabel()
        autoLead.text = getString(R.string.sec_auto_lead, name)
        dropSub.text = getString(R.string.auto_drop_sub, name)
        autoLimit.text = getString(R.string.auto_limit, name)
        val on = Prefs.autoFix(this)
        val helper = HelperService.isOn(this)
        val pkg = Prefs.target(this)
        when {
            on && !helper -> sub(autoSub, getString(R.string.needs_helper), warn = true)
            // It only acts once it has seen CarPlay running in the app (so it knows what CarPlay looks like there).
            on && (pkg == null || Prefs.videoSeen(this) != pkg) ->
                sub(autoSub, getString(R.string.auto_sub_learning, name), warn = false)
            on -> sub(autoSub, getString(R.string.auto_sub_on, name), warn = false)
            else -> sub(autoSub, getString(R.string.auto_sub_off), warn = false)
        }
        autoRules.alpha = if (on) 1f else 0.4f
        dropSwitch.isEnabled = on
        plugSwitch.isEnabled = on
        val log = Prefs.fixLog(this)
        autoLog.text = if (log.isEmpty()) getString(R.string.auto_log_empty) else log.take(5).joinToString("\n")
    }

    // ---- Quick restart ----

    private fun setUpQuick() {
        quickLead = findViewById(R.id.quick_lead)
        bubbleSub = findViewById(R.id.bubble_sub)
        bubbleSwitch = findViewById(R.id.bubble_switch)
        widgetSub = findViewById(R.id.widget_sub)
        widgetAdd = findViewById(R.id.widget_add)

        bubbleSwitch.isChecked = Prefs.bubble(this)
        bubbleSwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setBubble(this, on)
            HelperService.instance?.refresh()
            showQuick()
            if (on && !HelperService.isOn(this)) SetupDialog.show(this)
        }
        findViewById<View>(R.id.bubble_row).setOnClickListener { bubbleSwitch.toggle() }

        widgetAdd.setOnClickListener {
            if (!RestartWidget.add(this)) Toast.makeText(this, R.string.widget_add_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showQuick() {
        val name = targetLabel()
        quickLead.text = getString(R.string.sec_quick_lead, name)
        if (Prefs.bubble(this) && !HelperService.isOn(this)) {
            sub(bubbleSub, getString(R.string.needs_helper), warn = true)
        } else {
            sub(bubbleSub, getString(R.string.bubble_sub, name), warn = false)
        }
        val canAdd = RestartWidget.canAdd(this)
        widgetSub.setText(if (canAdd) R.string.widget_sub_add else R.string.widget_sub_manual)
        widgetAdd.visibility = if (canAdd) View.VISIBLE else View.GONE
    }

    // ---- Fixes ----

    private fun setUpFixes() {
        cacheSub = findViewById(R.id.cache_sub)
        cacheButton = findViewById(R.id.cache_button)
        wirelessSub = findViewById(R.id.wireless_sub)
        wirelessButton = findViewById(R.id.wireless_button)
        cacheButton.setOnClickListener { clearCache() }
        wirelessButton.setOnClickListener { confirmResetWireless() }
    }

    private fun showFixes() {
        val name = targetLabel()
        cacheSub.text = getString(R.string.cache_sub, name)
        val root = Root.known()
        wirelessSub.text = getString(if (root == false) R.string.wireless_sub_no_root else R.string.wireless_sub, name)
        wirelessButton.alpha = if (root == true) 1f else 0.45f
        if (!working) {
            cacheButton.setText(R.string.cache_button)
            wirelessButton.setText(R.string.wireless_button)
        }
    }

    private fun clearCache() {
        if (working || HelperService.instance?.isBusy == true) return
        working = true
        cacheButton.setText(R.string.cache_working)
        Restarter.restart(this, clearCache = true) { result ->
            working = false
            if (isFinishing || isDestroyed) return@restart
            showFixes()
            when (result) {
                Restarter.Result.NEEDS_SETUP -> SetupDialog.show(this)
                Restarter.Result.NO_APP -> {
                    Toast.makeText(this, R.string.pick_first, Toast.LENGTH_LONG).show()
                    show(0)
                    pickApp()
                }
                else -> Unit // done, or the helper is on it
            }
        }
    }

    private fun confirmResetWireless() {
        if (working) return
        when (Root.known()) {
            null -> {
                Toast.makeText(this, R.string.root_checking, Toast.LENGTH_SHORT).show()
                return
            }
            false -> {
                Toast.makeText(this, R.string.wireless_needs_root, Toast.LENGTH_LONG).show()
                return
            }
            true -> Unit
        }
        Styler.dialog(this, t)
            .setTitle(R.string.wireless_confirm_title)
            .setMessage(getString(R.string.wireless_confirm_message, targetLabel()))
            .setPositiveButton(R.string.wireless_button) { _, _ -> resetWireless() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { Styler.dress(it, t) }
    }

    private fun resetWireless() {
        working = true
        wirelessButton.setText(R.string.wireless_working)
        Restarter.resetWireless(this) { ok ->
            working = false
            Toast.makeText(
                applicationContext, if (ok) R.string.wireless_done else R.string.wireless_needs_root,
                Toast.LENGTH_LONG,
            ).show()
            if (!isFinishing && !isDestroyed) showFixes()
        }
    }

    // ---- Helper & root ----

    private fun setUpHelper() {
        helperDot = findViewById(R.id.helper_dot)
        helperStatus = findViewById(R.id.helper_status)
        helperButton = findViewById(R.id.helper_button)
        rootDot = findViewById(R.id.root_dot)
        rootStatus = findViewById(R.id.root_status)
        helperButton.setOnClickListener {
            if (HelperService.isOn(this)) SetupDialog.openAccessibilitySettings(this) else SetupDialog.show(this)
        }
        findViewById<TextView>(R.id.version).text = getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    private fun showHelper() {
        val on = HelperService.isOn(this)
        helperDot.color = if (on) t.ok else t.warn
        helperDot.pulsing = on
        helperStatus.setText(if (on) R.string.helper_on else R.string.helper_off)
        helperButton.setText(if (on) R.string.helper_manage else R.string.helper_turn_on)

        val root = Root.known()
        rootDot.color = if (root == true) t.ok else t.idle
        rootDot.pulsing = root == true
        rootStatus.setText(
            when (root) {
                true -> R.string.root_yes
                false -> R.string.root_no
                null -> R.string.root_checking
            }
        )
    }

    // ---- Everything ----

    private fun refresh() {
        val pkg = Prefs.target(this)
        if (pkg == null) {
            appIcon.setImageResource(R.drawable.ic_app_placeholder)
            appName.setText(R.string.no_carplay_app)
            appPkg.setText(R.string.tap_to_pick)
        } else {
            val icon = Apps.icon(this, pkg)
            if (icon != null) appIcon.setImageDrawable(icon) else appIcon.setImageResource(R.drawable.ic_app_placeholder)
            appName.text = Apps.label(this, pkg)
            appPkg.text = pkg
        }
        showAuto()
        showQuick()
        showFixes()
        showHelper()
    }

    /** The CarPlay app's name, for the explanations ("ZLINK5"), or "the CarPlay app". */
    private fun targetLabel(): String =
        Prefs.target(this)?.let { Apps.label(this, it) } ?: getString(R.string.the_carplay_app)

    /** An explanation under a switch, in the warning color when something's missing. */
    private fun sub(view: TextView, text: String, warn: Boolean) {
        view.text = text
        view.setTextColor(if (warn) t.warn else t.text2)
    }
}
