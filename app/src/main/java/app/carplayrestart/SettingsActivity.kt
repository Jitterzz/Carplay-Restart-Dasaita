package app.carplayrestart

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Which app to restart, whether to open it again, and how restarts happen on this radio. */
class SettingsActivity : Activity() {

    companion object {
        /** Open straight into "Which app runs CarPlay?" */
        const val EXTRA_PICK = "pick"
    }

    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appPkg: TextView
    private lateinit var reopenSwitch: Switch
    private lateinit var modeRow: View
    private lateinit var modeChoices: List<TextView>
    private lateinit var helperDot: PulseDot
    private lateinit var helperStatus: TextView
    private lateinit var helperButton: TextView
    private lateinit var rootDot: PulseDot
    private lateinit var rootStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appIcon = findViewById(R.id.app_icon)
        appName = findViewById(R.id.app_name)
        appPkg = findViewById(R.id.app_pkg)
        reopenSwitch = findViewById(R.id.reopen_switch)
        helperDot = findViewById(R.id.helper_dot)
        helperStatus = findViewById(R.id.helper_status)
        helperButton = findViewById(R.id.helper_button)
        rootDot = findViewById(R.id.root_dot)
        rootStatus = findViewById(R.id.root_status)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.app_row).setOnClickListener { pickApp() }

        reopenSwitch.isChecked = Prefs.reopen(this)
        reopenSwitch.setOnCheckedChangeListener { _, on ->
            Prefs.setReopen(this, on)
            showMode()
        }
        findViewById<View>(R.id.reopen_row).setOnClickListener { reopenSwitch.toggle() }

        modeRow = findViewById(R.id.mode_row)
        modeChoices = listOf(R.id.mode_auto, R.id.mode_carplay, R.id.mode_android).map { findViewById<TextView>(it) }
        modeChoices.forEachIndexed { mode, view ->
            view.setOnClickListener {
                Prefs.setOpenMode(this, mode)
                showMode()
            }
        }
        showMode()

        helperButton.setOnClickListener {
            if (HelperService.isOn(this)) SetupDialog.openAccessibilitySettings(this) else SetupDialog.show(this)
        }

        findViewById<TextView>(R.id.version).text = getString(R.string.version, BuildConfig.VERSION_NAME)

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

        val on = HelperService.isOn(this)
        helperDot.color = getColor(if (on) R.color.ok else R.color.warn)
        helperDot.pulsing = on
        helperStatus.setText(if (on) R.string.helper_on else R.string.helper_off)
        helperButton.setText(if (on) R.string.helper_manage else R.string.helper_turn_on)

        val root = Root.known()
        rootDot.color = getColor(if (root == true) R.color.ok else R.color.idle)
        rootDot.pulsing = root == true
        rootStatus.setText(
            when (root) {
                true -> R.string.root_yes
                false -> R.string.root_no
                null -> R.string.root_checking
            }
        )
    }

    /** Marks the chosen "Open it as" button; the whole row dims while "open it again" is off. */
    private fun showMode() {
        val chosen = Prefs.openMode(this)
        modeChoices.forEachIndexed { mode, view ->
            view.setBackgroundResource(if (mode == chosen) R.drawable.bg_pill_accent else R.drawable.bg_seg_off)
            view.isSelected = mode == chosen // TalkBack says "selected"
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
                row.findViewById<TextView>(R.id.name).text = app.label
                row.findViewById<TextView>(R.id.pkg).text = app.pkg
                return row
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_title)
            .setAdapter(adapter) { _, which ->
                Prefs.setTarget(this, apps[which].pkg)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
