package com.dcimcleaner.data.repository

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class SessionPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var totalTrashedCount: Int
        get() = prefs.getInt("trashed_count", 0)
        set(v) = prefs.edit().putInt("trashed_count", v).apply()

    var totalTrashedSizeMb: Float
        get() = prefs.getFloat("trashed_size_mb", 0f)
        set(v) = prefs.edit().putFloat("trashed_size_mb", v).apply()

    // today's freed — resets automatically when day changes
    var todayTrashedSizeMb: Float
        get() {
            val today = sdf.format(Date())
            return if (prefs.getString("today_date", "") == today)
                prefs.getFloat("today_trashed_mb", 0f) else 0f
        }
        set(v) {
            val today = sdf.format(Date())
            prefs.edit().putFloat("today_trashed_mb", v)
                .putString("today_date", today).apply()
        }

    var lastVisitedDate: String
        get() = prefs.getString("last_visited_date", "") ?: ""
        set(v) = prefs.edit().putString("last_visited_date", v).apply()

    var lastVisitedType: String
        get() = prefs.getString("last_visited_type", "month") ?: "month"
        set(v) = prefs.edit().putString("last_visited_type", v).apply()

    fun addTrashed(sizeMb: Float) {
        totalTrashedCount += 1
        totalTrashedSizeMb += sizeMb
        todayTrashedSizeMb += sizeMb  // setter reads current first, so += works fine
    }

    // History stack for Previous button — stored as "type:date|type:date"
    fun pushHistory(type: String, date: String) {
        val stack = getStack().toMutableList()
        val entry = "$type:$date"
        if (stack.lastOrNull() == entry) return
        stack.add(entry)
        if (stack.size > 20) stack.removeAt(0)
        prefs.edit().putString("history_stack", stack.joinToString("|")).apply()
    }

    fun popHistory(): Pair<String, String>? {
        val stack = getStack().toMutableList()
        if (stack.size < 2) return null
        stack.removeAt(stack.size - 1) // discard current
        val prev = stack.lastOrNull() ?: return null
        prefs.edit().putString("history_stack", stack.joinToString("|")).apply()
        val parts = prev.split(":")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else null
    }

    fun hasHistory(): Boolean = getStack().size >= 2

    private fun getStack(): List<String> {
        val raw = prefs.getString("history_stack", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("|")
    }
}
