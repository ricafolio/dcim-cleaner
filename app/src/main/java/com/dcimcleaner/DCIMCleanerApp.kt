package com.dcimcleaner

import android.app.Application
import com.dcimcleaner.data.repository.SessionPrefs

class DCIMCleanerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Clear "visited this session" tracking on a fresh app process start,
        // so no-repeat filters only apply within a single app session, not forever.
        SessionPrefs(this).clearVisitedSession()
    }
}
