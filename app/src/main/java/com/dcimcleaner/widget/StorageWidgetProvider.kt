package com.dcimcleaner.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.dcimcleaner.R
import java.text.SimpleDateFormat
import java.util.*

class StorageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onEnabled(context: Context) {
        // First time widget is added — save today's storage immediately
        saveStorageSnapshot(context)
    }

    private fun saveStorageSnapshot(context: Context) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        val availableGb = getAvailableGb()
        val prefs = context.getSharedPreferences("widget_storage", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("today_available_gb", availableGb)
            .putString("last_date", today)
            .apply()
    }

    private fun getAvailableGb(): Float {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / 1_073_741_824f
    }

    private fun formatGb(gb: Float): String =
        if (gb < 1f) "${"%.0f".format(gb * 1024)} MB"
        else "${"%.1f".format(gb)} GB"

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        val prefs = context.getSharedPreferences("widget_storage", Context.MODE_PRIVATE)
        val savedDate = prefs.getString("last_date", "")
        val availableGb = getAvailableGb()

        if (savedDate.isNullOrEmpty()) {
            // First run — save now, no yesterday data yet
            prefs.edit()
                .putFloat("today_available_gb", availableGb)
                .putString("last_date", today)
                .apply()
        } else if (savedDate != today) {
            // Day has changed — shift today → yesterday
            val prevGb = prefs.getFloat("today_available_gb", -1f)
            prefs.edit()
                .putFloat("yesterday_available_gb", prevGb)
                .putFloat("today_available_gb", availableGb)
                .putString("last_date", today)
                .apply()
        } else {
            // Same day — just update today's reading
            prefs.edit()
                .putFloat("today_available_gb", availableGb)
                .apply()
        }

        val yesterdayGb = prefs.getFloat("yesterday_available_gb", -1f)
        val views = RemoteViews(context.packageName, R.layout.widget_storage)

        // Today
        views.setTextViewText(R.id.tv_today_storage, formatGb(availableGb))
        val todayDisplay = SimpleDateFormat("MM/dd", Locale.US).format(Date())
        views.setTextViewText(R.id.tv_today_label, "today — $todayDisplay")

        // Yesterday
        if (yesterdayGb >= 0) {
            val diff = availableGb - yesterdayGb
            views.setTextViewText(R.id.tv_yesterday_storage, formatGb(yesterdayGb))

            val absDiff = kotlin.math.abs(diff)
            val diffFormatted = formatGb(absDiff)
            val diffText = when {
                diff < -0.01f -> "▼ $diffFormatted lost"
                diff > 0.01f  -> "▲ $diffFormatted freed"
                else          -> "no change"
            }
            views.setTextViewText(R.id.tv_yesterday_diff, diffText)
        } else {
            views.setTextViewText(R.id.tv_yesterday_storage, "—")
            views.setTextViewText(R.id.tv_yesterday_diff, "no data yet")
        }

        // TEMP: hardcode yesterday for testing — remove this block before release
        // prefs.edit()
        //     .putFloat("yesterday_available_gb", 2.5f)  // fake yesterday value
        //     .putString("yesterday_date", "2026-03-16")
        //     .apply()

        manager.updateAppWidget(id, views)
    }
}