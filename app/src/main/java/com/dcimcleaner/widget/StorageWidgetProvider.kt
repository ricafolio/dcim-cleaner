package com.dcimcleaner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.dcimcleaner.R
import java.text.SimpleDateFormat
import java.util.*

class StorageWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.dcimcleaner.STORAGE_WIDGET_REFRESH"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onEnabled(context: Context) {
        saveStorageSnapshot(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, StorageWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }
    }

    private fun saveStorageSnapshot(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prefs = context.getSharedPreferences("widget_storage", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("today_available_gb", getAvailableGb())
            .putString("last_date", today)
            .apply()
    }

    private fun getAvailableGb(): Float {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return (stat.availableBlocksLong * stat.blockSizeLong) / 1_073_741_824f
    }

    private fun formatGb(gb: Float): String =
        if (gb < 0.1f) "${"%.0f".format(gb * 1024)} MB"
        else "${"%.1f".format(gb)} GB"

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        val prefs = context.getSharedPreferences("widget_storage", Context.MODE_PRIVATE)
        val savedDate = prefs.getString("last_date", "")
        val availableGb = getAvailableGb()

        when {
            savedDate.isNullOrEmpty() -> {
                prefs.edit()
                    .putFloat("today_available_gb", availableGb)
                    .putString("last_date", today)
                    .apply()
            }
            savedDate != today -> {
                val prevGb = prefs.getFloat("today_available_gb", -1f)
                prefs.edit()
                    .putFloat("yesterday_available_gb", prevGb)
                    .putString("yesterday_date", savedDate)
                    .putFloat("today_available_gb", availableGb)
                    .putString("last_date", today)
                    .apply()
            }
            else -> {
                prefs.edit().putFloat("today_available_gb", availableGb).apply()
            }
        }

        val yesterdayGb = prefs.getFloat("yesterday_available_gb", -1f)
        val views = RemoteViews(context.packageName, R.layout.widget_storage)

        // Today label + value
        val todayDisplay = SimpleDateFormat("MM/dd", Locale.US).format(Date())
        views.setTextViewText(R.id.tv_today_label, "today ($todayDisplay)")
        views.setTextViewText(R.id.tv_today_storage, formatGb(availableGb))

        // Yesterday + diff
        if (yesterdayGb >= 0) {
            val diff = availableGb - yesterdayGb
            views.setTextViewText(R.id.tv_yesterday_storage, formatGb(yesterdayGb))
            val absDiff = kotlin.math.abs(diff)
            val diffText = when {
                diff < -0.01f -> "▼ ${formatGb(absDiff)} lost"
                diff > 0.01f  -> "▲ ${formatGb(absDiff)} freed"
                else          -> "no change"
            }
            views.setTextViewText(R.id.tv_yesterday_diff, diffText)
        } else {
            views.setTextViewText(R.id.tv_yesterday_storage, "—")
            views.setTextViewText(R.id.tv_yesterday_diff, "no data yet")
        }

        // Click to refresh
        val refreshIntent = Intent(context, StorageWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_storage_root, pendingIntent)

        manager.updateAppWidget(id, views)
    }
}
