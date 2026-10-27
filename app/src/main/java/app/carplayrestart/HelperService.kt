package app.carplayrestart

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * The helper, for radios without root (and for the extras that need to see the screen). Android doesn't let one
 * app close another by itself, so when you press Restart, the helper opens the CarPlay app's App info page,
 * presses Force stop (and OK), then opens the app again. For Reboot radio, it opens the power menu and presses
 * Restart. It can clear the app's cache on the way (Settings › Fixes).
 *
 * It also keeps the floating restart button over the CarPlay app (if turned on), and auto-fix: every few
 * seconds it glances at whether the CarPlay app is showing CarPlay or its own screen (see [lookAt]).
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
        private val STORAGE = listOf(
            "storage", "storage & cache", "storage and cache", "storage usage", "almacenamiento",
            "almacenamiento y caché", "almacenamiento y cache", "存储", "存储空间", "存储和缓存", "儲存空間", "存储占用",
        )
        private val CLEAR_CACHE = listOf(
            "clear cache", "borrar caché", "borrar cache", "limpiar caché", "limpiar cache", "vaciar caché",
            "清除缓存", "清除快取",
        )

        // Names of the CarPlay / Android Auto buttons inside the CarPlay app, for "Open it as".
        private val CARPLAY = listOf("carplay", "apple carplay", "apple car play", "car play", "carplay mode")
        private val ANDROID_AUTO = listOf("android auto", "androidauto", "google android auto", "android auto mode")

        // Words the CarPlay app shows while it's trying to connect (it isn't showing CarPlay yet).
        private val CONNECTING = listOf("connecting", "conectando", "连接中", "正在连接", "連線中")

        private const val TICK_MS = 250L
        private const val WATCH_MS = 3_000L
    }

    /**
     * What the CarPlay app is up to, as far as the helper can tell: not in front; SHOWING the phone's picture
     * (CarPlay or Android Auto); its OWN_SCREEN (words, buttons); CONNECTING ("Connecting…"); or UNKNOWN
     * (almost nothing on screen: a splash screen, a blank moment, the screen off).
     */
    enum class Look { NOT_FRONT, SHOWING, OWN_SCREEN, CONNECTING, UNKNOWN }

    private enum class Step {
        OPEN_STORAGE, CLEAR_CACHE, LEAVE_STORAGE,
        FIND_FORCE_STOP, CONFIRM_FORCE_STOP, REOPEN,
        FIND_RESTART, CONFIRM_RESTART,
        PICK_MODE,
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { onTick() }
    private var step: Step? = null
    private var stepStartedAt = 0L
    private var greyedSince = 0L
    private var target = ""
    private var reopenAfter = true
    private var cacheCleared = false
    private var autoRun = false
    private var scrolls = 0
    private var lastScrollAt = 0L

    private var bubble: FloatingButton? = null
    var autoFix: AutoFix? = null
        private set
    private var frontPkg: String? = null
    private var watching = false
    private val watch = Runnable { onWatch() }
    private var usbRegistered = false
    private var targetCache: String? = null
    private var targetAt = 0L

    /** A restart or reboot is under way. Pressing Apple CarPlay / Android Auto afterwards doesn't count: it's quiet
     *  and stops by itself, so the main screen never waits on it. */
    val isBusy: Boolean get() = step != null && step != Step.PICK_MODE

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null && looksLikePhone(device)) autoFix?.onPhonePlugged()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        bubble = FloatingButton(this)
        autoFix = AutoFix(this)
        registerUsb()
        refresh()
        Outcome.listener?.invoke() // the main screen can show "Ready" right away
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        stop()
        stopWatch()
        bubble?.hide()
        unregisterUsb()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            // Someone is using the CarPlay app (its settings, say): auto-fix waits.
            if (step == null && event.packageName?.toString() == targetPkg()) autoFix?.onUserActivity()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // A new screen or dialog just opened: look at it now instead of waiting for the next tick.
        if (step != null) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 120)
        }
        // Which app is in front, for the floating button. (This app's own windows only count if they're screens.)
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        if (pkg == packageName && event.className?.toString()?.endsWith("Activity") != true) return
        if (pkg != frontPkg) {
            frontPkg = pkg
            updateBubble()
        }
    }

    override fun onInterrupt() {}

    // ---- Settings changed: the floating button and auto-fix ----

    /** Starts or stops the extras to match Settings (and repaints the floating button in the current look). */
    fun refresh() {
        targetAt = 0L
        if (Prefs.bubble(this) || Prefs.autoFix(this)) startWatch() else stopWatch()
        bubble?.restyle()
        updateBubble()
    }

    /** The CarPlay app, looked up at most every few seconds. */
    fun targetPkg(): String? {
        val now = SystemClock.uptimeMillis()
        if (targetAt == 0L || now - targetAt > 10_000L) {
            targetCache = Prefs.target(this)
            targetAt = now
        }
        return targetCache
    }

    private fun startWatch() {
        if (watching) return
        watching = true
        handler.postDelayed(watch, 1_000L)
    }

    private fun stopWatch() {
        watching = false
        handler.removeCallbacks(watch)
    }

    private fun onWatch() {
        if (!watching) return
        try {
            rootInActiveWindow?.packageName?.toString()?.let { if (it != "com.android.systemui") frontPkg = it }
            updateBubble()
            if (Prefs.autoFix(this)) autoFix?.check()
        } catch (_: Exception) {
        }
        handler.postDelayed(watch, WATCH_MS)
    }

    /** The floating button shows over the CarPlay app, except while the helper is busy on screen. */
    private fun updateBubble() {
        val b = bubble ?: return
        val pkg = frontPkg
        b.update(Prefs.bubble(this) && step == null && pkg != null && pkg == targetPkg())
    }

    /** The floating button was tapped. */
    fun restartFromBubble() {
        bubble?.setWorking(true)
        Restarter.restart(this) { result ->
            bubble?.setWorking(false)
            if (result == Restarter.Result.NO_APP) {
                Toast.makeText(applicationContext, R.string.pick_first, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * CarPlay and Android Auto are the phone's picture, drawn on a big video surface; the CarPlay app's own screens
     * are words and buttons. The picture wins: a word left behind it (Android can't tell it's covered) mustn't make
     * a working CarPlay look stuck. Too little to go on is UNKNOWN, which auto-fix never acts on.
     */
    fun lookAt(pkg: String): Look {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return Look.NOT_FRONT
        if (root.packageName?.toString() != pkg) return Look.NOT_FRONT
        val window = Rect()
        root.getBoundsInScreen(window)
        val windowArea = window.width().toLong() * window.height()
        var words = 0
        var connecting = false
        var video = false
        var budget = 1_500
        val box = Rect()
        val stack = mutableListOf(root)
        while (stack.isNotEmpty() && budget-- > 0) {
            val node = stack.removeAt(stack.size - 1)
            if (node.isVisibleToUser) {
                val text = node.text
                if (!text.isNullOrBlank()) {
                    words++
                    val t = text.toString().lowercase()
                    if (CONNECTING.any { t.contains(it) }) connecting = true
                }
                // The phone's picture: a video surface covering at least half the window. Newer Android names it
                // SurfaceView or TextureView; older Android calls those plain View, so a big, blank, empty plain
                // View counts too.
                val cls = node.className?.toString()
                val surface = cls == "android.view.SurfaceView" || cls == "android.view.TextureView" ||
                    cls == "android.widget.VideoView" ||
                    (cls == "android.view.View" && node.childCount == 0 && text.isNullOrBlank() &&
                        node.contentDescription.isNullOrBlank())
                if (surface && windowArea > 0) {
                    node.getBoundsInScreen(box)
                    if (box.width().toLong() * box.height() * 2 >= windowArea) video = true
                }
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.add(it) }
        }
        return when {
            video -> Look.SHOWING
            connecting -> Look.CONNECTING
            words <= 2 -> Look.UNKNOWN
            else -> Look.OWN_SCREEN
        }
    }

    /** An iPhone, or an Android phone (as itself, or switched into Android Auto's accessory mode). Not a USB stick,
     *  dash cam, hub or dongle. */
    private fun looksLikePhone(d: UsbDevice): Boolean {
        if (d.vendorId == 0x05AC) return true // Apple
        if (d.vendorId == 0x18D1 && d.productId in 0x2D00..0x2D05) return true // Android Auto's accessory mode
        for (i in 0 until d.interfaceCount) {
            val f = d.getInterface(i)
            if (f.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) return true // a phone's MTP / PTP
            if (f.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC && f.interfaceSubclass == 0x42 &&
                f.interfaceProtocol == 1
            ) {
                return true // a phone's ADB
            }
        }
        return false
    }

    private fun registerUsb() {
        if (usbRegistered) return
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(usbReceiver, filter)
            }
            usbRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterUsb() {
        if (!usbRegistered) return
        usbRegistered = false
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {
        }
    }

    // ---- Restart, reboot, "Open it as" ----

    /**
     * Force stops [pkg] from its App info page (clearing its cache first if [clearCache]), then opens it again if
     * [reopen]. [auto]: it's auto-fix's doing. False if it couldn't start.
     */
    fun restart(pkg: String, reopen: Boolean, clearCache: Boolean = false, auto: Boolean = false): Boolean {
        stop()
        target = pkg
        reopenAfter = reopen
        cacheCleared = false
        autoRun = auto
        return try {
            startActivity(appInfo(pkg, fresh = false))
            go(if (clearCache) Step.OPEN_STORAGE else Step.FIND_FORCE_STOP, 500)
            true
        } catch (_: Exception) {
            Outcome.report(this, false, getString(R.string.err_no_app_info), restart = true)
            false
        }
    }

    /** [pkg]'s App info page. [fresh]: also close the settings pages opened on the way (the Storage page). */
    private fun appInfo(pkg: String, fresh: Boolean): Intent {
        var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        if (fresh) flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(flags)
    }

    /** After the CarPlay app [pkg] opens, presses its Apple CarPlay or Android Auto button (per "Open it as").
     *  Quiet and best effort: if the button never shows up, it just stops after a while. */
    fun pickMode(pkg: String, delayMs: Long = 1_500) {
        if (Prefs.openMode(this) == Prefs.MODE_AUTO) return
        if (step != null && step != Step.PICK_MODE) return // a restart or reboot is under way
        target = pkg
        go(Step.PICK_MODE, delayMs)
    }

    /** Opens the power menu and presses Restart. False if the radio has no power menu to open. */
    fun reboot(): Boolean {
        stop()
        autoRun = false
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
        scrolls = 0
        lastScrollAt = 0L
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
        updateBubble()
    }

    private fun again() {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    private fun stop() {
        step = null
        handler.removeCallbacks(tick)
        updateBubble()
    }

    private fun onTick() {
        val now = SystemClock.uptimeMillis()
        val waited = now - stepStartedAt
        when (step ?: return) {
            Step.OPEN_STORAGE -> {
                // Some radios have Clear cache right on App info; most have it under Storage, often a scroll down.
                if (find(CLEAR_CACHE) != null) {
                    go(Step.CLEAR_CACHE, 0)
                    return
                }
                val storage = find(STORAGE)
                when {
                    storage != null && tap(storage) -> go(Step.CLEAR_CACHE, 800)
                    waited > 7_000 -> go(Step.FIND_FORCE_STOP, 0) // no Storage page here: just restart it
                    waited > 1_200 && scrolls < 4 && now - lastScrollAt > 700 -> {
                        scroll(forward = true)
                        scrolls++
                        lastScrollAt = now
                        again()
                    }
                    else -> again()
                }
            }

            Step.CLEAR_CACHE -> {
                // Only ever the Clear cache button: never Clear storage / Clear data, which would wipe the app.
                val button = find(CLEAR_CACHE)?.let { clickableOf(it) }
                when {
                    button == null -> if (waited > 6_000) go(Step.LEAVE_STORAGE, 0) else again()
                    // Let the page settle first: until the sizes have loaded, the button can be greyed out, or look
                    // ready but not answer yet.
                    waited < 1_200 -> again()
                    !button.isEnabled -> // still greyed out: the cache is already empty
                        if (waited > 2_500) {
                            cacheCleared = true
                            go(Step.LEAVE_STORAGE, 0)
                        } else {
                            again()
                        }
                    tap(button) -> {
                        cacheCleared = true
                        go(Step.LEAVE_STORAGE, 800)
                    }
                    else -> go(Step.LEAVE_STORAGE, 0)
                }
            }

            Step.LEAVE_STORAGE -> {
                // Back to a fresh App info page (Back alone can land somewhere else) for Force stop.
                try {
                    startActivity(appInfo(target, fresh = true))
                    go(Step.FIND_FORCE_STOP, 700)
                } catch (_: Exception) {
                    fail(getString(R.string.err_no_app_info), restart = true)
                }
            }

            Step.FIND_FORCE_STOP -> {
                val button = find(FORCE_STOP)?.let { clickableOf(it) }
                when {
                    button == null -> when {
                        waited > 10_000 -> fail(getString(R.string.err_no_force_stop), restart = true)
                        // Scrolled out of sight? It lives at the top of the page.
                        waited > 1_500 && scrolls < 3 && now - lastScrollAt > 700 -> {
                            scroll(forward = false)
                            scrolls++
                            lastScrollAt = now
                            again()
                        }
                        else -> again()
                    }
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
                val labels = when (Prefs.openMode(this)) {
                    Prefs.MODE_CARPLAY -> CARPLAY
                    Prefs.MODE_ANDROID_AUTO -> ANDROID_AUTO
                    else -> {
                        stop() // switched back to Auto in the meantime
                        return
                    }
                }
                // Only a button: never flip a switch or checkbox that happens to be called "CarPlay" (a setting).
                val button = find(labels, activeWindowOnly = true, onlyPackage = target)
                    ?.takeUnless { clickableOf(it).isCheckable }
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
                (findId("android:id/button1", activeWindowOnly = true) ?: find(RESTART, activeWindowOnly = true))
                    ?.let { tap(it) }
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
            when {
                launch != null -> startActivity(launch)
                autoRun -> performGlobalAction(GLOBAL_ACTION_HOME) // auto-fix never puts this app over the road
                else -> startActivity(mainScreen())
            }
        } catch (_: Exception) {
        }
        val text = getString(
            when {
                autoRun -> R.string.done_auto_restarted
                cacheCleared -> R.string.done_cache_restarted
                else -> R.string.done_restarted
            },
            name,
        )
        Outcome.report(this, true, text, restart = true)
        if (launch != null) pickMode(target, 2_500)
    }

    /** [restart]: it was a CarPlay restart that failed (false for a reboot). */
    private fun fail(message: String, restart: Boolean, bringBack: Boolean = true) {
        stop()
        val auto = autoRun && restart
        if (bringBack) {
            try {
                if (auto) backToCarPlay() else startActivity(mainScreen())
            } catch (_: Exception) {
            }
        }
        if (auto) autoFix?.onFailed()
        Outcome.report(this, false, message, restart)
    }

    /** After an auto-fix run that went wrong: back to the CarPlay app (or the home screen), never this app. */
    private fun backToCarPlay() {
        val launch = Apps.launchIntent(this, target)
        if (launch != null) startActivity(launch) else performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun mainScreen() = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    // ---- Looking at the screen ----

    /** What's on screen, minus this app's own windows (its big "Restart" button mustn't be mistaken for the
     *  power menu's), or only [onlyPackage]'s. */
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
    private fun find(
        labels: List<String>,
        activeWindowOnly: Boolean = false,
        onlyPackage: String? = null,
    ): AccessibilityNodeInfo? {
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

    /** Scrolls the settings page a screen down (or up), to bring a button into view. Never the CarPlay app or this one. */
    private fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString()
        if (pkg == packageName || pkg == target) return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        var budget = 2_000
        val stack = mutableListOf(root)
        while (stack.isNotEmpty() && budget-- > 0) {
            val node = stack.removeAt(stack.size - 1)
            if (node.isScrollable && node.isVisibleToUser) return node.performAction(action)
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.add(it) }
        }
        return false
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
