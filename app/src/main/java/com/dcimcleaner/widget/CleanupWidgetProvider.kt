package com.dcimcleaner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dcimcleaner.MainActivity
import com.dcimcleaner.R
import com.dcimcleaner.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CleanupWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val statsDao = db.statsDao()

            // Pick randomly between month and day
            val useMonth = Random().nextBoolean()
            var date = ""
            var type = ""
            var count = 0
            var sizeMb = 0f

            if (useMonth) {
                val months = statsDao.getMonthStatsSync()
                val pick = months.randomOrNull()
                if (pick != null) {
                    date = pick.date
                    type = "month"
                    count = pick.fileCount
                    sizeMb = pick.totalSizeMb
                }
            } else {
                val days = statsDao.getDayStatsSync()
                val pick = days.randomOrNull()
                if (pick != null) {
                    date = pick.date
                    type = "day"
                    count = pick.fileCount
                    sizeMb = pick.totalSizeMb
                }
            }

            // Fallback to month if day returned nothing
            if (date.isEmpty()) {
                val months = statsDao.getMonthStatsSync()
                val pick = months.randomOrNull()
                if (pick != null) {
                    date = pick.date
                    type = "month"
                    count = pick.fileCount
                    sizeMb = pick.totalSizeMb
                }
            }

            val finalDate = date
            val finalType = type
            val finalCount = count
            val finalSizeMb = sizeMb

            CoroutineScope(Dispatchers.Main).launch {
                ids.forEach { id ->
                    updateWidget(context, manager, id, finalDate, finalType, finalCount, finalSizeMb)
                }
            }
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        date: String,
        type: String,
        count: Int,
        sizeMb: Float
    ) {
        if (date.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_cleanup)

        // Format date nicely
        val displayDate = try {
            if (type == "month") {
                val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                SimpleDateFormat("MMM yyyy", Locale.US).format(sdf.parse(date)!!)
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                SimpleDateFormat("MMM d, yyyy", Locale.US).format(sdf.parse(date)!!)
            }
        } catch (e: Exception) { date }

        // Format size
        val sizeText = if (sizeMb >= 1024f) "${"%.1f".format(sizeMb / 1024f)} GB"
                       else "${"%.0f".format(sizeMb)} MB"

        views.setTextViewText(R.id.tv_date, displayDate)
        views.setTextViewText(R.id.tv_count, "$count photos")
        views.setTextViewText(R.id.tv_size, sizeText)
        views.setTextViewText(R.id.tv_type, if (type == "month") "by month" else "by day")

        // Tap opens Image Cleaner with this date
        val argKey = if (type == "month") "load_month" else "load_day"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "WIDGET_CLEANUP_OPEN"
            putExtra(argKey, date)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tv_date, pending)

        // Make whole widget clickable
        val rootIntent = Intent(context, MainActivity::class.java).apply {
            action = "WIDGET_CLEANUP_OPEN"
            putExtra(argKey, date)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val rootPending = PendingIntent.getActivity(
            context, id + 1000, rootIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Set on the root layout id — add android:id="@+id/widget_cleanup_root" to the LinearLayout
        views.setOnClickPendingIntent(R.id.widget_cleanup_root, rootPending)

        manager.updateAppWidget(id, views)
    }
}