package app.carplayrestart

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast

/**
 * What the home screen widget opens: restarts the CarPlay app straight away, without showing this app. It's
 * see-through and closes itself. With root it stays a moment longer, because Android only lets an app open
 * another one (the CarPlay app, again) while it's on screen.
 */
class QuickRestartActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val close = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Rebuilt by Android (or a restart already under way): don't restart twice.
        if (savedInstanceState != null || HelperService.instance?.isBusy == true) {
            finish()
            return
        }
        Restarter.restart(this) { result ->
            when (result) {
                Restarter.Result.DONE_ROOT -> handler.postDelayed(close, 1_500)
                Restarter.Result.NEEDS_SETUP -> {
                    open(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETUP, true))
                    finish()
                }
                Restarter.Result.NO_APP -> {
                    Toast.makeText(applicationContext, R.string.pick_first, Toast.LENGTH_LONG).show()
                    open(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PICK, true))
                    finish()
                }
                else -> finish() // the helper is on it (or said why it couldn't)
            }
        }
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(close)
        super.onDestroy()
    }
}

/** The home screen widget: one round Restart button, in the look picked in Settings. */
class RestartWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = views(context)
        for (id in ids) manager.updateAppWidget(id, views)
    }

    companion object {

        /** Repaints every widget (after the look changes). */
        fun updateAll(ctx: Context) {
            try {
                val manager = AppWidgetManager.getInstance(ctx) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(ctx, RestartWidget::class.java))
                if (ids.isEmpty()) return
                val views = views(ctx)
                for (id in ids) manager.updateAppWidget(id, views)
            } catch (_: Exception) {
            }
        }

        /** Whether the home screen can add the widget when asked (many radio launchers can't). */
        fun canAdd(ctx: Context): Boolean = try {
            AppWidgetManager.getInstance(ctx)?.isRequestPinAppWidgetSupported == true
        } catch (_: Exception) {
            false
        }

        /** Asks the home screen to add the widget. */
        fun add(ctx: Context): Boolean = try {
            AppWidgetManager.getInstance(ctx)
                ?.requestPinAppWidget(ComponentName(ctx, RestartWidget::class.java), null, null) == true
        } catch (_: Exception) {
            false
        }

        private fun views(ctx: Context): RemoteViews {
            val app = ctx.applicationContext
            val views = RemoteViews(app.packageName, R.layout.widget_restart)
            views.setImageViewBitmap(R.id.widget_button, button(app, Themes.current(app)))
            val tap = PendingIntent.getActivity(
                app, 0,
                Intent(app, QuickRestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, tap)
            return views
        }

        /** The round button, drawn in the look's colors. */
        private fun button(ctx: Context, t: AppTheme): Bitmap {
            val dp = ctx.resources.displayMetrics.density
            val s = (64 * dp).toInt().coerceAtLeast(64)
            val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val c = s / 2f
            val r = c - 3 * dp
            val clamp = Shader.TileMode.CLAMP
            canvas.drawCircle(c, c + 1.5f * dp, c, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    c, c + 1.5f * dp, c,
                    intArrayOf(0x59000000, 0x59000000, 0x00000000), floatArrayOf(0f, 0.8f, 1f), clamp,
                )
            })
            canvas.drawCircle(c, c, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), t.pill, t.pill2, clamp)
            })
            canvas.drawCircle(c, c, r - 0.6f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * dp
                color = if (t.light) 0xE6FFFFFF.toInt() else 0x59FFFFFF
            })
            ctx.getDrawable(R.drawable.ic_jumpstart)?.mutate()?.let { d ->
                d.setTint(t.onPill)
                val half = (r * 0.48f).toInt()
                d.setBounds((c - half).toInt(), (c - half).toInt(), (c + half).toInt(), (c + half).toInt())
                d.draw(canvas)
            }
            return bitmap
        }
    }
}
